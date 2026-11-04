package com.example.examplemod.cargo.center;

import net.minecraft.nbt.*;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** CR escrow and settlement flags live INSIDE EconomyManager's save alongside actual balances. */
public final class CargoCommerceBook {
    public interface Accounts {long balance(OwnerRef owner);boolean debit(OwnerRef owner,long amount);boolean canCredit(OwnerRef owner,long amount);void credit(OwnerRef owner,long amount);long maximum();void dirty();}
    public enum State { HELD,ATTEMPTED,SETTLED,REFUNDED,BLOCKED,CANCELLING,CANCELLED }
    public record Offer(UUID id,UUID creator,OwnerRef owner,UUID warehouse,CompoundTag item,String itemName,int remaining,long unitPrice,boolean active){
        public Offer{Objects.requireNonNull(id);Objects.requireNonNull(owner);item=item.copy();if(remaining<0||unitPrice<1)throw new IllegalArgumentException("Invalid offer");}
        @Override public CompoundTag item(){return item.copy();}
    }
    public record Fee(UUID line,int hop,OwnerRef recipient,long amount){public Fee{if(hop<0||amount<0)throw new IllegalArgumentException("Invalid carrier fee");}}
    public record Order(UUID id,OwnerRef payer,OwnerRef seller,UUID shipment,UUID offer,long goods,List<Fee> fees,int quantity,State state,String problem){
        public Order{fees=List.copyOf(fees);if(goods<0||quantity<1)throw new IllegalArgumentException("Invalid order");}
        public long total(){long total=goods;for(var fee:fees)total=Math.addExact(total,fee.amount());return total;}
        Order with(State next,String reason){return new Order(id,payer,seller,shipment,offer,goods,fees,quantity,next,reason);}
    }
    private final Accounts accounts;
    private final Map<UUID,Offer> offers=new LinkedHashMap<>();
    private final Map<UUID,Order> orders=new LinkedHashMap<>();
    public CargoCommerceBook(Accounts accounts){this.accounts=accounts;}
    public List<Offer> offers(){return List.copyOf(offers.values());}public List<Order> orders(){return List.copyOf(orders.values());}
    public Optional<Offer> offer(UUID id){return Optional.ofNullable(offers.get(id));}public Optional<Order> order(UUID id){return Optional.ofNullable(orders.get(id));}
    public Offer publish(UUID id,UUID actor,OwnerRef owner,UUID warehouse,CompoundTag item,String itemName,int count,long price){
        if(offers.containsKey(id)||item.isEmpty()||count<1||count>4096||price<1||price>accounts.maximum())throw new IllegalArgumentException("Invalid offer");
        var offer=new Offer(id,actor,owner,warehouse,item,itemName,count,price,true);offers.put(id,offer);accounts.dirty();return offer;
    }
    public boolean close(UUID id){var o=offers.get(id);if(o==null||!o.active())return false;offers.put(id,new Offer(id,o.creator(),o.owner(),o.warehouse(),o.item(),o.itemName(),o.remaining(),o.unitPrice(),false));accounts.dirty();return true;}
    /** Retry of the same purchase cannot debit or consume offer capacity again. Goods remain unreserved in Create. */
    public Order reserve(UUID id,OwnerRef payer,OwnerRef seller,UUID shipment,UUID offerId,int quantity,long goods,List<Fee> fees){
        var existing=orders.get(id);if(existing!=null){if(!existing.payer().equals(payer)||!existing.seller().equals(seller)||!existing.shipment().equals(shipment)||!Objects.equals(existing.offer(),offerId)||existing.quantity()!=quantity||existing.goods()!=goods||!existing.fees().equals(fees))throw new IllegalArgumentException("Order identity conflict");return existing;}
        var offer=offerId==null?null:offers.get(offerId);
        if(offerId!=null&&(offer==null||!offer.active()||quantity>offer.remaining()||!offer.owner().equals(seller)||Math.multiplyExact(offer.unitPrice(),quantity)!=goods))throw new IllegalArgumentException("Offer changed");
        var order=new Order(id,payer,seller,shipment,offerId,goods,fees,quantity,State.HELD,"");long total=order.total();
        if(total<0||total>accounts.maximum()||!accounts.debit(payer,total))throw new IllegalArgumentException("Insufficient CR");
        orders.put(id,order);if(offer!=null)offers.put(offerId,new Offer(offer.id(),offer.creator(),offer.owner(),offer.warehouse(),offer.item(),offer.itemName(),offer.remaining()-quantity,offer.unitPrice(),offer.active()));accounts.dirty();return order;
    }
    /** Mark before invoking Create. Ambiguous interrupted attempts are held, never automatically rebroadcast/refunded. */
    public boolean markAttempted(UUID id){var o=orders.get(id);if(o==null||o.state()!=State.HELD)return false;orders.put(id,o.with(State.ATTEMPTED,""));accounts.dirty();return true;}
    public void block(UUID id,String reason){var o=orders.get(id);if(o==null||o.state()==State.SETTLED||o.state()==State.REFUNDED||o.state()==State.CANCELLED||o.state()==State.CANCELLING)return;orders.put(id,o.with(State.BLOCKED,reason));accounts.dirty();}
    public boolean refundUnattempted(UUID id){var o=orders.get(id);if(o==null||o.state()!=State.HELD||!accounts.canCredit(o.payer(),o.total()))return false;
        var offer=o.offer()==null?null:offers.get(o.offer());if(offer!=null)offers.put(offer.id(),new Offer(offer.id(),offer.creator(),offer.owner(),offer.warehouse(),offer.item(),offer.itemName(),Math.addExact(offer.remaining(),o.quantity()),offer.unitPrice(),offer.active()));
        accounts.credit(o.payer(),o.total());orders.put(id,o.with(State.REFUNDED,"Cancelled before Create request"));accounts.dirty();return true;}
    /** Stop dispatch without treating an attempted Create call as proof that no box exists. */
    public boolean requestCancellation(UUID id){
        var order=orders.get(id);if(order==null)return false;
        if(order.state()==State.CANCELLING)return true;
        if(order.state()!=State.ATTEMPTED&&order.state()!=State.BLOCKED&&order.state()!=State.HELD)return false;
        orders.put(id,order.with(State.CANCELLING,"Cancellation pending physical receipt or recovery"));accounts.dirty();return true;
    }
    /**
     * Close only when every requested unit has a physical outcome. Delivered goods pay the
     * seller; recovered goods refund the buyer. Carrier fees follow native proven units.
     * Missing and merely queued boxes never release escrow.
     */
    public boolean settleCancellation(UUID id,int delivered,int recovered,IntUnaryOperator hopUnits){
        var order=orders.get(id);if(order==null||order.state()!=State.CANCELLING||delivered<0||recovered<0
                ||(long)delivered+recovered!=order.quantity())return false;
        var credits=new HashMap<OwnerRef,Long>();long paid=proportion(order.goods(),delivered,order.quantity());
        if(paid>0)credits.merge(order.seller(),paid,Math::addExact);
        for(var fee:order.fees()){
            int proven=hopUnits.applyAsInt(fee.hop());if(proven<0||proven>order.quantity())return false;
            long amount=proportion(fee.amount(),proven,order.quantity());paid=Math.addExact(paid,amount);
            if(amount>0)credits.merge(fee.recipient(),amount,Math::addExact);
        }
        long refund=Math.subtractExact(order.total(),paid);if(refund>0)credits.merge(order.payer(),refund,Math::addExact);
        for(var credit:credits.entrySet())if(!accounts.canCredit(credit.getKey(),credit.getValue()))return false;
        var offer=order.offer()==null?null:offers.get(order.offer());
        int capacity=offer==null?0:Math.addExact(offer.remaining(),recovered);
        for(var credit:credits.entrySet())accounts.credit(credit.getKey(),credit.getValue());
        if(offer!=null)offers.put(offer.id(),new Offer(offer.id(),offer.creator(),offer.owner(),offer.warehouse(),offer.item(),offer.itemName(),capacity,offer.unitPrice(),offer.active()));
        orders.put(id,order.with(State.CANCELLED,""));accounts.dirty();return true;
    }
    private static long proportion(long amount,int units,int quantity){
        return Math.addExact(Math.multiplyExact(amount/quantity,units),Math.multiplyExact(amount%quantity,(long)units)/quantity);
    }
    /** All payments commit together after complete goods and native evidence for EVERY charged leg. */
    public boolean settle(UUID id,boolean fullGoodsReceived,IntPredicate nativeHopProven){
        var order=orders.get(id);if(order==null||(order.state()!=State.ATTEMPTED&&order.state()!=State.BLOCKED)||!fullGoodsReceived)return false;
        var credits=new HashMap<OwnerRef,Long>();if(order.goods()>0)credits.put(order.seller(),order.goods());
        for(var fee:order.fees()){if(fee.amount()>0&&!nativeHopProven.test(fee.hop()))return false;credits.merge(fee.recipient(),fee.amount(),Math::addExact);}
        for(var credit:credits.entrySet())if(!accounts.canCredit(credit.getKey(),credit.getValue()))return false;
        for(var credit:credits.entrySet())accounts.credit(credit.getKey(),credit.getValue());orders.put(id,order.with(State.SETTLED,""));accounts.dirty();return true;
    }
    public CompoundTag save(){var root=new CompoundTag();root.putInt("Version",2);var catalog=new ListTag();for(var o:offers.values()){var t=new CompoundTag();t.putUUID("Id",o.id());t.putUUID("Creator",o.creator());t.put("Owner",o.owner().save());t.putUUID("Warehouse",o.warehouse());t.put("Item",o.item());t.putString("ItemName",o.itemName());t.putInt("Remaining",o.remaining());t.putLong("Price",o.unitPrice());t.putBoolean("Active",o.active());catalog.add(t);}root.put("Offers",catalog);
        var list=new ListTag();for(var o:orders.values()){var t=new CompoundTag();t.putUUID("Id",o.id());t.put("Payer",o.payer().save());t.put("Seller",o.seller().save());t.putUUID("Shipment",o.shipment());if(o.offer()!=null)t.putUUID("Offer",o.offer());t.putLong("Goods",o.goods());t.putInt("Quantity",o.quantity());t.putString("State",o.state().name());t.putString("Problem",o.problem());var fees=new ListTag();for(var f:o.fees()){var v=new CompoundTag();v.putUUID("Line",f.line());v.putInt("Hop",f.hop());v.put("Recipient",f.recipient().save());v.putLong("Amount",f.amount());fees.add(v);}t.put("Fees",fees);list.add(t);}root.put("Orders",list);return root;}
    public void load(CompoundTag root){if(root.isEmpty())return;if(root.getInt("Version")<1||root.getInt("Version")>2)throw new IllegalArgumentException("Cargo commerce version");offers.clear();orders.clear();
        for(Tag raw:root.getList("Offers",10)){var t=(CompoundTag)raw;var offer=new Offer(t.getUUID("Id"),t.getUUID("Creator"),OwnerRef.load(t.getCompound("Owner")),t.getUUID("Warehouse"),t.getCompound("Item"),t.getString("ItemName"),t.getInt("Remaining"),t.getLong("Price"),t.getBoolean("Active"));if(offers.putIfAbsent(offer.id(),offer)!=null)throw new IllegalArgumentException("Duplicate offer");}
        for(Tag raw:root.getList("Orders",10)){var t=(CompoundTag)raw;var fees=new ArrayList<Fee>();for(Tag value:t.getList("Fees",10)){var v=(CompoundTag)value;fees.add(new Fee(v.getUUID("Line"),v.getInt("Hop"),OwnerRef.load(v.getCompound("Recipient")),v.getLong("Amount")));}var order=new Order(t.getUUID("Id"),OwnerRef.load(t.getCompound("Payer")),OwnerRef.load(t.getCompound("Seller")),t.getUUID("Shipment"),t.hasUUID("Offer")?t.getUUID("Offer"):null,t.getLong("Goods"),fees,t.getInt("Quantity"),State.valueOf(t.getString("State")),t.getString("Problem"));if(order.total()<0||orders.putIfAbsent(order.id(),order)!=null)throw new IllegalArgumentException("Invalid/duplicate order");}
    }
}
