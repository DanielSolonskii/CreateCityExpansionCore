package com.example.examplemod.cargo.center;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Pure workflow regression tests. Actual Create packaging and trains still need an in-game check. */
public final class CargoWorkflowCheck {
    private static int checks;
    private CargoWorkflowCheck() {}
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}require(rejected,message);}
    private static final class Accounts implements CargoCommerceBook.Accounts {
        final Map<OwnerRef,Long> balances=new HashMap<>();
        public long balance(OwnerRef owner){return balances.getOrDefault(owner,0L);}
        public boolean debit(OwnerRef owner,long amount){if(amount<0||balance(owner)<amount)return false;balances.put(owner,balance(owner)-amount);return true;}
        public boolean canCredit(OwnerRef owner,long amount){return amount>=0&&balance(owner)<=maximum()-amount;}
        public void credit(OwnerRef owner,long amount){if(!canCredit(owner,amount))throw new IllegalArgumentException("Credit capacity");balances.put(owner,balance(owner)+amount);}
        public long maximum(){return 10000;}
        public void dirty(){}
    }
    public static void run()throws Exception{
        var payer=OwnerRef.player(UUID.randomUUID());var seller=OwnerRef.player(UUID.randomUUID());var carrier=OwnerRef.player(UUID.randomUUID());
        var accounts=new Accounts();accounts.balances.put(payer,1000L);var book=new CargoCommerceBook(accounts);
        var item=new CompoundTag();item.putString("id","minecraft:iron_ingot");UUID offer=UUID.randomUUID(),orderId=UUID.randomUUID();
        book.publish(offer,seller.id(),seller,UUID.randomUUID(),item,"Iron",64,5);
        var order=book.reserve(orderId,payer,seller,orderId,offer,10,50,List.of(new CargoCommerceBook.Fee(UUID.randomUUID(),0,carrier,7)));
        require(book.reserve(orderId,payer,seller,orderId,offer,10,50,order.fees()).equals(order),"exact reservation replay returns original immutable order");
        rejects(()->book.reserve(orderId,payer,carrier,orderId,offer,10,50,order.fees()),"same-ID replay cannot substitute seller");
        rejects(()->book.reserve(orderId,payer,seller,orderId,null,10,50,order.fees()),"same-ID replay cannot substitute offer");
        rejects(()->book.reserve(orderId,payer,seller,orderId,offer,11,50,order.fees()),"same-ID replay cannot substitute quantity");
        rejects(()->book.reserve(orderId,payer,seller,orderId,offer,10,51,order.fees()),"same-ID replay cannot substitute goods price");
        rejects(()->book.reserve(orderId,payer,seller,orderId,offer,10,50,List.of()),"same-ID replay cannot strip carrier fees");
        require(accounts.balance(payer)==943&&book.offer(offer).orElseThrow().remaining()==54,"conflicting replays preserve one debit and original offer capacity");
        require(ShipmentCancellation.allowed(order,CargoShipmentRegistry.Stage.REQUESTED,false,0,true),"payer can cancel unattempted empty shipment");
        require(!ShipmentCancellation.allowed(order,CargoShipmentRegistry.Stage.REQUESTED,false,0,false),"read access is not permission to refund city or another player's CR");
        require(ShipmentCancellation.allowed(order,CargoShipmentRegistry.Stage.REQUESTED,true,0,true)&&!ShipmentCancellation.immediateRefund(order,CargoShipmentRegistry.Stage.REQUESTED,true,0),"native packaging claim allows cancellation request but forbids immediate refund");
        require(ShipmentCancellation.allowed(order,CargoShipmentRegistry.Stage.REQUESTED,false,1,true)&&!ShipmentCancellation.immediateRefund(order,CargoShipmentRegistry.Stage.REQUESTED,false,1),"physical parcel stays reserved during cancellation");
        for(var stage:CargoShipmentRegistry.Stage.values())
            require(ShipmentCancellation.allowed(order,stage,false,0,true)==(stage!=CargoShipmentRegistry.Stage.COMPLETED&&stage!=CargoShipmentRegistry.Stage.CANCELLED),"cancellation request follows financial finality at stage "+stage);
        require(book.refundUnattempted(orderId),"held escrow refunded before dispatch");
        require(accounts.balance(payer)==1000&&accounts.balance(seller)==0&&accounts.balance(carrier)==0,"goods and carrier fees return to payer only");
        require(book.offer(offer).orElseThrow().remaining()==64,"cancel restores offer capacity once");
        for(int i=0;i<20;i++)require(!book.refundUnattempted(orderId)&&accounts.balance(payer)==1000&&book.offer(offer).orElseThrow().remaining()==64,"cancel replay cannot create CR or offer capacity");
        var restored=new CargoCommerceBook(accounts);restored.load(book.save());
        require(!restored.refundUnattempted(orderId),"cancel replay after persistence cannot refund twice");
        require(!ShipmentCancellation.allowed(restored.order(orderId).orElseThrow(),CargoShipmentRegistry.Stage.REQUESTED,false,0,true),"refunded order cannot reopen dispatch or cancellation after partial save");
        var offered=new CompoundTag();offered.putString("Kind","shipment");offered.putBoolean("Manage",true);offered.putBoolean("CanCancel",true);
        var forms=CenterForms.forms("shipments",offered);
        require(forms.size()==1&&forms.getCompound(0).getString("Action").equals("SHIPMENT_CANCEL"),"shipment offers only explicit safe cancellation, never generic rename/access/remove");
        offered.putBoolean("CanCancel",false);require(CenterForms.forms("shipments",offered).isEmpty(),"dispatched shipment never advertises cancellation");
        UUID attemptedId=UUID.randomUUID();book.reserve(attemptedId,payer,seller,attemptedId,null,1,0,List.of(new CargoCommerceBook.Fee(UUID.randomUUID(),0,carrier,10)));
        require(book.markAttempted(attemptedId),"native request entered attempted state");
        require(ShipmentCancellation.allowed(book.order(attemptedId).orElseThrow(),CargoShipmentRegistry.Stage.REQUESTED,false,0,true)&&!book.refundUnattempted(attemptedId),"attempted native request allows cancellation but no automatic refund before physical proof");
        CargoCancellationCheck.run();

        UUID start=UUID.randomUUID(),via=UUID.randomUUID(),second=UUID.randomUUID(),end=UUID.randomUUID(),service=UUID.randomUUID(),throughTrain=UUID.randomUUID();
        var through=List.of(new CargoRoutePlanner.Leg(service,start,via,throughTrain,1),new CargoRoutePlanner.Leg(service,via,second,throughTrain,1),new CargoRoutePlanner.Leg(service,second,end,throughTrain,1),new CargoRoutePlanner.Leg(UUID.randomUUID(),start,end,UUID.randomUUID(),9));
        var preferred=CargoRoutePlanner.shortest(through,start,end,CargoRoutePlanner.Preference.FEWEST_TRANSFERS).orElseThrow();
        require(preferred.legs().size()==3&&preferred.price()==3,"through-service intermediate stops are not transfers; same-transfer tie chooses cheaper route");
        UUID otherTrain=UUID.randomUUID(),otherLine=UUID.randomUUID();
        var changing=List.of(new CargoRoutePlanner.Leg(otherLine,start,via,otherTrain,1),new CargoRoutePlanner.Leg(service,start,via,throughTrain,2),new CargoRoutePlanner.Leg(service,via,end,throughTrain,1));
        var noChange=CargoRoutePlanner.shortest(changing,start,end,CargoRoutePlanner.Preference.FEWEST_TRANSFERS).orElseThrow();
        require(noChange.price()==3&&noChange.legs().getFirst().train().equals(throughTrain),"route state keeps arriving service instead of pruning a no-transfer alternative at same point");
        var bounded=new ArrayList<CargoRoutePlanner.Leg>();UUID point=start;
        for(int i=0;i<30;i++){UUID next=UUID.randomUUID();bounded.add(new CargoRoutePlanner.Leg(service,point,next,throughTrain,0));point=next;}
        bounded.add(new CargoRoutePlanner.Leg(service,point,via,throughTrain,0));
        bounded.add(new CargoRoutePlanner.Leg(service,via,second,throughTrain,0));bounded.add(new CargoRoutePlanner.Leg(service,second,end,throughTrain,0));
        bounded.add(new CargoRoutePlanner.Leg(service,start,via,throughTrain,5));
        var withinBudget=CargoRoutePlanner.shortest(bounded,start,end,CargoRoutePlanner.Preference.CHEAPEST).orElseThrow();
        require(withinBudget.price()==5&&withinBudget.legs().size()==3,"over-budget cheap prefix cannot suppress valid route with remaining hop budget");
        var cyclic=new ArrayList<>(through);cyclic.add(new CargoRoutePlanner.Leg(service,via,start,throughTrain,0));
        for(var preference:CargoRoutePlanner.Preference.values()){
            var winner=CargoRoutePlanner.shortest(cyclic,start,end,preference).orElseThrow();var seen=new HashSet<UUID>();seen.add(start);
            require(winner.legs().stream().allMatch(leg->seen.add(leg.next())),"zero-cost cycle never improves the preferred route: "+preference);
        }
        var refs=new ShipmentLocks.References(UUID.randomUUID(),UUID.randomUUID(),start,end,through.subList(0,3));
        for(var stage:CargoShipmentRegistry.Stage.values()){
            boolean active=stage!=CargoShipmentRegistry.Stage.COMPLETED&&stage!=CargoShipmentRegistry.Stage.CANCELLED;
            require(ShipmentLocks.blocks(stage,"line",service,refs)==active,"line evidence protected until final settlement: "+stage);
            require(ShipmentLocks.blocks(stage,"warehouse",refs.destinationWarehouse(),refs)==active,"receiver binding protected until final settlement: "+stage);
            require(ShipmentLocks.blocks(stage,"point",via,refs)==active,"intermediate point protected: "+stage);
            require(ShipmentLocks.blocks(stage,"train",throughTrain,refs)==active,"quoted train registration protected: "+stage);
            require(!ShipmentLocks.blocks(stage,"line",UUID.randomUUID(),refs),"unrelated infrastructure remains editable");
        }
        System.out.println("PASS: "+checks+" shipment cancellation, refund replay and actual transfer checks");
    }
}
