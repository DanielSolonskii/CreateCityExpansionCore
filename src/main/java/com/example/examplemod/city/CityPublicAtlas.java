package com.example.examplemod.city;

import com.example.examplemod.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-selected, expiring public map views. Never grants management access. */
@net.neoforged.fml.common.EventBusSubscriber(modid="createcityexpansioncore")
public final class CityPublicAtlas {
    private record ClientViews(Map<Boolean, View> views) { }
    private record Window(long tick, int count) { }
    private static final Map<net.minecraft.server.network.ServerGamePacketListenerImpl, ClientViews> CLIENTS = new WeakHashMap<>();
    private static final Map<net.minecraft.server.network.ServerGamePacketListenerImpl, Window> WINDOWS = new WeakHashMap<>();
    private CityPublicAtlas() {}

    public record View(UUID viewId, UUID requestId, UUID token, ResourceLocation dimension, BlockPos anchor,
                       List<ResourceLocation> dimensions, List<CityPublicProfile> cities, long expires,
                       UUID worldId, UUID memberCityId) { }

    private static boolean allow(ServerPlayer player) {
        long tick = now(player);
        Window previous = WINDOWS.get(player.connection);
        int count = previous != null && previous.tick == tick ? previous.count : 0;
        if (count >= 8) return false;
        WINDOWS.put(player.connection, new Window(tick, count + 1));
        return true;
    }

    private static long now(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    private static View view(ServerPlayer player, boolean hud) {
        var client = CLIENTS.get(player.connection);
        if (client == null) return null;
        View view = client.views.get(hud);
        if (view != null && (view.expires < now(player) ||
                hud && !player.serverLevel().dimension().location().equals(view.dimension))) {
            client.views.remove(hud);
            unwatch(player,view);
            return null;
        }
        return view;
    }

    public static View view(ServerPlayer player) { return view(player, false); }

    public static View view(ServerPlayer player, UUID id) {
        var atlas = view(player, false);
        if (atlas != null && atlas.viewId.equals(id)) return atlas;
        var hud = view(player, true);
        return hud != null && hud.viewId.equals(id) ? hud : null;
    }

    public static void close(ServerPlayer player, UUID id) {
        var client = CLIENTS.get(player.connection);
        if (client == null) return;
        var it = client.views.values().iterator();
        while (it.hasNext()) {
            var value = it.next();
            if (!value.viewId.equals(id)) continue;
            it.remove();
            unwatch(player,value);
        }
    }

    private static void unwatch(ServerPlayer player, View view) {
        var level = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,view.dimension));
        if (level != null) CityMapSavedData.get(level).unwatch(player.getUUID(),view.viewId);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var client = CLIENTS.remove(player.connection);
        if (client != null) client.views.values().forEach(v -> unwatch(player,v));
        WINDOWS.remove(player.connection);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void expire(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            view(player,false);
            view(player,true);
        }
    }

    public static void open(ServerPlayer player, RequestCityWorldMapPayload request) {
        City city = CityManager.get(player.getServer().overworld()).getMemberCity(player.getUUID());
        var dim = city != null ? city.getPylonDimension() : player.serverLevel().dimension().location();
        request(player, new RequestCityDirectoryPayload(dim, RequestCityDirectoryPayload.NEW, 0, request.viewId(), request.requestId()));
    }

    public static void requestMinimap(ServerPlayer player, RequestMinimapPayload payload) {
        request(player, new RequestCityDirectoryPayload(player.serverLevel().dimension().location(), payload.token(),
                payload.offset(), payload.viewId(), payload.requestId()), true);
    }

    public static void request(ServerPlayer player, RequestCityDirectoryPayload request) { request(player, request, false); }

    private static void request(ServerPlayer player, RequestCityDirectoryPayload request, boolean hud) {
        if (!allow(player) || request.viewId().equals(RequestCityDirectoryPayload.NEW)
                || request.requestId().equals(RequestCityDirectoryPayload.NEW)) return;
        var manager = CityManager.get(player.getServer().overworld());
        var level = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, request.dimension()));
        if (level == null) return;
        var current = view(player, hud);
        var own = manager.getMemberCity(player.getUUID());
        boolean member = own != null && own.hasMember(player.getUUID()) && own.getPylonDimension().equals(request.dimension());
        UUID memberId = member ? own.getId() : RequestCityDirectoryPayload.NEW;
        if (request.token().equals(RequestCityDirectoryPayload.NEW)) {
            if (request.offset() != 0) return;
            var dimensions = new ArrayList<ResourceLocation>();
            player.getServer().getAllLevels().forEach(l -> dimensions.add(l.dimension().location()));
            if (dimensions.size() > 256) return;
            BlockPos anchor = member ? own.getPylonPos() :
                    current != null && current.dimension.equals(request.dimension()) ? current.anchor : player.blockPosition();
            if (!hud && player.containerMenu instanceof com.example.examplemod.menu.CityPylonMenu menu
                    && menu.getDimensionId().equals(request.dimension()) &&
                    player.serverLevel().dimension().location().equals(request.dimension())
                    && player.distanceToSqr(menu.getPylonPos().getCenter()) <= 64 &&
                    manager.getCityByPylon(request.dimension(),menu.getPylonPos()) != null) anchor = menu.getPylonPos();
            current = new View(request.viewId(), request.requestId(), UUID.randomUUID(), request.dimension(), anchor,
                    List.copyOf(dimensions), profiles(manager, player.getUUID(), request.dimension()),
                    now(player)+300, CityMapSavedData.get(level).worldId(), memberId);
        } else {
            if (current == null || !current.viewId.equals(request.viewId()) || !current.requestId.equals(request.requestId())
                    || !current.token.equals(request.token()) || !current.dimension.equals(request.dimension())
                    || !current.memberCityId.equals(memberId)) return;
            current = new View(current.viewId, current.requestId, current.token, current.dimension, current.anchor,
                    current.dimensions, current.cities, now(player)+300, current.worldId, current.memberCityId);
        }
        if (request.offset() < 0 || request.offset() > current.cities.size()) return;
        var views = CLIENTS.computeIfAbsent(player.connection, ignored -> new ClientViews(new HashMap<>()));
        var old = views.views.put(hud, current);
        if (old != null && (!old.viewId.equals(current.viewId) || !old.dimension.equals(current.dimension)
                || !old.anchor.equals(current.anchor))) unwatch(player,old);
        var directory = new CityDirectoryPayload(current.token, current.anchor, current.dimension, request.offset(),
                current.cities.size(), current.dimensions, CityDirectoryPager.page(current.cities,request.offset()),
                current.viewId, current.requestId, current.worldId, current.memberCityId);
        if (hud) PacketDistributor.sendToPlayer(player,new MinimapDirectoryPayload(directory));
        else PacketDistributor.sendToPlayer(player,directory);
        if (request.offset() != 0) return;
        var border = level.getWorldBorder();
        PacketDistributor.sendToPlayer(player, new MapBorderPayload(current.dimension, border.getCenterX(), border.getCenterZ(),
                border.getSize(), border.getLerpTarget(), border.getLerpRemainingTime(), current.viewId, current.requestId));
        if (!hud && member && current.anchor.equals(own.getPylonPos())) {
            var state = CityProjectNetworkHandlers.createState(player.getServer(), own, player);
            var relations = CityRelationsNetworkHandlers.state(player, manager, own);
            PacketDistributor.sendToPlayer(player,new MapPrivateStatePayload(current.viewId,current.requestId, state, relations));
        }
    }
    public static void edit(ServerPlayer player, SetCityIdentityPayload request) {
        if (!allow(player)) return;
        CityManager manager = CityManager.get(player.getServer().overworld());
        String result = manager.updateIdentity(manager.getCity(request.city()), player.getUUID(), request.revision(), request.colour(), request.description(), request.pixels());
        if (result.equals("saved")) {
            var old = CLIENTS.remove(player.connection);
            if (old != null) old.views.values().forEach(v -> unwatch(player,v));
        }
        PacketDistributor.sendToPlayer(player, new CityIdentityResultPayload(request.city(), request.requestId(), result));
    }
    public static List<CityPublicProfile> profiles(CityManager manager, UUID viewer, ResourceLocation dimension) {
        return manager.getCities().stream().filter(c -> c.getPylonDimension().equals(dimension))
                .sorted(Comparator.comparing(City::getName).thenComparing(City::getId))
                .map(c -> new CityPublicProfile(c.getId(), c.getName(), c.getPylonDimension(), c.getPylonPos(), c.getColorRgb(),
                        c.getMemberCount(), c.getClaimedPlotCount(), c.hasMember(viewer), c.isAdmin(viewer), c.getIdentityRevision(), c.getIdentity())).toList();
    }
}
