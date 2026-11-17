package com.example.examplemod.knowledge;

import com.example.examplemod.client.screen.KnowledgeBaseScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.lwjgl.glfw.GLFW;

/** Opens the article for a displayed item tooltip after holding the knowledge key. */
public final class KnowledgeTooltipHandler {
    private static final long HOLD_MILLIS = 2_000;
    private static final long MAX_FRAME_AGE_MILLIS = 250;
    private static Target renderedTarget;
    private static Target heldTarget;
    private static long holdSince;
    private static long lastFrame;
    private static boolean waitForRelease;

    private record Target(Screen screen, String itemId, String path) {}

    private KnowledgeTooltipHandler() {}

    @SubscribeEvent
    public static void tooltip(RenderTooltipEvent.GatherComponents event) {
        Minecraft client = Minecraft.getInstance();
        if (event.getItemStack().isEmpty() || client.player == null) return;
        String id = itemId(event.getItemStack());
        var file = KnowledgeBaseClient.byParentItem(id);
        if (file == null) return;
        Component hint;
        if (!KnowledgeBaseClient.hasAccess(file)) {
            hint = Component.translatable("tooltip.createcityexpansioncore.knowledge.locked");
        } else if (!KnowledgeKey.hold().isUnbound()) {
            var key = Component.literal("[").append(KnowledgeKey.hold().getTranslatedKeyMessage()).append("]")
                    .withStyle(ChatFormatting.GRAY);
            hint = Component.translatable("tooltip.createcityexpansioncore.knowledge.hold", key)
                    .withStyle(ChatFormatting.DARK_GRAY);
            long now = Util.getMillis();
            if (client.screen != null && new Target(client.screen, id, file.path()).equals(heldTarget)
                    && !waitForRelease && now - lastFrame <= MAX_FRAME_AGE_MILLIS && keyDown()) {
                double progress = Math.clamp((now - holdSince) / (double) HOLD_MILLIS, 0.0, 1.0);
                int segments = client.font.width(hint) / Math.max(1, client.font.width("|"));
                segments = Math.min(segments, Math.max(1, (event.getScreenWidth() - 12) / Math.max(1, client.font.width("|"))));
                int filled = (int) (segments * progress);
                hint = Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("|".repeat(segments - filled)).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            return;
        }
        // Gather runs during rendering, including when a screen reuses cached item text.
        var elements = event.getTooltipElements();
        elements.add(Math.min(1, elements.size()), Either.left(hint));
    }

    @SubscribeEvent
    public static void startFrame(RenderFrameEvent.Pre event) {
        renderedTarget = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderedTooltip(RenderTooltipEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        ItemStack stack = event.getItemStack();
        if (client.screen == null || client.screen instanceof KnowledgeBaseScreen || client.player == null
                || stack.isEmpty()) return;
        String id = itemId(stack);
        var file = KnowledgeBaseClient.byParentItem(id);
        renderedTarget = file != null && KnowledgeBaseClient.hasAccess(file)
                ? new Target(client.screen, id, file.path()) : null;
    }

    @SubscribeEvent
    public static void endFrame(RenderFrameEvent.Post event) {
        long now = Util.getMillis();
        if (!keyDown() || renderedTarget == null) {
            heldTarget = null;
        } else if (!renderedTarget.equals(heldTarget) || now - lastFrame > MAX_FRAME_AGE_MILLIS) {
            heldTarget = renderedTarget;
            holdSince = now;
        }
        lastFrame = now;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!keyDown()) {
            heldTarget = null;
            waitForRelease = false;
            return;
        }
        long now = Util.getMillis();
        Target target = heldTarget;
        if (target == null || target.screen() != client.screen || client.level == null || client.getOverlay() != null
                || (client.screen.getFocused() instanceof EditBox box && box.canConsumeInput())
                || now - lastFrame > MAX_FRAME_AGE_MILLIS) {
            heldTarget = null;
            return;
        }
        if (waitForRelease || now - holdSince < HOLD_MILLIS) return;
        var file = KnowledgeBaseClient.metaOf(target.path());
        if (file == null || !KnowledgeBaseClient.hasAccess(file)) return;
        heldTarget = null;
        waitForRelease = true;
        KnowledgeBaseScreen.open(target.screen(), target.path());
    }

    private static boolean keyDown() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isWindowActive() || KnowledgeKey.hold().isUnbound()) return false;
        // The normal mapping only applies in-world. Read its physical binding while a GUI is open.
        var key = KnowledgeKey.hold().getKey();
        long window = client.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
