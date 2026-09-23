package com.example.examplemod;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityInvite;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.electrical.*;
import com.example.examplemod.event.CityProtectionEvents;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.block.CityPylonBlock;
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
import com.example.examplemod.network.SyncCityMemberRemovalPayload;
import com.example.examplemod.city.CityMember;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.ChangeCityRolePayload;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.command.GeologyCommand;
import com.example.examplemod.item.ModItems;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.example.examplemod.network.CityNotificationPayload;
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

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import java.util.UUID;


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
                    )
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
            );

    public static final DeferredItem<BlockItem>
            CITY_PYLON_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "city_pylon",
                    CITY_PYLON
            );
    public static final DeferredItem<Item> MINE_CONTROLLER_ITEM =
            ITEMS.registerItem(
                    "mine_controller",
                    properties -> new BlockItem(
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
    public static final DeferredBlock<MineGatewayBlock> MINE_GATEWAY =
            BLOCKS.register(
                    "mine_gateway",
                    () -> new MineGatewayBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(3.0F)
                                    .mapColor(MapColor.METAL)
                    )
            );

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
        ModElectricalDevices.register(modEventBus);
        modEventBus.addListener(
                this::commonSetup
        );

        modEventBus.addListener(
                this::registerPayloads
        );

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

        if (
                event.getTabKey()
                        == CreativeModeTabs
                        .BUILDING_BLOCKS
        ) {

            event.accept(
                    EXAMPLE_BLOCK_ITEM
            );

            event.accept(
                    CITY_PYLON_ITEM
            );
        }
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

                        ServerLevel overworld =
                                serverPlayer
                                        .serverLevel()
                                        .getServer()
                                        .overworld();

                        /*
                         * Проверяем пилон
                         */

                        if (
                                !overworld
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
                                        pylonPos
                                );

                        LOGGER.info(
                                "Created city '{}' for player {}",
                                city.getName(),
                                serverPlayer
                                        .getName()
                                        .getString()
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

                        City city = CityManager.get(level)
                                .getCityByPylon(payload.pylonPos());

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