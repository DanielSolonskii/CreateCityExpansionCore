package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoTrainRegistry;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.graph.EdgePointType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Explicit server-authorized schedule assignment; no schedule items or invented stations/trains. */
public final class CargoSchedules extends SavedData {
    private final Map<UUID,CompoundTag> generated=new HashMap<>();
    public static CargoSchedules get(MinecraftServer server) {
        if(!server.isSameThread())throw new IllegalStateException("Server thread");
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CargoSchedules::new,CargoSchedules::load,null),"ccec_cargo_schedules");
    }
    public static String state(Train train) {
        if(train==null)return "MISSING";
        if(train.runtime.getSchedule()==null)return "NO_SCHEDULE";
        return train.runtime.paused?"PAUSED":"RUNNING";
    }
    /** Read-only readiness: a service must actually visit this directed pair. */
    public static boolean serves(MinecraftServer server,Train train,UUID from,UUID to) {
        var schedule=train.runtime.getSchedule();
        if(schedule==null||train.runtime.paused||train.manualTick||(!train.hasForwardConductor()&&!train.hasBackwardConductor()))return false;
        var a=CargoRailConnections.station(server,from);var b=CargoRailConnections.station(server,to);
        if(a==null||b==null)return false;
        for(String name:List.of(a.station().name,b.station().name))if(RailwaySavedData.load(server).getTrackNetworks().values().stream().flatMap(g->g.getPoints(EdgePointType.STATION).stream()).filter(station->station.name.equals(name)).count()!=1)return false;
        var destinations=new ArrayList<String>();
        for(var entry:schedule.entries)if(entry.instruction instanceof DestinationInstruction)
            destinations.add(entry.instruction.getData().getString("Text"));
        // Exact destinations are intentionally conservative: ambiguous globs cannot prove service.
        return CargoSchedulePlan.serves(destinations,CargoSchedulePlan.filter(a.station().name),CargoSchedulePlan.filter(b.station().name),schedule.cyclic);
    }
    public void apply(ServerPlayer actor,UUID lineId,int seconds) {
        var server=actor.getServer();var registry=CargoLineRegistry.get(server);
        var line=registry.find(lineId).orElseThrow();
        if(!CargoLineRegistry.manages(actor,line)||!line.enabled())throw new IllegalArgumentException("schedule_permission");
        var names=new ArrayList<String>();UUID graphId=null;
        for(var stop:line.stops()) {
            if(!stop.point().equals(registry.stations().get(stop.station())))throw new IllegalArgumentException("schedule_station_missing");
            var point=com.example.examplemod.cargo.CargoPointRegistry.find(server,stop.point()).orElseThrow();
            if(!CargoOwners.permits(server,CargoLineRegistry.access(server,line).owner(),
                CargoOwners.get(server).access("point/"+point.id(),point.owner(),CargoTrainRegistry.AccessMode.PRIVATE)))
                throw new IllegalArgumentException("schedule_permission");
            var station=CargoRailConnections.station(server,stop.station());
            if(station==null||station.station().assembling)throw new IllegalArgumentException("schedule_station_missing");
            if(graphId!=null&&!graphId.equals(station.graph().id))throw new IllegalArgumentException("schedule_disconnected");
            graphId=station.graph().id;String name=station.station().name;
            long matches=RailwaySavedData.load(server).getTrackNetworks().values().stream()
                .flatMap(g->g.getPoints(EdgePointType.STATION).stream()).filter(s->s.name.equals(name)).count();
            if(matches!=1)throw new IllegalArgumentException("schedule_station_name");
            names.add(name);
        }
        var route=CargoSchedulePlan.cycle(names,seconds);
        // Both directions are needed for the generated return journey. Never assume a reverse connection.
        for(int i=1;i<line.stops().size();i++) {
            var a=line.stops().get(i-1);var b=line.stops().get(i);
            if(CargoRailConnections.path(server,a.station(),b.station()).isEmpty()||CargoRailConnections.path(server,b.station(),a.station()).isEmpty())
                throw new IllegalArgumentException("schedule_disconnected");
        }
        var schedules=new LinkedHashMap<Train,Schedule>();
        var railway=RailwaySavedData.load(server);
        for(UUID id:new TreeSet<>(line.trains())) {
            var train=railway.getTrains().get(id);var registration=CargoTrainRegistry.find(server,id).orElse(null);
            if(train==null||registration==null||!registration.ownerId().equals(train.owner)||train.invalid||train.derailed||train.graph==null||!train.graph.id.equals(graphId))
                throw new IllegalArgumentException("schedule_train_missing");
            if(!CenterOperations.access(actor,"train",id).owner().manages(server,actor.getUUID()))throw new IllegalArgumentException("schedule_permission");
            if(!train.hasForwardConductor()&&!train.hasBackwardConductor())throw new IllegalArgumentException("schedule_conductor");
            var current=train.runtime.getSchedule();var tag=current==null?null:current.write(server.registryAccess());
            if(!CargoSchedulePlan.replaceable(tag,generated.get(id),train.runtime.isAutoSchedule))throw new IllegalArgumentException("schedule_existing");
            var next=build(server.registryAccess(),route,seconds);
            boolean same=tag!=null&&CargoSchedulePlan.shape(tag).equals(CargoSchedulePlan.shape(next.write(server.registryAccess())));
            if(!same&&(train.manualTick||Math.abs(train.speed)>0.001))throw new IllegalArgumentException("schedule_train_moving");
            if(!same&&current!=null&&CargoShipmentRegistry.get(server).inUse("train",id))throw new IllegalArgumentException("shipment_in_use");
            boolean otherShipment=CargoShipmentRegistry.get(server).all().stream()
                .filter(s->s.stage()!=CargoShipmentRegistry.Stage.COMPLETED&&s.stage()!=CargoShipmentRegistry.Stage.CANCELLED)
                .flatMap(s->s.route().stream()).anyMatch(leg->leg.train().equals(id)&&!leg.line().equals(lineId));
            if(otherShipment)throw new IllegalArgumentException("shipment_in_use");
            schedules.put(train,same?current:next);
        }
        if(schedules.isEmpty())throw new IllegalArgumentException("schedule_train_missing");
        // Validate the complete fleet before changing any train. Retrying an identical route preserves progress.
        schedules.forEach((train,schedule)->{
            if(train.runtime.getSchedule()!=schedule)train.runtime.setSchedule(schedule,true);
            else train.runtime.paused=false;
            generated.put(train.id,CargoSchedulePlan.shape(schedule.write(server.registryAccess())));
        });
        setDirty();railway.setDirty();
    }
    static Schedule build(HolderLookup.Provider lookup,List<String> route,int seconds) {
        var schedule=new Schedule();schedule.cyclic=true;
        for(String name:route) {
            var destination=new DestinationInstruction();var data=new CompoundTag();data.putString("Text",CargoSchedulePlan.filter(name));destination.setData(lookup,data);
            var delay=new ScheduledDelay();var wait=new CompoundTag();wait.putInt("Value",seconds);wait.putInt("TimeUnit",TimedWaitCondition.TimeUnit.SECONDS.ordinal());delay.setData(lookup,wait);
            schedule.entries.add(new ScheduleEntry(destination,List.of(List.of(delay))));
        }
        return schedule;
    }
    public static CargoSchedules load(CompoundTag tag,HolderLookup.Provider lookup) {
        if(tag.getInt("Version")!=1)throw new IllegalArgumentException("Schedule version");
        var result=new CargoSchedules();for(Tag raw:tag.getList("Trains",10)){var entry=(CompoundTag)raw;if(!entry.hasUUID("Id")||!entry.contains("Schedule",10)||result.generated.putIfAbsent(entry.getUUID("Id"),entry.getCompound("Schedule").copy())!=null)throw new IllegalArgumentException("Invalid generated schedule");}return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putInt("Version",1);var entries=new ListTag();generated.forEach((id,schedule)->{var entry=new CompoundTag();entry.putUUID("Id",id);entry.put("Schedule",schedule.copy());entries.add(entry);});tag.put("Trains",entries);return tag;
    }
}
