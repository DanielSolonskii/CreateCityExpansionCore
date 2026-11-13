package com.example.examplemod.electrical.bridge;

import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.generators.GeneratorBlockEntity;
import com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity;
import com.drmangotea.tfmg.content.electricity.utilities.resistor.ResistorBlockEntity;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.ACSource;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

@GameTestHolder(PowerBridge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DcBridgeGameTest {
    @GameTest(template = "power_bridge_empty", timeoutTicks = 90)
    public static void createVoltageSettingsAndAdminCommand(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos, PowerBridge.BLOCK.get().defaultBlockState());
        var bridge = (DcBridgeEntity) level.getBlockEntity(pos);
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putInt("ScrollValue", 1234);
        bridge.read(legacy, level.registryAccess(), false);
        helper.assertTrue(bridge.selectedVoltage() == 1234, "Legacy voltage is preserved");

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
        var behaviour = bridge.getBehaviour(ScrollValueBehaviour.TYPE);
        var board = behaviour.createBoard(player, null);
        helper.assertTrue(board.rows().size() == 3 && board.maxValue() == 99, "One compact Create board shows three rows");
        String label = "gui.createcityexpansioncore.powerbridge.";
        helper.assertTrue(board.title().getContents().equals(Component.translatable(label + "voltage_total", 1234).getContents()),
                "The title starts with the saved sum");
        var hovered = board.formatter().format(new ValueSettings(1, 44));
        helper.assertTrue(hovered.equals(Component.translatable(label + "voltage_value", 400)), "The cursor shows only the hundreds contribution");
        helper.assertTrue(board.title().getContents().equals(Component.translatable(label + "voltage_total", 1434).getContents()),
                "The title previews the updated sum");
        var thousands = board.formatter().format(new ValueSettings(2, 20));
        helper.assertTrue(thousands.equals(Component.translatable(label + "voltage_value_kv", 2)), "The thousands row shows kilovolts");
        behaviour.setValueSettings(player, new ValueSettings(0, 37), false);
        behaviour.setValueSettings(player, new ValueSettings(1, 44), false);
        behaviour.setValueSettings(player, new ValueSettings(2, 20), false);
        helper.assertTrue(bridge.selectedVoltage() == 2437 && bridge.volts() == 2437, "Rows sum to 2437 V");
        behaviour.setValueSettings(player, new ValueSettings(3, 10), false);
        behaviour.setValueSettings(player, new ValueSettings(0, 100), false);
        helper.assertTrue(bridge.selectedVoltage() == 2437, "Invalid rows and positions are rejected");
        for (int row = 0; row < 3; row++) {
            behaviour.setValueSettings(player, new ValueSettings(row, 99), false);
        }
        helper.assertTrue(bridge.selectedVoltage() == 10_999 && bridge.volts() == PowerBridge.outputVoltage(10_999),
                "Server caps the sum at the configured voltage limit");
        player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        behaviour.setValueSettings(player, new ValueSettings(0, 0), false);
        helper.assertTrue(bridge.selectedVoltage() == 10_999, "A player out of reach cannot change the voltage");
        var commandRoot = level.getServer().getCommands().getDispatcher().getRoot();
        var command = commandRoot.getChild("ccec").getChild("powerbridge");
        var admin = level.getServer().createCommandSourceStack();
        helper.assertTrue(command.getChild("efficiency") != null && command.canUse(admin)
                && !command.canUse(admin.withPermission(0)), "CCEC command retains its administrator permission");
        helper.assertTrue(commandRoot.getChild("powerbridge") == null, "Only the CCEC command is registered");
        helper.succeed();
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 90)
    public static void nativeFeIsolation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos, PowerBridge.BLOCK.get().defaultBlockState());
        var bridge = (DcBridgeEntity) level.getBlockEntity(pos);
        helper.assertTrue((bridge instanceof DcBridgeEntity.ActivePort) == PowerBridge.tfmgSupported(),
                "Only the supported TFMG installation loads the native adapter");
        BlockPos feConverter = pos.west(2);
        level.setBlockAndUpdate(feConverter, BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("electroenergetics:converter")).defaultBlockState());
        helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, feConverter, Direction.EAST) == null,
                "EE Forge Energy capability must be blocked on the server");
        var storage = new net.neoforged.neoforge.energy.EnergyStorage(100);
        helper.assertTrue(storage.receiveEnergy(50, false) == 0 && !storage.canReceive(),
                "Direct use of NeoForge's built-in FE storage is blocked");
        if (PowerBridge.tfmgSupported()) {
            BlockPos accumulator = pos.south(2);
            level.setBlockAndUpdate(accumulator, tfmg("accumulator"));
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, accumulator, Direction.NORTH) == null,
                    "TFMG Forge Energy capability must be blocked on the server");
            var stored = (AccumulatorBlockEntity) level.getBlockEntity(accumulator);
            stored.energy.setEnergy(10_000);
            helper.assertTrue(!stored.canPower() && stored.powerGeneration() == 0 && stored.getChargingRate() == 0,
                    "Previously stored FE cannot supply a native TFMG network");
        }
        helper.succeed();
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 145)
    public static void nativeGeneratorSharesBusAndRedstoneDisablesBridge(GameTestHelper helper) {
        if (!PowerBridge.tfmgSupported()) { helper.succeed(); return; }
        var level = helper.getLevel();
        BlockPos hub = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos bridgePos = hub.west();
        BlockPos generatorPos = hub.south();
        BlockPos motorPos = generatorPos.south();
        BlockPos loadPos = hub.east();
        level.setBlockAndUpdate(hub, tfmg("heavy_cable_hub"));
        level.setBlockAndUpdate(bridgePos, PowerBridge.BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(generatorPos, tfmg("generator").setValue(BlockStateProperties.FACING, Direction.SOUTH));
        var motorBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:creative_motor"));
        level.setBlockAndUpdate(motorPos, motorBlock.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        level.setBlockAndUpdate(loadPos, tfmg("resistor").setValue(BlockStateProperties.FACING, Direction.EAST));
        var bridge = (TfmgBridgePort) level.getBlockEntity(bridgePos);
        var generator = (GeneratorBlockEntity) level.getBlockEntity(generatorPos);
        var load = (ResistorBlockEntity) level.getBlockEntity(loadPos);
        load.resistance = 5;
        Consumer<AddToElectricGraphEvent> circuit = event -> {
            if (event.level == level) connectEeSource(event, bridgePos);
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        helper.runAfterDelay(10, () -> {
            var motor = (CreativeMotorBlockEntity) level.getBlockEntity(motorPos);
            motor.generatedSpeed.setValue(128);
        });
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(generator.powerGeneration() > 0, "Native generator is running");
            helper.assertTrue(bridge.getOrCreateElectricNetwork() == generator.getOrCreateElectricNetwork(), "Shared native bus");
            helper.assertTrue(bridge.outputWatts() > 1_000 && load.getData().getVoltage() >= 200,
                    "Bridge supplements the native generator: " + bridge.readings(false));
            bridge.cycleRedstone();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(bridge.powerGeneration() == 0 && !bridge.enabled(), "No redstone signal disables the bridge");
                level.setBlockAndUpdate(bridgePos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.runAfterDelay(25, () -> {
                    NeoForge.EVENT_BUS.unregister(circuit);
                    helper.assertTrue(bridge.enabled() && bridge.outputWatts() > 1_000, "Redstone restores the bridge");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 120)
    public static void parallelEeToTfmgAndSourceLoss(GameTestHelper helper) {
        if (!PowerBridge.tfmgSupported()) { helper.succeed(); return; }
        var level = helper.getLevel();
        BlockPos hub = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos first = hub.west();
        BlockPos second = hub.north();
        level.setBlockAndUpdate(hub, tfmg("heavy_cable_hub"));
        level.setBlockAndUpdate(first, PowerBridge.BLOCK.get().defaultBlockState().setValue(DcBridgeBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(second, PowerBridge.BLOCK.get().defaultBlockState().setValue(DcBridgeBlock.FACING, Direction.EAST));
        BlockPos load = hub.east();
        level.setBlockAndUpdate(load, tfmg("resistor").setValue(BlockStateProperties.FACING, Direction.EAST));
        boolean[] supply = {true, true};
        Consumer<AddToElectricGraphEvent> circuit = event -> {
            if (event.level != level) return;
            connectEeSource(event, first, supply[0] ? ElectricalProperties.fromThevenin(.05, 230)
                    : new ACSource(230, 0, .05));
            if (supply[1]) connectEeSource(event, second);
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        helper.runAfterDelay(50, () -> {
            var a = (TfmgBridgePort) level.getBlockEntity(first);
            var b = (TfmgBridgePort) level.getBlockEntity(second);
            helper.assertTrue(a.getOrCreateElectricNetwork() == b.getOrCreateElectricNetwork(), "Shared TFMG bus");
            helper.assertTrue(a.powerGeneration() > 0 && b.powerGeneration() > 0, "Both bridges supply the bus");
            helper.assertTrue(((IElectric) level.getBlockEntity(load)).getData().getVoltage() >= 200, "TFMG load receives voltage");
            supply[0] = false;
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(a.powerGeneration() == 0 && b.powerGeneration() > 0, "Live bridge takes over");
                helper.assertTrue(a.acDetected(), "EE AC is rejected even with one simulation sample per tick");
                helper.assertTrue(((IElectric) level.getBlockEntity(load)).getData().getVoltage() >= 200, "Remaining bridge supports the load");
                supply[1] = false;
                NeoForge.EVENT_BUS.unregister(circuit);
                helper.runAfterDelay(12, () -> {
                    helper.assertTrue(((IElectric) level.getBlockEntity(load)).getData().getVoltage() == 0, "No output after source loss");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 120)
    public static void tfmgToEeUnderLoad(GameTestHelper helper) {
        if (!PowerBridge.tfmgSupported()) { helper.succeed(); return; }
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(pos, PowerBridge.BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), tfmg("creative_generator"));
        var bridge = (DcBridgeEntity) level.getBlockEntity(pos);
        bridge.reverse();
        Consumer<AddToElectricGraphEvent> circuit = event -> {
            if (event.level != level) return;
            var returnNode = new InWorldNode(24, pos);
            var negative = new InWorldNode(0, pos);
            var positive = new InWorldNode(1, pos);
            event.builder.connect(negative, returnNode, ElectricalProperties.resistor(.01));
            event.builder.connect(returnNode, positive, ElectricalProperties.resistor(500));
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        helper.runAfterDelay(55, () -> {
            NeoForge.EVENT_BUS.unregister(circuit);
            helper.assertTrue(bridge.outputWatts() > 40, "EE load receives real DC power: " + bridge.readings(false));
            level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(bridge.outputWatts() == 0, "No output after TFMG generator removal");
                helper.succeed();
            });
        });
    }

    private static void connectEeSource(AddToElectricGraphEvent event, BlockPos pos) {
        connectEeSource(event, pos, ElectricalProperties.fromThevenin(.05, 230));
    }

    private static void connectEeSource(AddToElectricGraphEvent event, BlockPos pos, ElectricalProperties source) {
        var supply = new InWorldNode(23, pos);
        var negative = new InWorldNode(0, pos);
        var positive = new InWorldNode(1, pos);
        event.builder.connect(negative, supply, source);
        event.builder.connect(supply, positive, ElectricalProperties.resistor(.01));
        event.builder.defaultZeroPotential(negative, 100);
    }

    private static net.minecraft.world.level.block.state.BlockState tfmg(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("tfmg", id)).defaultBlockState();
    }
}
