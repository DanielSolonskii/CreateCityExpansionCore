package com.example.examplemod.client;

import com.example.examplemod.client.screen.*;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Server availability snapshots only. The animation clock never computes reward dates. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class DailyRewardsClient {
    private static CompoundTag state=new CompoundTag(),queued;
    private static UUID pending;private static long sent,nextPoll;private static boolean claimRequest,offered,autoOpen,forced;
    private DailyRewardsClient(){}
    public static CompoundTag state(){return state;}
    public static boolean waiting(){return pending!=null;}
    public static boolean available(){return state.getBoolean("Available");}
    public static void accept(DailyRewardsState payload){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){queued=payload.data();return;}apply(payload.data());}
    private static void apply(CompoundTag incoming){
        var mc=Minecraft.getInstance();if(!incoming.hasUUID("Owner")||!mc.player.getUUID().equals(incoming.getUUID("Owner")))return;
        boolean matched=pending!=null&&incoming.hasUUID("Request")&&pending.equals(incoming.getUUID("Request"));
        boolean external=incoming.getBoolean("Auto")||incoming.getBoolean("Open")||incoming.getString("Result").equals("reset");if(!matched&&!external)return;
        if(matched){pending=null;if(claimRequest)CommunityFx.sound(incoming.getString("Result").equals("received")?"success":"error");}
        state=incoming;if(!offered){autoOpen=available();if(!available()&&incoming.contains("Today"))offered=true;}if(incoming.getBoolean("Open"))forced=true;
    }
    public static void refresh(){if(pending!=null||Minecraft.getInstance().getConnection()==null)return;request(false,0);}
    public static void claim(int wood){if(pending==null&&available())request(true,wood);}
    private static void request(boolean claim,int wood){pending=UUID.randomUUID();sent=net.minecraft.Util.getMillis();nextPoll=sent+30000;claimRequest=claim;PacketDistributor.sendToServer(new DailyRewardsRequest(pending,claim,state.getLong("Today"),wood));}
    public static void open(Screen parent){offered=true;autoOpen=forced=false;Minecraft.getInstance().setScreen(new DailyRewardsScreen(parent));refresh();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getConnection()==null)return;
        if(queued!=null){var incoming=queued;queued=null;apply(incoming);}long now=net.minecraft.Util.getMillis();
        if(pending!=null&&now-sent>8000){pending=null;state.putString("Result","timeout");nextPoll=now+2000;}
        boolean inUI=mc.screen instanceof DailyRewardsScreen||mc.screen instanceof SocialHubScreen;
        if(mc.player.tickCount>=30&&now>=nextPoll&&(inUI||!offered&&!state.contains("Today")))refresh();
        if(mc.screen instanceof DailyRewardsScreen){offered=true;forced=autoOpen=false;return;}
        // Defer behind every other GUI/loading overlay, non-player cameras and unsafe moments.
        if((forced||autoOpen&&available())&&mc.player.tickCount>=60&&mc.screen==null&&mc.getOverlay()==null&&mc.getCameraEntity()==mc.player&&!mc.player.isDeadOrDying()&&!mc.player.isSpectator()&&!mc.options.hideGui&&mc.player.hurtTime==0&&!mc.player.isOnFire()&&mc.player.getAirSupply()>=250)open(null);
    }
    private static void clear(){state=new CompoundTag();queued=null;pending=null;sent=nextPoll=0;claimRequest=offered=autoOpen=forced=false;}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
}
