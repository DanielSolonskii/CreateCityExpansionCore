package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.map.MapPresentationDraw;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class KnowledgeChrome {
    static final int BACKDROP = 0x88090E10;
    static final int TEXT = 0xFFEEDFBF;
    static final int GOLD = 0xFFE9BC69;
    static final int ACCENT = 0xFFC38D40;
    static final int EDGE = 0xFF747587;
    static final int SURFACE = 0xFF181A21;
    static final int HIGHLIGHT = 0xFF302419;
    static final ResourceLocation CLOSE = texture("close");
    private static final ResourceLocation BRAIN = texture("heading_brain");
    private static final ResourceLocation BRAIN_GLOW = texture("heading_brain_glow");
    private static final long GLOW_PERIOD_MILLIS = 4_200;

    private KnowledgeChrome() {}

    static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "textures/gui/knowledge/" + name + ".png");
    }

    static void image(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height) {
        g.blit(texture, x, y, width, height, 0f, 0f, 1, 1, 1, 1);
    }

    static int headingMark(GuiGraphics g, int x, int y, int height) {
        int markHeight = height * 8 / 5;
        int markWidth = markHeight * 3 / 2;
        int markY = y - (markHeight - height) / 3;
        // Both textures use the same transparent padding around the 192 × 128 mark.
        int padX = Math.round(markWidth / 6f);
        int padY = Math.round(markHeight * 3 / 16f);
        int textureWidth = markWidth + padX * 2;
        int textureHeight = markHeight + padY * 2;
        image(g, BRAIN, x - padX, markY - padY, textureWidth, textureHeight);
        double glow = 0.3;
        if (!CommunityFx.reduced) {
            double phase = 2 * Math.PI * (Util.getMillis() % GLOW_PERIOD_MILLIS) / GLOW_PERIOD_MILLIS;
            glow = 0.36 + 0.24 * Math.cos(phase);
        }
        MapPresentationDraw.opacity(g, glow, () -> {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                image(g, BRAIN_GLOW, x - padX, markY - padY, textureWidth, textureHeight);
            } finally {
                RenderSystem.disableBlend();
            }
        });
        int divider = x + markWidth + Math.max(3, height / 8);
        g.fill(divider, y, divider + 1, y + height, ACCENT);
        return divider + Math.max(4, height / 5);
    }

    static void frame(GuiGraphics g, int x, int y, int width, int height) {
        stepped(g, x, y, width, height, 9, 0xFF0F1016);
        stepped(g, x + 1, y + 1, width - 2, height - 2, 8, 0xFF555765);
        stepped(g, x + 2, y + 2, width - 4, height - 4, 7, 0xFF9292A1);
        stepped(g, x + 4, y + 4, width - 8, height - 8, 5, 0xFF191B21);
        for (int side = 0; side < 2; side++) {
            for (int end = 0; end < 2; end++) {
                int cornerX = side == 0 ? x + 1 : x + width - 15;
                int cornerY = end == 0 ? y + 1 : y + height - 15;
                int capX = side == 0 ? cornerX + 5 : cornerX;
                int capY = end == 0 ? cornerY : cornerY + 10;
                int stemX = side == 0 ? cornerX : cornerX + 10;
                int stemY = end == 0 ? cornerY + 5 : cornerY;
                g.fill(capX, capY, capX + 9, capY + 4, 0xFFB87D30);
                g.fill(stemX, stemY, stemX + 4, stemY + 9, 0xFFB87D30);
                g.fill(capX, capY, capX + 9, capY + 1, GOLD);
                g.fill(stemX, stemY, stemX + 1, stemY + 9, GOLD);
            }
        }
    }

    static void stepped(GuiGraphics g, int x, int y, int w, int h, int corner, int color) {
        if (w <= 0 || h <= 0) return;
        int inset = Math.min(corner, Math.min(w, h) / 2);
        g.fill(x, y + inset, x + w, y + h - inset, color);
        for (int i = 0; i < inset; i++) {
            int cut = inset - i;
            g.fill(x + cut, y + i, x + w - cut, y + i + 1, color);
            g.fill(x + cut, y + h - i - 1, x + w - cut, y + h - i, color);
        }
    }
}
