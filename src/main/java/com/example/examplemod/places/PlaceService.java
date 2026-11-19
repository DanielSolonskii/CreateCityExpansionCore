package com.example.examplemod.places;

import com.example.examplemod.cargo.center.CargoWarehouseRegistry;
import com.example.examplemod.cargo.center.CargoOwners;
import com.example.examplemod.cargo.center.OwnerRef;
import com.example.examplemod.city.*;
import com.example.examplemod.community.ProjectWorkspaceService;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.projects.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class PlaceService {
    private record Budget(long second,int reads,int writes) {}
    private static final Map<ServerPlayer,Budget> budgets=new WeakHashMap<>();
    private record Mutation(UUID view,PlaceNetwork.Action action,UUID id,long version,String status) {}
    private static final Map<ServerPlayer,LinkedHashMap<UUID,Mutation>> mutations=new WeakHashMap<>();
    private record Listing(java.lang.ref.WeakReference<ServerPlayer> actor, UUID view, UUID world,
                           net.minecraft.resources.ResourceLocation dimension, UUID receipt, List<AtlasPlace> rows) {}
    private static final LinkedHashMap<UUID,Listing> listings=new LinkedHashMap<>();
    private PlaceService(){}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer actor){
            budgets.remove(actor);
            mutations.remove(actor);
            var row=listings.get(actor.getUUID());
            if(row!=null && row.actor.get()==actor)listings.remove(actor.getUUID());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){listings.clear();budgets.clear();mutations.clear();}
    public static AtlasPlace resolve(ServerPlayer actor,UUID id) {
        var place=PlaceBook.get(actor.serverLevel().getServer()).entries.get(id);
        return visible(actor,place);
    }
    public static List<AtlasPlace> entrancesFor(ServerPlayer actor,String kind,UUID sourceId) {
        if(sourceId==null)return List.of();
        var result=new ArrayList<AtlasPlace>();
        for(var p:PlaceBook.get(actor.serverLevel().getServer()).entries.values()){
            if(p.source()==null || !p.source().kind().name().equalsIgnoreCase(kind) || !p.source().id().equals(sourceId))continue;
            var row=visible(actor,p);if(row!=null && !row.entrances().isEmpty())result.add(row);
        }
        return List.copyOf(result);
    }
    private static AtlasPlace visible(ServerPlayer actor,AtlasPlace place) {
        if(place==null || !place.published() && !place.owner().equals(actor.getUUID()))return null;
        var icon=place.source()==null ? place.icon() : source(actor,place.dimension(),place.source(),false);
        if(icon==null)return null;
        var entrances=icon.equals(place.icon())?place.entrances():List.<BlockPos>of();
        boolean published=place.published() && publisherAuthorized(actor.getServer(),place,icon,entrances);
        if(!published && !place.owner().equals(actor.getUUID()))return null;
        if(icon.equals(place.icon()) && published==place.published())return place;
        return new AtlasPlace(place.id(),place.owner(),place.version(),place.name(),place.category(),place.description(),
                place.dimension(),icon,entrances,published,place.source());
    }
    private static boolean nearXZ(BlockPos a,BlockPos b,int radius){
        double dx=(double)a.getX()-b.getX(), dz=(double)a.getZ()-b.getZ();
        return dx*dx+dz*dz<=radius*(double)radius;
    }
    private static BlockPos source(ServerPlayer actor,net.minecraft.resources.ResourceLocation dimension,AtlasPlace.Source ref,boolean manage) {
        return source(actor.getServer(),actor.getUUID(),actor.hasPermissions(2),dimension,ref,manage);
    }
    private static BlockPos source(MinecraftServer server,UUID player,boolean op,ResourceLocation dimension,AtlasPlace.Source ref,boolean manage) {
        var cities=CityManager.get(server.overworld());
        UUID id=ref.id();
        return switch(ref.kind()){
            case CITY -> {
                var city=cities.getCity(id);
                yield city!=null && city.getPylonDimension().equals(dimension) &&
                        (!manage||op||city.canManageInfrastructure(player))?city.getPylonPos():null;
            }
            case PROJECT -> {
                var project=EconomyManager.get(server.overworld()).projects().get(id);
                var a=ProjectNetworkHandlers.actor(player,op,cities,cities.getMemberCity(player));
                if(project==null || !ProjectManager.visibleTo(a,project) || !project.zone().dimension().equals(dimension) ||
                        manage && !op && !(project.author().equals(player) ||
                                project.city()!=null && project.city().equals(a.city()) && ProjectWorkspaceService.manages(project,a)))yield null;
                var z=project.zone();
                yield new BlockPos(Math.floorDiv(z.x1()+z.x2(),2),0,Math.floorDiv(z.z1()+z.z2(),2));
            }
            case WAREHOUSE -> {
                var w=CargoWarehouseRegistry.get(server).find(id).orElse(null);
                if(w==null || !w.ticker().dimension().equals(dimension))yield null;
                var access=CargoWarehouseRegistry.access(server,w);
                yield CargoOwners.permits(server,OwnerRef.player(player),access) && (!manage || op || access.owner().manages(server,player)) ? w.ticker().pos():null;
            }
        };
    }
    /** Capture only the actor's currently occupied standing column, touching existing loaded chunks. */
    private static boolean atEntrance(ServerPlayer player,BlockPos pos) {
        var level=player.serverLevel();
        if(!player.blockPosition().equals(pos) || !player.onGround() || !level.hasChunksAt(pos.offset(-1,-1,-1),pos.offset(1,2,1)) ||
                !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos.above()))return false;
        var feet=new AABB(pos.getX()+.2,pos.getY(),pos.getZ()+.2,pos.getX()+.8,pos.getY()+1.8,pos.getZ()+.8);
        return level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty() &&
                level.noCollision(player,feet) && level.getBlockCollisions(player,feet.move(0,-.05,0)).iterator().hasNext();
    }
    private static boolean mayPublish(ServerPlayer actor,AtlasPlace p,BlockPos position){
        return actor.hasPermissions(2) || mayPublish(CityManager.get(actor.getServer().overworld()),actor.getUUID(),p,position);
    }
    private static boolean mayPublish(CityManager cities,UUID publisher,AtlasPlace p,BlockPos position){
        var city=cities.getCityAt(p.dimension(),position);
        return city==null || city.canManageInfrastructure(publisher);
    }
    private static boolean publisherAuthorized(MinecraftServer server,AtlasPlace p,BlockPos icon,List<BlockPos> entrances){
        var cities=CityManager.get(server.overworld());
        boolean territory=mayPublish(cities,p.owner(),p,icon) && entrances.stream().allMatch(pos->mayPublish(cities,p.owner(),p,pos));
        if(territory && p.source()==null)return true;
        var host=server.getSingleplayerProfile();
        var profile=host!=null && host.getId().equals(p.owner()) ? host : new com.mojang.authlib.GameProfile(p.owner(),"AtlasPublisher");
        boolean op=server.getProfilePermissions(profile)>=2;
        return (territory || op) && (p.source()==null || source(server,p.owner(),op,p.dimension(),p.source(),true)!=null);
    }
    public static String delete(ServerPlayer actor,UUID id,long version){
        return PlaceBook.get(actor.serverLevel().getServer()).change(actor.getUUID(),id,version,null);
    }
    public static String save(ServerPlayer actor,AtlasPlace value){
        if(actor instanceof FakePlayer)return "unavailable";
        var book=PlaceBook.get(actor.serverLevel().getServer());var old=book.entries.get(value.id());
        if(old!=null && !old.owner().equals(actor.getUUID()) || old==null && value.version()!=0)return "unavailable";
        if(!actor.level().dimension().location().equals(value.dimension()) || old!=null && !old.dimension().equals(value.dimension()))return "dimension";
        if(value.source()!=null){
            var anchor=source(actor,value.dimension(),value.source(),true);
            if(anchor==null)return "permission";
            value=value.at(anchor);
        }
        if(!actor.level().getWorldBorder().isWithinBounds(value.icon()) ||
                (old==null || !old.icon().equals(value.icon()) || value.published()&&!old.published()) && !nearXZ(actor.blockPosition(),value.icon(),128))return "near";
        if(value.published() && !mayPublish(actor,value,value.icon()))return "permission";
        // Standalone pins may be published without providing a destination for the route planner.
        if(value.entrances().isEmpty() && (value.source()!=null || old!=null && !old.entrances().isEmpty()))return "entrance";
        for(var pos:value.entrances()){
            if(!nearXZ(pos,value.icon(),128) || (!actor.level().getWorldBorder().isWithinBounds(pos)))return "near";
            if(value.published() && !mayPublish(actor,value,pos))return "permission";
            if((old==null || !Objects.equals(old.source(),value.source()) || old.source()!=null && !old.icon().equals(value.icon())
                    || !old.entrances().contains(pos)) && !atEntrance(actor,pos))return "entrance";
        }
        return book.change(actor.getUUID(),value.id(),value.version(),value);
    }
    public static PlaceNetwork.Reply handle(ServerPlayer actor,PlaceNetwork.Request r){
        var view=CityPublicAtlas.view(actor,r.view());
        if(actor instanceof FakePlayer || view==null || !view.worldId().equals(r.world()) || !view.dimension().equals(r.dimension()))
            return new PlaceNetwork.Reply(r.nonce(),"unavailable",List.of(),false);
        long sec=Util.getMillis()/1000;var budget=budgets.get(actor);boolean write=r.action()!=PlaceNetwork.Action.LIST;
        if(budget==null || sec!=budget.second())budget=new Budget(sec,0,0);
        if(budget.reads()>=4 || write && budget.writes()>=2)return new PlaceNetwork.Reply(r.nonce(),"busy",List.of(),false);
        budgets.put(actor,new Budget(sec,budget.reads()+1,budget.writes()+(write?1:0)));
        var book=PlaceBook.get(actor.serverLevel().getServer());
        String status=!book.available()?"incompatible":write?mutateOnce(actor,r):"ok";
        var rows=new ArrayList<AtlasPlace>();
        if(status.equals("saved") || status.equals("ok") || status.equals("conflict"))
            for(var p:book.entries.values())if(p.dimension().equals(r.dimension())){var visible=visible(actor,p);if(visible!=null)rows.add(visible);}
        boolean canLink=r.probe()!=null && source(actor,r.dimension(),r.probe(),true)!=null;
        return catalogReply(actor,r,status,rows,canLink);
    }
    static String mutateOnce(ServerPlayer actor,PlaceNetwork.Request request){
        var receipts=mutations.computeIfAbsent(actor,key->new LinkedHashMap<>());
        var old=receipts.get(request.nonce());
        if(old!=null)return old.view.equals(request.view()) && old.action==request.action() &&
                old.id.equals(request.id()) && old.version==request.expected() ? old.status : "unavailable";
        String result=switch(request.action()){
            case SAVE -> save(actor,request.value());
            case DELETE -> delete(actor,request.id(),request.expected());
            case LIST -> throw new IllegalArgumentException("Expected a place mutation");
        };
        receipts.put(request.nonce(),new Mutation(request.view(),request.action(),request.id(),request.expected(),result));
        if(receipts.size()>32)receipts.pollFirstEntry();
        return result;
    }
    static PlaceNetwork.Reply catalogReply(ServerPlayer actor,PlaceNetwork.Request request,String status,
                                          List<AtlasPlace> visibleRows,boolean canLink) {
        var previous=listings.remove(actor.getUUID());
        if(!status.equals("ok") && !status.equals("saved") && !status.equals("conflict"))
            return new PlaceNetwork.Reply(request.nonce(),status,List.of(),false);
        // Echo only a receipt the client actually applied. Every row has passed today's access checks.
        boolean unchanged=request.action()==PlaceNetwork.Action.LIST && previous!=null && previous.actor.get()==actor &&
                previous.view.equals(request.view()) && previous.world.equals(request.world()) &&
                previous.dimension.equals(request.dimension()) && previous.receipt.equals(request.id()) &&
                previous.rows.equals(visibleRows);
        var rows=unchanged ? previous.rows : List.copyOf(visibleRows);
        listings.put(actor.getUUID(),new Listing(new java.lang.ref.WeakReference<>(actor),request.view(),request.world(),
                request.dimension(),request.nonce(),rows));
        if(listings.size()>64)listings.pollFirstEntry();
        return new PlaceNetwork.Reply(request.nonce(),unchanged?"unchanged":status,unchanged?List.of():rows,canLink);
    }
}
