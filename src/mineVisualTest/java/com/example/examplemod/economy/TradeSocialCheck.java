package com.example.examplemod.economy;
import com.example.examplemod.projects.*;
import com.example.examplemod.social.*;
import com.example.examplemod.client.screen.InventoryBankLayout;
import com.example.examplemod.network.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.*;
import java.util.*;

/** Actual accounting, serialization and privacy policies. Does not claim game/render validation. */
public final class TradeSocialCheck {
    private static int checks;
    private static final UUID BUYER=UUID.randomUUID(),SELLER=UUID.randomUUID(),AUTHOR=UUID.randomUUID();
    private static final ResourceLocation WORLD=ResourceLocation.parse("minecraft:overworld"),IRON=ResourceLocation.parse("minecraft:iron_ingot");
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static <T> Map<UUID,T> map(EconomyManager m,String name)throws Exception{var f=EconomyManager.class.getDeclaredField(name);f.setAccessible(true);return (Map<UUID,T>)f.get(m);}
    private static IndustrialContract contract(EconomyManager m,long delivered,EconomyTypes.ContractMode mode)throws Exception{
        var c=new IndustrialContract(UUID.randomUUID(),EconomyTypes.ContractKind.ORDINARY,mode,BUYER,AUTHOR,"Iron order","",IRON,100,1001,UUID.randomUUID(),null,1,100);
        c.accept(SELLER,AUTHOR,1);if(delivered>0)c.recordDelivery(SELLER,IRON,delivered,2);TradeSocialCheck.<IndustrialContract>map(m,"contracts").put(c.id(),c);return c;
    }
    public static void main(String[] args)throws Exception{
        bootstrap();var m=new EconomyManager();var c=contract(m,40,EconomyTypes.ContractMode.EXCLUSIVE);
        require(EconomyManager.partialContractPayouts(c).get(SELLER)==400,"proportional accepted quantity uses floor, not floating money");
        require(m.revokeDispatch(c.id(),BUYER).equals("freight_revoked"),"buyer closes actual partial contract");
        require(m.treasury(SELLER)==400&&m.treasury(BUYER)==601&&c.escrow()==0,"accepted goods paid, unused escrow refunded, sum conserved");
        require(m.closedContractPayout(c.id())==400,"closeout amount recorded");
        require(m.revokeDispatch(c.id(),BUYER).equals("freight_revoke_already")&&m.treasury(SELLER)==400,"repeat cannot pay twice");
        var loaded=reload(m.save(new CompoundTag(),null));
        require(loaded.closedContractPayout(c.id())==400&&loaded.treasury(BUYER)==601,"receipt and balances survive reload");
        require(loaded.revokeDispatch(c.id(),BUYER).equals("freight_revoke_already"),"replay after restart rejected");
        var expiry=new EconomyManager();var timed=contract(expiry,40,EconomyTypes.ContractMode.EXCLUSIVE);
        var process=EconomyManager.class.getDeclaredMethod("processTimeouts",long.class);process.setAccessible(true);process.invoke(expiry,102L);process.invoke(expiry,103L);
        require(timed.state()==EconomyTypes.ContractState.FAILED&&expiry.treasury(SELLER)==400&&expiry.treasury(BUYER)==601,"timeout shares the same one-time settlement");
        var blocked=new EconomyManager();var overflow=contract(blocked,40,EconomyTypes.ContractMode.EXCLUSIVE);TradeSocialCheck.<Long>map(blocked,"treasuries").put(SELLER,Long.MAX_VALUE-399);
        require(!blocked.revokeDispatch(overflow.id(),BUYER).equals("freight_revoked")&&overflow.escrow()==1001&&!overflow.state().terminal(),"capacity checked before closing or removing escrow");
        var community=new EconomyManager();var cc=contract(community,33,EconomyTypes.ContractMode.COMMUNITY);UUID second=UUID.randomUUID();cc.accept(second,AUTHOR,1);cc.recordDelivery(second,IRON,17,2);
        require(community.revokeDispatch(cc.id(),BUYER).equals("freight_revoked"),"community closeout supported");
        require(community.treasury(BUYER)==501&&community.treasury(SELLER)+community.treasury(second)==500,"multiple contributors and rounding conserve escrow");
        var empty=new EconomyManager();var zero=contract(empty,0,EconomyTypes.ContractMode.EXCLUSIVE);empty.revokeDispatch(zero.id(),BUYER);require(empty.treasury(BUYER)==1001&&empty.treasury(SELLER)==0,"no physical receipt means no supplier payment");
        var link=new EconomyManager();var order=contract(link,0,EconomyTypes.ContractMode.EXCLUSIVE);
        var actor=new ProjectManager.Actor(AUTHOR,BUYER,true,id->true,z->true);
        var draft=new ProjectManager.Draft(Project.Scope.CITY,"Station","",Project.Category.BUILDING,new ProjectZone(WORLD,ProjectZone.Shape.RECTANGLE,0,0,10,10),List.of(new Project.Stage("Build",false)),Project.Funding.FREE,0);
        require(link.projects().execute(actor,new ProjectManager.Command(UUID.randomUUID(),null,0,ProjectManager.Action.CREATE,null,0,false,"",draft)).equals("ok"),"create real project fixture");
        var project=link.projects().all().iterator().next();link.linkProject(order.id(),project.id());var saved=link.save(new CompoundTag(),null);
        require(reload(saved).contractProjects().get(order.id()).equals(project.id()),"optional project/order link survives reload");
        saved.putInt("FormatVersion",10);saved.remove("ContractProjects");saved.remove("ContractClosures");var legacy=reload(saved);
        require(legacy.contractProjects().isEmpty()&&legacy.contract(order.id()).escrow()==1001,"v10 migration keeps old contracts without invented links");
        require(!EconomyNetworkHandlers.remoteAllowed(EconomyCommandPayload.Action.REGISTER_FREIGHT)&&!EconomyNetworkHandlers.remoteAllowed(EconomyCommandPayload.Action.BIND_INFRASTRUCTURE),"remote market cannot commission physical blocks");
        require(EconomyNetworkHandlers.remoteAllowed(EconomyCommandPayload.Action.ACCEPT_CONTRACT),"remote market supports existing validated trade action");
        var social=new SocialSavedData();social.migrateNearby(AUTHOR,false);require(social.sharesNearby(AUTHOR),"default enabled");social.shareNearby(AUTHOR,false);social.migrateNearby(AUTHOR,true);
        require(!SocialSavedData.load(social.save(new CompoundTag(),null),null).sharesNearby(AUTHOR),"explicit opt out survives repeat migration/restart");
        var old=new SocialSavedData();old.migrateNearby(AUTHOR,true);require(!old.sharesNearby(AUTHOR),"old explicit off preference remains off");
        require(NearbyScenePolicy.friend(true,true,true,false,900000000000000d)&&!NearbyScenePolicy.friend(false,true,true,false,0),"global friend position policy still excludes strangers");
        for(int width:new int[]{320,427,640,960})for(boolean recipes:new boolean[]{false,true}){
            int left=(width-176)/2,top=40;var bank=InventoryBankLayout.calculate(width,240,left,top,176,166,recipes);var market=InventoryBankLayout.market(width,240,left,top,176,166,recipes);
            if(market!=null){require(market.x()>=0&&market.x()+market.width()<=width&&market.y()+market.height()<=240,"inventory market stays on screen");require(bank==null||market.x()>=bank.x()+bank.width()||market.y()>=bank.y()+bank.height(),"market and balance do not overlap");}
        }
        System.out.println("PASS: "+checks+" trade settlement, migration, access and inventory checks");
    }
    private static EconomyManager reload(CompoundTag t)throws Exception{var method=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);method.setAccessible(true);return (EconomyManager)method.invoke(null,t,null);}
    private static void bootstrap()throws Exception{
        var f=com.example.examplemod.Config.class.getDeclaredField("SPEC");f.setAccessible(true);var spec=(net.neoforged.neoforge.common.ModConfigSpec)f.get(null);
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();spec.correct(config);config.set("items",List.of());
        var type=Class.forName("net.neoforged.fml.config.LoadedConfig");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)ctor.newInstance(config,null,null));
    }
}
