package com.example.examplemod.city;

import com.example.examplemod.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-selected, expiring public map views. Never grants management access. */
public final class CityPublicAtlas {
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private record Window(long tick, int count) { }
    private static boolean allow(ServerPlayer player) {
        long tick = now(player); Window previous = WINDOWS.get(player.getUUID());
        if (previous != null && previous.tick == tick && previous.count >= 8) return false;
        if (WINDOWS.size() > 64) WINDOWS.entrySet().removeIf(e -> e.getValue().tick + 200 < tick);
        WINDOWS.put(player.getUUID(), new Window(tick, previous != null && previous.tick == tick ? previous.count + 1 : 1));
        return true;
    }
    private CityPublicAtlas() { }
    public record View(UUID token, ResourceLocation dimension, BlockPos anchor, List<ResourceLocation> dimensions,
                       List<CityPublicProfile> cities, long created, long expires, UUID connection) { }
    public static View view(ServerPlayer player) {
        View view = VIEWS.get(player.getUUID());
        // Connection identity prevents an old view surviving a reconnect/server restart.
        if (view == null || view.expires < now(player) || !view.connection.equals(connection(player))) return null;
        return view;
    }
    private static UUID connection(ServerPlayer player) { return UUID.nameUUIDFromBytes((player.getUUID() + ":" + System.identityHashCode(player)).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    public static void open(ServerPlayer player) {
        View previous = view(player);
        City city = CityManager.get(player.getServer().overworld()).getMemberCity(player.getUUID());
        var dimension = previous != null ? previous.dimension : city != null ? city.getPylonDimension() : player.serverLevel().dimension().location();
        request(player, new RequestCityDirectoryPayload(dimension, RequestCityDirectoryPayload.NEW, 0));
    }
    public static void request(ServerPlayer player, RequestCityDirectoryPayload request) {
        if (!allow(player)) return;
        var manager = CityManager.get(player.getServer().overworld());
        View current = view(player);
        if (request.token().equals(RequestCityDirectoryPayload.NEW)) {
            if (request.offset() != 0) return;
            List<ResourceLocation> dimensions = new ArrayList<>();
            player.getServer().getAllLevels().forEach(level -> dimensions.add(level.dimension().location()));
            if (!dimensions.contains(request.dimension()) || dimensions.size() > 256) return;
            if (current == null || !current.dimension.equals(request.dimension()) || now(player) - current.created >= 20) {
                City own = manager.getMemberCity(player.getUUID());
                BlockPos anchor = own != null && own.getPylonDimension().equals(request.dimension()) ? own.getPylonPos()
                        : current != null && current.dimension.equals(request.dimension()) ? current.anchor : player.blockPosition();
                if (player.containerMenu instanceof com.example.examplemod.menu.CityPylonMenu menu && menu.getDimensionId().equals(request.dimension())) anchor = menu.getPylonPos();
                var profiles = profiles(manager, player.getUUID(), request.dimension());
                current = new View(UUID.randomUUID(), request.dimension(), anchor, List.copyOf(dimensions), profiles, now(player), now(player) + 200, connection(player));
                VIEWS.entrySet().removeIf(e -> e.getValue().expires < now(player));
                VIEWS.put(player.getUUID(), current);
            }
        } else if (current == null || !current.token.equals(request.token()) || !current.dimension.equals(request.dimension())) return;
        if (request.offset() < 0 || request.offset() > current.cities.size()) return;
        current = new View(current.token, current.dimension, current.anchor, current.dimensions, current.cities, current.created, now(player) + 200, current.connection);
        VIEWS.put(player.getUUID(), current);
        PacketDistributor.sendToPlayer(player, new CityDirectoryPayload(current.token, current.anchor, current.dimension,
                request.offset(), current.cities.size(), current.dimensions, CityDirectoryPager.page(current.cities, request.offset())));
    }
    public static void edit(ServerPlayer player, SetCityIdentityPayload request) {
        if (!allow(player)) return;
        CityManager manager = CityManager.get(player.getServer().overworld());
        String result = manager.updateIdentity(manager.getCity(request.city()), player.getUUID(), request.revision(), request.colour(), request.description(), request.pixels());
        if (result.equals("saved")) VIEWS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new CityIdentityResultPayload(request.city(), request.requestId(), result));
    }
    public static List<CityPublicProfile> profiles(CityManager manager, UUID viewer, ResourceLocation dimension) {
        return manager.getCities().stream().filter(c -> c.getPylonDimension().equals(dimension))
                .sorted(Comparator.comparing(City::getName).thenComparing(City::getId))
                .map(c -> new CityPublicProfile(c.getId(), c.getName(), c.getPylonDimension(), c.getPylonPos(), c.getColorRgb(),
                        c.getMemberCount(), c.getClaimedPlotCount(), c.hasMember(viewer), c.isAdmin(viewer), c.getIdentityRevision(), c.getIdentity())).toList();
    }
}
