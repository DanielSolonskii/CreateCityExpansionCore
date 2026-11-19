package com.example.examplemod.community;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.ProjectNetworkHandlers;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class ProjectPlanService {
    private record Budget(long window,int reads,int writes){}
    private static final Map<ServerPlayer,Budget> budgets=new WeakHashMap<>();
    private ProjectPlanService(){}
    public static void handle(ServerPlayer player,ProjectPlanRequest request){
        if(player instanceof FakePlayer)return;
        PacketDistributor.sendToPlayer(player,new WorkspaceState(process(player,request)));
    }
    public static CompoundTag process(ServerPlayer player,ProjectPlanRequest request){
        var out=new CompoundTag();out.putInt("ProjectPlanFormat",1);out.putUUID("Request",request.request());
        long now=net.minecraft.Util.getMillis()/1000;
        var old=budgets.get(player);
        if(old==null || old.window()!=now)old=new Budget(now,0,0);
        boolean write=request.changes()!=null;
        if(old.reads()>=8 || write && old.writes()>=3)return result(out,"busy");
        budgets.put(player,new Budget(now,old.reads()+1,old.writes()+(write?1:0)));
        var server=player.serverLevel().getServer();var cities=CityManager.get(server.overworld());
        var actor=ProjectNetworkHandlers.actor(player,cities,cities.getMemberCity(player.getUUID()));
        var project=EconomyManager.get(server.overworld()).projects().get(request.project());
        if(project==null || !CommunityActions.visible(project,actor) || !ProjectWorkspaceService.participant(project,actor))
            return result(out,"permission");
        var data=ProjectWorkspaceData.get(server);var book=data.plans;
        if(!book.available())return result(out,"incompatible");
        boolean manager=ProjectWorkspaceService.manages(project,actor);
        if(write){
            var doc=request.changes();
            if(project.status().terminal())return result(out,"closed");
            if(!doc.complete() || !project.zone().dimension().equals(doc.dimension()) ||
                    !doc.nodes().stream().allMatch(n->ProjectWorkspaceService.inside(project.zone(),n.x(),n.z())))return result(out,"bounds");
            String answer=book.put(player.getUUID(),request,manager);
            if(!answer.equals("saved") && !answer.equals("repeat"))return result(out,answer);
            if(answer.equals("saved"))data.setDirty();
        }
        out.putUUID("Project",project.id()); out.putBoolean("CanPublish",!project.status().terminal());
        var rows=new ListTag();
        for(var entry:book.list(project.id())){
            var row=new CompoundTag();row.putUUID("Id",entry.plan().id());row.putString("Name",entry.plan().name());
            row.putLong("Version",entry.version());row.putUUID("Author",entry.author());rows.add(row);
        }
        out.put("Rows",rows);
        if(!request.plan().equals(ProjectPlanRequest.LIST)){
            var entry=book.get(project.id(),request.plan());
            if(entry==null)return result(out,"missing");
            out.put("Document",entry.save());out.putBoolean("Editable",!project.status().terminal() && (manager || entry.author().equals(player.getUUID())));
        }
        return result(out,write?"saved":"ok");
    }
    private static CompoundTag result(CompoundTag out,String code){out.putString("Result",code);return out;}
}
