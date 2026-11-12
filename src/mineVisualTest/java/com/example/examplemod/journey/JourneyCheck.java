package com.example.examplemod.journey;

import com.example.examplemod.journey.JourneyModel.*;
import static com.example.examplemod.journey.JourneyModel.*;
import net.minecraft.nbt.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.*;

public final class JourneyCheck {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void rejects(Runnable action,String why){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}require(rejected,why);}
    public static void main(String[] args){
        JourneyAuthoringCheck.main(args);
        var d=new JourneyData();var spawn=new Marker("spawn",new Point("minecraft:overworld",0,64,0));spawn.newcomers=true;d.markers.put(spawn.id,spawn);
        var center=new Marker("center",new Point("minecraft:overworld",120,65,12));center.route.add(new Point("minecraft:overworld",5,64,0));center.route.add(new Point("minecraft:overworld",80,65,12));d.markers.put(center.id,center);
        var welcome=new Quest("welcome");welcome.enabled=true;welcome.marker="spawn";welcome.objectives.add(new Objective("visit",ObjectiveKind.VISIT,"spawn",1));d.quests.put(welcome.id,welcome);
        var delivery=new Quest("delivery");delivery.enabled=true;delivery.marker="center";delivery.objectives.add(new Objective("visit",ObjectiveKind.VISIT,"center",1));delivery.objectives.add(new Objective("deliver",ObjectiveKind.SHIPMENT_DELIVERED,"",1));d.quests.put(delivery.id,delivery);
        var chain=new Chain("newcomer");chain.enabled=chain.automatic=true;chain.steps.addAll(List.of("welcome","delivery"));d.chains.put(chain.id,chain);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var p=d.player(a);p.chain=chain.id;p.autoStarted=true;
        require(available(welcome,p,d.chains.values())&&!available(delivery,p,d.chains.values()),"only first step unlocked");
        JourneyServer.advance(d,p);require(p.activeQuest.equals("welcome"),"automatic journey accepts first objective");
        require(!p.finish(welcome),"tracking/reading a guide cannot complete it");
        p.met.add("welcome/visit");require(p.finish(welcome),"server evidence completes visit");require(!p.finish(welcome),"completion is idempotent");
        require(available(delivery,p,d.chains.values()),"next step unlocked");JourneyServer.advance(d,p);require(p.activeQuest.equals("delivery"),"chain advances without reconnect");
        require(JourneyServer.target(d,p).equals("center"),"next unmet visit becomes navigation target");
        p.met.add("delivery/visit");require(!p.finish(delivery),"visiting center does not prove delivery");
        require(JourneyServer.target(d,p).equals("center"),"non-spatial objective uses attached place");
        p.paused=true;require(JourneyServer.target(d,p).isEmpty(),"pause hides navigation");JourneyServer.advance(d,p);require(p.activeQuest.equals("delivery"),"pause preserves objective");p.paused=false;
        var snapshot=JourneyServer.snapshot(d,p,false,"");require(snapshot.getList("Markers",10).size()==2,"player sees published markers");
        require(snapshot.getList("Markers",10).stream().filter(v->((CompoundTag)v).getString("Id").equals("center")).map(v->(CompoundTag)v).findFirst().orElseThrow().getList("Route",10).size()==2,"only selected route transmitted");
        require(snapshot.getList("Quests",10).stream().map(v->(CompoundTag)v).filter(v->v.getString("Id").equals("delivery")).findFirst().orElseThrow().getList("Objectives",10).getCompound(0).getBoolean("Met"),"objective evidence visible in journal");
        p.met.add("delivery/deliver");require(p.finish(delivery),"delivery needs independent server evidence");JourneyServer.advance(d,p);require(p.graduated&&p.chain.isEmpty(),"final step graduates newcomer");
        require(!JourneyServer.visible(spawn,p)&&JourneyServer.visible(center,p),"newcomer markers disappear after graduation");center.enabled=false;require(!JourneyServer.visible(center,p),"disabled marker hidden");center.enabled=true;
        require(d.player(b).completed.isEmpty()&&!d.player(b).graduated,"UUID progress isolated");
        var saved=d.save(new CompoundTag(),null);var loaded=JourneyData.load(saved,null);require(loaded.player(a).completed.equals(p.completed)&&loaded.player(a).met.equals(p.met),"completion and objective IDs survive save/reload");
        require(loaded.markers.get("center").route.equals(center.route),"route and dimensions survive reload");require(loaded.chains.get("newcomer").automatic,"automatic enrollment setting persists");
        require(loaded.player(b).completed.isEmpty(),"reload cannot merge players");require(loaded.save(new CompoundTag(),null).equals(saved),"saved data roundtrip lossless");
        var cap=new Progress();cap.accept("one");cap.accept("two");cap.accept("three");rejects(()->cap.accept("four"),"three-pin limit enforced");cap.accept("one");require(cap.accepted.size()==3,"retracking does not consume another pin");
        require(new Point("minecraft:overworld",0,64,0).distance("minecraft:the_nether",.5,64,.5)==Double.POSITIVE_INFINITY,"no cross-dimension arrival");require(spawn.point.distance("minecraft:overworld",.5,64,.5)==0,"arrival uses 3D block-center position");
        rejects(()->new Point("minecraft:overworld",Integer.MIN_VALUE,64,0),"coordinate overflow rejected");rejects(()->identifier("../ Bad"),"invalid command IDs rejected");rejects(()->new Objective("x",ObjectiveKind.ITEM,"minecraft:stone",0),"invalid count rejected");
        var future=new CompoundTag();future.putInt("Version",99);future.putString("Future","keep");rejects(()->JourneyData.load(future,null),"unknown data versions rejected");var unavailable=JourneyData.loadForStorage(future,null);rejects(unavailable::requireAvailable,"unavailable data blocks authoring");require(unavailable.save(new CompoundTag(),null).equals(future),"unsupported data never replaced with an empty world");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);
        try{var request=new JourneyRequestPayload(JourneyRequestPayload.Action.TRACK_QUEST,"delivery");JourneyRequestPayload.STREAM_CODEC.encode(buffer,request);require(JourneyRequestPayload.STREAM_CODEC.decode(buffer).equals(request),"bounded request codec roundtrip");require(Arrays.stream(JourneyRequestPayload.Action.values()).noneMatch(v->v.name().contains("COMPLETE")||v.name().contains("REWARD")),"client has no completion or reward request");buffer.clear();var state=new JourneyStatePayload(snapshot);JourneyStatePayload.STREAM_CODEC.encode(buffer,state);require(JourneyStatePayload.STREAM_CODEC.decode(buffer).data().equals(snapshot),"state packet roundtrip");}finally{buffer.release();}
        // Worst-case legal definitions must fit NeoForge's 1 MiB client payload limit.
        var largest=new JourneyData();
        for(int i=0;i<MAX_MARKERS;i++){var m=new Marker("m".repeat(60)+i,spawn.point);m.title="界".repeat(96);m.description="界".repeat(512);for(int j=0;j<MAX_POINTS;j++)m.route.add(spawn.point);largest.markers.put(m.id,m);}
        for(int i=0;i<MAX_QUESTS;i++){var q=new Quest("q".repeat(60)+i);q.enabled=true;q.title="界".repeat(96);q.description="界".repeat(512);q.marker=largest.markers.keySet().iterator().next();for(int j=0;j<MAX_OBJECTIVES;j++)q.objectives.add(new Objective(UUID.randomUUID().toString(),ObjectiveKind.ITEM,"a".repeat(128),4096));largest.quests.put(q.id,q);}
        for(int i=0;i<16;i++){var c=new Chain("c".repeat(60)+i);c.title="界".repeat(96);c.enabled=true;c.steps.addAll(largest.quests.keySet().stream().limit(32).toList());largest.chains.put(c.id,c);}
        var maximumProgress=new Progress();maximumProgress.activeMarker=largest.markers.keySet().iterator().next();var maximum=JourneyServer.snapshot(largest,maximumProgress,false,"界".repeat(96));
        var out=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);try{JourneyStatePayload.STREAM_CODEC.encode(out,new JourneyStatePayload(maximum));require(out.readableBytes()<1_000_000,"bounded snapshots fit network limit: "+out.readableBytes());System.out.println("Maximum journey snapshot: "+out.readableBytes()+" bytes");}finally{out.release();}
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();JourneyCommands.register(new net.neoforged.neoforge.event.RegisterCommandsEvent(dispatcher,net.minecraft.commands.Commands.CommandSelection.ALL,null));var root=dispatcher.getRoot().getChild("ccec");require(root.getChild("quests")!=null&&root.getChild("marker")!=null&&root.getChild("quest")!=null&&root.getChild("journey")!=null&&root.getChild("route")!=null,"all command roots registered together");
        require(root.getChild("marker").getChild("set").getChild("id").getChild("type").getChild("guide")!=null,"marker type command exists");
        System.out.println("PASS: "+checks+" journey progression, isolation, persistence, commands and network checks");
    }
}
