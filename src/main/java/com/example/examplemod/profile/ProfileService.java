package com.example.examplemod.profile;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.auth.PlayerNames;
import com.example.examplemod.city.*;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.network.*;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;

/** Server-thread reads only. Bounded derived caches, no new statistics ledger or economy mutations. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class ProfileService {
    private record Snapshot(long until, boolean online, List<Long> values) {}
    private static final class Cache {
        final Map<UUID, Snapshot> snapshots = new LinkedHashMap<>(32, .75f, true);
        final Map<UUID, long[]> requests = new LinkedHashMap<>(32, .75f, true);
        long missWindow; int misses;
        long projectsUntil;
        List<com.example.examplemod.projects.Project> projects = List.of();
    }
    private static final Map<MinecraftServer, Cache> CACHES = new WeakHashMap<>();
    private ProfileService() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProfileSavedData.get(player.getServer()).rememberSkin(player.getGameProfile());
            invalidate(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProfileSavedData.get(player.getServer()).rememberSkin(player.getGameProfile());
            invalidate(player);
        }
    }
    private static void invalidate(ServerPlayer player) {
        Cache cache = CACHES.get(player.getServer()); if (cache != null) cache.snapshots.remove(player.getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { CACHES.remove(event.getServer()); }
    /** Independent profile budget; opening a screen never consumes project/claim command capacity. */
    public static boolean consume(long[] state, long now) {
        long window = now / 1000;
        if (state[0] != window) { state[0] = window; state[1] = 0; }
        return state[1]++ < 6;
    }
    private static <K,V> void bound(Map<K,V> map) { while (map.size() > 256) map.remove(map.keySet().iterator().next()); }
    public static void handle(ServerPlayer viewer, ProfileRequestPayload request) {
        MinecraftServer server = viewer.getServer();
        Cache cache = CACHES.computeIfAbsent(server, ignored -> new Cache());
        long now = net.minecraft.Util.getMillis();
        long[] budget = cache.requests.computeIfAbsent(viewer.getUUID(), ignored -> new long[2]); bound(cache.requests);
        if (!consume(budget, now)) { reply(viewer, request, "busy", null); return; }
        // This check precedes target lookup, disk access and all mutations. OP cannot edit another player's settings.
        if (request.save() && !PlayerProfile.mayEdit(viewer.getUUID(), request.player())) { reply(viewer, request, "permission", null); return; }
        var data = ProfileSavedData.get(server);
        var cities = CityManager.get(server.overworld());
        City city = cities.getMemberCity(request.player());
        ServerPlayer online = server.getPlayerList().getPlayer(request.player());
        Snapshot snapshot = cache.snapshots.get(request.player());
        if (snapshot == null || snapshot.until() <= now || snapshot.online() != (online != null)) {
            long window = now / 1000;
            if (cache.missWindow != window) { cache.missWindow = window; cache.misses = 0; }
            if (++cache.misses > 16) { reply(viewer, request, "busy", null); return; }
            Path file = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(request.player() + ".json");
            boolean known = online != null || city != null || data.contains(request.player()) || Files.isRegularFile(file)
                    || Files.isRegularFile(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(request.player() + ".dat"))
                    || server.getProfileCache() != null && server.getProfileCache().get(request.player()).isPresent();
            if (!known) { reply(viewer, request, "unknown", null); return; }
            StatsCounter stats = online == null ? offline(server, file) : online.getStats();
            List<Long> values = new ArrayList<>(Collections.nCopies(PlayerProfile.Metric.values().length, PlayerProfile.UNAVAILABLE));
            if (stats != null) {
                values.set(PlayerProfile.Metric.PLAY_TIME.ordinal(), custom(stats, Stats.PLAY_TIME));
                long mined = 0; for (var block : BuiltInRegistries.BLOCK) mined += Math.max(0, stats.getValue(Stats.BLOCK_MINED, block));
                values.set(PlayerProfile.Metric.MINED.ordinal(), mined);
                long distance = 0;
                for (ResourceLocation metric : List.of(Stats.WALK_ONE_CM, Stats.CROUCH_ONE_CM, Stats.SPRINT_ONE_CM, Stats.SWIM_ONE_CM,
                        Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.FLY_ONE_CM, Stats.AVIATE_ONE_CM,
                        Stats.BOAT_ONE_CM, Stats.MINECART_ONE_CM, Stats.HORSE_ONE_CM, Stats.PIG_ONE_CM, Stats.STRIDER_ONE_CM)) distance += custom(stats, metric);
                values.set(PlayerProfile.Metric.DISTANCE.ordinal(), distance);
                values.set(PlayerProfile.Metric.JUMPS.ordinal(), custom(stats, Stats.JUMP));
                values.set(PlayerProfile.Metric.MOB_KILLS.ordinal(), custom(stats, Stats.MOB_KILLS));
            }
            // Missing offline files are retried too; no manufactured zeroes. Online counters never read disk here.
            snapshot = new Snapshot(now + (online == null ? 300_000 : 30_000), online != null, List.copyOf(values));
            cache.snapshots.put(request.player(), snapshot); bound(cache.snapshots);
        }
        if (cache.projectsUntil <= now) {
            cache.projects = EconomyManager.get(server.overworld()).projects().all(); cache.projectsUntil = now + 5000;
        }
        var projects = ProfileHistory.completed(cache.projects, request.player());
        var values = new ArrayList<>(snapshot.values()); values.set(PlayerProfile.Metric.PROJECTS.ordinal(), (long) projects.size());
        String result = request.save() ? data.update(viewer.getUUID(), request.player(), request.settings()) : "ok";
        if (online != null) data.rememberSkin(online.getGameProfile());
        var skin = data.skin(request.player());
        var settings = data.settings(request.player());
        int page = Math.min(request.page(), Math.max(0, (projects.size() - 1) / PlayerProfile.PAGE_SIZE));
        int first = page * PlayerProfile.PAGE_SIZE;
        String name = PlayerNames.name(server, request.player());
        if (!PlayerNames.usable(name)) name = "Player " + request.player().toString().substring(0, 8);
        CityMember member = city == null ? null : city.getMember(request.player());
        PlayerProfile profile = new PlayerProfile(request.player(), name, online != null, city == null ? "" : city.getName(),
                member == null ? "" : member.getRole().name(), settings, PlayerNames.usable(skin.name()) ? skin.name() : name, skin.texture(), skin.signature(),
                PlayerProfile.publicValues(values, settings, viewer.getUUID().equals(request.player())), projects.size(), page,
                projects.subList(first, Math.min(projects.size(), first + PlayerProfile.PAGE_SIZE)));
        reply(viewer, request, result, profile);
    }
    private static long custom(StatsCounter stats, ResourceLocation type) { return Math.max(0, stats.getValue(Stats.CUSTOM.get(type))); }
    private static StatsCounter offline(MinecraftServer server, Path file) {
        if (!Files.isRegularFile(file)) return null;
        // Validate existence of the vanilla stats object: corrupt files are unavailable, not an empty career.
        // The vanilla loader then owns format migration/decoding. At most once per cached offline player / 5 min.
        try (var reader = Files.newBufferedReader(file)) {
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("stats") || !json.getAsJsonObject().get("stats").isJsonObject()) return null;
            return new ServerStatsCounter(server, file.toFile());
        } catch (Exception ignored) { return null; }
    }
    private static void reply(ServerPlayer player, ProfileRequestPayload request, String result, PlayerProfile profile) {
        PacketDistributor.sendToPlayer(player, new ProfileResponsePayload(request.request(), result, profile));
    }
}
