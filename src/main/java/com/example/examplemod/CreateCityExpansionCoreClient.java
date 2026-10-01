package com.example.examplemod;

import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.client.CokeBatteryGuideRenderer;
import com.example.examplemod.client.BlastFurnaceGuideRenderer;
import com.example.examplemod.client.SteelConverterGuideRenderer;
import com.example.examplemod.client.BuildGuideKeyMappings;
import com.example.examplemod.client.ModFluidClientExtensions;
import com.example.examplemod.client.render.CityPylonModels;
import com.example.examplemod.client.render.CityPylonRenderer;
import com.example.examplemod.client.render.IndustrialMineModels;
import com.example.examplemod.client.render.IndustrialMineRenderer;
import com.example.examplemod.client.screen.CityPylonScreen;
import com.example.examplemod.client.screen.MagneticAnalyzerScreen;
import com.example.examplemod.client.screen.MineControllerScreen;
import com.example.examplemod.multiblock.MultiblockPreviewRenderer;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.network.MineControllerSnapshotPayload;
import com.example.examplemod.network.MineCommissionContextPayload;
import com.example.examplemod.network.CokeBatteryGuidePayload;
import com.example.examplemod.network.BlastFurnaceGuidePayload;
import com.example.examplemod.network.SyncCityMemberRemovalPayload;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;


@Mod(
        value = CreateCityExpansionCore.MODID,
        dist = Dist.CLIENT
)
public class CreateCityExpansionCoreClient {

    public CreateCityExpansionCoreClient(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {

        /*
         * =========================================
         * Ð Ð•Ð“Ð˜Ð¡Ð¢Ð ÐÐ¦Ð˜Ð¯ Ð­ÐšÐ ÐÐÐ ÐŸÐ˜Ð›ÐžÐÐ
         * =========================================
         */

        modEventBus.addListener(
                this::registerScreens
        );

        /*
         * =========================================
         * CITY PYLON: RENDERER Ð˜ ÐœÐžÐ”Ð•Ð›Ð˜
         * =========================================
         */

        modEventBus.addListener(
                this::registerRenderers
        );

        modEventBus.addListener(
                CityPylonModels::registerAdditional
        );

        modEventBus.addListener(
                CityPylonModels::onBakingCompleted
        );
        modEventBus.addListener(IndustrialMineModels::registerAdditional);
        modEventBus.addListener(IndustrialMineModels::onBakingCompleted);
        com.example.examplemod.client.render.AssembledBlockVisibility.registerModels(
                "mine_frame", "technical_block", "drill_unit", "mine_controller", "mine_power_port", "mine_gateway");
        modEventBus.addListener(com.example.examplemod.client.render.AssembledBlockVisibility::wrapModels);
        com.example.examplemod.mine.MineControllerBlockEntity.CLIENT_VISUAL_UPDATE =
                com.example.examplemod.client.render.IndustrialMineVisuals::changed;
        NeoForge.EVENT_BUS.register(com.example.examplemod.client.render.IndustrialMineVisuals.class);


        /*
         * =========================================
         * Ð Ð•Ð“Ð˜Ð¡Ð¢Ð ÐÐ¦Ð˜Ð¯ CLIENT PAYLOADS
         * =========================================
         */

        modEventBus.addListener(
                this::registerClientPayloads
        );
        modEventBus.addListener(BuildGuideKeyMappings::register);
        modEventBus.addListener(ModFluidClientExtensions::register);


        /*
         * =========================================
         * Ð Ð•Ð“Ð˜Ð¡Ð¢Ð ÐÐ¦Ð˜Ð¯ Ð Ð•ÐÐ”Ð•Ð Ð•Ð Ð Ð“Ð ÐÐÐ˜Ð¦ Ð“ÐžÐ ÐžÐ”Ð
         * =========================================
         */
        NeoForge.EVENT_BUS.register(
                MultiblockPreviewRenderer.class
        );
        NeoForge.EVENT_BUS.register(
                CityBoundaryRenderer.class
        );
        NeoForge.EVENT_BUS.register(CokeBatteryGuideRenderer.class);
        NeoForge.EVENT_BUS.register(BlastFurnaceGuideRenderer.class);
        NeoForge.EVENT_BUS.register(SteelConverterGuideRenderer.class);
    }
    private void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                CreateCityExpansionCore.CITY_PYLON_BLOCK_ENTITY.get(),
                CityPylonRenderer::new
        );
        event.registerBlockEntityRenderer(
                CreateCityExpansionCore.MINE_CONTROLLER_BLOCK_ENTITY.get(),
                IndustrialMineRenderer::new
        );
    }

    /*
     * =========================================
     * SCREENS
     * =========================================
     */

    private void registerScreens(
            RegisterMenuScreensEvent event
    ) {

        event.register(
                CreateCityExpansionCore.CITY_PYLON_MENU.get(),
                CityPylonScreen::new
        );
        event.register(
                CreateCityExpansionCore.ECONOMY_INFRASTRUCTURE_MENU.get(),
                com.example.examplemod.client.screen.EconomyInfrastructureScreen::new
        );
    }


    /*
     * =========================================
     * CLIENT PAYLOAD HANDLERS
     * =========================================
     *
     * Ð£ÑÑ‚Ð°Ð½Ð°Ð²Ð»Ð¸Ð²Ð°ÐµÐ¼ ÑÑ‚Ð°Ñ‚Ð¸Ñ‡ÐµÑÐºÐ¸Ðµ Ð´ÐµÐ»ÐµÐ³Ð°Ñ‚Ñ‹ Ð² common ÐºÐ»Ð°ÑÑÐµ.
     * Ð ÐµÐ³Ð¸ÑÑ‚Ñ€Ð°Ñ†Ð¸Ñ ÑÐ°Ð¼Ð¸Ñ… TYPE Ð¿Ñ€Ð¾Ð¸ÑÑ…Ð¾Ð´Ð¸Ñ‚ Ð² common ÐºÐ»Ð°ÑÑÐµ (CreateCityExpansionCore).
     * Ð—Ð´ÐµÑÑŒ Ñ‚Ð¾Ð»ÑŒÐºÐ¾ Ð¿Ñ€ÐµÐ´Ð¾ÑÑ‚Ð°Ð²Ð»ÑÐµÐ¼ Ñ€ÐµÐ°Ð»ÑŒÐ½Ñ‹Ðµ client-side Ñ€ÐµÐ°Ð»Ð¸Ð·Ð°Ñ†Ð¸Ð¸.
     * ÐšÐ¾Ð½Ñ‚ÐµÐºÑÑ‚ Ð¿Ñ€Ð¸Ñ…Ð¾Ð´Ð¸Ñ‚ ÐºÐ°Ðº Object; Ð²Ñ‹Ð·Ñ‹Ð²Ð°ÐµÐ¼ enqueueWork Ñ‡ÐµÑ€ÐµÐ· Ñ€ÐµÑ„Ð»ÐµÐºÑÐ¸ÑŽ,
     * Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð½Ðµ Ð·Ð°Ð²Ð¸ÑÐµÑ‚ÑŒ Ð¾Ñ‚ Ð²Ð½ÑƒÑ‚Ñ€ÐµÐ½Ð½Ð¸Ñ… ÐºÐ»Ð°ÑÑÐ¾Ð² NeoForge Ð½Ð° compile-time.
     */

    private void registerClientPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        CreateCityExpansionCore.GEOLOGY_ANALYSIS_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> MagneticAnalyzerScreen.open(payload));
        };

        CreateCityExpansionCore.MINE_CONTROLLER_SNAPSHOT_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> handleMineSnapshot(payload));
        };

        CreateCityExpansionCore.MINE_COMMISSION_CONTEXT_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> MineControllerScreen.receiveCommissionContext(payload));
        };

        CreateCityExpansionCore.COKE_BATTERY_GUIDE_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CokeBatteryGuideRenderer.toggle(payload.controllerPos()));
        };

        CreateCityExpansionCore.BLAST_FURNACE_GUIDE_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> BlastFurnaceGuideRenderer.toggle(payload.controllerPos()));
        };
        CreateCityExpansionCore.STEEL_CONVERTER_GUIDE_HANDLER = (payload, context) ->
                invokeEnqueueWork(context, () -> SteelConverterGuideRenderer.toggle(payload.controllerPos()));

        CreateCityExpansionCore.CITY_INVITE_NOTIFICATION_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CityInviteClientHandler.receiveInvite(payload));
        };

        CreateCityExpansionCore.CITY_MAP_PEERS_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> {
                if (Minecraft.getInstance().screen instanceof CityPylonScreen screen
                        && screen.getMenu().getPylonPos().equals(payload.pylonPos())) {
                    screen.getMapPanel().receivePeers(payload.peers());
                }
            });
        };

        CreateCityExpansionCore.SYNC_CITY_MEMBER_REMOVAL_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> {
                if (Minecraft.getInstance().screen instanceof CityPylonScreen screen) {
                    if (screen.getMenu().getPylonPos().equals(payload.pylonPos())) {
                        screen.getMenu().removeMember(payload.playerId());
                    }
                }
            });
        };

        CreateCityExpansionCore.CITY_PLAYER_SEARCH_RESULT_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CityInviteClientHandler.receiveSearchResults(payload));
        };

        CreateCityExpansionCore.CITY_INVITE_RESULT_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CityInviteClientHandler.receiveInviteResult(payload));
        };

        CreateCityExpansionCore.CITY_INVITE_OUTCOME_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CityInviteClientHandler.receiveOutcome(payload));
        };
        CreateCityExpansionCore.SYNC_CITY_MEMBERS_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> {
                if (Minecraft.getInstance().screen instanceof CityPylonScreen screen) {
                    screen.applyMembersSnapshot(payload);
                }
            });
        };

        CreateCityExpansionCore.CITY_NOTIFICATION_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CityInviteClientHandler.receiveNotification(payload));
        };
    }

    /**
     * Ð’Ñ‹Ð·Ñ‹Ð²Ð°ÐµÑ‚ enqueueWork(Runnable) Ð½Ð° Ð¾Ð±ÑŠÐµÐºÑ‚Ðµ ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚Ð° Ñ‡ÐµÑ€ÐµÐ· Ñ€ÐµÑ„Ð»ÐµÐºÑÐ¸ÑŽ.
     * Ð­Ñ‚Ð¾ Ð¿Ð¾Ð·Ð²Ð¾Ð»ÑÐµÑ‚ Ð½Ðµ Ð·Ð°Ð²Ð¸ÑÐµÑ‚ÑŒ Ð¾Ñ‚ ÐºÐ»Ð°ÑÑÐ° PacketContext Ð½Ð° compile-time,
     * Ñ‚Ð°Ðº ÐºÐ°Ðº ÐµÐ³Ð¾ Ð¼Ð¾Ð¶ÐµÑ‚ Ð½Ðµ Ð±Ñ‹Ñ‚ÑŒ Ð½Ð° compile classpath.
     */
    private static void invokeEnqueueWork(Object context, Runnable runnable) {
        try {
            context.getClass().getMethod("enqueueWork", Runnable.class).invoke(context, runnable);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke enqueueWork on packet context", e);
        }
    }

    private void handleMineSnapshot(MineControllerSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.openScreen()) {
            MineControllerScreen.updateIfOpen(
                    payload.snapshot(), payload.railwayStationName(), payload.railwayStationAvailable());
            return;
        }
        if (payload.snapshot().structureValid()) {
            MultiblockPreviewRenderer.hide();
            MineControllerScreen.openOrUpdate(
                    payload.snapshot(), payload.railwayStationName(), payload.railwayStationAvailable());
            return;
        }

        if (minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof MineControllerScreen) {
            minecraft.setScreen(null);
        }
        BlockState state = minecraft.level.getBlockState(
                payload.snapshot().controllerPos()
        );
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        MultiblockPreviewRenderer.show(
                payload.snapshot().controllerPos(),
                facing
        );
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            payload.snapshot().structureMessage()
                    ),
                    false
            );
        }
    }
}
