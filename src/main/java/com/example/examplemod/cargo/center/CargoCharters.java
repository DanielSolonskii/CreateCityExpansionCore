package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent opt-in dispatch. Native mail receipts, never elapsed time, release a loaded train. */
public final class CargoCharters extends SavedData {
    private final Map<UUID,CompoundTag> permits=new HashMap<>(),jobs=new HashMap<>();
    public static CargoCharters get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CargoCharters::new,CargoCharters::load,null),"ccec_cargo_charters");}
    public void authorize(ServerPlayer actor,UUID lineId,boolean enabled,boolean returns){
        var s=actor.getServer();var line=CargoLineRegistry.get(s).find(lineId).orElseThrow();
        if(!CargoLineRegistry.manages(actor,line)||line.mode()!=CargoLineRegistry.Mode.CHARTER)throw new IllegalArgumentException("charter_permission");
        CargoShipmentRegistry.get(s).requireUnused("line",lineId);
        var permit=new CompoundTag();permit.put("Owner",CargoLineRegistry.access(s,line).owner().save());permit.putBoolean("Return",returns);
        var trains=new ListTag();for(UUID id:line.trains()){
            if(!CenterOperations.access(actor,"train",id).owner().manages(s,actor.getUUID()))throw new IllegalArgumentException("charter_permission");
            var t=new CompoundTag();t.putUUID("Id",id);t.put("Owner",CenterOperations.access(actor,"train",id).owner().save());trains.add(t);
        }
        if(returns)for(int i=1;i<line.stops().size();i++)if(CargoRailConnections.path(s,line.stops().get(i).station(),line.stops().get(i-1).station()).isEmpty())throw new IllegalArgumentException("schedule_disconnected");
        permit.put("Trains",trains);if(enabled)permits.put(lineId,permit);else permits.remove(lineId);setDirty();
    }
    private boolean permitted(MinecraftServer s,CargoLineRegistry.Line line,UUID train){
        var permit=permits.get(line.id());if(permit==null||!permit.getCompound("Owner").equals(CargoLineRegistry.access(s,line).owner().save()))return false;
        for(Tag raw:permit.getList("Trains",10)){var t=(CompoundTag)raw;if(t.getUUID("Id").equals(train)){
            var registration=com.example.examplemod.cargo.CargoTrainRegistry.find(s,train).orElse(null);
            return registration!=null&&t.getCompound("Owner").equals(CargoOwners.get(s).access("train/"+train,registration.ownerId(),registration.accessMode()).owner().save());
        }}return false;
    }
    private static boolean exactNames(MinecraftServer s,UUID from,UUID to){
        for(UUID id:List.of(from,to)){var station=CargoRailConnections.station(s,id);if(station==null)return false;String name=station.station().name;
            if(name.isBlank()||name.contains("*")||name.contains("?"))return false;
            if(RailwaySavedData.load(s).getTrackNetworks().values().stream().flatMap(g->g.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION).stream()).filter(p->p.name.equals(name)).count()!=1)return false;
        }return true;
    }
    public boolean available(MinecraftServer s,CargoLineRegistry.Line line,Train train,UUID from,UUID to){
        if(!permitted(s,line,train.id)||train.manualTick||(!train.hasForwardConductor()&&!train.hasBackwardConductor())||!exactNames(s,from,to))return false;
        var job=jobs.get(train.id);
        if(job!=null&&!job.getString("Phase").equals("DONE"))return false;
        if(CargoShipmentRegistry.get(s).inUse("train",train.id)||Math.abs(train.speed)>0.001)return false;
        var origin=train.getCurrentStation();if(origin==null||(!origin.id.equals(from)&&CargoRailConnections.path(s,origin.id,from).isEmpty()))return false;
        var schedule=train.runtime.getSchedule();
        return schedule==null||(job!=null&&owned(s,train,job));
    }
    public boolean assigned(MinecraftServer s,CargoShipmentRegistry.Shipment shipment,CargoRoutePlanner.Leg leg){var job=jobs.get(leg.train());var line=CargoLineRegistry.get(s).find(leg.line()).orElse(null);return job!=null&&job.getUUID("Shipment").equals(shipment.id())&&!job.getString("Phase").equals("DONE")&&line!=null&&line.enabled()&&permitted(s,line,leg.train());}
    private static UUID[] stations(MinecraftServer s,CargoRoutePlanner.Leg leg){
        var line=CargoLineRegistry.get(s).find(leg.line()).orElseThrow();
        for(int i=1;i<line.stops().size();i++)if(line.stops().get(i-1).point().equals(leg.from())&&line.stops().get(i).point().equals(leg.next()))return new UUID[]{line.stops().get(i-1).station(),line.stops().get(i).station()};
        throw new IllegalArgumentException("schedule_station_missing");
    }
    private static boolean owned(MinecraftServer s,Train train,CompoundTag job){return train.runtime.getSchedule()!=null&&job.contains("Schedule")&&CargoSchedulePlan.shape(train.runtime.getSchedule().write(s.registryAccess())).equals(job.getCompound("Schedule"));}
    /** Called on funded persisted orders before packaging; replay recreates missing jobs without another order. */
    public void prepare(MinecraftServer s,CargoShipmentRegistry.Shipment shipment){
        for(var leg:shipment.route()){
            var line=CargoLineRegistry.get(s).find(leg.line()).orElseThrow();if(line.mode()!=CargoLineRegistry.Mode.CHARTER)continue;
            var old=jobs.get(leg.train());if(old!=null&&old.getUUID("Shipment").equals(shipment.id()))continue;
            if(old!=null&&!old.getString("Phase").equals("DONE"))throw new IllegalArgumentException("charter_busy");
            var train=RailwaySavedData.load(s).getTrains().get(leg.train());var pair=stations(s,leg);
            if(train==null||train.invalid||train.derailed||train.manualTick||!permitted(s,line,leg.train())||!exactNames(s,pair[0],pair[1])||(!train.hasForwardConductor()&&!train.hasBackwardConductor())||Math.abs(train.speed)>0.001||(train.runtime.getSchedule()!=null&&(old==null||!owned(s,train,old))))throw new IllegalArgumentException("charter_not_ready");
            var job=new CompoundTag();if(old!=null&&old.contains("Schedule"))job.put("Schedule",old.getCompound("Schedule").copy());job.putUUID("Shipment",shipment.id());job.putString("Phase","PICKUP");job.putInt("Hop",shipment.route().indexOf(leg));job.putUUID("Home",pair[0]);job.putBoolean("Return",permits.get(line.id()).getBoolean("Return"));jobs.put(leg.train(),job);setDirty();
        }
    }
    private void target(MinecraftServer s,Train train,CompoundTag job,UUID station){
        var real=CargoRailConnections.station(s,station);if(real==null)throw new IllegalArgumentException("schedule_station_missing");
        var next=CargoSchedules.build(s.registryAccess(),List.of(real.station().name),86400);next.cyclic=false;
        var shape=CargoSchedulePlan.shape(next.write(s.registryAccess()));
        if(train.runtime.getSchedule()!=null&&!owned(s,train,job))throw new IllegalArgumentException("schedule_existing");
        if(!job.getCompound("Schedule").equals(shape)||train.runtime.getSchedule()==null){
            job.put("Schedule",shape);setDirty();train.runtime.setSchedule(next,true);RailwaySavedData.load(s).setDirty();
        }
    }
    public boolean locks(MinecraftServer s,String kind,UUID id){for(var job:jobs.values())if(!job.getString("Phase").equals("DONE")){var shipment=CargoShipmentRegistry.get(s).find(job.getUUID("Shipment")).orElse(null);if(shipment!=null&&ShipmentLocks.blocks(CargoShipmentRegistry.Stage.REQUESTED,kind,id,new ShipmentLocks.References(shipment.sourceWarehouse(),shipment.destinationWarehouse(),shipment.source(),shipment.finalPoint(),shipment.route())))return true;}return false;}
    public String progress(UUID shipment){for(var job:jobs.values())if(job.getUUID("Shipment").equals(shipment)&&!job.getString("Phase").equals("DONE"))return "CHARTER_"+job.getString("Phase");return "";}
    public boolean prepared(MinecraftServer s,CargoShipmentRegistry.Shipment shipment){
        for(var leg:shipment.route()){var line=CargoLineRegistry.get(s).find(leg.line()).orElse(null);if(line==null)return false;if(line.mode()!=CargoLineRegistry.Mode.CHARTER)continue;var job=jobs.get(leg.train());var train=RailwaySavedData.load(s).getTrains().get(leg.train());if(job==null||train==null||!job.getUUID("Shipment").equals(shipment.id())||!owned(s,train,job))return false;}return true;
    }
    static boolean loaded(int packaged,int quantity,int recovered,int boarded){return packaged==quantity&&boarded>=quantity-recovered;}
    public void tick(MinecraftServer s){
        for(var entry:jobs.entrySet()){
            var job=entry.getValue();if(job.getString("Phase").equals("DONE"))continue;
            var shipment=CargoShipmentRegistry.get(s).find(job.getUUID("Shipment")).orElse(null);var train=RailwaySavedData.load(s).getTrains().get(entry.getKey());if(shipment==null||train==null)continue;
            try{
                var order=com.example.examplemod.economy.EconomyManager.get(s.overworld()).cargo().order(shipment.id()).orElse(null);
                if(order==null)continue;
                if(order.state()==CargoCommerceBook.State.REFUNDED||order.state()==CargoCommerceBook.State.CANCELLED){if(owned(s,train,job))train.runtime.paused=true;job.putString("Phase","DONE");setDirty();continue;}
                if(train.invalid||train.derailed||train.manualTick)throw new IllegalArgumentException("charter_not_ready");
                if(shipment.stage()==CargoShipmentRegistry.Stage.CANCELLED||(shipment.stage()==CargoShipmentRegistry.Stage.COMPLETED&&!job.getBoolean("Return"))){if(owned(s,train,job)){train.runtime.paused=true;RailwaySavedData.load(s).setDirty();}job.putString("Phase","DONE");setDirty();continue;}
                if(shipment.stage()==CargoShipmentRegistry.Stage.BLOCKED||shipment.stage()==CargoShipmentRegistry.Stage.FAILED)continue;
                int hop=job.getInt("Hop");var leg=shipment.route().get(hop);var line=CargoLineRegistry.get(s).find(leg.line()).orElseThrow();
                if(!line.enabled()||!com.example.examplemod.cargo.CargoPackageAssignment.mayAssignTrain(s,CargoLineRegistry.access(s,line).owner(),train.id)||!permitted(s,line,train.id)||!line.trains().contains(train.id))throw new IllegalArgumentException("charter_permission");
                var pair=stations(s,leg);String phase=job.getString("Phase");
                UUID destination=phase.equals("RETURN")?job.getUUID("Home"):phase.equals("PICKUP")?pair[0]:pair[1];
                if(!exactNames(s,destination,destination))throw new IllegalArgumentException("schedule_station_name");
                target(s,train,job,destination);
                var current=train.getCurrentStation();boolean arrived=current!=null&&current.id.equals(destination);
                if(train.runtime.paused!=arrived){train.runtime.paused=arrived;RailwaySavedData.load(s).setDirty();} // Hold at the station until native receipts prove loading/unloading.
                if(!arrived)continue;
                if(phase.equals("RETURN")){job.putString("Phase","DONE");setDirty();continue;}
                int required=shipment.quantity()-shipment.recovered();
                boolean advance=phase.equals("PICKUP")?loaded(shipment.packaged(),shipment.quantity(),shipment.recovered(),shipment.boardedUnits(train.id,pair[0],hop)):shipment.hopUnits(hop)>=required;
                if(!advance)continue;
                if(phase.equals("PICKUP"))job.putString("Phase","DELIVERY");
                else{
                    int next=-1;for(int i=hop+1;i<shipment.route().size();i++)if(shipment.route().get(i).train().equals(train.id)){next=i;break;}
                    if(next>=0){job.putInt("Hop",next);job.putString("Phase","PICKUP");}
                    else if(job.getBoolean("Return"))job.putString("Phase","RETURN");else job.putString("Phase","DONE");
                }setDirty();RailwaySavedData.load(s).setDirty();
            }catch(RuntimeException failure){CargoShipmentRegistry.get(s).blocked(shipment.id(),"charter_dispatch_blocked");if(owned(s,train,job))train.runtime.paused=true;}
        }
    }
    public static CargoCharters load(CompoundTag tag,HolderLookup.Provider lookup){if(tag.getInt("Version")!=1)throw new IllegalArgumentException("Charter version");var data=new CargoCharters();read(tag,"Permits",data.permits);read(tag,"Jobs",data.jobs);for(var job:data.jobs.values())if(!job.hasUUID("Shipment")||!job.hasUUID("Home")||job.getInt("Hop")<0||!Set.of("PICKUP","DELIVERY","RETURN","DONE").contains(job.getString("Phase")))throw new IllegalArgumentException("Invalid charter job");return data;}
    private static void read(CompoundTag tag,String key,Map<UUID,CompoundTag> target){for(Tag raw:tag.getList(key,10)){var value=(CompoundTag)raw;if(!value.hasUUID("Id")||target.putIfAbsent(value.getUUID("Id"),value.getCompound("Data").copy())!=null)throw new IllegalArgumentException("Invalid charter entry");}}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){tag.putInt("Version",1);write(tag,"Permits",permits);write(tag,"Jobs",jobs);return tag;}
    private static void write(CompoundTag tag,String key,Map<UUID,CompoundTag> values){var list=new ListTag();values.forEach((id,data)->{var t=new CompoundTag();t.putUUID("Id",id);t.put("Data",data.copy());list.add(t);});tag.put(key,list);}
}
