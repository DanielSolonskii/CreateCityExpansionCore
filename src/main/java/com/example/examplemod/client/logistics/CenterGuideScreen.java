package com.example.examplemod.client.logistics;

import com.example.examplemod.client.CommunityFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Replayable first-visit guide. Its diagrams are illustrations, never live delivery commands. */
public final class CenterGuideScreen extends Screen implements CenterResponseScreen {
    private final LogisticsCenterScreen parent;
    private final boolean replay;
    private int step, left, top, panelWidth, panelHeight, textScroll, textHeight, textViewport;
    private long changed = net.minecraft.Util.getMillis();
    private ActionButton back, next;

    public CenterGuideScreen(LogisticsCenterScreen parent) {
        this(parent, !LogisticsTutorial.shouldOpen());
    }

    /** Explicit replay starts at the beginning without clearing the saved first-visit result. */
    public CenterGuideScreen(LogisticsCenterScreen parent, boolean replay) {
        super(tr("guide.title"));
        this.parent = parent;
        this.replay = replay;
        step = replay ? 0 : LogisticsTutorial.step();
    }

    @Override protected void init() {
        panelWidth = Math.min(760, width - 20);
        panelHeight = Math.min(440, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int footer = top + panelHeight - 34;
        int gap = 6, skipWidth = panelWidth < 380 ? 56 : 82;
        int backWidth = panelWidth < 380 ? 62 : 96;
        int nextWidth = Math.min(176, panelWidth - skipWidth - backWidth - gap * 2 - 24);
        addRenderableWidget(new ActionButton(left + 12, footer, skipWidth, 23, tr("guide.skip"), this::onClose));
        back = addRenderableWidget(new ActionButton(left + panelWidth - nextWidth - backWidth - gap - 12,
                footer, backWidth, 23, tr("guide.back"), () -> move(-1)));
        next = addRenderableWidget(new ActionButton(left + panelWidth - nextWidth - 12, footer,
                nextWidth, 23, tr(step == LogisticsTutorial.STEPS - 1 ? "guide.done" : "guide.next"), this::advance));
        setInitialFocus(next);
        back.active = step > 0;
        var motion = addRenderableWidget(new ActionButton(left + panelWidth - 38, top + 9, 26, 18,
                Component.literal(reducedMotion() ? "=" : "≈"), () -> {
                    boolean reduced = !reducedMotion();
                    CommunityFx.reduced = reduced;
                    CommunityFx.save();
                    LogisticsTutorial.setReducedMotion(reduced);
                    rebuildWidgets();
                }));
        motion.setTooltip(Tooltip.create(tr(reducedMotion() ? "guide.motion_reduced" : "guide.motion_full")));
        motion.narration(tr("guide.motion"));
    }

    private void advance() {
        if (step == LogisticsTutorial.STEPS - 1) {
            LogisticsTutorial.complete();
            minecraft.setScreen(parent);
        } else move(1);
    }

    private void move(int direction) {
        int value = Math.clamp(step + direction, 0, LogisticsTutorial.STEPS - 1);
        if (step == value) return;
        step = value;
        textScroll = 0;
        changed = net.minecraft.Util.getMillis();
        if (!replay) LogisticsTutorial.progress(step);
        rebuildWidgets();
    }

    @Override public void tick() { super.tick(); parent.tick(); }
    @Override public void accept(CompoundTag data) { parent.accept(data); }

    @Override public Component getNarrationMessage() {
        return title.copy().append(". ").append(tr("guide.page." + step + ".title")).append(". ")
                .append(tr("guide.page." + step + ".body")).append(". ").append(tr("guide.page." + step + ".tip"));
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        boolean reduced = reducedMotion();
        long now = net.minecraft.Util.getMillis();
        float reveal = reduced ? 1 : CenterPresentation.ease(now - changed, 280);
        g.fillGradient(0, 0, width, height, BG, 0xff15252e);
        for (int x = 0; x < width; x += 32) g.vLine(x, 0, height, 0x102b4954);
        for (int y = 0; y < height; y += 32) g.hLine(0, width, y, 0x102b4954);
        surface(g, left, top, panelWidth, panelHeight, PANEL);
        g.fill(left + 2, top, left + panelWidth - 2, top + 2, TEAL);
        g.drawString(font, fit(text("guide.title"), panelWidth - 118), left + 14, top + 13, TEAL, false);
        Component count = Component.translatable("ccec.center.guide.step", step + 1, LogisticsTutorial.STEPS);
        g.drawString(font, count, left + panelWidth - 48 - font.width(count), top + 14, MUTED, false);
        int segment = Math.max(1, (panelWidth - 28 - (LogisticsTutorial.STEPS - 1) * 5) / LogisticsTutorial.STEPS);
        for (int i = 0; i < LogisticsTutorial.STEPS; i++) {
            int x = left + 14 + i * (segment + 5);
            g.fill(x, top + 34, x + segment, top + 36, i <= step ? TEAL : EDGE);
        }
        boolean wide = panelWidth >= 580;
        boolean compact = panelHeight < 230;
        int contentTop = top + 48;
        int footerTop = top + panelHeight - 42;
        int contentWidth = panelWidth - 28;
        int textX = left + 14, textWidth = wide ? contentWidth * 51 / 100 : contentWidth;
        int diagramX = wide ? left + panelWidth * 56 / 100 : left + 14;
        int diagramY = wide ? contentTop + 4 : footerTop - (compact ? 26 : Math.min(108, panelHeight / 3));
        int diagramWidth = wide ? panelWidth * 44 / 100 - 18 : contentWidth;
        int diagramHeight = wide ? footerTop - contentTop - 10 : footerTop - diagramY - 4;
        int textBottom = wide ? footerTop : diagramY - 8;
        textViewport = Math.max(20, textBottom - contentTop);
        int offset = reduced ? 0 : Math.round((1 - reveal) * 8);
        g.enableScissor(textX, contentTop, textX + textWidth, textBottom);
        int y = contentTop - textScroll + offset;
        Component heading = tr("guide.page." + step + ".title");
        g.drawWordWrap(font, heading, textX, y, textWidth, TEXT);
        y += font.wordWrapHeight(heading, textWidth) + (compact ? 7 : 14);
        Component body = tr("guide.page." + step + ".body");
        g.drawWordWrap(font, body, textX, y, textWidth, MUTED);
        y += font.wordWrapHeight(body, textWidth) + (compact ? 8 : 18);
        Component tip = tr("guide.page." + step + ".tip");
        g.fill(textX, y, textX + 2, y + font.wordWrapHeight(tip, textWidth - 12), BRASS);
        g.drawWordWrap(font, tip, textX + 9, y, textWidth - 12, BRASS);
        y += font.wordWrapHeight(tip, textWidth - 12) + 8;
        textHeight = y + textScroll - contentTop;
        textScroll = Math.min(textScroll, CenterPresentation.scrollLimit(textHeight, textViewport));
        g.disableScissor();
        if (textHeight > textViewport) {
            int thumb = Math.max(8, textViewport * textViewport / textHeight);
            int thumbY = contentTop + (textViewport - thumb) * textScroll / Math.max(1, textHeight - textViewport);
            g.fill(textX + textWidth - 2, thumbY, textX + textWidth, thumbY + thumb, TEAL);
        }
        g.enableScissor(diagramX, diagramY, diagramX + diagramWidth, diagramY + diagramHeight);
        diagram(g, diagramX, diagramY, diagramWidth, diagramHeight, now, reduced);
        g.disableScissor();
        g.hLine(left + 12, left + panelWidth - 13, footerTop, EDGE);
        super.render(g, mx, my, partialTick);
    }

    private void diagram(GuiGraphics g, int x, int y, int w, int h, long now, boolean reduced) {
        if (w < 30 || h < 16) return;
        surface(g, x, y, w, h, 0xff101c24);
        if (h < 44) {
            int cy = y + h / 2;
            g.hLine(x + 14, x + w - 14, cy, EDGE);
            for (int i = 0; i < 3; i++) icon(g, x + 10 + i * (w - 32) / 2, cy - 6, i == 1 ? 2 : 5, TEAL);
            return;
        }
        switch (step) {
            case 1 -> {
                card(g, x + 12, y + 12, w - 24, Math.max(24, (h - 36) / 2), 0, "guide.diagram.player", TEAL);
                card(g, x + 12, y + (h + 12) / 2, w - 24, Math.max(24, (h - 36) / 2), 3, "guide.diagram.network", BRASS);
            }
            case 2 -> {
                surface(g, x + 12, y + 12, w - 24, 20, 0xff22323c);
                g.drawString(font, fit(text("search"), w - 44), x + 20, y + 18, MUTED, false);
                for (int i = 0; i < Math.min(3, (h - 40) / 24); i++) {
                    int yy = y + 42 + i * 24;
                    g.fill(x + 14, yy, x + w - 14, yy + 18, i == 1 ? 0xff29332f : 0xff172833);
                    icon(g, x + 20, yy + 3, i + 2, i == 1 ? BRASS : TEAL);
                    g.hLine(x + 44, x + w - 48, yy + 7, MUTED);
                    g.fill(x + w - 34, yy + 6, x + w - 22, yy + 11, i == 1 ? BRASS : TEAL);
                }
            }
            case 3 -> {
                route(g, x, y + 3, w, h - 28, now, reduced);
                int yy = y + h - 18;
                g.fill(x + 14, yy, x + w - 14, yy + 4, EDGE);
                g.fill(x + 14, yy, x + 14 + (w - 28) * 2 / 3, yy + 4, TEAL);
            }
            case 4 -> {
                int rows = h > 105 ? 3 : 2;
                for (int i = 0; i < rows; i++) {
                    int yy = y + 15 + i * Math.max(20, (h - 38) / rows);
                    icon(g, x + 16, yy, i == rows - 1 ? 8 : i == 0 ? 5 : 2, i == rows - 1 ? BRASS : TEAL);
                    g.drawString(font, fit(text("guide.diagram." + (i == rows - 1 ? "total" : i == 0 ? "goods" : "transport")), w - 52), x + 39, yy + 3, i == rows - 1 ? BRASS : TEXT, false);
                }
                g.fill(x + 14, y + h - 12, x + w - 14, y + h - 10, BRASS);
            }
            case 5 -> {
                for (int xx = x + 12; xx < x + w - 8; xx += 22) g.vLine(xx, y + 8, y + h - 8, 0xff20313b);
                for (int yy = y + 12; yy < y + h - 8; yy += 22) g.hLine(x + 8, x + w - 8, yy, 0xff20313b);
                int x1 = x + 28, x2 = x + w / 2, x3 = x + w - 30, y1 = y + h * 2 / 3, y2 = y + h / 3;
                g.hLine(x1, x2, y1, TEAL); g.vLine(x2, y2, y1, TEAL); g.hLine(x2, x3, y2, TEAL);
                icon(g, x1 - 6, y1 - 6, 5, BRASS); icon(g, x2 - 6, y2 - 6, 4, TEAL); icon(g, x3 - 6, y2 - 6, 5, BRASS);
                if (!reduced) {
                    int a = x2 - x1, b = y1 - y2, c = x3 - x2;
                    int distance = (int)((now % 2200) * (a + b + c) / 2200);
                    int px = distance < a ? x1 + distance : distance < a + b ? x2 : x2 + distance - a - b;
                    int py = distance < a ? y1 : distance < a + b ? y1 - distance + a : y2;
                    g.fill(px - 1, py - 1, px + 2, py + 2, TEXT);
                }
            }
            default -> route(g, x, y, w, h, now, reduced);
        }
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, int glyph, String key, int color) {
        surface(g, x, y, w, h, 0xff1c303a);
        icon(g, x + 10, y + (h - 13) / 2, glyph, color);
        g.drawString(font, fit(text(key), w - 44), x + 32, y + (h - 8) / 2, TEXT, false);
    }

    private void route(GuiGraphics g, int x, int y, int w, int h, long now, boolean reduced) {
        int cy = y + h / 2;
        int first = x + 24, last = x + w - 25;
        g.hLine(first, last, cy, EDGE);
        int phase = reduced ? 0 : (int)((now % 2200) * Math.max(1, last - first) / 2200);
        if (!reduced) g.fill(first + phase, cy - 1, first + phase + 9, cy + 1, TEAL);
        String[] labels = {"source", "train", "destination"};
        for (int i = 0; i < 3; i++) {
            int cx = first + i * (last - first) / 2;
            surface(g, cx - 14, cy - 15, 28, 29, i == 1 ? 0xff294138 : 0xff20333e);
            icon(g, cx - 6, cy - 7, i == 1 ? 2 : 5, i == 1 ? BRASS : TEAL);
            if (h > 86) g.drawCenteredString(font, fit(text("guide.diagram." + labels[i]), Math.max(16, w / 3 - 6)), cx, cy + 25, MUTED);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        textScroll = Math.max(0, Math.min(CenterPresentation.scrollLimit(textHeight, textViewport), textScroll - (int)(vertical * 20)));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key >= 266 && key <= 269) {
            int limit = CenterPresentation.scrollLimit(textHeight, textViewport);
            textScroll = key == 268 ? 0 : key == 269 ? limit
                    : Math.clamp(textScroll + (key == 267 ? 1 : -1) * Math.max(20, textViewport - 12), 0, limit);
            return true;
        }
        if (key == 262) { advance(); return true; }
        if (key == 263) { move(-1); return true; }
        if (key == 257 && !(getFocused() instanceof ActionButton)) { if (claimActivationKey(key)) advance(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() {
        if (!replay) LogisticsTutorial.dismiss();
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
