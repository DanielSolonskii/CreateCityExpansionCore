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
     * CLIENT PAYLOAD HANDLERS
     * =========================================
     *
     * Устанавливаем статические делегаты в common классе.
     * Регистрация самих TYPE происходит в common классе (CreateCityExpansionCore).
     * Здесь только предоставляем реальные client-side реализации.
     * Контекст приходит как Object; вызываем enqueueWork через рефлексию,
     * чтобы не зависеть от внутренних классов NeoForge на compile-time.
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

        CreateCityExpansionCore.CITY_NOTIFICATION_HANDLER = (payload, context) -> {
            invokeEnqueueWork(context, () -> CityInviteClientHandler.receiveNotification(payload));
        };
    }

    /**
     * Вызывает enqueueWork(Runnable) на объекте контекста через рефлексию.
     * Это позволяет не зависеть от класса PacketContext на compile-time,
     * так как его может не быть на compile classpath.
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
