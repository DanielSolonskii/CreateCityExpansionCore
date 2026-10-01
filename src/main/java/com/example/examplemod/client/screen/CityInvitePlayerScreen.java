package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.network.CityInviteResultPayload;
import com.example.examplemod.network.CityPlayerSearchResultPayload;
import com.example.examplemod.network.InvitePlayerPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Inviting a player, driven entirely by the server.
 *
 * <p>The previous version listed {@code getConnection().getOnlinePlayers()} on the client, which is
 * why an offline player could not be invited and why every server rule stayed invisible until a
 * button silently did nothing. Candidate rows now come from
 * {@link com.example.examplemod.network.CityPlayerSearchResultPayload}, so each row already carries
 * the server's own verdict and the exact reason for it.
 *
 * <p>Layout lives in {@link CityInviteUiLayout}, so the row a player sees and the row they can click
 * cannot drift apart.
 */
public class CityInvitePlayerScreen extends Screen {

    /** Debounce for the search box: long enough not to spam, short enough to feel live. */
    static final long SEARCH_DEBOUNCE_MILLIS = 300L;

    private final Screen parent;

    private EditBox search;

    private long lastEditAt;

    private String lastSentQuery = "";

    private int scroll;

    private int hoverRow = -1;

    private int lastMouseX;

    private int lastMouseY;

    private static final int PANEL_WIDTH = 340;

    private static final int PANEL_HEIGHT = 250;

    private static final int PANEL_TOP = 46;

    private final ItemStack playerIcon = new ItemStack(Items.PLAYER_HEAD);

    public CityInvitePlayerScreen(Screen parent) {
        super(Component.translatable("gui.createcityexpansioncore.city.invite_player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CityInviteClientHandler.resetSearch();
        int left = CityInviteUiLayout.panelLeft(this.width);
        this.search = new EditBox(this.font,
                CityInviteUiLayout.searchField(this.width, PANEL_TOP).x() + 2,
                CityInviteUiLayout.searchField(this.width, PANEL_TOP).y(),
                CityInviteUiLayout.searchField(this.width, PANEL_TOP).width() - 4, 16,
                Component.translatable("gui.createcityexpansioncore.city.invite.search"));
        this.search.setBordered(false);
        this.search.setMaxLength(64);
        this.search.setHint(Component.translatable("gui.createcityexpansioncore.city.invite.search.hint"));
        this.search.setResponder(query -> this.lastEditAt = System.currentTimeMillis());
        addRenderableWidget(this.search);
        setFocused(this.search);
        // The list opens on whatever the server considers recent, not on an empty box.
        CityInviteClientHandler.requestSearch(pylonPos(), "");
        this.lastSentQuery = "";
    }

    private net.minecraft.core.BlockPos pylonPos() {
        return this.parent instanceof com.example.examplemod.client.screen.CityPylonScreen pylon
                ? pylon.getMenu().getPylonPos()
                : net.minecraft.core.BlockPos.ZERO;
    }

    @Override
    public void tick() {
        // Debounced: the query is only sent once typing has paused, never every tick.
        if (this.search == null) {
            return;
        }
        String query = this.search.getValue().trim();
        long now = System.currentTimeMillis();
        if (!query.equals(this.lastSentQuery) && now - this.lastEditAt >= SEARCH_DEBOUNCE_MILLIS) {
            CityInviteClientHandler.requestSearch(pylonPos(), query);
            this.lastSentQuery = query;
        }
        int rows = visibleRowCount();
        int max = Math.max(0, rows - visibleCapacity());
        if (this.scroll > max) {
            this.scroll = max;
        }
    }

    private int visibleCapacity() {
        return Math.max(1, (this.height - 40 - CityInviteUiLayout.listTop(0, PANEL_TOP,
                CityInviteClientHandler.hasFeedback())) / CityInviteUiLayout.ROW_HEIGHT);
    }

    private int visibleRowCount() {
        return CityInviteClientHandler.results().size();
    }

    private boolean feedback() {
        return CityInviteClientHandler.hasFeedback();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = CityInviteUiLayout.panelLeft(this.width);
        graphics.fill(0, 0, this.width, this.height, 0xFF101719);
        graphics.fill(left, PANEL_TOP - 8, left + PANEL_WIDTH, PANEL_TOP + PANEL_HEIGHT, 0xFF181E21);
        graphics.renderOutline(left, PANEL_TOP - 8, PANEL_WIDTH, PANEL_HEIGHT, 0xFF596064);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.city.invite.search"),
                left + CityInviteUiLayout.EDGE, PANEL_TOP - 2, 0xFFFFD59A, false);

        this.hoverRow = -1;
        renderFeedback(graphics);
        renderRows(graphics, mouseX, mouseY);
        renderFooter(graphics);
    }

    private void renderFeedback(GuiGraphics graphics) {
        if (!feedback()) {
            return;
        }
        CityInviteUiLayout.Rect bounds = CityInviteUiLayout.feedback(this.width, PANEL_TOP);
        int colour = CityInviteClientHandler.feedbackOk() ? 0xFF9FE1C0 : 0xFFFFA7A0;
        Component text = CityInviteClientHandler.feedbackOk()
                ? Component.translatable("gui.createcityexpansioncore.city.invite.sent",
                        CityInviteClientHandler.feedbackTarget())
                : Component.translatable(CityInviteClientHandler.feedback());
        graphics.drawString(this.font,
                text.getString().length() > bounds.width() ? Component.literal(this.font.plainSubstrByWidth(text.getString(), bounds.width())) : text, bounds.x(), bounds.y() + 4, colour, false);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CityPlayerSearchResultPayload.Entry> results = CityInviteClientHandler.results();
        int capacity = visibleCapacity();
        if (results.isEmpty()) {
            Component text = CityInviteClientHandler.searching()
                    ? Component.translatable("gui.createcityexpansioncore.city.invite.search.pending")
                    : this.search.getValue().isBlank()
                            ? Component.translatable("gui.createcityexpansioncore.city.invite.search.hint")
                            : Component.translatable("gui.createcityexpansioncore.city.invite.search.empty");
            graphics.drawString(this.font, text,
                    CityInviteUiLayout.panelLeft(this.width) + CityInviteUiLayout.EDGE,
                    CityInviteUiLayout.listTop(this.width, PANEL_TOP, feedback()) + 4, 0xFF98A3A7, false);
            return;
        }
        for (int index = 0; index < results.size() && index - this.scroll < capacity; index++) {
            CityInviteUiLayout.Rect bounds =
                    CityInviteUiLayout.row(this.width, PANEL_TOP, feedback(), index - this.scroll, results.size());
            if (!bounds.contains(mouseX, mouseY)) {
                continue;
            }
            this.hoverRow = index - this.scroll;
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF2B3437);
        }
        for (int index = 0; index < results.size() && index - this.scroll < capacity; index++) {
            drawRow(graphics, results.get(index), index - this.scroll);
        }
    }

    private void drawRow(GuiGraphics graphics, CityPlayerSearchResultPayload.Entry entry, int visibleIndex) {
        CityPlayerSearchResultPayload.State state = entry.state();
        boolean actionable = state == CityPlayerSearchResultPayload.State.AVAILABLE;
        CityInviteUiLayout.Rect bounds =
                CityInviteUiLayout.row(this.width, PANEL_TOP, feedback(), visibleIndex, visibleRowCount());
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                this.hoverRow == visibleIndex ? 0xFF2B3437 : 0xFF232A2D);
        graphics.renderItem(this.playerIcon, bounds.x() + 3, bounds.y() + 3);
        int nameWidth = CityInviteUiLayout.nameColumnWidth(this.width, actionable) - 22;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.name(), nameWidth),
                bounds.x() + 19, bounds.y() + 3, actionable ? 0xFFD8DEDF : 0xFF98A3A7, false);
        graphics.drawString(this.font,
                Component.translatable(entry.online()
                        ? "gui.createcityexpansioncore.city.invite.search.online"
                        : "gui.createcityexpansioncore.city.invite.search.offline"),
                bounds.x() + 19, bounds.y() + 13, 0xFF6E787C, false);

        if (!actionable) {
            Component reason = entry.cityName().isBlank()
                    ? Component.translatable(entry.reasonKey())
                    : Component.translatable(entry.reasonKey(), entry.cityName());
            int available = bounds.width() - CityInviteUiLayout.nameColumnWidth(this.width, false) - 20;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(reason.getString(), available),
                    bounds.right() - available, bounds.y() + 9, 0xFF9AA5A9, false);
            return;
        }
        CityInviteUiLayout.Rect action =
                CityInviteUiLayout.rowAction(this.width, PANEL_TOP, feedback(), visibleIndex, visibleRowCount());
        boolean hover = action.contains(this.lastMouseX, this.lastMouseY);
        graphics.fill(action.x(), action.y(), action.right(), action.bottom(), hover ? 0xFF4A391E : 0xFF3A4145);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.createcityexpansioncore.city.invite.state.invite"),
                action.x() + action.width() / 2, action.y() + 7, 0xFFFFD59A);
    }

    private void renderFooter(GuiGraphics graphics) {
        int left = CityInviteUiLayout.panelLeft(this.width);
        int y = PANEL_TOP + PANEL_HEIGHT - 26;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.common.back"),
                left + CityInviteUiLayout.EDGE, y, 0xFF98A3A7, false);
        if (visibleRowCount() > visibleCapacity()) {
            graphics.drawString(this.font,
                    Component.literal(this.scroll + 1 + " / " + visibleRowCount()),
                    left + PANEL_WIDTH - CityInviteUiLayout.EDGE - 30, y, 0xFF6E787C, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        List<CityPlayerSearchResultPayload.Entry> results = CityInviteClientHandler.results();
        for (int index = 0; index < results.size() && index - this.scroll < visibleCapacity(); index++) {
            CityInviteUiLayout.Rect bounds =
                    CityInviteUiLayout.row(this.width, PANEL_TOP, feedback(), index - this.scroll, results.size());
            if (!bounds.contains(mouseX, mouseY)) {
                continue;
            }
            CityPlayerSearchResultPayload.Entry entry = results.get(index);
            if (entry.state() != CityPlayerSearchResultPayload.State.AVAILABLE) {
                return true;
            }
            CityInviteUiLayout.Rect action = CityInviteUiLayout.rowAction(this.width, PANEL_TOP, feedback(),
                    index - this.scroll, results.size());
            if (action.contains(mouseX, mouseY)) {
                sendInvite(entry);
            }
            return true;
        }
        int left = CityInviteUiLayout.panelLeft(this.width);
        if (mouseX >= left + CityInviteUiLayout.EDGE && mouseX < left + 100
                && mouseY >= PANEL_TOP + PANEL_HEIGHT - 30 && mouseY < PANEL_TOP + PANEL_HEIGHT - 12) {
            onClose();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * The server decides whether this is allowed. The row is not marked as invited here: the
     * authoritative answer arrives through {@link CityInviteResultPayload}, which is also what
     * covers the race where the player joined another city in between.
     */
    private void sendInvite(CityPlayerSearchResultPayload.Entry entry) {
        PacketDistributor.sendToServer(new InvitePlayerPayload(pylonPos(), entry.playerId()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int capacity = visibleCapacity();
        int max = Math.max(0, visibleRowCount() - capacity);
        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) -scrollY));
        return true;
    }

    @Override
    public void onClose() {
        CityInviteClientHandler.resetSearch();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}