package com.example.examplemod.knowledge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды выдачи знаний: {@code /ccec knowledge add <player> <knowledge>} и
 * {@code remove}. Корень {@code ccec} общий с другими системами — Brigadier
 * сливает одноимённые literal-узлы, поэтому отдельная регистрация безопасна и
 * консоль видит единое дерево.
 *
 * <p>{@code <knowledge>} — свободная строка (greedy), потому что имена файлов
 * содержат пробелы и кириллицу. Имя сохраняется в playerdata как введено;
 * совпадение при показе обеспечивает {@link KnowledgeAccess#matches}.
 */
public final class KnowledgeCommands {

    private KnowledgeCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ccec")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("knowledge")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("knowledge", StringArgumentType.greedyString())
                                                .executes(context -> change(context, true)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("knowledge", StringArgumentType.greedyString())
                                                .executes(context -> change(context, false)))))));
    }

    private static int change(CommandContext<net.minecraft.commands.CommandSourceStack> context, boolean add)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        var profiles = GameProfileArgument.getGameProfiles(context, "player");
        String name = StringArgumentType.getString(context, "knowledge");
        var server = source.getServer();
        for (var profile : profiles) {
            if (!KnowledgeBaseServer.edit(server, profile.getId(), add, name)) {
                source.sendFailure(Component.translatable(
                        add ? "command.createcityexpansioncore.knowledge.already"
                                : "command.createcityexpansioncore.knowledge.missing",
                        name, profile.getName()));
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(
                    add ? "command.createcityexpansioncore.knowledge.add_ok"
                            : "command.createcityexpansioncore.knowledge.remove_ok",
                    name, profile.getName()), true);
        }
        return profiles.size();
    }
}
