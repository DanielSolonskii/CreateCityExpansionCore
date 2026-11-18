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
        checkSplitSaves();

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

    private static void checkSplitSaves() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var lookup = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var payer = OwnerRef.player(UUID.randomUUID());
        var seller = OwnerRef.player(UUID.randomUUID());
        var accounts = new Accounts();
        accounts.balances.put(payer, 1000L);
        var book = new CargoCommerceBook(accounts);
        var tolls = new RailTollBook(accounts);
        UUID offer = UUID.randomUUID();
        var item = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        book.publish(offer, seller.id(), seller, UUID.randomUUID(), (CompoundTag) item.save(lookup), "Iron", 10, 5);

        for (boolean legacy : List.of(false, true)) {
            UUID id = UUID.randomUUID();
            book.reserve(id, payer, seller, id, offer, 2, 10, List.of());
            var physical = loadShipment(id, lookup, item, legacy ? "BLOCKED" : "REQUESTED",
                    legacy ? "master_buffer_unavailable" : "", false, 0);
            if (!legacy) physical.rejectBeforeDispatch(physical.find(id).orElseThrow(), "charter_dispatch_blocked");
            book.block(id, "Preflight failed");
            book.requestCancellation(id);
            physical.requestCancellation(id);
            var pendingFinance = book.save();
            var blockedPhysical = physical.save(new CompoundTag(), lookup);
            physical = CargoShipmentRegistry.load(blockedPhysical, lookup);
            var shipment = physical.find(id).orElseThrow();
            accounts.balances.put(payer, 9999L);
            require(!CargoFulfillment.reconcileOrder(book, tolls, physical, book.order(id).orElseThrow(), shipment)
                    && shipment.stage() == CargoShipmentRegistry.Stage.BLOCKED, "overflow keeps the reservation for a later refund");
            accounts.balances.put(payer, 990L);
            require(CargoFulfillment.reconcileOrder(book, tolls, physical, book.order(id).orElseThrow(), shipment)
                    && accounts.balance(payer) == 1000 && book.offer(offer).orElseThrow().remaining() == 10
                    && shipment.stage() == CargoShipmentRegistry.Stage.CANCELLED, "preflight rejection returns escrow and releases its lock");

            var cancelledPhysical = physical.save(new CompoundTag(), lookup);
            physical = CargoShipmentRegistry.load(blockedPhysical, lookup);
            tolls.reserveTrip(id, payer, 3, new CompoundTag());
            require(CargoFulfillment.reconcileOrder(book, tolls, physical, book.order(id).orElseThrow(), physical.find(id).orElseThrow())
                    && physical.find(id).orElseThrow().stage() == CargoShipmentRegistry.Stage.CANCELLED
                    && tolls.tripClosed(id) && accounts.balance(payer) == 1000 && book.offer(offer).orElseThrow().remaining() == 10,
                    "persisted refund repairs an older physical snapshot once");
            accounts.balances.put(payer, 990L);
            book.load(pendingFinance);
            physical = CargoShipmentRegistry.load(cancelledPhysical, lookup);
            require(CargoFulfillment.reconcileOrder(book, tolls, physical, book.order(id).orElseThrow(), physical.find(id).orElseThrow())
                    && accounts.balance(payer) == 1000 && book.offer(offer).orElseThrow().remaining() == 10,
                    "physical pre-dispatch cancellation repairs an older financial snapshot once");
        }

        UUID attempted = UUID.randomUUID();
        book.reserve(attempted, payer, seller, attempted, offer, 2, 10, List.of());
        book.markAttempted(attempted);
        book.block(attempted, "Create request interrupted");
        book.requestCancellation(attempted);
        var ambiguous = loadShipment(attempted, lookup, item, "BLOCKED", "charter_dispatch_blocked", false, 0);
        require(!CargoFulfillment.reconcileOrder(book, tolls, ambiguous, book.order(attempted).orElseThrow(), ambiguous.find(attempted).orElseThrow())
                && accounts.balance(payer) == 990, "an untracked native request never authorizes a refund");
        var claimed = loadShipment(attempted, lookup, item, "BLOCKED", "master_buffer_unavailable", true, 0);
        require(!book.refundRejectedShipment(claimed.find(attempted).orElseThrow()), "a packaging claim takes precedence over a legacy reason");
        var boxed = loadShipment(attempted, lookup, item, "BLOCKED", "master_buffer_unavailable", false, 1);
        require(!book.refundRejectedShipment(boxed.find(attempted).orElseThrow()), "physical parcels take precedence over a legacy reason");

        UUID settled = UUID.randomUUID();
        book.reserve(settled, payer, seller, settled, offer, 2, 10, List.of());
        require(book.markAttempted(settled) && book.settle(settled, true, hop -> true), "settlement fixture pays the seller");
        book.load(book.save());
        for (int delivered : List.of(1, 2)) {
            var physical = loadShipment(settled, lookup, item, "DELIVERED", "", true, delivered);
            var shipment = physical.find(settled).orElseThrow();
            for (int replay = 0; replay < 2; replay++) {
                require(CargoFulfillment.reconcileOrder(book, tolls, physical, book.order(settled).orElseThrow(), shipment)
                        && shipment.stage() == (delivered == 2 ? CargoShipmentRegistry.Stage.COMPLETED : CargoShipmentRegistry.Stage.DELIVERED)
                        && physical.inUse("warehouse", shipment.sourceWarehouse()) == (delivered != 2)
                        && accounts.balance(seller) == 10, "financial settlement releases locks only with complete physical proof and never pays twice");
            }
        }
    }

    private static CargoShipmentRegistry loadShipment(UUID id, net.minecraft.core.HolderLookup.Provider lookup,
            net.minecraft.world.item.ItemStack item, String stage, String problem, boolean claimed, int delivered) {
        var entry = new CompoundTag();
        entry.putUUID("Id", id);
        for (String field : List.of("Creator", "SourceWarehouse", "DestinationWarehouse", "Source", "Final")) {
            entry.putUUID(field, UUID.randomUUID());
        }
        entry.put("Owner", OwnerRef.player(entry.getUUID("Creator")).save());
        entry.put("Item", item.save(lookup));
        entry.putInt("Quantity", 2);
        var location = new CargoInfrastructure.Location(net.minecraft.world.level.Level.OVERWORLD.location(), net.minecraft.core.BlockPos.ZERO);
        entry.put("Receiver", location.save());
        entry.put("Inventory", location.save());
        entry.putString("Stage", stage);
        entry.putString("Problem", problem);
        entry.putBoolean("PackagingClaimed", claimed);
        if (delivered > 0) {
            var parcel = new CompoundTag();
            parcel.putUUID("Id", UUID.randomUUID());
            parcel.putInt("Units", delivered);
            parcel.putBoolean("Delivered", true);
            var parcels = new net.minecraft.nbt.ListTag();
            parcels.add(parcel);
            entry.put("Parcels", parcels);
        }
        var entries = new net.minecraft.nbt.ListTag();
        entries.add(entry);
        var root = new CompoundTag();
        root.putInt("Version", 3);
        root.put("Entries", entries);
        return CargoShipmentRegistry.load(root, lookup);
    }
}
