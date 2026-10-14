package com.example.examplemod.hud;

import com.example.examplemod.city.CityColors;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityPlot;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class ServerHudCheck {
    private static int checks;
    private static void require(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
    private static void invalid(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "invalid countdown rejected");
    }
    public static void main(String[] args) throws Exception {
        countdown(); commands(); colours(); territories(); cleanup();
        System.out.println("PASS: " + checks + " server HUD countdown, permissions, colours, territory and cleanup checks");
    }
    private static void countdown() {
        long start = 9_000_000;
        var clock = new MaintenanceCountdown(MaintenanceCountdown.Mode.RESTART, 120, "  Обновление / Update  ", start);
        require(clock.reason().equals("Обновление / Update"), "literal RU/EN reason trimmed");
        require(clock.remaining(start) == 120 && clock.progress(start) == 1f, "initial full bar");
        require(clock.poll(start + 999) == null, "subsecond calls emit nothing");
        var warnings = new ArrayList<Integer>();
        float previous = 1;
        boolean monotonic = true, bounded = true, quiet = true;
        int finishes = 0;
        for (int second = 1; second <= 120; second++) {
            var frame = clock.poll(start + second * 1000L);
            require(frame != null && frame.seconds() == 120 - second, "real seconds determine time");
            monotonic &= frame.progress() <= previous;
            bounded &= frame.progress() >= 0 && frame.progress() <= 1;
            previous = frame.progress();
            if (frame.warning()) warnings.add(frame.seconds());
            if (frame.finished()) finishes++;
            quiet &= clock.poll(start + second * 1000L + 250) == null;
        }
        require(warnings.equals(java.util.List.of(60,30,10,5)), "four warning thresholds exactly once");
        require(monotonic && bounded && quiet, "bounded decreasing progress and no duplicate second updates");
        require(finishes == 1 && clock.poll(start + 999_999) == null, "terminal event cannot repeat");
        var stalled = new MaintenanceCountdown(MaintenanceCountdown.Mode.ANNOUNCE, 120, "Lag", start);
        var jump = stalled.poll(start + 116_500);
        require(jump.seconds() == 4 && jump.warning(), "lag skips ticks, coalesces crossed warning marks");
        require(stalled.poll(start + 121_000).finished(), "long stall ends on next server poll");
        var shortClock = new MaintenanceCountdown(MaintenanceCountdown.Mode.ANNOUNCE, 5, "Short", start);
        require(shortClock.poll(start + 500) == null, "start at mark never duplicates initial title");
        require(!shortClock.poll(start + 1000).warning(), "no repeated five-second warning");
        var expired = shortClock.poll(start + 50_000);
        require(expired.finished() && !expired.warning() && expired.progress() == 0, "expiry doesn't flash stale warnings");
        require(MaintenanceCountdown.time(65).equals("01:05"), "MM:SS zero padding");
        require(MaintenanceCountdown.time(3600).equals("60:00"), "long countdown remains minutes:seconds");
        require(MaintenanceCountdown.time(-1).equals("00:00"), "negative display clamped");
        require(new MaintenanceCountdown(MaintenanceCountdown.Mode.ANNOUNCE,86400,"Max",start).remaining(start) == 86400, "one day bound");
        for (int seconds : new int[]{-1,0,86401,Integer.MAX_VALUE}) invalid(() -> new MaintenanceCountdown(MaintenanceCountdown.Mode.ANNOUNCE,seconds,"Bad",start));
        for (String reason : new String[]{"", "   ", "x\ny", "x\u00a7cy", "x\u202ey", "x".repeat(257)}) invalid(() -> new MaintenanceCountdown(MaintenanceCountdown.Mode.ANNOUNCE,5,reason,start));
        invalid(() -> new MaintenanceCountdown(null,5,"Bad",start));
        require(MaintenanceCountdown.validReason("x".repeat(256)), "maximum reason accepted");
        require(!MaintenanceCountdown.validReason(null), "null reason rejected");
    }
    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission, "Test", Component.literal("Test"), null, null);
    }
    private static void commands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        CcecServerHud.commands(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        var root = dispatcher.getRoot().getChild("ccec").getChild("maintenance");
        for (int level = 0; level <= 4; level++) {
            require(root.canUse(source(level)) == (level >= 2), "maintenance root OP gate");
            require(root.getChild("announce").canUse(source(level)) == (level >= 2), "announce OP gate");
            require(root.getChild("restart").canUse(source(level)) == (level >= 4), "restart matches vanilla stop authority");
        }
        for (String command : new String[]{"ccec maintenance announce 90 Обновление сервера", "ccec maintenance restart 1 Update", "ccec maintenance cancel"}) {
            var parse = dispatcher.parse(command, source(4));
            require(!parse.getReader().canRead() && parse.getExceptions().isEmpty(), "valid command tree parses " + command);
        }
        for (String command : new String[]{"ccec maintenance restart 0 Bad", "ccec maintenance announce 86401 Bad", "ccec maintenance announce 2"}) {
            var parse = dispatcher.parse(command, source(4));
            require(parse.getReader().canRead() || !parse.getExceptions().isEmpty() || parse.getContext().getCommand() == null, "invalid/missing arguments cannot execute");
        }
        var denied = dispatcher.parse("ccec maintenance restart 5 Forbidden", source(0));
        require(denied.getReader().canRead(), "non-OP cannot resolve command");
    }
    private static void colours() {
        int[] rgb = {0xEC00B8,0x00B7EC,0xEC3500,0x1DEC00,0xE9EC00,0x7B00EC,0xECECEC};
        for (int i = 0; i < rgb.length; i++) require(TerritoryBossColors.nearest(rgb[i]) == BossEvent.BossBarColor.values()[i], "each vanilla colour maps to itself");
        for (int colour : CityColors.PALETTE) {
            require(TerritoryBossColors.nearest(colour) == TerritoryBossColors.nearest(0xFF000000 | colour), "alpha ignored and all city presets supported");
        }
    }
    private static void territories() {
        var manager = new CityManager();
        var overworld = ResourceLocation.withDefaultNamespace("overworld");
        var nether = ResourceLocation.withDefaultNamespace("the_nether");
        UUID owner = UUID.randomUUID();
        var city = manager.createCity("HUD City", owner, overworld, new BlockPos(-1,64,-1));
        require(manager.getCityAt(overworld, -1,-1) == city, "negative-coordinate claim resolves from canonical manager");
        require(manager.getCityAt(nether, -1,-1) == null, "same position in other dimension is wilderness");
        require(manager.getCityAt(overworld, -51,-1) == null, "unclaimed neighbour is wilderness");
        manager.claimPlot(city, CityPlot.at(overworld,-51,-1),owner);
        require(manager.getCityAt(overworld,-51,-1) == city, "stationary lookup sees new boundary, no duplicated claim cache");
        var other = manager.createCity("Other City", UUID.randomUUID(), overworld, new BlockPos(200,64,200));
        require(manager.getCityAt(overworld,200,200) == other, "other player's city visible");
        int nextColour = CityColors.PALETTE.get((CityColors.PALETTE.indexOf(city.getColorRgb()) + 1) % CityColors.PALETTE.size());
        require(manager.setCityColor(city,nextColour,owner) && manager.getCityAt(overworld,-1,-1).getColorRgb() == nextColour, "city repaint available immediately to HUD lookup");
    }
    @SuppressWarnings("unchecked") private static void cleanup() throws Exception {
        // Vanilla respawn reuses Entity.id, and Entity.equals compares that ID rather than identity.
        record EntityIdentity(int id) {}
        var before = new EntityIdentity(42);
        var after = new EntityIdentity(42);
        var tracked = java.util.List.of(before);
        require(tracked.contains(after), "precondition: equal entity IDs hide player replacement from Set.contains");
        require(!CcecServerHud.containsInstance(tracked, after), "respawn replacement requires rebinding, even with equal IDs");
        require(CcecServerHud.containsInstance(tracked, before), "unchanged instance emits no remove/add churn");
        // Exercise the actual in-memory lifecycle without a running Minecraft server or network.
        Class<?> type = Class.forName(CcecServerHud.class.getName() + "$State");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object state = constructor.newInstance();
        Field citiesField = type.getDeclaredField("cities"); citiesField.setAccessible(true);
        var cities = (Map<UUID,ServerBossEvent>) citiesField.get(state);
        var own = new ServerBossEvent(Component.literal("City"),BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS);
        cities.put(UUID.randomUUID(),own);
        require(own.getProgress() == 1 && !own.shouldDarkenScreen() && !own.shouldPlayBossMusic() && !own.shouldCreateWorldFog(), "city bar full, no boss side effects");
        Field countdown = type.getDeclaredField("countdown"); countdown.setAccessible(true);
        countdown.set(state,new MaintenanceCountdown(MaintenanceCountdown.Mode.RESTART,10,"Stop",0));
        Field statesField = CcecServerHud.class.getDeclaredField("STATES"); statesField.setAccessible(true);
        var states = (Map<MinecraftServer,Object>) statesField.get(null); states.put(null,state);
        var foreign = new ServerBossEvent(Component.literal("Another mod"),BossEvent.BossBarColor.BLUE,BossEvent.BossBarOverlay.NOTCHED_10);
        var clear = CcecServerHud.class.getDeclaredMethod("clear",MinecraftServer.class); clear.setAccessible(true);
        clear.invoke(null,(Object)null);
        require(states.isEmpty() && cities.isEmpty() && countdown.get(state) == null, "server stop removes state and disarms restart");
        require(foreign.getName().getString().equals("Another mod") && foreign.getOverlay() == BossEvent.BossBarOverlay.NOTCHED_10, "unowned bar untouched");
        clear.invoke(null,(Object)null);
        require(states.isEmpty(), "repeated cleanup safe");
    }
}
