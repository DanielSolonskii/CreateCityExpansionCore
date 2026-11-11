package com.example.examplemod.city;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.*;
/** Bounded mutation receipts and latest offline membership notification; never a Telegram outbox. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class CityMembershipEvents extends SavedData {
    public record Receipt(UUID actor,UUID request,UUID city,UUID target,String action){}
    public record Notice(String kind,String city){}
    final LinkedHashSet<Receipt> receipts=new LinkedHashSet<>();
    final LinkedHashMap<UUID,Notice> notices=new LinkedHashMap<>();
    public static CityMembershipEvents get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CityMembershipEvents::new,CityMembershipEvents::load,null),"ccec_membership_events");}
    public boolean done(Receipt r){return receipts.contains(r);}
    public void record(Receipt r){receipts.add(r);while(receipts.size()>1024)receipts.remove(receipts.iterator().next());setDirty();}
    public void notify(MinecraftServer s,UUID player,String kind,String city){var online=s.getPlayerList().getPlayer(player);var notice=new Notice(kind,city);if(online!=null)show(online,notice);else{notices.put(player,notice);while(notices.size()>4096)notices.remove(notices.keySet().iterator().next());setDirty();}}
    private static void show(ServerPlayer p,Notice n){p.sendSystemMessage(Component.translatable("gui.createcityexpansioncore.community.membership.notice."+n.kind,n.city));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){var d=get(p.getServer());var n=d.notices.remove(p.getUUID());if(n!=null){show(p,n);d.setDirty();}}}
    public static CityMembershipEvents load(CompoundTag root,HolderLookup.Provider lookup){if(root.getInt("Version")>1)throw new IllegalStateException("Unsupported membership event version");var d=new CityMembershipEvents();for(Tag raw:root.getList("Receipts",10)){var t=(CompoundTag)raw;if(d.receipts.size()<1024&&t.hasUUID("Actor")&&t.hasUUID("Request")&&t.hasUUID("City")&&t.hasUUID("Target"))d.receipts.add(new Receipt(t.getUUID("Actor"),t.getUUID("Request"),t.getUUID("City"),t.getUUID("Target"),t.getString("Action")));}for(Tag raw:root.getList("Notices",10)){var t=(CompoundTag)raw;if(d.notices.size()<4096&&t.hasUUID("Player")&&Set.of("kicked","owner").contains(t.getString("Kind")))d.notices.put(t.getUUID("Player"),new Notice(t.getString("Kind"),t.getString("City")));}return d;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){root.putInt("Version",1);var receipts=new ListTag();for(var r:this.receipts){var t=new CompoundTag();t.putUUID("Actor",r.actor);t.putUUID("Request",r.request);t.putUUID("City",r.city);t.putUUID("Target",r.target);t.putString("Action",r.action);receipts.add(t);}root.put("Receipts",receipts);var notices=new ListTag();this.notices.forEach((id,n)->{var t=new CompoundTag();t.putUUID("Player",id);t.putString("Kind",n.kind);t.putString("City",n.city);notices.add(t);});root.put("Notices",notices);return root;}
}
