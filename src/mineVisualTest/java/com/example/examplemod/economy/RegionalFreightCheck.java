package com.example.examplemod.economy;

import com.example.examplemod.city.City;
import com.example.examplemod.network.FreightNetworkRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.*;

/** Executes real routing persistence, token eligibility and final economic receipt.
 * World inventory moves / Create train schedules still require an in-game run. */
public final class RegionalFreightCheck {
    private static int checks;
    private static final ResourceLocation WORLD=ResourceLocation.parse("minecraft:overworld"),IRON=ResourceLocation.parse("minecraft:iron_ingot");
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    private static final class Physical implements EconomyManager.PhysicalTransaction {
        int commits;public boolean prepare(){return true;}public boolean commit(){commits++;return true;}public void rollback(){}
    }
    private static final class Fixture {
        final EconomyManager manager=new EconomyManager();
        final UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID(),operator=UUID.randomUUID(),actor=UUID.randomUUID();
        final FreightPoint source=point(seller,"Source",0),hub=point(operator,"Hub",32),second=point(operator,"Second",64),destination=point(buyer,"Destination",96);
        final IndustrialContract contract=new IndustrialContract(UUID.randomUUID(),EconomyTypes.ContractKind.ORDINARY,EconomyTypes.ContractMode.EXCLUSIVE,buyer,actor,"","",IRON,128,8192,destination.id,null,0,12000);
        final ShippingManifest manifest=new ShippingManifest(UUID.randomUUID(),contract.id(),seller,128,12000);
        final FreightAssignment assignment;
        Fixture()throws Exception {
            for(var p:List.of(source,hub,second,destination)){p.status="online";manager.freight().points.put(p.id,p);}
            hub.kind=second.kind=FreightPoint.Kind.HUB;hub.publicTransit=second.publicTransit=true;
            map(manager,"contracts").put(contract.id(),contract);contract.accept(seller,actor,1);
            map(manager,"manifests").put(manifest.manifestId(),manifest);
            manager.freight().bind(contract.id(),source.id,destination);assignment=manager.freight().assignments.get(contract.id());assignment.running=true;
            manager.freight().transit.routes.set(contract.id(),List.of(hub.id,second.id));
        }
        UUID dispatch(long amount){
            UUID[] id={null};require(manager.authorizeExportPackage(manifest.manifestId(),IRON,amount,2,t->id[0]=t)==EconomyTypes.IntakeAttributionError.NONE,"real package authorization");return id[0];
        }
    }
    private static FreightPoint point(UUID city,String name,int x){return new FreightPoint(UUID.randomUUID(),city,WORLD,new BlockPos(x,64,0),new BlockPos(x+2,64,0),name,FreightPoint.Mode.BOTH);}
    @SuppressWarnings("unchecked") private static Map<UUID,Object> map(EconomyManager manager,String name)throws Exception {Field f=EconomyManager.class.getDeclaredField(name);f.setAccessible(true);return (Map<UUID,Object>)f.get(manager);}
    public static void main(String[] args)throws Exception {
        bootstrap();
        var book=new FreightRoutingBook();UUID subject=UUID.randomUUID(),token=UUID.randomUUID(),h1=UUID.randomUUID(),h2=UUID.randomUUID(),finalPoint=UUID.randomUUID();
        book.set(subject,List.of(h1,h2));book.freeze(token,subject,finalPoint);
        require(book.next(token,h1,0).equals(h2),"first centre forwards to second");
        require(book.next(token,h2,1).equals(finalPoint),"second centre forwards to final receiver");
        require(book.next(token,finalPoint,2)==null,"final receiver is never another transit step");
        require(book.next(token,h2,0)==null,"wrong physical hub cannot advance the cursor");
        require(book.next(token,h1,-1)==null&&book.next(token,h1,Integer.MAX_VALUE)==null,"invalid cursor fails closed");
        book.set(subject,List.of());book.freeze(token,subject,UUID.randomUUID());
        require(book.path(token).equals(List.of(h1,h2,finalPoint)),"already sealed token cannot be retargeted by editing an order");
        book.direct(subject);require(!book.regional(subject)&&!book.path(token).isEmpty(),"changing future dispatch does not delete an existing token itinerary");
        var restored=new FreightRoutingBook();restored.load(book.save());require(restored.path(token).equals(book.path(token)),"sealed itinerary survives serialization");
        rejects(()->book.set(subject,List.of(h1,h1)),"route loops rejected");
        rejects(()->book.set(subject,List.of(h1,h2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())),"more than four centres rejected");
        var legacy=new FreightRoutingBook();legacy.load(new CompoundTag());require(!legacy.regional(subject),"old saves remain direct");
        var future=new CompoundTag();future.putInt("Version",2);rejects(()->legacy.load(future),"unknown routing version cannot silently become direct");

        Fixture f=new Fixture();var transit=f.manager.freight().transit;long escrow=f.contract.escrow();require(escrow==8192,"test holds a non-zero real escrow");
        UUID first=f.dispatch(64);ShipmentToken seal=f.manager.shipmentToken(first);
        require(f.manager.freight().tokenTargets(first,f.destination.id),"economic destination stays final");
        require(!f.manager.freight().tokenTargets(first,f.hub.id),"intermediate centre is not an economic receiver");
        require(transit.address(f.assignment).equals(f.hub.address),"transport envelope addresses the first hub");
        require(transit.eligibleToken(seal,f.destination.id,f.hub.id,0,IRON,64,3),"sealed cargo may enter its first hub");
        require(!transit.eligibleToken(seal,f.destination.id,f.second.id,0,IRON,64,3),"second hub cannot skip a required step");
        require(transit.eligibleToken(seal,f.destination.id,f.second.id,1,IRON,64,3),"next hub accepts the advanced cursor");
        require(!transit.eligibleToken(seal,f.hub.id,f.hub.id,0,IRON,64,3),"wrong final destination rejected");
        require(!transit.eligibleToken(seal,f.destination.id,f.hub.id,0,IRON,63,3),"short package rejected");
        require(!transit.eligibleToken(seal,f.destination.id,f.hub.id,0,ResourceLocation.parse("minecraft:diamond"),64,3),"wrong contents rejected");
        require(!transit.eligibleToken(seal,f.destination.id,f.hub.id,0,IRON,64,13000),"expired package not forwarded");
        Physical intermediate=new Physical();
        require(f.manager.acceptShipmentPackage(f.hub.id,first,IRON,64,3,false,intermediate)==EconomyTypes.IntakeAttributionError.WRONG_INTAKE,"centre cannot finalize the trade");
        require(intermediate.commits==0&&f.contract.deliveredAmount()==0&&f.contract.escrow()==escrow&&f.manager.treasury(f.seller)==0,"no goods credit or CR payout at hub");
        var sellerCity=new City(f.seller,"Seller",f.actor,BlockPos.ZERO);
        f.assignment.running=false;
        require(transit.configure(null,sellerCity,f.contract.id(),List.of(),false).equals("route_locked"),"active cargo locks server-side itinerary edits even while paused");
        var wrongCity=new City(UUID.randomUUID(),"Stranger",UUID.randomUUID(),BlockPos.ZERO);
        require(transit.configure(null,wrongCity,f.contract.id(),List.of(),false).equals("permission"),"unrelated city cannot edit an itinerary");
        Physical partial=new Physical();require(f.manager.acceptShipmentPackage(f.destination.id,first,IRON,64,3,false,partial)==EconomyTypes.IntakeAttributionError.NONE,"first real receipt accepted");
        require(f.contract.deliveredAmount()==64&&f.contract.escrow()==escrow&&f.manager.treasury(f.seller)==0,"partial receipt retains escrow until existing final settlement");
        require(!transit.eligibleToken(seal,f.destination.id,f.hub.id,0,IRON,64,3),"spent token cannot be forwarded again");
        f.assignment.running=true;UUID last=f.dispatch(64);
        CompoundTag save=f.manager.save(new CompoundTag(),null);
        var load=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);load.setAccessible(true);
        var restarted=(EconomyManager)load.invoke(null,save,null);
        require(save.getInt("FormatVersion")==12,"new routing data has an explicit economy format version");
        var legacySave=save.copy();legacySave.putInt("FormatVersion",9);legacySave.getCompound("FreightLogistics").remove("Routing");
        for(Tag raw:legacySave.getCompound("FreightLogistics").getList("Points",10)){var p=(CompoundTag)raw;p.remove("Kind");p.remove("PublicTransit");p.remove("Forwarding");p.remove("HubBuffer");}
        var legacyEconomy=(EconomyManager)load.invoke(null,legacySave,null);
        require(!legacyEconomy.freight().transit.routes.regional(f.contract.id())&&legacyEconomy.freight().point(f.hub.id).kind==FreightPoint.Kind.ENDPOINT,"v9 migration keeps old deliveries direct and points as endpoints");
        require(legacyEconomy.contract(f.contract.id()).deliveredAmount()==64&&legacyEconomy.contract(f.contract.id()).escrow()==escrow&&legacyEconomy.freight().tokenTargets(last,f.destination.id),"v9 migration preserves financial progress, escrow and final token identity");
        require(restarted.freight().transit.routes.path(last).equals(List.of(f.hub.id,f.second.id,f.destination.id)),"complete economy reload preserves a live multi-leg itinerary");
        require(restarted.freight().tokenTargets(last,f.destination.id),"restart retains final economic destination");
        require(restarted.freight().point(f.destination.id).status.equals("unloaded"),"reload waits for fresh world observation");
        restarted.freight().point(f.destination.id).status="online"; // Fixture supplies the observation a live server normally provides.
        Physical receipt=new Physical();require(restarted.acceptShipmentPackage(f.destination.id,last,IRON,64,4,false,receipt)==EconomyTypes.IntakeAttributionError.NONE,"final receipt after restart");
        require(restarted.contract(f.contract.id()).state()==EconomyTypes.ContractState.COMPLETED,"full order completed");
        require(restarted.treasury(f.seller)==escrow&&restarted.treasury(f.operator)==0,"only supplier paid; intermediary mints no CR");
        Physical replay=new Physical();require(restarted.acceptShipmentPackage(f.destination.id,last,IRON,64,5,false,replay)==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,"final replay refused");
        require(replay.commits==0&&restarted.treasury(f.seller)==escrow,"replay cannot move goods or pay twice");

        f.hub.hubBuffer=new BlockPos(34,64,-1);f.hub.forwarding=false;
        var node=FreightPoint.load(f.hub.save());require(node.kind==FreightPoint.Kind.HUB&&node.publicTransit&&!node.forwarding&&node.hubBuffer.equals(f.hub.hubBuffer),"centre identity and fixed buffer survive restart");
        var old=f.hub.save();old.remove("Kind");old.remove("PublicTransit");old.remove("Forwarding");old.remove("HubBuffer");
        var endpoint=FreightPoint.load(old);require(endpoint.kind==FreightPoint.Kind.ENDPOINT&&!endpoint.publicTransit&&endpoint.hubBuffer==null,"old point migrates as private endpoint");
        endpoint.station=UUID.randomUUID();endpoint.status="unloaded";require(FreightTransit.availableOrUnloaded(endpoint),"unloaded remote node is not a broken node");
        endpoint.status="station_missing";require(!FreightTransit.availableOrUnloaded(endpoint),"known broken station blocks new dispatch");
        endpoint.status="unloaded";endpoint.enabled=false;require(!FreightTransit.availableOrUnloaded(endpoint),"explicitly disabled node stays blocked");
        for(var axis:List.of(net.minecraft.core.Direction.Axis.X,net.minecraft.core.Direction.Axis.Z))for(int radius=1;radius<=3;radius++){
            var positions=FreightStorage.vaultPositions(BlockPos.ZERO,axis,radius,radius*3);
            require(positions.size()==radius*radius*radius*3,"all native vault inventory blocks included");
            require(new HashSet<>(positions).size()==positions.size(),"vault slots are not presented twice");
        }
        require(FreightStorage.vaultPositions(BlockPos.ZERO,net.minecraft.core.Direction.Axis.Y,3,9).isEmpty(),"unsupported vault axis fails closed");
        for(var action:FreightNetworkRequest.Action.values()){
            var request=new FreightNetworkRequest(BlockPos.ZERO,action,subject,true,List.of(h1,h2));var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try{FreightNetworkRequest.STREAM_CODEC.encode(buf,request);require(request.equals(FreightNetworkRequest.STREAM_CODEC.decode(buf)),"bounded network round trip "+action);}finally{buf.release();}
        }
        rejects(()->new FreightNetworkRequest(BlockPos.ZERO,FreightNetworkRequest.Action.ROUTE,subject,true,List.of(h1,h1)),"packet route loop rejected");
        System.out.println("PASS: "+checks+" regional routing, migration, token authority and economic settlement checks");
    }
    private static void bootstrap()throws Exception {
        Field field=com.example.examplemod.Config.class.getDeclaredField("SPEC");field.setAccessible(true);var spec=(net.neoforged.neoforge.common.ModConfigSpec)field.get(null);
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();spec.correct(config);config.set("items",List.of());
        var type=Class.forName("net.neoforged.fml.config.LoadedConfig");var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)constructor.newInstance(config,null,null));
    }
}
