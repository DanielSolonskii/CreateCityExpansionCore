package com.example.examplemod.journey;

import com.example.examplemod.journey.JourneyModel.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import java.util.*;

/** Exercises real authoring transactions and wire codecs without a graphical client. */
public final class JourneyAuthoringCheck {
    private static int checks;
    private static void require(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static JourneyEditorPayload request(JourneyData d,String action,String group,String id,CompoundTag value){return new JourneyEditorPayload(action,group,id,JourneyAuthoring.revision(JourneyAuthoring.record(d,group,id)),value.toString());}
    private static void rejects(JourneyData d,String code,Runnable action){var before=JourneyCodec.save(d);boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;require(e.getMessage().equals(code),"expected "+code+" got "+e.getMessage());}require(rejected,code+" rejected");require(before.equals(JourneyCodec.save(d)),code+" leaves all data unchanged");}
    private static CompoundTag save(JourneyData d,String group,String id,CompoundTag value){return JourneyAuthoring.apply(d,true,request(d,"save",group,id,value),value);}
    public static void main(String[] args){
        var d=new JourneyData();var point=new Point("minecraft:overworld",20,64,40);var m=new Marker("spawn",point);m.title="Начало";m.icon=5;m.tint=0xd0ad73;m.size=14;m.range=120;m.onMap=false;m.inWorld=true;m.route.add(new Point("minecraft:overworld",10,65,20));var value=JourneyCodec.marker(m);
        rejects(d,"permission",()->JourneyAuthoring.apply(d,false,request(d,"save","marker",m.id,value),value));
        var saved=save(d,"marker",m.id,value);require(JourneyCodec.marker(saved).icon==5&&d.marker(m.id).size==14,"appearance stored");
        var copy=JourneyCodec.load(JourneyCodec.save(d));require(JourneyCodec.marker(JourneyAuthoring.record(copy,"marker",m.id)).route.equals(m.route),"authored route survives restart");require(!copy.marker(m.id).onMap&&copy.marker(m.id).inWorld&&copy.marker(m.id).range==120,"visibility survives restart");
        var legacy=value.copy();for(String key:List.of("OnMap","InWorld","Tint","Icon","Size","Range","AfterQuest"))legacy.remove(key);var old=JourneyCodec.marker(legacy);require(old.onMap&&old.inWorld&&old.size==10&&old.range==72&&old.afterQuest.isEmpty(),"legacy markers migrate with visible defaults");
        var edit=value.copy();edit.putString("Title","Другой автор");var stale=request(d,"save","marker",m.id,edit);var concurrent=value.copy();concurrent.putString("Title","Серверная правка");save(d,"marker",m.id,concurrent);
        rejects(d,"conflict",()->JourneyAuthoring.apply(d,true,stale,edit));require(d.marker(m.id).title.equals("Серверная правка"),"conflict cannot overwrite winner");
        var invalid=concurrent.copy();invalid.putInt("Range",10000);rejects(d,"range",()->save(d,"marker",m.id,invalid));
        var dimension=concurrent.copy();var route=new ListTag();route.add(JourneyCodec.point(new Point("minecraft:the_nether",0,70,0)));dimension.put("Route",route);rejects(d,"dimension",()->save(d,"marker",m.id,dimension));
        var q=new Quest("welcome");q.title="Добро пожаловать";q.marker=m.id;q.objectives.add(new Objective("visit",ObjectiveKind.VISIT,m.id,1));q.enabled=true;save(d,"quest",q.id,JourneyCodec.quest(q));
        var delete=request(d,"delete","marker",m.id,new CompoundTag());rejects(d,"referenced",()->JourneyAuthoring.apply(d,true,delete,new CompoundTag()));
        var cycle=concurrent.copy();cycle.putString("AfterQuest",q.id);rejects(d,"cycle",()->save(d,"marker",m.id,cycle));
        var changed=JourneyCodec.quest(q);changed.getList("Objectives",10).getCompound(0).putString("Kind","INTERACT");rejects(d,"disable_first",()->save(d,"quest",q.id,changed));
        var off=JourneyCodec.quest(q);off.putBoolean("Enabled",false);save(d,"quest",q.id,off);var player=d.player(UUID.randomUUID());player.met.add("welcome/visit");changed.putBoolean("Enabled",false);save(d,"quest",q.id,changed);require(!d.quest(q.id).objectives.getFirst().id().equals("visit"),"changed conditions receive fresh evidence ID");require(!player.finish(d.quest(q.id)),"old evidence cannot finish changed condition");
        var c=new Chain("tour");c.steps.add(q.id);c.automatic=true;save(d,"chain",c.id,JourneyAuthoring.chain(c));var other=new Chain("other");other.automatic=true;rejects(d,"automatic",()->save(d,"chain",other.id,JourneyAuthoring.chain(other)));
        other.automatic=false;other.steps.add(q.id);rejects(d,"duplicate",()->save(d,"chain",other.id,JourneyAuthoring.chain(other)));
        rejects(d,"referenced",()->JourneyAuthoring.apply(d,true,request(d,"delete","quest",q.id,new CompoundTag()),new CompoundTag()));
        JourneyAuthoring.apply(d,true,request(d,"delete","chain",c.id,new CompoundTag()),new CompoundTag());require(d.chains.isEmpty(),"unreferenced chain deleted");require(player.met.contains("welcome/visit"),"authoring never rewrites player evidence");
        var gated=new Marker("later",point);gated.afterQuest=q.id;require(!JourneyServer.visible(gated,player),"prerequisite hides locked marker");player.completed.add(q.id);require(JourneyServer.visible(gated,player),"completion unlocks marker");
        var shuffled=new CompoundTag();var keys=new ArrayList<>(value.getAllKeys());Collections.reverse(keys);for(String key:keys)shuffled.put(key,value.get(key).copy());require(JourneyAuthoring.revision(shuffled).equals(JourneyAuthoring.revision(value)),"revision ignores compound insertion order");
        var max=new Marker("m".repeat(64),new Point("minecraft:"+"x".repeat(118),30000000,2048,-30000000));max.title="界".repeat(96);max.description="界".repeat(512);for(int i=0;i<64;i++)max.route.add(max.point);var maximum=JourneyCodec.marker(max); // Directory sends one entry per packet, including full route.
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);
        try{var payload=new JourneyEditorStatePayload(maximum);JourneyEditorStatePayload.STREAM_CODEC.encode(buffer,payload);require(buffer.readableBytes()<32768,"individual catalogue entry bounded");require(JourneyEditorStatePayload.STREAM_CODEC.decode(buffer).data().equals(maximum),"editor catalogue codec roundtrip");buffer.clear();var normal=request(d,"save","marker",m.id,value);JourneyEditorPayload.STREAM_CODEC.encode(buffer,normal);require(buffer.readableBytes()<32768,"edit fits server packet budget");require(JourneyEditorPayload.STREAM_CODEC.decode(buffer).equals(normal),"bounded edit codec roundtrip");}finally{buffer.release();}
        routeTransactions();
        System.out.println("PASS: "+checks+" authoring permissions, conflicts, migration, references and packet checks");
    }

    private static void routeTransactions() {
        var data = new JourneyData();
        var start = new Point("minecraft:overworld", 1, 64, 2);
        var legacy = new Marker("standalone", start);
        data.markers.put(legacy.id, legacy);
        var visit = new JourneyRouteDraft.Step("Подойдите", "", ObjectiveKind.VISIT, start, 3, "", 1, List.of(), 0);
        var open = new JourneyRouteDraft.Step("Откройте пилон", "", ObjectiveKind.OPEN_PYLON,
                new Point("minecraft:overworld", 8, 65, 2), 4, "", 1, List.of(start), 1);
        var draft = new JourneyRouteDraft("Первые шаги", start, List.of(visit, open));
        var saved = editRoute(data, "save", "", draft.write());
        require(data.chains.isEmpty() && data.markers.size() == 1, "draft save creates no live definitions");
        require(!saved.revision().isEmpty(), "draft has an optimistic concurrency token");
        data = JourneyData.load(data.save(new CompoundTag(), null), null);
        require(JourneyRouteDraft.parse(data.routeDraft).equals(draft), "draft and test start survive reload");

        var before = data.save(new CompoundTag(), null);
        var incomplete = draft.write();
        incomplete.getList("Steps", Tag.TAG_COMPOUND).getCompound(1).remove("Dimension");
        try {
            editRoute(data, "publish", saved.revision(), incomplete);
            throw new AssertionError("incomplete route published");
        } catch (JourneyRouteDraft.Failure failure) {
            require("location".equals(failure.code()) && failure.step() == 2, "validation identifies the failing step");
        }
        try {
            editRoute(data, "publish", "", draft.write());
            throw new AssertionError("stale route published");
        } catch (JourneyRouteDraft.Failure failure) {
            require("conflict".equals(failure.code()), "stale editor cannot publish");
        }
        require(before.equals(data.save(new CompoundTag(), null)), "failed publication changes neither draft nor live state");
        editRoute(data, "check", saved.revision(), draft.write());
        require(before.equals(data.save(new CompoundTag(), null)), "checking a draft is read-only");

        var published = editRoute(data, "publish", saved.revision(), draft.write());
        var chain = data.chain(JourneyRouteAuthoring.CHAIN_ID);
        require(chain.enabled && !chain.automatic && chain.steps.size() == 2, "published route can be selected manually");
        String first = chain.steps.getFirst();
        String second = chain.steps.getLast();
        require(data.marker(data.quest(second).marker).afterQuest.equals(first), "later marker unlocks after prior step");
        var player = data.player(UUID.randomUUID());
        require(JourneyRouteAuthoring.accessible(data, player, chain.id) && !JourneyRouteAuthoring.accessible(data, player, first),
                "player can discover route without seeing its steps before enrolling");
        player.chain = chain.id;
        player.completed.add(first);
        player.met.add(first + "/complete");
        player.met.add("standalone/condition");
        player.accept(second);
        require(JourneyRouteAuthoring.accessible(data, player, first), "enrolled player sees route steps");

        var retitle = draft.write();
        retitle.getList("Steps", Tag.TAG_COMPOUND).getCompound(0).putString("Title", "Начните здесь");
        var retitled = editRoute(data, "publish", published.revision(), retitle);
        require(player.completed.contains(first), "wording changes retain valid progress");
        var moved = retitle.copy();
        moved.getList("Steps", Tag.TAG_COMPOUND).getCompound(0).putInt("X", 3);
        editRoute(data, "publish", retitled.revision(), moved);
        require(!player.completed.contains(first) && !player.met.contains(first + "/complete"),
                "changing an objective location cannot reuse old proof");
        require(player.activeQuest.isEmpty() && player.met.contains("standalone/condition"),
                "changed prerequisites allow the next eligible step and preserve unrelated progress");
        require(data.chains.size() == 1 && data.markers.size() == 3 && data.marker(legacy.id).point.equals(start),
                "publication replaces only the managed route without storing old versions");
    }

    private static JourneyRouteAuthoring.Result editRoute(JourneyData data, String action, String base, CompoundTag value) {
        var request = new JourneyEditorPayload(action, JourneyRouteAuthoring.GROUP, JourneyRouteAuthoring.DRAFT_ID, base, value.toString());
        return JourneyRouteAuthoring.apply(data, request, value, ignored -> {});
    }
}
