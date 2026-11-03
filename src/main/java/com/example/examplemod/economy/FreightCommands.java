package com.example.examplemod.economy;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Player and operator freight tools. Mutating cancellation always has an explicit confirm step. */
public final class FreightCommands {
    private static final String COMMAND = "command.createcityexpansioncore.freight.";
    private static final String MESSAGE = "message.createcityexpansioncore.economy.";
    private FreightCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("freight")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("subject", StringArgumentType.word())
                                .suggests((context, builder) -> suggestions(context, builder, false))
                                .executes(context -> cancel(context.getSource(), subject(context), false, false))
                                .then(Commands.literal("confirm")
                                        .executes(context -> cancel(context.getSource(), subject(context), false, true)))))
                .then(Commands.literal("recover")
                        .executes(context -> recover(context.getSource(), false)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("subject", StringArgumentType.word())
                                .suggests((context, builder) -> suggestions(context, builder, context.getSource().hasPermission(2)))
                                .executes(context -> inspect(context.getSource(), subject(context)))))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("subject", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestions(context, builder, true))
                                        .executes(context -> cancel(context.getSource(), subject(context), true, false))
                                        .then(Commands.literal("confirm")
                                                .executes(context -> cancel(context.getSource(), subject(context), true, true)))))
                        .then(Commands.literal("recover")
                                .executes(context -> recover(context.getSource(), true)))));
    }

    private static UUID subject(CommandContext<CommandSourceStack> context) {
        try { return UUID.fromString(StringArgumentType.getString(context, "subject")); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(COMMAND + "help"), false);
        return 1;
    }

    private static City city(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? null : CityManager.get(source.getServer().overworld()).getCityByMember(player.getUUID());
    }

    private static boolean endpoint(EconomyManager manager, FreightAssignment assignment, City city) {
        if (assignment == null || city == null) return false;
        IndustrialContract contract = manager.contract(assignment.subject);
        FreightPoint source = manager.freight().point(assignment.source);
        FreightPoint destination = manager.freight().point(assignment.destination);
        return contract != null && city.getId().equals(contract.issuerCityId())
                || source != null && city.getId().equals(source.cityId)
                || destination != null && city.getId().equals(destination.cityId);
    }

    private static int cancel(CommandSourceStack source, UUID subject, boolean admin, boolean confirmed) {
        if (subject == null) return feedback(source, "freight_invalid_id", false);
        if (admin && !source.hasPermission(2)) return feedback(source, "freight_revoke_denied", false);
        EconomyManager manager = EconomyManager.get(source.getServer().overworld());
        FreightAssignment assignment = manager.freight().assignments.get(subject);
        City city = city(source);
        if (!admin && (source.getPlayer() == null || city == null || !city.canBuild(source.getPlayer().getUUID())
                || !endpoint(manager, assignment, city))) return feedback(source, "freight_revoke_denied", false);
        if (assignment == null) return feedback(source, "freight_revoke_unknown", false);
        if (!confirmed) {
            String command = "/freight " + (admin ? "admin " : "") + "cancel " + subject + " confirm";
            Component action = Component.literal(command).withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
            source.sendSuccess(() -> Component.translatable(COMMAND + "cancel_confirm", subject.toString(), action), false);
            return 1;
        }
        String reason = admin ? manager.revokeDispatchAsAdmin(subject) : manager.revokeDispatch(subject, city.getId());
        boolean accepted = reason.equals("freight_revoked") || reason.equals("freight_cancel_pending");
        int result = feedback(source, reason, accepted);
        if (accepted) {
            FreightParcelRecovery.neutralizeUnsealed(source.getServer(), subject);
            EconomyNetworkHandlers.pushOpenMenus(source.getServer());
        }
        return result;
    }

    private static int recover(CommandSourceStack source, boolean admin) {
        if (admin && !source.hasPermission(2)) return feedback(source, "freight_revoke_denied", false);
        ServerPlayer player = source.getPlayer();
        if (player == null) return feedback(source, "freight_recover_player", false);
        String reason = FreightParcelRecovery.recoverHeld(player, null, admin);
        boolean recovered = reason.equals("freight_recovered");
        int result = feedback(source, reason, recovered);
        if (recovered) EconomyNetworkHandlers.pushOpenMenus(source.getServer());
        return result;
    }

    private static int inspect(CommandSourceStack source, UUID subject) {
        if (subject == null) return feedback(source, "freight_invalid_id", false);
        EconomyManager manager = EconomyManager.get(source.getServer().overworld());
        FreightAssignment assignment = manager.freight().assignments.get(subject);
        if (!source.hasPermission(2) && !endpoint(manager, assignment, city(source))) return feedback(source, "freight_revoke_denied", false);
        if (assignment == null) return feedback(source, "freight_revoke_unknown", false);
        boolean pending = manager.cancellationPending(subject), requested = manager.cancellationRequested(subject);
        Component state = Component.translatable(COMMAND + (pending ? "state.pending" : requested ? "state.closed" : "state.active"));
        source.sendSuccess(() -> Component.translatable(COMMAND + "inspect", subject.toString(), state,
                manager.cancellationOutstanding(subject), manager.cancellationPaid(subject), manager.cancellationRefunded(subject)), false);
        return 1;
    }

    private static int feedback(CommandSourceStack source, String reason, boolean success) {
        if (success) source.sendSuccess(() -> Component.translatable(MESSAGE + reason), false);
        else source.sendFailure(Component.translatable(MESSAGE + reason));
        return success ? 1 : 0;
    }

    private static CompletableFuture<Suggestions> suggestions(CommandContext<CommandSourceStack> context,
                                                              SuggestionsBuilder builder, boolean admin) {
        CommandSourceStack source = context.getSource();
        EconomyManager manager = EconomyManager.get(source.getServer().overworld());
        City city = city(source);
        boolean privileged = admin && source.hasPermission(2);
        for (FreightAssignment assignment : manager.freight().assignments.values()) {
            String id = assignment.subject.toString();
            if ((privileged || endpoint(manager, assignment, city)) && id.startsWith(builder.getRemainingLowerCase())) builder.suggest(id);
        }
        return builder.buildFuture();
    }
}
