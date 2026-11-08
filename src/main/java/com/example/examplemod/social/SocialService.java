package com.example.examplemod.social;
import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.auth.PlayerNames;
import com.example.examplemod.profile.ProfileSavedData;
import com.example.examplemod.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class SocialService {
    private SocialService(){}
    private static final Map<MinecraftServer,Map<UUID,long[]>> budgets=new WeakHashMap<>();
    private static boolean allow(ServerPlayer player,SocialBook.Action action) {
        var map=budgets.computeIfAbsent(player.getServer(),s->new HashMap<>());var v=map.computeIfAbsent(player.getUUID(),u->new long[4]);long now=net.minecraft.Util.getMillis(),window=now/1000;
        if(v[0]!=window){v[0]=window;v[1]=0;v[2]=0;v[3]=0;}
        if(action==SocialBook.Action.LIST||action==SocialBook.Action.READ)return ++v[1]<=6;
        if(action==SocialBook.Action.SEND)return ++v[2]<=2;
        return ++v[3]<=3;
    }
    private static boolean known(MinecraftServer s,UUID id) {
        return s.getPlayerList().getPlayer(id)!=null||ProfileSavedData.get(s).contains(id)
            ||s.getProfileCache()!=null&&s.getProfileCache().get(id).isPresent()
            ||java.nio.file.Files.isRegularFile(s.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id+".dat"));
    }
    public static void handle(ServerPlayer viewer,SocialRequestPayload r) {
        if(viewer instanceof FakePlayer)return;
        if(!allow(viewer,r.action())) {
            PacketDistributor.sendToPlayer(viewer,new SocialStatePayload(r.request(),"busy",0,r.target(),"",SocialBook.Relation.NONE,r.filter(),r.page(),0,List.of(),List.of(),false,0,0));return;
        }
        var s=viewer.getServer();var data=SocialSavedData.get(s);var book=data.book;UUID actor=viewer.getUUID();
        var cities=com.example.examplemod.city.CityManager.get(s.overworld());
        String result="ok";
        if(r.action()!=SocialBook.Action.LIST && (r.target()==null||!known(s,r.target())))result="unknown";
        else if(r.action()!=SocialBook.Action.LIST) {
            result=book.apply(actor,r.target(),r.action(),r.revision(),r.text(),r.before(),System.currentTimeMillis(),r.request());
            if(result.equals("ok")) {
                data.setDirty();
                if(r.action()==SocialBook.Action.CANCEL||r.action()==SocialBook.Action.BLOCK||r.action()==SocialBook.Action.DECLINE){var inbox=com.example.examplemod.community.CommunityInbox.get(s);inbox.resolvePeer(actor,r.target(),"friend_request","friend_request_closed");inbox.resolvePeer(r.target(),actor,"friend_request","friend_request_closed");if(r.action()==SocialBook.Action.BLOCK){var notices=com.example.examplemod.community.CommunityData.get(s);notices.outbox.removeIf(n->n.kind().equals("MESSAGE")&&((n.owner().equals(actor)&&n.peer().equals(r.target()))||(n.owner().equals(r.target())&&n.peer().equals(actor))));notices.setDirty();}}

                if(r.action()==SocialBook.Action.READ)for(var message:book.dialogs.getOrDefault(SocialBook.Pair.of(actor,r.target()),List.of()))if(message.sender().equals(r.target())&&message.id()<=r.before())com.example.examplemod.community.CommunityInbox.get(s).read(actor,message.nonce());
                if(r.action()==SocialBook.Action.ACCEPT||r.action()==SocialBook.Action.DECLINE)com.example.examplemod.community.CommunityInbox.get(s).readPeer(actor,r.target(),"friend_request");
                if(r.action()==SocialBook.Action.REQUEST||r.action()==SocialBook.Action.SEND||r.action()==SocialBook.Action.ACCEPT)com.example.examplemod.community.CommunityInbox.get(s).add(r.target(),r.request(),r.action()==SocialBook.Action.REQUEST?"friend_request":r.action()==SocialBook.Action.SEND?"message":"friend_accepted",actor,null,null,PlayerNames.name(s,actor),System.currentTimeMillis());
                if(r.action()==SocialBook.Action.SEND||r.action()==SocialBook.Action.REQUEST||r.action()==SocialBook.Action.ACCEPT) {
                    var peer=s.getPlayerList().getPlayer(r.target());if(peer!=null)notify(peer,actor,r.action()==SocialBook.Action.SEND?"new_message":r.action()==SocialBook.Action.REQUEST?"new_request":"accepted");
                }
                if(r.action()==SocialBook.Action.SEND)com.example.examplemod.community.CommunityData.get(s).enqueue(r.target(),actor,"MESSAGE",r.text(),s.getPlayerList().getPlayer(r.target())!=null);
            }
        }
        if(result.equals("ok")&&r.action()==SocialBook.Action.ACCEPT)com.example.examplemod.auth.ServerTelemetryBridge.emit("FRIEND_ACCEPTED",actor,null,"");
        UUID target=r.target();if(target!=null&&!known(s,target))target=null;
        Set<UUID> ids=new HashSet<>();
        if(r.filter()==2)book.dialogs.keySet().stream().filter(p->p.includes(actor)).forEach(p->ids.add(p.other(actor)));else ids.addAll(book.contacts(actor));
        List<UUID> visible=ids.stream().filter(id->switch(r.filter()){
            case 0->book.relation(actor,id)==SocialBook.Relation.FRIEND;
            case 1->book.relation(actor,id)==SocialBook.Relation.INCOMING||book.relation(actor,id)==SocialBook.Relation.OUTGOING;
            case 2->true;default->book.relation(actor,id)==SocialBook.Relation.BLOCKED;
        }).filter(id->PlayerNames.name(s,id).toLowerCase(Locale.ROOT).contains(r.query().toLowerCase(Locale.ROOT))).sorted(Comparator.<UUID>comparingInt(id->s.getPlayerList().getPlayer(id)!=null?0:1).thenComparing(id->PlayerNames.name(s,id),String.CASE_INSENSITIVE_ORDER).thenComparing(UUID::toString)).toList();
        int page=Math.min(r.page(),Math.max(0,(visible.size()-1)/16));List<SocialStatePayload.Contact> contacts=new ArrayList<>();
        for(UUID id:visible.subList(page*16,Math.min(visible.size(),page*16+16)))contacts.add(new SocialStatePayload.Contact(id,PlayerNames.name(s,id),s.getPlayerList().getPlayer(id)!=null,book.relation(actor,id),book.unread(actor,id),cities.getMemberCity(id)==null?"":cities.getMemberCity(id).getName(),cities.getMemberCity(id)==null?"":cities.getMemberCity(id).getMember(id).getRole().name()));
        var messages=target==null?List.<SocialBook.Message>of():book.messages(actor,target,r.before());
        boolean older=target!=null&&!messages.isEmpty()&&book.dialogs.getOrDefault(SocialBook.Pair.of(actor,target),List.of()).stream().anyMatch(m->m.id()<messages.getFirst().id());
        int incoming=(int)book.requests.stream().filter(e->e.to().equals(actor)).count();int unread=book.dialogs.keySet().stream().filter(p->p.includes(actor)).mapToInt(p->book.unread(actor,p.other(actor))).sum();
        PacketDistributor.sendToPlayer(viewer,new SocialStatePayload(r.request(),result,book.revision(actor),target,target==null?"":PlayerNames.name(s,target),target==null?SocialBook.Relation.NONE:book.relation(actor,target),r.filter(),page,visible.size(),contacts,messages,older,incoming,unread,target==null||cities.getMemberCity(target)==null?"":cities.getMemberCity(target).getName(),target==null||cities.getMemberCity(target)==null?"":cities.getMemberCity(target).getMember(target).getRole().name()));
    }
    public static void notify(ServerPlayer player,UUID peer,String key) {
        var text=Component.translatable("gui.createcityexpansioncore.social."+key,PlayerNames.name(player.getServer(),peer));
        text.setStyle(Style.EMPTY.withColor(0xD5AF6A).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/ccec profile "+peer)));
        if(com.example.examplemod.community.CommunityData.get(player.getServer()).muted(player.getUUID(),peer))return;
        if(!key.equals("new_message"))player.sendSystemMessage(text);PacketDistributor.sendToPlayer(player,new CommunityNoticePayload(peer,PlayerNames.name(player.getServer(),peer),key));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {if(e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
        var b=SocialSavedData.get(p.getServer()).book;UUID id=p.getUUID();int incoming=(int)b.requests.stream().filter(r->r.to().equals(id)).count();int unread=b.dialogs.keySet().stream().filter(pair->pair.includes(id)).mapToInt(pair->b.unread(id,pair.other(id))).sum();
        if(incoming+unread>0)p.sendSystemMessage(Component.translatable("gui.createcityexpansioncore.social.login_notice",incoming,unread));
    }}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {if(e.getEntity() instanceof ServerPlayer p){var m=budgets.get(p.getServer());if(m!=null)m.remove(p.getUUID());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){budgets.remove(e.getServer());}
}
