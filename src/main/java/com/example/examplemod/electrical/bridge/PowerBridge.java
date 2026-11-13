package com.example.examplemod.electrical.bridge;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(PowerBridge.MOD_ID)
public final class PowerBridge {
    public static final String MOD_ID = "createcityexpansioncore";
    public static final String NAME = "dc_power_bridge";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredBlock<DcBridgeBlock> BLOCK =
            BLOCKS.register(NAME, () -> new DcBridgeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> ITEM =
            ITEMS.registerSimpleBlockItem(NAME, BLOCK);
    public static final net.neoforged.neoforge.registries.DeferredHolder<BlockEntityType<?>, BlockEntityType<DcBridgeEntity>> ENTITY =
            ENTITIES.register(NAME, () -> BlockEntityType.Builder.of(DcBridgeEntity::create, BLOCK.get()).build(null));
    public static final net.neoforged.neoforge.registries.DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<EeBridgeDevice>> EE_DEVICE =
            DEVICES.register(NAME, () -> new SimulatedDeviceType<>(id(NAME), EeBridgeDevice::new));

    private static final ModConfigSpec.Builder CONFIG = new ModConfigSpec.Builder();
    static final ModConfigSpec.DoubleValue MIN_VOLTAGE = CONFIG.defineInRange("minimumOutputVolts", 50.0, 1, 20_000);
    static final ModConfigSpec.DoubleValue MAX_VOLTAGE = CONFIG.defineInRange("maximumOutputVolts", 3000.0, 1, 20_000);
    static final ModConfigSpec.DoubleValue MAX_WATTS = CONFIG.defineInRange("maximumWatts", 100_000.0, 1, 1_000_000);
    static final ModConfigSpec.DoubleValue MAX_AMPS = CONFIG.defineInRange("maximumAmps", 100.0, 0.1, 1_000);
    static final ModConfigSpec.DoubleValue EFFICIENCY = CONFIG.defineInRange("efficiency", 0.95, 0.01, 1);
    private static final ModConfigSpec SPEC = CONFIG.build();

    public PowerBridge(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        DEVICES.register(bus);
        bus.addListener(this::addCreativeItem);
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "createcityexpansioncore-powerbridge.toml");
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        if (tfmgSupported()) {
            TfmgBridgePort.registerEvents();
        }
    }

    static boolean tfmgSupported() {
        return ModList.get().getModContainerById("tfmg")
                .map(mod -> mod.getModInfo().getVersion().toString().equals("1.2.0")).orElse(false);
    }

    static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }

    static int outputVoltage(int voltage) {
        int max = (int) MAX_VOLTAGE.get().doubleValue();
        int min = Math.min(max, (int) Math.ceil(MIN_VOLTAGE.get()));
        return Math.clamp(voltage, min, max);
    }

    private void addCreativeItem(BuildCreativeModeTabContentsEvent event) {
        if (tfmgSupported() && event.getTabKey().location().equals(id("industrial"))) {
            event.accept(ITEM.get());
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        var command = Commands.literal("powerbridge").requires(source -> source.hasPermission(2));
        addSetting(command, "minimumOutputVolts", MIN_VOLTAGE, 1, 20_000);
        addSetting(command, "maximumOutputVolts", MAX_VOLTAGE, 1, 20_000);
        addSetting(command, "maximumWatts", MAX_WATTS, 1, 1_000_000);
        addSetting(command, "maximumAmps", MAX_AMPS, 0.1, 1_000);
        addSetting(command, "efficiency", EFFICIENCY, 0.01, 1);
        event.getDispatcher().register(Commands.literal("ccec").then(command));
    }

    private static void addSetting(com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command,
                                   String name, ModConfigSpec.DoubleValue setting, double min, double max) {
        command.then(Commands.literal(name)
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(name + " = " + setting.get()), false);
                    return 1;
                })
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(min, max)).executes(context -> {
                    double value = DoubleArgumentType.getDouble(context, "value");
                    if ((setting == MIN_VOLTAGE && value > MAX_VOLTAGE.get())
                            || (setting == MAX_VOLTAGE && value < MIN_VOLTAGE.get())) {
                        context.getSource().sendFailure(Component.translatable("gui.createcityexpansioncore.powerbridge.voltage_range"));
                        return 0;
                    }
                    setting.set(value);
                    setting.save();
                    context.getSource().sendSuccess(() -> Component.literal(name + " = " + value), true);
                    return 1;
                })));
    }
}
