package com.example.examplemod.client;

import com.example.examplemod.client.screen.CityInviteScreen;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.RespondCityInvitePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class CityInviteClientHandler {

    private static final List<
            CityInviteNotificationPayload
            > INVITES =
            new ArrayList<>();

    /*
     * =========================================
     * ПОЛУЧИЛИ ПРИГЛАШЕНИЕ
     * =========================================
     */

    public static void receiveInvite(
            CityInviteNotificationPayload invite
    ) {

        boolean alreadyExists =
                INVITES.stream()
                        .anyMatch(
                                existing ->
                                        existing.pylonPos()
                                                .equals(
                                                        invite.pylonPos()
                                                )
                        );

        if (alreadyExists) {
            return;
        }

        INVITES.add(
                invite
        );

        openScreenIfNeeded();
    }

    /*
     * =========================================
     * ПОЛУЧИЛИ УВЕДОМЛЕНИЕ
     * =========================================
     */

    public static void receiveNotification(
            CityNotificationPayload notification
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        SystemToast.add(
                minecraft.getToasts(),

                SystemToast.SystemToastId
                        .PERIODIC_NOTIFICATION,

                Component.literal(
                        notification.title()
                ),

                Component.literal(
                        notification.message()
                )
        );
    }

    /*
     * =========================================
     * ОТКРЫТЬ ЭКРАН
     * =========================================
     */

    private static void openScreenIfNeeded() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.screen
                        instanceof CityInviteScreen
        ) {

            return;
        }

        if (
                INVITES.isEmpty()
        ) {

            return;
        }

        minecraft.setScreen(
                new CityInviteScreen()
        );
    }

    /*
     * =========================================
     * ТЕКУЩЕЕ ПРИГЛАШЕНИЕ
     * =========================================
     */

    public static CityInviteNotificationPayload
    getCurrentInvite() {

        if (
                INVITES.isEmpty()
        ) {

            return null;
        }

        return INVITES.get(0);
    }

    /*
     * =========================================
     * КОЛИЧЕСТВО ПРИГЛАШЕНИЙ
     * =========================================
     */

    public static int getInviteCount() {

        return INVITES.size();
    }

    /*
     * =========================================
     * ОТВЕТ НА ПРИГЛАШЕНИЕ
     * =========================================
     */

    public static void respond(
            boolean accept
    ) {

        CityInviteNotificationPayload invite =
                getCurrentInvite();

        if (
                invite == null
        ) {

            return;
        }

        PacketDistributor.sendToServer(
                new RespondCityInvitePayload(
                        invite.pylonPos(),
                        accept
                )
        );

        /*
         * Убираем локально.
         *
         * Если сервер не разрешит вступление
         * из-за другого города, сервер повторно
         * отправит приглашение.
         */

        INVITES.remove(0);

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                !INVITES.isEmpty()
        ) {

            minecraft.setScreen(
                    new CityInviteScreen()
            );

        } else {

            minecraft.setScreen(
                    null
            );
        }
    }
}