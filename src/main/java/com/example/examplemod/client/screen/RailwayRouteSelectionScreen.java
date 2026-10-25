package com.example.examplemod.client.screen;

import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayRouteOptionsPayload;
import com.example.examplemod.railway.RailwayRouteOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Consumer;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Route cards share the profile window's chrome and ease in instead of appearing mid-frame. */
public final class RailwayRouteSelectionScreen extends CityPylonPageScreen {
    private static final int PANEL_WIDTH = 390, PANEL_HEIGHT = 250;
    private static final int CARD_WIDTH = 116, CARD_HEIGHT = 180, CARD_PITCH = 124, CARD_MARGIN = 14;

    private final Screen parent;
    private final RailwayRouteOptionsPayload payload;
    private final Consumer<RailwayRouteOption> selector;
    private final UiMotion motion = new UiMotion();

    public RailwayRouteSelectionScreen(Screen parent, RailwayRouteOptionsPayload payload) {
        this(parent, payload, null);
    }

    public RailwayRouteSelectionScreen(Screen parent, RailwayRouteOptionsPayload payload, Consumer<RailwayRouteOption> selector) {
        super(Component.translatable("gui.createcityexpansioncore.railway.routes.title"));
        this.parent = parent;
        this.payload = payload;
        this.selector = selector;
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    /** Truncates rather than overflowing, so a long route name cannot run into the next column. */
    private void text(GuiGraphics g, Component value, int x, int y, int width, int color) {
        g.drawString(font, fit(font, value, width), x, y, color, false);
    }

    /**
     * One geometry, used by both the painter and the click handler. Rendering clamps the panel to
     * the window while clicking used the unclamped size, so below 406x266 the cursor landed
     * between the cards it was drawn over.
     */
    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 16); }
    private int panelHeight() { return Math.min(PANEL_HEIGHT, height - 16); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int cardLeft(int index) { return panelLeft() + CARD_MARGIN + index * CARD_PITCH; }
    private int cardTop() { return panelTop() + 50; }
    private boolean cardAt(double mouseX, double mouseY, int index) {
        return mouseX >= cardLeft(index) && mouseX < cardLeft(index) + CARD_WIDTH
                && mouseY >= cardTop() && mouseY < cardTop() + CARD_HEIGHT;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        long now = net.minecraft.Util.getMillis();
        motion.opened(now);
        float delta = motion.delta(now);
        // Hit-testing subtracts the same value the painter has added, so the cards stay under the
        // cursor for the whole entrance rather than only after it settles.
        double localX = mouseX, localY = mouseY - motion.entranceOffset(now);

        int left = panelLeft(), top = panelTop(), w = panelWidth(), h = panelHeight();
        g.pose().pushPose();
        g.pose().translate(0, motion.entranceOffset(now), 0);

        panel(g, left, top, w, h);
        text(g, title, left + 14, top + 12, w - 28, ACCENT);
        text(g, Component.literal(payload.source() + "  →  " + payload.destination()),
                left + 14, top + 29, w - 28, MUTED);

        String error = !payload.errorKey().isEmpty() ? payload.errorKey() : payload.options().isEmpty() ? "no_route_options" : "";
        if (!error.isEmpty()) {
            Component message = Component.translatable("gui.createcityexpansioncore.railway.routes." + error);
            g.drawWordWrap(font, message, left + 24, top + 92, w - 48, TEXT);
            g.drawCenteredString(font, Component.translatable("gui.createcityexpansioncore.railway.routes.return_hint"),
                    left + w / 2, top + h - 30, MUTED);
        } else {
            for (int i = 0; i < payload.options().size(); i++) {
                renderCard(g, payload.options().get(i), cardLeft(i), cardTop(), i, localX, localY, delta);
            }
        }
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partial);
    }

    private void renderCard(GuiGraphics g, RailwayRouteOption o, int x, int y, int index,
                            double mx, double my, float delta) {
        boolean over = cardAt(mx, my, index);
        // The highlight eases toward its new value instead of snapping, so a card picked up by the
        // cursor reads as lit rather than as repainted.
        float lit = motion.hover(index, over, delta);
        rounded(g, x, y, CARD_WIDTH, CARD_HEIGHT, 5, UiMotion.mix(EDGE, ACCENT, lit * 0.6f));
        rounded(g, x + 1, y + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2, 4, UiMotion.mix(INSET, RAISED, lit));

        text(g, Component.translatable("gui.createcityexpansioncore.railway.routes." + o.strategy().name().toLowerCase(Locale.ROOT)),
                x + 8, y + 9, CARD_WIDTH - 16, ACCENT);
        drawMap(g, o, x + 8, y + 28, CARD_WIDTH - 16, 62);
        text(g, Component.translatable("gui.createcityexpansioncore.railway.routes.distance", String.format(Locale.ROOT, "%.0f", o.distance())),
                x + 8, y + 99, CARD_WIDTH - 16, TEXT);
        text(g, Component.translatable("gui.createcityexpansioncore.railway.routes.price", o.tariffCost()),
                x + 8, y + 115, CARD_WIDTH - 16, 0xFF88C4A9);
        text(g, Component.translatable("gui.createcityexpansioncore.railway.routes.eta", formatTicks(o.estimatedTicks())),
                x + 8, y + 131, CARD_WIDTH - 16, MUTED);

        var select = new WorldMapUiLayout.Rect(x + 8, y + 151, CARD_WIDTH - 16, 21);
        control(g, select, false, over, true, true);
        g.drawCenteredString(font, Component.translatable("gui.createcityexpansioncore.railway.routes.select"),
                select.x() + select.width() / 2, select.y() + 6, over ? 0xFFFFFFFF : 0xFFEFE3CC);
    }

    private void drawMap(GuiGraphics g, RailwayRouteOption o, int x, int y, int w, int h) {
        if (o.edges().isEmpty()) return;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (var e : o.edges()) for (var n : List.of(e.first(), e.second())) {
            minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x());
            minZ = Math.min(minZ, n.z()); maxZ = Math.max(maxZ, n.z());
        }
        double dx = Math.max(1, maxX - minX), dz = Math.max(1, maxZ - minZ);
        for (var e : o.edges()) {
            int x1 = x + (int) ((e.first().x() - minX) / dx * w), z1 = y + (int) ((e.first().z() - minZ) / dz * h);
            int x2 = x + (int) ((e.second().x() - minX) / dx * w), z2 = y + (int) ((e.second().z() - minZ) / dz * h);
            line(g, x1, z1, x2, z2, 0xFF65B9C8);
        }
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1, dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1, err = dx + dy;
        while (true) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e = 2 * err;
            if (e >= dy) { err += dy; x1 += sx; }
            if (e <= dx) { err += dx; y1 += sy; }
        }
    }

    private static String formatTicks(long ticks) {
        long seconds = ticks / 20;
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        double localY = my - motion.entranceOffset(net.minecraft.Util.getMillis());
        for (int i = 0; i < payload.options().size(); i++) {
            if (cardAt(mx, localY, i)) {
                RailwayRouteOption option = payload.options().get(i);
                if (selector == null) PacketDistributor.sendToServer(RailwayCommandPayload.simple(payload.pylonPos(),
                        RailwayCommandPayload.Action.SELECT_ROUTE, payload.fleetTrainId(), option.id()));
                else selector.accept(option);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
