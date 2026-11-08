package com.example.examplemod.community;
import com.example.examplemod.city.*;
import com.example.examplemod.projects.*;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.alliance.*;
import com.example.examplemod.auth.PlayerNames;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.*;
import java.util.*;
/** Small recipient-scoped projection; no city/member directory or financial data. */
public final class CommunitySummary {
    private CommunitySummary(){}
    public static CompoundTag build(ServerPlayer p,CompoundTag out,boolean includeInbox,int page){
        var s=p.getServer();var cities=CityManager.get(s.overworld());var own=cities.getMemberCity(p.getUUID());var inbox=CommunityInbox.get(s);var social=com.example.examplemod.social.SocialSavedData.get(s).book;
        out.putString("SelfName",PlayerNames.name(p));out.putString("OwnRole",own==null?"":own.getMember(p.getUUID()).getRole().name());
        if(own!=null){out.putUUID("OwnCity",own.getId());out.putString("OwnName",own.getName());out.putInt("OwnColor",own.getColorRgb());out.putInt("OwnMembers",own.getMemberCount());}
        out.putInt("Unread",(int)social.dialogs.keySet().stream().filter(pair->pair.includes(p.getUUID())&&social.unread(p.getUUID(),pair.other(p.getUUID()))>0).count());
        out.putInt("FriendRequests",(int)social.requests.stream().filter(e->e.to().equals(p.getUUID())).count());out.putInt("InboxUnread",inbox.unread(p.getUUID()));
        if(includeInbox){out.put("Inbox",inbox.page(p.getUUID(),Math.clamp(page,0,1)));out.putInt("InboxTotal",inbox.history(p.getUUID()).size());}
        var tasks=new ListTag();
        for(var c:cities.getCities()){var invite=c.getInvite(p.getUUID());if(invite!=null&&tasks.size()<24){var t=task("invite",c.getId(),c.getName(),"invite");t.putUUID("Token",invite.token());t.putBoolean("Allowed",cities.getMemberCity(p.getUUID())==null&&(invite.getInvokerId()==null||c.isAdmin(invite.getInvokerId())));tasks.add(t);}}
        var actor=ProjectNetworkHandlers.actor(p,cities,own);int projectTotal=0;
        for(var project:EconomyManager.get(s.overworld()).projects().all())if(CommunityActions.visible(project,actor)&&!project.status().terminal()){
            projectTotal++;String action=CommunityActions.attention(project,actor);if(!action.isBlank()&&tasks.size()<24){var t=task("project",project.id(),project.title(),action);t.putString("Status",project.status().name());t.putBoolean("Allowed",true);tasks.add(t);}
        }
        var alliances=AllianceSavedData.get(s).book;
        if(own!=null){var a=alliances.city(own.getId());if(a!=null){out.putUUID("AllianceId",a.id);out.putString("AllianceName",a.name);var ref=new AllianceBook.CityRef(own.getId(),own.getName(),own.getOwner(),own.getMembers().stream().map(CityMember::getPlayerId).collect(java.util.stream.Collectors.toSet()));
            UUID read=inbox.allianceRead(p.getUUID(),a.id);int at=-1;for(int i=0;i<a.chat.size();i++)if(a.chat.get(i).id().equals(read))at=i;
            boolean unread=read==null?inbox.history(p.getUUID()).stream().anyMatch(e->!e.read()&&e.kind().equals("alliance_message")&&a.id.equals(e.alliance())):a.chat.subList(at+1,a.chat.size()).stream().anyMatch(m->!m.author().equals(p.getUUID()));out.putInt("AllianceUnread",unread?1:0);
            if(alliances.council(a,ref,p.getUUID()))for(var vote:a.proposals.values())if(tasks.size()<24&&vote.expires>System.currentTimeMillis()&&vote.electorate.contains(own.getId())&&!vote.votes.containsKey(own.getId()))tasks.add(task("council",a.id,a.name,"vote"));
        }
            for(var incoming:alliances.all())if(tasks.size()<24&&incoming.invitations.getOrDefault(own.getId(),0L)>System.currentTimeMillis()){var t=task("alliance",incoming.id,incoming.name,"alliance");t.putBoolean("Allowed",own.isOwner(p.getUUID()));tasks.add(t);}
        }
        out.putInt("ProjectCount",projectTotal);out.put("Tasks",tasks);out.putInt("TaskCount",tasks.size());out.putString("Result","ok");return out;
    }
    private static CompoundTag task(String kind,UUID id,String name,String action){var t=new CompoundTag();t.putString("Kind",kind);t.putUUID("Id",id);t.putString("Name",name);t.putString("Action",action);t.putBoolean("Allowed",true);return t;}
}
