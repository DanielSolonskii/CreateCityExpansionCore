package com.example.examplemod;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityInvite;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityMapNetworkHandlers;
import com.example.examplemod.city.CityProjectNetworkHandlers;
import com.example.examplemod.electrical.*;
import com.example.examplemod.event.CityProtectionEvents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.block.CityPylonBlockEntity;
import com.example.examplemod.client.screen.MineControllerScreen;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import com.example.examplemod.city.City;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.item.MagneticAnalyzerItem;
import com.example.examplemod.item.CityPylonItem;
import com.example.examplemod.item.MineControllerItem;
import com.example.examplemod.item.DispatcherKeyItem;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.RailwayStatePayload;
import com.example.examplemod.railway.RailwayNetworkHandlers;
import com.example.examplemod.railway.RailwayManager;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.mine.MineGatewayBlockEntity;
import com.example.examplemod.mine.MineNetworkHandlers;
import com.example.examplemod.network.SelectMineDepositPayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import com.example.examplemod.network.CommissionMineProjectPayload;
import com.example.examplemod.network.DecommissionCityProjectPayload;
import com.example.examplemod.network.CityDevelopmentPayload;
import com.example.examplemod.network.CityPylonStatePayload;
import com.example.examplemod.network.MineControlPayload;
import com.example.examplemod.network.SyncCityMemberRemovalPayload;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.ChangeCityRolePayload;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.command.GeologyCommand;
import com.example.examplemod.item.ModItems;
import com.example.examplemod.item.IndustrialMaterials;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.sound.WorldSoundHelper;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.network.MineControllerSnapshotPayload;
import com.example.examplemod.network.MineCommissionContextPayload;
import com.example.examplemod.network.CreateCityPayload;
import com.example.examplemod.network.InvitePlayerPayload;
import com.example.examplemod.network.RespondCityInvitePayload;
import com.example.examplemod.network.SyncCityMemberRolePayload;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import com.example.examplemod.network.RemoveCityMemberPayload;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.BlockPos;
import com.example.examplemod.network.GeologyAnalysisPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.BiConsumer;


/*
 * =========================================
 * ГЛАВНЫЙ КЛАСС МОДА
 * =========================================
 */

@Mod(CreateCityExpansionCore.MODID)
public class CreateCityExpansionCore {

    /*
     * =========================================
     * MOD ID
     * =========================================
     */

    public static final String MODID =
            "createcityexpansioncore";

    /*
     * =========================================
     * LOGGER
     * =========================================
     */

    public static final Logger LOGGER =
            LogUtils.getLogger();

    /*
     * =========================================
     * S2C PAYLOAD HANDLERS (Client-side delegates)
     * =========================================
     *
     * Эти статические поля хранят делегаты для обработки S2C пейлоадов на клиенте.
     * В common коде они инициализированы no-op; CreateCityExpansionCoreClient
     * переопределяет их реальными обработчиками в своём конструкторе.
     * Это позволяет зарегистрировать каждый S2C TYPE ровно один раз в common коде.
     */

    /**
     * Функциональный интерфейс для S2C обработчиков.
     * Контекст использует Object, чтобы избежать зависимости от внутренних классов NeoForge,
     * которых может не быть на compile classpath. На клиенте приводим к реальному типу
     * через рефлексию или небезопасное приведение.
     */
    @FunctionalInterface
    public interface PayloadHandler<P> {
        void handle(P payload, Object context);
    }

    public static PayloadHandler<GeologyAnalysisPayload> GEOLOGY_ANALYSIS_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<MineControllerSnapshotPayload> MINE_CONTROLLER_SNAPSHOT_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<MineCommissionContextPayload> MINE_COMMISSION_CONTEXT_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<CityInviteNotificationPayload> CITY_INVITE_NOTIFICATION_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<CityMapPeersPayload> CITY_MAP_PEERS_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<SyncCityMemberRemovalPayload> SYNC_CITY_MEMBER_REMOVAL_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<CityNotificationPayload> CITY_NOTIFICATION_HANDLER =
            (payload, context) -> {};

    /*
     * =========================================
     * BLOCKS
     * =========================================
     */

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(
                    MODID
            );

    /*
     * =========================================
     * ITEMS
     * =========================================
     */

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(
                    MODID
            );

    /*
     * =========================================
     * MENUS
     * =========================================
     */

    public static final DeferredRegister<MenuType<?>>
            MENUS =
            DeferredRegister.create(
                    Registries.MENU,
                    MODID
            );

    public static final DeferredRegister<BlockEntityType<?>>
            BLOCK_ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    MODID
            );

    /*
     * =========================================
     * CITY PYLON MENU
     * =========================================
     */

    public static final DeferredHolder<
            MenuType<?>,
            MenuType<CityPylonMenu>
            > CITY_PYLON_MENU =
            MENUS.register(
                    "city_pylon",
                    () ->
                            IMenuTypeExtension.create(
                                    CityPylonMenu::new
                            )
            );
    public static final DeferredHolder<MenuType<?>, MenuType<com.example.examplemod.menu.EconomyInfrastructureMenu>>
            ECONOMY_INFRASTRUCTURE_MENU = MENUS.register("economy_infrastructure",
            () -> IMenuTypeExtension.create(com.example.examplemod.menu.EconomyInfrastructureMenu::new));
    public static final DeferredBlock<MinePowerPortBlock> MINE_POWER_PORT =
            BLOCKS.register(
                    "mine_power_port",
                    () -> new MinePowerPortBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0f)
                                    .mapColor(MapColor.METAL)
                    )
            );
    public static final DeferredBlock<MineControllerBlock> MINE_CONTROLLER =
            BLOCKS.register(
                    "mine_controller",
                    () -> new MineControllerBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                                    .noLootTable()
                    )
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<MineControllerBlockEntity>
            > MINE_CONTROLLER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mine_controller",
                    () -> BlockEntityType.Builder.of(
                            MineControllerBlockEntity::new,
                            MINE_CONTROLLER.get()
                    ).build(null)
            );

    /*
     * =========================================
     * CREATIVE TABS
     * =========================================
     */

    public static final DeferredRegister<CreativeModeTab>
            CREATIVE_MODE_TABS =
            DeferredRegister.create(
                    Registries.CREATIVE_MODE_TAB,
                    MODID
            );

    /*
     * =========================================
     * CREATIVE TAB МОДА
     * =========================================
     *
     * Порядок содержимого соответствует
     * производственной иерархии:
     *
     *   Tier 1-derived (инструменты анализа)
     *   -> Tier 2 (промышленные полуфабрикаты)
     *   -> Tier 3 (инженерная продукция)
     *   -> машины и инфраструктура
     */

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab>
            INDUSTRIAL_TAB =
            CREATIVE_MODE_TABS.register(
                    "industrial",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable(
                                    "itemGroup." + MODID
                            ))
                            .icon(() -> IndustrialMaterials.STEEL_PLATE.get()
                                    .getDefaultInstance())
                            .displayItems(
                                    (parameters, output) ->
                                            IndustrialCreativeTab.populate(
                                                    parameters,
                                                    output
                                            )
                            )
                            .build()
            );

    /*
     * =========================================
     * EXAMPLE BLOCK
     * =========================================
     */

    public static final DeferredBlock<Block>
            EXAMPLE_BLOCK =
            BLOCKS.registerSimpleBlock(
                    "example_block",
                    BlockBehaviour.Properties
                            .of()
                            .mapColor(
                                    MapColor.STONE
                            )
            );

    public static final DeferredItem<BlockItem>
            EXAMPLE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "example_block",
                    EXAMPLE_BLOCK
            );
    /*
     * =========================================
     * MAGNETIC ANALYZER
     * =========================================
     */

    public static final DeferredItem<Item>
            MAGNETIC_ANALYZER =
            ITEMS.registerItem(
                    "magnetic_analyzer",
                    MagneticAnalyzerItem::new,
                    new Item.Properties()
                            .durability(10)
            );
    public static final DeferredItem<DispatcherKeyItem> DISPATCHER_KEY =
            ITEMS.registerItem(
                    "dispatcher_key",
                    DispatcherKeyItem::new,
                    new Item.Properties().stacksTo(1)
            );
    public static final DeferredItem<Item> MINE_POWER_PORT_ITEM =
            ITEMS.registerItem(
                    "mine_power_port",
                    properties -> new BlockItem(
                            MINE_POWER_PORT.get(),
                            properties
                    ),
                    new Item.Properties()
            );
    /*
     * =========================================
     * CITY PYLON
     * =========================================
     */

    public static final DeferredBlock<CityPylonBlock>
            CITY_PYLON =
            BLOCKS.registerBlock(
                    "city_pylon",
                    CityPylonBlock::new,
                    BlockBehaviour.Properties
                            .of()
                            .mapColor(
                                    MapColor.METAL
                            )
                            .strength(3.0f)
                            .noLootTable()
    );

    /*
     * =========================================
     * BLOCK ENTITY ГОРОДСКОГО ПИЛОНА
     * =========================================
     *
     * Нужен только для рендера восьми анимируемых частей модели
     * и для синхронизации одного флага активности на клиент.
     * Игровая логика остаётся в CityManager / City.
     */

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CityPylonBlockEntity>>
            CITY_PYLON_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "city_pylon",
                    () -> BlockEntityType.Builder.of(
                            CityPylonBlockEntity::new,
                            CITY_PYLON.get()
                    ).build(null)
            );

    public static final DeferredItem<CityPylonItem>
            CITY_PYLON_ITEM =
            ITEMS.registerItem(
                    "city_pylon",
                    properties -> new CityPylonItem(CITY_PYLON.get(), properties),
                    new Item.Properties()
            );
    public static final DeferredItem<MineControllerItem> MINE_CONTROLLER_ITEM =
            ITEMS.registerItem(
                    "mine_controller",
                    properties -> new MineControllerItem(
                            MINE_CONTROLLER.get(),
                            properties
                    ),
                    new Item.Properties()
            );
    public static final DeferredBlock<MineFrameBlock> MINE_FRAME =
            BLOCKS.register(
                    "mine_frame",
                    () -> new MineFrameBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                    )
            );
    public static final DeferredBlock<DrillUnitBlock> DRILL_UNIT =
            BLOCKS.register(
                    "drill_unit",
                    () -> new DrillUnitBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                    )
            );
    public static final DeferredBlock<TechnicalBlock> TECHNICAL_BLOCK =
            BLOCKS.register(
                    "technical_block",
                    () -> new TechnicalBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                    )
            );
    public static final DeferredBlock<ControlBlock> CONTROL_BLOCK =
            BLOCKS.register(
                    "control_block",
                    () -> new ControlBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                    )
            );

    /*
     * =========================================
     * ЖЕЛЕЗОБЕТОН
     * =========================================
     *
     * Массовый строительный материал.
     * Предназначен для тысячного применения:
     * инфраструктура, станции, мосты, заводы,
     * City Projects, Public Works Master City.
     */

    public static final DeferredBlock<Block> REINFORCED_CONCRETE =
            BLOCKS.registerSimpleBlock(
                    "reinforced_concrete",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(2.0F)
                            .requiresCorrectToolForDrops()
                            .sound(net.minecraft.world.level.block.SoundType.STONE)
            );
    public static final DeferredItem<BlockItem> REINFORCED_CONCRETE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "reinforced_concrete",
                    REINFORCED_CONCRETE
            );
    public static final DeferredBlock<MineGatewayBlock> MINE_GATEWAY =
            BLOCKS.register(
                    "mine_gateway",
                    () -> new MineGatewayBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                    )
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<MineGatewayBlockEntity>
            > MINE_GATEWAY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mine_gateway",
                    () -> BlockEntityType.Builder.of(
                            MineGatewayBlockEntity::new,
                            MINE_GATEWAY.get()
                    ).build(null)
            );

    public static final DeferredBlock<com.example.examplemod.economy.block.ContractIntakeBlock> CONTRACT_INTAKE =
            BLOCKS.register("contract_intake", () -> new com.example.examplemod.economy.block.ContractIntakeBlock(
                    BlockBehaviour.Properties.of().strength(3.5F).mapColor(MapColor.METAL).noLootTable()));
    public static final DeferredBlock<com.example.examplemod.economy.block.WarehouseGatewayBlock> WAREHOUSE_GATEWAY =
            BLOCKS.register("warehouse_gateway", () -> new com.example.examplemod.economy.block.WarehouseGatewayBlock(
                    BlockBehaviour.Properties.of().strength(3.5F).mapColor(MapColor.METAL).noLootTable()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.example.examplemod.economy.block.EconomyThroughputBlockEntity>>
            ECONOMY_THROUGHPUT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "economy_throughput", () -> BlockEntityType.Builder.of(
                    com.example.examplemod.economy.block.EconomyThroughputBlockEntity::new,
                    CONTRACT_INTAKE.get(), WAREHOUSE_GATEWAY.get()).build(null));
    public static final DeferredItem<BlockItem> CONTRACT_INTAKE_ITEM = ITEMS.registerSimpleBlockItem(
            "contract_intake", CONTRACT_INTAKE);
    public static final DeferredItem<BlockItem> WAREHOUSE_GATEWAY_ITEM = ITEMS.registerSimpleBlockItem(
            "warehouse_gateway", WAREHOUSE_GATEWAY);

    /*
     * =========================================
     * EXAMPLE ITEM
     * =========================================
     */
    public static final DeferredItem<Item> MINE_FRAME_ITEM =
            ITEMS.registerItem(
                    "mine_frame",
                    properties -> new BlockItem(
                            MINE_FRAME.get(),
                            properties
                    ),
                    new Item.Properties()
            );
    public static final DeferredItem<Item> DRILL_UNIT_ITEM =
            ITEMS.registerItem(
                    "drill_unit",
                    properties -> new BlockItem(
                            DRILL_UNIT.get(),
                            properties
                    ),
                    new Item.Properties()
            );
    public static final DeferredItem<Item> TECHNICAL_BLOCK_ITEM =
            ITEMS.registerItem(
                    "technical_block",
                    properties -> new BlockItem(
                            TECHNICAL_BLOCK.get(),
                            properties
                    ),
                    new Item.Properties()
            );
    public static final DeferredItem<Item> CONTROL_BLOCK_ITEM =
            ITEMS.registerItem(
                    "control_block",
                    properties -> new BlockItem(
                            CONTROL_BLOCK.get(),
                            properties
                    ),
                    new Item.Properties()
            );
    public static final DeferredItem<Item> MINE_GATEWAY_ITEM =
            ITEMS.registerItem(
                    "mine_gateway",
                    properties -> new BlockItem(
                            MINE_GATEWAY.get(),
                            properties
                    ),
                    new Item.Properties()
            );

    /*
     * =========================================
     * EXAMPLE CREATIVE TAB
     * =========================================
     */



    /*
     * =========================================
     * CONSTRUCTOR
     * =========================================
     */

    public CreateCityExpansionCore(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {
        /*
         * Принудительная инициализация класса промышленных
         * материалов.
         *
         * IndustrialMaterials объявляет DeferredHolder'ы в
         * статических полях, а обращается к нему только
         * отложенные supplier'ы (creative tab, рецепты).
         * Без явного касания класса JVM не выполнит его
         * <clinit>, регистрация предметов не произойдёт,
         * и рецепты упадут с «Unknown registry key».
         */
        IndustrialMaterials.INDUSTRIAL_COKE.getClass();

        ModElectricalDevices.register(modEventBus);
        modEventBus.addListener(
                this::commonSetup
        );

        ModSounds.SOUNDS.register(modEventBus);


        modEventBus.addListener(
                this::registerPayloads
        );
        modEventBus.addListener(this::registerCapabilities);

        BLOCKS.register(
                modEventBus
        );

        ITEMS.register(
                modEventBus
        );

        CREATIVE_MODE_TABS.register(
                modEventBus
        );

        MENUS.register(
                modEventBus
        );

        BLOCK_ENTITY_TYPES.register(
                modEventBus
        );
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.register(
                CityProtectionEvents.class

        );

        modEventBus.addListener(
                this::addCreative
        );

        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                Config.SPEC
        );

    }

    private void registerCommands(RegisterCommandsEvent event) {
        GeologyCommand.register(event.getDispatcher());
        com.example.examplemod.economy.EconomyCommands.register(event.getDispatcher());
        /*
         * =========================================
         * CLEAR CITY
         * =========================================
         */
        event.getDispatcher().register(
                Commands.literal("clearcity")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {

                            ServerPlayer player =
                                    context.getSource().getPlayerOrException();

                            ServerLevel overworld =
                                    player
                                            .serverLevel()
                                            .getServer()
                                            .overworld();

                            CityManager cityManager =
                                    CityManager.get(overworld);

                            City city =
                                    cityManager.removeCityByOwner(
                                            player.getUUID()
                                    );

                            if (city == null) {

                                player.sendSystemMessage(
                                        Component.literal(
                                                "У тебя нет города."
                                        )
                                );

                                return 0;
                            }

                            RailwayManager.get(overworld).removeCity(city.getId());

                            player.sendSystemMessage(
                                    Component.literal(
                                            "Город «"
                                                    + city.getName()
                                                    + "» удалён."
                                    )
                            );

                            return 1;
                        })
        );

        /*
         * =========================================
         * TEST INVITE
         * =========================================
         */

        event.getDispatcher().register(
                Commands.literal("testinvite")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {

                            ServerPlayer player =
                                    context.getSource().getPlayerOrException();

                            ServerLevel overworld =
                                    player.serverLevel()
                                            .getServer()
                                            .overworld();

                            CityManager cityManager =
                                    CityManager.get(overworld);

                            City city =
                                    cityManager.getCityByMember(
                                            player.getUUID()
                                    );

                            /*
                             * Игрок не состоит ни в одном городе
                             */

                            if (city == null) {

                                player.sendSystemMessage(
                                        Component.literal(
                                                "§cВы не состоите ни в одном городе."
                                        )
                                );

                                return 0;
                            }

                            /*
                             * Создаём тестовое приглашение.
                             *
                             * Здесь намеренно не используем
                             * city.addInvite(), потому что обычная
                             * система запрещает приглашать владельца
                             * собственного города.
                             */

                            if (!city.hasInvite(player.getUUID())) {

                                city.getInvites().add(
                                        new CityInvite(
                                                player.getUUID()
                                        )
                                );

                                cityManager.setDirty();
                            }

                            /*
                             * Отправляем приглашение клиенту
                             */

                            PacketDistributor.sendToPlayer(
                                    player,
                                    new CityInviteNotificationPayload(
                                            city.getPylonPos(),
                                            city.getName(),
                                            player.getName().getString()
                                    )
                            );

                            return 1;
                        })
        );
    }
    /*
     * =========================================
     * COMMON SETUP
     * =========================================
     */

    private void commonSetup(
            FMLCommonSetupEvent event
    ) {

        LOGGER.info(
                "HELLO FROM COMMON SETUP"
        );

        if (
                Config.LOG_DIRT_BLOCK
                        .getAsBoolean()
        ) {

            LOGGER.info(
                    "DIRT BLOCK >> {}",
                    BuiltInRegistries.BLOCK
                            .getKey(
                                    Blocks.DIRT
                            )
            );
        }

        LOGGER.info(
                "{}{}",
                Config.MAGIC_NUMBER_INTRODUCTION
                        .get(),
                Config.MAGIC_NUMBER
                        .getAsInt()
        );

        Config.ITEM_STRINGS
                .get()
                .forEach(
                        item ->
                                LOGGER.info(
                                        "ITEM >> {}",
                                        item
                                )
                );
    }

    /*
     * =========================================
     * CREATIVE TAB
     * =========================================
     */

    private void addCreative(
            BuildCreativeModeTabContentsEvent event
    ) {

        /*
         * Все предметы мода вынесены в собственный
         * creative tab «createcityexpansioncore:industrial»
         * (см. IndustrialCreativeTab).
         *
         * В ванильные вкладки они намеренно не добавляются,
         * чтобы не было дублей и чтобы порядок
         * соответствовал производственной иерархии.
         */
    }

    /*
     * =========================================
     * NETWORK
     * =========================================
     */

    private void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {

        PayloadRegistrar registrar =
                event.registrar("1");

        registrar.playToServer(
                com.example.examplemod.network.EconomyCommandPayload.TYPE,
                com.example.examplemod.network.EconomyCommandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.example.examplemod.economy.EconomyNetworkHandlers.handle(player, payload);
                    }
                })
        );
        registrar.playToClient(
                com.example.examplemod.network.EconomyStatePayload.TYPE,
                com.example.examplemod.network.EconomyStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) {
                        menu.applyEconomyState(payload);
                    }
                })
        );
        registrar.playToServer(
                com.example.examplemod.network.InfrastructureCommandPayload.TYPE,
                com.example.examplemod.network.InfrastructureCommandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.example.examplemod.economy.InfrastructureNetworkHandlers.handle(player, payload);
                    }
                })
        );
        registrar.playToClient(
                com.example.examplemod.network.InfrastructureStatePayload.TYPE,
                com.example.examplemod.network.InfrastructureStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof com.example.examplemod.menu.EconomyInfrastructureMenu menu) {
                        menu.applyState(payload);
                    }
                })
        );

        registrar.playToServer(
                RailwayCommandPayload.TYPE,
                RailwayCommandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RailwayNetworkHandlers.handle(player, payload);
                    }
                })
        );

        registrar.playToClient(
                RailwayStatePayload.TYPE,
                RailwayStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) {
                        menu.applyRailwayState(payload);
                    }
                })
        );

        registrar.playToClient(
                RailwayMapPayload.TYPE,
                RailwayMapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) {
                        menu.acceptRailwayMap(payload);
                    }
                })
        );

        registrar.playToServer(
                RequestCityMapTilePayload.TYPE,
                RequestCityMapTilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CityMapNetworkHandlers.requestTile(player, payload);
                    }
                })
        );

        registrar.playToServer(
                CommissionMineProjectPayload.TYPE,
                CommissionMineProjectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MineNetworkHandlers.commission(
                                player,
                                payload.controllerPos(),
                                payload.cityId(),
                                payload.depositId()
                        );
                    }
                })
        );

        registrar.playToServer(
                DecommissionCityProjectPayload.TYPE,
                DecommissionCityProjectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CityProjectNetworkHandlers.decommission(
                                player, payload.pylonPos(), payload.projectId()
                        );
                    }
                })
        );

        registrar.playToServer(
                CityDevelopmentPayload.TYPE,
                CityDevelopmentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CityProjectNetworkHandlers.development(
                                player, payload.pylonPos(), payload.action()
                        );
                    }
                })
        );

        registrar.playToClient(
                CityPylonStatePayload.TYPE,
                CityPylonStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) {
                        menu.applyState(payload);
                    }
                })
        );

        registrar.playToClient(
                CityMapTilePayload.TYPE,
                CityMapTilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) {
                        menu.acceptMapTile(payload);
                    }
                })
        );

        registrar.playToServer(
                SelectMineDepositPayload.TYPE,
                SelectMineDepositPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MineNetworkHandlers.selectDeposit(
                                player,
                                payload.controllerPos(),
                                payload.depositId()
                        );
                    }
                })
        );

        registrar.playToServer(
                MineControlPayload.TYPE,
                MineControlPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        MineNetworkHandlers.control(
                                player,
                                payload.controllerPos(),
                                payload.command()
                        );
                    }
                })
        );
        /*
         * =========================================
         * СИНХРОНИЗАЦИЯ РОЛИ НА КЛИЕНТ
         * =========================================
         */

        registrar.playToClient(
                SyncCityMemberRolePayload.TYPE,
                SyncCityMemberRolePayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        /*
                         * Получаем открытое меню игрока
                         */

                        if (
                                !(context.player()
                                        .containerMenu
                                        instanceof CityPylonMenu menu)
                        ) {

                            return;
                        }

                        /*
                         * Проверяем, что это именно
                         * тот пилон, для которого пришёл пакет.
                         */

                        if (
                                !menu.getPylonPos()
                                        .equals(
                                                payload.pylonPos()
                                        )
                        ) {

                            return;
                        }

                        /*
                         * Проверяем ID роли.
                         */

                        CityMember.Role[] roles =
                                CityMember.Role.values();

                        int roleId =
                                payload.roleId();

                        if (
                                roleId < 0
                                        || roleId >= roles.length
                        ) {

                            return;
                        }

                        CityMember.Role role =
                                roles[roleId];

                        /*
                         * Обновляем роль игрока
                         * в открытом меню.
                         */

                        menu.updateMemberRole(
                                payload.playerId(),
                                role
                        );
                    });
                }
        );
        /*
         * =====================================
         * СОЗДАНИЕ ГОРОДА
         * =====================================
         */

        registrar.playToServer(
                CreateCityPayload.TYPE,
                CreateCityPayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        if (
                                !(context.player()
                                        instanceof ServerPlayer serverPlayer)
                        ) {

                            return;
                        }

                        String name =
                                payload.name();

                        if (
                                name == null
                                        || name.isBlank()
                        ) {

                            return;
                        }

                        BlockPos pylonPos =
                                payload.pylonPos();

                        ServerLevel pylonLevel = serverPlayer.serverLevel();
                        ServerLevel overworld = pylonLevel.getServer().overworld();

                        /*
                         * Проверяем пилон
                         */

                        if (
                                !pylonLevel
                                        .getBlockState(
                                                pylonPos
                                        )
                                        .is(
                                                CITY_PYLON.get()
                                        )
                        ) {

                            return;
                        }

                        /*
                         * Проверяем расстояние
                         */

                        if (
                                serverPlayer
                                        .distanceToSqr(
                                                pylonPos.getX()
                                                        + 0.5,
                                                pylonPos.getY()
                                                        + 0.5,
                                                pylonPos.getZ()
                                                        + 0.5
                                        )
                                        > 64 * 64
                        ) {

                            return;
                        }

                        CityManager cityManager =
                                CityManager.get(
                                        overworld
                                );

                        UUID playerId =
                                serverPlayer.getUUID();

                        /*
                         * Игрок уже состоит
                         * в каком-либо городе.
                         */

                        if (
                                cityManager
                                        .getCityByMember(
                                                playerId
                                        ) != null
                        ) {

                            return;
                        }

                        /*
                         * Создаём город
                         */

                        City city =
                                cityManager.createCity(
                                        name.trim(),
                                        playerId,
                                        pylonLevel.dimension().location(),
                                        pylonPos
                                );

                        /*
                         * createCity выставляет pylonActive = true прямо в
                         * модели города, поэтому визуальный флаг на
                         * BlockEntity нужно обновить отдельно — иначе лампы
                         * пилона останутся тёмными до перезагрузки чанка.
                         */
                        CityPylonBlock.refreshActive(
                                pylonLevel,
                                pylonPos
                        );

                        LOGGER.info(
                                "Created city '{}' for player {}",
                                city.getName(),
                                serverPlayer
                                        .getName()
                                        .getString()
                        );

                        /*
                         * Редкое значимое событие: основан город.
                         * Раздаётся сервером nearby-игрокам,
                         * отдельный пакет не нужен.
                         */
                        WorldSoundHelper.playAt(
                                pylonLevel,
                                pylonPos,
                                ModSounds.CITY_CREATE.get(),
                                1.0F,
                                1.0F
                        );
                    });
                }
        );
        registrar.playToServer(
                RemoveCityMemberPayload.TYPE,
                RemoveCityMemberPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {

                        if (!(context.player() instanceof ServerPlayer sender)) {
                            return;
                        }

                        ServerLevel level = sender.serverLevel();

                        City city = CityManager.get(level.getServer().overworld())
                                .getCityByPylon(level.dimension().location(), payload.pylonPos());

                        if (city == null) {
                            return;
                        }

                        UUID targetId = payload.targetPlayer();

                        // Отправитель должен быть OWNER или ADMIN
                        if (!city.isAdmin(sender.getUUID())) {
                            return;
                        }

                        // Нельзя исключить самого себя
                        if (sender.getUUID().equals(targetId)) {
                            return;
                        }

                        // Проверяем, что игрок действительно состоит в городе
                        CityMember targetMember = city.getMember(targetId);

                        if (targetMember == null) {
                            return;
                        }

                        // Владельца удалить нельзя
                        if (city.isOwner(targetId)) {
                            return;
                        }

                        /*
                         * OWNER:
                         * может исключать ADMIN, BUILDER и RESIDENT.
                         *
                         * ADMIN:
                         * может исключать только BUILDER и RESIDENT.
                         * Другого ADMIN исключить нельзя.
                         */
                        if (targetMember.isAdmin()
                                && !city.isOwner(sender.getUUID())) {
                            return;
                        }

                        // Исключаем игрока
                        city.removeMember(targetId);

                        // Сохраняем изменения
                        CityManager.get(level).setDirty();
                        SyncCityMemberRemovalPayload syncPayload =
                                new SyncCityMemberRemovalPayload(
                                        city.getPylonPos(),
                                        targetId
                                );

                        for (ServerPlayer player : level.getServer()
                                .getPlayerList()
                                .getPlayers()) {

                            if (player.containerMenu
                                    instanceof CityPylonMenu menu
                                    && menu.getPylonPos()
                                    .equals(city.getPylonPos())) {

                                PacketDistributor.sendToPlayer(
                                        player,
                                        syncPayload
                                );
                            }
                        }
                        // Уведомление инициатору
                        sender.connection.send(
                                new ClientboundCustomPayloadPacket(
                                        new CityNotificationPayload(
                                                "ГОРОД",
                                                "Игрок «"
                                                        + targetId
                                                        + "» исключён из города."
                                        )
                                )
                        );

                        // Если игрок онлайн — уведомляем его тоже
                        ServerPlayer targetPlayer =
                                sender.server.getPlayerList().getPlayer(targetId);

                        if (targetPlayer != null) {
                            targetPlayer.connection.send(
                                    new ClientboundCustomPayloadPacket(
                                            new CityNotificationPayload(
                                                    "ГОРОД",
                                                    "Вы были исключены из города «"
                                                            + city.getName()
                                                            + "»."
                                            )
                                    )
                            );
                        }
                    });
                }
        );
        /*
         * =====================================
         * ИЗМЕНЕНИЕ РОЛИ
         * =====================================
         */

        registrar.playToServer(
                ChangeCityRolePayload.TYPE,
                ChangeCityRolePayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        if (
                                !(context.player()
                                        instanceof ServerPlayer serverPlayer)
                        ) {

                            return;
                        }

                        ServerLevel overworld =
                                serverPlayer
                                        .serverLevel()
                                        .getServer()
                                        .overworld();

                        CityManager cityManager =
                                CityManager.get(
                                        overworld
                                );

                        City city =
                                cityManager.getCityByPylon(
                                        serverPlayer.serverLevel().dimension().location(),
                                        payload.pylonPos()
                                );

                        if (
                                city == null
                        ) {

                            return;
                        }

                        UUID senderId =
                                serverPlayer.getUUID();

                        /*
                         * Только OWNER / ADMIN
                         */

                        if (
                                !city.isAdmin(senderId)
                        ) {

                            return;
                        }

                        UUID targetId =
                                payload.targetPlayer();

                        /*
                         * Нельзя менять свою роль
                         */

                        if (
                                senderId.equals(
                                        targetId
                                )
                        ) {

                            return;
                        }

                        /*
                         * Целевой игрок должен
                         * существовать в городе.
                         */

                        if (
                                !city.hasMember(targetId)
                        ) {

                            return;
                        }

                        int roleId =
                                payload.roleId();

                        CityMember.Role[] roles =
                                CityMember.Role.values();

                        if (
                                roleId < 0
                                        || roleId >= roles.length
                        ) {

                            return;
                        }

                        CityMember.Role newRole =
                                roles[roleId];

                        /*
                         * OWNER нельзя назначить
                         * через эту систему.
                         */

                        if (
                                newRole
                                        == CityMember.Role.OWNER
                        ) {

                            return;
                        }

                        /*
                         * Нельзя менять владельца.
                         */

                        if (
                                city.isOwner(targetId)
                        ) {

                            return;
                        }

                        /*
                         * ADMIN может назначать ADMIN
                         * только если сам OWNER.
                         */

                        if (
                                newRole
                                        == CityMember.Role.ADMIN
                                        && !city.isOwner(
                                        senderId
                                )
                        ) {

                            return;
                        }

                        /*
                         * ADMIN не может менять
                         * другого ADMIN.
                         */

                        if (
                                city.isAdmin(targetId)
                                        && !city.isOwner(
                                        senderId
                                )
                        ) {

                            return;
                        }

                        city.setMemberRole(
                                targetId,
                                newRole
                        );

                        cityManager.setDirty();

                        /*
                         * Синхронизируем
                         * отправителю.
                         */

                        PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new SyncCityMemberRolePayload(
                                        payload.pylonPos(),
                                        targetId,
                                        newRole.ordinal()
                                )
                        );

                        /*
                         * И самому игроку,
                         * если он онлайн.
                         */

                        ServerPlayer target =
                                serverPlayer
                                        .getServer()
                                        .getPlayerList()
                                        .getPlayer(
                                                targetId
                                        );

                        if (
                                target != null
                        ) {

                            PacketDistributor.sendToPlayer(
                                    target,
                                    new SyncCityMemberRolePayload(
                                            payload.pylonPos(),
                                            targetId,
                                            newRole.ordinal()
                                    )
                            );
                        }
                    });
                }
        );

        /*
         * =====================================
         * ПРИГЛАШЕНИЕ
         * =====================================
         */

        registrar.playToServer(
                InvitePlayerPayload.TYPE,
                InvitePlayerPayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        if (
                                !(context.player()
                                        instanceof ServerPlayer serverPlayer)
                        ) {

                            return;
                        }

                        ServerLevel overworld =
                                serverPlayer
                                        .serverLevel()
                                        .getServer()
                                        .overworld();

                        CityManager cityManager =
                                CityManager.get(
                                        overworld
                                );

                        City city =
                                cityManager.getCityByPylon(
                                        serverPlayer.serverLevel().dimension().location(),
                                        payload.pylonPos()
                                );

                        if (
                                city == null
                        ) {

                            return;
                        }

                        UUID senderId =
                                serverPlayer.getUUID();

                        /*
                         * Только OWNER / ADMIN
                         */

                        if (
                                !city.isAdmin(
                                        senderId
                                )
                        ) {

                            return;
                        }

                        UUID targetId =
                                payload.targetPlayer();

                        /*
                         * Нельзя пригласить себя.
                         */

                        if (
                                targetId.equals(
                                        senderId
                                )
                        ) {

                            return;
                        }

                        /*
                         * Игрок уже в этом городе.
                         */

                        if (
                                city.hasMember(
                                        targetId
                                )
                        ) {

                            return;
                        }

                        /*
                         * Игрок уже состоит
                         * в ДРУГОМ городе.
                         */

                        if (
                                cityManager
                                        .getCityByMember(
                                                targetId
                                        ) != null
                        ) {

                            return;
                        }

                        /*
                         * Игрок должен быть онлайн.
                         */

                        ServerPlayer target =
                                serverPlayer
                                        .getServer()
                                        .getPlayerList()
                                        .getPlayer(
                                                targetId
                                        );

                        if (
                                target == null
                        ) {

                            return;
                        }

                        /*
                         * Создаём приглашение.
                         */

                        city.addInvite(
                                targetId
                        );

                        cityManager.setDirty();

                        /*
                         * Отправляем приглашение.
                         */

                        PacketDistributor.sendToPlayer(
                                target,
                                new CityInviteNotificationPayload(
                                        city.getPylonPos(),
                                        city.getName(),
                                        serverPlayer
                                                .getName()
                                                .getString()
                                )
                        );
                    });
                }
        );
        /*
         * =====================================
         * ОТВЕТ НА ПРИГЛАШЕНИЕ
         * =====================================
         */

        registrar.playToServer(
                RespondCityInvitePayload.TYPE,
                RespondCityInvitePayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        ServerPlayer player =
                                (ServerPlayer) context.player();

                        ServerLevel overworld =
                                player.serverLevel()
                                        .getServer()
                                        .overworld();

                        CityManager cityManager =
                                CityManager.get(overworld);

                        City city =
                                cityManager.getCityByPylon(
                                        player.serverLevel().dimension().location(),
                                        payload.pylonPos()
                                );

                        /*
                         * Город не найден
                         */

                        if (city == null) {

                            PacketDistributor.sendToPlayer(
                                    player,
                                    new CityNotificationPayload(
                                            "ПРИГЛАШЕНИЕ",
                                            "Город больше не существует."
                                    )
                            );

                            return;
                        }

                        /*
                         * Приглашения у игрока нет
                         */

                        if (!city.hasInvite(player.getUUID())) {

                            PacketDistributor.sendToPlayer(
                                    player,
                                    new CityNotificationPayload(
                                            "ПРИГЛАШЕНИЕ",
                                            "Это приглашение больше недействительно."
                                    )
                            );

                            return;
                        }

                        /*
                         * ОТКЛОНИТЬ
                         */

                        if (!payload.accept()) {

                            city.removeInvite(
                                    player.getUUID()
                            );

                            cityManager.setDirty();

                            PacketDistributor.sendToPlayer(
                                    player,
                                    new CityNotificationPayload(
                                            "ПРИГЛАШЕНИЕ",
                                            "Вы отклонили приглашение в город."
                                    )
                            );

                            return;
                        }

                        /*
                         * ПРИНЯТЬ
                         *
                         * Проверяем, не состоит ли игрок
                         * уже в другом городе.
                         */

                        City currentCity =
                                cityManager.getCityByMember(
                                        player.getUUID()
                                );

                        if (currentCity != null) {

                            PacketDistributor.sendToPlayer(
                                    player,
                                    new CityNotificationPayload(
                                            "ПРИГЛАШЕНИЕ",
                                            "Вы уже находитесь в городе."
                                    )
                            );

                            return;
                        }

                        /*
                         * Принимаем приглашение
                         */

                        city.acceptInvite(
                                player.getUUID()
                        );

                        cityManager.setDirty();

                        /*
                         * Уведомление об успешном принятии
                         */

                        PacketDistributor.sendToPlayer(
                                player,
                                new CityNotificationPayload(
                                        "ГОРОД",
                                        "Вы приняли приглашение и стали жителем города."
                                )
                        );
                    });
                }
        );
        /*
         * =========================================
         * S2C PAYLOADS (Server -> Client)
         * =========================================
         *
         * Эти пейлоиды отправляются сервером клиенту.
         * На клиенте обработчики заменяются на реальные в CreateCityExpansionCoreClient.
         * На сервере регистрируем no-op handler, чтобы тип пейлоада был известен
         * при NeoForge handshake (dedicated server должен знать о каналах).
         */

        registrar.playToClient(
                GeologyAnalysisPayload.TYPE,
                GeologyAnalysisPayload.STREAM_CODEC,
                (payload, context) -> GEOLOGY_ANALYSIS_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                MineControllerSnapshotPayload.TYPE,
                MineControllerSnapshotPayload.STREAM_CODEC,
                (payload, context) -> MINE_CONTROLLER_SNAPSHOT_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                MineCommissionContextPayload.TYPE,
                MineCommissionContextPayload.STREAM_CODEC,
                (payload, context) -> MINE_COMMISSION_CONTEXT_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                CityInviteNotificationPayload.TYPE,
                CityInviteNotificationPayload.STREAM_CODEC,
                (payload, context) -> CITY_INVITE_NOTIFICATION_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                CityMapPeersPayload.TYPE,
                CityMapPeersPayload.STREAM_CODEC,
                (payload, context) -> CITY_MAP_PEERS_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                SyncCityMemberRemovalPayload.TYPE,
                SyncCityMemberRemovalPayload.STREAM_CODEC,
                (payload, context) -> SYNC_CITY_MEMBER_REMOVAL_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                CityNotificationPayload.TYPE,
                CityNotificationPayload.STREAM_CODEC,
                (payload, context) -> CITY_NOTIFICATION_HANDLER.handle(payload, context)
        );
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MINE_GATEWAY_BLOCK_ENTITY.get(),
                (gateway, side) -> gateway.itemHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ECONOMY_THROUGHPUT_BLOCK_ENTITY.get(),
                (gateway, side) -> gateway.itemHandler(side)
        );
    }

    /*
     * =========================================
     * SERVER STARTING
     * =========================================
     */

    @SubscribeEvent
    public void onServerStarting(
            ServerStartingEvent event
    ) {

        LOGGER.info(
                "HELLO from server starting"
        );
    }
}
