package com.example.examplemod.economy;
import com.example.examplemod.railway.*;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Buyer shares reuse EconomyManager transport escrow; allocation and allowances persist together. */
public final class FreightRoadCharges{
 private record Charge(UUID escrow,UUID subject,UUID city,long amount){}
 private final Map<UUID,List<Charge>> trips=new LinkedHashMap<>();private final Set<UUID> closed=new HashSet<>();
 private final FreightLogistics freight;private final EconomyManager economy;
 public FreightRoadCharges(FreightLogistics f,EconomyManager e){freight=f;economy=e;}
 public boolean managed(UUID trip){return trips.containsKey(trip);}
 /** Null means ordinary railway accounting; false cancels navigation before leaving the station. */
 public Boolean reserve(Train train,CityFleetTrain fleet,UUID destination,UUID trip,long amount,RailwayManager railway){
  var weights=new TreeMap<UUID,Long>();UUID direct=freight.carriers.payer(fleet.fleetTrainId(),destination,railway);
  if(direct!=null){for(var a:freight.assignments.values())if(a.buyerTransport&&fleet.fleetTrainId().equals(a.train)&&freight.carriers.configured(a.subject)){weights.put(a.subject,1L);break;}}
  else{
   var seen=new HashSet<UUID>();
   for(var carriage:train.carriages){var inventory=carriage.storage.getAllItems();if(inventory==null)continue;
    for(int slot=0;slot<inventory.getSlots();slot++){var box=inventory.getStackInSlot(slot);if(!PackageItem.isPackage(box))continue;
     var ids=FreightLogistics.packageTokens(box);if(ids.size()!=1)continue;UUID id=ids.iterator().next();var token=economy.shipmentToken(id);if(token==null||!seen.add(id)||token.state()!=EconomyTypes.ShipmentTokenState.ACTIVE)continue;
     var a=freight.assignments.get(token.subjectId());if(a==null||!a.buyerTransport||!freight.transit.routes.regional(a.subject))continue;
     var path=freight.transit.routes.path(id);int hop=FreightTransit.cursor(box);var next=hop<0||hop>=path.size()?null:freight.point(path.get(hop));
     boolean matches=next!=null&&destination.equals(next.station)&&PackageItem.matchAddress(box,next.address);
     long contents=0;boolean exact=true;var inventoryContents=PackageItem.getContents(box);for(int i=0;i<inventoryContents.getSlots();i++){var item=inventoryContents.getStackInSlot(i);if(item.isEmpty())continue;exact&=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).equals(token.itemId());contents+=item.getCount();}
     if(!exact||contents!=token.amount())return false;
     if(!matches){a.dispatchError="wrong_destination";freight.markChanged();return false;}
     if(!a.running){a.dispatchError="paused";freight.markChanged();return false;}
     weights.merge(a.subject,token.amount(),Math::addExact);
    }
   }
  }
  if(weights.isEmpty())return null;
  return reserveShares(weights,trip,amount);
 }
 boolean reserveShares(Map<UUID,Long> input,UUID trip,long amount){
  if(trips.containsKey(trip))return false;
  var weights=new TreeMap<UUID,Long>(input);
  var subjects=new ArrayList<>(weights.keySet());var shares=FreightCostSharing.split(amount,new ArrayList<>(weights.values()));var charges=new ArrayList<Charge>();var perCity=new HashMap<UUID,Long>();
  for(int i=0;i<subjects.size();i++){var a=freight.assignments.get(subjects.get(i));UUID city=freight.carriers.controller(a);long share=shares.get(i);
   if(city==null||!freight.carriers.budget(a.subject).canReserve(share)){a.running=false;a.dispatchError="transport_budget";freight.markChanged();return false;}
   perCity.merge(city,share,Math::addExact);charges.add(new Charge(UUID.nameUUIDFromBytes((trip+":"+a.subject).getBytes(StandardCharsets.UTF_8)),a.subject,city,share));
  }
  for(var c:charges)if(economy.treasury(c.city)<perCity.get(c.city)){var a=freight.assignments.get(c.subject);a.running=false;a.dispatchError="insufficient_funds";freight.markChanged();return false;}
  var reserved=new ArrayList<Charge>();
  for(var c:charges){if(!economy.reserveTransport(c.escrow,true,c.city,c.amount)){for(var old:reserved){economy.settleTransport(old.escrow,true,old.city,old.amount,List.of());freight.carriers.budget(old.subject).settle(old.escrow,0);}return false;}
   if(!freight.carriers.budget(c.subject).reserve(c.escrow,c.amount))throw new IllegalStateException("Freight allowance changed on server thread");reserved.add(c);
  }
  trips.put(trip,List.copyOf(charges));freight.markChanged();return true;
 }
 public boolean settle(UUID trip,List<EconomyManager.TransportPayout> payouts){
  if(closed.contains(trip))return true;var charges=trips.get(trip);if(charges==null)return false;
  long total=payouts.stream().mapToLong(EconomyManager.TransportPayout::amount).reduce(0,Math::addExact);if(total<0||total>charges.stream().mapToLong(Charge::amount).sum())return false;var shares=FreightCostSharing.split(total,charges.stream().map(Charge::amount).toList());
  long[] remaining=payouts.stream().mapToLong(EconomyManager.TransportPayout::amount).toArray();int recipient=0;
  for(int i=0;i<charges.size();i++){var c=charges.get(i);long paid=shares.get(i),left=paid;var allocations=new ArrayList<EconomyManager.TransportPayout>();
   while(left>0){while(recipient<remaining.length&&remaining[recipient]==0)recipient++;if(recipient>=remaining.length)throw new IllegalStateException("Freight payout imbalance");long value=Math.min(left,remaining[recipient]);var p=payouts.get(recipient);allocations.add(new EconomyManager.TransportPayout(p.cityAccount(),p.ownerId(),value));remaining[recipient]-=value;left-=value;}
   if(!economy.transportSettled(c.escrow)&&!economy.settleTransport(c.escrow,true,c.city,c.amount,allocations))return false;
   freight.carriers.budget(c.subject).settle(c.escrow,paid);
  }
  closed.add(trip);freight.markChanged();return true;
 }
 /** A save interrupted between economy reservation and railway ledger creation is refunded. */
 public void reconcile(RailwayManager railway){
  var ledgers=new HashMap<UUID,RailwayTripLedger>();railway.tripLedgers().forEach(t->ledgers.put(t.id(),t));
  for(UUID trip:trips.keySet())if(!closed.contains(trip)){
   var ledger=ledgers.get(trip);if(ledger==null)settle(trip,List.of());
   else if(!ledger.active())settle(trip,ledger.legs().stream().map(leg->new EconomyManager.TransportPayout(leg.ownerType()==RailwayOwnershipTypes.OwnerType.CITY,leg.ownerId(),leg.charge(leg.usedLength(ledger.actualDistance())))).toList());
  }
 }

 public CompoundTag save(){var root=new CompoundTag();var list=new ListTag();trips.forEach((trip,charges)->{var t=new CompoundTag();t.putUUID("Trip",trip);t.putBoolean("Closed",closed.contains(trip));var cs=new ListTag();for(var c:charges){var v=new CompoundTag();v.putUUID("Escrow",c.escrow);v.putUUID("Subject",c.subject);v.putUUID("City",c.city);v.putLong("Amount",c.amount);cs.add(v);}t.put("Shares",cs);list.add(t);});root.put("Trips",list);return root;}
 public void load(CompoundTag root){for(Tag raw:root.getList("Trips",10)){var t=(CompoundTag)raw;UUID id=t.getUUID("Trip");var cs=new ArrayList<Charge>();for(Tag r:t.getList("Shares",10)){var v=(CompoundTag)r;if(v.getLong("Amount")<0)throw new IllegalStateException("Negative freight charge");cs.add(new Charge(v.getUUID("Escrow"),v.getUUID("Subject"),v.getUUID("City"),v.getLong("Amount")));}trips.put(id,List.copyOf(cs));if(t.getBoolean("Closed"))closed.add(id);}}
}
