package com.example.examplemod.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class CutsceneCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
            detail -> Component.literal(String.valueOf(detail)));

    private CutsceneCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ccec")
                .then(Commands.literal("cutscene").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("edit").then(id().executes(CutsceneCommands::edit)))
                        .then(Commands.literal("play").then(id()
                                .executes(context -> play(context, List.of(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(context -> play(context, EntityArgument.getPlayers(context, "players"))))))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context.getSource(), List.of(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(context -> stop(context.getSource(), EntityArgument.getPlayers(context, "players")))))
                        .then(Commands.literal("intro")
                                .executes(context -> showIntro(context.getSource()))
                                .then(Commands.literal("set").then(id().executes(CutsceneCommands::setIntro)))
                                .then(Commands.literal("clear").executes(context -> clearIntro(context.getSource()))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.argument("id", StringArgumentType.word()).suggests((context, builder) -> {
            try {
                return SharedSuggestionProvider.suggest(CutsceneStore.list(context.getSource().getServer()), builder);
            } catch (IOException e) {
                return builder.buildFuture();
            }
        });
    }

    private static int edit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!CutsceneNetwork.canEdit(player)) {
            throw ERROR.create("Клиент не поддерживает редактор кат-сцен.");
        }
        String id = StringArgumentType.getString(context, "id");
        Cutscene scene = load(context.getSource().getServer(), id);
        try {
            CutsceneNetwork.open(player, id, scene);
            return 1;
        } catch (IllegalArgumentException e) {
            throw ERROR.create(e.getMessage());
        }
    }

    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        try {
            List<String> ids = CutsceneStore.list(source.getServer());
            if (ids.isEmpty()) {
                source.sendSuccess(() -> Component.literal("На сервере ещё нет сохранённых кат-сцен."), false);
            } else {
                String displayed = String.join(", ", ids.stream().limit(50).toList());
                String suffix = ids.size() > 50 ? " и ещё " + (ids.size() - 50) : "";
                source.sendSuccess(() -> Component.literal(displayed + suffix), false);
            }
            return ids.size();
        } catch (IOException e) {
            LOGGER.warn("Could not list cutscenes", e);
            throw ERROR.create("Сервер не смог прочитать каталог кат-сцен. Подробности в журнале.");
        }
    }

    private static int play(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        Cutscene scene = requireScene(source.getServer(), id);
        String json = CutsceneJson.encode(scene);
        int sent = 0;
        for (ServerPlayer player : players) {
            if (!(player instanceof FakePlayer) && CutsceneNetwork.canPlay(player)
                    && player.level().dimension().location().equals(scene.dimension())) {
                CutsceneNetwork.play(player, json, false);
                sent++;
            }
        }
        if (sent == 0) {
            throw ERROR.create("Для показа нужен игрок с поддержкой кат-сцен в измерении " + scene.dimension() + ".");
        }
        int count = sent;
        source.sendSuccess(() -> Component.literal("Сцена «" + id + "» отправлена игрокам (" + count
                + " из " + players.size() + ")."), false);
        return sent;
    }

    private static int stop(CommandSourceStack source, Collection<ServerPlayer> players) throws CommandSyntaxException {
        int sent = 0;
        for (ServerPlayer player : players) {
            if (!(player instanceof FakePlayer) && CutsceneNetwork.canPlay(player)) {
                CutsceneNetwork.stop(player);
                sent++;
            }
        }
        if (sent == 0) {
            throw ERROR.create("Среди выбранных игроков нет клиентов с поддержкой кат-сцен.");
        }
        int count = sent;
        source.sendSuccess(() -> Component.literal("Остановка кат-сцены отправлена игрокам (" + count
                + " из " + players.size() + ")."), false);
        return sent;
    }

    private static int showIntro(CommandSourceStack source) {
        String id = CutsceneIntro.get(source.getServer()).id();
        source.sendSuccess(() -> Component.literal(id.isEmpty() ? "Вступительная сцена отключена."
                : "Вступительная сцена для новых игроков — «" + id + "»."), false);
        return 1;
    }

    private static int setIntro(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        requireScene(source.getServer(), id);
        CutsceneIntro.get(source.getServer()).set(id);
        return showIntro(source);
    }

    private static int clearIntro(CommandSourceStack source) {
        CutsceneIntro.get(source.getServer()).set("");
        return showIntro(source);
    }

    private static Cutscene requireScene(MinecraftServer server, String id) throws CommandSyntaxException {
        Cutscene scene = load(server, id);
        if (scene == null) {
            throw ERROR.create("Сцена «" + id + "» не найдена.");
        }
        try {
            CutsceneNetwork.requireContent(scene);
        } catch (IllegalArgumentException e) {
            throw ERROR.create(e.getMessage());
        }
        return scene;
    }

    private static Cutscene load(MinecraftServer server, String id) throws CommandSyntaxException {
        try {
            return CutsceneStore.load(server, id);
        } catch (IllegalArgumentException e) {
            throw ERROR.create(e.getMessage());
        } catch (IOException e) {
            LOGGER.warn("Could not read cutscene {}", id, e);
            throw ERROR.create("Сервер не смог прочитать JSON сцены. Подробности в журнале.");
        }
    }
}
