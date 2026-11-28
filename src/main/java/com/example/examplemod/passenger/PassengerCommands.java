package com.example.examplemod.passenger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

@EventBusSubscriber(modid = "createcityexpansioncore")
public final class PassengerCommands {
    private PassengerCommands() {}
    private static final Map<ServerPlayer,Long> lastCommand = new WeakHashMap<>();
    private static Component text(String key,Object... args) {
        return Component.translatable("ccec.passenger." + key,args);
    }
    private interface Action { Component run(ServerPlayer player); }
    private static int mutate(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
        var actor = context.getSource().getPlayerOrException();
        long now = actor.getServer().overworld().getGameTime();
        if (now - lastCommand.getOrDefault(actor,-10L) < 5) {
            context.getSource().sendFailure(text("rate")); return 0;
        }
        lastCommand.put(actor,now);
        try {
            var message = action.run(actor);
            context.getSource().sendSuccess(() -> message,false);
            return 1;
        } catch (IllegalArgumentException e) {
            var key = Objects.toString(e.getMessage(),"unavailable");
            context.getSource().sendFailure(text(key.matches("[a-z_]{1,32}") ? key : "unavailable"));
            return 0;
        }
    }
    private static RequiredArgumentBuilder<CommandSourceStack,UUID> line() {
        return Commands.argument("line",UuidArgument.uuid()).suggests((ctx,builder) -> {
            var player = ctx.getSource().getPlayerOrException();
            for (var line : PassengerData.get(player.getServer()).lines.values())
                if ((line.owner.equals(player.getUUID()) || player.hasPermissions(2)) &&
                        line.id.toString().startsWith(builder.getRemainingLowerCase()))
                    builder.suggest(line.id.toString(),Component.literal(line.name));
            return builder.buildFuture();
        });
    }
    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var actor = ctx.getSource().getPlayerOrException();
        ctx.getSource().sendSuccess(() -> text("help"),false);
        for (var l : PassengerData.get(actor.getServer()).lines.values()) {
            if (!l.owner.equals(actor.getUUID()) && !actor.hasPermissions(2)) continue;
            var state = l.train == null ? "draft" : PassengerService.running(actor.getServer(),l.id,l.train) ? "verified" : l.status;
            ctx.getSource().sendSuccess(() -> text("row",l.name,l.id,l.stops.size(),text(state)),false);
        }
        return 1;
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("passenger").requires(source -> source.hasPermission(2)).executes(PassengerCommands::list);
        root.then(Commands.literal("platform").executes(c -> mutate(c,p -> {
            PassengerService.platform(p,false); return text("platform_saved");
        })).then(Commands.literal("remove").executes(c -> mutate(c,p -> {
            PassengerService.platform(p,true); return text("removed");
        })).then(Commands.argument("station",UuidArgument.uuid()).suggests((ctx,builder) -> {
            var p = ctx.getSource().getPlayerOrException();
            for (var station : PassengerData.get(p.getServer()).platforms.values())
                if ((station.owner().equals(p.getUUID()) || p.hasPermissions(2)) && station.id().toString().startsWith(builder.getRemainingLowerCase()))
                    builder.suggest(station.id().toString(),Component.literal(station.stationPos().toShortString()));
            return builder.buildFuture();
        }).executes(c -> mutate(c,p -> {
            PassengerService.withdrawPlatform(p,UuidArgument.getUuid(c,"station")); return text("removed");
        })))));
        root.then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c ->
                mutate(c,p -> text("created",PassengerService.create(p,StringArgumentType.getString(c,"name")))))));
        root.then(Commands.literal("append").then(line().executes(c -> mutate(c,p -> {
            PassengerService.append(p,UuidArgument.getUuid(c,"line")); return text("appended");
        }))));
        root.then(Commands.literal("pop").then(line().executes(c -> mutate(c,p -> {
            PassengerService.pop(p,UuidArgument.getUuid(c,"line")); return text("removed");
        }))));
        root.then(Commands.literal("start").then(line().then(Commands.argument("seconds",IntegerArgumentType.integer(10,120))
                .executes(c -> mutate(c,p -> {
                    PassengerService.start(p,UuidArgument.getUuid(c,"line"),IntegerArgumentType.getInteger(c,"seconds"));
                    return text("started");
                })))));
        root.then(Commands.literal("stop").then(line().executes(c -> mutate(c,p -> {
            PassengerService.close(p,UuidArgument.getUuid(c,"line"),false); return text("stopped");
        }))));
        root.then(Commands.literal("delete").then(line().then(Commands.literal("confirm").executes(c -> mutate(c,p -> {
            PassengerService.close(p,UuidArgument.getUuid(c,"line"),true); return text("removed");
        })))));
        event.getDispatcher().register(Commands.literal("ccec").then(root));
    }
}
