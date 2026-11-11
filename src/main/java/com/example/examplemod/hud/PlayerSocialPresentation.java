package com.example.examplemod.hud;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Presentation only. Account names, UUIDs, teams and message contents remain authoritative. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class PlayerSocialPresentation {
    private PlayerSocialPresentation() {}
    private record Cached(Component city,Component header) {}
    private static final Map<ServerPlayer,Cached> CACHE=new WeakHashMap<>();
    private static City city(ServerPlayer player){return CityManager.get(player.server.overworld()).getCities().stream().filter(c->c.getMember(player.getUUID())!=null).sorted(Comparator.comparing(c->c.getId().toString())).findFirst().orElse(null);}
    private static Component badge(ServerPlayer player,boolean compact){
        var city=city(player);if(city==null)return Component.empty();String full=city.getName();String label=compact&&full.codePointCount(0,full.length())>20?full.substring(0,full.offsetByCodePoints(0,19))+"…":full;
        var role=city.getMember(player.getUUID()).getRole();
        return TabCityLabel.create(city.getId().toString(),label,city.getColorRgb()).copy().withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(full).append(" · ").append(Component.translatable("role.createcityexpansioncore."+role.name().toLowerCase(Locale.ROOT))))));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void chatName(PlayerEvent.NameFormat event){if(event.getEntity() instanceof ServerPlayer player){var city=badge(player,false);var name=event.getDisplayname().copy();if(!city.getString().isEmpty())name.append(Component.literal(" · ").withStyle(style->style.withColor(0x8C999B))).append(city);event.setDisplayname(name);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tabName(PlayerEvent.TabListNameFormat event){if(event.getEntity() instanceof ServerPlayer player){Component base=event.getDisplayName();if(base==null)base=player.getName();var label=base.copy();var city=badge(player,true);if(!city.getString().isEmpty())label.append(Component.literal(" · ").withStyle(style->style.withColor(0x8C999B))).append(city);event.setDisplayName(label);}}
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();if(server.getTickCount()%20!=0)return;
        long dayTime=Math.floorMod(server.overworld().getDayTime()+6000,24000);String clock=String.format(Locale.ROOT,"%02d:%02d",dayTime/1000,dayTime%1000*60/1000);
        var header=Component.translatableWithFallback(TabCityLabel.HEADER,"RE:CREATED").withStyle(style->style.withColor(0xE5E9E7)).append(Component.literal("\n").append(Component.translatable("gui.createcityexpansioncore.tab.online",server.getPlayerCount(),server.getMaxPlayers(),clock)).withStyle(style->style.withColor(0x8C999B)));
        var footer=Component.empty();
        for(var player:server.getPlayerList().getPlayers()){
            var city=badge(player,false);var identity=Component.empty().append(city);var previous=CACHE.get(player);
            if(previous==null||!previous.city().equals(identity)){player.refreshDisplayName();player.refreshTabListName();}
            if(previous==null||!previous.header().equals(header))player.setTabListHeaderFooter(header,footer);
            CACHE.put(player,new Cached(identity,header.copy()));
        }
    }
}
