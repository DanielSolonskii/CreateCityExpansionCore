package com.example.examplemod.client.screen;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityRelationSnapshot;
import com.example.examplemod.network.CityRelationsCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Relations list sharing the profile window's chrome, palette and easing. */
public final class CityRelationsScreen extends CityPylonPageScreen {
    private static final int PANEL_WIDTH = 340, ROW_HEIGHT = 42, ROW_PLATE = 36;

    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private final UiMotion motion = new UiMotion();
    private EditBox search;
    private int scroll;
    /** Where the search field sits before the entrance slide is applied. */
    private int searchBaseX, searchBaseY;

    public CityRelationsScreen(CityPylonScreen parent) {
        super(Component.translatable("gui.createcityexpansioncore.relations.title"));
        this.parent = parent;
        this.menu = parent.getMenu();
    }

    @Override protected void init() {
        var field = searchField();
        searchBaseX = field.x();
        searchBaseY = field.y();
        search = new EditBox(font, searchBaseX, searchBaseY, field.width(), 20,
                Component.translatable("gui.createcityexpansioncore.relations.search"));
        search.setHint(Component.translatable("gui.createcityexpansioncore.relations.search"));
        search.setMaxLength(16);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setResponder(ignored -> scroll = 0);
        addRenderableWidget(search);
        request(CityRelationsCommandPayload.Action.REQUEST_STATE, null);
    }

    /** One geometry shared by painting, hit-testing and widget placement. */
    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 16); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelRight() { return panelLeft() + panelWidth(); }
    private WorldMapUiLayout.Rect searchField() { return new WorldMapUiLayout.Rect(panelLeft() + 12, 42, panelWidth() - 104, 20); }
    private WorldMapUiLayout.Rect backButton() { return new WorldMapUiLayout.Rect(panelRight() - 84, 42, 72, 20); }
    private int rowTop(int index) { return 72 + (index - scroll) * ROW_HEIGHT; }

    private void request(CityRelationsCommandPayload.Action action, java.util.UUID target) {
        PacketDistributor.sendToServer(new CityRelationsCommandPayload(menu.getPylonPos(), action, target));
    }

    private List<CityRelationSnapshot> visible() {
        var state = menu.getRelationsState(); if (state == null) return List.of();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return state.cities().stream().filter(city -> query.isEmpty()
                || city.cityName().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    /** Rows that fit between the header and the bottom edge; drives both painting and scrolling. */
    private int visibleRows() { return Math.max(1, (height - 22 - 72) / ROW_HEIGHT); }

    private void text(GuiGraphics g, Component value, int x, int y, int width, int color) {
        g.drawString(font, fit(font, value, width), x, y, color, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        long now = net.minecraft.Util.getMillis();
        motion.opened(now);
        float delta = motion.delta(now);
        float offset = motion.entranceOffset(now);
        // The EditBox is a vanilla widget rendered in screen coordinates, so it has to be moved to
        // where the panel is this frame. Leaving it behind would leave the field floating outside
        // the window that is sliding in underneath it.
        if (search != null) search.setY(searchBaseY + (int) offset);
        double localY = mouseY - offset;

        int left = panelLeft(), right = panelRight(), top = 20, bottom = height - 20;
        graphics.pose().pushPose();
        graphics.pose().translate(0, offset, 0);

        panel(graphics, left, top, panelWidth(), bottom - top);
        graphics.hLine(left + 12, right - 13, 58, EDGE);
        text(graphics, title, left + 14, 28, panelWidth() - 28, ACCENT);

        var field = searchField();
        rounded(graphics, field.x(), field.y(), field.width(), field.height(), 4,
                search != null && search.isFocused() ? RAISED : INSET);

        var back = backButton();
        control(graphics, back, false, back.contains(mouseX, localY), true, false);
        graphics.drawCenteredString(font, Component.translatable("gui.createcityexpansioncore.common.back"),
                back.x() + back.width() / 2, back.y() + 6, MUTED);

        var state = menu.getRelationsState();
        if (state == null) {
            text(graphics, Component.translatable("gui.createcityexpansioncore.relations.loading"),
                    left + 14, 90, panelWidth() - 28, MUTED);
        } else {
            List<CityRelationSnapshot> cities = visible();
            if (cities.isEmpty()) text(graphics, Component.translatable("gui.createcityexpansioncore.relations.empty"),
                    left + 14, 90, panelWidth() - 28, MUTED);
            for (int i = scroll; i < cities.size() && i - scroll < visibleRows(); i++) {
                renderRow(graphics, cities.get(i), i, state.canManage(), left, right, mouseX, localY, delta);
            }
        }
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics g, CityRelationSnapshot city, int index, boolean canManage,
                           int left, int right, double mx, double my, float delta) {
        int y = rowTop(index);
        int actionX = right - 120, actionW = 108;
        boolean overRow = mx >= left + 12 && mx < right - 12 && my >= y && my < y + ROW_PLATE;
        boolean overAction = canManage && mx >= actionX && mx < actionX + actionW && my >= y + 7 && my < y + 29;
        float lit = motion.hover(index, overRow || overAction, delta);

        rounded(g, left + 12, y, right - left - 24, ROW_PLATE, 4, UiMotion.mix(INSET, RAISED, lit));
        text(g, Component.literal(city.cityName()), left + 20, y + 7, actionX - left - 28, TEXT);
        text(g, Component.translatable("gui.createcityexpansioncore.relations.status."
                + city.status().name().toLowerCase(Locale.ROOT)), left + 20, y + 21, actionX - left - 28,
                statusColor(city.status()));

        Component action = Component.translatable(city.status() == CityRelationSnapshot.Status.TRUSTED
                || city.status() == CityRelationSnapshot.Status.PARTNER
                ? "gui.createcityexpansioncore.relations.remove" : "gui.createcityexpansioncore.relations.add");
        var button = new WorldMapUiLayout.Rect(actionX, y + 7, actionW, 22);
        control(g, button, false, overAction, canManage, true);
        g.drawCenteredString(font, action, button.x() + button.width() / 2, button.y() + 6,
                canManage ? (overAction ? 0xFFFFFFFF : 0xFFEFE3CC) : 0xFF6F7D80);
    }

    private static int statusColor(CityRelationSnapshot.Status status) {
        return switch (status) {
            case PARTNER -> 0xFF88C4A9;
            case TRUSTED -> 0xFF85C3DC;
            case TRUSTS_US -> 0xFFB39BD8;
            case NONE -> MUTED;
        };
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        double localY = mouseY - motion.entranceOffset(net.minecraft.Util.getMillis());
        if (backButton().contains(mouseX, localY)) { onClose(); return true; }
        var state = menu.getRelationsState();
        if (state == null || !state.canManage()) return super.mouseClicked(mouseX, mouseY, button);
        int left = panelLeft(), right = panelRight();
        List<CityRelationSnapshot> cities = visible();
        for (int i = scroll; i < cities.size() && i - scroll < visibleRows(); i++) {
            int y = rowTop(i);
            if (mouseX >= right - 120 && mouseX < right - 12 && localY >= y + 7 && localY < y + 29) {
                CityRelationSnapshot city = cities.get(i);
                boolean remove = city.status() == CityRelationSnapshot.Status.TRUSTED
                        || city.status() == CityRelationSnapshot.Status.PARTNER;
                request(remove ? CityRelationsCommandPayload.Action.REMOVE_TRUST
                        : CityRelationsCommandPayload.Action.ADD_TRUST, city.cityId());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, visible().size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll + (vertical < 0 ? 1 : -1)));
        return true;
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
