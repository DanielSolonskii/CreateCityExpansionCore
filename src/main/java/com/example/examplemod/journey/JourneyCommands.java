package com.example.examplemod.journey;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;
import static com.example.examplemod.journey.JourneyModel.*;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class JourneyCommands {
    @FunctionalInterface private interface Edit {void run(CommandContext<CommandSourceStack> c,JourneyData d);}
    private static LiteralArgumentBuilder<CommandSourceStack> lit(String name){return Commands.literal(name);}
    private static RequiredArgumentBuilder<CommandSourceStack,String> id(String name,String group){return Commands.argument(name,StringArgumentType.word()).suggests((c,b)->{var d=JourneyData.get(c.getSource().getServer());var names=switch(group){case "marker"->d.markers.keySet();case "quest"->d.quests.keySet();default->d.chains.keySet();};return SharedSuggestionProvider.suggest(names,b);});}
    private static String str(CommandContext<CommandSourceStack> c,String name){return StringArgumentType.getString(c,name);}
    private static int edit(CommandContext<CommandSourceStack> context, Edit edit) {
        try {
            var data = JourneyData.get(context.getSource().getServer());
            data.requireAvailable();
            for (String argument : List.of("id", "marker", "quest", "target")) {
                String value;
                try {
                    value = context.getArgument(argument, String.class);
                } catch (IllegalArgumentException absent) {
                    continue;
                }
                JourneyRouteAuthoring.requireUnmanaged(value);
            }
            edit.run(context, data);
            data.setDirty();
            context.getSource().sendSuccess(() -> Component.translatable("gui.createcityexpansioncore.journey.saved"), false);
            for (var player : context.getSource().getServer().getPlayerList().getPlayers()) {
                JourneyServer.send(player, false, "");
            }
            return 1;
        } catch (IllegalArgumentException failure) {
            Component message = "route_managed".equals(failure.getMessage())
                    ? Component.translatable("gui.createcityexpansioncore.journey.error.route_managed")
                    : Component.translatable("gui.createcityexpansioncore.journey.error", failure.getMessage());
            context.getSource().sendFailure(message);
            return 0;
        }
    }
    private static String text(CommandContext<CommandSourceStack> c,int max){String s=str(c,"text");if(s.isBlank()||s.length()>max)throw new IllegalArgumentException("Text length: 1–"+max);return s;}
    private static Point here(CommandContext<CommandSourceStack> c){var p=c.getSource().getPosition();var b=BlockPos.containing(p);return new Point(c.getSource().getLevel().dimension().location().toString(),b.getX(),b.getY(),b.getZ());}
    private static void questReady(JourneyData d,Quest q){if(q.objectives.isEmpty())throw new IllegalArgumentException("Add objectives first");if(!q.marker.isEmpty())d.marker(q.marker);for(var o:q.objectives)if(JourneyRouteAuthoring.spatial(o.kind())){if(!d.marker(o.target()).enabled)throw new IllegalArgumentException("Objective marker disabled");}}
    private static void objective(JourneyData d,Quest q,ObjectiveKind kind,String target,int count){
        if(q.enabled)throw new IllegalArgumentException("Disable the quest before changing objectives");if(q.objectives.size()>=MAX_OBJECTIVES)throw new IllegalArgumentException("Too many objectives");
        if(kind!=ObjectiveKind.ITEM&&count!=1)throw new IllegalArgumentException("This objective requires count 1");
        switch(kind){case VISIT,INTERACT,OPEN_PYLON->d.marker(target);case ITEM->{if(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(target)))throw new IllegalArgumentException("Unknown item");}case ADVANCEMENT->ResourceLocation.parse(target);default->{}}
        q.objectives.add(new Objective(UUID.randomUUID().toString(),kind,target,count));
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        var root=lit("ccec");
        root.then(lit("quests").executes(c->{try{JourneyServer.request(c.getSource().getPlayerOrException(),new JourneyRequestPayload(JourneyRequestPayload.Action.JOURNAL,""));return 1;}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}}));
        var marker=lit("marker").requires(s->s.hasPermission(2));
        marker.then(lit("create").then(id("id","marker").then(lit("here").executes(c->edit(c,(x,d)->{String key=str(x,"id");if(d.markers.containsKey(key)||d.markers.size()>=MAX_MARKERS)throw new IllegalArgumentException("Marker exists or limit reached");d.markers.put(key,new Marker(key,here(x)));})))));
        marker.then(lit("list").executes(c->{var d=JourneyData.get(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal(String.join(", ",d.markers.keySet())),false);return 1;}));
        marker.then(lit("info").then(id("id","marker").executes(c->edit(c,(x,d)->{var m=d.marker(str(x,"id"));x.getSource().sendSuccess(()->Component.literal(m.id+" · "+m.kind+" · "+m.point+" · radius="+m.radius+" · enabled="+m.enabled+" · route="+m.route.size()),false);})))) ;
        marker.then(lit("move").then(id("id","marker").then(lit("here").executes(c->edit(c,(x,d)->{var m=d.marker(str(x,"id"));m.point=here(x);m.route.clear();}))).then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(c->edit(c,(x,d)->{var b=BlockPosArgument.getBlockPos(x,"pos");var m=d.marker(str(x,"id"));m.point=new Point(x.getSource().getLevel().dimension().location().toString(),b.getX(),b.getY(),b.getZ());m.route.clear();})))));
        var markerSet=id("id","marker");
        markerSet.then(lit("title").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->edit(c,(x,d)->d.marker(str(x,"id")).title=text(x,96)))));
        markerSet.then(lit("description").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->edit(c,(x,d)->d.marker(str(x,"id")).description=text(x,512)))));
        for(var kind:Kind.values())markerSet.then(lit("type").then(lit(kind.name().toLowerCase(Locale.ROOT)).executes(c->edit(c,(x,d)->{var m=d.marker(str(x,"id"));m.kind=kind;m.icon=kind.ordinal();}))));
        markerSet.then(lit("radius").then(Commands.argument("value",IntegerArgumentType.integer(1,64)).executes(c->edit(c,(x,d)->d.marker(str(x,"id")).radius=IntegerArgumentType.getInteger(x,"value")))));
        markerSet.then(lit("enabled").then(Commands.argument("value",BoolArgumentType.bool()).executes(c->edit(c,(x,d)->d.marker(str(x,"id")).enabled=BoolArgumentType.getBool(x,"value")))));
        for(String audience:List.of("all","newcomers"))markerSet.then(lit("audience").then(lit(audience).executes(c->edit(c,(x,d)->d.marker(str(x,"id")).newcomers=audience.equals("newcomers")))));
        marker.then(lit("set").then(markerSet));root.then(marker);

        var quest=lit("quest").requires(s->s.hasPermission(2));
        quest.then(lit("create").then(id("id","quest").executes(c->edit(c,(x,d)->{String key=str(x,"id");if(d.quests.containsKey(key)||d.quests.size()>=MAX_QUESTS)throw new IllegalArgumentException("Quest exists or limit reached");d.quests.put(key,new Quest(key));}))));
        var questSet=id("id","quest");
        questSet.then(lit("title").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->edit(c,(x,d)->d.quest(str(x,"id")).title=text(x,96)))));
        questSet.then(lit("description").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->edit(c,(x,d)->d.quest(str(x,"id")).description=text(x,512)))));
        questSet.then(lit("marker").then(id("marker","marker").executes(c->edit(c,(x,d)->{d.marker(str(x,"marker"));d.quest(str(x,"id")).marker=str(x,"marker");}))));
        questSet.then(lit("enabled").then(Commands.argument("value",BoolArgumentType.bool()).executes(c->edit(c,(x,d)->{var q=d.quest(str(x,"id"));boolean enable=BoolArgumentType.getBool(x,"value");if(enable)questReady(d,q);q.enabled=enable;}))));
        quest.then(lit("set").then(questSet));
        var add=id("id","quest");
        for(var kind:ObjectiveKind.values()){
            var branch=lit(kind.name().toLowerCase(Locale.ROOT));
            if(kind==ObjectiveKind.CITY_MEMBER||kind==ObjectiveKind.SHIPMENT_DELIVERED||kind==ObjectiveKind.PROJECT_COMPLETED)branch.executes(c->edit(c,(x,d)->objective(d,d.quest(str(x,"id")),kind,"",1)));
            else branch.then(Commands.argument("target",StringArgumentType.word()).then(Commands.argument("count",IntegerArgumentType.integer(1,4096)).executes(c->edit(c,(x,d)->objective(d,d.quest(str(x,"id")),kind,str(x,"target"),IntegerArgumentType.getInteger(x,"count"))))));
            add.then(branch);
        }
        quest.then(lit("objective").then(lit("add").then(add)).then(lit("clear").then(id("id","quest").executes(c->edit(c,(x,d)->{var q=d.quest(str(x,"id"));if(q.enabled)throw new IllegalArgumentException("Disable quest first");q.objectives.clear();})))));
        root.then(quest);

        var journey=lit("journey").requires(s->s.hasPermission(2));
        journey.then(lit("reset").then(Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(c->{try{var player=net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player");return edit(c,(x,d)->d.players.remove(player.getUUID()));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}})));
        journey.then(lit("create").then(id("id","chain").executes(c->edit(c,(x,d)->{String key=str(x,"id");if(d.chains.containsKey(key)||d.chains.size()>=16)throw new IllegalArgumentException("Journey exists or limit reached");d.chains.put(key,new Chain(key));}))));
        journey.then(lit("step").then(lit("add").then(id("id","chain").then(id("quest","quest").executes(c->edit(c,(x,d)->{var chain=d.chain(str(x,"id"));var q=d.quest(str(x,"quest"));if(chain.enabled||chain.steps.size()>=MAX_STEPS||chain.steps.contains(q.id))throw new IllegalArgumentException("Disable journey; steps must be unique (max 32)");if(d.chains.values().stream().anyMatch(v->v!=chain&&v.steps.contains(q.id)))throw new IllegalArgumentException("A quest belongs to one journey");chain.steps.add(q.id);}))))).then(lit("clear").then(id("id","chain").executes(c->edit(c,(x,d)->{var chain=d.chain(str(x,"id"));if(chain.enabled)throw new IllegalArgumentException("Disable journey first");chain.steps.clear();})))));
        var chainSet=id("id","chain");
        chainSet.then(lit("title").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->edit(c,(x,d)->d.chain(str(x,"id")).title=text(x,96)))));
        chainSet.then(lit("automatic").then(Commands.argument("value",BoolArgumentType.bool()).executes(c->edit(c,(x,d)->{boolean value=BoolArgumentType.getBool(x,"value");var chain=d.chain(str(x,"id"));if(value&&d.chains.values().stream().anyMatch(v->v!=chain&&v.automatic))throw new IllegalArgumentException("Only one automatic journey");chain.automatic=value;}))));
        chainSet.then(lit("enabled").then(Commands.argument("value",BoolArgumentType.bool()).executes(c->edit(c,(x,d)->{var chain=d.chain(str(x,"id"));boolean enable=BoolArgumentType.getBool(x,"value");if(enable){if(chain.steps.isEmpty())throw new IllegalArgumentException("Add steps first");for(String key:chain.steps){var q=d.quest(key);if(!q.enabled)throw new IllegalArgumentException("Enable quest: "+key);questReady(d,q);}}chain.enabled=enable;}))));
        journey.then(lit("set").then(chainSet));root.then(journey);
        var route=lit("route").requires(s->s.hasPermission(2));
        route.then(lit("point").then(lit("add").then(id("id","marker").then(lit("here").executes(c->edit(c,(x,d)->{var m=d.marker(str(x,"id"));var p=here(x);if(m.route.size()>=MAX_POINTS||!m.point.dimension().equals(p.dimension()))throw new IllegalArgumentException("Route: same dimension, max 64 points");m.route.add(p);}))))));
        route.then(lit("clear").then(id("id","marker").executes(c->edit(c,(x,d)->d.marker(str(x,"id")).route.clear()))));root.then(route);
        event.getDispatcher().register(root);
    }
    private JourneyCommands(){}
}
