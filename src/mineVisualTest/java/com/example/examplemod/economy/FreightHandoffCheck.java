package com.example.examplemod.economy;

import com.example.examplemod.railway.*;
import com.example.examplemod.status.CcecStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Headless regression for the false "Источник недоступен" (SOURCE_OFFLINE) a shipment reported
 * after a completely normal cargo handoff — plus the physical-cargo invariant that keeps that fix
 * from turning into the opposite bug.
 *
 * <p>The invariant under test is <b>UNKNOWN != ABSENT</b>. A source we cannot see (unloaded chunk),
 * or a source that reads empty for reasons we cannot account for, must never be read as proof that
 * the cargo left. {@code handedOff} may only become true from positive, server-authoritative
 * evidence: the ACTIVE shipment token inside the selected train's cargo, or a started trip (which
 * {@code tryStartTrain} only allows once the whole outstanding cargo is loaded).
 *
 * <p>Cases: <b>A</b> package still at source + source unloads -&gt; stays pre-handoff;
 * <b>B</b> positive detection in the train -&gt; handed off, then the source unloads -&gt; shipment stays
 * valid; <b>C</b> package gone from a readable source but never in the train -&gt; no silent handoff,
 * waiting state retained; <b>D</b> no forced chunk loading, no source-inventory input;
 * <b>E</b> no duplicate tokens/rewards, no weakened pre-handoff validation.
 *
 * <p>Everything is level-free: {@code state()} short-circuits before it reaches the Create track
 * graph whenever the source is the failing endpoint, and the fixture registers no track graph at
 * all, so a healthy source reports {@code route_error} here rather than {@code running}.
 */
public final class FreightHandoffCheck {
    private static int checks;
    private static final ResourceLocation ITEM=ResourceLocation.parse("minecraft:iron_ingot");
    private static final ResourceLocation OVERWORLD=ResourceLocation.parse("minecraft:overworld");

    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}

    private static final class Physical implements EconomyManager.PhysicalTransaction {
        public boolean prepare(){return true;}public boolean commit(){return true;}public void rollback(){}
    }

    private static final class Fixture {
        final EconomyManager m=new EconomyManager();
        final UUID sellerCity=UUID.randomUUID(),buyerCity=UUID.randomUUID(),player=UUID.randomUUID();
        final FreightPoint src=new FreightPoint(UUID.randomUUID(),sellerCity,OVERWORLD,new BlockPos(0,64,0),new BlockPos(2,64,0),"Source",FreightPoint.Mode.BOTH);
        final FreightPoint dst=new FreightPoint(UUID.randomUUID(),buyerCity,OVERWORLD,new BlockPos(100,64,0),new BlockPos(102,64,0),"Destination",FreightPoint.Mode.BOTH);
        final IndustrialContract c=new IndustrialContract(UUID.randomUUID(),EconomyTypes.ContractKind.ORDINARY,EconomyTypes.ContractMode.EXCLUSIVE,buyerCity,player,"","",ITEM,128,18000,dst.id,null,0,12000);
        final ShippingManifest manifest=new ShippingManifest(UUID.randomUUID(),c.id(),sellerCity,128,12000);
        Fixture()throws Exception{
            src.status="online";dst.status="online";
            m.freight().points.put(src.id,src);m.freight().points.put(dst.id,dst);
            map(m,"contracts").put(c.id(),c);
            c.accept(sellerCity,player,1);
            map(m,"manifests").put(manifest.manifestId(),manifest);
            m.freight().bind(c.id(),src.id,dst);
            m.freight().assignments.get(c.id()).running=true;
        }
        UUID token(long amount){
            UUID[] id={null};
            require(m.authorizeExportPackage(manifest.manifestId(),ITEM,amount,2,v->id[0]=v)==EconomyTypes.IntakeAttributionError.NONE,"authorize export");
            return id[0];
        }
        EconomyTypes.IntakeAttributionError receive(UUID token,long count){
            return m.acceptShipmentPackage(dst.id,token,ITEM,count,3,false,new Physical());
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID,Object> map(EconomyManager m,String name)throws Exception{
        Field f=EconomyManager.class.getDeclaredField(name);f.setAccessible(true);return(java.util.Map<UUID,Object>)f.get(m);
    }

    private static long inFlight(EconomyManager m,UUID subject){
        return m.shipmentTokens().stream().filter(t->t.subjectId().equals(subject)&&t.state()==EconomyTypes.ShipmentTokenState.ACTIVE).mapToLong(ShipmentToken::amount).sum();
    }

    /** The exact card the Logistics screen renders from {@code State} + {@code UiStatus}. */
    private static CcecStatus card(FreightLogistics freight,FreightAssignment a,String state,long flight,RailwayManager railway){
        return freight.uiStatus(a,state,0L,128L,flight,true,railway);
    }

    public static void main(String[] args)throws Exception{
        bootstrapConfig();

        // ---------------------------------------------------- valid source -> addressed package
        Fixture f=new Fixture();
        FreightLogistics freight=f.m.freight();
        FreightAssignment a=freight.assignments.get(f.c.id());
        UUID pointId=f.src.id;
        String address=f.src.address;
        BlockPos postbox=f.src.postbox,packager=f.src.packager;
        require(f.src.online()&&f.dst.online(),"valid source and destination to start with");
        require(a.running&&freight.assignments.get(f.c.id())==a,"dispatch running on the bound assignment");

        RailwayManager railway=new RailwayManager();
        UUID createId=UUID.randomUUID();
        CityFleetTrain fleet=railway.registerTrain(createId,UUID.randomUUID(),"Hopper");
        RailwayRouteOption option=new RailwayRouteOption(UUID.randomUUID(),RailwayRouteStrategy.CHEAPEST,
                UUID.randomUUID(),UUID.randomUUID(),false,List.of(),120,10,200,List.of(),11);
        railway.proposeRoutes(fleet.fleetTrainId(),List.of(option));
        require(railway.selectRoute(fleet.fleetTrainId(),option.id(),1L),"route selection accepted");
        a.train=fleet.fleetTrainId();
        a.routeSelection=railway.selectedRoute(fleet.fleetTrainId()).selectionId();

        UUID token=f.token(64);
        long flight=inFlight(f.m,f.c.id());
        require(flight==64L,"the addressed package is stamped and in flight");
        require(!a.handedOff,"a freshly stamped package has not been handed off yet");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.WAITING_FOR_CARGO,
                "a healthy shipment waits for its cargo and never renders an endpoint block");

        // ------------- a stuck dispatch names the gate it is stuck at, instead of one catch-all
        require(a.waitReason.isEmpty(),"a dispatch that has not been evaluated yet names no reason");
        a.waitReason="train_not_at_source";
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.TRAIN_NOT_AT_SOURCE,
                "a train parked away from the source station is reported as such");
        require(card(freight,a,freight.state(a),flight,railway).action()==CcecStatus.Action.OPEN_FREIGHT_POINT,
                "the named reason keeps the actionable freight point step");
        a.waitReason="cargo_not_counted";
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.CARGO_NOT_COUNTED,
                "packages in the train that this dispatch cannot accept are reported as such");
        a.running=false;
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.READY_TO_DISPATCH,
                "a stopped dispatch never shows a stale reason");
        a.running=true;
        a.waitReason="";
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.WAITING_FOR_CARGO,
                "no named gate falls back to the ordinary cargo wait");
        require(a.waitReason.isEmpty(),"the reason is presentation-only and is never mistaken for cargo state");
        FreightAssignment reloaded=FreightAssignment.load(a.save());
        require(reloaded.waitReason.isEmpty(),"the reason is not persisted, so a restart cannot show a stale one");
        a.waitReason="cargo_not_counted";
        require(FreightAssignment.load(a.save()).waitReason.isEmpty(),"a recorded reason is re-derived on the next tick");
        a.waitReason="";

        // -------------------------------------------- E: pre-handoff source validation still bites
        f.src.status="postbox_missing";
        require("source_unavailable".equals(freight.state(a)),"a broken source blocks the shipment before the cargo is handed off");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.SOURCE_OFFLINE,
                "the pre-handoff outage is reported as SOURCE_OFFLINE");
        require(f.m.authorizeExportPackage(f.manifest.manifestId(),ITEM,64,2,id->{})==EconomyTypes.IntakeAttributionError.INVALID_MANIFEST,
                "no further package leaves a broken source");
        require(a.running,"the outage pauses, it never cancels the dispatch");

        // ------------------------- A: package still at source, source chunk unloads -> stays put
        f.src.status="unloaded";   // observe(): neither endpoint chunk is readable -> point not online
        require(!FreightLogistics.handoff(false,false,false,true),
                "an ACTIVE package that never left the source is never handed off");
        require(!a.handedOff,
                "an unreadable source is UNKNOWN: it never becomes proof that the cargo moved");
        require("source_unavailable".equals(freight.state(a)),
                "an unloaded source still gates a shipment whose cargo never moved");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.SOURCE_OFFLINE,
                "before handoff an unreadable source is still SOURCE_OFFLINE");

        // ------- C: package gone from a READABLE source but never seen in the train -> no handoff
        f.src.status="online";
        require(!FreightLogistics.handoff(false,false,false,true),
                "an empty source the train never received is UNKNOWN, not proof of handoff");
        require(!a.handedOff,"a package that vanished at a readable source is not silently handed off");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.WAITING_FOR_CARGO,
                "the waiting state is retained instead of a silent handoff");

        // ----------------------------------- B: positive proof, then the source goes unreadable
        require(FreightLogistics.handoff(false,true,false,true),
                "cargo positively detected in the selected train is proof of handoff");
        require(FreightLogistics.handoff(false,false,true,true),
                "a started trip is proof of handoff: navigation only starts once the cargo is loaded");
        require(FreightLogistics.handoff(true,false,false,true),
                "the handoff holds while the shipment is still in the air");
        require(!FreightLogistics.handoff(true,false,false,false),
                "with nothing in the air the next shipment starts un-handed-off");

        a.handedOff=true;          // what refreshHandoff() records once the train carries the cargo
        f.src.status="unloaded";   // the source itself goes unreadable afterwards
        require(f.src.id.equals(pointId)&&f.src.address.equals(address),
                "the source is left exactly as it was: no automatic rebind, no identity churn");
        require(f.src.postbox.equals(postbox)&&f.src.packager.equals(packager),"the Postbox/Packager pair is never retargeted");
        require("running".equals(freight.state(a)),"a handed-off shipment keeps its own state instead of the source's");
        require(card(freight,a,freight.state(a),flight,railway).code()!=CcecStatus.Code.SOURCE_OFFLINE,
                "the current shipment stays valid after the source unloads");

        a.running=false;
        require("paused".equals(freight.state(a)),"a handed-off shipment that is not running reports paused");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.READY_TO_DISPATCH,
                "READY_TO_DISPATCH continues after the handoff");
        a.running=true;
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.WAITING_FOR_CARGO,
                "WAITING_FOR_CARGO continues after the handoff");

        RailwayTripLedger trip=new RailwayTripLedger(UUID.randomUUID(),createId,fleet.fleetTrainId(),
                RailwayOwnershipTypes.OwnerType.CITY,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),100,50,42,List.of());
        railway.addTrip(trip);
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.IN_TRANSIT,
                "IN_TRANSIT continues while the trip is under way");

        // ------------------------------------------------- destination stays relevant until intake
        f.dst.status="unloaded";
        require("destination_unavailable".equals(freight.state(a)),"the destination still gates an in-flight shipment");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.DESTINATION_OFFLINE,
                "the destination outage is still reported");
        f.dst.status="online";

        // ------------------------------------ a genuinely broken source still blocks future cargo
        f.src.status="postbox_missing";
        a.handedOff=false;
        require("source_unavailable".equals(freight.state(a)),"the next shipment is gated by the source again");
        require(card(freight,a,freight.state(a),flight,railway).code()==CcecStatus.Code.SOURCE_OFFLINE,
                "future shipments from the actually broken source report SOURCE_OFFLINE");
        require(f.m.authorizeExportPackage(f.manifest.manifestId(),ITEM,64,2,id->{})==EconomyTypes.IntakeAttributionError.INVALID_MANIFEST,
                "future packages from the broken source stay blocked");
        require(a.running,"blocking the next shipment still never cancels the dispatch");

        // --------------------------------------------- no stale dispatchError after validation
        f.src.status="online";
        a.dispatchError="source_offline";
        freight.clearResolvedDispatchError(a);
        require(a.dispatchError.isEmpty(),"a successful fresh validation clears the recorded endpoint failure");
        f.src.status="unloaded";
        a.dispatchError="source_offline";
        freight.clearResolvedDispatchError(a);
        require("source_offline".equals(a.dispatchError),"a still-broken endpoint keeps its reason");
        f.src.status="online";

        // --------------------------------- D: handoff never touches the world or source inventory
        Path root=Paths.get("").toAbsolutePath();
        while(root!=null&&!Files.isDirectory(root.resolve("src/main/java")))root=root.getParent();
        if(root==null)throw new AssertionError("cannot locate source tree");
        String logic=Files.readString(root.resolve("src/main/java/com/example/examplemod/economy/FreightLogistics.java"));
        require(logic.contains("static boolean handoff(boolean handedOff,boolean carried,boolean travelling,boolean airborne)"),
                "the handoff rule takes only positive-evidence inputs: no world, no source inventory");
        require(!logic.contains("sourceStillHolds")&&!logic.contains("sourceHolds"),
                "inability to inspect the source Postbox/Packager is never an input to handoff");
        require(!logic.contains("getChunkAt(")&&!logic.contains("loadChunk"),
                "freight logic never force-loads a chunk");

        // ------------------------------------ identity, token reuse and no duplicate reward
        require(f.m.shipmentToken(token)!=null&&f.m.shipmentToken(token).state()==EconomyTypes.ShipmentTokenState.ACTIVE,
                "the shipment token is reused, never recreated");
        require(freight.assignments.get(f.c.id())==a,"the assignment object is reused, never recreated");
        CompoundTag persisted=a.save();
        require(!persisted.getBoolean("HandedOff"),"an un-handed-off assignment round-trips un-handed-off");
        a.handedOff=true;
        require(FreightAssignment.load(a.save()).handedOff,"the handoff survives a restart");
        require(f.c.deliveredAmount()==0,"no reward before intake");
        require(f.receive(token,64)==EconomyTypes.IntakeAttributionError.NONE,"delivery still works after the handoff");
        require(f.c.deliveredAmount()==64,"exactly one shipment is credited");
        require(f.receive(token,64)==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,
                "the same package can never pay twice");

        System.out.println("PASS: "+checks+" freight handoff, source gating and stale-error checks");
    }

    private static void bootstrapConfig()throws Exception{
        Field configField=com.example.examplemod.Config.class.getDeclaredField("SPEC");
        configField.setAccessible(true);
        var spec=(net.neoforged.neoforge.common.ModConfigSpec)configField.get(null);
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(config);
        config.set("items",List.of());
        var loadedType=Class.forName("net.neoforged.fml.config.LoadedConfig");
        var constructor=loadedType.getDeclaredConstructors()[0];constructor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)constructor.newInstance(config,null,null));
    }
}
