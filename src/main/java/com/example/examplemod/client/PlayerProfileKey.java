package com.example.examplemod.client;

import com.example.examplemod.client.screen.PlayerProfileScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Independent, rebindable profile shortcut; never intercepts typing in an open screen. */
public final class PlayerProfileKey {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.createcityexpansioncore.open_player_profile", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.createcityexpansioncore");

    private PlayerProfileKey() {}

    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (OPEN.getKey().equals(InputConstants.UNKNOWN)) return;
        boolean pressed = false;
        while (OPEN.consumeClick()) pressed = true;
        Minecraft client = Minecraft.getInstance();
        if (!pressed || client.player == null || client.level == null || client.getConnection() == null || client.screen != null) return;
        PlayerProfileScreen.open(null, client.player.getUUID());
    }
}
