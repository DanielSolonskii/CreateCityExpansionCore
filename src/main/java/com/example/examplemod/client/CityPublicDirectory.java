package com.example.examplemod.client;

import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.client.map.MapViewBindings;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** One active full-map directory, independent of HUD paging. All mutations run on the client thread. */
@EventBusSubscriber(modid="createcityexpansioncore", value=Dist.CLIENT)
public final class CityPublicDirectory {
    private record Area(ResourceLocation dimension, BlockPos anchor, UUID world) {}
    private static Area remembered, confirmed;
    private static UUID view, request, accepted, token, member, peerRequest;
    private static ResourceLocation desired;
    private static long accessEpoch;
    public static long accessEpoch() { return accessEpoch; }
    private static long nextRequest, accessExpires, nextPeerRequest;
    private static int attempts, expected, total, menuId = -1;
    private static boolean waiting;
    private static DirectoryPages pages;
    private static final Map<UUID,CityPublicProfile> staging = new LinkedHashMap<>();
    private static Map<UUID,CityPublicProfile> profiles = Map.of();
    public static List<ResourceLocation> dimensions = List.of();

    private CityPublicDirectory() {}
    public static UUID viewId() { return view; }
    public static CityPublicProfile profile(UUID id) { return id == null ? null : profiles.get(id); }
    public static List<CityPublicProfile> profiles(ResourceLocation dim) {
        return profiles.values().stream().filter(c -> c.dimension().equals(dim)).toList();
    }
    public static UUID worldId(ResourceLocation dim) {
        return confirmed != null && confirmed.dimension.equals(dim) ? confirmed.world : null;
    }
    public static boolean loading() { return waiting; }
    public static boolean failed() { return waiting && attempts >= 3; }
    public static boolean hasPrivateAccess() { return member != null && !member.equals(RequestCityDirectoryPayload.NEW); }
    public static boolean acceptsBorder(MapBorderPayload p) {
        return p.viewId().equals(view) && p.requestId().equals(accepted)
                && confirmed != null && p.dimension().equals(confirmed.dimension);
    }

    public static void cancelLoading() {
        token = request = null;
        waiting = false;
        pages = null;
        expected = total = 0;
        staging.clear();
    }

    private static void end(boolean sendClose) {
        if (sendClose && view != null && Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new CloseMapViewPayload(view));
        cancelLoading();
        view = accepted = member = peerRequest = null;
        confirmed = null;
        accessEpoch++;
        desired = null;
        profiles = Map.of();
        dimensions = List.of();
        menuId = -1;
        attempts = 0;
        nextPeerRequest = 0;
        MapViewBindings.invalidate(false);
        CityWorldMapSession.INSTANCE.clear();
    }
    public static void reset() { end(true); }

    /** Warm only public terrain. The first page separately revalidates private access. */
    public static void open(ResourceLocation dimension) {
        end(true);
        desired = dimension != null ? dimension : remembered != null ? remembered.dimension : null;
        view = UUID.randomUUID();
        CityWorldMapSession.INSTANCE.awaitAnchor();
        if (remembered != null && remembered.dimension.equals(desired)) {
            confirmed = remembered;
            CityWorldMapSession.INSTANCE.begin(confirmed.anchor, confirmed.dimension);
            MapViewBindings.confirm(false, confirmed.anchor, confirmed.dimension, view, confirmed.world);
        }
        send();
    }

    private static void send() {
        cancelLoading();
        com.example.examplemod.client.map.MapDiagnostics.request(attempts > 0);
        request = UUID.randomUUID();
        pages = new DirectoryPages(view,request,desired);
        waiting = true;
        attempts = Math.min(4, attempts + 1);
        nextRequest = Util.getMillis() + (5_000L << (attempts - 1));
        if (desired == null) PacketDistributor.sendToServer(new RequestCityWorldMapPayload(view,request));
        else PacketDistributor.sendToServer(new RequestCityDirectoryPayload(desired, RequestCityDirectoryPayload.NEW,0,view,request));
    }

    public static void refresh() {
        if (view != null && Util.getMillis() >= nextRequest) send();
    }
    public static void retry() {
        if (view != null && (Util.getMillis() >= nextRequest-1000 || failed())) {
            attempts = 0;
            send();
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        var menu = mc.player.containerMenu instanceof CityPylonMenu p ? p : null;
        if (hasPrivateAccess() && Util.getMillis() >= accessExpires) {
            accessEpoch++;
            member = peerRequest = accepted = null;
            cancelLoading();
            profiles = Map.of();
            if (confirmed != null) CityWorldMapSession.INSTANCE.begin(confirmed.anchor,confirmed.dimension);
            var atlas = PolishedWorldMapScreen.activeView();
            if (atlas != null) atlas.expireMapAccess();
        }
        if (menuId >= 0 && (menu == null || menu.containerId != menuId)) end(true);
        if (view == null && menu != null) {
            desired = menu.getDimensionId();
            view = UUID.randomUUID();
            menuId = menu.containerId;
            send();
        } else refresh();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        PolishedWorldMapScreen.disconnected();
        end(false);
        remembered = null;
    }

    public static void accept(CityDirectoryPayload p) {
        if (!waiting || pages == null) return;
        var screen = PolishedWorldMapScreen.activeView();
        if (menuId >= 0) {
            var player = Minecraft.getInstance().player;
            if (player == null || !(player.containerMenu instanceof CityPylonMenu menu) || menu.containerId != menuId
                    || !menu.getPylonPos().equals(p.anchor()) || !menu.getDimensionId().equals(p.dimension())) return;
        }
        if (!pages.accept(p)) { com.example.examplemod.client.map.MapDiagnostics.stale(); return; }
        if (p.offset() == 0) {
            if (screen != null && !screen.acceptsDirectory(p)) return;
            com.example.examplemod.client.journey.JourneyClient.confirmedMapAccess(p.dimension(), p.worldId(), p.memberCityId());
            var area = new Area(p.dimension(), p.anchor(), p.worldId());
            var session = CityWorldMapSession.INSTANCE;
            if (!area.equals(confirmed) || !Objects.equals(member,p.memberCityId())) {
                session.begin(area.anchor,area.dimension);
                profiles = Map.of();
                if (hasPrivateAccess() && !Objects.equals(member,p.memberCityId())) {
                    accessEpoch++;
                    if (screen != null) screen.expireMapAccess();
                }
            }
            token = p.token();
            total = p.total();
            confirmed = area;
            if (menuId < 0) remembered = area;
            desired = p.dimension();
            member = p.memberCityId();
            accepted = p.requestId();
            accessExpires = Util.getMillis()+15_000;
            MapViewBindings.confirm(false,area.anchor,area.dimension,view,area.world);
            dimensions = p.dimensions();
        }
        CityWorldMapSession.INSTANCE.acceptDirectory(p);
        for (var city : p.cities()) staging.put(city.id(),city);
        expected += p.cities().size();
        if (expected < total) {
            if (p.cities().isEmpty()) return;
            nextRequest = Util.getMillis()+15_000;
            PacketDistributor.sendToServer(new RequestCityDirectoryPayload(desired,token,expected,view,request));
            return;
        }
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(staging));
        waiting = false;
        attempts = 0;
        nextRequest = Util.getMillis()+5_000;
        staging.clear();
        if (screen != null) screen.directoryReady(p);
    }

    public static boolean currentConnection(net.minecraft.network.Connection incoming) {
        var current = Minecraft.getInstance().getConnection();
        return current != null && current.getConnection() == incoming;
    }
    public static void acceptPrivate(MapPrivateStatePayload payload) {
        if (!hasPrivateAccess() || !payload.viewId().equals(view) || !payload.requestId().equals(accepted)
                || !payload.relations().cityId().equals(member) || !payload.state().pylonPos().equals(confirmed.anchor)
                || !payload.relations().pylonPos().equals(confirmed.anchor)) return;
        CityWorldMapSession.INSTANCE.applyState(payload.state());
        com.example.examplemod.client.journey.JourneyClient.confirmedIndustries(confirmed.dimension, payload.state().projects());
        CityWorldMapSession.INSTANCE.applyRelationsState(payload.relations());
        var screen = PolishedWorldMapScreen.activeView();
        if (screen != null) screen.privateSnapshotReady();
    }

    public static void requestTerritories(BlockPos center,int range) {
        if (confirmed == null || !hasPrivateAccess() || peerRequest != null && Util.getMillis() < nextPeerRequest) return;
        nextPeerRequest = Util.getMillis()+15_000;
        peerRequest = UUID.randomUUID();
        PacketDistributor.sendToServer(new RequestCityTerritoriesPayload(confirmed.anchor,center,range,view,peerRequest));
    }
    public static boolean acceptsPeers(CityMapPeersPayload p) {
        boolean acceptedPeer = hasPrivateAccess() && p.viewId().equals(view) && p.requestId().equals(peerRequest)
                && p.dimensionId().equals(confirmed.dimension) && p.pylonPos().equals(confirmed.anchor)
                && p.peers().stream().anyMatch(peer -> peer.own() && peer.cityId().equals(member));
        if (acceptedPeer) peerRequest = null;
        return acceptedPeer;
    }
}
