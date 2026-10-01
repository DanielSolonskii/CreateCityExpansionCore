package com.example.examplemod.economy;

import com.example.examplemod.railway.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Headless regression for destination intake and settlement: what must happen the moment a shipment
 * package is physically accepted at the destination.
 *
 * <p>The runtime defect this covers was not in the ledger. {@code FreightLogistics.tick} drove the
 * sending side every tick but had no arrival pass, and the only other caller of
 * {@link EconomyManager#acceptShipmentPackage} is Create's unpacking handler — which fires when
 * something unpacks a package, not when one merely rests in a Postbox. So a trip that had physically
 * completed left the token ACTIVE, {@code deliveredAmount} at zero, and the dispatch reporting
 * IN_TRANSIT indefinitely.
 *
 * <p>These cases pin the settlement contract that the arrival pass now relies on: one package leads
 * to exactly one consumption, one increment, one settlement, and a replay of the very same package
 * pays nothing. The world-dependent wrapper (reading the Postbox and moving the box) cannot run
 * headless, because this harness cannot construct an {@code ItemStack}; the ledger rules below are
 * the part that decides settlement and are executed for real.
 */
public final class FreightArrivalSettlementCheck {
    private static int checks;
    private static final ResourceLocation ITEM=ResourceLocation.parse("minecraft:diamond");
    private static final ResourceLocation OVERWORLD=ResourceLocation.parse("minecraft:overworld");

    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}

    /** Counts what the physical transaction was actually asked to do. */
    private static final class Physical implements EconomyManager.PhysicalTransaction {
        int prepared,committed,rolledBack,inserted;
        boolean fits=true,commitWorks=true;
        public boolean prepare(){prepared++;return fits;}
        public boolean commit(){committed++;if(!commitWorks)return false;inserted++;return true;}
        public void rollback(){rolledBack++;}
    }

    private static final class Fixture {
        final EconomyManager m=new EconomyManager();
        final UUID sellerCity=UUID.randomUUID(),buyerCity=UUID.randomUUID(),player=UUID.randomUUID();
        final FreightPoint src=new FreightPoint(UUID.randomUUID(),sellerCity,OVERWORLD,new BlockPos(0,64,0),new BlockPos(2,64,0),"Source",FreightPoint.Mode.BOTH);
        final FreightPoint dst=new FreightPoint(UUID.randomUUID(),buyerCity,OVERWORLD,new BlockPos(100,64,0),new BlockPos(102,64,0),"Destination",FreightPoint.Mode.BOTH);
        final IndustrialContract c;
        final ShippingManifest manifest;
        Fixture(long amount)throws Exception{
            src.status="online";dst.status="online";
            m.freight().points.put(src.id,src);m.freight().points.put(dst.id,dst);
            c=new IndustrialContract(UUID.randomUUID(),EconomyTypes.ContractKind.ORDINARY,EconomyTypes.ContractMode.EXCLUSIVE,
                    buyerCity,player,"","",ITEM,amount,18000,dst.id,null,0,12000);
            map(m,"contracts").put(c.id(),c);
            c.accept(sellerCity,player,1);
            manifest=new ShippingManifest(UUID.randomUUID(),c.id(),sellerCity,amount,12000);
            map(m,"manifests").put(manifest.manifestId(),manifest);
            m.freight().bind(c.id(),src.id,dst);
            m.freight().assignments.get(c.id()).running=true;
        }
        UUID dispatch(long amount){
            UUID[] id={null};
            require(m.authorizeExportPackage(manifest.manifestId(),ITEM,amount,2,v->id[0]=v)==EconomyTypes.IntakeAttributionError.NONE,
                    "dispatch authorised for "+amount);
            require(m.freight().tokenTargets(id[0],dst.id),"the token is bound to this destination");
            return id[0];
        }
        EconomyTypes.IntakeAttributionError accept(UUID token,long amount,EconomyManager.PhysicalTransaction physical){
            return m.acceptShipmentPackage(dst.id,token,ITEM,amount,3,false,physical);
        }
    }

    public static void main(String[] args)throws Exception{
        bootstrapConfig();

        // ======================== full delivery: one package settles the whole contract exactly once
        Fixture full=new Fixture(64L);
        FreightLogistics freight=full.m.freight();
        FreightAssignment assignment=freight.assignments.get(full.c.id());
        RailwayManager railway=new RailwayManager();
        UUID createId=UUID.randomUUID();
        CityFleetTrain fleet=railway.registerTrain(createId,UUID.randomUUID(),"Hopper");
        assignment.train=fleet.fleetTrainId();
        long escrow=full.c.escrow();
        long sellerBefore=full.m.treasury(full.sellerCity);

        UUID token=full.dispatch(64L);
        require(assignment.handedOff==false||!assignment.handedOff,"a freshly stamped package is not handed off yet");
        require(inFlight(full.m,full.c.id())==64L,"the package counts as in flight before arrival");

        Physical tx=new Physical();
        require(full.accept(token,64L,tx)==EconomyTypes.IntakeAttributionError.NONE,"the arrival is accepted");
        require(tx.prepared==1&&tx.committed==1&&tx.rolledBack==0&&tx.inserted==1,
                "the physical move happened exactly once, with no rollback");

        ShipmentToken settled=full.m.shipmentToken(token);
        require(settled.state()==EconomyTypes.ShipmentTokenState.CONSUMED,"the token is CONSUMED by the arrival");
        require(full.c.deliveredAmount()==64L,"deliveredAmount increased exactly once");
        require(full.c.state()==EconomyTypes.ContractState.COMPLETED,
                "the contract passes through DELIVERED and settles to COMPLETED inside the same arrival call");
        require(full.manifest.acceptedAmount()==64L,"the manifest recorded the accepted amount");
        require(full.manifest.state()==EconomyTypes.ManifestState.COMPLETED,"the manifest closes");
        require(full.m.treasury(full.sellerCity)-sellerBefore==escrow,"the escrow is released to the contractor exactly once");
        require(inFlight(full.m,full.c.id())==0L,"nothing is in flight any more, so IN_TRANSIT can no longer apply");
        require("complete".equals(freight.state(assignment)),"the assignment renders as complete, not in transit");
        require(freight.uiStatus(assignment,"complete",64L,64L,0L,true,railway).code()==com.example.examplemod.status.CcecStatus.Code.DELIVERED,
                "the card reads DELIVERED instead of IN_TRANSIT");
        require(freight.uiStatus(assignment,"complete",64L,64L,0L,true,railway).code()
                        !=com.example.examplemod.status.CcecStatus.Code.IN_TRANSIT,
                "IN_TRANSIT is gone once the cargo is accepted");

        // ============================== replay: the very same package cannot pay a second time
        Physical replay=new Physical();
        require(full.accept(token,64L,replay)==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,
                "replaying the same package is refused by the ledger");
        require(replay.committed==0,"a refused replay never touches the world");
        require(full.c.deliveredAmount()==64L,"a refused replay does not move deliveredAmount");
        require(full.m.treasury(full.sellerCity)-sellerBefore==escrow,"a refused replay pays nothing");
        for(int i=0;i<3;i++)require(full.accept(token,64L,new Physical())==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,
                "repeated destination observation keeps refusing the same package");
        require(full.c.deliveredAmount()==64L,"repeated observation cannot double-increment");

        // ================================================== partial delivery really stays partial
        Fixture partial=new Fixture(128L);
        UUID first=partial.dispatch(64L);
        require(partial.accept(first,64L,new Physical())==EconomyTypes.IntakeAttributionError.NONE,"the first half is accepted");
        require(partial.c.deliveredAmount()==64L&&partial.c.amount()==128L,"only the delivered half is recorded");
        require(partial.c.state()!=EconomyTypes.ContractState.DELIVERED&&partial.c.state()!=EconomyTypes.ContractState.COMPLETED,
                "a half-delivered contract is neither DELIVERED nor COMPLETED");
        require(partial.manifest.state()==EconomyTypes.ManifestState.ACTIVE,"the manifest stays open while cargo is outstanding");
        require(!"complete".equals(partial.m.freight().state(partial.m.freight().assignments.get(partial.c.id()))),
                "a half-delivered shipment is not complete");
        long escrowPartial=partial.c.escrow();
        UUID second=partial.dispatch(64L);
        require(partial.accept(second,64L,new Physical())==EconomyTypes.IntakeAttributionError.NONE,"the second half is accepted");
        require(partial.c.deliveredAmount()==128L,"the contract is fully delivered");
        require(partial.c.state()==EconomyTypes.ContractState.COMPLETED,"the contract settles once the last package lands");
        require(partial.m.treasury(partial.sellerCity)==escrowPartial,"a two-package contract settles exactly once, in full");
        require(partial.accept(first,64L,new Physical())==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,
                "the first package stays spent after the contract completed");

        // =========================== a full receiver must refuse without any partial application
        Fixture blocked=new Fixture(64L);
        UUID blockedToken=blocked.dispatch(64L);
        Physical fullReceiver=new Physical();fullReceiver.fits=false;
        require(blocked.accept(blockedToken,64L,fullReceiver)==EconomyTypes.IntakeAttributionError.EXCESS_CARGO,
                "a receiver that cannot take the goods refuses the arrival");
        require(fullReceiver.committed==0&&fullReceiver.rolledBack==0&&fullReceiver.inserted==0,
                "a refusal at prepare() touches nothing, so there is nothing to roll back");
        require(blocked.m.shipmentToken(blockedToken).state()==EconomyTypes.ShipmentTokenState.ACTIVE,
                "a refused arrival leaves the token ACTIVE, so the package is retried rather than lost");
        require(blocked.c.deliveredAmount()==0L,"a refused arrival credits nothing");

        Physical commitFails=new Physical();commitFails.commitWorks=false;
        require(blocked.accept(blockedToken,64L,commitFails)==EconomyTypes.IntakeAttributionError.EXCESS_CARGO,
                "an arrival whose physical move fails mid-way is refused");
        require(commitFails.committed==1&&commitFails.rolledBack==1,
                "a failure at commit() rolls the physical move back");
        require(blocked.m.shipmentToken(blockedToken).state()==EconomyTypes.ShipmentTokenState.ACTIVE,
                "a rolled-back arrival leaves the token ACTIVE and the cargo uncounted");
        require(blocked.c.deliveredAmount()==0L,"a rolled-back arrival credits nothing");

        Physical retry=new Physical();
        require(blocked.accept(blockedToken,64L,retry)==EconomyTypes.IntakeAttributionError.NONE,"the arrival succeeds once the receiver has room");
        require(blocked.m.shipmentToken(blockedToken).state()==EconomyTypes.ShipmentTokenState.CONSUMED,
                "the retried arrival then consumes the token exactly once");

        // ================================ a full trip ledger is no longer a reason to stay in transit
        RailwayManager tripRailway=new RailwayManager();
        UUID tripCreate=UUID.randomUUID();
        CityFleetTrain tripFleet=tripRailway.registerTrain(tripCreate,UUID.randomUUID(),"Hopper 2");
        FreightAssignment live=freight.assignments.get(full.c.id());
        live.train=tripFleet.fleetTrainId();
        RailwayTripLedger ledger=new RailwayTripLedger(UUID.randomUUID(),tripCreate,tripFleet.fleetTrainId(),
                RailwayOwnershipTypes.OwnerType.CITY,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),100,50,42,List.of());
        tripRailway.addTrip(ledger);
        require(freight.uiStatus(live,"complete",64L,64L,0L,true,tripRailway).code()
                        !=com.example.examplemod.status.CcecStatus.Code.IN_TRANSIT,
                "a completed ledger no longer pins a settled shipment to IN_TRANSIT");

        System.out.println("PASS: "+checks+" freight arrival settlement, replay and partial-delivery checks");
    }

    private static long inFlight(EconomyManager m,UUID subject){
        return m.shipmentTokens().stream().filter(t->t.subjectId().equals(subject)
                &&t.state()==EconomyTypes.ShipmentTokenState.ACTIVE).mapToLong(ShipmentToken::amount).sum();
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID,Object> map(EconomyManager m,String name)throws Exception{
        Field f=EconomyManager.class.getDeclaredField(name);f.setAccessible(true);return(java.util.Map<UUID,Object>)f.get(m);
    }

    private static void bootstrapConfig()throws Exception{
        Field configField=com.example.examplemod.Config.class.getDeclaredField("SPEC");
        configField.setAccessible(true);
        var spec=(net.neoforged.neoforge.common.ModConfigSpec)configField.get(null);
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(config);
        config.set("items",List.of());
        var loadedType=Class.forName("net.neoforged.fml.config.LoadedConfig");
        var constructor=loadedType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)constructor.newInstance(config,null,null));
    }
}