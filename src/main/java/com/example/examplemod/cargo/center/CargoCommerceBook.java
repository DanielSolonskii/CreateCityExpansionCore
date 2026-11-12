package com.example.examplemod.cargo.center;

import net.minecraft.nbt.*;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** CR escrow and settlement flags live INSIDE EconomyManager's save alongside actual balances. */
public final class CargoCommerceBook {
    public interface Accounts {
        long balance(OwnerRef owner);boolean debit(OwnerRef owner,long amount);boolean canCredit(OwnerRef owner,long amount);void credit(OwnerRef owner,long amount);long maximum();void dirty();
        default OwnerRef saleRecipient(UUID offer,OwnerRef seller){return seller;}
        default void revenue(OwnerRef owner,long amount){credit(owner,amount);}
        default void revenue(UUID receipt,OwnerRef owner,long amount){revenue(owner,amount);}
        default boolean canReceiveRevenue(OwnerRef owner,long amount){return canCredit(owner,amount);}
        default boolean canReceivePayments(Map<OwnerRef,Long> revenue,Map<OwnerRef,Long> refunds){var totals=new HashMap<>(refunds);revenue.forEach((o,n)->totals.merge(o,n,Math::addExact));for(var e:totals.entrySet())if(!canCredit(e.getKey(),e.getValue()))return false;for(var e:revenue.entrySet())if(!canReceiveRevenue(e.getKey(),e.getValue()))return false;return true;}
    }
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
    private final Set<UUID> paidGoods=new HashSet<>();
    private final Map<UUID,Long> refunds=new HashMap<>();
    private final Map<UUID,CompoundTag> carrierTerms=new HashMap<>();
    public CargoCommerceBook(Accounts accounts){this.accounts=accounts;}
    public List<Offer> offers(){return List.copyOf(offers.values());}public List<Order> orders(){return List.copyOf(orders.values());}
    public long refunded(UUID id){return refunds.getOrDefault(id,0L);}
    public long carrierEarned(UUID id){var terms=carrierTerms.get(id);if(terms==null)return 0;long amount=0;for(var raw:terms.getList("Paid",8)){String phase=raw.getAsString();amount=Math.addExact(amount,terms.getCompound("PaidAmounts").contains(phase)?terms.getCompound("PaidAmounts").getLong(phase):terms.getCompound("Terms").getLong(phase));}return amount;}
    public void configureCarrier(UUID id,Map<String,Long> phases){var order=orders.get(id);if(order==null||order.fees().size()!=1)throw new IllegalArgumentException("Carrier phase order");var values=new CompoundTag();long total=0;for(var entry:phases.entrySet()){if(entry.getValue()<0)throw new IllegalArgumentException("Carrier phase amount");values.putLong(entry.getKey(),entry.getValue());total=Math.addExact(total,entry.getValue());}if(total!=order.fees().getFirst().amount())throw new IllegalArgumentException("Carrier phase total");var old=carrierTerms.get(id);if(old!=null){if(!old.getCompound("Terms").equals(values))throw new IllegalArgumentException("Carrier phase identity conflict");return;}var t=new CompoundTag();t.put("Terms",values);t.put("Paid",new ListTag());carrierTerms.put(id,t);accounts.dirty();}
    /** A trusted dispatcher calls this only after the corresponding physical arrival/unload proof. */
    public boolean payCarrierPhase(UUID id,String phase){var data=carrierTerms.get(id);return data!=null&&payCarrierPhase(id,phase,data.getCompound("Terms").getLong(phase));}
    public boolean payCarrierPhase(UUID id,String phase,long amount){var order=orders.get(id);var data=carrierTerms.get(id);if(order==null||data==null||!data.getCompound("Terms").contains(phase))return false;if(amount<0||amount>data.getCompound("Terms").getLong(phase))throw new IllegalArgumentException("Carrier payment exceeds quoted phase");var paid=data.getList("Paid",8);if(paid.stream().anyMatch(t->t.getAsString().equals(phase)))return true;if(Set.of(State.SETTLED,State.CANCELLED,State.REFUNDED).contains(order.state()))return false;var recipient=order.fees().getFirst().recipient();if(!accounts.canReceivePayments(Map.of(recipient,amount),Map.of()))return false;if(amount>0)accounts.revenue(RailTollBook.component(id,"carrier/"+phase),recipient,amount);paid.add(StringTag.valueOf(phase));data.put("Paid",paid);var amounts=data.getCompound("PaidAmounts");amounts.putLong(phase,amount);data.put("PaidAmounts",amounts);accounts.dirty();return true;}
    public Optional<Offer> offer(UUID id){return Optional.ofNullable(offers.get(id));}public Optional<Order> order(UUID id){return Optional.ofNullable(orders.get(id));}
    public Offer publish(UUID id,UUID actor,OwnerRef owner,UUID warehouse,CompoundTag item,String itemName,int count,long price){
        if(offers.containsKey(id)||item.isEmpty()||count<1||count>4096||price<1||price>accounts.maximum())throw new IllegalArgumentException("Invalid offer");
        var offer=new Offer(id,actor,owner,warehouse,item,itemName,count,price,true);offers.put(id,offer);accounts.dirty();return offer;
    }
    public boolean close(UUID id){var o=offers.get(id);if(o==null||!o.active())return false;offers.put(id,new Offer(id,o.creator(),o.owner(),o.warehouse(),o.item(),o.itemName(),o.remaining(),o.unitPrice(),false));accounts.dirty();return true;}
    /** Trusted recovery only: restores the same frozen offer after its durable physical receipt was verified. */
    public boolean reopen(UUID id){var o=offers.get(id);if(o==null||o.remaining()<=0||o.active())return false;offers.put(id,new Offer(id,o.creator(),o.owner(),o.warehouse(),o.item(),o.itemName(),o.remaining(),o.unitPrice(),true));accounts.dirty();return true;}
    /** Retry of the same purchase cannot debit or consume offer capacity again. Goods remain unreserved in Create. */
    public Order reserve(UUID id,OwnerRef payer,OwnerRef seller,UUID shipment,UUID offerId,int quantity,long goods,List<Fee> fees){
        var recipient=accounts.saleRecipient(offerId,seller);
        var existing=orders.get(id);if(existing!=null){if(!existing.payer().equals(payer)||!existing.seller().equals(recipient)||!existing.shipment().equals(shipment)||!Objects.equals(existing.offer(),offerId)||existing.quantity()!=quantity||existing.goods()!=goods||!existing.fees().equals(fees))throw new IllegalArgumentException("Order identity conflict");return existing;}
        var offer=offerId==null?null:offers.get(offerId);
        if(offerId!=null&&(offer==null||!offer.active()||quantity>offer.remaining()||!offer.owner().equals(seller)||Math.multiplyExact(offer.unitPrice(),quantity)!=goods))throw new IllegalArgumentException("Offer changed");
        var order=new Order(id,payer,recipient,shipment,offerId,goods,fees,quantity,State.HELD,"");long total=order.total();
        if(total<0||total>accounts.maximum()||!accounts.debit(payer,total))throw new IllegalArgumentException("Insufficient CR");
        orders.put(id,order);if(offer!=null)offers.put(offerId,new Offer(offer.id(),offer.creator(),offer.owner(),offer.warehouse(),offer.item(),offer.itemName(),offer.remaining()-quantity,offer.unitPrice(),offer.active()));accounts.dirty();return order;
    }
    /** Mark before invoking Create. Ambiguous interrupted attempts are held, never automatically rebroadcast/refunded. */
    public boolean markAttempted(UUID id){var o=orders.get(id);if(o==null||o.state()!=State.HELD)return false;orders.put(id,o.with(State.ATTEMPTED,""));accounts.dirty();return true;}
    public void block(UUID id,String reason){var o=orders.get(id);if(o==null||o.state()==State.SETTLED||o.state()==State.REFUNDED||o.state()==State.CANCELLED||o.state()==State.CANCELLING)return;orders.put(id,o.with(State.BLOCKED,reason));accounts.dirty();}
    public boolean refundUnattempted(UUID id){var o=orders.get(id);if(o==null||o.state()!=State.HELD)return false;long refund=Math.subtractExact(o.total(),carrierEarned(id));if(!accounts.canCredit(o.payer(),refund))return false;
        var offer=o.offer()==null?null:offers.get(o.offer());if(offer!=null)offers.put(offer.id(),new Offer(offer.id(),offer.creator(),offer.owner(),offer.warehouse(),offer.item(),offer.itemName(),Math.addExact(offer.remaining(),o.quantity()),offer.unitPrice(),offer.active()));
        accounts.credit(o.payer(),refund);refunds.put(id,refund);orders.put(id,o.with(State.REFUNDED,"Cancelled before Create request"));accounts.dirty();return true;}
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
        if(paidGoods.contains(id)&&delivered!=order.quantity())return false;
        if(paid>0&&!paidGoods.contains(id))credits.merge(order.seller(),paid,Math::addExact);
        if(carrierTerms.containsKey(id))paid=Math.addExact(paid,carrierEarned(id));
        else for(var fee:order.fees()){
            int proven=hopUnits.applyAsInt(fee.hop());if(proven<0||proven>order.quantity())return false;
            long amount=proportion(fee.amount(),proven,order.quantity());paid=Math.addExact(paid,amount);
            if(amount>0)credits.merge(fee.recipient(),amount,Math::addExact);
        }
        long refund=Math.subtractExact(order.total(),paid);var returned=refund>0?Map.of(order.payer(),refund):Map.<OwnerRef,Long>of();
        if(!accounts.canReceivePayments(credits,returned))return false;
        var offer=order.offer()==null?null:offers.get(order.offer());
        int capacity=offer==null?0:Math.addExact(offer.remaining(),recovered);
        for(var credit:credits.entrySet())accounts.revenue(RailTollBook.component(id,"cancel/"+credit.getKey()),credit.getKey(),credit.getValue());
        if(refund>0)accounts.credit(order.payer(),refund);refunds.put(id,refund);
        if(offer!=null)offers.put(offer.id(),new Offer(offer.id(),offer.creator(),offer.owner(),offer.warehouse(),offer.item(),offer.itemName(),capacity,offer.unitPrice(),offer.active()));
        orders.put(id,order.with(State.CANCELLED,""));accounts.dirty();return true;
    }
    private static long proportion(long amount,int units,int quantity){
        return Math.addExact(Math.multiplyExact(amount/quantity,units),Math.multiplyExact(amount%quantity,(long)units)/quantity);
    }
    private void validateFinancialReceipts(){for(UUID id:paidGoods)if(!orders.containsKey(id))throw new IllegalArgumentException("Orphan goods payment");for(var entry:refunds.entrySet()){var order=orders.get(entry.getKey());if(order==null||entry.getValue()>order.total())throw new IllegalArgumentException("Invalid refund amount");}for(var entry:carrierTerms.entrySet()){var order=orders.get(entry.getKey());if(order==null||order.fees().size()!=1)throw new IllegalArgumentException("Orphan carrier agreement");var terms=entry.getValue().getCompound("Terms");long total=0;for(String phase:terms.getAllKeys()){long amount=terms.getLong(phase);if(amount<0)throw new IllegalArgumentException("Negative carrier phase");total=Math.addExact(total,amount);}if(total!=order.fees().getFirst().amount())throw new IllegalArgumentException("Carrier agreement total");var unique=new HashSet<String>();for(var raw:entry.getValue().getList("Paid",8))if(!terms.contains(raw.getAsString())||!unique.add(raw.getAsString()))throw new IllegalArgumentException("Invalid carrier payment receipt");var amounts=entry.getValue().getCompound("PaidAmounts");for(String phase:amounts.getAllKeys())if(!unique.contains(phase)||amounts.getLong(phase)<0||amounts.getLong(phase)>terms.getLong(phase))throw new IllegalArgumentException("Invalid actual carrier phase amount");}}
    /** All payments commit together after complete goods and native evidence for EVERY charged leg. */
    public boolean settleGoods(UUID id,boolean fullGoodsReceived){var o=orders.get(id);if(o==null||!fullGoodsReceived||paidGoods.contains(id)||(o.state()!=State.ATTEMPTED&&o.state()!=State.BLOCKED))return false;if(!accounts.canReceivePayments(Map.of(o.seller(),o.goods()),Map.of()))return false;if(o.goods()>0)accounts.revenue(RailTollBook.component(id,"goods"),o.seller(),o.goods());paidGoods.add(id);accounts.dirty();return true;}
    public boolean settle(UUID id,boolean fullGoodsReceived,IntPredicate nativeHopProven){
        var order=orders.get(id);if(order==null||(order.state()!=State.ATTEMPTED&&order.state()!=State.BLOCKED)||!fullGoodsReceived)return false;
        var credits=new HashMap<OwnerRef,Long>();if(order.goods()>0&&!paidGoods.contains(id))credits.put(order.seller(),order.goods());
        long prepaid=carrierEarned(id);for(var fee:order.fees()){if(fee.amount()>0&&!nativeHopProven.test(fee.hop()))return false;long paid=Math.min(prepaid,fee.amount());prepaid-=paid;credits.merge(fee.recipient(),fee.amount()-paid,Math::addExact);}
        if(!accounts.canReceivePayments(credits,Map.of()))return false;
        for(var credit:credits.entrySet())accounts.revenue(RailTollBook.component(id,"settle/"+credit.getKey()),credit.getKey(),credit.getValue());if(carrierTerms.containsKey(id)){var all=new ListTag();var amounts=new CompoundTag();for(String phase:carrierTerms.get(id).getCompound("Terms").getAllKeys()){all.add(StringTag.valueOf(phase));amounts.putLong(phase,carrierTerms.get(id).getCompound("Terms").getLong(phase));}carrierTerms.get(id).put("Paid",all);carrierTerms.get(id).put("PaidAmounts",amounts);}orders.put(id,order.with(State.SETTLED,""));accounts.dirty();return true;
    }
    public CompoundTag save(){var root=new CompoundTag();root.putInt("Version",3);var catalog=new ListTag();for(var o:offers.values()){var t=new CompoundTag();t.putUUID("Id",o.id());t.putUUID("Creator",o.creator());t.put("Owner",o.owner().save());t.putUUID("Warehouse",o.warehouse());t.put("Item",o.item());t.putString("ItemName",o.itemName());t.putInt("Remaining",o.remaining());t.putLong("Price",o.unitPrice());t.putBoolean("Active",o.active());catalog.add(t);}root.put("Offers",catalog);
        var list=new ListTag();for(var o:orders.values()){var t=new CompoundTag();t.putUUID("Id",o.id());t.put("Payer",o.payer().save());t.put("Seller",o.seller().save());t.putUUID("Shipment",o.shipment());if(o.offer()!=null)t.putUUID("Offer",o.offer());t.putLong("Goods",o.goods());t.putInt("Quantity",o.quantity());t.putString("State",o.state().name());t.putString("Problem",o.problem());var fees=new ListTag();for(var f:o.fees()){var v=new CompoundTag();v.putUUID("Line",f.line());v.putInt("Hop",f.hop());v.put("Recipient",f.recipient().save());v.putLong("Amount",f.amount());fees.add(v);}t.put("Fees",fees);list.add(t);}root.put("Orders",list);var paid=new ListTag();paidGoods.stream().sorted().forEach(id->paid.add(NbtUtils.createUUID(id)));root.put("PaidGoods",paid);var rs=new ListTag();refunds.forEach((id,amount)->{var t=new CompoundTag();t.putUUID("Id",id);t.putLong("Amount",amount);rs.add(t);});root.put("Refunds",rs);var phases=new ListTag();carrierTerms.forEach((id,terms)->{var t=terms.copy();t.putUUID("Id",id);phases.add(t);});root.put("CarrierTerms",phases);return root;}
    public void load(CompoundTag root){if(root.isEmpty())return;if(root.getInt("Version")<1||root.getInt("Version")>3)throw new IllegalArgumentException("Cargo commerce version");offers.clear();orders.clear();paidGoods.clear();refunds.clear();carrierTerms.clear();for(var raw:root.getList("CarrierTerms",10)){var t=((CompoundTag)raw).copy();if(carrierTerms.putIfAbsent(t.getUUID("Id"),t)!=null)throw new IllegalArgumentException("Duplicate carrier terms");}for(var raw:root.getList("PaidGoods",11))paidGoods.add(NbtUtils.loadUUID(raw));for(var raw:root.getList("Refunds",10)){var t=(CompoundTag)raw;if(t.getLong("Amount")<0||refunds.putIfAbsent(t.getUUID("Id"),t.getLong("Amount"))!=null)throw new IllegalArgumentException("Invalid refund receipt");}
        for(Tag raw:root.getList("Offers",10)){var t=(CompoundTag)raw;var offer=new Offer(t.getUUID("Id"),t.getUUID("Creator"),OwnerRef.load(t.getCompound("Owner")),t.getUUID("Warehouse"),t.getCompound("Item"),t.getString("ItemName"),t.getInt("Remaining"),t.getLong("Price"),t.getBoolean("Active"));if(offers.putIfAbsent(offer.id(),offer)!=null)throw new IllegalArgumentException("Duplicate offer");}
        for(Tag raw:root.getList("Orders",10)){var t=(CompoundTag)raw;var fees=new ArrayList<Fee>();for(Tag value:t.getList("Fees",10)){var v=(CompoundTag)value;fees.add(new Fee(v.getUUID("Line"),v.getInt("Hop"),OwnerRef.load(v.getCompound("Recipient")),v.getLong("Amount")));}var order=new Order(t.getUUID("Id"),OwnerRef.load(t.getCompound("Payer")),OwnerRef.load(t.getCompound("Seller")),t.getUUID("Shipment"),t.hasUUID("Offer")?t.getUUID("Offer"):null,t.getLong("Goods"),fees,t.getInt("Quantity"),State.valueOf(t.getString("State")),t.getString("Problem"));if(order.total()<0||orders.putIfAbsent(order.id(),order)!=null)throw new IllegalArgumentException("Invalid/duplicate order");}validateFinancialReceipts();
    }
}
