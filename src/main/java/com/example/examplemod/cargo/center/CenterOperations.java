package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Every mutation re-resolves actual server objects. Form options are hints, never authorization. */
public final class CenterOperations {
    private CenterOperations() {}
    public static OwnerRef owner(String value){var p=value.split("/",-1);if(p.length!=2)throw new IllegalArgumentException("Invalid owner");return new OwnerRef(OwnerRef.Kind.valueOf(p[0]),UUID.fromString(p[1]));}
    static UUID id(CompoundTag q,String key){return UUID.fromString(q.getString(key));}
    static long number(CompoundTag q,String key){return Long.parseLong(q.getString(key));}
    static CargoOwners.Access access(ServerPlayer actor,String kind,UUID id){var s=actor.getServer();return switch(kind){
        case "train"->{var t=CargoTrainRegistry.find(s,id).orElseThrow();yield CargoOwners.get(s).access("train/"+id,t.ownerId(),t.accessMode());}
        case "point"->{var p=CargoPointRegistry.find(s,id).orElseThrow();yield CargoOwners.get(s).access("point/"+id,p.owner(),CargoTrainRegistry.AccessMode.PRIVATE);}
        case "warehouse"->CargoWarehouseRegistry.access(s,CargoWarehouseRegistry.get(s).find(id).orElseThrow());
        case "line"->CargoLineRegistry.access(s,CargoLineRegistry.get(s).find(id).orElseThrow());
        case "offer"->{var o=com.example.examplemod.economy.EconomyManager.get(actor.serverLevel()).cargo().offer(id).orElseThrow();yield CargoOwners.get(s).access("offer/"+id,o.creator(),CargoTrainRegistry.AccessMode.PUBLIC);}
        default->throw new IllegalArgumentException("Unknown object");};}
    static boolean manage(ServerPlayer actor,String kind,UUID id){return access(actor,kind,id).owner().manages(actor.getServer(),actor.getUUID());}
    static void require(boolean result){if(!result)throw new IllegalArgumentException("Operation denied or equipment unavailable");}
    public static void execute(ServerPlayer actor,CompoundTag q){
        if(EconomicCenter.action(q.getString("Action"))){EconomicCenter.execute(actor,q);return;}
        var s=actor.getServer();String action=q.getString("Action");var a=q.getCompound("Values");UUID selected=q.hasUUID("Selected")?q.getUUID("Selected"):null;String kind=q.getString("Kind");
        if(selected!=null&&Set.of("ACCESS","ACL_ADD","ACL_REMOVE","REMOVE","POINT_UNBIND","STATION_REMOVE","LINE_SCHEDULE","LINE_CHARTER").contains(action)&&(CargoCharters.get(s).locks(s,kind,selected)||PublicFleetRegistry.get(s).locks(s,kind,selected)))throw new IllegalArgumentException("shipment_in_use");
        if(Set.of("TRAIN_REGISTER","POINT_CREATE").contains(action))CargoTrainRegistry.AccessMode.valueOf(a.getString("Access"));
        switch(action){
            case "PICK_RECEIVER"->{require(selected!=null);CenterEquipmentSelection.begin(actor,selected);}
            case "CANCEL_RECEIVER_PICK"->CenterEquipmentSelection.cancel(actor);
            case "TRAIN_REGISTER"->{UUID train=id(a,"Train");var o=owner(a.getString("Owner"));require(o.manages(s,actor.getUUID()));require(CargoTrainRegistry.register(s,actor.getUUID(),train,CargoTrainRegistry.AccessMode.PRIVATE));require(CargoOwners.get(s).change(s,actor.getUUID(),"train/"+train,actor.getUUID(),CargoTrainRegistry.AccessMode.PRIVATE,new CargoOwners.Access(o,CargoTrainRegistry.AccessMode.valueOf(a.getString("Access")),Set.of(),Set.of())));}
            case "POINT_CREATE"->{var o=owner(a.getString("Owner"));require(o.manages(s,actor.getUUID()));var p=CargoPointRegistry.create(s,actor.getUUID(),a.getString("Name"));require(CargoOwners.get(s).change(s,actor.getUUID(),"point/"+p.id(),p.owner(),CargoTrainRegistry.AccessMode.PRIVATE,new CargoOwners.Access(o,CargoTrainRegistry.AccessMode.valueOf(a.getString("Access")),Set.of(),Set.of())));}
            case "WAREHOUSE_REGISTER"->{var w=CargoWarehouseRegistry.get(s).register(actor,id(a,"Point"),a.getString("Name"),owner(a.getString("Owner")));require(w!=null);}
            case "RENAME"->{require(selected!=null&&manage(actor,kind,selected));require(switch(kind){case "train"->CargoTrainRegistry.rename(s,actor.getUUID(),selected,a.getString("Name"));case "point"->CargoPointRegistry.rename(s,actor.getUUID(),selected,a.getString("Name"));case "warehouse"->CargoWarehouseRegistry.get(s).rename(actor,selected,a.getString("Name"));default->false;});}
            case "REMOVE"->{require(q.getBoolean("Confirmed")&&selected!=null&&manage(actor,kind,selected));CargoShipmentRegistry.get(s).requireUnused(kind,selected);require(switch(kind){case "train"->CargoTrainRegistry.unregister(s,actor.getUUID(),selected);case "point"->CargoPointRegistry.delete(s,actor.getUUID(),selected);case "warehouse"->CargoWarehouseRegistry.get(s).remove(actor,selected);case "line"->CargoLineRegistry.get(s).remove(actor,selected);default->false;});}
            case "ACCESS"->{require(selected!=null&&manage(actor,kind,selected));var old=access(actor,kind,selected);var next=owner(a.getString("Owner"));if(!old.owner().equals(next))CargoShipmentRegistry.get(s).requireUnused(kind,selected);require(CargoOwners.get(s).change(s,actor.getUUID(),kind+"/"+selected,old.owner().id(),old.mode(),new CargoOwners.Access(next,CargoTrainRegistry.AccessMode.valueOf(a.getString("Access")),old.players(),old.cities(),old.companies())));}
            case "ACL_ADD","ACL_REMOVE"->{require(selected!=null&&manage(actor,kind,selected));var old=access(actor,kind,selected);var subject=owner(a.getString("Subject"));var players=new HashSet<>(old.players());var cities=new HashSet<>(old.cities());var companies=new HashSet<>(old.companies());var set=switch(subject.kind()){case PLAYER->players;case CITY->cities;case COMPANY->companies;default->throw new IllegalArgumentException("delivery_payer_denied");};if(action.equals("ACL_ADD"))set.add(subject.id());else set.remove(subject.id());require(players.size()+cities.size()+companies.size()<=128);require(CargoOwners.get(s).change(s,actor.getUUID(),kind+"/"+selected,old.owner().id(),old.mode(),new CargoOwners.Access(old.owner(),old.mode(),players,cities,companies)));}
            case "POINT_BIND"->{require(selected!=null&&manage(actor,"point",selected));var be=CargoInfrastructure.lookedAt(actor);require(be instanceof CargoRouterBlockEntity);require(CargoPointRegistry.bindRouter(actor,selected,actor.serverLevel(),be.getBlockPos()));}
            case "POINT_UNBIND"->{require(selected!=null&&manage(actor,"point",selected));CargoShipmentRegistry.get(s).requireUnused("point",selected);UUID instance=id(a,"Router");var point=CargoPointRegistry.find(s,selected).orElseThrow();var ref=point.routers().stream().filter(r->r.instanceId().equals(instance)).findFirst().orElseThrow();require(CargoPointRegistry.unbindRouter(s,actor.getUUID(),ref));}
            case "POINT_STATION"->require(selected!=null&&CargoLineRegistry.get(s).attachStation(actor,selected));
            case "STATION_REMOVE"->{require(selected!=null&&selected.equals(CargoLineRegistry.get(s).stations().get(id(a,"Station"))));require(CargoLineRegistry.get(s).detachStation(actor,id(a,"Station")));}
            case "POINT_POSTBOX"->{require(selected!=null&&manage(actor,"point",selected));var point=CargoPointRegistry.find(s,selected).orElseThrow();int changed=0;for(var ref:point.routers()){var router=CargoInfrastructure.router(s,ref);var above=new CargoInfrastructure.Location(ref.dimension(),ref.pos().above()).loaded(s);if(router!=null&&above instanceof PostboxBlockEntity box){box.addressFilter=CargoAddress.postboxFilter(selected);box.acceptsPackages=true;box.filterChanged();box.setChanged();box.sendData();changed++;}}require(changed>0);}
            case "WAREHOUSE_RECEIVER"->{require(selected!=null&&q.getBoolean("Confirmed"));if(a.contains("EquipmentToken"))CenterEquipmentSelection.confirm(actor,selected,a.getString("EquipmentToken"));else require(CargoWarehouseRegistry.get(s).attachReceiver(actor,selected));}
            case "WAREHOUSE_RECEIVER_REMOVE"->{require(q.getBoolean("Confirmed")&&selected!=null);require(CargoWarehouseRegistry.get(s).detachReceiver(actor,selected));}
            case "LINE_SCHEDULE"->{require(q.getBoolean("Confirmed")&&selected!=null);CargoSchedules.get(s).apply(actor,selected,Math.toIntExact(number(a,"WaitSeconds")));}
            case "POINT_AUTOMATIC"->{require(selected!=null&&manage(actor,"point",selected));var p=CargoPointRegistry.find(s,selected).orElseThrow();int changed=0;for(var ref:p.routers()){var router=CargoInfrastructure.router(s,ref);if(router!=null&&router.configureAutomaticRouting(actor,Boolean.parseBoolean(a.getString("Enabled"))))changed++;}require(changed>0);}
            case "POINT_DISPATCH","POINT_TRANSFER","DISPATCH_REMOVE","TRANSFER_REMOVE"->{require(selected!=null&&manage(actor,"point",selected));var point=CargoPointRegistry.find(s,selected).orElseThrow();var ref=point.routers().stream().filter(r->r.instanceId().equals(id(a,"Router"))).findFirst().orElseThrow();var router=CargoInfrastructure.router(s,ref);require(router!=null);require(switch(action){case "POINT_DISPATCH"->router.setDispatchRule(actor,id(a,"NextPoint"),id(a,"Train"));case "POINT_TRANSFER"->router.setTransferRule(actor,id(a,"FinalPoint"),id(a,"NextPoint"));case "DISPATCH_REMOVE"->router.removeDispatchRule(actor,id(a,"NextPoint"));default->router.removeTransferRule(actor,id(a,"FinalPoint"));});}
            case "PUBLISH"->{require(selected!=null);CargoFulfillment.publish(actor,selected,a,q.getUUID("Request"));}
            case "OFFER_CLOSE"->{require(selected!=null&&manage(actor,"offer",selected));require(com.example.examplemod.economy.EconomyManager.get(actor.serverLevel()).cargo().close(selected));}
            case "BUY","SEND"->{require(q.getBoolean("Confirmed")&&selected!=null);CargoFulfillment.confirm(actor,q,action.equals("BUY"));}
            case "SHIPMENT_CANCEL"->{require(q.getBoolean("Confirmed")&&selected!=null);CargoFulfillment.cancel(actor,selected);}
            case "LINE_CHARTER"->{require(selected!=null);CargoCharters.get(s).authorize(actor,selected,Boolean.parseBoolean(a.getString("Enabled")),a.getString("Return").equals("RETURN"));}
            case "LINE_CREATE"->{var lines=CargoLineRegistry.get(s);UUID first=id(a,"FirstStation"),last=id(a,"LastStation");var stops=List.of(new CargoLineRegistry.Stop(lines.stations().get(first),first),new CargoLineRegistry.Stop(lines.stations().get(last),last));lines.create(actor,a.getString("Name"),owner(a.getString("Owner")),stops,Set.of(id(a,"Train")),CargoLineRegistry.Mode.valueOf(a.getString("Mode")),number(a,"Tariff"),a.getString("Terms"));}
            case "LINE_EDIT","LINE_STOP_ADD","LINE_STOP_REMOVE","LINE_TRAIN_ADD","LINE_TRAIN_REMOVE"->{var lines=CargoLineRegistry.get(s);var line=lines.find(selected).orElseThrow();require(CargoLineRegistry.manages(actor,line));var stops=new ArrayList<>(line.stops());var trains=new HashSet<>(line.trains());
                if(action.equals("LINE_STOP_ADD")){UUID station=id(a,"Station");stops.add(stops.size()-1,new CargoLineRegistry.Stop(lines.stations().get(station),station));}
                if(action.equals("LINE_STOP_REMOVE"))stops.removeIf(stop->stop.station().equals(id(a,"Station")));
                if(action.equals("LINE_TRAIN_ADD"))trains.add(id(a,"Train"));if(action.equals("LINE_TRAIN_REMOVE"))trains.remove(id(a,"Train"));
                boolean edit=action.equals("LINE_EDIT");require(lines.edit(actor,selected,edit?a.getString("Name"):line.name(),stops,trains,edit?CargoLineRegistry.Mode.valueOf(a.getString("Mode")):line.mode(),edit?number(a,"Tariff"):line.tariff(),edit?a.getString("Terms"):line.terms(),edit?Boolean.parseBoolean(a.getString("Enabled")):line.enabled()));}
            default->throw new IllegalArgumentException("Unknown logistics operation");
        }
    }
}
