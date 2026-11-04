package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.example.examplemod.economy.EconomyManager;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Quote -> escrow -> single native request -> observed physical receipt -> idempotent settlement. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class CargoFulfillment {
    private record Prepared(UUID token,long expires,OwnerRef payer,OwnerRef seller,UUID offer,
                            CargoWarehouseRegistry.Warehouse source,CargoWarehouseRegistry.Warehouse destination,
                            ItemStack item,int quantity,long goods,CargoRoutePlanner.Route route,List<CargoCommerceBook.Fee> fees) {}
    private static final Map<ServerPlayer,Prepared> QUOTES=new WeakHashMap<>();
    private CargoFulfillment() {}
    public static String stockKey(MinecraftServer s,ItemStack type){return UUID.nameUUIDFromBytes(type.copyWithCount(1).save(s.registryAccess()).toString().getBytes(StandardCharsets.UTF_8)).toString();}
    public static ItemStack stockChoice(ServerPlayer actor,CargoWarehouseRegistry.Warehouse warehouse,String token){var ticker=CargoWarehouseRegistry.ticker(actor.getServer(),warehouse);if(ticker==null)throw new IllegalArgumentException("Warehouse unavailable");return ticker.warehouseStock(actor,warehouse.id()).orElseThrow().stream().map(CargoTickerBlockEntity.StockEntry::type).filter(item->stockKey(actor.getServer(),item).equals(token)).findFirst().orElseThrow();}
    private static Prepared prepare(ServerPlayer actor,UUID selected,boolean purchase,CompoundTag values){
        var s=actor.getServer();var registry=CargoWarehouseRegistry.get(s);var book=EconomyManager.get(actor.serverLevel()).cargo();
        var payer=CenterOperations.owner(values.getString("Owner"));if(!payer.manages(s,actor.getUUID()))throw new IllegalArgumentException("Payment permission denied");
        var offer=purchase?book.offer(selected).orElseThrow():null;var source=registry.find(purchase?offer.warehouse():selected).orElseThrow();var destination=registry.find(CenterOperations.id(values,"Destination")).orElseThrow();
        if(!CargoWarehouseRegistry.access(s,destination).owner().operates(s,actor.getUUID())||CargoWarehouseRegistry.receivingInventory(s,destination)==null)throw new IllegalArgumentException("Your receiving Packager must be configured and loaded");
        OwnerRef seller=CargoWarehouseRegistry.access(s,source).owner();ItemStack item;
        int quantity=Math.toIntExact(CenterOperations.number(values,"Quantity"));if(quantity<1||quantity>4096)throw new IllegalArgumentException("Invalid quantity");
        if(purchase){if(!offer.active()||quantity>offer.remaining()||!offer.owner().equals(seller))throw new IllegalArgumentException("Offer changed");var access=CargoOwners.get(s).access("offer/"+offer.id(),offer.creator(),CargoTrainRegistry.AccessMode.PUBLIC);if(!CargoOwners.permits(s,OwnerRef.player(actor.getUUID()),access))throw new IllegalArgumentException("Offer access denied");item=ItemStack.parseOptional(s.registryAccess(),offer.item());}
        else {if(!CargoWarehouseRegistry.permits(actor,source))throw new IllegalArgumentException("Warehouse access denied");item=stockChoice(actor,source,values.getString("Stock"));}
        if(item.isEmpty())throw new IllegalArgumentException("Item unavailable");
        var route=CargoRoutePlanner.plan(s,payer,source.point(),destination.point(),CargoRoutePlanner.Preference.valueOf(values.getString("Preference"))).orElseThrow(()->new IllegalArgumentException("No physically available permitted route"));
        var fees=new ArrayList<CargoCommerceBook.Fee>();for(int i=0;i<route.legs().size();i++){var leg=route.legs().get(i);var line=CargoLineRegistry.get(s).find(leg.line()).orElseThrow();fees.add(new CargoCommerceBook.Fee(line.id(),i,CargoLineRegistry.access(s,line).owner(),leg.tariff()));}
        return new Prepared(UUID.randomUUID(),actor.serverLevel().getGameTime()+1200,payer,seller,purchase?offer.id():null,source,destination,item,quantity,purchase?Math.multiplyExact(offer.unitPrice(),quantity):0,route,List.copyOf(fees));
    }
    public static CompoundTag quote(ServerPlayer actor,UUID selected,boolean purchase,CompoundTag values){var p=prepare(actor,selected,purchase,values);QUOTES.put(actor,p);var t=new CompoundTag();t.putUUID("Token",p.token());t.putLong("Goods",p.goods());t.putLong("Transport",p.route().price());t.putLong("Total",Math.addExact(p.goods(),p.route().price()));t.putString("Source",p.source().name());t.putString("Destination",p.destination().name());var route=new ListTag();for(var leg:p.route().legs()){var line=CargoLineRegistry.get(actor.getServer()).find(leg.line()).orElseThrow();var v=new CompoundTag();v.putString("Name",line.name());v.putLong("Price",leg.tariff());v.putString("Carrier",CargoLineRegistry.access(actor.getServer(),line).owner().name(actor.getServer()));v.putString("Terms",line.terms());route.add(v);}t.put("Route",route);return t;}
    public static void confirm(ServerPlayer actor,CompoundTag request,boolean purchase){
        UUID orderId=UUID.nameUUIDFromBytes((actor.getUUID()+"/"+request.getUUID("Request")).getBytes(StandardCharsets.UTF_8));var book=EconomyManager.get(actor.serverLevel()).cargo();if(book.order(orderId).isPresent())return;
        var expected=QUOTES.get(actor);if(expected==null||!request.hasUUID("QuoteToken")||!expected.token().equals(request.getUUID("QuoteToken"))||expected.expires()<actor.serverLevel().getGameTime())throw new IllegalArgumentException("Quote expired; calculate again");
        var current=prepare(actor,request.getUUID("Selected"),purchase,request.getCompound("Values"));
        if(!expected.payer().equals(current.payer())||!Objects.equals(expected.offer(),current.offer())||!expected.source().equals(current.source())||!expected.destination().equals(current.destination())||!ItemStack.isSameItemSameComponents(expected.item(),current.item())||expected.quantity()!=current.quantity()||expected.goods()!=current.goods()||!expected.fees().equals(current.fees())||!expected.route().equals(current.route()))throw new IllegalArgumentException("Quote changed; calculate again");
        book.reserve(orderId,expected.payer(),expected.seller(),orderId,expected.offer(),expected.quantity(),expected.goods(),expected.fees());
        try{CargoShipmentRegistry.get(actor.getServer()).create(actor.getServer(),orderId,actor.getUUID(),expected.payer(),expected.source(),expected.destination(),expected.item(),expected.quantity(),expected.route());}
        catch(RuntimeException invalid){book.refundUnattempted(orderId);throw invalid;}
        QUOTES.remove(actor);
    }
    public static void cancel(ServerPlayer actor,UUID shipmentId){
        var registry=CargoShipmentRegistry.get(actor.getServer());var shipment=registry.find(shipmentId).orElseThrow();
        var book=EconomyManager.get(actor.getServer().overworld()).cargo();var order=book.order(shipmentId).orElseThrow();
        boolean permitted=actor.hasPermissions(2)||order.payer().manages(actor.getServer(),actor.getUUID());
        if(!permitted)throw new IllegalArgumentException("shipment_cancel_denied");
        if(order.state()==CargoCommerceBook.State.CANCELLING){registry.requestCancellation(shipmentId);return;}
        if((order.state()==CargoCommerceBook.State.REFUNDED||order.state()==CargoCommerceBook.State.CANCELLED)&&shipment.stage()==CargoShipmentRegistry.Stage.CANCELLED)return;
        if(!shipment.canCancel(order,permitted))throw new IllegalArgumentException("shipment_not_cancellable");
        if(ShipmentCancellation.immediateRefund(order,shipment.stage(),shipment.packagingClaimed(),shipment.parcelCount())){
            if(!book.refundUnattempted(order.id()))throw new IllegalArgumentException("shipment_refund_unavailable");
            registry.cancelled(shipmentId);
        }else{
            if(!book.requestCancellation(order.id()))throw new IllegalArgumentException("shipment_not_cancellable");
            registry.requestCancellation(shipmentId);
        }
    }
    public static void publish(ServerPlayer actor,UUID warehouseId,CompoundTag values,UUID nonce){var warehouse=CargoWarehouseRegistry.get(actor.getServer()).find(warehouseId).orElseThrow();if(!CargoWarehouseRegistry.manages(actor,warehouse))throw new IllegalArgumentException("Warehouse management denied");var type=stockChoice(actor,warehouse,values.getString("Stock"));var book=EconomyManager.get(actor.serverLevel()).cargo();var offer=book.publish(nonce,actor.getUUID(),CargoWarehouseRegistry.access(actor.getServer(),warehouse).owner(),warehouseId,(CompoundTag)type.save(actor.registryAccess()),type.getHoverName().getString(),Math.toIntExact(CenterOperations.number(values,"Quantity")),CenterOperations.number(values,"Price"));var owner=CargoWarehouseRegistry.access(actor.getServer(),warehouse).owner();CargoOwners.get(actor.getServer()).change(actor.getServer(),actor.getUUID(),"offer/"+offer.id(),actor.getUUID(),CargoTrainRegistry.AccessMode.PUBLIC,new CargoOwners.Access(owner,CargoTrainRegistry.AccessMode.valueOf(values.getString("Access")),Set.of(),Set.of()));}
    private static ServerPlayer operator(MinecraftServer s,CargoWarehouseRegistry.Warehouse w){var ticker=CargoWarehouseRegistry.ticker(s,w);if(ticker==null)return null;return s.getPlayerList().getPlayers().stream().filter(p->CargoWarehouseRegistry.manages(p,w)&&ticker.behaviour.mayInteract(p)).findFirst().orElse(null);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){var server=event.getServer();if(server.overworld().getGameTime()%20!=0)return;var registry=CargoShipmentRegistry.get(server);registry.reconcile(server);var book=EconomyManager.get(server.overworld()).cargo();int budget=4;
        for(var order:book.orders()){var shipment=registry.find(order.shipment()).orElse(null);if(shipment==null)continue;
            // Economy and physical metadata are separate SavedData files. Repair a completed
            // half-save without issuing another native request or repeating a credit.
            if(order.state()==CargoCommerceBook.State.CANCELLED){if(shipment.cancellationResolved()&&shipment.stage()!=CargoShipmentRegistry.Stage.CANCELLED)registry.cancellationCompleted(shipment.id());continue;}
            if(order.state()==CargoCommerceBook.State.REFUNDED){if(shipment.stage()==CargoShipmentRegistry.Stage.REQUESTED&&!shipment.packagingClaimed()&&shipment.parcelCount()==0)registry.cancelled(shipment.id());continue;}
            if(shipment.cancellationRequested()&&order.state()!=CargoCommerceBook.State.CANCELLING){book.requestCancellation(order.id());continue;}
            if(order.state()==CargoCommerceBook.State.CANCELLING){
                registry.requestCancellation(shipment.id());
                if(order.seller().available(server)&&order.fees().stream().allMatch(f->f.recipient().available(server))
                        &&(shipment.delivered()==0||shipment.receivingOwnerValid(server))&&shipment.cancellationResolved()
                        &&book.settleCancellation(order.id(),shipment.delivered(),shipment.recovered(),shipment::hopUnits))registry.cancellationCompleted(shipment.id());
                continue;
            }
            if(order.seller().available(server)&&order.fees().stream().allMatch(f->f.recipient().available(server))&&shipment.receivingOwnerValid(server)&&shipment.physicalComplete()&&book.settle(order.id(),true,shipment::hopComplete))registry.completed(shipment.id());
            if(order.state()!=CargoCommerceBook.State.HELD||budget<=0||shipment.stage()!=CargoShipmentRegistry.Stage.REQUESTED)continue;
            var warehouse=CargoWarehouseRegistry.get(server).find(shipment.sourceWarehouse()).orElse(null);if(warehouse==null)continue;var ticker=CargoWarehouseRegistry.ticker(server,warehouse);if(ticker==null||!CargoWarehouseRegistry.nativeAuthorized(server,warehouse))continue;
            if(!order.seller().equals(CargoWarehouseRegistry.access(server,warehouse).owner())||!shipment.receivingOwnerValid(server)){registry.blocked(shipment.id(),"Warehouse ownership changed before packaging");book.block(order.id(),"Ownership changed");continue;}
            if(ticker.shipmentStock(server,warehouse.id(),shipment.id())<shipment.quantity())continue;
            if(!book.markAttempted(order.id()))continue;budget--;
            try{var result=ticker.requestShipment(server,warehouse.id(),shipment.id());if(result!=CargoTickerBlockEntity.RequestResult.DISPATCH_ATTEMPTED){registry.blocked(shipment.id(),result.name());book.block(order.id(),result.name());}}
            catch(RuntimeException failed){registry.blocked(shipment.id(),"Create request interrupted; no automatic retry");book.block(order.id(),"Create request interrupted");}
        }
    }
}
