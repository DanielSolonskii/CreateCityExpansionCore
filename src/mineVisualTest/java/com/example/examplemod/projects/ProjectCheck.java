package com.example.examplemod.projects;

import com.example.examplemod.city.*;
import com.example.examplemod.economy.EconomyManager;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.example.examplemod.projects.Project.*;
import static com.example.examplemod.projects.ProjectManager.*;

/** Exercises production money/persistence/lifecycle code, with no Minecraft client. */
public final class ProjectCheck {
    private static int checks;
    private static final ResourceLocation DIM=ResourceLocation.parse("minecraft:overworld");
    private static final UUID ZERO=new UUID(0,0);
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void config()throws Exception {
        var f=com.example.examplemod.Config.class.getDeclaredField("SPEC");f.setAccessible(true);
        var spec=(net.neoforged.neoforge.common.ModConfigSpec)f.get(null);
        var data=com.electronwill.nightconfig.core.CommentedConfig.inMemory();spec.correct(data);data.set("items",List.of());
        var ctor=Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];ctor.setAccessible(true);
        spec.acceptConfig((net.neoforged.fml.config.IConfigSpec.ILoadedConfig)ctor.newInstance(data,null,null));
    }
    private static EconomyManager reload(EconomyManager m)throws Exception {
        var load=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);load.setAccessible(true);
        return(EconomyManager)load.invoke(null,m.save(new CompoundTag(),null),null);
    }
    private static final class Fixture {
        EconomyManager economy=new EconomyManager();
        final UUID author=UUID.randomUUID(),admin=UUID.randomUUID(),worker=UUID.randomUUID(),other=UUID.randomUUID(),city=UUID.randomUUID();
        final Set<UUID> members=new HashSet<>(List.of(author,admin,worker,other));
        final Set<CityPlot> claims=new HashSet<>(List.of(new CityPlot(DIM,0,0)));
        Fixture(){economy.adminSetWallet(author,1000);economy.adminSetTreasury(city,2000);}
        ProjectManager manager(){return economy.projects();}
        Actor actor(UUID player){return new Actor(player,city,player.equals(admin),members::contains,z->z.plots().stream().allMatch(claims::contains));}
        Draft draft(Funding f,long amount){return new Draft(Scope.CITY,"Town hall","Build a town hall",Category.BUILDING,new ProjectZone(DIM,ProjectZone.Shape.RECTANGLE,2,2,20,20),List.of(new Stage("Foundations",false),new Stage("Roof",false)),f,amount);}
        Command command(Action action,UUID id,UUID target,int stage,boolean done,String note,Draft draft){Project p=manager().get(id);return new Command(UUID.randomUUID(),id,p==null?0:p.revision(),action,target,stage,done,note,draft);}
        String run(UUID who,Action action,UUID id){return manager().execute(actor(who),command(action,id,ZERO,0,false,"",null));}
        UUID create(Funding funding,long amount){Command c=command(Action.CREATE,ZERO,ZERO,0,false,"",draft(funding,amount));require(manager().execute(actor(author),c).equals("ok"),"create");UUID id=manager().all().getLast().id();require(manager().execute(actor(author),c).equals("ok")&&manager().all().size()==1,"creation retry deduplicated");return id;}
        void assign(UUID id){require(run(worker,Action.APPLY,id).equals("ok"),"member applies");require(manager().execute(actor(author),command(Action.ASSIGN,id,worker,0,false,"",null)).equals("ok"),"author assigns");}
        void completeStages(UUID id){for(int i=0;i<2;i++)require(manager().execute(actor(worker),command(Action.STAGE,id,ZERO,i,true,"",null)).equals("ok"),"executor marks stage");}
    }
    public static void main(String[] args)throws Exception {
        CompoundTag budget=new CompoundTag();
        for(int i=0;i<8;i++)require(ProjectNetworkHandlers.consumeRequestBudget(budget,1000),"requests within real-time budget allowed");
        require(!ProjectNetworkHandlers.consumeRequestBudget(budget,1999),"burst is still limited");
        require(ProjectNetworkHandlers.consumeRequestBudget(budget,2000),"budget recovers without advancing world time");
        config();personal();treasury();permissions();failures();geometry();compatibility();cityGuards();codecs();clientSession();serverProjects();
        System.out.println("PASS: "+checks+" City Projects lifecycle, money, save and security checks");
    }
    private static Actor publicActor(Fixture f,UUID player,boolean op,boolean conflict) {
        return new Actor(player,null,false,id->false,z->false,op,(city,representative)->city.equals(f.city)&&representative.equals(f.admin),z->conflict);
    }
    private static Command serverCommand(Fixture f,Action action,UUID id,UUID target,boolean confirmed,Draft draft) {
        return f.command(action,id,target,0,confirmed,"Operator decision",draft);
    }
    private static UUID serverDraft(Fixture f,long reward) {
        var d=new Draft(Scope.SERVER,"Public railway","Outside all cities",Category.RAILWAY,
                new ProjectZone(DIM,ProjectZone.Shape.LINE,1000,1000,1100,1100),List.of(new Stage("Build",false)),Funding.SERVER_ISSUANCE,reward);
        Command c=serverCommand(f,Action.CREATE,ZERO,ZERO,false,d);
        Actor op=publicActor(f,f.author,true,false);
        require(f.manager().execute(op,c).equals("ok"),"OP without a city creates SERVER outside claims");
        int count=f.manager().all().size();require(f.manager().execute(op,c).equals("ok")&&f.manager().all().size()==count,"SERVER creation retry is idempotent");
        return f.manager().all().getLast().id();
    }
    private static void serverProjects()throws Exception {
        Fixture f=new Fixture();Actor op=publicActor(f,f.author,true,false),player=publicActor(f,f.worker,false,false);
        UUID id=serverDraft(f,500);Project p=f.manager().get(id);
        require(p.city()==null&&p.scope()==Scope.SERVER,"SERVER has no Master City or city binding");
        require(f.manager().visible(player,0).isEmpty()&&f.manager().visible(op,0).size()==1,"only OP sees SERVER drafts");
        require(f.manager().execute(player,serverCommand(f,Action.CREATE,ZERO,ZERO,false,new Draft(Scope.SERVER,p.title(),p.description(),p.category(),p.zone(),p.stages(),p.funding(),p.reward()))).equals("permission"),"non-OP cannot create SERVER");
        for(Action a:List.of(Action.UPDATE,Action.PUBLISH,Action.CANCEL,Action.ASSIGN,Action.ACCEPT,Action.RETURN))
            require(f.manager().execute(player,serverCommand(f,a,id,f.worker,false,null)).equals("permission"),"forged SERVER management denied: "+a);
        Actor conflicts=publicActor(f,f.author,true,true);
        require(f.manager().execute(conflicts,serverCommand(f,Action.PUBLISH,id,ZERO,false,null)).equals("conflict"),"territory conflict requires OP acknowledgement");
        require(f.manager().serverReserved()==0&&f.economy.wallet(f.worker)==0,"rejected publication never reserves or issues");
        require(f.manager().execute(conflicts,serverCommand(f,Action.PUBLISH,id,ZERO,true,null)).equals("ok"),"OP confirms territorial overlap separately");
        require(f.manager().visible(player,0).size()==1,"outsider sees published SERVER projects");
        require(f.manager().serverReserved()==500&&f.manager().serverIssued()==0&&f.economy.wallet(f.author)==1000,"publication only reserves capacity, no debit or emission");
        f.economy=reload(f.economy);require(f.manager().serverReserved()==500,"SERVER reservation survives restart");
        require(f.manager().execute(player,serverCommand(f,Action.APPLY,id,ZERO,false,null)).equals("ok"),"player without city applies");
        require(f.manager().execute(op,serverCommand(f,Action.ASSIGN,id,f.worker,false,null)).equals("ok"),"OP assigns outsider");
        require(f.manager().execute(op,serverCommand(f,Action.STAGE,id,ZERO,true,null)).equals("permission"),"unassigned OP cannot mark executor stage");
        require(f.manager().execute(player,serverCommand(f,Action.STAGE,id,ZERO,true,null)).equals("ok"),"outsider executor marks stages");
        require(f.manager().execute(player,serverCommand(f,Action.SUBMIT,id,ZERO,false,null)).equals("ok"),"outsider submits for OP review");
        require(f.economy.wallet(f.worker)==0,"stage and submission do not mint");
        require(f.manager().execute(publicActor(f,f.author,false,false),serverCommand(f,Action.ACCEPT,id,ZERO,false,null)).equals("permission"),"demoted OP author loses acceptance authority");
        require(f.manager().execute(op,serverCommand(f,Action.RETURN,id,ZERO,false,null)).equals("ok"),"OP returns to rework");
        require(f.manager().execute(player,serverCommand(f,Action.SUBMIT,id,ZERO,false,null)).equals("ok"),"executor resubmits");
        require(f.manager().execute(conflicts,serverCommand(f,Action.ACCEPT,id,ZERO,false,null)).equals("conflict"),"acceptance rechecks current territories");
        Command accept=serverCommand(f,Action.ACCEPT,id,ZERO,true,null);
        require(f.manager().execute(conflicts,accept).equals("ok"),"OP accepts and issues once");
        require(f.economy.wallet(f.worker)==500&&f.manager().serverIssued()==500&&f.manager().serverReserved()==0&&f.manager().get(id).paid(),"wallet, receipt and issuance ledger settle together");
        f.economy=reload(f.economy);
        require(!f.manager().execute(conflicts,accept).equals("ok")&&f.economy.wallet(f.worker)==500&&f.manager().serverIssued()==500,"replayed acceptance after restart cannot issue twice");
        require(f.manager().visible(player,3).size()==1,"SERVER completion remains in map history");
        require(f.manager().serverAvailable()==f.manager().serverLimit()-500,"restart does not replenish issued budget");
        CompoundTag corrupted=f.economy.save(new CompoundTag(),null);corrupted.getCompound("CityProjects").putLong("ServerIssued",0);
        var loader=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);loader.setAccessible(true);
        boolean rejected=false;try{loader.invoke(null,corrupted,null);}catch(java.lang.reflect.InvocationTargetException ex){rejected=true;}
        require(rejected,"ledger mismatch fails closed");

        Fixture budget=new Fixture();Actor budgetOp=publicActor(budget,budget.author,true,false);
        long cap=budget.manager().serverLimit();UUID large=serverDraft(budget,cap),second=serverDraft(budget,1);
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.PUBLISH,large,ZERO,false,null)).equals("ok"),"reserve complete available lifetime cap");
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.PUBLISH,second,ZERO,false,null)).equals("budget"),"combined project reservations cannot exceed cap");
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.CANCEL,large,ZERO,false,null)).equals("ok"),"OP cancels and releases capacity");
        require(budget.manager().serverIssued()==0&&budget.manager().serverAvailable()==cap&&budget.economy.wallet(budget.author)==1000,"cancellation issues no currency and performs no refund credit");
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.PUBLISH,second,ZERO,false,null)).equals("ok"),"released capacity can fund another project");

        require(budget.manager().execute(player,serverCommand(budget,Action.WITHDRAW,second,ZERO,false,null)).equals("permission"),"outsider cannot unpublish SERVER project");
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.WITHDRAW,second,ZERO,false,null)).equals("ok")&&budget.manager().serverReserved()==0,"OP unpublishes to edit without money creation");
        Project editable=budget.manager().get(second);
        var edited=new Draft(Scope.SERVER,"Edited public project",editable.description(),editable.category(),editable.zone(),editable.stages(),editable.funding(),2);
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.UPDATE,second,ZERO,false,edited)).equals("ok")&&budget.manager().get(second).reward()==2,"OP edits shared draft fields and budget");
        require(budget.manager().execute(budgetOp,serverCommand(budget,Action.PUBLISH,second,ZERO,false,null)).equals("ok")&&budget.manager().serverReserved()==2,"edited reward is reserved only on republishing");
        Fixture cityPay=new Fixture();UUID cityProject=serverDraft(cityPay,300);Actor cityOp=publicActor(cityPay,cityPay.author,true,false);
        cityPay.manager().execute(cityOp,serverCommand(cityPay,Action.PUBLISH,cityProject,ZERO,false,null));
        Actor representative=new Actor(cityPay.admin,cityPay.city,true,cityPay.members::contains,z->false,false,(city,who)->city.equals(cityPay.city)&&who.equals(cityPay.admin),z->false);
        require(cityPay.manager().execute(cityPay.actor(cityPay.worker),serverCommand(cityPay,Action.APPLY_CITY,cityProject,ZERO,false,null)).equals("permission"),"ordinary member cannot pledge a city");
        require(cityPay.manager().execute(representative,serverCommand(cityPay,Action.APPLY_CITY,cityProject,ZERO,false,null)).equals("ok"),"existing city admin applies on behalf of city");
        require(cityPay.manager().execute(cityOp,serverCommand(cityPay,Action.ASSIGN_CITY,cityProject,cityPay.city,false,null)).equals("ok"),"OP assigns applicant city");
        cityPay.manager().execute(representative,serverCommand(cityPay,Action.STAGE,cityProject,ZERO,true,null));
        cityPay.manager().execute(representative,serverCommand(cityPay,Action.SUBMIT,cityProject,ZERO,false,null));
        require(cityPay.manager().execute(cityOp,serverCommand(cityPay,Action.ACCEPT,cityProject,ZERO,false,null)).equals("ok"),"OP accepts city execution");
        require(cityPay.economy.treasury(cityPay.city)==2300&&cityPay.economy.wallet(cityPay.admin)==0&&cityPay.manager().serverIssued()==300,"city reward credits existing treasury, never representative wallet");
        cityPay.economy=reload(cityPay.economy);require(cityPay.economy.treasury(cityPay.city)==2300&&cityPay.manager().serverIssued()==300,"city issuance settlement survives restart");

        Fixture legacy=new Fixture();UUID oldId=legacy.create(Funding.PERSONAL,25);legacy.run(legacy.author,Action.PUBLISH,oldId);
        var saved=legacy.economy.save(new CompoundTag(),null);var section=saved.getCompound("CityProjects");section.putInt("Version",1);section.remove("ServerIssued");
        EconomyManager migrated=(EconomyManager)loader.invoke(null,saved,null);
        require(migrated.projects().get(oldId).equals(legacy.manager().get(oldId))&&migrated.wallet(legacy.author)==975&&migrated.projects().serverIssued()==0,"v1 CITY migration preserves escrow and wallet exactly");
        require(migrated.save(new CompoundTag(),null).getCompound("CityProjects").getInt("Version")==2,"migration writes additive projects v2");
        require(ProjectNetworkHandlers.filterType(f.manager().visible(op,3),1).isEmpty()&&ProjectNetworkHandlers.filterType(f.manager().visible(op,3),2).size()==1,"scope filter separates SERVER history");
    }
    private static void personal()throws Exception {
        Fixture f=new Fixture();UUID id=f.create(Funding.PERSONAL,300);
        require(f.economy.wallet(f.author)==1000&&f.manager().get(id).reserved()==0,"draft does not reserve money");
        require(f.run(f.author,Action.PUBLISH,id).equals("ok"),"publish personal project");
        require(f.economy.wallet(f.author)==700&&f.manager().get(id).reserved()==300,"publication reserves exact amount");
        f.economy=reload(f.economy);require(f.economy.wallet(f.author)==700&&f.manager().get(id).reserved()==300,"restart retains debit and reserve together");
        f.assign(id);require(f.run(f.worker,Action.SUBMIT,id).equals("stages"),"incomplete stages block review");f.completeStages(id);
        require(f.economy.wallet(f.worker)==0,"stages never pay");require(f.run(f.worker,Action.SUBMIT,id).equals("ok"),"submit");
        require(f.run(f.worker,Action.ACCEPT,id).equals("permission"),"executor cannot accept own work");
        require(f.manager().execute(f.actor(f.author),f.command(Action.RETURN,id,ZERO,0,false,"Fix roof",null)).equals("ok"),"author returns for rework");
        require(f.manager().get(id).status()==Status.REWORK&&f.economy.wallet(f.worker)==0,"rework retains reserve");
        require(f.run(f.worker,Action.SUBMIT,id).equals("ok"),"resubmit");Command accept=f.command(Action.ACCEPT,id,ZERO,0,false,"",null);
        require(f.manager().execute(f.actor(f.author),accept).equals("ok"),"author accepts");
        require(f.economy.wallet(f.worker)==300&&f.manager().get(id).reserved()==0&&f.manager().get(id).paid(),"one final payout");
        f.economy=reload(f.economy);require(!f.manager().execute(f.actor(f.author),accept).equals("ok")&&f.economy.wallet(f.worker)==300,"replayed acceptance after restart cannot pay again");
        require(f.manager().visible(f.actor(f.author),3).size()==1&&f.manager().visible(f.actor(f.author),0).isEmpty(),"completed history retained");
        require(f.economy.wallet(f.author)+f.economy.wallet(f.worker)==1000,"CITY conserves currency");
    }
    private static void treasury()throws Exception {
        Fixture f=new Fixture();UUID id=f.create(Funding.TREASURY,500);
        require(f.run(f.author,Action.PUBLISH,id).equals("ok")&&f.manager().get(id).status()==Status.PENDING_FUNDING,"treasury requests approval");
        require(f.economy.treasury(f.city)==2000,"request does not debit");require(f.run(f.author,Action.APPROVE,id).equals("permission"),"ordinary author cannot approve treasury");
        require(f.manager().execute(f.actor(f.admin),f.command(Action.REJECT_FUNDING,id,ZERO,0,false,"Reduce scope",null)).equals("ok"),"admin can reject funding to draft");
        require(f.manager().get(id).status()==Status.DRAFT&&f.economy.treasury(f.city)==2000,"rejection is unfunded");
        f.run(f.author,Action.PUBLISH,id);require(f.run(f.admin,Action.APPROVE,id).equals("ok"),"admin approves");
        require(f.economy.treasury(f.city)==1500&&f.economy.treasuryEscrow(f.city)==500,"treasury debit and aggregate reserve");
        f.economy=reload(f.economy);f.assign(id);
        require(f.run(f.author,Action.CANCEL,id).equals("permission"),"assigned cancellation needs admin");
        require(f.run(f.admin,Action.CANCEL,id).equals("reason"),"assigned cancellation needs reason");
        Command cancel=f.command(Action.CANCEL,id,ZERO,0,false,"Abandoned",null);
        require(f.manager().execute(f.actor(f.admin),cancel).equals("ok"),"cancel assigned project");
        f.economy=reload(f.economy);require(f.economy.treasury(f.city)==2000&&f.economy.treasuryEscrow(f.city)==0,"refund reaches original treasury");
        require(!f.manager().execute(f.actor(f.admin),cancel).equals("ok")&&f.economy.treasury(f.city)==2000,"refund cannot repeat");
        Fixture paid=new Fixture();UUID p=paid.create(Funding.TREASURY,500);paid.run(paid.author,Action.PUBLISH,p);paid.run(paid.admin,Action.APPROVE,p);paid.assign(p);paid.completeStages(p);paid.run(paid.worker,Action.SUBMIT,p);
        require(paid.run(paid.admin,Action.ACCEPT,p).equals("ok")&&paid.economy.treasury(paid.city)==1500&&paid.economy.wallet(paid.worker)==500,"admin accepts treasury project with no emission");
    }
    private static void permissions()throws Exception {
        Fixture f=new Fixture();UUID id=f.create(Funding.FREE,0);
        require(f.manager().visible(f.actor(f.worker),0).isEmpty(),"other members cannot read private drafts");
        require(f.run(f.other,Action.PUBLISH,id).equals("permission"),"other member cannot publish author draft");
        UUID outsider=UUID.randomUUID();require(f.run(outsider,Action.PUBLISH,id).equals("membership"),"nonmember denied");
        Actor foreign=new Actor(f.author,UUID.randomUUID(),true,f.members::contains,z->true);
        require(f.manager().execute(foreign,f.command(Action.PUBLISH,id,ZERO,0,false,"",null)).equals("missing"),"cross-city target denied");
        f.run(f.author,Action.PUBLISH,id);f.assign(id);
        require(f.manager().execute(f.actor(f.author),f.command(Action.STAGE,id,ZERO,0,true,"",null)).equals("permission"),"author cannot mark executor stages");
        f.run(f.other,Action.APPLY,id); // not open; exercise reassignment with a previously submitted applicant below.
        f.members.remove(f.worker);
        require(f.run(f.worker,Action.SUBMIT,id).equals("membership"),"departed executor loses authority");
        f.members.remove(f.author);require(f.run(f.admin,Action.CANCEL,id).equals("reason"),"admin retains control when author leaves");
        Fixture reassigned=new Fixture();UUID r=reassigned.create(Funding.PERSONAL,100);reassigned.run(reassigned.author,Action.PUBLISH,r);
        reassigned.run(reassigned.other,Action.APPLY,r);reassigned.assign(r);reassigned.members.remove(reassigned.worker);
        require(reassigned.manager().execute(reassigned.actor(reassigned.author),reassigned.command(Action.ASSIGN,r,reassigned.other,0,false,"",null)).equals("ok"),"departed executor can be replaced from applications");
        require(reassigned.manager().get(r).executor().id().equals(reassigned.other)&&reassigned.manager().get(r).reserved()==100,"reassignment preserves escrow");
        Fixture free=new Fixture();UUID p=free.create(Funding.FREE,0);free.run(free.author,Action.PUBLISH,p);free.assign(p);free.completeStages(p);free.run(free.worker,Action.SUBMIT,p);
        require(free.run(free.admin,Action.ACCEPT,p).equals("ok")&&free.economy.wallet(free.worker)==0,"unpaid project completes without minting");
    }
    @SuppressWarnings("unchecked") private static Map<UUID,Long> wallets(EconomyManager m)throws Exception {var field=EconomyManager.class.getDeclaredField("wallets");field.setAccessible(true);return(Map<UUID,Long>)field.get(m);}
    private static void failures()throws Exception {
        Fixture f=new Fixture();UUID id=f.create(Funding.PERSONAL,1001);require(f.run(f.author,Action.PUBLISH,id).equals("funds")&&f.economy.wallet(f.author)==1000&&f.manager().get(id).status()==Status.DRAFT,"failed reserve has no partial effect");
        Draft server=new Draft(Scope.SERVER,"Public works","",Category.ROAD,new ProjectZone(DIM,ProjectZone.Shape.POINT,0,0,0,0),List.of(new Stage("Build",false)),Funding.SERVER_ISSUANCE,500);
        require(f.manager().execute(f.actor(f.admin),f.command(Action.CREATE,ZERO,ZERO,0,false,"",server)).equals("permission"),"forged SERVER creation denied even for city admin");
        Draft outside=new Draft(Scope.CITY,"Outside","",Category.OTHER,new ProjectZone(DIM,ProjectZone.Shape.RECTANGLE,0,0,50,50),List.of(new Stage("Build",false)),Funding.FREE,0);
        require(f.manager().execute(f.actor(f.author),f.command(Action.CREATE,ZERO,ZERO,0,false,"",outside)).equals("territory"),"rectangle must be fully owned");
        require(f.manager().execute(f.actor(f.author),new Command(UUID.randomUUID(),id,-1,Action.CANCEL,ZERO,0,false,"",null)).equals("stale"),"stale version denied");
        Fixture overflow=new Fixture();UUID p=overflow.create(Funding.PERSONAL,1);overflow.run(overflow.author,Action.PUBLISH,p);overflow.assign(p);overflow.completeStages(p);overflow.run(overflow.worker,Action.SUBMIT,p);
        wallets(overflow.economy).put(overflow.worker,Long.MAX_VALUE);
        require(overflow.run(overflow.admin,Action.ACCEPT,p).equals("overflow")&&overflow.manager().get(p).reserved()==1&&overflow.manager().get(p).status()==Status.REVIEW,"overflow leaves reward reserved");
        CompoundTag saved=overflow.economy.save(new CompoundTag(),null);saved.getCompound("CityProjects").getList("Projects",Tag.TAG_COMPOUND).getCompound(0).putLong("Reserved",0);
        boolean corrupt=false;try{new ProjectManager(new StubAccounts()).load(saved.getCompound("CityProjects"));}catch(IllegalArgumentException ex){corrupt=true;}require(corrupt,"corrupt funded project fails closed");
    }
    private static final class StubAccounts implements Accounts {
        public long balance(boolean c,UUID id){return 0;}public boolean debit(boolean c,UUID id,long n){return false;}public boolean canCredit(boolean c,UUID id,long n){return true;}public void credit(boolean c,UUID id,long n){}public long maximum(){return 1000;}public void dirty(){}
    }
    private static void geometry() {
        require(new ProjectZone(DIM,ProjectZone.Shape.POINT,-1,-1,80,80).plots().equals(Set.of(new CityPlot(DIM,-1,-1))),"negative point normalizes endpoints");
        var diagonal=new ProjectZone(DIM,ProjectZone.Shape.LINE,25,25,75,75);
        require(diagonal.plots().size()==4,"diagonal corner crossings include both neighbouring plots");
        var line=new ProjectZone(DIM,ProjectZone.Shape.LINE,-75,25,75,25);require(line.plots().size()==4,"line checks intervening plots");
        require(new ProjectZone(DIM,ProjectZone.Shape.RECTANGLE,99,99,0,0).plots().size()==4,"reversed rectangle covers all plots");
        require(diagonal.distance(50,50)==0&&diagonal.distance(25,75)>30,"map picking uses actual line");
        boolean refused=false;try{new ProjectZone(DIM,ProjectZone.Shape.LINE,Integer.MIN_VALUE,0,Integer.MAX_VALUE,0);}catch(IllegalArgumentException ex){refused=true;}require(refused,"coordinate overflow rejected");
        for(int x=-100;x<=100;x+=5)for(int z=-100;z<=100;z+=5) {
            var p=new ProjectZone(DIM,ProjectZone.Shape.POINT,x,z,x,z);require(p.plots().contains(CityPlot.at(DIM,x,z)),"point and claim grid agree");
        }
    }
    private static void compatibility()throws Exception {
        EconomyManager original=new EconomyManager();UUID player=UUID.randomUUID(),city=UUID.randomUUID();original.adminSetWallet(player,1234);original.adminSetTreasury(city,5678);
        CompoundTag old=original.save(new CompoundTag(),null);old.remove("CityProjects");old.putInt("FormatVersion",7);
        var load=EconomyManager.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);load.setAccessible(true);
        EconomyManager migrated=(EconomyManager)load.invoke(null,old,null);
        require(migrated.wallet(player)==1234&&migrated.treasury(city)==5678&&migrated.projects().all().isEmpty(),"v7 save loads with unchanged balances and no projects");
        CompoundTag round=migrated.save(new CompoundTag(),null);require(round.getInt("FormatVersion")==12&&round.contains("FreightLogistics")&&round.contains("PersonalBank")&&round.getCompound("FreightLogistics").contains("Routing"),"v10 retains Freight, personal banking and regional routing after legacy migration");
    }
    private static void cityGuards()throws Exception {
        CityManager cities=new CityManager();UUID owner=UUID.randomUUID();City city=cities.createCity("Test",owner,DIM,new BlockPos(0,64,0));
        var guard=CityManager.class.getDeclaredField("activeBuildProjects");guard.setAccessible(true);guard.set(cities,(java.util.function.Predicate<UUID>)id->true);
        require(cities.removeCityByOwner(owner)==null&&cities.getCityByOwner(owner)==city,"active projects block deletion by owner");
        require(cities.removeCityByPylon(city.getPylonPos())==null,"active projects block deletion by pylon");
        city.setPylonActive(false);require(!cities.rebindPylon(city,ResourceLocation.parse("minecraft:the_nether"),new BlockPos(0,64,0)),"active projects block dimension rebasing");
        guard.set(cities,(java.util.function.Predicate<UUID>)id->false);require(cities.removeCityByOwner(owner)==city,"no active projects permits existing deletion");
    }
    private static void codecs() {
        Fixture fixture=new Fixture();var command=fixture.command(Action.CREATE,ZERO,ZERO,0,false,"",fixture.draft(Funding.PERSONAL,500));
        var payload=new com.example.examplemod.network.ProjectCommandPayload(command,12,3);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            com.example.examplemod.network.ProjectCommandPayload.STREAM_CODEC.encode(buffer,payload);
            require(com.example.examplemod.network.ProjectCommandPayload.STREAM_CODEC.decode(buffer).equals(payload),"command codec preserves bounded draft, request, scope and page");
            buffer.clear();CompoundTag snapshot=new CompoundTag();snapshot.putUUID("Request",command.request());snapshot.put("Records",fixture.manager().save());
            var state=new com.example.examplemod.network.ProjectStatePayload(snapshot);
            com.example.examplemod.network.ProjectStatePayload.STREAM_CODEC.encode(buffer,state);
            require(com.example.examplemod.network.ProjectStatePayload.STREAM_CODEC.decode(buffer).data().equals(snapshot),"snapshot codec roundtrip");
            require(buffer.readableBytes()==0,"snapshot consumes exactly its frame");
        } finally {buffer.release();}
        boolean refused=false;try {new com.example.examplemod.network.ProjectCommandPayload(command,-1,0);}catch(IllegalArgumentException ex){refused=true;}
        require(refused,"negative network page rejected");
        refused=false;try {new com.example.examplemod.network.ProjectCommandPayload(command,0,16);}catch(IllegalArgumentException ex){refused=true;}
        require(refused,"unknown filter rejected");
        UUID serverId=UUID.randomUUID();Project server=new Project(serverId,UUID.randomUUID(),Scope.SERVER,null,fixture.admin,"Public works","",Category.ROAD,
                new ProjectZone(DIM,ProjectZone.Shape.LINE,-100,0,100,0),List.of(new Stage("Build",false)),Funding.SERVER_ISSUANCE,0,Status.DRAFT,List.of(),null,0,false,0,"");
        require(ProjectCodec.read(ProjectCodec.write(server)).equals(server),"shared model preserves SERVER shape without enabling execution or emission");
    }
    private static void clientSession()throws Exception {
        Fixture f=new Fixture();UUID id=f.create(Funding.FREE,0);Project p=f.manager().get(id);
        var command=f.command(Action.REQUEST,ZERO,ZERO,0,false,"",null);
        var pending=com.example.examplemod.client.ProjectClientState.class.getDeclaredField("pending");pending.setAccessible(true);
        var payload=new com.example.examplemod.network.ProjectCommandPayload(command,0,0);
        CompoundTag snapshot=new CompoundTag();snapshot.putUUID("Request",command.request());snapshot.putUUID("City",f.city);snapshot.putString("Result","ok");snapshot.put("Records",f.manager().save());
        ListTag markers=new ListTag();markers.add(ProjectCodec.write(p));snapshot.put("Markers",markers);
        com.example.examplemod.client.ProjectClientState.clear();pending.set(null,payload);
        CompoundTag stale=snapshot.copy();stale.putUUID("Request",UUID.randomUUID());
        com.example.examplemod.client.ProjectClientState.accept(new com.example.examplemod.network.ProjectStatePayload(stale));
        require(com.example.examplemod.client.ProjectClientState.city==null&&com.example.examplemod.client.ProjectClientState.pending(),"out-of-order reply cannot install another snapshot");
        com.example.examplemod.client.ProjectClientState.accept(new com.example.examplemod.network.ProjectStatePayload(snapshot));
        require(com.example.examplemod.client.ProjectClientState.city.equals(f.city)&&com.example.examplemod.client.ProjectClientState.records.size()==1,"matching reply installs complete snapshot");
        require(com.example.examplemod.client.ProjectClientState.markers.getFirst().scope()==Scope.CITY&&com.example.examplemod.client.ProjectClientState.markers.getFirst().zone().equals(p.zone()),"map marker preserves shared scope and footprint");
        pending.set(null,payload);CompoundTag busy=new CompoundTag();busy.putUUID("Request",command.request());busy.putBoolean("KeepState",true);busy.putString("Result","busy");
        com.example.examplemod.client.ProjectClientState.accept(new com.example.examplemod.network.ProjectStatePayload(busy));
        require(com.example.examplemod.client.ProjectClientState.records.size()==1&&!com.example.examplemod.client.ProjectClientState.pending(),"rate rejection preserves last valid map snapshot");
        com.example.examplemod.client.ProjectClientState.op=true;com.example.examplemod.client.ProjectClientState.serverIssued=10;
        com.example.examplemod.client.ProjectClientState.clear();
        require(!com.example.examplemod.client.ProjectClientState.op&&com.example.examplemod.client.ProjectClientState.serverIssued==0,"closed session clears OP capability and budget snapshot");
        com.example.examplemod.client.ProjectClientState.accept(new com.example.examplemod.network.ProjectStatePayload(snapshot));
        require(com.example.examplemod.client.ProjectClientState.records.isEmpty()&&com.example.examplemod.client.ProjectClientState.markers.isEmpty(),"closed session rejects late replies");
    }
}
