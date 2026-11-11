package com.example.examplemod.client.logistics;

import com.example.examplemod.cargo.center.CenterRequest;
import com.example.examplemod.client.CommunityFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Native dispatch workspace. Browsing is read-only; server forms own every mutation. */
public final class LogisticsCenterScreen extends Screen implements CenterResponseScreen {
    private static final String[] IDS = {"dashboard", "map", "trains", "lines", "points", "warehouses", "trade", "shipments", "access"};
    private static final String[] TASKS = {"buy", "send", "track", "network"};
    private record Navigation(int section, ActionButton button) {}
    private boolean networkMode, openNetwork, guideChecked;
    private int section, sidebar, scroll, page, detailScroll, detailContentHeight, navScroll, dashboardScroll;
    private UUID selected, lastRequest;
    private CompoundTag state = new CompoundTag();
    private long sent, received, searchChanged, sectionChanged = net.minecraft.Util.getMillis(), selectedAt, clickedAt;
    private boolean pending, reverse, onlyProblems, readTimedOut;
    private EditBox search;
    private CenterLayout layout;
    private ActionButton previousPage, nextPage, actionsButton, refreshButton, sortButton, problemButton, mapLaunchButton, networkButton;
    private final List<ActionButton> dashboardButtons = new ArrayList<>();
    private final List<Navigation> navigationButtons = new ArrayList<>();
    private String query = "", lastResult = "", actionsHint = "";

    public LogisticsCenterScreen() { super(tr("title")); }

    public void focusObject(String kind, UUID id) {
        query = ""; reverse = onlyProblems = false; page = scroll = detailScroll = dashboardScroll = 0;
        state = new CompoundTag(); received = 0;
        section = switch (kind) { case "train" -> 2; case "line" -> 3; case "point", "station" -> 4; case "warehouse" -> 5; case "offer" -> 6; case "shipment" -> 7; default -> 0; };
        selected = id;
        if (section == 2 || section == 3 || section == 4 || section == 8) networkMode = true;
    }

    @Override protected void init() {
        CommunityFx.load();
        layout = CenterLayout.of(width, height); sidebar = layout.sidebar();
        navigationButtons.clear(); dashboardButtons.clear(); actionsHint = "";
        var mode = new ActionButton(6, 42, sidebar - 12, 22,
                layout.compact() ? Component.literal(networkMode ? "II" : "I") : tr(networkMode ? "mode.network" : "mode.player"),
                () -> { networkMode = !networkMode; navScroll = 0; selectSection(0); rebuildWidgets(); });
        addRenderableWidget(mode.hint(tr("mode.help")).narration(tr(networkMode ? "mode.network" : "mode.player")));
        for (int target : navigation()) {
            var button = new ActionButton(6, 70, sidebar - 12, layout.navigationStep() - 3,
                    layout.compact() ? Component.empty() : tr("tab." + IDS[target]), () -> selectSection(target), target);
            button.hint(tr("nav_hint." + IDS[target])).narration(tr("tab." + IDS[target]));
            addWidget(button); navigationButtons.add(new Navigation(target, button));
        }
        var help = layout.help();
        addRenderableWidget(new ActionButton(help.x(), help.y(), help.width(), help.height(),
                layout.compact() ? Component.literal("?") : tr("ui.help"), this::openGuide)
                .hint(tr("ui.help_hint")).narration(tr("ui.help")));
        ensureNavigationVisible();

        var s = layout.search();
        search = new EditBox(font, s.x(), s.y(), s.width(), s.height(), tr("search"));
        search.setHint(tr("search")); search.setMaxLength(96); search.setValue(query);
        search.setResponder(value -> { query = value; searchChanged = net.minecraft.Util.getMillis(); });
        addRenderableWidget(search);
        var box = layout.toolbar(0);
        refreshButton = addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), Component.literal("↻"), this::refresh)
                .hint(tr("refresh")).narration(tr("refresh")));
        box = layout.toolbar(1);
        sortButton = addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), Component.literal("↕"),
                () -> { reverse = !reverse; scroll = 0; }).hint(tr("sort")).narration(tr("sort")));
        box = layout.toolbar(2);
        problemButton = addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), Component.literal("!"),
                () -> { onlyProblems = !onlyProblems; scroll = 0; }).hint(tr("problems")).narration(tr("problems")));
        box = layout.map();
        addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), tr("map"), this::openMap).hint(tr("map_hint")));
        box = layout.previous();
        previousPage = addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), Component.literal("‹"), () -> {
            if (page > 0) { page--; scroll = 0; refresh(); }
        }).hint(tr("ui.previous_page")).narration(tr("ui.previous_page")));
        box = layout.next();
        nextPage = addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), Component.literal("›"), () -> {
            if ((page + 1) * 32 < state.getInt("Total")) { page++; scroll = 0; refresh(); }
        }).hint(tr("ui.next_page")).narration(tr("ui.next_page")));
        box = layout.actions();
        actionsButton = addRenderableWidget(new ActionButton(box.x(), box.y(), box.width(), box.height(), tr("actions"),
                () -> minecraft.setScreen(new CenterActionsScreen(this, state))).primary().hint(tr("home.actions_help")));
        mapLaunchButton = addRenderableWidget(new ActionButton(layout.left() + 16, height - 91,
                Math.min(230, layout.area() - 32), 28, tr("ui.open_map"), this::openMap, 1).primary());
        networkButton = addRenderableWidget(new ActionButton(s.x(), s.y(), s.width(), s.height(), tr("visual.network"),
                () -> minecraft.setScreen(new CenterNetworkScreen(this))).hint(tr("visual.network_help")));
        int[] targets = {6, 5, 7, 3}, symbols = {6, 5, 7, 3};
        for (int i = 0; i < TASKS.length; i++) {
            final int target = targets[i]; var card = layout.dashboardCard(i);
            var button = new ActionButton(card.x(), card.y(), card.width(), Math.min(34, card.height()), tr("home." + TASKS[i]), () -> {
                if (target == 3) { networkMode = true; selectSection(target); openNetwork = true; rebuildWidgets(); }
                else selectSection(target);
            }, symbols[i]).hint(tr("home." + TASKS[i] + ".hint"));
            addWidget(button); dashboardButtons.add(button);
        }
        updateControls();
        refresh();
    }

    private void updateControls() {
        if (search == null) return;
        boolean list = section >= 2;
        networkButton.visible = section == 3;
        search.visible = list && section != 3; search.active = search.visible;
        if (!search.visible && search.isFocused()) setFocused(null);
        refreshButton.visible = sortButton.visible = problemButton.visible = list;
        sortButton.selected(reverse); problemButton.selected(onlyProblems);
        mapLaunchButton.visible = section == 1;
        previousPage.visible = nextPage.visible = list && state.getInt("Total") > 32;
        previousPage.active = !pending && page > 0;
        nextPage.active = !pending && (page + 1) * 32 < state.getInt("Total");
        actionsButton.visible = list;
        actionsButton.active = !pending && !state.getList("Forms", 10).isEmpty();
        String hint = actionsButton.active ? "home.actions_help" : pending ? "ui.wait_actions" : "ui.no_actions";
        if (!hint.equals(actionsHint)) { actionsHint = hint; actionsButton.hint(tr(hint)); }
        positionDashboard();
        positionNavigation();
    }

    private int dashboardContentBottom() {
        int bottom = layout.dashboardCard(3).bottom();
        if (height < 260) return bottom + 4;
        int columns = layout.area() >= 440 ? 4 : 2;
        return bottom + 18 + 20 + ((4 + columns - 1) / columns) * 46 + 18
                + font.wordWrapHeight(tr("ui.dashboard_tip"), layout.area() - 4) + 8;
    }

    private void positionDashboard() {
        int bottom = height - 58;
        dashboardScroll = Math.clamp(dashboardScroll, 0, CenterPresentation.scrollLimit(dashboardContentBottom() - 58, bottom - 58));
        for (int i = 0; i < dashboardButtons.size(); i++) {
            var box = layout.dashboardCard(i); var button = dashboardButtons.get(i);
            button.setY(box.y() - dashboardScroll);
            button.visible = section == 0 && button.getY() >= 58 && button.getY() + button.getHeight() <= bottom;
            button.active = button.visible;
        }
    }

    private void positionNavigation() {
        if (layout == null) return;
        navScroll = Math.clamp(navScroll, 0, layout.navigationLimit(navigationButtons.size()));
        var viewport = layout.navigation();
        for (int i = 0; i < navigationButtons.size(); i++) {
            var entry = navigationButtons.get(i); var box = layout.navigationRow(i, navScroll);
            entry.button().setY(box.y()); entry.button().selected(entry.section() == section);
            entry.button().visible = box.y() >= viewport.y() && box.bottom() <= viewport.bottom();
        }
    }

    private void ensureNavigationVisible() {
        int[] nav = navigation();
        for (int i = 0; i < nav.length; i++) if (nav[i] == section) {
            int top = i * layout.navigationStep(), bottom = top + layout.navigationStep();
            if (top < navScroll) navScroll = top;
            if (bottom > navScroll + layout.navigation().height()) navScroll = bottom - layout.navigation().height();
        }
        positionNavigation();
    }

    private void request(CompoundTag q) {
        q.putBoolean("CenterV2", true); q.putUUID("Request", UUID.randomUUID()); q.putString("Section", IDS[section]);
        q.putString("Search", query); q.putInt("Page", page); if (selected != null) q.putUUID("Selected", selected);
        lastRequest = q.getUUID("Request"); PacketDistributor.sendToServer(new CenterRequest(q));
        sent = net.minecraft.Util.getMillis(); pending = true; readTimedOut = false;
    }
    private void refresh() {
        if (minecraft == null || minecraft.player == null) return;
        var q = new CompoundTag(); q.putString("Action", "VIEW");
        if (state.contains("Hash")) q.putInt("Hash", state.getInt("Hash")); request(q);
    }
    UUID mutate(String action, CompoundTag values) { return mutate(action, values, null); }
    UUID searchStock(String value) {
        var q = new CompoundTag(); q.putString("Action", "VIEW"); q.putString("StockSearch", value); request(q); return lastRequest;
    }
    CompoundTag snapshot() { return state.copy(); }
    void selectForDetails(UUID id) { selected = id; detailScroll = 0; selectedAt = net.minecraft.Util.getMillis(); refresh(); }
    UUID mutate(String action, CompoundTag values, UUID quote) {
        var q = new CompoundTag(); q.putString("Action", action); q.putString("Kind", state.getCompound("SelectedRow").getString("Kind"));
        q.putBoolean("Confirmed", true); q.put("Values", values.copy()); if (quote != null) q.putUUID("QuoteToken", quote);
        request(q); return lastRequest;
    }
    @Override public void accept(CompoundTag data) {
        if (data.hasUUID("Request") && !data.getUUID("Request").equals(lastRequest) && !data.getBoolean("Open")) return;
        if (!data.getString("Section").equals(IDS[section]) && !data.getBoolean("Open")) return;
        if (!data.getBoolean("Unchanged")) { state = com.example.examplemod.cargo.center.CenterDelta.apply(state, data); page = state.getInt("Page"); }
        else if (data.contains("Hash")) state.putInt("Hash", data.getInt("Hash"));
        received = net.minecraft.Util.getMillis(); pending = false; readTimedOut = false;
        lastResult = !data.getString("Result").equals("ok") || data.getBoolean("Mutation") ? result(data.getString("Result")) : "";
        updateControls();
        if (openNetwork && section == 3 && minecraft.screen == this) { openNetwork = false; minecraft.setScreen(new CenterNetworkScreen(this)); }
    }
    @Override public void tick() {
        super.tick(); long now = net.minecraft.Util.getMillis();
        if (pending && CenterPresentation.timedOut(sent, now)) { pending = false; readTimedOut = true; lastResult = text("timeout"); }
        if (searchChanged > 0 && now - searchChanged > 350) { searchChanged = 0; page = scroll = 0; refresh(); }
        else if (!pending && !readTimedOut && now - sent > 3000) refresh();
        updateControls();
        if (!guideChecked && minecraft.screen == this && minecraft.player != null && (received > 0 || readTimedOut)) {
            guideChecked = true;
            if (LogisticsTutorial.shouldOpen()) minecraft.setScreen(new CenterGuideScreen(this));
        }
    }
    private List<CompoundTag> rows() {
        var rows = new ArrayList<CompoundTag>();
        for (Tag raw : state.getList("Rows", 10)) {
            var row = (CompoundTag) raw;
            if (!onlyProblems || CenterPresentation.problem(row.getString("Status"))) rows.add(row);
        }
        if (reverse) Collections.reverse(rows); return rows;
    }
    private void selectSection(int next) {
        openNetwork = false; query = "";
        if (search != null) search.setValue("");
        searchChanged = 0; reverse = onlyProblems = false; section = next; selected = null;
        scroll = page = detailScroll = dashboardScroll = 0; state = new CompoundTag(); received = 0; lastResult = "";
        sectionChanged = net.minecraft.Util.getMillis(); ensureNavigationVisible(); updateControls(); refresh();
    }
    private int[] navigation() { return networkMode ? new int[]{0, 2, 3, 4, 5, 8, 1, 6, 7} : new int[]{0, 6, 5, 7, 1}; }
    private void openMap() { LogisticsMapOverlay.open(this, state.getCompound("SelectedRow")); }
    private void openGuide() { guideChecked = true; minecraft.setScreen(new CenterGuideScreen(this)); }
    private void openDetails(CompoundTag row) { minecraft.setScreen(new CenterDetailsScreen(this, row)); }

    @Override public void render(GuiGraphics g, int mx, int my, float dt) {
        background(g, width, height);
        g.fill(0, 0, sidebar, height, 0xff0c141f); g.vLine(sidebar, 0, height, EDGE);
        icon(g, 17, 15, 3, TEAL);
        if (!layout.compact()) { g.drawString(font, "CCEC", 39, 15, TEXT, false); g.drawString(font, text("ui.workspace"), 16, 29, MUTED, false); }
        var nav = layout.navigation();
        g.enableScissor(nav.x(), nav.y(), nav.right(), nav.bottom());
        for (var entry : navigationButtons) if (entry.button().visible) entry.button().render(g, mx, my, dt);
        g.disableScissor();
        if (layout.navigationLimit(navigationButtons.size()) > 0) {
            int thumb = Math.max(10, nav.height() * nav.height() / (navigationButtons.size() * layout.navigationStep()));
            int y = nav.y() + (nav.height() - thumb) * navScroll / layout.navigationLimit(navigationButtons.size());
            g.fill(sidebar - 3, y, sidebar - 2, y + thumb, TEAL);
        }
        int left = layout.left(), area = layout.area();
        g.drawString(font, text("ui.workspace") + " / " + text(networkMode ? "mode.network" : "mode.player"), left, 13, MUTED, false);
        g.pose().pushPose(); g.pose().translate(left, 30, 0); g.pose().scale(1.35f, 1.35f, 1);
        g.drawString(font, fit(text("tab." + IDS[section]), (int) (area / 1.35f)), 0, 0, TEXT, false); g.pose().popPose();
        float reveal = reducedMotion() ? 1 : CenterPresentation.ease(net.minecraft.Util.getMillis() - sectionChanged, 260);
        g.fill(left, 49, left + (int) (Math.min(area, 100) * reveal), 50, TEAL);
        if (section == 0) renderDashboard(g, mx, my, dt);
        else if (section == 1) renderMapPreview(g);
        else {
            surface(g, left, layout.toolbarY() - 6, area, 31, 0xff121e2c);
            renderRows(g, mx, my);
            if (layout.detailWidth() > 0) renderDetails(g, layout.details(), mx, my);
        }
        renderStatus(g);
        super.render(g, mx, my, dt);
        if (section >= 2 && mx >= left && mx < left + area && my >= layout.body().y() - 7 && my < layout.body().y())
            g.renderTooltip(font, tr("section_hint." + IDS[section]), mx, my);
        if (my > height - 22 && mx > sidebar && !lastResult.isEmpty()) g.renderTooltip(font, Component.literal(lastResult), mx, my);
    }

    private void renderRows(GuiGraphics g, int mx, int my) {
        var rows = rows(); var viewport = layout.list();
        scroll = Math.clamp(scroll, 0, CenterPresentation.scrollLimit(layout.listContentHeight(rows.size()), viewport.height()));
        g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        long now = net.minecraft.Util.getMillis();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); var box = layout.row(i, scroll);
            if (box.bottom() < viewport.y() || box.y() > viewport.bottom()) continue;
            boolean picked = row.hasUUID("Id") && row.getUUID("Id").equals(selected), hover = box.contains(mx, my);
            float enter = reducedMotion() ? 1 : CenterPresentation.ease(now - sectionChanged - Math.min(i, 8) * 22L, 240);
            surface(g, box.x(), box.y(), box.width(), box.height(), blend(BG, picked ? 0xff1b3a3b : hover ? 0xff213446 : PANEL, enter));
            if (picked) g.fill(box.x(), box.y() + 3, box.x() + 2, box.bottom() - 3, TEAL);
            int symbol = switch (row.getString("Kind")) { case "station" -> 11; case "train" -> 2; case "line" -> 3; case "point" -> 4; case "warehouse" -> 5; case "offer" -> 6; case "shipment" -> 7; default -> section; };
            icon(g, box.x() + 11, box.y() + 9, symbol, picked ? TEAL : MUTED);
            int statusWidth = Math.min(104, Math.max(50, box.width() / 3));
            g.drawString(font, fit(name(row), box.width() - statusWidth - 48), box.x() + 32, box.y() + 9, TEXT, false);
            boolean problem = CenterPresentation.problem(row.getString("Status"));
            g.drawString(font, fit(value(row.getString("Status")), statusWidth - 8), box.right() - statusWidth, box.y() + 9, problem ? BRASS : TEAL, false);
            g.drawString(font, fit(summary(row), box.width() - 70), box.x() + 32, box.y() + box.height() - 15, MUTED, false);
            g.drawString(font, "›", box.right() - 18, box.y() + box.height() - 16, TEAL, false);
        }
        if (rows.isEmpty()) renderEmpty(g, viewport);
        g.disableScissor();
        int limit = CenterPresentation.scrollLimit(layout.listContentHeight(rows.size()), viewport.height());
        if (limit > 0) scrollbar(g, viewport.right() + 3, viewport.y(), viewport.height(), layout.listContentHeight(rows.size()), scroll);
        if (viewport.contains(mx, my)) {
            int index = (my - viewport.y() + scroll) / layout.rowStep();
            if (index >= 0 && index < rows.size() && layout.row(index, scroll).contains(mx, my)) {
                var row = rows.get(index); var box = layout.row(index, scroll);
                if (mx > box.right() - 28) g.renderTooltip(font, tr("ui.open_details"), mx, my);
                else if (mx > box.right() - Math.min(104, Math.max(50, box.width() / 3)))
                    g.renderTooltip(font, Component.literal(value(row.getString("Status"))).append(" · ").append(tr("section_hint." + IDS[section])), mx, my);
                else if (font.width(name(row)) > box.width() - 150) g.renderTooltip(font, Component.literal(name(row)), mx, my);
            }
        }
    }

    private void renderEmpty(GuiGraphics g, CenterLayout.Rect box) {
        surface(g, box.x(), box.y(), box.width(), box.height(), PANEL);
        String key = readTimedOut ? "timeout" : received == 0 ? "loading" : onlyProblems ? "filtered_empty" : !query.isBlank() ? "search_empty" : state.getBoolean("Implemented") ? "empty" : "unavailable";
        int y = box.y() + 12;
        icon(g, box.x() + 12, y, readTimedOut ? 13 : section, readTimedOut ? BRASS : TEAL);
        g.drawWordWrap(font, tr(key), box.x() + 34, y, Math.max(30, box.width() - 48), MUTED);
        if (box.height() > 90 && received > 0 && query.isBlank() && !onlyProblems) {
            int hintY = y + font.wordWrapHeight(tr(key), Math.max(30, box.width() - 48)) + 20;
            g.drawWordWrap(font, tr("section_hint." + IDS[section]), box.x() + 14, hintY, box.width() - 28, MUTED);
        }
    }

    private void renderDashboard(GuiGraphics g, int mx, int my, float dt) {
        int left = layout.left(), area = layout.area(), bottom = height - 58;
        if (height >= 260) g.drawString(font, text(networkMode ? "home.network_intro" : "home.intro"), left, 65, MUTED, false);
        positionDashboard();
        g.enableScissor(left, 58, left + area, bottom);
        int maxBottom = 0;
        for (int i = 0; i < TASKS.length; i++) {
            var box = layout.dashboardCard(i); int y = box.y() - dashboardScroll;
            maxBottom = Math.max(maxBottom, box.bottom());
            surface(g, box.x(), y, box.width(), box.height(), PANEL);
            var button = dashboardButtons.get(i);
            if (button.visible) button.render(g, mx, my, dt);
            if (box.height() > 40 && y + box.height() - 6 > 58 && y + 37 < bottom) {
                g.enableScissor(box.x() + 8, Math.max(58, y + 37), box.right() - 8, Math.max(58, Math.min(bottom, y + box.height() - 6)));
                g.drawWordWrap(font, tr("home." + TASKS[i] + ".hint"), box.x() + 10, y + 40, box.width() - 20, MUTED);
                g.disableScissor();
            }
        }
        if (height >= 260) {
            int top = maxBottom + 18 - dashboardScroll;
            g.drawString(font, text("home.available"), left, top, MUTED, false);
            String[] metrics = {"TrainCount", "PointCount", "WarehouseCount", "ShipmentCount"}; int[] symbols = {2, 4, 5, 7};
            int columns = area >= 440 ? 4 : 2, cw = (area - (columns - 1) * 8) / columns;
            for (int i = 0; i < metrics.length; i++) {
                int x = left + i % columns * (cw + 8), y = top + 20 + i / columns * 46;
                surface(g, x, y, cw, 39, 0xff142333); icon(g, x + 9, y + 12, symbols[i], TEAL);
                g.drawString(font, received == 0 ? "—" : Integer.toString(state.getInt(metrics[i])), x + 30, y + 7, TEXT, false);
                g.drawString(font, fit(text("metric." + metrics[i]), cw - 38), x + 30, y + 23, MUTED, false);
            }
            int hintY = top + 20 + ((4 + columns - 1) / columns) * 46 + 18;
            g.drawWordWrap(font, tr("ui.dashboard_tip"), left + 2, hintY, area - 4, MUTED);
        }
        g.disableScissor();
    }

    private void renderMapPreview(GuiGraphics g) {
        int x = layout.left(), w = layout.area(), top = 62, bottom = height - 58;
        surface(g, x, top, w, Math.max(36, bottom - top), PANEL);
        g.enableScissor(x + 8, top + 8, x + w - 8, bottom - 6);
        if (height >= 260) {
            int cy = top + 55, a = x + w / 5, b = x + w / 2, c = x + w * 4 / 5;
            g.hLine(a, c, cy, EDGE);
            if (!reducedMotion()) {
                int p = a + (int) ((net.minecraft.Util.getMillis() % 2600) / 2600.0 * (c - a));
                g.fill(p, cy - 1, p + 4, cy + 2, TEAL);
            }
            for (int node : new int[]{a, b, c}) { surface(g, node - 14, cy - 14, 28, 28, 0xff203547); icon(g, node - 6, cy - 6, node == b ? 2 : 5, TEAL); }
            g.drawCenteredString(font, tr("ui.route_preview"), x + w / 2, top + 14, MUTED);
            g.drawWordWrap(font, tr("map_hint"), x + 16, cy + 33, w - 32, MUTED);
        } else if (height >= 220) g.drawWordWrap(font, tr("ui.map_compact"), x + 16, top + 12, w - 32, MUTED);
        g.disableScissor();
    }

    private void renderDetails(GuiGraphics g, CenterLayout.Rect box, int mx, int my) {
        surface(g, box.x(), box.y(), box.width(), box.height(), PANEL);
        float reveal = reducedMotion() ? 1 : CenterPresentation.ease(net.minecraft.Util.getMillis() - selectedAt, 240);
        g.fill(box.x() + 3, box.y(), box.x() + 3 + (int) ((box.width() - 6) * reveal), box.y() + 1, TEAL);
        var row = state.getCompound("SelectedRow");
        if (row.isEmpty()) detailScroll = 0;
        g.enableScissor(box.x() + 8, box.y() + 8, box.right() - 8, box.bottom() - 8);
        int y = box.y() + 14 - detailScroll, visualTop = -1;
        if (row.isEmpty()) {
            icon(g, box.x() + 14, y, 13, TEAL);
            g.drawWordWrap(font, tr("select_hint"), box.x() + 14, y + 26, box.width() - 28, MUTED); y += 80;
        } else {
            var title = Component.literal(name(row));
            g.drawWordWrap(font, title, box.x() + 14, y, box.width() - 28, TEXT);
            y += font.wordWrapHeight(title, box.width() - 28) + 8;
            g.drawString(font, fit(value(row.getString("Status")), box.width() - 28), box.x() + 14, y,
                    CenterPresentation.problem(row.getString("Status")) ? BRASS : TEAL, false); y += 24;
            if (box.height() >= 180) { visualTop = y; y = CenterVisualContent.render(g, row, box.x() + 14, y, box.width() - 28, selectedAt, mx, my); }
            var details = row.copy();
            details.remove("Stock"); details.remove("Details");
            for (var field : fields(details)) {
                icon(g, box.x() + 14, y, field.icon(), MUTED);
                var label = Component.literal(field.label());
                g.drawWordWrap(font, label, box.x() + 33, y, box.width() - 47, MUTED);
                y += font.wordWrapHeight(label, box.width() - 47) + 5;
                var value = Component.literal(field.value());
                g.drawWordWrap(font, value, box.x() + 14, y, box.width() - 28, TEXT);
                y += font.wordWrapHeight(value, box.width() - 28) + 16;
            }
        }
        detailContentHeight = y + detailScroll - box.y() + 14;
        detailScroll = Math.clamp(detailScroll, 0, CenterPresentation.scrollLimit(detailContentHeight, box.height() - 16));
        g.disableScissor();
        if (detailContentHeight > box.height() - 16) scrollbar(g, box.right() - 4, box.y() + 8, box.height() - 16, detailContentHeight, detailScroll);
        if (visualTop >= 0 && mx >= box.x() + 8 && mx < box.right() - 8 && my >= box.y() + 8 && my < box.bottom() - 8)
            CenterVisualContent.tooltip(g, row, box.x() + 14, visualTop, box.width() - 28, mx, my);
    }

    private void renderStatus(GuiGraphics g) {
        int left = layout.left(), right = width - layout.margin();
        g.fill(sidebar + 1, height - 54, width, height, BG); g.hLine(left, right, height - 55, EDGE);
        if (pending) {
            int x = reducedMotion() ? left : left + (int) ((net.minecraft.Util.getMillis() % 1400) / 1400.0 * Math.max(1, layout.area() - 30));
            g.fill(x, height - 55, Math.min(right, x + 30), height - 54, TEAL);
        }
        String message = !lastResult.isEmpty() ? lastResult : pending ? text("sync") : received == 0 ? text("loading") : text("fresh");
        int color = readTimedOut || !lastResult.isEmpty() && !lastResult.equals(text("success")) ? BRASS : MUTED;
        g.fill(left + 1, height - 13, left + 4, height - 10, readTimedOut ? BRASS : pending ? MUTED : TEAL);
        g.drawString(font, fit(message, layout.area() - 14), left + 10, height - 16, color, false);
        if (section >= 2 && layout.area() > 380) {
            String label = state.getInt("Total") > 32 ? (page + 1) + " / " + ((state.getInt("Total") + 31) / 32)
                    : Component.translatable("ccec.center.count", state.getInt("Total")).getString();
            g.drawString(font, fit(label, 145), layout.next().right() + 12, height - 40, MUTED, false);
        }
    }

    private static void scrollbar(GuiGraphics g, int x, int top, int viewport, int content, int scroll) {
        int thumb = Math.max(8, viewport * viewport / Math.max(1, content));
        int y = top + (viewport - thumb) * scroll / Math.max(1, content - viewport);
        g.fill(x, top, x + 2, top + viewport, EDGE); g.fill(x, y, x + 2, y + thumb, TEAL);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return super.mouseClicked(x, y, button);
        if (section >= 2 && layout.list().contains(x, y)) {
            int index = (int) (y - layout.list().y() + scroll) / layout.rowStep(); var rows = rows();
            if (index >= 0 && index < rows.size() && layout.row(index, scroll).contains(x, y)) {
                var row = rows.get(index);
                if (!row.hasUUID("Id")) return true;
                long now = net.minecraft.Util.getMillis();
                boolean doubleClick = row.getUUID("Id").equals(selected) && now - clickedAt < 300;
                clickedAt = now;
                if (layout.detailWidth() == 0 || x >= layout.list().right() - 28 || doubleClick) openDetails(row);
                else selectForDetails(row.getUUID("Id"));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 290 || key == 72 && hasControlDown()) { openGuide(); return true; }
        if (!search.isFocused() && (key == 70 && hasControlDown() || key == 47)) { if (search.visible) { setFocused(search); search.setFocused(true); return true; } }
        if (!search.isFocused() && key == 82 && hasControlDown()) { refresh(); return true; }
        if (getFocused() == null && key == 257 && !state.getCompound("SelectedRow").isEmpty()) { if (claimActivationKey(key)) openDetails(state.getCompound("SelectedRow")); return true; }
        if (!search.isFocused() && (key == 266 || key == 267) && section >= 2) {
            int limit = CenterPresentation.scrollLimit(layout.listContentHeight(rows().size()), layout.list().height());
            scroll = Math.clamp(scroll + (key == 267 ? 1 : -1) * layout.list().height(), 0, limit); return true;
        }
        if (!search.isFocused() && hasControlDown() && (key == 264 || key == 265)) {
            int[] nav = navigation(); for (int i = 0; i < nav.length; i++) if (nav[i] == section) { selectSection(nav[Math.floorMod(i + (key == 264 ? 1 : -1), nav.length)]); break; } return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (layout.navigation().contains(x, y)) { navScroll = Math.clamp(navScroll - (int) (vertical * layout.navigationStep()), 0, layout.navigationLimit(navigationButtons.size())); positionNavigation(); return true; }
        if (section == 0 && x > sidebar) { dashboardScroll = Math.max(0, dashboardScroll - (int) (vertical * 28)); positionDashboard(); return true; }
        if (section < 2) return super.mouseScrolled(x, y, horizontal, vertical);
        if (layout.detailWidth() > 0 && layout.details().contains(x, y)) {
            detailScroll = Math.clamp(detailScroll - (int) (vertical * 26), 0, CenterPresentation.scrollLimit(detailContentHeight, layout.details().height() - 16)); return true;
        }
        if (layout.list().contains(x, y)) { scroll = Math.clamp(scroll - (int) (vertical * 28), 0, CenterPresentation.scrollLimit(layout.listContentHeight(rows().size()), layout.list().height())); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean isPauseScreen() { return false; }
}
