package com.example.examplemod;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityInvite;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityMapNetworkHandlers;
import com.example.examplemod.city.CityTerritoryHandlers;
import com.example.examplemod.city.CityInviteDirectory;
import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.city.CityProjectNetworkHandlers;
import com.example.examplemod.chunkloader.ChunkLoaderBlock;
import com.example.examplemod.chunkloader.ChunkLoaderBlockEntity;
import com.example.examplemod.electrical.*;
import com.example.examplemod.event.CityProtectionEvents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.block.CityPylonBlockEntity;
import com.example.examplemod.fluid.ModFluids;
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
import com.example.examplemod.item.MagneticAnalyzerMode;
import com.example.examplemod.item.CityPylonItem;
import com.example.examplemod.item.MineControllerItem;
import com.example.examplemod.item.AccessControlRemoteItem;
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
import com.example.examplemod.network.GeologyWideScanPayload;
import com.example.examplemod.command.GeologyCommand;
import com.example.examplemod.item.ModItems;
import com.example.examplemod.item.IndustrialMaterials;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.sound.WorldSoundHelper;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.network.ClaimCityPlotPayload;
import com.example.examplemod.network.RequestCityTerritoriesPayload;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.network.SetCityColorPayload;
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
import net.minecraft.core.component.DataComponentType;
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
import com.example.examplemod.network.CityPlayerSearchResultPayload;
import com.example.examplemod.network.CityInviteResultPayload;
import com.example.examplemod.network.CityInviteOutcomePayload;
import com.example.examplemod.network.CityPlayerSearchPayload;
import com.example.examplemod.network.SyncCityMembersPayload;
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

    /*
     * Широкий спектр анализатора: сервер отправляет контуры
     * залежей, клиентское состояние принимает overlay карты
     * (см. GeologyWideScanOverlay). До замены — no-op, чтобы
     * тип канала был известен при handshake.
     */
    public static PayloadHandler<GeologyWideScanPayload> GEOLOGY_WIDE_SCAN_HANDLER =
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

    public static PayloadHandler<CityPlayerSearchResultPayload> CITY_PLAYER_SEARCH_RESULT_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<CityInviteResultPayload> CITY_INVITE_RESULT_HANDLER =
            (payload, context) -> {};

    public static PayloadHandler<CityInviteOutcomePayload> CITY_INVITE_OUTCOME_HANDLER =
            (payload, context) -> {};

    /**
     * The authoritative member list, pushed when an invite is accepted. Removal and role changes
     * already have their own delta payloads and are deliberately not duplicated here.
     */
    public static PayloadHandler<SyncCityMembersPayload> SYNC_CITY_MEMBERS_HANDLER =
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
     * DATA COMPONENTS
     * =========================================
     */

    /*
     * Компоненты предметов. Режим спектра магнитного
     * анализатора живёт в самом предмете: переживает
     * выкладывание и перезапуск, синхронизирован в сеть
     * для тултипа.
     */
    public static final DeferredRegister<DataComponentType<?>>
            DATA_COMPONENTS =
            DeferredRegister.create(
                    Registries.DATA_COMPONENT_TYPE,
                    MODID
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MagneticAnalyzerMode>>
            ANALYZER_MODE =
            DATA_COMPONENTS.register(
                    "analyzer_mode",
                    () -> DataComponentType.<MagneticAnalyzerMode>builder()
                            .persistent(MagneticAnalyzerMode.CODEC)
                            .networkSynchronized(MagneticAnalyzerMode.STREAM_CODEC)
                            .build()
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
     * CHUNK LOADER
     * =========================================
     *
     * Инфраструктура для удалённых заводов и станций.
     * Один блок = один forced/ticking чанк, в котором он стоит.
     * Лимит владельца живёт в ChunkLoaderSavedData, тикет — в ChunkLoaderTickets.
     */

    public static final DeferredBlock<ChunkLoaderBlock> CHUNK_LOADER =
            BLOCKS.register("chunk_loader", () -> new ChunkLoaderBlock(ChunkLoaderBlock.chunkLoaderProperties()));

    public static final DeferredItem<BlockItem> CHUNK_LOADER_ITEM =
            ITEMS.registerSimpleBlockItem("chunk_loader", CHUNK_LOADER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkLoaderBlockEntity>>
            CHUNK_LOADER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("chunk_loader",
            () -> BlockEntityType.Builder.of(ChunkLoaderBlockEntity::new, CHUNK_LOADER.get()).build(null));

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
                            .icon(() -> CreateCityExpansionCore.CITY_PYLON_ITEM
                                    .get()
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
    /*
     * Пульт управления доступом: открывает один блок города для ПКМ
     * любого игрока. Выключатель — повторное использование закрывает блок.
     */
    public static final DeferredItem<Item> ACCESS_CONTROL_REMOTE =
            ITEMS.registerItem(
                    "access_control_remote",
                    AccessControlRemoteItem::new,
                    new Item.Properties().stacksTo(1)
            );
    public static final DeferredItem<Item> INCOMPLETE_ACCESS_CONTROL_REMOTE =
            ITEMS.registerItem(
                    "incomplete_access_control_remote",
                    com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new,
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

    public static final DeferredBlock<com.example.examplemod.block.TransportRegistrationTerminalBlock> TRANSPORT_REGISTRATION_TERMINAL =
            BLOCKS.register("transport_registration_terminal", () -> new com.example.examplemod.block.TransportRegistrationTerminalBlock(
                    BlockBehaviour.Properties.of().strength(3.5F).mapColor(MapColor.METAL)));
    public static final DeferredItem<BlockItem> TRANSPORT_REGISTRATION_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(
            "transport_registration_terminal", TRANSPORT_REGISTRATION_TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.example.examplemod.block.TransportRegistrationTerminalBlockEntity>>
            TRANSPORT_REGISTRATION_TERMINAL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "transport_registration_terminal",
            () -> BlockEntityType.Builder.of(
                    com.example.examplemod.block.TransportRegistrationTerminalBlockEntity::new,
                    TRANSPORT_REGISTRATION_TERMINAL.get()
            ).build(null));

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
        IndustrialMaterials.IRON_ORE_RAW.getClass();
        com.example.examplemod.cargo.CargoBlocks.init();

        com.example.examplemod.auth.AuthenticationBootstrap.setup(modEventBus, modContainer);

        ModElectricalDevices.register(modEventBus);
        ModFluids.register(modEventBus);
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

        DATA_COMPONENTS.register(
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
        NeoForge.EVENT_BUS.register(CityMapSavedData.class);
        NeoForge.EVENT_BUS.register(com.example.examplemod.knowledge.KnowledgeBaseServer.class);
        NeoForge.EVENT_BUS.register(com.example.examplemod.knowledge.KnowledgeCommands.class);
        NeoForge.EVENT_BUS.register(com.example.examplemod.event.TransportProtectionEvents.class);

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

                            City existingCity = cityManager.getCityByOwner(player.getUUID());
                            if (existingCity != null && com.example.examplemod.economy.EconomyManager.get(overworld).projects().hasActive(existingCity.getId())) {
                                player.sendSystemMessage(Component.translatable("gui.createcityexpansioncore.projects.delete_blocked"));
                                return 0;
                            }
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
                                            com.example.examplemod.auth.PlayerNames.name(player)
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
                event.registrar("3");
        registrar.playToServer(com.example.examplemod.network.CommunityRequestPayload.TYPE,
                com.example.examplemod.network.CommunityRequestPayload.STREAM_CODEC,
                (p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)com.example.examplemod.community.CommunityService.handle(player,p);}));
        registrar.playToClient(com.example.examplemod.network.CommunityStatePayload.TYPE,
                com.example.examplemod.network.CommunityStatePayload.STREAM_CODEC,
                (p,c)->c.enqueueWork(()->{com.example.examplemod.client.CommunityClient.accept(p);com.example.examplemod.client.screen.CommunityScreen.accept(p);}));
        registrar.playToClient(com.example.examplemod.network.CommunityNoticePayload.TYPE,
                com.example.examplemod.network.CommunityNoticePayload.STREAM_CODEC,
                (p,c)->c.enqueueWork(()->com.example.examplemod.client.screen.CommunityNoticeClient.accept(p)));
        registrar.playToServer(com.example.examplemod.network.SocialRequestPayload.TYPE,
                com.example.examplemod.network.SocialRequestPayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->{
                    if(context.player() instanceof ServerPlayer player) com.example.examplemod.social.SocialService.handle(player,payload);
                }));
        registrar.playToClient(com.example.examplemod.network.SocialStatePayload.TYPE,
                com.example.examplemod.network.SocialStatePayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->com.example.examplemod.client.screen.SocialScreen.accept(payload)));
        registrar.playToServer(com.example.examplemod.network.ProfileSearchPayload.TYPE,
                com.example.examplemod.network.ProfileSearchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) com.example.examplemod.profile.ProfileAccess.search(player, payload);
                }));
        registrar.playToClient(com.example.examplemod.network.ProfileDirectoryPayload.TYPE,
                com.example.examplemod.network.ProfileDirectoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.screen.PlayerProfileScreen.acceptDirectory(payload)));
        registrar.playToClient(com.example.examplemod.network.OpenProfilePayload.TYPE,
                com.example.examplemod.network.OpenProfilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.ProfileDiscoveryClient.open(payload)));
        registrar.playToServer(com.example.examplemod.network.ProfileRequestPayload.TYPE,
                com.example.examplemod.network.ProfileRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) com.example.examplemod.profile.ProfileService.handle(player, payload);
                }));
        registrar.playToClient(com.example.examplemod.network.ProfileResponsePayload.TYPE,
                com.example.examplemod.network.ProfileResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.screen.PlayerProfileScreen.accept(payload)));

        registrar.playToServer(com.example.examplemod.network.ProjectCommandPayload.TYPE,
                com.example.examplemod.network.ProjectCommandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) com.example.examplemod.projects.ProjectNetworkHandlers.handle(player,payload);
                }));
        registrar.playToClient(com.example.examplemod.network.ProjectStatePayload.TYPE,
                com.example.examplemod.network.ProjectStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.ProjectClientState.accept(payload)));

registrar.playToServer(com.example.examplemod.network.RequestCityDirectoryPayload.TYPE,
                com.example.examplemod.network.RequestCityDirectoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) com.example.examplemod.city.CityPublicAtlas.request(player, payload);
                }));
        registrar.playToServer(com.example.examplemod.network.SetCityIdentityPayload.TYPE,
                com.example.examplemod.network.SetCityIdentityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) com.example.examplemod.city.CityPublicAtlas.edit(player, payload);
                }));
        registrar.playToServer(com.example.examplemod.network.ClientTelemetryPayload.TYPE,
                com.example.examplemod.network.ClientTelemetryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {if(context.player() instanceof ServerPlayer player) com.example.examplemod.auth.ServerTelemetryBridge.client(player,payload);}));
        registrar.playToServer(com.example.examplemod.network.OnboardingRequestPayload.TYPE,
                com.example.examplemod.network.OnboardingRequestPayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)com.example.examplemod.onboarding.OnboardingServer.request(player,payload);}));
        registrar.playToClient(com.example.examplemod.network.OnboardingStatePayload.TYPE,
                com.example.examplemod.network.OnboardingStatePayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->com.example.examplemod.client.OnboardingClient.accept(payload)));
        registrar.playToClient(com.example.examplemod.network.KnowledgeAccessPayload.TYPE,
                com.example.examplemod.network.KnowledgeAccessPayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->com.example.examplemod.knowledge.KnowledgeBaseClient.acceptAccess(payload)));
        registrar.playToServer(com.example.examplemod.network.RequestCityBoundariesPayload.TYPE,
                com.example.examplemod.network.RequestCityBoundariesPayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->{if(context.player() instanceof ServerPlayer player)com.example.examplemod.city.CityBoundaryNetwork.request(player);}));
        registrar.playToClient(com.example.examplemod.network.CityBoundariesPayload.TYPE,
                com.example.examplemod.network.CityBoundariesPayload.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->com.example.examplemod.client.CityBoundaryRenderer.accept(payload)));
        registrar.playToServer(com.example.examplemod.network.RequestMinimapPayload.TYPE,
                com.example.examplemod.network.RequestMinimapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) com.example.examplemod.city.CityPublicAtlas.requestMinimap(player,payload);
                }));
        registrar.playToClient(com.example.examplemod.network.MinimapDirectoryPayload.TYPE,
                com.example.examplemod.network.MinimapDirectoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.map.MinimapClient.directory(payload.directory())));
        registrar.playToClient(com.example.examplemod.network.MapBorderPayload.TYPE,
                com.example.examplemod.network.MapBorderPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.map.MapBorderLayer.accept(payload)));
        registrar.playToClient(com.example.examplemod.network.CityDirectoryPayload.TYPE,
                com.example.examplemod.network.CityDirectoryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.CityPublicDirectory.accept(payload)));
        registrar.playToClient(com.example.examplemod.network.CityIdentityResultPayload.TYPE,
                com.example.examplemod.network.CityIdentityResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.screen.CityIdentityEditorScreen.accept(payload)));
        registrar.playToClient(com.example.examplemod.network.CityCreationResultPayload.TYPE,
                com.example.examplemod.network.CityCreationResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.example.examplemod.client.screen.CityCreationScreen.accept(payload)));

registrar.playToServer(
                com.example.examplemod.network.CityRelationsCommandPayload.TYPE,
                com.example.examplemod.network.CityRelationsCommandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.example.examplemod.city.CityRelationsNetworkHandlers.handle(player, payload);
                    }
                })
        );
        registrar.playToClient(
                com.example.examplemod.network.CityRelationsStatePayload.TYPE,
                com.example.examplemod.network.CityRelationsStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) menu.applyRelationsState(payload);
                    else CityWorldMapSession.INSTANCE.applyRelationsState(payload);
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
                    } else {
                        CityWorldMapSession.INSTANCE.applyState(payload);
                    }
                })
        );

        registrar.playToClient(
                CityMapTilePayload.TYPE,
                CityMapTilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof CityPylonMenu menu) {
                        com.example.examplemod.client.map.MinimapClient.tile(payload);
                        menu.acceptMapTile(payload);
                    } else {
                        com.example.examplemod.client.map.MinimapClient.tile(payload);
                        CityWorldMapSession.INSTANCE.acceptMapTile(payload);
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

                        java.util.function.Consumer<String> reply = result -> PacketDistributor.sendToPlayer(serverPlayer, new com.example.examplemod.network.CityCreationResultPayload(payload.pylonPos(), result));
                        String name =
                                payload.name();

                        if (
                                name == null
                                        || name.isBlank() || name.trim().length() > 32
                        ) {

                            reply.accept("invalid_name");
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

                            reply.accept("invalid_pylon");
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

                            reply.accept("too_far");
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

                            reply.accept("already_member");
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
                        if (city == null) { reply.accept("occupied"); return; }
                        reply.accept("created");
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
                        // Reuse the normal pylon opening path to send a fresh, server-owned menu.
                        // The old menu's hasCity/name fields are immutable and cannot become an overview.
                        if (serverPlayer.containerMenu instanceof com.example.examplemod.menu.CityPylonMenu openMenu
                                && openMenu.getPylonPos().equals(pylonPos)) {
                            var state = pylonLevel.getBlockState(pylonPos);
                            if (state.getBlock() instanceof CityPylonBlock pylon) {
                                pylon.useWithoutItem(state,pylonLevel,pylonPos,serverPlayer,
                                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pylonPos),
                                                net.minecraft.core.Direction.UP,pylonPos,false));
                                serverPlayer.displayClientMessage(Component.translatable("gui.createcityexpansioncore.city.founding.created"),true);
                            }
                        }
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

                        var city = CityManager.get(sender.getServer().overworld()).getCityByPylon(sender.level().dimension().location(), payload.pylonPos());
                        String result = com.example.examplemod.city.CityMembershipService.change(sender,
                                com.example.examplemod.city.CityMembershipService.Action.KICK,
                                city == null ? null : city.getId(), payload.targetPlayer(), UUID.randomUUID());
                        sender.sendSystemMessage(Component.translatable("gui.createcityexpansioncore.community.result." + result));
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

                            CityInviteDirectory.reportResult(
                                    serverPlayer,
                                    payload.pylonPos(),
                                    false,
                                    "gui.createcityexpansioncore.city.invite.state.city_gone",
                                    ""
                            );

                            return;
                        }

                        UUID targetId =
                                payload.targetPlayer();

                        String targetName =
                                CityInviteDirectory.displayName(
                                        serverPlayer.server,
                                        targetId
                                );

                        /*
                         * One authoritative verdict, computed exactly the way the search computes it,
                         * so a row that said AVAILABLE and a click that happens later can never
                         * disagree with each other. Every refusal is reported instead of swallowed.
                         */

                        if (!city.isAdmin(serverPlayer.getUUID())) {

                            CityInviteDirectory.reportResult(
                                    serverPlayer,
                                    payload.pylonPos(),
                                    false,
                                    CityInviteDirectory.NO_PERMISSION_KEY,
                                    targetName
                            );

                            return;
                        }

                        CityPlayerSearchResultPayload.State state =
                                CityInviteDirectory.stateOf(
                                        targetId.equals(
                                                serverPlayer.getUUID()
                                        ),
                                        city.hasMember(
                                                targetId
                                        ),
                                        city.hasInvite(
                                                targetId
                                        ),
                                        cityManager
                                                .getCityByMember(
                                                        targetId
                                                ) != null
                                );

                        boolean resend = state == CityPlayerSearchResultPayload.State.INVITED;
                        if (state != CityPlayerSearchResultPayload.State.AVAILABLE && !resend) {

                            CityInviteDirectory.reportResult(
                                    serverPlayer,
                                    payload.pylonPos(),
                                    false,
                                    CityInviteDirectory.reasonKey(
                                            state
                                    ),
                                    targetName
                            );

                            return;
                        }

                        /*
                         * Being offline is not a refusal. The invite is stored against the account id,
                         * so it survives a restart and is waiting on the next login; being connected
                         * only decides whether the recipient is told straight away.
                         */

                        // Renew the single pending record and attribute the response to this sender.
                        if (resend) {
                            city.removeInvite(targetId);
                        }
                        City.InviteResult result =
                                city.addInvite(
                                        targetId,
                                        serverPlayer.getUUID()
                                );

                        cityManager.setDirty();

                        ServerPlayer target =
                                serverPlayer
                                        .server
                                        .getPlayerList()
                                        .getPlayer(
                                                targetId
                                        );

                        if (target != null) {

                            PacketDistributor.sendToPlayer(
                                    target,
                                    new CityInviteNotificationPayload(
                                            city.getPylonPos(),
                                            city.getName(),
                                            com.example.examplemod.auth.PlayerNames.name(serverPlayer)
                                    )
                            );
                        }

                        if(result==City.InviteResult.SENT){
                            var community=com.example.examplemod.community.CommunityData.get(serverPlayer.server);
                            community.audit(city.getId(),serverPlayer.getUUID(),targetId,"invited");
                            community.enqueue(targetId,serverPlayer.getUUID(),"INVITE",city.getName(),target!=null,city.getId(),city.getInvite(targetId).token());
                        }
                        CityInviteDirectory.reportResult(
                                serverPlayer,
                                payload.pylonPos(),
                                result == City.InviteResult.SENT,
                                resend && result == City.InviteResult.SENT
                                        ? "gui.createcityexpansioncore.city.invite.state.resent"
                                        : CityInviteDirectory.reasonKey(result),
                                targetName
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
                         * Read the invite before it is consumed: this is the only place the sender is
                         * known, and they are the one who wants to hear how it ended.
                         */

                        CityInvite invite =
                                city.getInvite(
                                        player.getUUID()
                                );

                        UUID inviterId =
                                invite == null ? null : invite.getInvokerId();

                        /*
                         * ОТКЛОНИТЬ
                         */

                        if (!payload.accept()) {

                            city.removeInvite(
                                    player.getUUID()
                            );

                            cityManager.setDirty();

                            CityInviteDirectory.reportOutcome(
                                    player.server,
                                    inviterId,
                                    com.example.examplemod.auth.PlayerNames.name(player),
                                    city.getName(),
                                    false
                            );

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

                        CityInviteDirectory.reportOutcome(
                                player.server,
                                inviterId,
                                com.example.examplemod.auth.PlayerNames.name(player),
                                city.getName(),
                                true
                        );

                        /*
                         * Membership really changed here, so the inviter receives the complete
                         * authoritative list and their open pylon updates without a reopen. A decline
                         * never reaches this point, because it leaves the member list untouched.
                         */

                        CityInviteDirectory.publishMembers(
                                player.server,
                                city
                        );

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
                GeologyWideScanPayload.TYPE,
                GeologyWideScanPayload.STREAM_CODEC,
                (payload, context) -> GEOLOGY_WIDE_SCAN_HANDLER.handle(payload, context)
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

        /* City invite search, its authoritative result, the invite outcome, and the member list
         * pushed on accept. Removal and role changes keep using their own delta payloads. */

        registrar.playToServer(
                CityPlayerSearchPayload.TYPE,
                CityPlayerSearchPayload.STREAM_CODEC,
                (payload, context) -> CityInviteDirectory.handleSearch(payload, context)
        );

        registrar.playToClient(
                CityPlayerSearchResultPayload.TYPE,
                CityPlayerSearchResultPayload.STREAM_CODEC,
                (payload, context) -> CITY_PLAYER_SEARCH_RESULT_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                CityInviteResultPayload.TYPE,
                CityInviteResultPayload.STREAM_CODEC,
                (payload, context) -> CITY_INVITE_RESULT_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                CityInviteOutcomePayload.TYPE,
                CityInviteOutcomePayload.STREAM_CODEC,
                (payload, context) -> CITY_INVITE_OUTCOME_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                SyncCityMembersPayload.TYPE,
                SyncCityMembersPayload.STREAM_CODEC,
                (payload, context) -> SYNC_CITY_MEMBERS_HANDLER.handle(payload, context)
        );

        registrar.playToClient(
                CityMapPeersPayload.TYPE,
                CityMapPeersPayload.STREAM_CODEC,
                (payload, context) -> CITY_MAP_PEERS_HANDLER.handle(payload, context)
        );

        /*
         * =====================================
         * ТЕРРИТОРИЯ: УЧАСТКИ И ЦВЕТ ГОРОДА
         * =====================================
         *
         * Оба пакета — запросы, а не команды: сервер находит город по
         * пилону и проверяет права сам. Клиент не присылает ни город,
         * ни факт успеха, поэтому подделать расширение территории или
         * смену цвета на клиенте нельзя.
         */

        registrar.playToServer(
                ClaimCityPlotPayload.TYPE,
                ClaimCityPlotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CityTerritoryHandlers.claim(player, payload);
                    }
                })
        );

        registrar.playToServer(
                SetCityColorPayload.TYPE,
                SetCityColorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CityTerritoryHandlers.setColor(player, payload);
                    }
                })
        );

        /*
         * Запрос снимка территорий: экран переспрашивает его сам, когда
         * открывает карту. Одноразовая отправка при openMenu остаётся
         * быстрым первым кадром, но она ненадёжна — пакет может прийти
         * раньше экрана.
         */
        registrar.playToServer(
                RequestCityTerritoriesPayload.TYPE,
                RequestCityTerritoriesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CityTerritoryHandlers.requestTerritories(player, payload);
                    }
                })
        );

        /*
         * Карта по клавише. Запрос пустой: сервер сам определяет город по
         * членству игрока. Координата пилона в запросе была бы
         * непроверяемым вводом и позволила бы запросить чужой город.
         */
        registrar.playToServer(
                RequestCityWorldMapPayload.TYPE,
                RequestCityWorldMapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.example.examplemod.city.CityWorldMapHandlers.openForMember(player);
                    }
                })
        );

        registrar.playToClient(
                com.example.examplemod.network.CityWorldMapDeniedPayload.TYPE,
                com.example.examplemod.network.CityWorldMapDeniedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.example.examplemod.client.CityWorldMapSession.INSTANCE.deny())
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
                com.example.examplemod.cargo.CargoBlocks.ROUTER_ENTITY.get(),
                (router, side) -> router.inputHandler()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MINE_GATEWAY_BLOCK_ENTITY.get(),
                (gateway, side) -> gateway.itemHandler()
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
