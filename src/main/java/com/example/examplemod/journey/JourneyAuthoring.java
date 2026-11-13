package com.example.examplemod.journey;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.journey.JourneyModel.*;

/** Operator-only authoring, optimistic concurrency and validation before mutation. */
public final class JourneyAuthoring {
    public static CompoundTag chain(Chain c) {
        var t=new CompoundTag();t.putString("Id",c.id);t.putString("Title",c.title);
        t.putBoolean("Enabled",c.enabled);t.putBoolean("Automatic",c.automatic);t.put("Steps",JourneyCodec.strings(c.steps));return t;
    }
    public static CompoundTag record(JourneyData d,String group,String id) {
        return switch(group) {
            case "marker" -> d.markers.containsKey(id)?JourneyCodec.marker(d.markers.get(id)):new CompoundTag();
            case "quest" -> d.quests.containsKey(id)?JourneyCodec.quest(d.quests.get(id)):new CompoundTag();
            case "chain" -> d.chains.containsKey(id)?chain(d.chains.get(id)):new CompoundTag();
            default -> throw new IllegalArgumentException("group");
        };
    }
    public static String revision(CompoundTag t) {
        if(t.isEmpty())return "";
        try { // CompoundTag iteration order is not a wire contract: sort keys recursively.
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical(t).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static String canonical(Tag t) {
        if(t instanceof CompoundTag c){var keys=new TreeSet<>(c.getAllKeys());var b=new StringBuilder("{");for(var k:keys)b.append(k.length()).append(':').append(k).append(canonical(c.get(k)));return b.append('}').toString();}
        if(t instanceof ListTag l){var b=new StringBuilder("[");for(var v:l)b.append(canonical(v));return b.append(']').toString();}
        return t.getId()+":"+t;
    }
    private static void ensure(boolean condition,String code){if(!condition)throw new IllegalArgumentException(code);}
    private static void text(String s,int max,boolean required){ensure(s.length()<=max&&(!required||!s.isBlank())&&s.chars().noneMatch(c->c==0||c==167),"text");}
    public static CompoundTag apply(JourneyData d,boolean operator,JourneyEditorPayload request,CompoundTag value) {
        ensure(operator,"permission");d.requireAvailable();identifier(request.id());
        JourneyRouteAuthoring.requireUnmanaged(request.id());
        var old=record(d,request.group(),request.id());ensure(revision(old).equals(request.base()),"conflict");
        if(request.action().equals("delete")) {
            ensure(!old.isEmpty(),"missing");
            switch(request.group()) {
                case "marker" -> {ensure(d.quests.values().stream().noneMatch(q->q.marker.equals(request.id())||q.objectives.stream().anyMatch(o->JourneyRouteAuthoring.spatial(o.kind())&&o.target().equals(request.id()))),"referenced");d.markers.remove(request.id());}
                case "quest" -> {ensure(d.chains.values().stream().noneMatch(c->c.steps.contains(request.id()))&&d.markers.values().stream().noneMatch(m->m.afterQuest.equals(request.id())),"referenced");d.quests.remove(request.id());}
                case "chain" -> d.chains.remove(request.id());
                default -> throw new IllegalArgumentException("group");
            }
            d.setDirty();return new CompoundTag();
        }
        ensure(request.action().equals("save"),"action");ensure(value.getString("Id").equals(request.id()),"id");
        text(value.getString("Title"),96,true);text(value.getString("Description"),512,false);
        switch(request.group()) {
            case "marker" -> {
                ensure(!old.isEmpty()||d.markers.size()<MAX_MARKERS,"limit");
                ensure(value.getInt("Radius")>=1&&value.getInt("Radius")<=64,"range");
                ensure(value.getInt("Size")>=8&&value.getInt("Size")<=16&&value.getInt("Range")>=16&&value.getInt("Range")<=160,"range");
                ensure(value.getInt("Icon")>=0&&value.getInt("Icon")<=7,"range");
                var m=JourneyCodec.marker(value);ensure(m.point.dimension().length()<=128,"dimension");
                ensure(m.route.stream().allMatch(p->p.dimension().equals(m.point.dimension())),"dimension");
                if(!m.afterQuest.isEmpty()) {JourneyRouteAuthoring.requireUnmanaged(m.afterQuest);var q=d.quests.get(m.afterQuest);ensure(q!=null,"reference");ensure(q.objectives.stream().noneMatch(o->JourneyRouteAuthoring.spatial(o.kind())&&o.target().equals(m.id)),"cycle");}
                d.markers.put(m.id,m);
            }
            case "quest" -> {
                ensure(!old.isEmpty()||d.quests.size()<MAX_QUESTS,"limit");var q=JourneyCodec.quest(value);
                JourneyRouteAuthoring.requireUnmanaged(q.marker);
                ensure(q.marker.isEmpty()||d.markers.containsKey(q.marker),"reference");
                ensure(new HashSet<>(q.objectives.stream().map(Objective::id).toList()).size()==q.objectives.size(),"duplicate");
                var previous=d.quests.get(q.id);
                if(previous!=null&&!previous.objectives.equals(q.objectives))ensure(!previous.enabled,"disable_first");
                for(int i=0;i<q.objectives.size();i++) {
                    var o=q.objectives.get(i);
                    if(JourneyRouteAuthoring.spatial(o.kind())||o.kind()==ObjectiveKind.CITY_MEMBER||o.kind()==ObjectiveKind.ADVANCEMENT)ensure(o.count()==1,"range");
                    if(JourneyRouteAuthoring.spatial(o.kind())){JourneyRouteAuthoring.requireUnmanaged(o.target());var m=d.markers.get(o.target());ensure(m!=null&&(!q.enabled||m.enabled)&&!m.afterQuest.equals(q.id),"reference");}
                    if(o.kind()==ObjectiveKind.ITEM)ensure(ResourceLocation.tryParse(o.target())!=null&&BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(o.target())),"item");
                    if(o.kind()==ObjectiveKind.ADVANCEMENT)ensure(ResourceLocation.tryParse(o.target())!=null,"advancement");
                    // Changed conditions never inherit evidence from an old objective with the same ID.
                    if(previous!=null&&previous.objectives.stream().anyMatch(v->v.id().equals(o.id())&&!v.equals(o)))q.objectives.set(i,new Objective(UUID.randomUUID().toString(),o.kind(),o.target(),o.count()));
                }
                ensure(!q.enabled||!q.objectives.isEmpty(),"objectives");d.quests.put(q.id,q);
            }
            case "chain" -> {
                ensure(!old.isEmpty()||d.chains.size()<16,"limit");var c=new Chain(request.id());c.title=value.getString("Title");c.enabled=value.getBoolean("Enabled");c.automatic=value.getBoolean("Automatic");c.steps.addAll(JourneyCodec.strings(value,"Steps"));
                ensure(c.steps.size()<=MAX_STEPS&&new HashSet<>(c.steps).size()==c.steps.size(),"duplicate");
                var previous=d.chains.get(c.id);if(previous!=null&&!previous.steps.equals(c.steps))ensure(!previous.enabled,"disable_first");
                ensure(!c.enabled||!c.steps.isEmpty(),"steps");
                ensure(!c.automatic||d.chains.values().stream().noneMatch(v->!v.id.equals(c.id)&&v.automatic),"automatic");
                for(var id:c.steps){JourneyRouteAuthoring.requireUnmanaged(id);var q=d.quests.get(id);ensure(q!=null&&(!c.enabled||q.enabled),"reference");ensure(d.chains.values().stream().noneMatch(v->!v.id.equals(c.id)&&v.steps.contains(id)),"duplicate");}
                d.chains.put(c.id,c);
            }
            default -> throw new IllegalArgumentException("group");
        }
        d.setDirty();return record(d,request.group(),request.id());
    }
    private static void reply(ServerPlayer p,String event,String group,String id,CompoundTag value,String error) {
        var t=new CompoundTag();t.putString("Event",event);t.putString("Group",group);t.putString("Id",id);t.put("Value",value);t.putString("Revision",revision(value));t.putString("Error",error);PacketDistributor.sendToPlayer(p,new JourneyEditorStatePayload(t));
    }
    public static void request(ServerPlayer p,JourneyEditorPayload r) {
        if(p instanceof FakePlayer)return;
        if(!p.hasPermissions(2)){reply(p,"error",r.group(),r.id(),new CompoundTag(),"permission");return;}
        long now=net.minecraft.Util.getMillis();var budget=p.getPersistentData();
        if(now-budget.getLong("JourneyEditorWindow")>=1000){budget.putLong("JourneyEditorWindow",now);budget.putInt("JourneyEditorCount",0);}
        int count=budget.getInt("JourneyEditorCount");if(count>=4){reply(p,"error",r.group(),r.id(),new CompoundTag(),"rate");return;}budget.putInt("JourneyEditorCount",count+1);
        try {
            var d=JourneyData.get(p.getServer());d.requireAvailable();
            if (JourneyRouteAuthoring.GROUP.equals(r.group())) {
                JourneyRouteAuthoring.request(p, d, r);
                return;
            }
            if(r.action().equals("list")) {
                reply(p,"begin","","",new CompoundTag(),"");
                for(var id:d.markers.keySet())if(!JourneyRouteAuthoring.managed(id))reply(p,"entry","marker",id,record(d,"marker",id),"");
                for(var id:d.quests.keySet())if(!JourneyRouteAuthoring.managed(id))reply(p,"entry","quest",id,record(d,"quest",id),"");
                for(var id:d.chains.keySet())if(!JourneyRouteAuthoring.managed(id))reply(p,"entry","chain",id,record(d,"chain",id),"");
                reply(p,"ready","","",new CompoundTag(),"");return;
            }
            var value=r.action().equals("save")?TagParser.parseTag(r.value()):new CompoundTag();
            if(r.group().equals("marker")&&r.action().equals("save"))ensure(p.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(value.getString("Dimension"))))!=null,"dimension");
            if(r.group().equals("quest")&&r.action().equals("save"))for(var raw:value.getList("Objectives",10)){var o=(CompoundTag)raw;if(o.getString("Kind").equals("ADVANCEMENT"))ensure(p.getServer().getAdvancements().get(ResourceLocation.parse(o.getString("Target")))!=null,"advancement");}
            var result=apply(d,true,r,value);reply(p,"saved",r.group(),r.id(),result,"");
            for(var player:p.getServer().getPlayerList().getPlayers())JourneyServer.send(player,false,"");
        } catch(Exception failure){reply(p,"error",r.group(),r.id(),new CompoundTag(),failure instanceof IllegalArgumentException?failure.getMessage():"invalid");}
    }
    private JourneyAuthoring(){}
}
