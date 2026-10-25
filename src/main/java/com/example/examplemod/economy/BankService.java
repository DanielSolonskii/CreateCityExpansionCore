package com.example.examplemod.economy;
import com.example.examplemod.auth.PlayerNames;
import com.example.examplemod.city.*;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.Project;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid="createcityexpansioncore")
public final class BankService {
    private static final Map<MinecraftServer,Map<UUID,long[]>> budgets=new WeakHashMap<>();
    private record Directory(long until,List<CityInviteDirectory.Candidate> candidates){}
    private static final Map<MinecraftServer,Directory> directories=new WeakHashMap<>();
    private static List<CityInviteDirectory.Candidate> known(ServerPlayer player){var s=player.getServer();long now=net.minecraft.Util.getMillis();var cached=directories.get(s);if(cached==null||cached.until<now){cached=new Directory(now+30000,List.copyOf(CityInviteDirectory.knownProfiles(s,CityManager.get(s.overworld()),player)));directories.put(s,cached);}return cached.candidates;}
    public static void handle(ServerPlayer player,BankRequest request){if(player instanceof net.neoforged.neoforge.common.util.FakePlayer)return;var s=player.getServer();UUID owner=player.getUUID();var out=new CompoundTag();out.putUUID("Request",request.request());out.putUUID("Owner",owner);long window=net.minecraft.Util.getMillis()/1000;var budget=budgets.computeIfAbsent(s,k->new HashMap<>()).computeIfAbsent(owner,k->new long[3]);if(budget[0]!=window){budget[0]=window;budget[1]=budget[2]=0;}boolean view=request.action()==BankBook.Action.VIEW;int slot=view?1:2;if(++budget[slot]>(view?4:3)){out.putString("Result","busy");reply(player,out);return;}
        var economy=EconomyManager.get(s.overworld());var bank=economy.bank();String result="ok";
        if(request.action()==BankBook.Action.RESOLVE){var matches=known(player).stream().filter(c->request.peer()!=null?c.id().equals(request.peer()):c.name().equalsIgnoreCase(request.text())).toList();if(matches.size()!=1)result=matches.isEmpty()?"unknown":"ambiguous";else{out.putUUID("Resolved",matches.getFirst().id());out.putString("ResolvedName",matches.getFirst().name());}}
        else if(!view){if((request.action()==BankBook.Action.TRANSFER||request.action()==BankBook.Action.REQUEST)&&request.peer()!=null&&!request.peer().equals(owner)&&known(player).stream().noneMatch(c->c.id().equals(request.peer())))result="unknown";else result=bank.execute(owner,new BankBook.Command(request.request(),request.revision(),request.action(),request.source(),request.destination(),request.peer(),request.amount(),request.goal(),request.text(),request.kind()),System.currentTimeMillis());}
        out.putString("Result",result);out.putBoolean("Compact",view&&request.compact());out.putLong("Revision",bank.revision(owner));out.putLong("Wallet",economy.wallet(owner));out.putBoolean("HasCity",CityManager.get(s.overworld()).getMemberCity(owner)!=null);out.putLong("Maximum",com.example.examplemod.Config.ECONOMY_MAX_TRANSACTION.get());
        if(!out.getBoolean("Compact")){
            var accounts=new ListTag();for(var a:bank.accounts(owner)){var t=new CompoundTag();t.putUUID("Id",a.id());t.putString("Name",a.name());t.putString("Kind",a.kind().name());t.putLong("Balance",a.balance());t.putLong("Goal",a.goal());accounts.add(t);}out.put("Accounts",accounts);
            var payments=new ListTag();for(var p:bank.requests(owner)){var t=new CompoundTag();t.putUUID("Id",p.id());UUID peer=p.owner().equals(owner)?p.payer():p.owner();t.putUUID("Peer",peer);t.putString("Name",PlayerNames.name(s,peer));t.putBoolean("Incoming",p.payer().equals(owner));t.putLong("Amount",p.amount());t.putString("Note",p.note());t.putString("State",p.expires()<=System.currentTimeMillis()&&p.state()==BankBook.State.OPEN?"EXPIRED":p.state().name());t.putLong("Created",p.created());payments.add(t);}out.put("Requests",payments);
            var history=new ListTag();for(var e:bank.history(owner)){var t=new CompoundTag();t.putUUID("Id",e.id());t.putLong("Time",e.time());t.putString("Kind",e.kind());t.putLong("Amount",e.amount());t.putString("Note",e.note());if(e.peer()!=null){t.putUUID("Peer",e.peer());t.putString("Name",PlayerNames.name(s,e.peer()));}history.add(t);}out.put("History",history);
            var reserves=new ListTag();long reserved=0;for(var p:economy.projects().all())if(p.author().equals(owner)&&p.funding()==Project.Funding.PERSONAL&&p.reserved()>0){reserved=EconomyMath.saturatingAdd(reserved,p.reserved(),Long.MAX_VALUE);if(reserves.size()<128){var t=new CompoundTag();t.putUUID("Id",p.id());t.putString("Title",p.title());t.putLong("Amount",p.reserved());reserves.add(t);}}out.putLong("ProjectReserved",reserved);out.put("Reserves",reserves);
        }reply(player,out);
    }
    private static void reply(ServerPlayer p,CompoundTag out){PacketDistributor.sendToPlayer(p,new BankState(out));}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){budgets.remove(e.getServer());directories.remove(e.getServer());}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)directories.remove(p.getServer());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){var b=budgets.get(p.getServer());if(b!=null)b.remove(p.getUUID());directories.remove(p.getServer());}}
}
