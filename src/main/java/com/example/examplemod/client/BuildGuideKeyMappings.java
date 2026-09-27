package com.example.examplemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Shared layer controls for all CCEC multiblock guides. */
public final class BuildGuideKeyMappings {
    private static final KeyMapping NEXT = new KeyMapping(
            "key.createcityexpansioncore.build_guide.next_layer", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP, "key.categories.createcityexpansioncore");
    private static final KeyMapping PREVIOUS = new KeyMapping(
            "key.createcityexpansioncore.build_guide.previous_layer", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.createcityexpansioncore");
    private static final KeyMapping ALL = new KeyMapping(
            "key.createcityexpansioncore.build_guide.all_layers", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_HOME, "key.categories.createcityexpansioncore");

    private BuildGuideKeyMappings() {}
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(NEXT); event.register(PREVIOUS); event.register(ALL);
    }
    public static boolean consumeNext() { return NEXT.consumeClick(); }
    public static boolean consumePrevious() { return PREVIOUS.consumeClick(); }
    public static boolean consumeAll() { return ALL.consumeClick(); }
}
