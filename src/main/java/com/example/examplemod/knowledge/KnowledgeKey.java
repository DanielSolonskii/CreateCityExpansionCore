package com.example.examplemod.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import com.example.examplemod.client.screen.KnowledgeBaseScreen;

/**
 * Клавиша открытия базы знаний (по умолчанию K, переназначается в настройках).
 * Работает только в игре, без открытого экрана; когда экран уже открыт, IS_DOWN
 * возвращает false автоматически из-за KeyConflictContext.IN_GAME — это защищает
 * ввод в чате и в полях GUI.
 */
public final class KnowledgeKey {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.createcityexpansioncore.open_knowledge_base",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.createcityexpansioncore");

    private KnowledgeKey() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    /** Клавиша задержки-открытия из тултипа (та же K). */
    public static KeyMapping hold() {
        return OPEN;
    }

    /** Нажался ли бинд в этот тик и можно ли следом открыть экран. */
    private static boolean consumePress() {
        if (OPEN.getKey() == InputConstants.UNKNOWN) return false;
        boolean pressed = false;
        while (OPEN.consumeClick()) pressed = true;
        return pressed;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!consumePress()) return;
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null
                || minecraft.screen != null) return;
        KnowledgeBaseClient.ensureFolder();
        KnowledgeBaseScreen.open(null, null);
    }
}
