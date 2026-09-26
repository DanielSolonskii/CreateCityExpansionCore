package com.example.examplemod;

import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.client.render.CityPylonModels;
import com.example.examplemod.client.render.CityPylonRenderer;
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
import net.neoforged.neoforge.network.registration.PayloadRegistrar;


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
         * РЕГИСТРАЦИЯ ЭКРАНА ПИЛОНА
         * =========================================
         */

        modEventBus.addListener(
                this::registerScreens
        );

        /*
         * =========================================
         * CITY PYLON: RENDERER И МОДЕЛИ
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


        /*
         * =========================================
         * РЕГИСТРАЦИЯ CLIENT PAYLOADS
         * =========================================
         */

        modEventBus.addListener(
                this::registerClientPayloads
        );


        /*
         * =========================================
         * РЕГИСТРАЦИЯ РЕНДЕРЕРА ГРАНИЦ ГОРОДА
         * =========================================
         */
        NeoForge.EVENT_BUS.register(
                MultiblockPreviewRenderer.class
        );
        NeoForge.EVENT_BUS.register(
                CityBoundaryRenderer.class
        );
    }
    private void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                CreateCityExpansionCore.CITY_PYLON_BLOCK_ENTITY.get(),
                CityPylonRenderer::new
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
     * CLIENT PAYLOADS
     * =========================================
     */

    private void registerClientPayloads(
            RegisterPayloadHandlersEvent event
    ) {

        PayloadRegistrar registrar =
                event.registrar("1");

        /*
         * =========================================
         * ГЕОЛОГИЧЕСКИЙ АНАЛИЗАТОР
         * =========================================
         */

        registrar.playToClient(
                GeologyAnalysisPayload.TYPE,
                GeologyAnalysisPayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        MagneticAnalyzerScreen.open(
                                payload
                        );

                    });

                }
        );

        registrar.playToClient(
                MineControllerSnapshotPayload.TYPE,
                MineControllerSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        handleMineSnapshot(payload)
                )
        );

        registrar.playToClient(
                MineCommissionContextPayload.TYPE,
                MineCommissionContextPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        MineControllerScreen.receiveCommissionContext(payload)
                )
        );


        /*
         * =========================================
         * ПРИГЛАШЕНИЕ В ГОРОД
         * =========================================
         */

        registrar.playToClient(
                CityInviteNotificationPayload.TYPE,
                CityInviteNotificationPayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        CityInviteClientHandler.receiveInvite(
                                payload
                        );

                    });

                }
        );


        /*
         * =========================================
         * СИНХРОНИЗАЦИЯ УДАЛЕНИЯ УЧАСТНИКА
         * =========================================
         */

        registrar.playToClient(
                CityMapPeersPayload.TYPE,
                CityMapPeersPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (Minecraft.getInstance().screen instanceof CityPylonScreen screen
                            && screen.getMenu().getPylonPos().equals(payload.pylonPos())) {
                        screen.getMapPanel().receivePeers(payload.peers());
                    }
                })
        );

        registrar.playToClient(
                SyncCityMemberRemovalPayload.TYPE,
                SyncCityMemberRemovalPayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        if (Minecraft.getInstance().screen
                                instanceof CityPylonScreen screen) {

                            if (screen.getMenu()
                                    .getPylonPos()
                                    .equals(payload.pylonPos())) {

                                screen.getMenu().removeMember(
                                        payload.playerId()
                                );
                            }
                        }

                    });

                }
        );


        /*
         * =========================================
         * ОБЩЕЕ УВЕДОМЛЕНИЕ
         * =========================================
         */

        registrar.playToClient(
                CityNotificationPayload.TYPE,
                CityNotificationPayload.STREAM_CODEC,
                (payload, context) -> {

                    context.enqueueWork(() -> {

                        CityInviteClientHandler.receiveNotification(
                                payload
                        );

                    });

                }
        );
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
