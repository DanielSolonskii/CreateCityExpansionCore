package com.example.examplemod.client;
import com.example.examplemod.network.ClientTelemetryPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Bounded frame sample reservoir; no logs, chats, coordinates or player input. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class ClientTelemetry {
 private ClientTelemetry(){}
 private static final int[] frames=new int[4096];private static int count,seen,stalls,tiles,hits,maxWait;private static long frameAt,nextSend;private static boolean context;
 private static boolean enabled(){return !Boolean.getBoolean("ccec.telemetry.disabled");}
 public static void diskHit(){if(hits<100000)hits++;}
 public static void tile(long millis){if(tiles<100000)tiles++;maxWait=Math.max(maxWait,(int)Math.min(120000,Math.max(0,millis)));}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){var mc=Minecraft.getInstance();long now=System.nanoTime();
  if(!enabled()||mc.player==null||mc.level==null){frameAt=0;return;}
  if(frameAt!=0){int ms=(int)Math.min(60000,(now-frameAt)/1000000);if(ms>=100)stalls=Math.min(100000,stalls+1);if(count<frames.length)frames[count++]=ms;else {int index=java.util.concurrent.ThreadLocalRandom.current().nextInt(++seen+frames.length);if(index<frames.length)frames[index]=ms;}}
  frameAt=now;
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(!enabled()||mc.player==null||mc.getConnection()==null){context=false;return;}
  if(!context){context=true;String attempt=System.getProperty("ccec.launchAttempt","");try{UUID.fromString(attempt);PacketDistributor.sendToServer(new ClientTelemetryPayload(0,attempt,0,0,0,0,0,0,-1));}catch(IllegalArgumentException ignored){}}
  long now=net.minecraft.Util.getMillis();if(nextSend==0){nextSend=now+30000;return;}if(now<nextSend)return;nextSend=now+30000;
  int[] sorted=Arrays.copyOf(frames,count);Arrays.sort(sorted);int p95=count==0?0:sorted[Math.min(count-1,(int)(count*.95))];
  var info=mc.getConnection().getPlayerInfo(mc.player.getUUID());int ping=info==null?-1:Math.clamp(info.getLatency(),0,60000);
  PacketDistributor.sendToServer(new ClientTelemetryPayload(1,"",Math.clamp(Minecraft.getInstance().getFps(),0,2000),p95,stalls,tiles,hits,maxWait,ping));
  count=seen=stalls=tiles=hits=maxWait=0;
 }
 @SubscribeEvent public static void screen(ScreenEvent.Opening event){var mc=Minecraft.getInstance();if(!enabled()||mc.player==null||mc.getConnection()==null)return;
  var screen=event.getNewScreen();String surface=screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen?"MAP":screen instanceof com.example.examplemod.client.screen.PlayerProfileScreen?"PROFILE":screen instanceof com.example.examplemod.client.screen.SocialHubScreen?"COMMUNITY":screen instanceof com.example.examplemod.client.screen.CommunityScreen?"COMMUNITY":screen instanceof com.example.examplemod.client.screen.SocialScreen?"SOCIAL":screen instanceof com.example.examplemod.client.screen.CityPylonScreen?"PYLON":"";
  if(!surface.isEmpty())PacketDistributor.sendToServer(new ClientTelemetryPayload(2,surface,0,0,0,0,0,0,-1));
 }
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){context=false;frameAt=nextSend=0;count=seen=stalls=tiles=hits=maxWait=0;}
}
