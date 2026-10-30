package com.example.examplemod.devtools;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.UUID;

/** No client classes in the server command registration. Delete the entire devtools package to uninstall. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class RecipeWorkbench {
    public static volatile java.util.function.Consumer<UUID> openClient = ignored -> {};
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ccec").then(Commands.literal("recipes")
                .requires(source -> !source.getServer().isDedicatedServer() && source.hasPermission(2)
                        && source.getPlayer()!=null && source.getServer().isSingleplayerOwner(source.getPlayer().getGameProfile()))
                .executes(context -> {openClient.accept(context.getSource().getPlayerOrException().getUUID()); return 1;})));
    }
}
