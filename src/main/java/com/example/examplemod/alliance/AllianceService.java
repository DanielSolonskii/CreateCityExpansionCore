package com.example.examplemod.alliance;

import com.example.examplemod.city.*;
import com.example.examplemod.network.*;
import com.example.examplemod.auth.PlayerNames;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Public identity and member-only council/chat are deliberately different projections. No bridge/outbox. */
public final class AllianceService {
    private AllianceService(){}
    public static Map<UUID,AllianceBook.CityRef> directory(CityManager manager){
        var out=new LinkedHashMap<UUID,AllianceBook.CityRef>();for(var c:manager.getCities()){var members=new HashSet<UUID>();for(var m:c.getMembers())members.add(m.getPlayerId());members.add(c.getOwner());out.put(c.getId(),new AllianceBook.CityRef(c.getId(),c.getName(),c.getOwner(),members));}return out;
    }
    public static boolean mayReadPrivate(AllianceBook.Alliance alliance,AllianceBook.CityRef own,UUID actor){return alliance!=null&&own!=null&&own.members().contains(actor)&&alliance.cities.contains(own.id());}
    public static CompoundTag summary(AllianceBook.Alliance a,Map<UUID,AllianceBook.CityRef> cities){
        var t=new CompoundTag();t.putUUID("Id",a.id);t.putString("Name",a.name);t.putString("Description",a.description);t.putInt("Color",a.color);t.putInt("Emblem",a.emblem);t.putLong("Revision",a.revision);
        var members=new ListTag();for(UUID id:a.cities){var c=cities.get(id);if(c==null)continue;var m=new CompoundTag();m.putUUID("Id",id);m.putString("Name",c.name());members.add(m);}t.put("Cities",members);return t;
    }
    public static void handle(ServerPlayer p,AllianceRequestPayload request){
        if(p instanceof net.neoforged.neoforge.common.util.FakePlayer)return;
        var budget=p.getPersistentData();long second=net.minecraft.Util.getMillis()/1000;if(budget.getLong("CCECAllianceWindow")!=second){budget.putLong("CCECAllianceWindow",second);budget.putInt("CCECAllianceRequests",0);}
        int count=budget.getInt("CCECAllianceRequests");var c=request.command();var out=new CompoundTag();out.putUUID("Request",c.request());
        if(count>=8){out.putString("Result","busy");PacketDistributor.sendToPlayer(p,new AllianceStatePayload(out));return;}budget.putInt("CCECAllianceRequests",count+1);
        var server=p.getServer();var manager=CityManager.get(server.overworld());var cities=directory(manager);var realOwn=manager.getMemberCity(p.getUUID());var own=realOwn==null?null:cities.get(realOwn.getId());
        var data=AllianceSavedData.get(server);var book=data.book;long now=System.currentTimeMillis();if(book.reconcile(cities,now))data.setDirty();String result="ok";
        boolean mutation=c.action()!=AllianceBook.Action.VIEW&&c.action()!=AllianceBook.Action.DIRECTORY&&c.action()!=AllianceBook.Action.PLAYER;
        var prior=c.alliance()==null?null:book.get(c.alliance());var oldInvites=prior==null?Set.<UUID>of():new HashSet<>(prior.invitations.keySet());var oldProposals=prior==null?Set.<UUID>of():new HashSet<>(prior.proposals.keySet());
        if(mutation){
            boolean targetRequired=switch(c.action()){case INVITE,EXCLUDE,YES,NO,REPRESENTATIVE->true;default->false;};
            if(targetRequired&&c.target()==null)result="invalid";
            else if(c.action()==AllianceBook.Action.CHAT&&budget.getLong("CCECAllianceChatAfter")>now)result="busy";
            else {result=book.apply(c,p.getUUID(),own,cities,PlayerNames.name(p),now);if(result.equals("sent"))budget.putLong("CCECAllianceChatAfter",now+700);}
            if(Set.of("ok","created","sent","left","voted","proposed").contains(result))data.setDirty();
        }
        if(mutation&&Set.of("ok","proposed","voted","sent").contains(result)&&prior!=null){
            var inbox=com.example.examplemod.community.CommunityInbox.get(server);
            for(UUID city:prior.invitations.keySet())if(!oldInvites.contains(city)&&cities.containsKey(city))inbox.add(cities.get(city).owner(),c.request(),"alliance_invite",p.getUUID(),null,prior.id,prior.name,now);
            boolean newVote=prior.proposals.keySet().stream().anyMatch(id->!oldProposals.contains(id));boolean closed=oldProposals.stream().anyMatch(id->!prior.proposals.containsKey(id));
            if(newVote||closed)for(UUID city:prior.cities){var ref=cities.get(city);if(ref!=null)inbox.add(book.representative(prior,ref),c.request(),newVote?"council_proposal":"council_closed",p.getUUID(),null,prior.id,prior.name,now);}
            if(result.equals("sent"))for(UUID city:prior.cities){var ref=cities.get(city);if(ref!=null)for(UUID member:ref.members())if(!member.equals(p.getUUID()))inbox.add(member,c.request(),"alliance_message",p.getUUID(),null,prior.id,prior.name,now);}
        }
        out.putString("Result",result);out.putString("Action",c.action().name());
        if(own!=null){out.putUUID("OwnCity",own.id());out.putBoolean("Owner",own.owner().equals(p.getUUID()));out.putString("OwnCityName",own.name());var ownAlliance=book.city(own.id());if(ownAlliance!=null){out.putUUID("OwnAlliance",ownAlliance.id);out.put("OwnSummary",summary(ownAlliance,cities));}}
        var selected=c.alliance()==null?null:book.get(c.alliance());
        if(c.action()==AllianceBook.Action.CREATE&&own!=null)selected=book.city(own.id());
        if(c.action()==AllianceBook.Action.PLAYER){var target=c.target()==null?null:manager.getMemberCity(c.target());selected=target==null?null:book.city(target.getId());if(c.target()!=null)out.putUUID("Player",c.target());}
        else if(c.action()==AllianceBook.Action.VIEW&&c.target()!=null)selected=book.city(c.target());
        else if(c.action()!=AllianceBook.Action.DIRECTORY&&c.alliance()==null&&selected==null&&own!=null)selected=book.city(own.id());
        if(selected!=null){out.put("Alliance",summary(selected,cities));boolean member=c.action()!=AllianceBook.Action.PLAYER&&mayReadPrivate(selected,own,p.getUUID());out.putBoolean("Member",member);out.putBoolean("Council",member&&book.council(selected,own,p.getUUID()));out.putBoolean("Invited",own!=null&&selected.invitations.containsKey(own.id()));
            var members=new ListTag();for(UUID id:selected.cities){var city=cities.get(id);if(city==null)continue;var t=new CompoundTag();t.putUUID("Id",id);t.putString("Name",city.name());UUID representative=book.representative(selected,city);t.putUUID("Representative",representative);t.putString("RepresentativeName",PlayerNames.name(server,representative));members.add(t);}out.put("Cities",members);
            if(member){
                var proposals=new ListTag();for(var proposal:selected.proposals.values()){var t=new CompoundTag();t.putUUID("Id",proposal.id);t.putString("Kind",proposal.kind.name());t.putString("Text",proposal.text);if(proposal.target!=null){t.putUUID("Target",proposal.target);t.putString("TargetName",cities.get(proposal.target).name());}t.putInt("Yes",proposal.yes());t.putInt("No",proposal.no());t.putInt("Needed",proposal.majority());t.putLong("Expires",proposal.expires);t.putBoolean("CanVote",book.council(selected,own,p.getUUID())&&proposal.electorate.contains(own.id())&&!proposal.votes.containsKey(own.id()));proposals.add(t);}out.put("Proposals",proposals);
                var invites=new ListTag();selected.invitations.forEach((id,expiry)->{var t=new CompoundTag();t.putUUID("Id",id);t.putString("Name",cities.get(id).name());t.putLong("Expires",expiry);invites.add(t);});out.put("Outgoing",invites);
                int total=selected.chat.size(),page=Math.min(request.chatPage(),Math.max(0,(total-1)/40));out.putInt("ChatPage",page);out.putInt("ChatTotal",total);var messages=new ListTag();for(int i=Math.max(0,total-(page+1)*40);i<total-page*40;i++){var m=selected.chat.get(i);var t=new CompoundTag();t.putUUID("Id",m.id());t.putUUID("Author",m.author());t.putString("Name",m.name());t.putString("CityName",cities.containsKey(m.city())?cities.get(m.city()).name():"");t.putString("Text",m.text());t.putLong("Time",m.time());messages.add(t);}out.put("Chat",messages);
                if(own.owner().equals(p.getUUID())){var representatives=new ListTag();for(UUID id:own.members()){var t=new CompoundTag();t.putUUID("Id",id);t.putString("Name",PlayerNames.name(server,id));representatives.add(t);if(representatives.size()==64)break;}out.put("Representatives",representatives);}
            }
        }
        var incoming=new ListTag();if(own!=null)for(var a:book.all())if(a.invitations.containsKey(own.id())){incoming.add(summary(a,cities));if(incoming.size()==32)break;}out.put("Incoming",incoming);
        var list=new ArrayList<>(book.all());int page=Math.min(request.page(),Math.max(0,(list.size()-1)/32));out.putInt("DirectoryPage",page);out.putInt("DirectoryTotal",list.size());var directory=new ListTag();for(int i=page*32;i<Math.min(list.size(),(page+1)*32);i++)directory.add(summary(list.get(i),cities));out.put("Directory",directory);
        if(c.action()==AllianceBook.Action.VIEW||c.action()==AllianceBook.Action.DIRECTORY){var candidates=new ListTag();for(var city:cities.values())if(book.city(city.id())==null&&city.name().toLowerCase(Locale.ROOT).contains(c.text().toLowerCase(Locale.ROOT))){var t=new CompoundTag();t.putUUID("Id",city.id());t.putString("Name",city.name());candidates.add(t);if(candidates.size()==128)break;}out.put("Candidates",candidates);}
        PacketDistributor.sendToPlayer(p,new AllianceStatePayload(out));
    }
}
