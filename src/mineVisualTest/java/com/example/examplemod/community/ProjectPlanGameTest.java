package com.example.examplemod.community;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.network.ProjectPlanRequest;
import com.example.examplemod.projects.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class ProjectPlanGameTest {
    private static ServerPlayer player(ServerLevel level,String name){
        return new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());
    }
    @GameTest(template="auger_empty",timeoutTicks=200)
    public static void shareConflictAndRevoke(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();
        var owner=player(level,"PlanMayor"); var member=player(level,"PlanMember"); var stranger=player(level,"PlanGuest");
        var cities=CityManager.get(server.overworld());
        var city=cities.createCity("Plan test",owner.getUUID(),level.dimension().location(),new BlockPos(65001,70,65001));
        h.assertTrue(city!=null,"created city"); city.addMember(member.getUUID());
        var manager=EconomyManager.get(server.overworld()).projects();var actor=ProjectNetworkHandlers.actor(owner,cities,city);
        var zone=new ProjectZone(level.dimension().location(),ProjectZone.Shape.RECTANGLE,65000,65000,65009,65009);
        var spec=new ProjectManager.Draft(Project.Scope.CITY,"Plan yard","",Project.Category.RAILWAY,zone,
                List.of(new Project.Stage("Track",false)),Project.Funding.FREE,0);
        var zero=ProjectPlanRequest.LIST;
        var create=new ProjectManager.Command(UUID.randomUUID(),zero,0,ProjectManager.Action.CREATE,zero,0,false,"",spec);
        h.assertTrue("ok".equals(manager.execute(actor,create)),"create project");
        var project=manager.all().stream().filter(p->p.creationRequest().equals(create.request())).findFirst().orElseThrow();
        var id=project.id();
        h.assertTrue("ok".equals(manager.execute(actor,new ProjectManager.Command(UUID.randomUUID(),id,project.revision(),ProjectManager.Action.PUBLISH,zero,0,false,"",null))),"publish project");
        var plan=new EngineeringPlan(UUID.randomUUID(),zone.dimension(),EngineeringPlan.Kind.LINE,"Bridge","note",
                List.of(new EngineeringPlan.Node(65000,65001),new EngineeringPlan.Node(65006,65002)));
        var first=new ProjectPlanRequest(UUID.randomUUID(),id,plan.id(),0,plan);
        var saved=ProjectPlanService.process(member,first);
        h.assertTrue(saved.getString("Result").equals("saved") && saved.getCompound("Document").getLong("Version")==1,"publish participant draft");
        var repeat=ProjectPlanService.process(member,first);
        h.assertTrue(repeat.getCompound("Document").getLong("Version")==1,"idempotent replay");
        var revised=plan.describe("Mayor revision","checked");
        var put=new ProjectPlanRequest(UUID.randomUUID(),id,plan.id(),1,revised);
        h.assertTrue(ProjectPlanService.process(owner,put).getCompound("Document").getLong("Version")==2,"manager updates by version");
        var stale=new ProjectPlanRequest(UUID.randomUUID(),id,plan.id(),1,plan.describe("My local edits","keep me"));
        h.assertTrue(ProjectPlanService.process(member,stale).getString("Result").equals("conflict"),"stale CAS refused");
        var get=new ProjectPlanRequest(UUID.randomUUID(),id,plan.id(),0,null);
        var reply=ProjectPlanService.process(owner,get);
        h.assertTrue(ProjectPlanVersion.load(reply.getCompound("Document")).plan().equals(revised),"concurrent edit survives");
        var db=ProjectWorkspaceData.get(server);
        var restored=ProjectWorkspaceData.load(db.save(new CompoundTag(),server.registryAccess()),server.registryAccess());
        h.assertTrue(restored.plans.get(id,plan.id()).version()==2,"persist shared revision");
        h.assertTrue(ProjectWorkspaceData.load(new CompoundTag(),server.registryAccess()).plans.list(id).isEmpty(),"legacy empty extension");
        city.removeMember(member.getUUID());
        for(var person:List.of(member,stranger)){
            var denied=ProjectPlanService.process(person,get);
            h.assertTrue(denied.getString("Result").equals("permission") && !denied.contains("Rows") && !denied.contains("Document"),"revoked or unrelated actor receives no geometry or metadata");
        }
        h.succeed();
    }
}
