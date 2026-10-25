package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.network.CityInviteResultPayload;
import com.example.examplemod.network.CityPlayerSearchResultPayload;
import com.example.examplemod.network.InvitePlayerPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

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
public class CityInvitePlayerScreen extends CityPylonPageScreen {

    /** Debounce for the search box: long enough not to spam, short enough to feel live. */
    static final long SEARCH_DEBOUNCE_MILLIS = 300L;

    private static final int PANEL_HEIGHT = 250;
    private static final int PANEL_TOP = 46;
    /** Footer strip reserved for the back control and the page counter. */
    private static final int FOOTER_HEIGHT = 26;

    private final Screen parent;
    private final UiMotion motion = new UiMotion();
    private final ItemStack playerIcon = new ItemStack(Items.PLAYER_HEAD);

    private EditBox search;
    /** Where the field sits before the entrance slide is applied. */
    private int searchBaseY;

    private long lastEditAt;
    private String lastSentQuery = "";
    private int scroll;
    private int hoverRow = -1;
    private int lastMouseX;
    private int lastMouseY;

    public CityInvitePlayerScreen(Screen parent) {
        super(Component.translatable("gui.createcityexpansioncore.city.invite_player"));
        this.parent = parent;
    }

    /**
     * One geometry, shared by painting, clipping and hit-testing.
     *
     * <p>Capacity used to be derived from the screen height while the panel was a fixed 250 pixels
     * tall, so on any screen above ~300 pixels the surplus rows were painted and clickable on top
     * of the open world, outside the panel. Both the count and the clip now come from the panel.
     */
    private int panelLeft() { return CityInviteUiLayout.panelLeft(this.width); }
    /**
     * Delegates to the shared layout: a hard 340 here would disagree with the row geometry on a
     * narrow window, where the layout clamps the panel to fit but the fill would still be 340 wide
     * and run off the screen.
     */
    private int panelWidth() { return CityInviteUiLayout.panelWidth(this.width); }
    private int panelBottom() { return PANEL_TOP + PANEL_HEIGHT; }
    private int listTop() { return CityInviteUiLayout.listTop(this.width, PANEL_TOP, feedback()); }
    private int listBottom() { return panelBottom() - FOOTER_HEIGHT; }

    private int visibleCapacity() {
        return Math.max(1, (listBottom() - listTop()) / CityInviteUiLayout.ROW_HEIGHT);
    }

    @Override
    protected void init() {
        CityInviteClientHandler.resetSearch();
        this.searchBaseY = CityInviteUiLayout.searchField(this.width, PANEL_TOP).y();
        this.search = new EditBox(this.font,
                CityInviteUiLayout.searchField(this.width, PANEL_TOP).x() + 2,
                this.searchBaseY,
                CityInviteUiLayout.searchField(this.width, PANEL_TOP).width() - 4, 16,
                Component.translatable("gui.createcityexpansioncore.city.invite.search"));
        this.search.setBordered(false);
        this.search.setTextColor(TEXT);
        this.search.setMaxLength(64);
        this.search.setHint(Component.translatable("gui.createcityexpansioncore.city.invite.search.hint"));
        this.search.setResponder(query -> this.lastEditAt = System.currentTimeMillis());
        // addWidget rather than addRenderableWidget: the field must take keyboard and mouse input,
        // but it is painted here, inside the same translated pose as the panel. Rendering it through
        // super.render() would leave it underneath the opaque panel drawn afterwards.
        addWidget(this.search);
        setFocused(this.search);
        // The list opens on whatever the server considers recent, not on an empty box.
        CityInviteClientHandler.requestSearch(pylonPos(), "");
        this.lastSentQuery = "";
    }

    private net.minecraft.core.BlockPos pylonPos() {
        return this.parent instanceof CityPylonScreen pylon
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
        int max = Math.max(0, visibleRowCount() - visibleCapacity());
        if (this.scroll > max) {
            this.scroll = max;
        }
    }

    private int visibleRowCount() {
        return CityInviteClientHandler.results().size();
    }

    private boolean feedback() {
        return CityInviteClientHandler.hasFeedback();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = net.minecraft.Util.getMillis();
        motion.opened(now);
        float delta = motion.delta(now);
        float offset = motion.entranceOffset(now);
        // Hit-testing subtracts the value the painter added, so the rows stay under the cursor
        // for the whole entrance rather than only after it settles.
        this.lastMouseX = mouseX;
        this.lastMouseY = (int) (mouseY - offset);
        // The field is painted inside the translated pose and its own Y stays at the base value:
        // moving both would shift it by twice the offset and leave it hanging below the panel it
        // belongs to for the length of the entrance.
        this.search.setY(this.searchBaseY);

        graphics.fill(0, 0, this.width, this.height, 0x87080D10);
        graphics.pose().pushPose();
        graphics.pose().translate(0, offset, 0);
        try {
            panel(graphics, panelLeft(), PANEL_TOP, panelWidth(), PANEL_HEIGHT);
            graphics.drawString(this.font,
                    fit(this.font, Component.translatable("gui.createcityexpansioncore.city.invite.search"),
                            panelWidth() - CityInviteUiLayout.EDGE * 2),
                    panelLeft() + CityInviteUiLayout.EDGE, PANEL_TOP - 2, ACCENT, false);

            this.hoverRow = -1;
            renderFeedback(graphics);
            renderRows(graphics, delta);
            renderFooter(graphics);
            renderSearchField(graphics, delta);
        } finally {
            graphics.pose().popPose();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The search field is painted last so it reads as sitting on top of the panel, not inside it. */
    private void renderSearchField(GuiGraphics graphics, float delta) {
        var field = CityInviteUiLayout.searchField(this.width, PANEL_TOP);
        rounded(graphics, field.x(), field.y(), field.width(), field.height(), 4,
                this.search != null && this.search.isFocused() ? RAISED : INSET);
        if (this.search != null) {
            this.search.render(graphics, lastMouseX, lastMouseY, delta);
        }
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
        graphics.drawString(this.font, fit(this.font, text, bounds.width()),
                bounds.x(), bounds.y() + 4, colour, false);
    }

    private void renderRows(GuiGraphics graphics, float delta) {
        List<CityPlayerSearchResultPayload.Entry> results = CityInviteClientHandler.results();
        int capacity = visibleCapacity();
        if (results.isEmpty()) {
            Component text = CityInviteClientHandler.searching()
                    ? Component.translatable("gui.createcityexpansioncore.city.invite.search.pending")
                    : this.search.getValue().isBlank()
                            ? Component.translatable("gui.createcityexpansioncore.city.invite.search.hint")
                            : Component.translatable("gui.createcityexpansioncore.city.invite.search.empty");
            graphics.drawString(this.font, text,
                    panelLeft() + CityInviteUiLayout.EDGE, listTop() + 4, MUTED, false);
            return;
        }
        // Rows are clipped to the list area: a long result set scrolls instead of spilling over
        // the footer and out of the panel onto the world behind it.
        int clipLeft = panelLeft() + 1;
        int clipRight = panelLeft() + panelWidth() - 1;
        graphics.enableScissor(clipLeft, listTop(), clipRight, listBottom());
        try {
            for (int index = 0; index < results.size() && index - this.scroll < capacity; index++) {
                int visibleIndex = index - this.scroll;
                CityInviteUiLayout.Rect bounds =
                        CityInviteUiLayout.row(this.width, PANEL_TOP, feedback(), visibleIndex, results.size());
                if (!bounds.contains(lastMouseX, lastMouseY)) {
                    continue;
                }
                this.hoverRow = visibleIndex;
            }
            for (int index = 0; index < results.size() && index - this.scroll < capacity; index++) {
                int visibleIndex = index - this.scroll;
                // The id is the player, not the slot: re-sorting the list under the cursor must
                // move the highlight away with its row rather than leave it on whichever row is
                // now at that index.
                int id = results.get(index).playerId().hashCode();
                float lit = motion.hover(id, this.hoverRow == visibleIndex, delta);
                drawRow(graphics, results.get(index), visibleIndex, lit);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawRow(GuiGraphics graphics, CityPlayerSearchResultPayload.Entry entry,
                         int visibleIndex, float lit) {
        CityPlayerSearchResultPayload.State state = entry.state();
        boolean actionable = state == CityPlayerSearchResultPayload.State.AVAILABLE
                || state == CityPlayerSearchResultPayload.State.INVITED;
        CityInviteUiLayout.Rect bounds =
                CityInviteUiLayout.row(this.width, PANEL_TOP, feedback(), visibleIndex, visibleRowCount());
        rounded(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 3,
                UiMotion.mix(actionable ? INSET : 0xFF141B1E, RAISED, lit));
        graphics.renderItem(this.playerIcon, bounds.x() + 3, bounds.y() + 3);
        int nameWidth = CityInviteUiLayout.nameColumnWidth(this.width, actionable) - 22;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.name(), nameWidth),
                bounds.x() + 19, bounds.y() + 3, actionable ? TEXT : MUTED, false);
        graphics.drawString(this.font,
                Component.translatable(entry.online()
                        ? "gui.createcityexpansioncore.city.invite.search.online"
                        : "gui.createcityexpansioncore.city.invite.search.offline"),
                bounds.x() + 19, bounds.y() + 13, MUTED, false);

        if (!actionable) {
            Component reason = entry.cityName().isBlank()
                    ? Component.translatable(entry.reasonKey())
                    : Component.translatable(entry.reasonKey(), entry.cityName());
            int available = bounds.width() - CityInviteUiLayout.nameColumnWidth(this.width, false) - 20;
            graphics.drawString(this.font, fit(this.font, reason, available),
                    bounds.right() - available, bounds.y() + 9, MUTED, false);
            return;
        }
        CityInviteUiLayout.Rect action =
                CityInviteUiLayout.rowAction(this.width, PANEL_TOP, feedback(), visibleIndex, visibleRowCount());
        control(graphics, new WorldMapUiLayout.Rect(action.x(), action.y(), action.width(), action.height()),
                false, this.hoverRow == visibleIndex && action.contains(lastMouseX, lastMouseY), true, true);
        graphics.drawCenteredString(this.font,
                Component.translatable(state == CityPlayerSearchResultPayload.State.INVITED
                        ? "gui.createcityexpansioncore.city.invite.state.resend"
                        : "gui.createcityexpansioncore.city.invite.state.invite"),
                action.x() + action.width() / 2, action.y() + 7, TEXT);
    }

    private void renderFooter(GuiGraphics graphics) {
        int y = panelBottom() - FOOTER_HEIGHT + 6;
        graphics.hLine(panelLeft() + CityInviteUiLayout.EDGE,
                panelLeft() + panelWidth() - CityInviteUiLayout.EDGE - 1, y - 4, EDGE);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.common.back"),
                panelLeft() + CityInviteUiLayout.EDGE, y, MUTED, false);
        if (visibleRowCount() > visibleCapacity()) {
            graphics.drawString(this.font,
                    Component.literal((this.scroll + 1) + " / " + visibleRowCount()),
                    panelLeft() + panelWidth() - CityInviteUiLayout.EDGE - 30, y, MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localY = mouseY - motion.entranceOffset(net.minecraft.Util.getMillis());
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        var field = CityInviteUiLayout.searchField(this.width, PANEL_TOP);
        if (field.contains(mouseX, localY)) {
            setFocused(this.search);
            this.search.setFocused(true);
            this.search.mouseClicked(
                    Math.max(this.search.getX(), Math.min(this.search.getX() + this.search.getWidth() - 1, mouseX)),
                    this.search.getY() + 8, button);
            return true;
        }
        List<CityPlayerSearchResultPayload.Entry> results = CityInviteClientHandler.results();
        int capacity = visibleCapacity();
        for (int index = 0; index < results.size() && index - this.scroll < capacity; index++) {
            CityInviteUiLayout.Rect bounds =
                    CityInviteUiLayout.row(this.width, PANEL_TOP, feedback(), index - this.scroll, results.size());
            if (!bounds.contains(mouseX, localY)) {
                continue;
            }
            CityPlayerSearchResultPayload.Entry entry = results.get(index);
            if (entry.state() != CityPlayerSearchResultPayload.State.AVAILABLE
                    && entry.state() != CityPlayerSearchResultPayload.State.INVITED) {
                return true;
            }
            CityInviteUiLayout.Rect action = CityInviteUiLayout.rowAction(this.width, PANEL_TOP, feedback(),
                    index - this.scroll, results.size());
            if (action.contains(mouseX, localY)) {
                sendInvite(entry);
            }
            return true;
        }
        if (mouseX >= panelLeft() + CityInviteUiLayout.EDGE && mouseX < panelLeft() + 100
                && localY >= panelBottom() - FOOTER_HEIGHT && localY < panelBottom()) {
            onClose();
            return true;
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
        double localY = mouseY - motion.entranceOffset(net.minecraft.Util.getMillis());
        if (localY < listTop() || localY >= listBottom()) {
            return false;
        }
        int max = Math.max(0, visibleRowCount() - visibleCapacity());
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
