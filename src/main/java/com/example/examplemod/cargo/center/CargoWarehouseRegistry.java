package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.util.*;

/** Metadata for actual Create networks, not item storage. A network may be registered only once. */
public final class CargoWarehouseRegistry extends net.minecraft.world.level.saveddata.SavedData {
    public record Warehouse(UUID id,UUID creator,String name,UUID point,CargoInfrastructure.Location ticker,
                            UUID tickerInstance,UUID network,CargoInfrastructure.Location receiver) {}
    private final Map<UUID,Warehouse> entries=new HashMap<>();
    public static CargoWarehouseRegistry get(MinecraftServer s){if(!s.isSameThread())throw new IllegalStateException("Server thread");return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CargoWarehouseRegistry::new,CargoWarehouseRegistry::load,null),"ccec_cargo_warehouses");}
    public List<Warehouse> all(){return List.copyOf(entries.values());}
    public Optional<Warehouse> find(UUID id){return Optional.ofNullable(entries.get(id));}
    public static CargoOwners.Access access(MinecraftServer s,Warehouse w){return CargoOwners.get(s).access("warehouse/"+w.id(),w.creator(),CargoTrainRegistry.AccessMode.PRIVATE);}
    public static CargoTickerBlockEntity ticker(MinecraftServer s,Warehouse w){var be=w.ticker().loaded(s);return be instanceof CargoTickerBlockEntity t&&!t.isRemoved()&&w.tickerInstance().equals(t.instanceId())&&w.network().equals(t.behaviour.freqId)?t:null;}
    /** Registration was explicitly authorized by this UUID with native mayAdministrate. No fake Player is created. */
    public static boolean nativeAuthorized(MinecraftServer s,Warehouse w){if(!s.isSameThread()||!access(s,w).owner().available(s))return false;
        var network=com.simibubi.create.Create.LOGISTICS.logisticsNetworks.get(w.network());
        return network!=null&&nativePermission(network.owner,network.locked,w.creator());
    }
    static boolean nativePermission(UUID owner,boolean locked,UUID authorizedPrincipal){return owner==null||!locked||owner.equals(authorizedPrincipal);}
    public static boolean permits(ServerPlayer actor,Warehouse w){return CargoOwners.permits(actor.getServer(),OwnerRef.player(actor.getUUID()),access(actor.getServer(),w));}
    public static boolean manages(ServerPlayer actor,Warehouse w){return access(actor.getServer(),w).owner().manages(actor.getServer(),actor.getUUID());}
    public Warehouse register(ServerPlayer actor,UUID pointId,String name,OwnerRef owner){
        var s=actor.getServer();var p=CargoPointRegistry.find(s,pointId).orElseThrow();
        if(!owner.manages(s,actor.getUUID())||!CargoOwners.point(s,p).manages(s,actor.getUUID()))throw new IllegalArgumentException("Permission denied");
        if(!(CargoInfrastructure.lookedAt(actor) instanceof CargoTickerBlockEntity t)||!actor.getUUID().equals(t.ownerId())||!t.behaviour.mayAdministrate(actor)||!t.behaviour.mayInteract(actor)||t.behaviour.freqId==null)throw new IllegalArgumentException("Look at your linked Cargo Ticker");
        if(entries.values().stream().anyMatch(w->w.network().equals(t.behaviour.freqId)))throw new IllegalArgumentException("Network already registered");
        var w=new Warehouse(UUID.randomUUID(),actor.getUUID(),CargoInfrastructure.name(name),pointId,new CargoInfrastructure.Location(actor.level().dimension().location(),t.getBlockPos()),t.instanceId(),t.behaviour.freqId,null);
        CargoOwners.get(s).change(s,actor.getUUID(),"warehouse/"+w.id(),w.creator(),CargoTrainRegistry.AccessMode.PRIVATE,new CargoOwners.Access(owner,CargoTrainRegistry.AccessMode.PRIVATE,Set.of(),Set.of()));
        entries.put(w.id(),w);setDirty();return w;
    }
    public boolean rename(ServerPlayer actor,UUID id,String name){var w=entries.get(id);if(w==null||!manages(actor,w))return false;entries.put(id,new Warehouse(id,w.creator(),CargoInfrastructure.name(name),w.point(),w.ticker(),w.tickerInstance(),w.network(),w.receiver()));setDirty();return true;}
    public boolean remove(ServerPlayer actor,UUID id){var w=entries.get(id);if(w==null||!manages(actor,w))return false;CargoShipmentRegistry.get(actor.getServer()).requireUnused("warehouse",id);entries.remove(id);setDirty();return true;}
    /** Receiver is selected in world and must sit beside an owned Router at this point. */
    public boolean attachReceiver(ServerPlayer actor,UUID id){
        var looked=CargoInfrastructure.lookedAt(actor);
        return looked instanceof PackagerBlockEntity&&attachReceiverAt(actor,id,new CargoInfrastructure.Location(actor.level().dimension().location(),looked.getBlockPos()));
    }
    public boolean attachReceiverAt(ServerPlayer actor,UUID id,CargoInfrastructure.Location location){var w=entries.get(id);if(w==null||!manages(actor,w))return false;
        CargoShipmentRegistry.get(actor.getServer()).requireUnused("warehouse",id);
        if(!(location.loaded(actor.getServer()) instanceof PackagerBlockEntity packager))return false;
        var p=CargoPointRegistry.find(actor.getServer(),w.point()).orElse(null);if(p==null)return false;
        boolean nearby=p.routers().stream().anyMatch(r->r.dimension().equals(location.dimension())&&r.pos().distSqr(packager.getBlockPos())<=256&&CargoInfrastructure.router(actor.getServer(),r)!=null);
        if(!nearby)return false;
        var proposed=new Warehouse(id,w.creator(),w.name(),w.point(),w.ticker(),w.tickerInstance(),w.network(),location);
        if(receivingInventory(actor.getServer(),proposed)==null)return false;
        entries.put(id,proposed);setDirty();return true;
    }
    public static CargoInfrastructure.Location receivingInventory(MinecraftServer s,Warehouse w){
        if(w.receiver()==null||!(w.receiver().loaded(s) instanceof PackagerBlockEntity p)||!p.getBlockState().hasProperty(BlockStateProperties.FACING))return null;
        if(!(p.getLevel() instanceof ServerLevel level))return null;
        var facing=p.getBlockState().getValue(BlockStateProperties.FACING);
        var pos=p.getBlockPos().relative(facing.getOpposite());
        if(!level.hasChunkAt(pos))return null;
        var target=level.getBlockEntity(pos);
        // Match the native receipt observer: other handlers/inventories cannot prove settlement.
        if(target==null||target.isRemoved()||!(target.getClass()==ChestBlockEntity.class
                ||target.getClass()==BarrelBlockEntity.class||target.getClass()==ItemVaultBlockEntity.class))return null;
        var state=level.getBlockState(pos);var unpacker=UnpackingHandler.REGISTRY.get(state);
        if(unpacker!=null&&unpacker!=UnpackingHandler.DEFAULT)return null;
        var inventory=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,state,target,facing);
        if(inventory==null||inventory.getSlots()<1||inventory.getSlots()>4096)return null;
        return new CargoInfrastructure.Location(w.receiver().dimension(),pos);
    }
    /** Remove metadata only. Never extracts, deletes or moves the physical Packager or its contents. */
    public boolean detachReceiver(ServerPlayer actor,UUID id) {
        var warehouse=entries.get(id);if(warehouse==null||!manages(actor,warehouse)||warehouse.receiver()==null)return false;
        CargoShipmentRegistry.get(actor.getServer()).requireUnused("warehouse",id);
        entries.put(id,withoutReceiver(warehouse));setDirty();return true;
    }
    static Warehouse withoutReceiver(Warehouse warehouse) {
        return new Warehouse(warehouse.id(),warehouse.creator(),warehouse.name(),warehouse.point(),warehouse.ticker(),warehouse.tickerInstance(),warehouse.network(),null);
    }
    public static CargoWarehouseRegistry load(CompoundTag root,HolderLookup.Provider lookup){
        if(root.getInt("Version")!=1)throw new IllegalArgumentException("Warehouse version");var d=new CargoWarehouseRegistry();var networks=new HashSet<UUID>();
        for(Tag raw:root.getList("Entries",10)){var t=(CompoundTag)raw;var w=new Warehouse(t.getUUID("Id"),t.getUUID("Creator"),CargoInfrastructure.name(t.getString("Name")),t.getUUID("Point"),CargoInfrastructure.Location.load(t.getCompound("Ticker")),t.getUUID("Instance"),t.getUUID("Network"),t.contains("Receiver")?CargoInfrastructure.Location.load(t.getCompound("Receiver")):null);
            if(d.entries.putIfAbsent(w.id(),w)!=null||!networks.add(w.network()))throw new IllegalArgumentException("Duplicate warehouse");}return d;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){root.putInt("Version",1);var list=new ListTag();for(var w:entries.values()){var t=new CompoundTag();t.putUUID("Id",w.id());t.putUUID("Creator",w.creator());t.putString("Name",w.name());t.putUUID("Point",w.point());t.put("Ticker",w.ticker().save());t.putUUID("Instance",w.tickerInstance());t.putUUID("Network",w.network());if(w.receiver()!=null)t.put("Receiver",w.receiver().save());list.add(t);}root.put("Entries",list);return root;}
}
