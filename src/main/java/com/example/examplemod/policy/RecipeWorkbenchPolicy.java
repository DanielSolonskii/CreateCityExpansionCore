package com.example.examplemod.policy;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Keep this class and the generated datapack when removing devtools. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class RecipeWorkbenchPolicy {
    @SubscribeEvent public static void packs(AddPackFindersEvent event) {
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "datapacks/recipe_workbench"),
                PackType.SERVER_DATA, Component.literal("Project recipe overrides"), PackSource.DEFAULT, true, Pack.Position.TOP);
    }
}
