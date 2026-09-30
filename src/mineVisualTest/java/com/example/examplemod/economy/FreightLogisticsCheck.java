package com.example.examplemod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Exercises production validation, ledger and settlement without a client. */
public final class FreightLogisticsCheck {
    private static int checks;
    private static final ResourceLocation ITEM=ResourceLocation.parse("minecraft:iron_ingot");
    private static final class Physical implements EconomyManager.PhysicalTransaction {
        int prepared,committed,rolledBack;boolean fits=true,commit=true;
        public boolean prepare(){prepared++;return fits;}public boolean commit(){committed++;return commit;}public void rollback(){rolledBack++;}
    }
    private static final class Fixture {
        final EconomyManager m=new EconomyManager();final UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID(),player=UUID.randomUUID();
        final FreightPoint src=new FreightPoint(UUID.randomUUID(),seller,ResourceLocation.parse("minecraft:overworld"),new BlockPos(0,64,0),new BlockPos(2,64,0),"Source",FreightPoint.Mode.BOTH);
        final FreightPoint dst=new FreightPoint(UUID.randomUUID(),buyer,src.dimension,new BlockPos(100,64,0),new BlockPos(102,64,0),"Destination",FreightPoint.Mode.BOTH);
        final IndustrialContract c=new IndustrialContract(UUID.randomUUID(),EconomyTypes.ContractKind.ORDINARY,EconomyTypes.ContractMode.EXCLUSIVE,buyer,player,"","",ITEM,128,18000,dst.id,null,0,12000);
        final ShippingManifest manifest=new ShippingManifest(UUID.randomUUID(),c.id(),seller,128,12000);
        Fixture()throws Exception {src.status="online";dst.status="online";m.freight().points.put(src.id,src);m.freight().points.put(dst.id,dst);map(m,"contracts").put(c.id(),c);c.accept(seller,player,1);map(m,"manifests").put(manifest.manifestId(),manifest);m.freight().bind(c.id(),src.id,dst);m.freight().assignments.get(c.id()).running=true;}
        UUID token(long amount){UUID[] id={null};require(m.authorizeExportPackage(manifest.manifestId(),ITEM,amount,2,v->id[0]=v)==EconomyTypes.IntakeAttributionError.NONE,"authorize export");return id[0];}
        EconomyTypes.IntakeAttributionError receive(UUID id,long count,boolean simulate,Physical p){return m.acceptShipmentPackage(dst.id,id,ITEM,count,3,simulate,p);}
    }
    @SuppressWarnings("unchecked") private static <T> Map<UUID,T> map(EconomyManager m,String name)throws Exception {var f=EconomyManager.class.getDeclaredField(name);f.setAccessible(true);return(Map<UUID,T>)f.get(m);}
    @SuppressWarnings("unchecked") private static Map<ResourceLocation,Long> stock(EconomyManager m)throws Exception {var f=EconomyManager.class.getDeclaredField("warehouseStock");f.setAccessible(true);return(Map<ResourceLocation,Long>)f.get(m);}
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var configField=com.example.examplemod.Config.class.getDeclaredField("SPEC");configField.setAccessible(true);
        var spec=(net.neoforged.neoforge.common.ModConfigSpec)configField.get(null);
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();spec.correct(config);
        // The example item-list setting is unrelated to economics and needs a bootstrapped item registry.
        config.set("items",List.of());
        var loadedType=Class.forName("net.neoforged.fml.config.LoadedConfig");
        var constructor=loadedType.getDeclaredConstructors()[0];constructor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)constructor.newInstance(config,null,null));
        Fixture f=new Fixture();MarketOffer masterPurchase=new MarketOffer(UUID.randomUUID(),ITEM,ITEM,EconomyTypes.MarketDirection.SELL_TO_MASTER,128,10,0,1000,0);
        require(masterPurchase.reserve(f.seller,f.player,128,0),"Master purchase reserved");map(f.m,"marketOffers").put(masterPurchase.id(),masterPurchase);map(f.m,"offerByContract").put(f.c.id(),masterPurchase.id());
        UUID a=f.token(64);Physical p=new Physical();f.m.setDirty(false);
        require(f.receive(a,64,true,p)==EconomyTypes.IntakeAttributionError.NONE,"simulate accepted");require(p.committed==0&&f.c.deliveredAmount()==0&&f.manifest.acceptedAmount()==0&&!f.m.isDirty(),"simulate has no mutation");require(f.m.shipmentToken(a).state()==EconomyTypes.ShipmentTokenState.ACTIVE,"simulate preserves token");
        Physical full=new Physical();full.fits=false;require(f.receive(a,64,false,full)==EconomyTypes.IntakeAttributionError.EXCESS_CARGO&&full.committed==0,"full inventory rejects before commit");
        Physical failed=new Physical();failed.commit=false;require(f.receive(a,64,false,failed)==EconomyTypes.IntakeAttributionError.EXCESS_CARGO&&failed.rolledBack==1&&f.c.deliveredAmount()==0,"failed physical commit rolls back");
        require(f.receive(a,65,false,new Physical())==EconomyTypes.IntakeAttributionError.EXCESS_CARGO,"excess is all-or-nothing");
        require(f.m.acceptShipmentPackage(f.src.id,a,ITEM,64,3,false,new Physical())==EconomyTypes.IntakeAttributionError.WRONG_INTAKE,"wrong point rejected");
        require(f.m.acceptShipmentPackage(f.dst.id,a,ResourceLocation.parse("minecraft:gold_ingot"),64,3,false,new Physical())==EconomyTypes.IntakeAttributionError.WRONG_ITEM,"wrong cargo rejected");
        f.dst.enabled=false;require(f.receive(a,64,false,new Physical())==EconomyTypes.IntakeAttributionError.WRONG_INTAKE,"disabled destination rejects");require(f.m.shipmentToken(a).state()==EconomyTypes.ShipmentTokenState.ACTIVE,"disabled destination preserves token");f.dst.enabled=true;
        f.src.enabled=false;f.m.freight().assignments.get(f.c.id()).running=false;require(f.receive(a,64,false,new Physical())==EconomyTypes.IntakeAttributionError.NONE,"source failure does not block delivery");
        require(f.receive(a,64,false,new Physical())==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED&&f.c.deliveredAmount()==64,"replay gives no progress");require(f.m.warehouseStock(ITEM)==64,"physical Master receipt credits warehouse once");require(f.m.freight().busy(f.src.id,UUID.randomUUID()),"one active assignment per source");
        CompoundTag saved=f.m.freight().save();EconomyManager restored=new EconomyManager();restored.freight().load(saved);
        require(!restored.freight().assignments.get(f.c.id()).running,"paused state survives restart");require(restored.freight().tokenTargets(a,f.dst.id),"token destination survives restart");require(restored.freight().point(f.src.id).address.equals(f.src.address),"address stable on reload");
        f.src.enabled=true;f.m.freight().assignments.get(f.c.id()).running=true;UUID b=f.token(64);require(f.receive(b,64,false,new Physical())==EconomyTypes.IntakeAttributionError.NONE,"final delivery accepted");require(f.c.state()==EconomyTypes.ContractState.COMPLETED&&f.c.escrow()==0&&f.m.treasury(f.seller)==18000,"physical proof settles escrow");require(f.m.warehouseStock(ITEM)==128&&masterPurchase.status()==EconomyTypes.MarketOfferStatus.COMPLETED,"completed Master purchase becomes sellable warehouse stock");
        require(f.receive(b,64,false,new Physical())==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED&&f.m.treasury(f.seller)==18000&&f.m.warehouseStock(ITEM)==128,"settlement and stock credit happen once");require(!f.m.freight().busy(f.src.id,null),"completed assignment releases source");
        require(f.m.authorizeExportPackage(f.manifest.manifestId(),ITEM,1,4,id->{throw new AssertionError("marker after completion");})!=EconomyTypes.IntakeAttributionError.NONE,"completed contract cannot dispatch extra box");
        Fixture race=new Fixture();race.token(128);require(race.m.authorizeExportPackage(race.manifest.manifestId(),ITEM,1,2,id->{})==EconomyTypes.IntakeAttributionError.EXCESS_CARGO,"in-flight reservation prevents overdispatch");
        FreightPoint another=new FreightPoint(UUID.randomUUID(),f.seller,f.src.dimension,f.src.postbox,f.src.packager,f.src.name,FreightPoint.Mode.BOTH);require(!another.address.equals(f.src.address),"display names do not collide");
        CompoundTag economy=f.m.save(new CompoundTag(),null);require(economy.getInt("FormatVersion")==6&&economy.getList("ShipmentTokens",10).size()==2&&economy.getList("Manifests",10).size()==1,"save retains ledgers");
        MarketOffer o=new MarketOffer(UUID.randomUUID(),ITEM,ITEM,EconomyTypes.MarketDirection.BUY_FROM_MASTER,64,10,0,1000,1);require(o.reserve(f.buyer,f.player,64,1)&&o.markDispatched(64),"physical import reserved");require(o.receiveImport(64)&&!o.receiveImport(64),"import completion rejects second receipt");
        EconomyManager inventory=new EconomyManager();stock(inventory).put(ITEM,100L);MarketOffer reservedImport=new MarketOffer(UUID.randomUUID(),ITEM,ITEM,EconomyTypes.MarketDirection.BUY_FROM_MASTER,60,10,0,1000,0);require(reservedImport.reserve(f.buyer,f.player,60,0),"warehouse import reserved");map(inventory,"marketOffers").put(reservedImport.id(),reservedImport);require(inventory.availableWarehouseStock(ITEM)==40,"accepted imports reserve warehouse stock before packaging");
        var load=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);load.setAccessible(true);
        EconomyManager reloaded=(EconomyManager)load.invoke(null,economy,null);
        require(reloaded.treasury(f.seller)==18000&&reloaded.contract(f.c.id()).escrow()==0&&reloaded.shipmentToken(a).state()==EconomyTypes.ShipmentTokenState.CONSUMED,"v6 complete ledger roundtrip");
        CompoundTag legacy=race.m.save(new CompoundTag(),null);legacy.putInt("FormatVersion",5);legacy.remove("FreightLogistics");EconomyManager migrated=(EconomyManager)load.invoke(null,legacy,null);
        require(migrated.contract(race.c.id()).escrow()==18000&&migrated.shipmentTokens().size()==1&&migrated.manifests().size()==1&&migrated.freight().assignments.isEmpty(),"v5 migration preserves in-flight escrow and ledgers without guessing point");
        UUID oldToken=race.m.shipmentTokens().getFirst().tokenId();
        require(race.m.acceptShipmentPackage(race.dst.id,oldToken,ITEM,128,12001,false,new Physical())==EconomyTypes.IntakeAttributionError.CONTRACT_EXPIRED,"expired delivery rejected");
        Fixture imported=new Fixture();MarketOffer order=new MarketOffer(UUID.randomUUID(),ITEM,ITEM,EconomyTypes.MarketDirection.BUY_FROM_MASTER,64,10,0,1000,7);
        order.reserve(imported.buyer,imported.player,64,7);order.markDispatched(64);map(imported.m,"marketOffers").put(order.id(),order);
        imported.m.freight().bind(order.id(),imported.src.id,imported.dst);
        ShipmentToken importToken=new ShipmentToken(UUID.randomUUID(),null,order.id(),EconomyTypes.ShipmentTokenKind.IMPORT_ORDER,imported.seller,imported.buyer,ITEM,64,1,1000);
        map(imported.m,"shipmentTokens").put(importToken.tokenId(),importToken);imported.m.freight().tokenCreated(importToken.tokenId(),order.id());
        require(imported.receive(importToken.tokenId(),64,true,new Physical())==EconomyTypes.IntakeAttributionError.NONE&&order.remaining()==64,"import simulate does not complete order");
        require(imported.receive(importToken.tokenId(),64,false,new Physical())==EconomyTypes.IntakeAttributionError.NONE&&order.status()==EconomyTypes.MarketOfferStatus.COMPLETED&&imported.m.treasury(imported.buyer)==7,"physical import completes and returns bond");
        require(imported.receive(importToken.tokenId(),64,false,new Physical())==EconomyTypes.IntakeAttributionError.SHIPMENT_ALREADY_USED&&imported.m.treasury(imported.buyer)==7,"import bond cannot be paid twice");
        System.out.println("PASS: "+checks+" freight ledger, atomicity, replay, settlement and persistence checks");
    }
}
