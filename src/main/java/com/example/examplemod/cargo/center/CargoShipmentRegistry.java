package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.DimensionDataStorage;
import java.util.*;

/** Metadata/physical observations only. Request acceptance is never evidence of goods or delivery. */
public final class CargoShipmentRegistry extends net.minecraft.world.level.saveddata.SavedData {
    public enum Stage { REQUESTED,PACKAGED,IN_TRANSIT,TRANSSHIPMENT,ARRIVED,DELIVERED,COMPLETED,BLOCKED,FAILED,CANCELLED }
    public static final class Shipment {
        private final UUID id,creator,sourceWarehouse,destinationWarehouse,source,finalPoint;
        private final OwnerRef owner;
        private final ItemStack type;
        private final int quantity;
        private final List<CargoRoutePlanner.Leg> route;
        private final CargoInfrastructure.Location receiver,inventory;
        private final Map<UUID,CompoundTag> parcels=new LinkedHashMap<>();
        private final List<String> history=new ArrayList<>();
        private Stage stage=Stage.REQUESTED;
        private boolean packagingClaimed;
        private boolean cancellationRequested;
        private OwnerRef receiverOwner;
        private String problem="";
        private Shipment(UUID id,UUID creator,OwnerRef owner,UUID sourceWarehouse,UUID destinationWarehouse,UUID source,UUID finalPoint,ItemStack type,int quantity,List<CargoRoutePlanner.Leg> route,CargoInfrastructure.Location receiver,CargoInfrastructure.Location inventory){
            this.id=id;this.creator=creator;this.owner=owner;this.sourceWarehouse=sourceWarehouse;this.destinationWarehouse=destinationWarehouse;this.source=source;this.finalPoint=finalPoint;this.type=type.copyWithCount(1);this.quantity=quantity;this.route=List.copyOf(route);this.receiver=receiver;this.inventory=inventory;
        }
        public UUID id(){return id;} public UUID creator(){return creator;} public OwnerRef owner(){return owner;}
        public UUID sourceWarehouse(){return sourceWarehouse;} public UUID destinationWarehouse(){return destinationWarehouse;}
        public UUID source(){return source;} public UUID finalPoint(){return finalPoint;}
        public ItemStack type(){return type.copy();} public int quantity(){return quantity;} public List<CargoRoutePlanner.Leg> route(){return route;}
        public boolean packagingClaimed(){return packagingClaimed;}
        public int parcelCount(){return parcels.size();}
        public boolean cancellationRequested(){return cancellationRequested;}
        public Stage stage(){return stage;} public String problem(){return problem;} public List<String> history(){return List.copyOf(history);}
        public List<CompoundTag> parcelPositions(){var result=new ArrayList<CompoundTag>();for(var parcel:parcels.values()){var t=new CompoundTag();t.putUUID("Id",parcel.getUUID("Id"));if(parcel.contains("Location"))t.put("Location",parcel.getCompound("Location").copy());if(parcel.hasUUID("Train")&&parcel.hasUUID("BoardStation"))t.putUUID("Train",parcel.getUUID("Train"));t.putString("Status",parcel.getString("Status"));result.add(t);}return List.copyOf(result);}
        public boolean receivingOwnerValid(MinecraftServer s){var destination=CargoWarehouseRegistry.get(s).find(destinationWarehouse).orElse(null);return receiverOwner!=null&&receiverOwner.available(s)&&destination!=null&&receiverOwner.equals(CargoWarehouseRegistry.access(s,destination).owner());}
        public int packaged(){return parcels.values().stream().mapToInt(p->p.getInt("Units")).sum();}
        public int delivered(){return parcels.values().stream().filter(p->p.getBoolean("Delivered")).mapToInt(p->p.getInt("Units")).sum();}
        public int recovered(){return parcels.values().stream().filter(p->p.getBoolean("Recovered")).mapToInt(p->p.getInt("Units")).sum();}
        public boolean cancellationResolved(){return cancellationRequested&&packaged()==quantity&&(long)delivered()+recovered()==quantity;}
        public int boardedUnits(UUID train,UUID station,int hop){return parcels.values().stream().filter(p->!p.getBoolean("Recovered")&&(Arrays.stream(p.getIntArray("Hops")).anyMatch(i->i==hop)||(p.hasUUID("Train")&&train.equals(p.getUUID("Train"))&&p.hasUUID("BoardStation")&&station.equals(p.getUUID("BoardStation"))))).mapToInt(p->p.getInt("Units")).sum();}
        public int hopUnits(int hop){return parcels.values().stream().filter(p->Arrays.stream(p.getIntArray("Hops")).anyMatch(i->i==hop)).mapToInt(p->p.getInt("Units")).sum();}
        public boolean physicalComplete(){return stage!=Stage.FAILED&&stage!=Stage.BLOCKED&&stage!=Stage.CANCELLED&&delivered()==quantity&&packaged()==quantity;}
        public boolean canCancel(CargoCommerceBook.Order order,boolean payerManages){return ShipmentCancellation.allowed(order,stage,packagingClaimed,parcels.size(),payerManages);}
        public boolean hopComplete(int hop){return physicalComplete()&&parcels.values().stream().allMatch(p->Arrays.stream(p.getIntArray("Hops")).anyMatch(i->i==hop));}
    }
    private CompoundTag unavailableData;
    private RuntimeException loadFailure;
    private final Map<UUID,Shipment> entries=new LinkedHashMap<>();
    private final Map<UUID,UUID> parcelShipments=new HashMap<>();
    public static CargoShipmentRegistry get(MinecraftServer s){if(!s.isSameThread())throw new IllegalStateException("Server thread");return fromStorage(s.overworld().getDataStorage());}
    static CargoShipmentRegistry fromStorage(DimensionDataStorage storage){
        var data=storage.computeIfAbsent(new Factory<>(CargoShipmentRegistry::new,CargoShipmentRegistry::loadForStorage,null),"ccec_cargo_shipments");
        if(data.loadFailure!=null)throw new IllegalStateException("Cargo shipment data unavailable; original NBT retained",data.loadFailure);
        return data;
    }
    private static CargoShipmentRegistry loadForStorage(CompoundTag root,HolderLookup.Provider lookup){
        try{return load(root,lookup);}catch(RuntimeException invalid){var data=new CargoShipmentRegistry();data.unavailableData=root.copy();data.loadFailure=invalid;return data;}
    }
    public List<Shipment> all(){return List.copyOf(entries.values());}
    public Optional<Shipment> find(UUID id){return Optional.ofNullable(entries.get(id));}
    public boolean inUse(String kind,UUID id){return entries.values().stream().anyMatch(shipment->ShipmentLocks.blocks(shipment.stage,kind,id,new ShipmentLocks.References(shipment.sourceWarehouse,shipment.destinationWarehouse,shipment.source,shipment.finalPoint,shipment.route)));}
    public void requireUnused(String kind,UUID id){if(inUse(kind,id))throw new IllegalArgumentException("shipment_in_use");}
    public boolean claimPackaging(UUID id){var shipment=entries.get(id);if(shipment==null||shipment.cancellationRequested||shipment.packagingClaimed||shipment.stage!=Stage.REQUESTED)return false;
        shipment.packagingClaimed=true;setDirty();return true;}
    /** Called on the server thread after the same order was refunded. No physical goods are touched. */
    public void cancelled(UUID id){var shipment=entries.get(id);if(shipment==null||shipment.stage!=Stage.REQUESTED||shipment.packagingClaimed||!shipment.parcels.isEmpty())throw new IllegalArgumentException("shipment_not_cancellable");shipment.cancellationRequested=true;shipment.stage=Stage.CANCELLED;shipment.problem="";event(shipment,"CANCELLED");}
    public void requestCancellation(UUID id){var shipment=entries.get(id);if(shipment==null)throw new IllegalArgumentException("Unknown shipment");if(!shipment.cancellationRequested){shipment.cancellationRequested=true;event(shipment,"CANCELLATION_REQUESTED");}}
    public void cancellationCompleted(UUID id){var shipment=entries.get(id);if(shipment==null||!shipment.cancellationResolved())throw new IllegalArgumentException("Physical cargo unresolved");shipment.stage=Stage.CANCELLED;shipment.problem="";event(shipment,"CANCELLED");}
    public boolean recoveredParcel(UUID parcel){var id=parcelShipments.get(parcel);var shipment=id==null?null:entries.get(id);return shipment!=null&&shipment.parcels.get(parcel).getBoolean("Recovered");}
    /** Validates an actual held box against frozen contents before inventory replacement. */
    CompoundTag recoverableParcel(MinecraftServer server,UUID shipmentId,UUID parcel,ItemStack box){
        var shipment=entries.get(shipmentId);var p=shipment==null?null:shipment.parcels.get(parcel);
        if(shipment==null||!shipment.cancellationRequested||p==null||p.getBoolean("Delivered")||p.getBoolean("Recovered")
                ||CargoDeliveryReceipts.find(server,parcel).isPresent()||!p.getCompound("Contents").equals(PackageItem.getContents(box).serializeNBT(server.registryAccess())))return null;
        return p.copy();
    }
    void recovered(UUID shipmentId,UUID parcel){var shipment=entries.get(shipmentId);var p=shipment.parcels.get(parcel);p.putBoolean("Recovered",true);p.putString("Status","RECOVERED");p.remove("Train");p.remove("BoardStation");p.remove("Location");event(shipment,"RECOVERED "+shipment.recovered()+"/"+shipment.quantity);}
    public Shipment create(MinecraftServer server,UUID id,UUID creator,OwnerRef owner,CargoWarehouseRegistry.Warehouse source,CargoWarehouseRegistry.Warehouse destination,ItemStack type,int count,CargoRoutePlanner.Route route){
        var inventory=CargoWarehouseRegistry.receivingInventory(server,destination);
        if(entries.containsKey(id)||count<1||count>CargoTickerBlockEntity.MAX_REQUEST_COUNT||type.isEmpty()||source.id().equals(destination.id())||inventory==null||destination.receiver()==null)throw new IllegalArgumentException("Invalid shipment or receiving Packager unavailable");
        var value=new Shipment(id,creator,owner,source.id(),destination.id(),source.point(),destination.point(),type,count,route.legs(),destination.receiver(),inventory);value.receiverOwner=CargoWarehouseRegistry.access(server,destination).owner();entries.put(id,value);event(value,"REQUESTED");return value;
    }
    public void blocked(UUID id,String reason){var value=entries.get(id);if(value==null)return;value.stage=Stage.BLOCKED;value.problem=reason;event(value,"BLOCKED: "+reason);}
    public void failed(UUID id,String reason){var value=entries.get(id);if(value==null)return;value.stage=Stage.FAILED;value.problem=reason;event(value,"FAILED: "+reason);}
    public void completed(UUID id){var value=entries.get(id);if(value!=null&&value.physicalComplete()&&value.stage!=Stage.COMPLETED){value.stage=Stage.COMPLETED;event(value,"COMPLETED");}}
    private void event(Shipment value,String text){if(value.history.isEmpty()||!value.history.getLast().equals(text)){value.history.add(text);if(value.history.size()>64)value.history.removeFirst();}setDirty();}
    /** Called only for the actual Router slot, after stamping and state transitions. */
    public static void observe(MinecraftServer server,UUID point,CargoInfrastructure.Location location,ItemStack stack,CargoTransshipment.Status status){
        if(point==null||stack.getCount()!=1||!PackageItem.isPackage(stack))return;
        var address=CargoAddress.parse(PackageItem.getAddress(stack)).orElse(null);var parcel=CargoParcelIdentity.id(stack).orElse(null);
        if(address==null||address.shipment()==null||parcel==null)return;var data=get(server);var shipment=data.entries.get(address.shipment());
        if(shipment==null||shipment.stage==Stage.COMPLETED||shipment.stage==Stage.FAILED&&!shipment.cancellationRequested||shipment.stage==Stage.CANCELLED||!shipment.finalPoint.equals(address.finalPoint()))return;
        CompoundTag contents=PackageItem.getContents(stack).serializeNBT(server.registryAccess());var current=shipment.parcels.get(parcel);
        if(current==null){
            if(!point.equals(shipment.source)||data.parcelShipments.containsKey(parcel)){data.blocked(shipment.id,"Unexpected first parcel observation");return;}
            int units=0;var inventory=PackageItem.getContents(stack);for(int slot=0;slot<inventory.getSlots();slot++){var item=inventory.getStackInSlot(slot);if(!item.isEmpty()&&!ItemStack.isSameItemSameComponents(item,shipment.type)){data.blocked(shipment.id,"Unexpected parcel contents");return;}units+=item.getCount();}
            if(units<=0||shipment.packaged()+units>shipment.quantity){data.blocked(shipment.id,"Physical quantity mismatch");return;}
            current=new CompoundTag();current.putUUID("Id",parcel);current.put("Contents",contents);current.putInt("Units",units);current.putIntArray("Hops",new int[0]);shipment.parcels.put(parcel,current);data.parcelShipments.put(parcel,shipment.id);shipment.stage=Stage.PACKAGED;data.event(shipment,"PACKAGED "+shipment.packaged()+"/"+shipment.quantity);
        }else if(current.getBoolean("Recovered")||current.getBoolean("Delivered")){return;}else if(!contents.equals(current.getCompound("Contents"))){data.failed(shipment.id,"Parcel contents changed");return;}
        if(!current.hasUUID("Point")||!current.getUUID("Point").equals(point)||!current.getString("Status").equals(status.name())){current.putUUID("Point",point);current.put("Location",location.save());current.putString("Status",status.name());
            if(shipment.stage!=Stage.BLOCKED){shipment.stage=status==CargoTransshipment.Status.ARRIVED?Stage.ARRIVED:point.equals(shipment.source)?Stage.PACKAGED:Stage.TRANSSHIPMENT;}data.event(shipment,status+" "+CargoPointRegistry.find(server,point).map(CargoPointRegistry.Point::name).orElse("MISSING"));}
    }
    /** Post-insertion source clearing in native mail transfer proves actual boarding/alighting. */
    public static void mailTransfer(MinecraftServer server,ItemStack stack,GlobalStation station,Train train,boolean boarding){
        if(server==null||train==null||stack.isEmpty())return;var parcel=CargoParcelIdentity.id(stack).orElse(null);if(parcel==null)return;
        var data=get(server);var shipId=data.parcelShipments.get(parcel);var shipment=shipId==null?null:data.entries.get(shipId);if(shipment==null||shipment.stage==Stage.CANCELLED)return;var p=shipment.parcels.get(parcel);if(p.getBoolean("Recovered")||p.getBoolean("Delivered"))return;
        if(boarding){p.putUUID("BoardStation",station.id);p.putUUID("Train",train.id);if(shipment.stage!=Stage.BLOCKED)shipment.stage=Stage.IN_TRANSIT;data.event(shipment,"IN_TRANSIT "+train.name.getString());}
        else if(p.hasUUID("Train")&&train.id.equals(p.getUUID("Train"))&&p.hasUUID("BoardStation")){
            var completed=new TreeSet<Integer>();for(int h:p.getIntArray("Hops"))completed.add(h);
            for(int i=0;i<shipment.route.size();i++){var leg=shipment.route.get(i);var line=CargoLineRegistry.get(server).find(leg.line()).orElse(null);if(line==null||!line.trains().contains(train.id))continue;
                var order=com.example.examplemod.economy.EconomyManager.get(server.overworld()).cargo().order(shipment.id).orElse(null);
                int index=i;if(order!=null&&order.fees().stream().anyMatch(f->f.hop()==index&&!f.recipient().equals(CargoLineRegistry.access(server,line).owner())))continue;
                for(int j=0;j<line.stops().size()-1;j++)if(line.stops().get(j).point().equals(leg.from())&&line.stops().get(j+1).point().equals(leg.next())&&line.stops().get(j).station().equals(p.getUUID("BoardStation"))&&line.stops().get(j+1).station().equals(station.id))completed.add(i);}
            p.putIntArray("Hops",completed.stream().mapToInt(Integer::intValue).toArray());p.remove("BoardStation");p.put("Location",new CargoInfrastructure.Location(station.getBlockEntityDimension().location(),station.getBlockEntityPos()).save());data.event(shipment,"UNLOADED "+station.name);}
        data.setDirty();
    }
    /** Can safely be repeated after restart; UUID receipts are credited only to the frozen receiving equipment. */
    public void reconcile(MinecraftServer server){for(var shipment:entries.values()){if(shipment.stage==Stage.COMPLETED||shipment.stage==Stage.FAILED&&!shipment.cancellationRequested||shipment.stage==Stage.CANCELLED)continue;
        for(var entry:shipment.parcels.entrySet()){var p=entry.getValue();if(p.getBoolean("Delivered")||p.getBoolean("Recovered"))continue;var receipt=CargoDeliveryReceipts.find(server,entry.getKey()).orElse(null);
            if(receipt==null||!shipment.finalPoint.equals(receipt.finalPoint())||!shipment.inventory.dimension().equals(receipt.dimension())||!shipment.inventory.pos().equals(receipt.inventory())||!shipment.receiver.pos().equals(receipt.packager()))continue;p.putBoolean("Delivered",true);event(shipment,"DELIVERED "+shipment.delivered()+"/"+shipment.quantity);}
        if(shipment.physicalComplete()&&shipment.stage!=Stage.DELIVERED){if(!shipment.receivingOwnerValid(server)){blocked(shipment.id,"Receiving warehouse ownership changed");continue;}shipment.stage=Stage.DELIVERED;event(shipment,"DELIVERED");}}}
    public static CargoShipmentRegistry load(CompoundTag root,HolderLookup.Provider lookup){if(root.getInt("Version")<1||root.getInt("Version")>2)throw new IllegalArgumentException("Shipment version");var data=new CargoShipmentRegistry();for(Tag raw:root.getList("Entries",10)){var t=(CompoundTag)raw;var route=new ArrayList<CargoRoutePlanner.Leg>();for(Tag value:t.getList("Route",10)){var v=(CompoundTag)value;route.add(new CargoRoutePlanner.Leg(v.getUUID("Line"),v.getUUID("From"),v.getUUID("Next"),v.getUUID("Train"),v.getLong("Tariff")));}
        var shipment=new Shipment(t.getUUID("Id"),t.getUUID("Creator"),OwnerRef.load(t.getCompound("Owner")),t.getUUID("SourceWarehouse"),t.getUUID("DestinationWarehouse"),t.getUUID("Source"),t.getUUID("Final"),ItemStack.parseOptional(lookup,t.getCompound("Item")),t.getInt("Quantity"),route,CargoInfrastructure.Location.load(t.getCompound("Receiver")),CargoInfrastructure.Location.load(t.getCompound("Inventory")));shipment.receiverOwner=t.contains("ReceiverOwner")?OwnerRef.load(t.getCompound("ReceiverOwner")):null;shipment.packagingClaimed=t.getBoolean("PackagingClaimed");shipment.cancellationRequested=t.getBoolean("CancellationRequested");shipment.stage=Stage.valueOf(t.getString("Stage"));shipment.problem=t.getString("Problem");for(Tag value:t.getList("History",8))shipment.history.add(value.getAsString());for(Tag value:t.getList("Parcels",10)){var parcel=((CompoundTag)value).copy();UUID id=parcel.getUUID("Id");if(shipment.parcels.putIfAbsent(id,parcel)!=null||data.parcelShipments.putIfAbsent(id,shipment.id)!=null)throw new IllegalArgumentException("Duplicate physical parcel");}if(data.entries.putIfAbsent(shipment.id,shipment)!=null)throw new IllegalArgumentException("Duplicate shipment");}return data;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){if(unavailableData!=null)return unavailableData.copy();root.putInt("Version",2);var list=new ListTag();for(var shipment:entries.values()){var t=new CompoundTag();t.putUUID("Id",shipment.id);t.putUUID("Creator",shipment.creator);t.put("Owner",shipment.owner.save());t.putUUID("SourceWarehouse",shipment.sourceWarehouse);t.putUUID("DestinationWarehouse",shipment.destinationWarehouse);t.putUUID("Source",shipment.source);t.putUUID("Final",shipment.finalPoint);t.put("Item",shipment.type.save(lookup));t.putInt("Quantity",shipment.quantity);t.put("Receiver",shipment.receiver.save());t.put("Inventory",shipment.inventory.save());if(shipment.receiverOwner!=null)t.put("ReceiverOwner",shipment.receiverOwner.save());t.putBoolean("PackagingClaimed",shipment.packagingClaimed);t.putBoolean("CancellationRequested",shipment.cancellationRequested);t.putString("Stage",shipment.stage.name());t.putString("Problem",shipment.problem);var history=new ListTag();for(String event:shipment.history)history.add(StringTag.valueOf(event));t.put("History",history);var parcels=new ListTag();for(var p:shipment.parcels.values())parcels.add(p.copy());t.put("Parcels",parcels);var route=new ListTag();for(var leg:shipment.route){var v=new CompoundTag();v.putUUID("Line",leg.line());v.putUUID("From",leg.from());v.putUUID("Next",leg.next());v.putUUID("Train",leg.train());v.putLong("Tariff",leg.tariff());route.add(v);}t.put("Route",route);list.add(t);}root.put("Entries",list);return root;}
}
