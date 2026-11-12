package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Short-lived server selections. Picking equipment never changes registrations or block filters. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class CenterEquipmentSelection {
    private record Pick(UUID warehouse,UUID token,long expires,CargoInfrastructure.Location packager,CargoInfrastructure.Location postbox) {}
    private static final Map<ServerPlayer,Pick> PICKS=new WeakHashMap<>();
    private CenterEquipmentSelection() {}
    public static UUID session(ServerPlayer actor){var pick=PICKS.get(actor);return pick==null?null:pick.token();}
    /** Cancels selection only; an already saved receiver and physical blocks are untouched. */
    public static void cancel(ServerPlayer actor){PICKS.remove(actor);}

    public static void begin(ServerPlayer actor,UUID warehouse) {
        var value=CargoWarehouseRegistry.get(actor.getServer()).find(warehouse).orElseThrow();
        if(!CargoWarehouseRegistry.manages(actor,value))throw new IllegalArgumentException("setup_permission");
        CargoShipmentRegistry.get(actor.getServer()).requireUnused("warehouse",warehouse);
        PICKS.put(actor,new Pick(warehouse,UUID.randomUUID(),actor.getServer().overworld().getGameTime()+6000,null,null));
        prompt(actor,"pick.packager");
    }
    private static void prompt(ServerPlayer actor,String key){actor.displayClientMessage(Component.translatable("ccec.center."+key),true);}
    private static void progress(ServerPlayer actor,Pick pick,String hint){
        var data=new CompoundTag();data.putBoolean("EquipmentProgress",true);data.putUUID("EquipmentSession",pick.token());
        data.putInt("EquipmentStep",pick.packager()==null?0:1);data.putString("EquipmentHint",hint);
        if(pick.packager()!=null)data.put("EquipmentPackager",pick.packager().save());
        PacketDistributor.sendToPlayer(actor,new CenterState(data));
    }
    private static void cancelled(ServerPlayer actor,Pick pick,String hint){
        var data=new CompoundTag();data.putBoolean("EquipmentSelection",true);data.putBoolean("EquipmentCancelled",true);
        data.putUUID("EquipmentSession",pick.token());data.putString("EquipmentHint",hint);
        PacketDistributor.sendToPlayer(actor,new CenterState(data));
    }
    static boolean withinReach(double distanceSquared){return distanceSquared<=64;}
    static <T> Optional<T> unambiguous(List<T> candidates){return candidates.size()==1?Optional.of(candidates.getFirst()):Optional.empty();}
    private static boolean validBox(ServerPlayer actor,UUID warehouse,CargoInfrastructure.Location packager,CargoInfrastructure.Location location){
        var value=CargoWarehouseRegistry.get(actor.getServer()).find(warehouse).orElse(null);
        if(value==null||!CargoWarehouseRegistry.manages(actor,value)||!location.dimension().equals(packager.dimension())||location.pos().distSqr(packager.pos())>256||!(location.loaded(actor.getServer()) instanceof PostboxBlockEntity))return false;
        var point=CargoPointRegistry.find(actor.getServer(),value.point()).orElse(null);
        return point!=null&&CargoOwners.point(actor.getServer(),point).manages(actor.getServer(),actor.getUUID())&&point.routers().stream().anyMatch(ref->ref.dimension().equals(location.dimension())&&ref.pos().above().equals(location.pos())&&CargoInfrastructure.router(actor.getServer(),ref)!=null);
    }
    private static List<CargoInfrastructure.Location> nearby(ServerPlayer actor,Pick pick){
        var warehouse=CargoWarehouseRegistry.get(actor.getServer()).find(pick.warehouse()).orElse(null);if(warehouse==null)return List.of();
        var point=CargoPointRegistry.find(actor.getServer(),warehouse.point()).orElse(null);if(point==null)return List.of();
        return point.routers().stream().map(ref->new CargoInfrastructure.Location(ref.dimension(),ref.pos().above())).filter(location->validBox(actor,pick.warehouse(),pick.packager(),location)).toList();
    }
    @SubscribeEvent public static void click(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer actor))return;
        var pick=PICKS.get(actor);if(pick==null||pick.postbox()!=null)return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(actor.isShiftKeyDown()||actor.getServer().overworld().getGameTime()>pick.expires()){
            PICKS.remove(actor);cancelled(actor,pick,actor.isShiftKeyDown()?"cancelled":"expired");return;
        }
        if(event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        if(!withinReach(actor.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(event.getPos())))){progress(actor,pick,"too_far");return;}
        var location=new CargoInfrastructure.Location(actor.level().dimension().location(),event.getPos());
        boolean automatic=false;
        if(pick.packager()==null){
            if(!(location.loaded(actor.getServer()) instanceof PackagerBlockEntity)){prompt(actor,"pick.packager");progress(actor,pick,"packager_required");return;}
            pick=new Pick(pick.warehouse(),pick.token(),pick.expires(),location,null);PICKS.put(actor,pick);
            var candidates=nearby(actor,pick);var suggested=unambiguous(candidates);
            if(suggested.isEmpty()){prompt(actor,"pick.postbox");progress(actor,pick,candidates.size()>1?"ambiguous":"postbox_required");return;}
            pick=new Pick(pick.warehouse(),pick.token(),pick.expires(),location,suggested.get());automatic=true;
        }else{
            if(!validBox(actor,pick.warehouse(),pick.packager(),location)){prompt(actor,"pick.postbox_invalid");progress(actor,pick,"postbox_invalid");return;}
            pick=new Pick(pick.warehouse(),pick.token(),pick.expires(),pick.packager(),location);
        }
        PICKS.put(actor,pick);reply(actor,pick,automatic);
    }
    private static void reply(ServerPlayer actor,Pick pick,boolean automatic){var data=new CompoundTag();data.putBoolean("EquipmentSelection",true);data.putUUID("EquipmentSession",pick.token());data.putUUID("EquipmentToken",pick.token());data.putBoolean("EquipmentAuto",automatic);data.put("EquipmentPackager",pick.packager().save());data.put("EquipmentPostbox",pick.postbox().save());PacketDistributor.sendToPlayer(actor,new CenterState(data));}
    public static void confirm(ServerPlayer actor,UUID warehouse,String token){
        var pick=PICKS.get(actor);
        if(pick==null||actor.getServer().overworld().getGameTime()>pick.expires())throw new IllegalArgumentException("setup_expired");
        if(!pick.warehouse().equals(warehouse)||!pick.token().toString().equals(token)||pick.postbox()==null)throw new IllegalArgumentException("setup_selection_changed");
        if(!validBox(actor,warehouse,pick.packager(),pick.postbox()))throw new IllegalArgumentException("setup_postbox_invalid");
        if(!CargoWarehouseRegistry.get(actor.getServer()).attachReceiverAt(actor,warehouse,pick.packager()))throw new IllegalArgumentException("setup_receiver_invalid");
        var value=CargoWarehouseRegistry.get(actor.getServer()).find(warehouse).orElseThrow();
        var box=(PostboxBlockEntity)pick.postbox().loaded(actor.getServer());box.addressFilter=CargoAddress.postboxFilter(value.point());box.acceptsPackages=true;box.filterChanged();box.setChanged();box.sendData();
        PICKS.remove(actor);
    }
}
