package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Line metadata references real Create stations/trains. Never edits Create schedules. */
public final class CargoLineRegistry extends net.minecraft.world.level.saveddata.SavedData {
    public enum Mode { REGULAR, CHARTER }
    public record Stop(UUID point,UUID station) {}
    public record Line(UUID id,UUID creator,String name,List<Stop> stops,Set<UUID> trains,Mode mode,long tariff,String terms,boolean enabled){public Line{stops=List.copyOf(stops);trains=Set.copyOf(trains);}}
    private final Map<UUID,Line> lines=new HashMap<>();
    private final Map<UUID,UUID> stationPoints=new HashMap<>();
    public static CargoLineRegistry get(MinecraftServer s){if(!s.isSameThread())throw new IllegalStateException("Server thread");return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CargoLineRegistry::new,CargoLineRegistry::load,null),"ccec_cargo_lines");}
    public List<Line> all(){return List.copyOf(lines.values());}
    public Optional<Line> find(UUID id){return Optional.ofNullable(lines.get(id));}
    public Map<UUID,UUID> stations(){return Map.copyOf(stationPoints);}
    public static CargoOwners.Access access(MinecraftServer s,Line l){return CargoOwners.get(s).access("line/"+l.id(),l.creator(),CargoTrainRegistry.AccessMode.PRIVATE);}
    public static boolean manages(ServerPlayer actor,Line l){return access(actor.getServer(),l).owner().manages(actor.getServer(),actor.getUUID());}
    public boolean attachStation(ServerPlayer actor,UUID pointId){
        var s=actor.getServer();var point=CargoPointRegistry.find(s,pointId).orElse(null);
        if(point==null||!CargoOwners.point(s,point).manages(s,actor.getUUID())||!(CargoInfrastructure.lookedAt(actor) instanceof StationBlockEntity be)||be.getStation()==null)return false;
        var station=be.getStation();
        boolean connected=point.routers().stream().anyMatch(r->r.dimension().equals(actor.level().dimension().location())&&station.connectedPorts.containsKey(r.pos().above())&&CargoInfrastructure.router(s,r)!=null);
        if(!connected||stationPoints.containsKey(station.id))return false;
        stationPoints.put(station.id,pointId);setDirty();return true;
    }
    public boolean detachStation(ServerPlayer actor,UUID stationId){var p=stationPoints.get(stationId);var point=p==null?null:CargoPointRegistry.find(actor.getServer(),p).orElse(null);if(point==null||!CargoOwners.point(actor.getServer(),point).manages(actor.getServer(),actor.getUUID()))return false;CargoShipmentRegistry.get(actor.getServer()).requireUnused("point",p);stationPoints.remove(stationId);setDirty();return true;}
    public Line create(ServerPlayer actor,String name,OwnerRef owner,List<Stop> stops,Set<UUID> trains,Mode mode,long tariff,String terms){
        if(!owner.manages(actor.getServer(),actor.getUUID()))throw new IllegalArgumentException("Permission denied");
        var line=new Line(UUID.randomUUID(),actor.getUUID(),CargoInfrastructure.name(name),stops,trains,mode,tariff,terms,true);validate(actor.getServer(),line,owner);
        lines.put(line.id(),line);CargoOwners.get(actor.getServer()).change(actor.getServer(),actor.getUUID(),"line/"+line.id(),line.creator(),CargoTrainRegistry.AccessMode.PRIVATE,new CargoOwners.Access(owner,CargoTrainRegistry.AccessMode.PRIVATE,Set.of(),Set.of()));setDirty();return line;
    }
    public boolean edit(ServerPlayer actor,UUID id,String name,List<Stop> stops,Set<UUID> trains,Mode mode,long tariff,String terms,boolean enabled){var old=lines.get(id);if(old==null||!manages(actor,old))return false;if(!old.stops().equals(stops)||!old.trains().equals(trains))CargoShipmentRegistry.get(actor.getServer()).requireUnused("line",id);var next=new Line(id,old.creator(),CargoInfrastructure.name(name),stops,trains,mode,tariff,terms,enabled);validate(actor.getServer(),next,access(actor.getServer(),old).owner());lines.put(id,next);setDirty();return true;}
    public boolean remove(ServerPlayer actor,UUID id){var line=lines.get(id);if(line==null||!manages(actor,line))return false;CargoShipmentRegistry.get(actor.getServer()).requireUnused("line",id);lines.remove(id);setDirty();return true;}
    private void validate(MinecraftServer s,Line l,OwnerRef owner){
        if(l.tariff()<0||l.tariff()>1_000_000_000L||l.terms().length()>256||l.stops().size()<2||l.stops().size()>32||l.trains().isEmpty()||l.trains().size()>32)throw new IllegalArgumentException("Invalid line conditions");
        var visited=new HashSet<UUID>();for(var stop:l.stops()){var point=CargoPointRegistry.find(s,stop.point()).orElse(null);if(point==null||!visited.add(stop.point())||!stop.point().equals(stationPoints.get(stop.station())))throw new IllegalArgumentException("Invalid terminal/station binding");
            if(!CargoOwners.permits(s,owner,CargoOwners.get(s).access("point/"+point.id(),point.owner(),CargoTrainRegistry.AccessMode.PRIVATE)))throw new IllegalArgumentException("Line operator has no terminal access");}
        for(int i=1;i<l.stops().size();i++)if(CargoRailConnections.path(s,l.stops().get(i-1).station(),l.stops().get(i).station()).isEmpty())throw new IllegalArgumentException("Stations have no verified railway connection");
        for(UUID train:l.trains())if(!CargoPackageAssignment.mayAssignTrain(s,owner,train))throw new IllegalArgumentException("Train unavailable or forbidden");
    }
    public UUID availableTrain(MinecraftServer s,Line line,OwnerRef requester,UUID from,UUID next){
        if(!line.enabled()||!CargoOwners.permits(s,requester,access(s,line)))return null;
        int index=-1;for(int i=0;i<line.stops().size()-1;i++)if(line.stops().get(i).point().equals(from)&&line.stops().get(i+1).point().equals(next))index=i;
        if(index<0)return null;var a=line.stops().get(index);var b=line.stops().get(index+1);
        for(var stop:List.of(a,b)){var point=CargoPointRegistry.find(s,stop.point()).orElse(null);if(point==null||!CargoOwners.permits(s,access(s,line).owner(),CargoOwners.get(s).access("point/"+point.id(),point.owner(),CargoTrainRegistry.AccessMode.PRIVATE)))return null;}
        if(!from.equals(stationPoints.get(a.station()))||!next.equals(stationPoints.get(b.station())))return null;
        var path=CargoRailConnections.path(s,a.station(),b.station());if(path.isEmpty())return null;
        var trains=RailwaySavedData.load(s).getTrains();
        return line.trains().stream().sorted().filter(id->{var t=trains.get(id);return t!=null&&!t.invalid&&!t.derailed&&t.graph!=null&&t.graph.id.equals(path.get().graph())&&CargoPackageAssignment.mayAssignTrain(s,access(s,line).owner(),id)&&(line.mode()==Mode.CHARTER||t.runtime.getSchedule()!=null&&!t.runtime.paused);}).findFirst().orElse(null);
    }
    public static CargoLineRegistry load(CompoundTag root,HolderLookup.Provider lookup){if(root.getInt("Version")!=1)throw new IllegalArgumentException("Cargo line version");var d=new CargoLineRegistry();for(Tag raw:root.getList("Stations",10)){var t=(CompoundTag)raw;if(d.stationPoints.putIfAbsent(t.getUUID("Station"),t.getUUID("Point"))!=null)throw new IllegalArgumentException("Duplicate station");}
        for(Tag raw:root.getList("Lines",10)){var t=(CompoundTag)raw;var stops=new ArrayList<Stop>();for(Tag value:t.getList("Stops",10)){var v=(CompoundTag)value;stops.add(new Stop(v.getUUID("Point"),v.getUUID("Station")));}var trains=new HashSet<UUID>();for(Tag value:t.getList("Trains",11))trains.add(NbtUtils.loadUUID(value));var line=new Line(t.getUUID("Id"),t.getUUID("Creator"),CargoInfrastructure.name(t.getString("Name")),stops,trains,Mode.valueOf(t.getString("Mode")),t.getLong("Tariff"),t.getString("Terms"),t.getBoolean("Enabled"));if(d.lines.putIfAbsent(line.id(),line)!=null)throw new IllegalArgumentException("Duplicate line");}return d;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){root.putInt("Version",1);var stations=new ListTag();stationPoints.forEach((station,point)->{var t=new CompoundTag();t.putUUID("Station",station);t.putUUID("Point",point);stations.add(t);});root.put("Stations",stations);var list=new ListTag();for(var l:lines.values()){var t=new CompoundTag();t.putUUID("Id",l.id());t.putUUID("Creator",l.creator());t.putString("Name",l.name());t.putString("Mode",l.mode().name());t.putLong("Tariff",l.tariff());t.putString("Terms",l.terms());t.putBoolean("Enabled",l.enabled());var stops=new ListTag();for(var stop:l.stops()){var a=new CompoundTag();a.putUUID("Point",stop.point());a.putUUID("Station",stop.station());stops.add(a);}t.put("Stops",stops);var trains=new ListTag();for(UUID train:l.trains())trains.add(NbtUtils.createUUID(train));t.put("Trains",trains);list.add(t);}root.put("Lines",list);return root;}
}
