package com.example.examplemod.onboarding;
import com.example.examplemod.client.OneTimeClientDefaults;
import com.example.examplemod.client.screen.OnboardingLayout;
import com.example.examplemod.network.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.nio.file.*;
import java.util.*;
public final class OnboardingCheck {
 private static int checks;private static void require(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  UUID id=UUID.randomUUID();var data=new OnboardingSavedData();require(data.progress(id).eligible(0),"new and existing unrecorded players enter v1");
  var p=data.progress(id).seen(0).completed(1);data.put(id,p);var loaded=OnboardingSavedData.load(data.save(new CompoundTag(),null),null);
  require(!loaded.progress(id).eligible(0)&&!loaded.progress(id).eligible(1)&&loaded.progress(id).eligible(2),"seen/completed survive reconnect and reload");
  data.put(id,p.skip());require(OnboardingSavedData.load(data.save(new CompoundTag(),null),null).progress(id).skipped(),"skip persists");
  require(p.seen(-1).equals(p)&&p.completed(999).equals(p),"invalid hint indices do not alter flags");
  require(new OnboardingProgress(-1,-1,false).seen()==OnboardingProgress.MASK,"unknown bits masked");
  require(loaded.progress(UUID.randomUUID()).eligible(0),"players isolated by UUID");
  for(int w:new int[]{320,426,640,960})for(int h:new int[]{180,240,360,540})for(boolean screen:new boolean[]{false,true}){
   var r=OnboardingLayout.card(w,h,5,screen);require(r.x()>=0&&r.y()>=0&&r.x()+r.width()<=w&&r.y()+r.height()<=h,"card stays on screen");
   for(int i=0;i<3;i++){var a=OnboardingLayout.action(r,i);require(a.x()>=r.x()&&a.x()+a.width()<=r.x()+r.width()&&a.y()+a.height()<=r.y()+r.height(),"action in card");require(a.contains(a.x()+1,a.y()+1),"shared hit box");}
  }
  var root=Files.createTempDirectory("ccec-client-defaults");var options=root.resolve("options.txt");String original="guiScale:4\nmenuBackgroundBlurriness:5\nkey_key.attack:key.mouse.left\nmusic:0.4\n";Files.writeString(options,original);
  int[] invoked={0};require(OneTimeClientDefaults.apply(root,()->{invoked[0]++;try{Files.writeString(options,original.replace("guiScale:4","guiScale:2").replace("menuBackgroundBlurriness:5","menuBackgroundBlurriness:0"));}catch(Exception e){throw new RuntimeException(e);}}),"existing client receives defaults");
  require(Files.readString(root.resolve("config/ccec-options-before-defaults-v1.txt")).equals(original),"original options backed up");
  Files.writeString(options,"guiScale:3\nmenuBackgroundBlurriness:2\n");require(!OneTimeClientDefaults.apply(root,()->invoked[0]++)&&invoked[0]==1,"later manual settings not overwritten");
  for(var file:List.of(root.resolve("config/ccec-client-defaults-v1.done"),root.resolve("config/ccec-options-before-defaults-v1.txt"),options))Files.delete(file);Files.delete(root.resolve("config"));Files.delete(root);
  var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);try{
   var state=new OnboardingStatePayload(1,3,4,false,"City","BUILDER",true);OnboardingStatePayload.STREAM_CODEC.encode(buffer,state);require(OnboardingStatePayload.STREAM_CODEC.decode(buffer).equals(state),"bounded state codec");
   buffer.clear();var request=new OnboardingRequestPayload(OnboardingRequestPayload.Action.COMPLETE,2);OnboardingRequestPayload.STREAM_CODEC.encode(buffer,request);require(OnboardingRequestPayload.STREAM_CODEC.decode(buffer).equals(request),"request contains no target UUID");
  }finally{buffer.release();}
  System.out.println("PASS: "+checks+" onboarding persistence, defaults and layout checks");
 }
}
