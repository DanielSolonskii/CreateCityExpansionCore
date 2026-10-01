package com.example.examplemod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Headless reconciliation contract for Freight Points and cancel/revoke.
 *
 * <p>Covers the remove/restore, rebind, pause/resume and revoke transitions against the same pure
 * classifier and the same EconomyManager entry points the server uses, so no client and no loaded
 * world is needed.
 */
public final class FreightReconciliationCheck {
    private static int checks;
    private static final ResourceLocation ITEM=ResourceLocation.parse("minecraft:iron_ingot");
    private static final ResourceLocation OVERWORLD=ResourceLocation.parse("minecraft:overworld");

    private static final class Physical implements EconomyManager.PhysicalTransaction {
        boolean fits=true,commit=true;
        public boolean prepare(){return fits;}public boolean commit(){return commit;}public void rollback(){}
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
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}

    private static FreightPointRules.Observation observation(FreightPointRules.Probe postbox,
                                                             FreightPointRules.Probe packager,
                                                             boolean stationPresent,boolean stationMatched){
        return new FreightPointRules.Observation(false,true,true,postbox,packager,stationPresent,stationMatched,true,true);
    }

    public static void main(String[] args)throws Exception{
        bootstrapConfig();

        // ---------------------------------------------------------------- postbox remove/restore
        Fixture broken=new Fixture();
        require(FreightPointRules.status(observation(FreightPointRules.Probe.MISSING,FreightPointRules.Probe.PRESENT,true,true)).equals("postbox_missing"),"removed postbox is reported as missing");
        require(FreightPointRules.status(observation(FreightPointRules.Probe.INCOMPATIBLE,FreightPointRules.Probe.PRESENT,true,true)).equals("postbox_incompatible"),"foreign block at the postbox position is reported as incompatible");
        require(FreightPointRules.ui("postbox_missing").category()==com.example.examplemod.status.CcecStatus.Category.ACTION_REQUIRED,
                "missing postbox asks the player to act");
        require(FreightPointRules.ui("postbox_incompatible").action()==com.example.examplemod.status.CcecStatus.Action.REBIND_POINT,
                "incompatible postbox asks for a rebind");
        require(FreightPointRules.status(observation(FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,true,true)).equals("online"),
                "restored postbox returns to online");

        // ------------------------------------------------- export pauses while the source is broken
        FreightAssignment assignment=broken.m.freight().assignments.get(broken.c.id());
        require(assignment.running,"dispatch starts running");
        broken.src.status="postbox_missing";
        require(!broken.src.online(),"broken point is offline");
        require(broken.m.authorizeExportPackage(broken.manifest.manifestId(),ITEM,64,2,id->{})
                ==EconomyTypes.IntakeAttributionError.INVALID_MANIFEST,"export is gated while the source point is broken");
        require(assignment.running,"the player's dispatch intent survives the outage");
        require(broken.m.freight().assignments.get(broken.c.id())==assignment,"the assignment object is reused, never recreated");
        broken.src.status="online";
        require(broken.m.authorizeExportPackage(broken.manifest.manifestId(),ITEM,64,2,id->{})
                ==EconomyTypes.IntakeAttributionError.NONE,"export resumes once the point is back online");
        require(broken.m.freight().assignments.get(broken.c.id())==assignment&&assignment.running,
                "recovery keeps the same assignment and its running flag");

        // ------------------------------------------------------------- packager remove/restore
        require(FreightPointRules.status(observation(FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.MISSING,true,true)).equals("packager_missing"),"removed packager is reported as missing");
        require(FreightPointRules.status(observation(FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.INCOMPATIBLE,true,true)).equals("packager_incompatible"),"repackager or foreign block is reported as incompatible");
        require(FreightPointRules.ui("packager_incompatible").action()==com.example.examplemod.status.CcecStatus.Action.REBIND_POINT,
                "incompatible packager asks for a rebind");

        // ----------------------------------------------------------------- station loss/changed
        require(FreightPointRules.status(observation(FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,true)).equals("station_missing"),"temporary station loss has its own reason");
        require(FreightPointRules.ui("station_missing").category()==com.example.examplemod.status.CcecStatus.Category.WARNING,
                "temporary station loss is a warning, not a hard block");
        require(FreightPointRules.status(observation(FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,true,false)).equals("station_changed"),"a rebuilt station at another position needs an explicit rebind");
        require(FreightPointRules.ui("station_changed").category()==com.example.examplemod.status.CcecStatus.Category.BLOCKED
                        &&FreightPointRules.ui("station_changed").action()==com.example.examplemod.status.CcecStatus.Action.REBIND_POINT,
                "station mismatch blocks and offers a rebind");
        require(FreightPointRules.status(observation(FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,true,true)).equals("online"),
                "station rebuilt at the registered position heals automatically");

        // --------------------------------------------------------- receiver and address causes
        FreightPointRules.Observation receiverDown=new FreightPointRules.Observation(false,true,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,true,true,false,true);
        require(FreightPointRules.status(receiverDown).equals("receiver_missing"),"blocked intake has its own reason");
        FreightPointRules.Observation foreignAddress=new FreightPointRules.Observation(false,true,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,true,true,true,false);
        require(FreightPointRules.status(foreignAddress).equals("address_changed"),"foreign address has its own reason");

        // --------------------------------------------------------------- address auto-heal rule
        require(FreightPointRules.healAddress("CCEC_x_y",""),"an empty filter on a replaced postbox is healed");
        require(FreightPointRules.healAddress("CCEC_x_y",null),"a missing filter is healed");
        require(!FreightPointRules.healAddress("CCEC_x_y","CCEC_other"),"a deliberately foreign address is left for an explicit rebind");
        require(!FreightPointRules.healAddress("",""),"our own address is never empty, so nothing is invented");

        // ------------------------------------------------------------------ rebind pre-flight
        require(FreightPointRules.pairFailure(true,true,false,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,false,true).isEmpty(),
                "a free compatible pair is accepted");
        require(FreightPointRules.pairFailure(true,true,false,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,true,false,true).equals("duplicate"),
                "a pair owned by another point is refused");
        require(FreightPointRules.pairFailure(true,true,false,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,true,true).equals("limit"),
                "the per-city point limit only applies to new registrations");
        require(FreightPointRules.pairFailure(true,true,false,true,
                FreightPointRules.Probe.INCOMPATIBLE,FreightPointRules.Probe.PRESENT,false,false,true).equals("postbox_incompatible"),
                "rebinding onto a foreign postbox is refused with the precise reason");
        require(FreightPointRules.pairFailure(true,true,false,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.INCOMPATIBLE,false,false,true).equals("packager_incompatible"),
                "rebinding onto an incompatible packager is refused with the precise reason");
        require(FreightPointRules.pairFailure(true,true,true,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,false,true).equals("packager_too_far"),
                "an unreachable packager is no longer reported as simply missing");
        require(FreightPointRules.pairFailure(false,true,false,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,false,true).equals("wrong_city"),
                "infrastructure outside the city is refused");
        require(FreightPointRules.pairFailure(true,true,false,false,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,false,true).equals("unloaded"),
                "unloaded chunks are never force-loaded");
        require(FreightPointRules.pairFailure(true,true,false,true,
                FreightPointRules.Probe.PRESENT,FreightPointRules.Probe.PRESENT,false,false,false).equals("station_missing"),
                "a pair with no station link is refused");

        // ------------------------------------------------------- safe rebind keeps trade identity
        Fixture rebind=new Fixture();
        FreightPoint point=rebind.src;
        UUID identity=point.id;
        String address=point.address;
        UUID token=rebind.token(64);
        FreightAssignment bound=rebind.m.freight().assignments.get(rebind.c.id());
        require(rebind.m.freight().tokenTargets(token,rebind.dst.id),"token knows its destination before the rebind");
        // The physical anchor is the only thing a rebind is allowed to move.
        point.postbox=new BlockPos(10,64,0).immutable();
        point.packager=new BlockPos(12,64,0).immutable();
        point.stationPos=null;point.stationMatched=true;point.status="online";
        require(point.id.equals(identity)&&point.address.equals(address),"rebind keeps the point identity and address");
        require(rebind.m.freight().destination(rebind.c.id()).equals(rebind.dst.id),"assignment destination is untouched");
        require(bound.source.equals(identity),"assignment source is untouched");
        require(rebind.m.freight().tokenTargets(token,rebind.dst.id),"in-flight token destination survives a rebind");
        FreightPoint roundTrip=FreightPoint.load(point.save());
        require(roundTrip.id.equals(identity)&&roundTrip.address.equals(address)
                        &&roundTrip.postbox.equals(point.postbox)&&roundTrip.packager.equals(point.packager),
                "rebound position and identity survive a restart");

        // ------------------------------------------------------ station identity survives restart
        FreightPoint legacy=new FreightPoint(UUID.randomUUID(),UUID.randomUUID(),OVERWORLD,
                new BlockPos(1,64,1),new BlockPos(3,64,1),"Legacy",FreightPoint.Mode.BOTH);
        CompoundTag oldSave=legacy.save();
        oldSave.remove("StationPos");oldSave.remove("StationMatched");
        FreightPoint fromOld=FreightPoint.load(oldSave);
        require(fromOld.stationPos==null,"a legacy save has no registered station position");
        require(fromOld.stationMatched,"a legacy save assumes the station it knows is still the one");
        FreightPoint registered=new FreightPoint(UUID.randomUUID(),UUID.randomUUID(),OVERWORLD,
                new BlockPos(1,64,1),new BlockPos(3,64,1),"Registered",FreightPoint.Mode.BOTH);
        registered.stationPos=new BlockPos(10,70,20).asLong();
        registered.stationMatched=false;
        FreightPoint fromRegistered=FreightPoint.load(registered.save());
        require(fromRegistered.stationPos!=null&&fromRegistered.stationPos.longValue()==registered.stationPos.longValue(),
                "the registered station position survives a restart");
        require(!fromRegistered.stationMatched,"a pending station mismatch survives a restart");

        // --------------------------------------------------------------------- revoke: open case
        Fixture open=new Fixture();
        require(open.m.freight().revocable(open.c.id(),open.buyerCity).equals("ok"),"revoke is offered while the trade can still move back");
        require(open.m.revokeDispatch(open.c.id(),UUID.randomUUID()).equals("freight_revoke_denied"),
                "a city that funds neither end cannot revoke");
        require(open.m.revokeDispatch(open.c.id(),open.buyerCity).equals("freight_revoked"),"revoke succeeds");
        require(open.c.state()==EconomyTypes.ContractState.CANCELLED&&open.c.escrow()==0L,"contract is cancelled and escrow released");
        require(open.manifest.state()==EconomyTypes.ManifestState.INVALID,"shipping manifest is invalidated");
        require(open.m.treasury(open.buyerCity)==18000L,"escrow returns to the funding city exactly once");
        require(open.m.freight().revocable(open.c.id(),open.buyerCity).equals("freight_revoke_already"),
                "the card explains the shipment is already closed");
        require(open.m.revokeDispatch(open.c.id(),open.buyerCity).equals("freight_revoke_already"),
                "a replayed revoke is a no-op");
        require(open.m.treasury(open.buyerCity)==18000L,"no double refund on replay");
        require(open.m.authorizeExportPackage(open.manifest.manifestId(),ITEM,64,4,id->{})
                ==EconomyTypes.IntakeAttributionError.INVALID_MANIFEST,"no new dispatch after a revoke");

        // --------------------------------------------------------------- revoke: partial shipment
        Fixture partial=new Fixture();
        UUID landed=partial.token(64);
        require(partial.receive(landed,64)==EconomyTypes.IntakeAttributionError.NONE,"partial delivery accepted");
        require(partial.c.deliveredAmount()==64L&&partial.c.state()==EconomyTypes.ContractState.IN_DELIVERY,
                "half the cargo is already accepted");
        UUID inFlight=partial.token(64);
        require(partial.m.freight().revocable(partial.c.id(),partial.buyerCity).equals("ok"),"an in-flight shipment is still revocable");
        require(partial.m.revokeDispatch(partial.c.id(),partial.buyerCity).equals("freight_revoked"),"revoke with a partial shipment succeeds");
        require(partial.c.deliveredAmount()==64L,"accepted cargo is never rolled back");
        require(partial.m.treasury(partial.buyerCity)==18000L,"escrow refunded once despite partial delivery");
        require(partial.m.shipmentToken(inFlight).state()==EconomyTypes.ShipmentTokenState.INVALID,"unused token invalidated");
        require(partial.m.shipmentToken(landed).state()==EconomyTypes.ShipmentTokenState.CONSUMED,"consumed token is left alone");
        require(partial.receive(inFlight,64)==EconomyTypes.IntakeAttributionError.INVALID_SHIPMENT,
                "a late package after a revoke is rejected");
        require(partial.receive(landed,64)==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED,
                "a replayed package after a revoke makes no progress");
        require(partial.c.deliveredAmount()==64L,"no settlement happens after a revoke");

        // ----------------------------------------------------- revoke: delivered and disputed stay
        Fixture delivered=new Fixture();
        require(delivered.c.recordDelivery(delivered.sellerCity,ITEM,128,1)==128L,"full delivery recorded");
        require(delivered.c.state()==EconomyTypes.ContractState.DELIVERED,"contract delivered");
        require(delivered.m.revokeDispatch(delivered.c.id(),delivered.buyerCity).equals("freight_revoke_state"),
                "a delivered contract must be confirmed or disputed instead of revoked");
        require(delivered.c.escrow()==18000L&&delivered.m.treasury(delivered.buyerCity)==0L,
                "a refused revoke releases nothing");
        require(delivered.c.dispute(),"dispute allowed after delivery");
        require(delivered.m.freight().revocable(delivered.c.id(),delivered.buyerCity).equals("freight_revoke_state"),
                "a disputed contract explains why it cannot be revoked");
        require(delivered.m.revokeDispatch(delivered.c.id(),delivered.buyerCity).equals("freight_revoke_state"),
                "a disputed contract is not revoked");
        require(delivered.c.escrow()==18000L&&delivered.m.treasury(delivered.buyerCity)==0L,
                "a refused revoke is financially inert");

        // ---------------------------------------------------------- restart while revoked/broken
        Fixture restarted=new Fixture();
        restarted.src.status="station_changed";restarted.src.stationMatched=false;
        restarted.src.stationPos=new BlockPos(10,70,20).asLong();
        FreightLogistics restoredLogistics=new FreightLogistics(new EconomyManager());
        restoredLogistics.load(restarted.m.freight().save());
        FreightPoint restoredPoint=restoredLogistics.point(restarted.src.id);
        require(restoredPoint!=null,"point survives a restart");
        require(restoredPoint.id.equals(restarted.src.id)&&restoredPoint.address.equals(restarted.src.address),
                "point identity and address survive a restart");
        require(restoredPoint.stationPos!=null&&restoredPoint.stationPos.longValue()==restarted.src.stationPos.longValue(),
                "registered station position survives a restart");
        require(!restoredPoint.stationMatched,"pending station mismatch survives a restart");
        require(restoredLogistics.assignments.get(restarted.c.id()).running,"dispatch intent survives a restart");
        require(!restoredPoint.online(),"an unvalidated point is offline after a restart, never optimistically online");

        Fixture revoked=new Fixture();
        require(revoked.m.revokeDispatch(revoked.c.id(),revoked.buyerCity).equals("freight_revoked"),"revoke before restart");
        CompoundTag economySave=revoked.m.save(new CompoundTag(),null);
        EconomyManager reloadedManager=loadManager(economySave);
        require(reloadedManager.contract(revoked.c.id()).state()==EconomyTypes.ContractState.CANCELLED,
                "revoked contract survives a restart");
        require(reloadedManager.contract(revoked.c.id()).escrow()==0L,"escrow stays released after a restart");
        require(reloadedManager.treasury(revoked.buyerCity)==18000L,"the refund survives a restart");
        require(reloadedManager.revokeDispatch(revoked.c.id(),revoked.buyerCity).equals("freight_revoke_already"),
                "replaying a revoke after a restart is still a no-op");
        require(reloadedManager.treasury(revoked.buyerCity)==18000L,"no double refund after a restart");

        // ------------------------------------------------------- state push to open screens/cards
        EconomyManager quiet=new EconomyManager();
        require(!quiet.freight().pushPending(),"a fresh logistics has nothing to push");
        quiet.freight().markChanged();
        require(quiet.freight().pushPending(),"a state transition requests a push to open menus");
        Fixture pushed=new Fixture();
        Field pending=FreightLogistics.class.getDeclaredField("pendingPush");
        pending.setAccessible(true);
        pending.setBoolean(pushed.m.freight(),false);
        UUID pushedToken=pushed.token(64);
        require(pushed.m.freight().pushPending(),"a new shipment token requests a push");
        pending.setBoolean(pushed.m.freight(),false);
        require(pushed.receive(pushedToken,64)==EconomyTypes.IntakeAttributionError.NONE,"package accepted");
        require(pushed.m.freight().pushPending(),"a package acceptance requests a push");
        pending.setBoolean(pushed.m.freight(),false);
        require(pushed.m.revokeDispatch(pushed.c.id(),pushed.buyerCity).equals("freight_revoked"),"revoke accepted");
        require(pushed.m.freight().pushPending(),"a revoke requests a push");

        System.out.println("PASS: "+checks+" freight reconciliation, rebind and revoke checks");
    }

    @SuppressWarnings("unchecked")
    private static EconomyManager loadManager(CompoundTag save)throws Exception{
        java.lang.reflect.Method load=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        return(EconomyManager)load.invoke(null,save,null);
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
