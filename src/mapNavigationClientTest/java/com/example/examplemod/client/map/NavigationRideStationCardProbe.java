package com.example.examplemod.client.map;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.citymap.CityMapNetwork;
import com.example.examplemod.citymap.CityMapService;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.screen.AtlasTripScreen;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.navigation.JourneyTripGameTest;
import com.example.examplemod.navigation.TripRef;
import com.example.examplemod.passenger.PassengerService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import static com.example.examplemod.client.map.NavigationClientProbe.*;

/** Verifies native platform identity through the public station card and its network-backed planner. */
final class NavigationRideStationCardProbe {
    private static final UUID station = UUID.randomUUID();
    private static CompletableFuture<?> setup;
    private static int stage, ticks;
    private static int shown;
    private static PassengerService.Stop first, chosen;

    private static void require(boolean result,String why) { if (!result) throw new AssertionError(why); }
    private static void next() { stage++; ticks=0; }

    static boolean tick(Minecraft mc, JourneyTripGameTest.Interactive scene) throws Exception {
        if (++ticks>500) throw new AssertionError("Public station card stalled at "+stage);
        if (ticks<6) return false;
        var atlas=mc.screen instanceof PolishedWorldMapScreen a ? a : null;
        var rail=scene.rails().getFirst();
        switch(stage) {
            case 0 -> {
                var dim=mc.level.dimension().location();
                var view=CityPublicDirectory.viewId();
                var world=CityPublicDirectory.worldId(dim);
                var server=mc.getSingleplayerServer();
                setup=server.submit(()->prepare(server.getPlayerList().getPlayer(scene.player()),rail,dim,view,world));
                next();
            }
            case 1 -> {
                if (!setup.isDone()) return false;
                setup.join();
                if(atlas!=null) atlas.onClose();
                next();
            }
            case 2 -> {
                if (mc.player.hasPermissions(2)) throw new AssertionError("Expected ordinary passenger on client");
                atlasKey();next();
            }
            case 3 -> {
                var f=CityMapFeatures.get(station);
                if (ticks==80) NavigationRideProbe.log("Public card load: screen="+mc.screen+", view="+CityPublicDirectory.viewId()+
                        ", ready="+CityMapFeatures.ready(mc.level.dimension().location())+", feature="+(f!=null)+", stops="+
                        (f==null ? 0 : CityMapFeatures.platforms(f).size()));
                if(atlas==null || f==null || CityMapFeatures.platforms(f).size()!=2) return false;
                var map=(CityMapPanel)field(atlas,"map");
                map.restoreView(f.anchor().getX()+.5,f.anchor().getZ()+.5,4);
                atlas.pinMapObject(CityCartographyLayer.selection(f));
                next();
            }
            case 4 -> {
                var actions=(List<?>)field(atlas,"cardActions");
                int last=-1,count=0;
                for(int i=0;i<actions.size();i++) {
                    var action=actions.get(i);
                    require(!get(action,"action").toString().equals("EDIT_CARTOGRAPHY"),"ordinary visitor received edit action");
                    if(!get(action,"action").toString().equals("TO_PLATFORM"))continue;
                    var stop=(PassengerService.Stop)get(action,"platform");
                    if(count==0) first=stop;
                    chosen=stop;last=i;count++;
                }
                require(count==2 && !chosen.id().equals(first.id()),"two distinct platform buttons");
                require(List.of(rail.west(),rail.east()).containsAll(List.of(first.id(),chosen.id())),"published native station UUIDs on rows");
                NavigationRideProbe.log("Public card shows two distinct platform buttons. Activating row "+last+": "+chosen.name()+" "+chosen.id());
                screenshot(mc,"public-station-two-platforms");
                activate(atlas,"card."+last);
                next();
            }
            case 5 -> {
                require(field(atlas,"atlasTool") instanceof AtlasTripScreen,"second row opened the route planner");
                var planner=(AtlasTripScreen)field(atlas,"atlasTool");
                var goal=(TripRef)field(planner,"goal");
                require(goal.equals(new TripRef(TripRef.Kind.STATION,chosen.id())),"second action used the wrong TripRef: "+goal);
                var reply=(CompoundTag)field(planner,"response");
                if(reply==null || reply.getList("Options",Tag.TAG_COMPOUND).isEmpty()){
                    var state=field(planner,"status");
                    require(state.equals("loading") || ticks<40,"server did not supply a route for "+goal+": "+state);
                    return false;
                }
                require(reply.getString("Goal").equals(chosen.name()),"server offered another destination: "+reply.getString("Goal"));
                if (++shown<6) return false;
                NavigationRideProbe.log("Non-operator visitor opened the public station card; first platform "+first.name()+" "+first.id()+
                        "; activated second platform "+chosen.name()+" "+chosen.id()+". Planner TripRef="+goal+
                        ", server destination="+reply.getString("Goal")+", options="+reply.getList("Options",Tag.TAG_COMPOUND).size()+".");
                screenshot(mc,"public-second-platform-route");
                return true;
            }
        }
        return false;
    }

    private static void prepare(ServerPlayer player,JourneyTripGameTest.Rail rail,ResourceLocation dim,UUID view,UUID world) {
        var server=player.serverLevel().getServer();var manager=CityManager.get(server.overworld());
        var publicStops=PassengerService.stops(player);
        var stops=publicStops.stream().filter(p->p.id().equals(rail.west()) || p.id().equals(rail.east())).toList();
        require(stops.size()==2,"two physical platforms published by railway tablet");
        var anchor=rail.westEntrance();
        var city=manager.getClaimOwner(CityPlot.at(dim,anchor));
        if(city==null)city=manager.createCity("Пересадочный узел",UUID.randomUUID(),dim,anchor);
        require(city!=null && !city.hasMember(player.getUUID()),"passenger is outside the fixture city");
        for(var stop:stops) for(var pos:List.of(stop.entrance(),stop.stationPos())) {
            var plot=CityPlot.at(dim,pos);
            manager.claimPlot(city,plot,city.getOwner());
            require(city.hasClaim(plot),"both real platforms are in the fixture city");
        }
        var f=new CityMapFeature(station,city.getId(),0,dim,CityMapFeature.Kind.STATION,CityMapFeature.Shape.POINT,
                "Пересадочный вокзал","","",List.of(anchor),List.of(anchor));
        var saved=CityMapService.handle(player,new CityMapNetwork.Request(UUID.randomUUID(),view,world,dim,city.getId(),
                CityMapNetwork.Action.SAVE,station,0,f));
        require(saved.status().equals("ok"),"save public station: "+saved.status());
        for(var stop:stops) {
            long revision=CityMapService.resolve(player,station).revision();
            var bound=CityMapService.handle(player,new CityMapNetwork.Request(UUID.randomUUID(),view,world,dim,city.getId(),
                    CityMapNetwork.Action.BIND_PLATFORM,station,revision,null,stop.id()));
            require(bound.status().equals("ok"),"bind native platform: "+bound.status());
        }
        server.getPlayerList().deop(player.getGameProfile());
        server.getPlayerList().sendPlayerPermissionLevel(player);
        require(!player.hasPermissions(2),"server restored ordinary passenger permissions");
        require(PassengerService.stops(player).stream().filter(s->station.equals(s.feature())).count()==2,
                "station retains both native platforms after staff leaves");
    }
}
