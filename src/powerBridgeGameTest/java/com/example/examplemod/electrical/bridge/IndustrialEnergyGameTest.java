package com.example.examplemod.electrical.bridge;

import com.drmangotea.tfmg.content.electricity.generators.large_generator.RotorBlockEntity;
import com.example.examplemod.electrical.MinePowerPortDevice;
import com.example.examplemod.mine.MinePowerDemand;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.george_vi.electroenergetics.content.rotor.*;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.world.level.validation.DirectoryValidator;

@GameTestHolder(PowerBridge.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustrialEnergyGameTest {
    @GameTest(template = "power_bridge_empty", timeoutTicks = 150)
    public static void industrialEeAt64(GameTestHelper h) { alternator(h, 64); }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 150)
    public static void industrialEeAt128(GameTestHelper h) { alternator(h, 128); }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 150)
    public static void industrialEeAt256(GameTestHelper h) { alternator(h, 256); }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 150)
    public static void eeMotorLosesPowerUnderLoad(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(4, 3, 4));
        BlockPos load = pos.east();
        level.setBlockAndUpdate(pos, block("electroenergetics:red_electric_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        // The compact TFMG generator loads the shaft at 50 SU per RPM.
        // At 32 RPM it takes 1600 SU, close to the motor's capacity at 224 V.
        level.setBlockAndUpdate(load, block("tfmg:generator").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        Consumer<AddToElectricGraphEvent> source = event -> {
            if (event.level != level) return;
            var negative = new InWorldNode(0, pos);
            var positive = new InWorldNode(1, pos);
            var supply = new InWorldNode(44, pos);
            event.builder.defaultZeroPotential(negative, 100);
            event.builder.connect(negative, supply, ElectricalProperties.fromThevenin(.01, 224));
            event.builder.connect(supply, positive, ElectricalProperties.resistor(.01));
        };
        double[] watts = {0};
        Consumer<FinishElectricSimulationEvent> measure = event -> {
            if (event.level != level) return;
            double voltage = event.results.getVoltageAt(pos, 0, 1);
            double current = event.results.getCurrentThrough(pos, 0, 1);
            watts[0] = Math.abs(voltage * current);
        };
        NeoForge.EVENT_BUS.addListener(source);
        NeoForge.EVENT_BUS.addListener(measure);
        h.runAfterDelay(100, () -> {
            NeoForge.EVENT_BUS.unregister(source);
            NeoForge.EVENT_BUS.unregister(measure);
            var motor = (ElectricMotorBlockEntity) level.getBlockEntity(pos);
            var generator = (com.drmangotea.tfmg.content.electricity.generators.GeneratorBlockEntity) level.getBlockEntity(load);
            double stress = generator.calculateStressApplied() * Math.abs(generator.getSpeed());
            System.out.printf("EE motor under load: %.1f RPM, %.2f W, %.2f SU%n", motor.getSpeed(), watts[0], stress);
            h.assertTrue(stress >= 1500 && watts[0] > 0, "Actual mechanical load and electrical input");
            h.assertTrue(stress / watts[0] <= .82, "EE motor must lose power under load: " + stress / watts[0]);
            h.succeed();
        });
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 120)
    public static void tfmgMotorCapacityStaysBelowInput(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(4, 3, 4));
        level.setBlockAndUpdate(pos, block("tfmg:electric_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        level.setBlockAndUpdate(pos.west(), block("tfmg:creative_generator").defaultBlockState());
        h.runAfterDelay(5, () -> {
            // 80 V is the highest SU/W point with the stock 12 SU/RPM capacity.
            var source = (com.drmangotea.tfmg.content.electricity.generators.creative_generator.CreativeGeneratorBlockEntity)
                    level.getBlockEntity(pos.west());
            var output = source.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE);
            output.setValue(8);
        });
        h.runAfterDelay(65, () -> {
            var motor = (com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity)
                    level.getBlockEntity(pos);
            double capacity = motor.calculateAddedStressCapacity() * Math.abs(motor.getSpeed());
            int watts = motor.getPowerUsage();
            System.out.printf("TFMG motor: %d V, %.1f RPM, %d W, %.1f SU%n",
                    motor.getData().getVoltage(), motor.getSpeed(), watts, capacity);
            h.assertTrue(motor.getData().getVoltage() == 80 && capacity > 0 && watts > 0, "Motor reached test voltage and speed");
            h.assertTrue(capacity / watts <= .91, "TFMG motor capacity must stay below electrical input");
            h.succeed();
        });
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 200)
    public static void industrialRecipesLoaded(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var repository = server.getPackRepository();
        repository.addPackFinder(new FolderRepositorySource(Path.of(System.getProperty("ccec.energyDatapacks")),
                PackType.SERVER_DATA, PackSource.WORLD, new DirectoryValidator(path -> false)));
        repository.reload();
        h.assertTrue(repository.isAvailable("file/industrial-ee"), "Industrial EE datapack discovered");
        var ids = new ArrayList<>(repository.getSelectedIds());
        ids.remove("file/industrial-ee");
        ids.add("file/industrial-ee");
        var reload = server.reloadResources(ids);
        h.succeedWhen(() -> {
            h.assertTrue(reload.isDone(), "Waiting for the recipe reload");
            reload.join();
            assertIndustrialRecipes(h);
        });
    }

    private static void assertIndustrialRecipes(GameTestHelper h) {
        String[] names = {"alternator_rotor", "stator", "alternator_brushes", "three_phase_alternator_brushes"};
        String[] parts = {"tfmg:rotor", "tfmg:stator", "tfmg:capacitor_item", "tfmg:electromagnetic_coil"};
        for (int i = 0; i < names.length; i++) {
            var id = ResourceLocation.parse("electroenergetics:crafting/" + names[i]);
            var recipe = h.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(parts[i]));
            h.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(item))),
                    id + " uses " + parts[i]);
        }
    }

    private static void alternator(GameTestHelper h, int rpm) {
        var level = h.getLevel();
        var brush = h.absolutePos(new BlockPos(4, 3, 4));
        var rotor = brush.east();
        var load = brush.south(2);
        var rotorState = block("electroenergetics:alternator_rotor").defaultBlockState()
                .setValue(AlternatorRotorBlock.AXIS, Direction.Axis.X);
        level.setBlockAndUpdate(brush, block("electroenergetics:alternator_brushes").defaultBlockState()
                .setValue(AlternatorBrushesBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(rotor, rotorState);
        var stator = block("electroenergetics:stator");
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == Direction.Axis.X) continue;
            BlockPos cell = rotor.relative(direction);
            var state = stator.getStateDefinition().getPossibleStates().stream()
                    .filter(s -> StatorBlock.canPowerRotor(cell, s, rotor, rotorState)).findFirst().orElseThrow();
            level.setBlockAndUpdate(cell, state);
        }
        motor(h, rotor.east(), Direction.WEST, rpm);
        level.setBlockAndUpdate(load, block("createcityexpansioncore:mine_power_port").defaultBlockState());
        double rated = 216.0 * rpm;
        double voltage = rated / 100.0;
        // Demand slightly above the rated output to exercise the source limit.
        double demand = 250_000.0 / (voltage * voltage / (rated * 1.15));
        for (int tick = 0; tick < 100; tick += 5) {
            h.runAfterDelay(tick, () -> MinePowerDemand.publishWatts(level, load, demand));
        }
        var circuit = connect(h, brush, load);
        h.runAfterDelay(100, () -> {
            NeoForge.EVENT_BUS.unregister(circuit);
            var machine = (com.simibubi.create.content.kinetics.base.KineticBlockEntity) level.getBlockEntity(rotor);
            var port = DevicesSavedData.load(level).getDevice(load, MinePowerPortDevice.class);
            double watts = port.getPower();
            double stress = machine.calculateStressApplied() * Math.abs(machine.getSpeed());
            System.out.printf("Industrial EE %d RPM: %.1f V, %.1f W, %.1f SU%n", rpm, port.getVoltage(), watts, stress);
            h.assertTrue(Math.abs(machine.getSpeed()) == rpm, "Expected shaft speed " + rpm);
            h.assertTrue(watts >= rated * 0.91 && watts <= rated * 1.09, "Loaded output in target range: " + watts);
            h.assertTrue(Math.abs(stress - (12.125 * 18 * rpm)) < 1, "Full section has four active stators: " + stress);
            h.succeed();
        });
    }

    @GameTest(template = "power_bridge_empty", timeoutTicks = 180)
    public static void tfmgBridgePowersMinePort(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(4, 3, 4));
        var state = PowerBridge.BLOCK.get().defaultBlockState().setValue(DcBridgeBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(pos, state);
        PowerBridge.BLOCK.get().setPlacedBy(level, pos, state, null, new ItemStack(PowerBridge.ITEM.get()));
        var bridge = (DcBridgeEntity) level.getBlockEntity(pos);
        bridge.reverse();
        bridge.setSelectedVoltage(600);
        BlockPos source = pos.east().above();
        // Connect through the stator ring so all eight stators can stay installed.
        BlockPos rotorPos = source.above(2);
        level.setBlockAndUpdate(rotorPos, block("tfmg:rotor").defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                if (y != 0 || z != 0) {
                    level.setBlockAndUpdate(rotorPos.offset(0, y, z), block("tfmg:stator").defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(source, block("tfmg:heavy_cable_hub").defaultBlockState());
        motor(h, rotorPos.east(), Direction.WEST, 256);
        ((RotorBlockEntity) level.getBlockEntity(rotorPos)).findStators();
        BlockPos portPos = pos.west(2);
        level.setBlockAndUpdate(portPos, block("createcityexpansioncore:mine_power_port").defaultBlockState());
        for (int tick = 45; tick < 120; tick += 5) {
            h.runAfterDelay(tick, () -> MinePowerDemand.publish(level, portPos, true, Tier.I));
        }
        var circuit = connect(h, pos, portPos);
        h.runAfterDelay(40, () -> {
            var port = DevicesSavedData.load(level).getDevice(portPos, MinePowerPortDevice.class);
            System.out.printf("Mine port in standby: %.1f V, %.1f W%n", port.getVoltage(), port.getPower());
            h.assertTrue(port.getVoltage() >= 500 && port.getVoltage() <= 600, "Safe startup voltage");
        });
        h.runAfterDelay(120, () -> {
            NeoForge.EVENT_BUS.unregister(circuit);
            var generator = (RotorBlockEntity) level.getBlockEntity(rotorPos);
            var port = DevicesSavedData.load(level).getDevice(portPos, MinePowerPortDevice.class);
            System.out.printf("Mine port via TFMG bridge: %.1f V, %.2f A, %.1f W, generator %d W%n",
                    port.getVoltage(), port.getCurrent(), port.getPower(), generator.powerGeneration());
            h.assertTrue(generator.powerGeneration() > 0, "Native TFMG generator driven by shaft");
            h.assertTrue(port.getVoltage() >= 500 && port.getVoltage() <= 600
                    && port.getCurrent() >= 17 && port.getCurrent() <= 26
                    && port.getPower() >= 8500 && port.getPower() <= 15000, "Tier I operating window: "
                    + port.getVoltage() + " V / " + port.getCurrent() + " A / " + port.getPower() + " W");
            h.succeed();
        });
    }

    private static Consumer<AddToElectricGraphEvent> connect(GameTestHelper h, BlockPos source, BlockPos load) {
        Consumer<AddToElectricGraphEvent> circuit = event -> {
            if (event.level != h.getLevel()) return;
            var ground = new InWorldNode(0, source);
            event.builder.defaultZeroPotential(ground, 100);
            event.builder.connect(ground, new InWorldNode(0, load), ElectricalProperties.resistor(.001));
            event.builder.connect(new InWorldNode(1, source), new InWorldNode(1, load), ElectricalProperties.resistor(.001));
        };
        NeoForge.EVENT_BUS.addListener(circuit);
        return circuit;
    }

    private static void motor(GameTestHelper h, BlockPos pos, Direction facing, int rpm) {
        h.getLevel().setBlockAndUpdate(pos, block("create:creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, facing));
        h.runAfterDelay(5, () -> {
            var motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(pos);
            motor.generatedSpeed.setValue(rpm);
            motor.updateGeneratedRotation();
        });
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }
}
