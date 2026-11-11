package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;

/** Headless production-policy checks. Does not pretend to simulate Create, trains or a running game. */
public final class CargoCenterCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException|ArithmeticException expected){rejected=true;}require(rejected,message);}
    private static final class Accounts implements CargoCommerceBook.Accounts {
        final Map<OwnerRef,Long> balances=new HashMap<>();
        public long balance(OwnerRef owner){return balances.getOrDefault(owner,0L);}
        public boolean debit(OwnerRef owner,long n){if(n<0||balance(owner)<n)return false;balances.put(owner,balance(owner)-n);return true;}
        public boolean canCredit(OwnerRef owner,long n){return n>=0&&balance(owner)<=Long.MAX_VALUE-n;}
        public void credit(OwnerRef owner,long n){balances.put(owner,Math.addExact(balance(owner),n));}
        public long maximum(){return 1_000_000;}
        public void dirty(){}
        long total(){return balances.values().stream().mapToLong(Long::longValue).sum();}
    }
    public static void main(String[] args)throws Exception{
        var buyer=OwnerRef.player(UUID.randomUUID());var seller=OwnerRef.player(UUID.randomUUID());var carrier=new OwnerRef(OwnerRef.Kind.CITY,UUID.randomUUID());
        require(OwnerRef.load(carrier.save()).equals(carrier),"CITY ownership round trip");require(buyer.manages(null,buyer.id())&&!buyer.manages(null,seller.id()),"PLAYER authorization does not depend on city lookup");
        var privateAccess=new CargoOwners.Access(seller,CargoTrainRegistry.AccessMode.PRIVATE,Set.of(),Set.of());
        require(!CargoOwners.permits(null,buyer,privateAccess),"private transport denies outsider");require(CargoOwners.permits(null,buyer,new CargoOwners.Access(seller,CargoTrainRegistry.AccessMode.PUBLIC,Set.of(),Set.of())),"public transport explicit access");
        require(CargoOwners.permits(null,buyer,new CargoOwners.Access(seller,CargoTrainRegistry.AccessMode.RESTRICTED,Set.of(buyer.id()),Set.of())),"restricted player grant");
        require(CargoOwners.permits(null,carrier,new CargoOwners.Access(seller,CargoTrainRegistry.AccessMode.RESTRICTED,Set.of(),Set.of(carrier.id()))),"restricted city grant");
        require(CargoWarehouseRegistry.nativePermission(seller.id(),true,seller.id()),"registered native owner can fulfill while offline");require(!CargoWarehouseRegistry.nativePermission(seller.id(),true,buyer.id()),"native owner change/lock revokes offline delegation");require(CargoWarehouseRegistry.nativePermission(seller.id(),false,buyer.id()),"unlocked native network follows Create public interaction policy");
        UUID from=UUID.randomUUID(),via=UUID.randomUUID(),to=UUID.randomUUID(),train=UUID.randomUUID(),line=UUID.randomUUID();
        var route=List.of(new CargoRoutePlanner.Leg(line,from,to,train,20),new CargoRoutePlanner.Leg(UUID.randomUUID(),from,via,train,2),new CargoRoutePlanner.Leg(UUID.randomUUID(),via,to,train,3),new CargoRoutePlanner.Leg(UUID.randomUUID(),via,from,train,0));
        var cheapest=CargoRoutePlanner.shortest(route,from,to,CargoRoutePlanner.Preference.CHEAPEST).orElseThrow();require(cheapest.price()==5&&cheapest.legs().size()==2,"cheapest real declared route selected");
        var fewer=CargoRoutePlanner.shortest(route,from,to,CargoRoutePlanner.Preference.FEWEST_TRANSFERS).orElseThrow();require(fewer.price()==20&&fewer.legs().size()==1,"fewer transfers does not pretend to be cheaper");
        require(CargoRoutePlanner.shortest(route,to,from,CargoRoutePlanner.Preference.CHEAPEST).isEmpty(),"directed routes never invent reverse service");
        require(CargoRoutePlanner.shortest(List.of(new CargoRoutePlanner.Leg(line,from,via,train,Long.MAX_VALUE),new CargoRoutePlanner.Leg(line,via,to,train,1)),from,to,CargoRoutePlanner.Preference.CHEAPEST).isEmpty(),"overflowing price rejected");
        var address=new CargoAddress(via,to,UUID.randomUUID());require(CargoAddress.parse(address.toString()).orElseThrow().equals(address),"correlated address round trip");require(address.withNext(from).shipment().equals(address.shipment()),"correlation survives redirect");
        require(!CargoAddress.mayForward(address.toString(),new CargoAddress(via,to).toString()),"manual forwarding cannot strip shipment ID");require(CargoAddress.parse(new CargoAddress(via,to).toString()).isPresent(),"legacy address compatibility");
        var accounts=new Accounts();accounts.balances.put(buyer,1000L);var book=new CargoCommerceBook(accounts);UUID offerId=UUID.randomUUID(),warehouse=UUID.randomUUID(),order=UUID.randomUUID();var item=new CompoundTag();item.putString("id","minecraft:iron_ingot");
        book.publish(offerId,seller.id(),seller,warehouse,item,"Iron",64,4);var fees=List.of(new CargoCommerceBook.Fee(line,0,carrier,10),new CargoCommerceBook.Fee(UUID.randomUUID(),1,seller,5));
        var reserved=book.reserve(order,buyer,seller,order,offerId,16,64,fees);require(accounts.balance(buyer)==921&&reserved.total()==79,"one exact debit into escrow");require(book.offer(offerId).orElseThrow().remaining()==48,"offer queue capacity changes without pretending to reserve Create stock");
        book.reserve(order,buyer,seller,order,offerId,16,64,fees);require(accounts.balance(buyer)==921&&book.offer(offerId).orElseThrow().remaining()==48,"duplicate purchase is idempotent");
        require(book.markAttempted(order)&&!book.markAttempted(order),"only one native request attempt authorized");require(!book.refundUnattempted(order),"ambiguous/started native request never refunds automatically");
        require(!book.settle(order,false,h->true)&&!book.settle(order,true,h->h==0)&&accounts.balance(seller)==0,"no payment for extraction, partial delivery or unproven paid hop");
        var file=Files.createTempFile("ccec-cargo-center-",".nbt");NbtIo.writeCompressed(book.save(),file);var saved=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());Files.delete(file);book=new CargoCommerceBook(accounts);book.load(saved);
        require(!book.markAttempted(order),"attempt flag survives restart");require(book.settle(order,true,h->true),"full native delivery and service proof release escrow");require(accounts.balance(seller)==69&&accounts.balance(carrier)==10&&accounts.total()==1000,"seller and multiple carriers receive exact existing CR with conservation");
        var settled=book.save();book=new CargoCommerceBook(accounts);book.load(settled);for(int i=0;i<100;i++)require(!book.settle(order,true,h->true)&&accounts.total()==1000,"repeated receipt/restart never pays twice");
        var finalBook=book;rejects(()->finalBook.reserve(UUID.randomUUID(),buyer,seller,UUID.randomUUID(),offerId,100,400,List.of()),"offer overcommit rejected before debit");require(accounts.balance(buyer)==921,"failed reservation preserves balance");
        UUID own=UUID.randomUUID();book.reserve(own,buyer,seller,own,null,1,0,List.of());require(book.refundUnattempted(own)&&!book.refundUnattempted(own),"unattempted cancellation is idempotent");
        var clone=book.offer(offerId).orElseThrow().item();clone.putString("id","forged");require(book.offer(offerId).orElseThrow().item().getString("id").equals("minecraft:iron_ingot"),"offer components are not mutable through snapshots");
        var selected=new CompoundTag();selected.putString("Kind","offer");var forms=CenterForms.forms("trade",selected);require(forms.stream().map(t->((CompoundTag)t).getString("Action")).anyMatch("BUY"::equals),"public offer has actual purchase form without manager privileges");
        selected.putString("Kind","point");require(CenterForms.forms("points",selected).stream().noneMatch(t->((CompoundTag)t).getString("Action").equals("POINT_BIND")),"read-only point does not advertise privileged actions");
        var ownership=new CargoOwners();require(ownership.access("train/"+train,buyer.id(),CargoTrainRegistry.AccessMode.PRIVATE).owner().equals(buyer),"legacy player UUID resolves to PLAYER without creating another train registration");
        var first=new CompoundTag();var firstRows=new ListTag();var firstRow=CenterService.row(from,"First","","AVAILABLE");firstRow.putString("Kind","train");firstRow.putBoolean("Manage",true);firstRows.add(firstRow);first.put("Rows",firstRows);first.put("SelectedRow",firstRow.copy());var next=first.copy();next.remove("SelectedRow");next.getList("Rows",10).getCompound(0).putBoolean("Manage",false);
        require(CenterDelta.apply(first,CenterDelta.diff(first,next)).equals(next),"permission revocation removes selected details and updates visible rows through delta");
        for(int i=0;i<100;i++){var changed=next.copy();changed.getList("Rows",10).getCompound(0).putString("Status","MOVE_"+i);require(CenterDelta.apply(next,CenterDelta.diff(next,changed)).equals(changed),"view patch matches exact next snapshot");next=changed;}
        var empty=next.copy();empty.put("Rows",new ListTag());require(CenterDelta.apply(next,CenterDelta.diff(next,empty)).equals(empty),"removed object leaves no stale client row");
        var changed=empty.copy();var changedRows=new ListTag();changedRows.add(firstRow.copy());var otherRow=firstRow.copy();otherRow.putUUID("Id",to);changedRows.add(otherRow);changed.put("Rows",changedRows);require(CenterDelta.apply(empty,CenterDelta.diff(empty,changed)).equals(changed),"new/reordered page preserves order");
        for(int width:new int[]{320,426,500,640,854,1280})for(int height:new int[]{240,360,720}){var layout=com.example.examplemod.client.logistics.CenterLayout.of(width,height);var buttons=List.of(layout.map(),layout.previous(),layout.next(),layout.actions());for(var a:buttons){require(a.x()>layout.sidebar()&&a.y()>=0&&a.right()<=width&&a.bottom()<=height,"controls stay inside scaled viewport");for(var b:buttons)if(a!=b)require(!a.overlaps(b),"pagination and actions never overlap");}require(65+8*layout.navigationStep()<height-18,"all nine navigation sections remain reachable");}
        var languages=new ArrayList<com.google.gson.JsonObject>();for(String locale:List.of("ru_ru","en_us")){try(var input=CargoCenterCheck.class.getClassLoader().getResourceAsStream("assets/createcityexpansioncore/lang/"+locale+".json")){languages.add(com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());}}
        for(String section:List.of("trains","points","lines","warehouses","trade","access"))for(String kind:List.of("train","point","line","warehouse","offer")){var object=new CompoundTag();object.putString("Kind",kind);object.putBoolean("Manage",true);for(Tag raw:CenterForms.forms(section,object)){var form=(CompoundTag)raw;for(var language:languages){require(language.has("ccec.center.action."+form.getString("Action")),"every advertised action has a translation");for(Tag f:form.getList("Fields",10))require(language.has("ccec.center.field."+((CompoundTag)f).getString("Key")),"every form field has a translation");}}}
        CargoWorkflowCheck.run();
        com.example.examplemod.client.logistics.CenterUiCheck.run();
        CenterEquipmentCheck.run();
        System.out.println("PASS: "+checks+" Logistics Center ownership, route, correlation, escrow, replay and form checks");
    }
}
