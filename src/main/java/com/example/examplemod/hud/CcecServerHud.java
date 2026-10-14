package com.example.examplemod.hud;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only vanilla HUD. Owns only CCEC bars; no custom payloads or persistent data. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class CcecServerHud {
    private static final String KEY = "hud.createcityexpansioncore.";
    private static final Map<MinecraftServer, State> STATES = new HashMap<>();
    private CcecServerHud() {}
    private static final class State {
        final Map<UUID, ServerBossEvent> cities = new HashMap<>();
        final ServerBossEvent maintenance = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        MaintenanceCountdown countdown;
        long nextPoll, nextTerritory;
        void clear() {
            cities.values().forEach(ServerBossEvent::removeAllPlayers);
            cities.clear();
            maintenance.removeAllPlayers();
            countdown = null;
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var maintenance = Commands.literal("maintenance").requires(source -> source.hasPermission(2));
        for (MaintenanceCountdown.Mode mode : MaintenanceCountdown.Mode.values()) {
            maintenance.then(Commands.literal(mode == MaintenanceCountdown.Mode.RESTART ? "restart" : "announce")
                    .requires(source -> source.hasPermission(requiredPermission(mode)))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MaintenanceCountdown.MAX_SECONDS))
                            .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx ->
                                    start(ctx.getSource(), mode, IntegerArgumentType.getInteger(ctx, "seconds"), StringArgumentType.getString(ctx, "reason"))))));
        }
        maintenance.then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())));
        event.getDispatcher().register(Commands.literal("ccec").then(maintenance));
    }
    public static int requiredPermission(MaintenanceCountdown.Mode mode) {
        return mode == MaintenanceCountdown.Mode.RESTART ? 4 : 2;
    }
    private static int start(CommandSourceStack source, MaintenanceCountdown.Mode mode, int seconds, String reason) {
        if (!source.hasPermission(requiredPermission(mode))) return 0;
        if (!MaintenanceCountdown.validReason(reason)) {
            source.sendFailure(Component.translatable(KEY + "invalid_reason", MaintenanceCountdown.MAX_REASON));
            return 0;
        }
        MinecraftServer server = source.getServer();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        if (state.countdown != null) {
            source.sendFailure(Component.translatable(KEY + "already_active"));
            return 0;
        }
        state.countdown = new MaintenanceCountdown(mode, seconds, reason, Util.getMillis());
        state.cities.values().forEach(bar -> bar.setVisible(false));
        updateMaintenance(state, seconds, 1f);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) continue;
            state.maintenance.addPlayer(player);
            warning(player, state.countdown, seconds);
        }
        source.sendSuccess(() -> Component.translatable(KEY + "started", MaintenanceCountdown.time(seconds)), true);
        return 1;
    }
    private static int cancel(CommandSourceStack source) {
        if (!source.hasPermission(2)) return 0;
        State state = STATES.get(source.getServer());
        if (state == null || state.countdown == null) {
            source.sendFailure(Component.translatable(KEY + "not_active"));
            return 0;
        }
        finish(source.getServer(), state, true);
        source.sendSuccess(() -> Component.translatable(KEY + "cancelled"), true);
        return 1;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        long now = Util.getMillis();
        if (now < state.nextPoll) return;
        state.nextPoll = now + 250;
        if (state.countdown != null) {
            var frame = state.countdown.poll(now);
            if (frame != null) {
                if (frame.finished()) { finish(server, state, false); return; }
                updateMaintenance(state, frame.seconds(), frame.progress());
                if (frame.warning()) {
                    for (ServerPlayer player : state.maintenance.getPlayers()) warning(player, state.countdown, frame.seconds());
                }
            }
        }
        if (now >= state.nextTerritory) {
            state.nextTerritory = now + 500;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) refreshTerritory(player, state);
        }
    }
    private static void updateMaintenance(State state, int seconds, float progress) {
        state.maintenance.setName(Component.translatable(KEY + (state.countdown.mode() == MaintenanceCountdown.Mode.RESTART ? "restart_bar" : "announce_bar"),
                MaintenanceCountdown.time(seconds)));
        state.maintenance.setProgress(progress);
    }
    private static void warning(ServerPlayer player, MaintenanceCountdown countdown, int seconds) {
        title(player, Component.translatable(KEY + (countdown.mode() == MaintenanceCountdown.Mode.RESTART ? "restart_title" : "announce_title"),
                MaintenanceCountdown.time(seconds)).withStyle(ChatFormatting.RED), Component.literal(countdown.reason()));
    }
    private static void title(ServerPlayer player, Component title, Component subtitle) {
        // Same packets as /title; subtitle first prevents the preceding notification from flashing.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
    private static void finish(MinecraftServer server, State state, boolean cancelled) {
        MaintenanceCountdown old = state.countdown;
        state.countdown = null; // Disarm before effects: there can be no later stale stop.
        state.maintenance.removeAllPlayers();
        boolean stop = !cancelled && old.mode() == MaintenanceCountdown.Mode.RESTART;
        if (stop) {
            state.clear();
            STATES.remove(server);
            // Exactly the vanilla /stop shutdown path: saves worlds and disconnects normally.
            server.halt(false);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) continue;
            title(player, Component.translatable(KEY + (cancelled ? "cancelled" : "finished")), Component.empty());
            refreshTerritory(player, state);
        }
    }
    private static void refreshTerritory(ServerPlayer player, State state) {
        if (player instanceof FakePlayer) return;
        City city = CityManager.get(player.getServer().overworld()).getCityAt(player.level().dimension().location(), player.blockPosition());
        ServerBossEvent bar = state.cities.get(player.getUUID());
        if (city == null) {
            if (bar != null) { bar.removeAllPlayers(); state.cities.remove(player.getUUID()); }
            return;
        }
        Component name = Component.literal(city.getName());
        var color = TerritoryBossColors.nearest(city.getColorRgb());
        if (bar == null) {
            bar = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.PROGRESS);
            bar.setVisible(state.countdown == null);
            state.cities.put(player.getUUID(), bar);
        }
        bar.setName(name);
        bar.setColor(color);
        bar.setVisible(state.countdown == null);
        // Respawn/reconnect may replace the ServerPlayer while keeping its UUID.
        if (!containsInstance(bar.getPlayers(), player)) { bar.removeAllPlayers(); bar.addPlayer(player); }
    }
    // Entity.equals uses numeric entity ID, which vanilla deliberately reuses on respawn.
    // Membership here must compare instances or the bar retains the obsolete ServerPlayer.
    static boolean containsInstance(java.util.Collection<?> players, Object player) {
        return players.stream().anyMatch(existing -> existing == player);
    }
    private static void arrived(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        if (state.countdown != null) {
            int remaining = state.countdown.remaining(Util.getMillis());
            if (remaining == 0) { finish(server, state, false); return; }
            updateMaintenance(state, remaining, state.countdown.progress(Util.getMillis()));
            boolean joined = !containsInstance(state.maintenance.getPlayers(), player);
            // Replace any obsolete ServerPlayer retained for the same UUID.
            for (ServerPlayer old : java.util.List.copyOf(state.maintenance.getPlayers())) {
                if (old != player && old.getUUID().equals(player.getUUID())) state.maintenance.removePlayer(old);
            }
            state.maintenance.addPlayer(player);
            if (joined) warning(player, state.countdown, remaining);
        }
        refreshTerritory(player, state);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) arrived(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) arrived(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) arrived(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = STATES.get(player.getServer());
        if (state == null) return;
        ServerBossEvent city = state.cities.remove(player.getUUID());
        if (city != null) city.removeAllPlayers();
        state.maintenance.removePlayer(player);
    }
    private static void clear(MinecraftServer server) {
        State state = STATES.remove(server);
        if (state != null) state.clear();
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(event.getServer()); }
}
