package com.example.examplemod.economy;

import com.example.examplemod.city.*;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.*;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Short-lived setup authority. No second point registry, money ledger or persisted draft. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class FreightGuideService {
    private static final long LIFETIME=20*60*1000L;
    private static final Map<MinecraftServer,Map<UUID,Session>> sessions=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Long>> lastBegin=new WeakHashMap<>();
    private static final class Session {
        final UUID id=UUID.randomUUID(),city,point;
        final net.minecraft.resources.ResourceLocation dimension;
        final long expires=net.minecraft.Util.getMillis()+LIFETIME;
        BlockPos packager,postbox;
        long lastPick,lastRefresh,lastFinish;int mode=2;boolean rebind;
        Session(City c,ServerPlayer p,FreightPoint existing) {
            city=c.getId();dimension=p.serverLevel().dimension().location();point=existing==null?null:existing.id;
            if(existing!=null){packager=existing.packager;postbox=existing.postbox;}
        }
    }
    private FreightGuideService() {}
    public static void handle(ServerPlayer p,FreightGuideRequest request) {
        if(p instanceof FakePlayer)return;
        var server=p.getServer();long now=net.minecraft.Util.getMillis();
        var map=sessions.computeIfAbsent(server,k->new HashMap<>());
        if(request.action()==FreightGuideRequest.Action.BEGIN||request.action()==FreightGuideRequest.Action.BEGIN_REBIND) {
            var budget=lastBegin.computeIfAbsent(server,k->new HashMap<>());
            if(now-budget.getOrDefault(p.getUUID(),0L)<750)return;
            budget.put(p.getUUID(),now);
            if(!(p.containerMenu instanceof CityPylonMenu menu)||!menu.getPylonPos().equals(request.pos())
                    ||p.distanceToSqr(request.pos().getCenter())>64){replyClosed(p,"pylon_required");return;}
            City city=CityManager.get(server.overworld()).getCityByPylon(p.serverLevel().dimension().location(),request.pos());
            if(city==null||!city.isAdmin(p.getUUID())){replyClosed(p,"permission");return;}
            FreightPoint point=request.point()==null?null:EconomyManager.get(server.overworld()).freight().point(request.point());
            if(request.point()!=null&&(point==null||point.removed||!point.cityId.equals(city.getId())||!point.dimension.equals(p.serverLevel().dimension().location())))return;
            if(request.action()==FreightGuideRequest.Action.BEGIN_REBIND&&(point==null||point.kind==FreightPoint.Kind.HUB)){replyClosed(p,"hub_rebind");return;}
            Session s=new Session(city,p,point);if(point!=null)s.mode=point.mode.ordinal();s.rebind=request.action()==FreightGuideRequest.Action.BEGIN_REBIND;if(s.rebind){s.packager=null;s.postbox=null;}map.put(p.getUUID(),s);reply(p,s,"ok");return;
        }
        Session s=map.get(p.getUUID());
        if(s==null||!s.id.equals(request.session()))return;
        City city=CityManager.get(server.overworld()).getCity(s.city);
        if(now>s.expires||city==null||!city.isAdmin(p.getUUID())||!s.dimension.equals(p.serverLevel().dimension().location())) {
            map.remove(p.getUUID());replyClosed(p,"expired");return;
        }
        // Read-only polling must never swallow a player's confirmation or cancellation.
        if(request.action()==FreightGuideRequest.Action.REFRESH){if(now-s.lastRefresh<250)return;s.lastRefresh=now;}
        if(request.action()==FreightGuideRequest.Action.PICK){if(now-s.lastPick<150)return;s.lastPick=now;}
        if(request.action()==FreightGuideRequest.Action.FINISH){if(now-s.lastFinish<250)return;s.lastFinish=now;}
        if(request.action()==FreightGuideRequest.Action.CANCEL){map.remove(p.getUUID());replyClosed(p,"cancelled");return;}
        if(request.action()==FreightGuideRequest.Action.PICK&&(s.point==null||s.rebind)) {
            BlockPos pos=request.pos();
            if(!p.serverLevel().hasChunkAt(pos)||p.distanceToSqr(pos.getCenter())>36){reply(p,s,"reach");return;}
            City owner=CityManager.get(server.overworld()).getCityAt(s.dimension,pos);
            if(owner!=null&&!owner.getId().equals(s.city)){reply(p,s,"wrong_city");return;}
            var be=p.serverLevel().getBlockEntity(pos);
            if(be instanceof PackagerBlockEntity&&!(be instanceof RepackagerBlockEntity)){s.packager=pos.immutable();}
            else if(be instanceof PostboxBlockEntity&&s.packager!=null){
                if(!FreightGuideGeometry.inRange(s.packager,pos)){reply(p,s,"packager_too_far");return;}
                s.postbox=pos.immutable();
            }else {reply(p,s,s.packager==null?"pick_packager":"pick_postbox");return;}
        }
        if(request.action()==FreightGuideRequest.Action.FINISH&&(s.point==null||s.rebind)) {
            if(s.packager==null||s.postbox==null||request.name().isBlank()){reply(p,s,"incomplete");return;}
            if(!observations(p,s,request.mode()).getBoolean("Ready")){reply(p,s,"incomplete");return;}
            String result=s.rebind?EconomyManager.get(server.overworld()).freight().rebind(p.serverLevel(),city,p.getUUID(),s.point,s.postbox,s.packager):EconomyManager.get(server.overworld()).freight().register(p.serverLevel(),city,p.getUUID(),s.postbox,s.packager,request.name().strip(),request.mode());
            if(result.equals("ok")){map.remove(p.getUUID());replyClosed(p,"registered");return;}
            reply(p,s,result);return;
        }
        s.mode=request.mode();reply(p,s,"ok",s.mode);
    }
    private static CompoundTag observations(ServerPlayer p,Session s,int mode) {
        var out=new CompoundTag();var checks=new ListTag();var level=p.serverLevel();
        boolean ready=s.packager!=null&&s.postbox!=null;
        if(s.packager!=null) {
            boolean loaded=level.hasChunkAt(s.packager);check(checks,"loaded",loaded);
            var be=loaded?level.getBlockEntity(s.packager):null;
            boolean valid=be instanceof PackagerBlockEntity&&!(be instanceof RepackagerBlockEntity);
            check(checks,"packager",valid);ready&=valid;
            if(valid) {
                var pe=(PackagerBlockEntity)be;
                var facing=level.getBlockState(s.packager).getValue(BlockStateProperties.FACING);
                BlockPos warehouse=FreightGuideGeometry.warehouse(s.packager,facing);
                out.putLong("Warehouse",warehouse.asLong());out.putString("Facing",facing.getName());
                boolean warehouseLoaded=level.hasChunkAt(warehouse);check(checks,"warehouse_loaded",warehouseLoaded);
                boolean inventory=warehouseLoaded&&pe.targetInventory.hasInventory();check(checks,"inventory",inventory);ready&=inventory;
                if(mode!=FreightPoint.Mode.SEND.ordinal()) {
                    boolean receive=warehouseLoaded&&FreightStorage.supported(level,warehouse);
                    check(checks,"receiver",receive);ready&=receive;
                }
            }
        }
        if(s.postbox!=null) {
            var be=level.hasChunkAt(s.postbox)?level.getBlockEntity(s.postbox):null;
            boolean box=be instanceof PostboxBlockEntity;check(checks,"postbox",box);ready&=box;
            boolean station=box&&((PostboxBlockEntity)be).target instanceof PackagePortTarget.TrainStationFrogportTarget target
                    &&CreateRailwayAdapter.stationAt(level.dimension(),s.postbox.offset(target.relativePos))!=null;
            check(checks,"station",station);ready&=station;
            if(box&&((PostboxBlockEntity)be).target instanceof PackagePortTarget.TrainStationFrogportTarget target)
                out.putLong("Station",s.postbox.offset(target.relativePos).asLong());
            if(s.packager!=null){boolean range=FreightGuideGeometry.inRange(s.packager,s.postbox);check(checks,"range",range);ready&=range;}
        }
        out.put("Checks",checks);out.putBoolean("Ready",ready);return out;
    }
    private static void check(ListTag list,String key,boolean ok){var t=new CompoundTag();t.putString("Key",key);t.putBoolean("Ok",ok);list.add(t);}
    private static void reply(ServerPlayer p,Session s,String result){reply(p,s,result,s.mode);}
    private static void reply(ServerPlayer p,Session s,String result,int mode) {
        FreightPoint point=s.point==null?null:EconomyManager.get(p.getServer().overworld()).freight().point(s.point);
        if(point!=null){if(!s.rebind){s.packager=point.packager;s.postbox=point.postbox;}mode=point.mode.ordinal();}
        var out=observations(p,s,mode);out.putUUID("Session",s.id);out.putString("Dimension",s.dimension.toString());
        out.putString("Result",result);out.putBoolean("Existing",s.point!=null);out.putBoolean("Rebind",s.rebind);out.putString("Name",point==null?"":point.name);
        out.putInt("Mode",mode);out.putInt("Step",s.packager==null?0:s.postbox==null?1:2);
        if(s.packager!=null)out.putLong("Packager",s.packager.asLong());
        if(s.postbox!=null)out.putLong("Postbox",s.postbox.asLong());
        if(point!=null)out.putString("PointStatus",point.status);
        PacketDistributor.sendToPlayer(p,new FreightGuideState(out));
    }
    private static void replyClosed(ServerPlayer p,String result) {
        var out=new CompoundTag();out.putBoolean("Closed",true);out.putString("Result",result);
        PacketDistributor.sendToPlayer(p,new FreightGuideState(out));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(e.getServer().getTickCount()%20!=0)return;
        var map=sessions.get(e.getServer());if(map==null)return;
        for(var id:List.copyOf(map.keySet())){
            var p=e.getServer().getPlayerList().getPlayer(id);Session s=map.get(id);
            if(p==null){map.remove(id);continue;}
            var city=CityManager.get(e.getServer().overworld()).getCity(s.city);
            if(net.minecraft.Util.getMillis()>s.expires||!p.serverLevel().dimension().location().equals(s.dimension)||city==null||!city.isAdmin(id)){
                map.remove(id);replyClosed(p,"expired");
            }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){var map=sessions.get(p.getServer());if(map!=null)map.remove(p.getUUID());var b=lastBegin.get(p.getServer());if(b!=null)b.remove(p.getUUID());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){sessions.remove(e.getServer());lastBegin.remove(e.getServer());}
}
