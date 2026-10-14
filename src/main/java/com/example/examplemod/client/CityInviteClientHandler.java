package com.example.examplemod.client;

import com.example.examplemod.client.screen.CityInviteScreen;
import com.example.examplemod.network.CityInviteNotificationPayload;
import com.example.examplemod.network.CityInviteOutcomePayload;
import com.example.examplemod.network.CityInviteResultPayload;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.CityPlayerSearchPayload;
import com.example.examplemod.network.CityPlayerSearchResultPayload;
import com.example.examplemod.network.RespondCityInvitePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.BlockPos;
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
     * ПОИСК КАНДИДАТОВ (presentation state)
     * =========================================
     *
     * Здесь нет и не должно быть никакой серверной логики: только
     * то, что нужно нарисовать, плюс защита от устаревших ответов.
     */

    private static List<CityPlayerSearchResultPayload.Entry> RESULTS = List.of();

    /** Query that produced the results currently on screen. */
    private static String RESULT_QUERY = "";

    /** Most recent query the user typed; sent once nothing is in flight. */
    private static String PENDING_QUERY = null;

    /** True while one request is outstanding, so responses can never arrive out of order. */
    private static boolean IN_FLIGHT = false;

    /** Last invite outcome, rendered inline under the search field. */
    private static String FEEDBACK_KEY = "";

    private static String FEEDBACK_TARGET = "";

    private static boolean FEEDBACK_OK = false;

    /** Pylon the current search results belong to, so another city's answers can never leak in. */
    private static BlockPos RESULTS_PYLON = null;

    /**
     * Presentation state for the invite screen only. No membership decision is ever taken here:
     * accept, removal and role change each push their own authoritative payload straight to the
     * client and the pylon repaints itself, so there is deliberately no "members are stale" flag.
     */

    public static List<CityPlayerSearchResultPayload.Entry> results() {
        return RESULTS;
    }

    public static String feedback() {
        return FEEDBACK_KEY;
    }

    public static String feedbackTarget() {
        return FEEDBACK_TARGET;
    }

    public static boolean feedbackOk() {
        return FEEDBACK_OK;
    }

    public static boolean hasFeedback() {
        return !FEEDBACK_KEY.isEmpty();
    }

    public static boolean searching() {
        return IN_FLIGHT;
    }

    /** Drops everything that belongs to another city, so reopening cannot show a stale list. */
    public static void resetSearch() {
        RESULTS = List.of();
        RESULT_QUERY = "";
        PENDING_QUERY = null;
        IN_FLIGHT = false;
        FEEDBACK_KEY = "";
        FEEDBACK_TARGET = "";
        FEEDBACK_OK = false;
        RESULTS_PYLON = null;
    }

    /**
     * Asks the server for candidates. One request at a time: if a newer query is typed while one is
     * outstanding it simply replaces the pending one, so an older answer can never overwrite a newer
     * result set.
     */
    public static void requestSearch(BlockPos pylonPos, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.equals(RESULT_QUERY) && !IN_FLIGHT) {
            return;
        }
        PENDING_QUERY = normalized;
        if (IN_FLIGHT) {
            return;
        }
        send(pylonPos);
    }

    private static void send(BlockPos pylonPos) {
        if (PENDING_QUERY == null) {
            return;
        }
        IN_FLIGHT = true;
        PacketDistributor.sendToServer(new CityPlayerSearchPayload(pylonPos, PENDING_QUERY));
    }

    public static void receiveSearchResults(CityPlayerSearchResultPayload payload) {
        IN_FLIGHT = false;
        RESULTS_PYLON = payload.pylonPos();
        RESULTS = payload.entries();
        // The server echoes nothing, so the query the answers belong to is the one that was sent.
        RESULT_QUERY = PENDING_QUERY == null ? "" : PENDING_QUERY;
        // A row that just changed state on the server must not keep claiming "can invite".
        if (FEEDBACK_OK && FEEDBACK_TARGET != null) {
            RESULTS = RESULTS.stream()
                    .map(entry -> entry.playerId().equals(FEEDBACK_TARGET)
                            ? new CityPlayerSearchResultPayload.Entry(entry.playerId(), entry.name(),
                                    entry.online(), CityPlayerSearchResultPayload.State.INVITED,
                                    entry.reasonKey(), entry.cityName())
                            : entry)
                    .toList();
        }
        if (PENDING_QUERY != null && !PENDING_QUERY.equals(RESULT_QUERY)) {
            send(payload.pylonPos());
        } else {
            PENDING_QUERY = null;
        }
    }

    /** The server answered an invite: show the exact reason, never a generic error. */
    public static void receiveInviteResult(CityInviteResultPayload payload) {
        FEEDBACK_KEY = payload.reasonKey();
        FEEDBACK_TARGET = payload.targetName();
        FEEDBACK_OK = payload.sent();
        if (payload.sent()) {
            toast(Component.translatable("gui.createcityexpansioncore.city.invite.toast.sent"));
            // Reflect the new state immediately instead of waiting for the next search.
            RESULTS = RESULTS.stream()
                    .map(entry -> payload.targetName().equals(entry.name())
                            ? new CityPlayerSearchResultPayload.Entry(entry.playerId(), entry.name(),
                                    entry.online(), CityPlayerSearchResultPayload.State.INVITED,
                                    entry.reasonKey(), entry.cityName())
                            : entry)
                    .toList();
        }
    }

    /**
     * The sender learns how their invite ended. A success is never a problem, so never the Problem
     * Center.
     *
     * <p>No "members are stale" flag is kept here: accept, removal and role change each push their
     * own authoritative payload straight to the client and the pylon repaints itself, so a second
     * unread flag would only be a second thing to forget.
     */
    public static void receiveOutcome(CityInviteOutcomePayload payload) {
        String key = payload.accepted()
                ? "gui.createcityexpansioncore.city.invite.outcome.accepted"
                : "gui.createcityexpansioncore.city.invite.outcome.declined";
        toast(Component.translatable(key, payload.invitedName()));
    }

    private static void toast(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.add(
                minecraft.getToasts(),
                SystemToast.SystemToastId
                        .PERIODIC_NOTIFICATION,
                Component.translatable("gui.createcityexpansioncore.city.invites.title",
                        Component.translatable("gui.createcityexpansioncore.city.invite")),
                message);
    }

    /*
     * =========================================
     * ПОЛУЧИЛИ ПРИГЛАШЕНИЕ
     * =========================================
     */

    public static void receiveInvite(
            CityInviteNotificationPayload invite
    ) {

        // A repeated notification renews the display name and reopens the invitation.
        for (int index = 0; index < INVITES.size(); index++) {
            if (INVITES.get(index).pylonPos().equals(invite.pylonPos())) {
                INVITES.set(index, invite);
                openScreenIfNeeded();
                return;
            }
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
