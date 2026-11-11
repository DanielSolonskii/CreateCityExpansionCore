package com.example.examplemod.client;
import com.example.examplemod.onboarding.OnboardingProgress;
import com.example.examplemod.network.*;
import com.example.examplemod.client.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.network.OnboardingRequestPayload.Action;
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class OnboardingClient {
 private OnboardingClient(){}
 private static Object world,previousScreen;
 private static OnboardingProgress progress=OnboardingProgress.fresh();
 private static boolean ready,defaultsAttempted;
 private static String city="",role="";
 private static long joined,lastTick,nextState,nextHint,screenSince,dangerUntil,projectUntil;
 private static int active=-1;private static long displayed;
 public static void accept(OnboardingStatePayload p){
  var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||p.version()!=OnboardingProgress.VERSION)return;ensureWorld();
  progress=new OnboardingProgress(progress.seen()|p.seen(),progress.completed()|p.completed(),progress.skipped()||p.skipped());city=p.city();role=p.role();ready=true;
  if(progress.skipped())active=-1;if(p.openHelp())openHelp();
 }
 private static void ensureWorld(){var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;progress=OnboardingProgress.fresh();ready=false;active=-1;city=role="";joined=net.minecraft.Util.getMillis();lastTick=joined;nextState=nextHint=screenSince=projectUntil=0;previousScreen=null;displayed=0;}}
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){world=null;ready=false;active=-1;}
 public static void projectsVisible(){projectUntil=net.minecraft.Util.getMillis()+300;}
 public static void openHelp(){var mc=Minecraft.getInstance();if(mc.player!=null)mc.setScreen(new OnboardingHelpScreen(mc.screen));}
 public static void later(){active=-1;displayed=0;nextHint=net.minecraft.Util.getMillis()+45000;}
 public static void acknowledge(){if(active>=0){send(Action.SEEN,active);active=-1;displayed=0;nextHint=net.minecraft.Util.getMillis()+30000;}}
 public static void skip(){send(Action.SKIP,0);active=-1;}
 public static boolean skipped(){return progress.skipped();}
 public static boolean done(int id){return ((progress.completed()|progress.seen())&(1<<id))!=0;}
 private static void send(Action action,int id){
  if(action==Action.SEEN)progress=progress.seen(id);if(action==Action.COMPLETE)progress=progress.completed(id);if(action==Action.SKIP)progress=progress.skip();
  PacketDistributor.sendToServer(new OnboardingRequestPayload(action,id));
 }
 private static void completed(int id){if((progress.completed()&(1<<id))==0)send(Action.COMPLETE,id);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();
  if(!defaultsAttempted&&mc.options!=null){defaultsAttempted=true;try{OneTimeClientDefaults.apply(mc.gameDirectory.toPath(),()->{mc.options.menuBackgroundBlurriness().set(0);mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.save();});}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("CCEC client defaults were not applied; retry on next launch: {}",failure.getClass().getSimpleName());}}
  if(mc.player==null||mc.level==null||mc.getConnection()==null)return;ensureWorld();long now=net.minecraft.Util.getMillis(),elapsed=Math.min(100,Math.max(0,now-lastTick));lastTick=now;
  if(mc.screen!=previousScreen){previousScreen=mc.screen;screenSince=now;nextState=Math.min(nextState,now+2000);}
  if(mc.screen==null)while(!OnboardingKeys.HELP.isUnbound()&&OnboardingKeys.HELP.consumeClick())openHelp();
  if(now>=nextState){nextState=now+(ready?20000:5000);send(Action.STATE,0);}
  if(!ready)return;
  if(mc.screen instanceof PolishedWorldMapScreen)completed(0);
  if(mc.screen instanceof SocialHubScreen||mc.screen instanceof CommunityScreen||mc.screen instanceof PlayerProfileScreen)completed(2);
  if(mc.player.hurtTime>0||mc.player.getHealth()<=6||mc.player.isOnFire()||mc.player.getAirSupply()<250||mc.player.isDeadOrDying())dangerUntil=now+12000;
  if(progress.skipped()||mc.options.hideGui||mc.isPaused()||now<dangerUntil)return;
  if(active>=0){
   if((progress.completed()&(1<<active))!=0||!context(active,now)){active=-1;displayed=0;nextHint=now+3000;}
   else if((displayed+=elapsed)>=12000)acknowledge();
  }
  if(active<0&&now-joined>=12000&&now>=nextHint){for(int id=0;id<OnboardingProgress.COUNT;id++)if(progress.eligible(id)&&context(id,now)){active=id;displayed=0;break;}}
 }
 private static boolean context(int id,long now){var screen=Minecraft.getInstance().screen;return switch(id){
  case 0->screen==null;
  case 1->screen instanceof PolishedWorldMapScreen&&now-screenSince>2500&&projectUntil<now;
  case 2->screen==null&&done(0);
  case 3->CityPylonWorkspace.controller()!=null&&now-screenSince>2500;
  case 4->screen==null&&!city.isBlank();
  case 5->projectUntil>=now&&now-screenSince>2500;
  case 6->screen==null&&!city.isBlank()&&done(4);
  default->false;};}
 public static Component key(String name){var mc=Minecraft.getInstance();for(var key:mc.options.keyMappings)if(key.getName().equals(name))return key.isUnbound()?Component.translatable("gui.createcityexpansioncore.onboarding.unbound"):key.getTranslatedKeyMessage();return Component.translatable("gui.createcityexpansioncore.onboarding.unbound");}
 public static Component title(int id){return Component.translatable("gui.createcityexpansioncore.onboarding."+id+".title");}
 public static Component body(int id){if(id==4&&city.isBlank())return Component.translatable("gui.createcityexpansioncore.onboarding.no_city");return Component.translatable("gui.createcityexpansioncore.onboarding."+id+".body",key("key.createcityexpansioncore.open_world_map"),key("key.createcityexpansioncore.open_player_profile"),Component.literal(city),Component.translatable("gui.createcityexpansioncore.onboarding.role."+role.toLowerCase(Locale.ROOT)),key("key.createcityexpansioncore.city_boundaries"));}
 private static boolean drawable(){var mc=Minecraft.getInstance();return ready&&active>=0&&!progress.skipped()&&!mc.options.hideGui&&net.minecraft.Util.getMillis()>=dangerUntil;}
 @SubscribeEvent public static void hud(RenderGuiEvent.Post event){if(Minecraft.getInstance().screen==null&&drawable())OnboardingHelpScreen.renderCard(event.getGuiGraphics(),active,displayed,false);}
 @SubscribeEvent public static void screen(ScreenEvent.Render.Post event){if(!(event.getScreen() instanceof OnboardingHelpScreen)&&drawable()&&context(active,net.minecraft.Util.getMillis()))OnboardingHelpScreen.renderCard(event.getGuiGraphics(),active,displayed,true);}
 @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event){
  if(!drawable()||event.getScreen() instanceof OnboardingHelpScreen||!context(active,net.minecraft.Util.getMillis())||event.getButton()!=0)return;
  int action=OnboardingHelpScreen.cardAction(active,displayed,event.getMouseX(),event.getMouseY());if(action<0)return;
  event.setCanceled(true);if(action==0)acknowledge();else if(action==1)later();else skip();
 }
}
