package com.example.examplemod.onboarding;
import com.example.examplemod.city.*;
import com.example.examplemod.network.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="createcityexpansioncore")
public final class OnboardingServer {
 private OnboardingServer(){}
 public static void request(ServerPlayer player,OnboardingRequestPayload request){
  if(player instanceof FakePlayer)return;var budget=player.getPersistentData();long window=net.minecraft.Util.getMillis()/1000;
  if(budget.getLong("CCECGuideWindow")!=window){budget.putLong("CCECGuideWindow",window);budget.putInt("CCECGuideRequests",0);}
  int count=budget.getInt("CCECGuideRequests");if(count>=8)return;budget.putInt("CCECGuideRequests",count+1);
  var data=OnboardingSavedData.get(player.getServer());var current=data.progress(player.getUUID());
  var next=switch(request.action()){case STATE->current;case SEEN->current.seen(request.hint());case COMPLETE->current.completed(request.hint());case SKIP->current.skip();};
  data.put(player.getUUID(),next);send(player,false);
 }
 public static void send(ServerPlayer player,boolean open){
  var data=OnboardingSavedData.get(player.getServer());var progress=data.progress(player.getUUID());data.put(player.getUUID(),progress);
  var city=CityManager.get(player.getServer().overworld()).getMemberCity(player.getUUID());var member=city==null?null:city.getMember(player.getUUID());
  PacketDistributor.sendToPlayer(player,new OnboardingStatePayload(OnboardingProgress.VERSION,progress.seen(),progress.completed(),progress.skipped(),city==null?"":city.getName(),member==null?"":member.getRole().name(),open));
 }
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p&&!(p instanceof FakePlayer))send(p,false);}
 @SubscribeEvent public static void commands(RegisterCommandsEvent event){event.getDispatcher().register(Commands.literal("ccec").then(Commands.literal("guide").requires(s->s.getEntity() instanceof ServerPlayer).executes(c->{var p=c.getSource().getPlayerOrException();if(!(p instanceof FakePlayer))send(p,true);return 1;})));}
}
