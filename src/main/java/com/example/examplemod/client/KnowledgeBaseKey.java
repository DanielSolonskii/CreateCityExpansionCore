package com.example.examplemod.client;

import com.example.examplemod.client.screen.KnowledgeBaseScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиша открытия локальной базы знаний — K, переназначаемая как и остальные.
 *
 * <p><b>Почему клавиша работает как обычный просмотр.</b> База знаний читает
 * только файлы .md из папки игрока; ни один пакет не уходит на сервер и ни одно
 * состояние мира не меняется, поэтому здесь не нужна дистанция до пилона или
 * открытое меню — как у карты города, которая по клавише тоже только читает.
 *
 * <p><b>Почему экран не открывается поверх другого.</b> Нажатие обрабатывается
 * только когда игрок в мире и ничего не открыто: так клавиша не перехватывает
 * печать в чате, поиск в терминале и остальные экраны мода.
 */
public final class KnowledgeBaseKey {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.createcityexpansioncore.open_knowledge_base", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.createcityexpansioncore");

    private KnowledgeBaseKey() {}

    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (OPEN.isUnbound()) return;
        boolean pressed = false;
        while (OPEN.consumeClick()) pressed = true;
        Minecraft client = Minecraft.getInstance();
        if (!pressed || client.player == null || client.level == null
                || client.getConnection() == null || client.screen != null) return;
        client.setScreen(new KnowledgeBaseScreen(client.screen));
    }
}
