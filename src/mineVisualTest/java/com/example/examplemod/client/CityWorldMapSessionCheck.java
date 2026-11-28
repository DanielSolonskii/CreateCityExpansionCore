package com.example.examplemod.client;

import com.example.examplemod.city.CityIdentity;
import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.network.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class CityWorldMapSessionCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var dim = ResourceLocation.withDefaultNamespace("overworld");
        var anchor = new BlockPos(1,64,3);
        var view = UUID.randomUUID(); var request = UUID.randomUUID(); var world = UUID.randomUUID();
        var token = UUID.randomUUID(); var city = UUID.randomUUID();
        var profile = new CityPublicProfile(city,"City",dim,anchor,3,1,2,true,false,1,new CityIdentity("","0".repeat(1024)));
        var pages = new DirectoryPages(view,request,dim);
        var first = new CityDirectoryPayload(token,anchor,dim,0,2,List.of(dim),List.of(profile),view,request,world,city);
        var second = new CityDirectoryPayload(token,anchor,dim,1,2,List.of(dim),List.of(profile),view,request,world,city);
        require(!pages.accept(second),"later page before first");
        require(!new DirectoryPages(UUID.randomUUID(),request,dim).accept(first),"old opening");
        require(!new DirectoryPages(view,UUID.randomUUID(),dim).accept(first),"old request");
        require(!new DirectoryPages(view,request,ResourceLocation.withDefaultNamespace("the_nether")).accept(first),"old dimension");
        require(pages.accept(first) && !pages.accept(first),"first page accepted once");
        require(!pages.accept(new CityDirectoryPayload(token,anchor,dim,1,2,List.of(dim),List.of(profile),view,request,UUID.randomUUID(),city)),"stale world");
        require(!pages.accept(new CityDirectoryPayload(token,anchor.above(),dim,1,2,List.of(dim),List.of(profile),view,request,world,city)),"moved anchor");
        require(!pages.accept(new CityDirectoryPayload(token,anchor,dim,1,2,List.of(dim),List.of(profile),view,request,world,UUID.randomUUID())),"changed access");
        require(pages.accept(second) && !pages.accept(second),"last page accepted once");
        var session = CityWorldMapSession.INSTANCE;
        session.awaitAnchor();
        var tile = new CityMapTilePayload(anchor,dim,0,0,true,new int[1024],new BitSet());
        session.acceptMapTile(tile);
        require(!session.isReady() && session.drainMapTiles().isEmpty(),"unsolicited tile cannot establish anchor");
        session.acceptDirectory(first);
        require(session.isReady() && session.getPylonPos().equals(anchor),"directory establishes anchor");
        session.clear();
        session.acceptMapTile(tile);
        require(!session.isReady(),"closed session rejects late terrain");
        var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var hud = new RequestMinimapPayload(token,1,view,request);
            RequestMinimapPayload.STREAM_CODEC.encode(buf,hud);
            require(hud.equals(RequestMinimapPayload.STREAM_CODEC.decode(buf)),"HUD view and request codec");
            var open = new RequestCityWorldMapPayload(view,request);
            RequestCityWorldMapPayload.STREAM_CODEC.encode(buf,open);
            require(open.equals(RequestCityWorldMapPayload.STREAM_CODEC.decode(buf)),"atlas open codec");
            CityDirectoryPayload.STREAM_CODEC.encode(buf,first);
            require(first.equals(CityDirectoryPayload.STREAM_CODEC.decode(buf)),"confirmed world and member codec");
            var pending = new CityMapTilePayload(anchor,dim,3,-7,false,new int[0],new BitSet(),3,new short[0],world,0,false,view,request,0,true);
            CityMapTilePayload.STREAM_CODEC.encode(buf,pending);
            var decoded = CityMapTilePayload.STREAM_CODEC.decode(buf);
            require(decoded.pending() && !decoded.unchanged() && decoded.viewId().equals(view)
                    && decoded.requestId().equals(request) && decoded.colors().length == 0,"pending tile codec");
        } finally { buf.release(); }
        System.out.println("PASS: production directory correlation, session and protocol");
    }
}
