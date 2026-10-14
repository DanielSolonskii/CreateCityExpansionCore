package com.example.examplemod.projects;

import com.example.examplemod.city.*;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.network.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class ProjectNetworkHandlers {
    public static final int PAGE_SIZE=16;
    private ProjectNetworkHandlers(){}
    static boolean consumeRequestBudget(CompoundTag budget,long realTimeMillis) {
        long window=realTimeMillis/1000;
        if(budget.getLong("CCECProjectWindow")!=window){budget.putLong("CCECProjectWindow",window);budget.putInt("CCECProjectRequests",0);}
        int requests=budget.getInt("CCECProjectRequests");
        if(requests>=8)return false;
        budget.putInt("CCECProjectRequests",requests+1);return true;
    }
    public static ProjectManager.Actor actor(ServerPlayer player,CityManager cities,City city) {
        return new ProjectManager.Actor(player.getUUID(),city==null?null:city.getId(),city!=null&&city.isAdmin(player.getUUID()),
                id->city!=null&&city.hasMember(id),
                zone->city!=null&&zone.dimension().equals(city.getPylonDimension())&&zone.plots().stream().allMatch(p->city==cities.getClaimOwner(p)),
                player.hasPermissions(2),(id,representative)->{City represented=cities.getCity(id);return represented!=null&&represented.isAdmin(representative);},
                zone->zone.plots().stream().anyMatch(plot->cities.getClaimOwner(plot)!=null));
    }
    public static void handle(ServerPlayer player,ProjectCommandPayload payload) {
        CityManager cities=CityManager.get(player.getServer().overworld());City city=cities.getMemberCity(player.getUUID());
        CompoundTag out=new CompoundTag();out.putUUID("Request",payload.command().request());out.putInt("Filter",payload.filter());
        if(!consumeRequestBudget(player.getPersistentData(),net.minecraft.Util.getMillis())){reply(player,out,"busy",true);return;}
        var actor=actor(player,cities,city);out.putBoolean("Op",actor.op());
        int type=payload.filter()/4,statusFilter=payload.filter()%4;
        if(type==3&&!actor.op()){reply(player,out,"permission",true);return;}
        if(payload.command().draft()!=null&&player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,payload.command().draft().zone().dimension()))==null){reply(player,out,"dimension",true);return;}
        EconomyManager economy=EconomyManager.get(player.serverLevel());ProjectManager manager=economy.projects();
        String result=manager.execute(actor,payload.command());out.putString("Result",result);
        if(city!=null)out.putUUID("City",city.getId());out.putBoolean("Admin",actor.admin());out.putBoolean("Op",actor.op());
        out.putBoolean("CityRepresentative",city!=null&&actor.representsCity().test(city.getId(),player.getUUID()));
        out.putLong("Wallet",economy.wallet(player.getUUID()));out.putLong("Treasury",city==null?0:economy.treasury(city.getId()));
        out.putLong("Maximum",com.example.examplemod.Config.ECONOMY_MAX_TRANSACTION.get());
        if(actor.op()){out.putLong("ServerLimit",manager.serverLimit());out.putLong("ServerAvailable",manager.serverAvailable());out.putLong("ServerIssued",manager.serverIssued());out.putLong("ServerReserved",manager.serverReserved());}
        Project created=payload.command().action()==ProjectManager.Action.CREATE?manager.all().stream().filter(p->p.author().equals(player.getUUID())&&p.creationRequest().equals(payload.command().request())).findFirst().orElse(null):null;
        List<Project> visible=filterType(manager.visible(actor,statusFilter),type);int page=Math.min(payload.page(),Math.max(0,(visible.size()-1)/PAGE_SIZE));
        UUID focus=created!=null?created.id():payload.command().project();
        if(focus!=null)for(int i=0;i<visible.size();i++)if(visible.get(i).id().equals(focus)){page=i/PAGE_SIZE;break;}
        List<Project> records=visible.subList(page*PAGE_SIZE,Math.min(visible.size(),(page+1)*PAGE_SIZE));
        out.putInt("Page",page);out.putInt("Total",visible.size());out.put("Records",ProjectCodec.save(records));
        List<Project> mapProjects=statusFilter==3?records:filterType(manager.visible(actor,0),type);
        ListTag markers=new ListTag();for(Project p:mapProjects){CompoundTag t=ProjectCodec.write(p);t.remove("Applicants");t.remove("Description");t.remove("Stages");markers.add(t);}out.put("Markers",markers);
        CompoundTag names=new CompoundTag(),conflicts=new CompoundTag();
        ListTag cityMembers=new ListTag();
        if(city!=null)for(CityMember member:city.getMembers()){addName(names,player,member.getPlayerId());cityMembers.add(StringTag.valueOf(member.getPlayerId().toString()));}
        out.put("CityMembers",cityMembers);
        for(Project p:records){if(p.scope()==Project.Scope.SERVER){addName(names,player,p.author());for(Project.Applicant app:p.applicants()){
            addName(names,player,app.representative());
            if(app.kind()==Project.ApplicantKind.CITY){City represented=cities.getCity(app.id());if(represented!=null)names.putString(app.id().toString(),represented.getName());}
        }}
            if(p.scope()==Project.Scope.SERVER){ListTag overlaps=new ListTag();p.zone().plots().stream().map(cities::getClaimOwner).filter(Objects::nonNull).map(City::getName).distinct().sorted().forEach(name->overlaps.add(StringTag.valueOf(name)));conflicts.put(p.id().toString(),overlaps);}
        }
        out.put("Names",names);out.put("Conflicts",conflicts);
        if(created!=null)out.putUUID("Created",created.id());
        PacketDistributor.sendToPlayer(player,new ProjectStatePayload(out));
    }
    static List<Project> filterType(List<Project> projects,int type){return projects.stream().filter(p->type==0||p.scope()==(type==1?Project.Scope.CITY:Project.Scope.SERVER)).toList();}
    private static void addName(CompoundTag names,ServerPlayer player,UUID id){names.putString(id.toString(),com.example.examplemod.auth.PlayerNames.name(player.getServer(),id));}
    private static void reply(ServerPlayer player,CompoundTag out,String result,boolean keep){out.putString("Result",result);out.putBoolean("KeepState",keep);PacketDistributor.sendToPlayer(player,new ProjectStatePayload(out));}
}
