package com.example.examplemod.command;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.devtools.BlueprintEditorAccess;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.network.BlueprintAccessNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class BlueprintCommands {
    private static final String MESSAGE = "command.createcityexpansioncore.blueprint.";

    private BlueprintCommands() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ccec")
                .then(Commands.literal("blueprint").requires(source -> source.hasPermission(2))
                        .then(tierCommand("unlock", true))
                        .then(tierCommand("lock", false))
                        .then(Commands.literal("terminal")
                                .requires(source -> BlueprintEditorAccess.isLocalOperator(source.getPlayer()))
                                .executes(context -> BlueprintEditorAccess.issue(context.getSource(),
                                        context.getSource().getPlayerOrException())))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tierCommand(String action, boolean unlock) {
        var city = Commands.argument("city", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        CityManager.get(context.getSource().getServer().overworld()).getCities().stream()
                                .map(City::getName).map(StringArgumentType::escapeIfRequired), builder));
        for (Structure structure : Structure.values()) {
            city.then(Commands.literal(structure.key())
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1, structure.maxTier().level()))
                            .executes(context -> changeAccess(context.getSource(),
                                    StringArgumentType.getString(context, "city"), structure,
                                    structure.tierFromLevel(IntegerArgumentType.getInteger(context, "tier")), unlock))));
        }
        return Commands.literal(action).then(city);
    }

    private static int changeAccess(CommandSourceStack source, String cityName, Structure structure, Tier tier, boolean unlock) {
        var server = source.getServer();
        CityManager cities = CityManager.get(server.overworld());
        City city = cities.getCityByName(cityName);
        if (city == null) {
            source.sendFailure(Component.translatable(MESSAGE + "city_missing", cityName));
            return 0;
        }
        boolean changed = unlock ? cities.unlockBlueprintTier(city.getId(), structure, tier)
                : cities.lockBlueprintTier(city.getId(), structure, tier);
        if (changed) {
            BlueprintAccessNetwork.syncCity(server, city);
        }
        Component name = Component.translatable("gui.createcityexpansioncore.blueprint." + structure.key() + ".name");
        String action = unlock ? "unlocked" : "locked";
        String key = changed ? action : "already_" + action;
        source.sendSuccess(() -> Component.translatable(MESSAGE + key, city.getName(), name, tier.name()), false);
        return changed ? 1 : 0;
    }
}
