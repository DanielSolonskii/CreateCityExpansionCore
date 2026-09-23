package com.example.examplemod;

import com.example.examplemod.client.CityBoundaryRenderer;
import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.client.screen.CityPylonScreen;
import com.example.examplemod.client.screen.MagneticAnalyzerScreen;
import com.example.examplemod.client.screen.MineControllerScreen;
import com.example.examplemod.multiblock.MultiblockPreviewRenderer;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.network.SyncCityMemberRemovalPayload;
import com.example.examplemod.electrical.MineControllerBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

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
        NeoForge.EVENT_BUS.addListener(
                this::onMineControllerClick
        );
    }

    private void onMineControllerClick(RightClickBlock event) {

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getLevel().getBlockState(event.getPos()).getBlock()
                instanceof MineControllerBlock)) {
            return;
        }

        if (!event.getLevel().isClientSide()) {
            return;
        }

        BlockState state =
                event.getLevel().getBlockState(event.getPos());

        Direction facing =
                state.getValue(
                        BlockStateProperties.HORIZONTAL_FACING
                );

        MultiblockPreviewRenderer.show(
                event.getPos(),
                facing
        );

        Minecraft.getInstance().setScreen(
                new MineControllerScreen()
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
}