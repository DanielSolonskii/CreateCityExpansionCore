package com.example.examplemod.client.map;

import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.citymap.CityMapNetwork;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.screen.CityCartographyScreen;
import com.example.examplemod.passenger.PassengerRailMap;
import com.example.examplemod.passenger.PassengerRailNetwork;
import com.example.examplemod.passenger.PassengerService;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Published atlas geometry belongs to one connection and confirmed atlas view. */
public final class CityMapFeatures {
    private static final UUID ALL = new UUID(0,0);
    private static ClientPacketListener connection;
    private static ResourceLocation dimension;
    private static UUID view, world, pending;
    private static long nextRequest;
    private static boolean ready;
    private static List<CityMapFeature> rows = List.of();
    private static PassengerRailMap.Snapshot railMap=PassengerRailMap.EMPTY;
    private static PassengerRailMap.Snapshot liveRails=PassengerRailMap.EMPTY;
    private static final java.util.ArrayList<PassengerRailMap.Segment> railPages=new java.util.ArrayList<>();
    private static UUID railRequest;
    private static long nextRailRequest, lastRailReply;
    private static int railOffset;
    private static boolean railReady;
    private static List<PassengerService.Stop> platforms=List.of();
    private static final Map<UUID,CityMapFeature> byId=new HashMap<>();

    private CityMapFeatures() {}
    public static boolean supportsIcons() {
        var link = Minecraft.getInstance().getConnection();
        return link != null && link.hasChannel(CityMapNetwork.RequestV3.TYPE);
    }
    public static boolean supported() {
        var link = Minecraft.getInstance().getConnection();
        return link != null && link.hasChannel(CityMapNetwork.Request.TYPE);
    }
    public static void refresh(ResourceLocation dim) {
        var link = Minecraft.getInstance().getConnection();
        var nextView = CityPublicDirectory.viewId();
        var nextWorld = CityPublicDirectory.worldId(dim);
        if (link != connection || !Objects.equals(dimension,dim) || !Objects.equals(view,nextView) || !Objects.equals(world,nextWorld)) {
            connection=link; dimension=dim; view=nextView; world=nextWorld;
            pending=null; rows=List.of(); byId.clear(); nextRequest=0; ready=false;
            railMap=PassengerRailMap.EMPTY; platforms=List.of();
            liveRails=PassengerRailMap.EMPTY; railPages.clear(); railRequest=null; railOffset=0;
            railReady=false; nextRailRequest=lastRailReply=0;
        }
        long now=Util.getMillis();
        if (view==null || world==null || !supported()) return;
        if (link.hasChannel(PassengerRailNetwork.Request.TYPE) && now>=nextRailRequest) {
            railRequest=UUID.randomUUID();
            nextRailRequest=now+3000;
            PacketDistributor.sendToServer(new PassengerRailNetwork.Request(railRequest,view,world,dim,liveRails.revision(),railOffset));
            if (railReady && now-lastRailReply>5000 && liveRails.lines().stream().anyMatch(line->line.train()!=null)) {
                var lines=liveRails.lines().stream().map(l->new PassengerRailMap.LineInfo(l.id(),l.name(),l.description(),
                        l.color(),l.stops(),l.trainId(),l.trainName(),null,l.status(),l.unavailableLegs())).toList();
                liveRails=new PassengerRailMap.Snapshot(liveRails.segments(),liveRails.pending(),lines,liveRails.revision());
            }
        }
        if (now<nextRequest) return;
        pending=UUID.randomUUID();
        nextRequest=now+15_000;
        var request=new CityMapNetwork.Request(pending,view,world,dim,ALL,CityMapNetwork.Action.LIST,ALL,0,null);
        PacketDistributor.sendToServer(supportsIcons() ? new CityMapNetwork.RequestV3(request) : CityMapNetwork.outbound(request,link.hasChannel(CityMapNetwork.RequestV2.TYPE)));
    }
    public static void receiveRail(PassengerRailNetwork.Reply reply) {
        if (!reply.nonce().equals(railRequest) || !current(dimension)) return;
        railRequest=null;
        var shown=liveRails.segments();
        if (reply.offset()==0) {
            railPages.clear();
            shown=reply.segments();
        } else if (reply.revision()!=liveRails.revision() || reply.offset()!=railOffset) {
            railOffset=0;
            nextRailRequest=0;
            return;
        }
        if (reply.offset()>=0) {
            railPages.addAll(reply.segments());
            if (reply.last()) {
                shown=List.copyOf(railPages);
                railPages.clear();
            }
        }
        liveRails=new PassengerRailMap.Snapshot(shown,!reply.last() || reply.pending(),reply.lines(),reply.revision());
        railOffset=reply.last()?-1:railPages.size();
        lastRailReply=Util.getMillis();
        nextRailRequest=lastRailReply+(!reply.last()?100:reply.pending()?300:900);
        railReady=true;
    }
    public static void receive(CityMapNetwork.Reply reply) {
        if(MinimapClient.receiveFeatures(reply))return;
        if (!ALL.equals(reply.city())) {
            CityCartographyScreen.receive(reply);
            if ("ok".equals(reply.status())) { pending=null; nextRequest=0; }
            return;
        }
        if (!reply.nonce().equals(pending) || !current(dimension)) return;
        pending=null;
        ready="ok".equals(reply.status());
        railMap=ready?reply.railMap():PassengerRailMap.EMPTY;
        platforms=ready?reply.platforms():List.of();
        if(railMap.pending()) nextRequest=Util.getMillis()+1500;
        if (ready) rows=List.copyOf(reply.features());
        else { rows=List.of(); nextRequest=Util.getMillis()+2_000; }
        byId.clear();
        for(var row:rows)byId.put(row.id(),row);
    }
    private static boolean current(ResourceLocation dim) {
        return dim!=null && dim.equals(dimension) && world!=null && world.equals(CityPublicDirectory.worldId(dim)) &&
                Objects.equals(view,CityPublicDirectory.viewId()) && connection==Minecraft.getInstance().getConnection();
    }
    public static List<CityMapFeature> all(ResourceLocation dim) { return current(dim)?rows:List.of(); }
    public static PassengerRailMap.Snapshot rails(ResourceLocation dim) { return current(dim)?(railReady?liveRails:railMap):PassengerRailMap.EMPTY; }
    public static boolean railReady(ResourceLocation dim) {
        if (!current(dim)) return false;
        return connection.hasChannel(PassengerRailNetwork.Request.TYPE)?railReady:ready || !supported();
    }
    public static List<PassengerService.Stop> platforms(CityMapFeature station) {
        if (!current(station.dimension())) return List.of();
        return platforms.stream().filter(p->station.id().equals(p.feature())).toList();
    }
    public static boolean ready(ResourceLocation dim) { return current(dim) && ready; }
    public static CityMapFeature get(UUID id) {
        return current(dimension)?byId.get(id):null;
    }
}
