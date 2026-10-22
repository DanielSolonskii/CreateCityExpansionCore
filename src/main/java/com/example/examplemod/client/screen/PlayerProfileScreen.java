package com.example.examplemod.client.screen;

import com.example.examplemod.network.*;
import com.example.examplemod.profile.PlayerProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Supplier;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
import static com.example.examplemod.profile.PlayerProfile.*;

/** A single bounded screen for self and other players. No container, inventory slots or map widgets. */
public final class PlayerProfileScreen extends Screen {
    private final Screen parent;
    private final UUID target;
    private PlayerProfileLayout layout;
    private PlayerProfile profile;
    private UUID pending;
    private long requestedAt, openedAt, switchedAt, refreshedAt;
    private String result = "loading", skinKey = "";
    private Supplier<PlayerSkin> skin;
    private EditBox about;
    private boolean editing;
    private boolean draggingScroll;
    private List<Metric> showcase = new ArrayList<>(DEFAULT_SHOWCASE);
    private int tab, scroll, contentHeight;
    private final float[] hover = new float[3];
    private long previousFrame;
    private Component tooltip;
    private EditBox search;
    private boolean searching, searchDirty;
    private long searchChangedAt, searchRequestedAt;
    private UUID searchPending;
    private ProfileDirectoryPayload directory;
    private String searchResult = "search_intro";
    public PlayerProfileScreen(Screen parent, UUID target) {
        super(tr("title")); this.parent = parent; this.target = target;
    }
    public static void openSearch(Screen parent) {
        var client=Minecraft.getInstance(); if(client.player==null)return;
        var screen=new PlayerProfileScreen(parent,client.player.getUUID()); screen.searching=true; SocialHubScreen.show(parent,screen,SocialHubScreen.Section.PROFILE);
    }
    public static void open(Screen parent, UUID target) {
        SocialHubScreen.show(parent,new PlayerProfileScreen(parent,target),SocialHubScreen.Section.PROFILE);
    }
    public static void accept(ProfileResponsePayload response) {
        if(SocialHubScreen.acceptProfile(response))return;var screen=SocialHubScreen.find(PlayerProfileScreen.class,s->response.request().equals(s.pending));if(screen==null)return;
        screen.pending = null; screen.result = response.result(); screen.refreshedAt = net.minecraft.Util.getMillis();
        if (response.profile() == null || !screen.target.equals(response.profile().id())) return;
        screen.profile = response.profile();
        if (response.result().equals("saved")) screen.editing = false;
        if (!screen.editing) { screen.about.setValue(screen.profile.settings().about()); screen.showcase = new ArrayList<>(screen.profile.settings().showcase()); }
        screen.about.setVisible(screen.editing);
        screen.updateSkin();
    }
    public static void acceptDirectory(ProfileDirectoryPayload response) {
        var screen=SocialHubScreen.find(PlayerProfileScreen.class,s->response.request().equals(s.searchPending));if(screen==null)return;
        screen.searchPending = null;
        if (!response.query().equals(screen.search.getValue())) return;
        screen.directory = response;
        screen.searchResult = response.result().equals("ok") ? "search_empty" : "result." + response.result();
        screen.scroll = 0;
    }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.createcityexpansioncore.profile." + key, args); }
    private boolean owner() { return minecraft.player != null && minecraft.player.getUUID().equals(target); }
    public boolean ownProfileView(){var p=Minecraft.getInstance().player;return !searching&&p!=null&&target.equals(p.getUUID());}
    @Override protected void init() {
        layout = PlayerProfileLayout.calculate(width, height);var hub=SocialHubScreen.geometry(this);if(hub!=null)layout=new PlayerProfileLayout(width,height,hub.pageHeight(),hub.scale(),hub.pageX(PlayerProfileLayout.WIDTH),hub.pageY());
        com.example.examplemod.client.AllianceClient.profile(target);
        PacketDistributor.sendToServer(new CommunityRequestPayload(UUID.randomUUID(),CommunityRequestPayload.Action.VIEW,null,null,null,0,0,""));
        String draft = about == null ? profile == null ? "" : profile.settings().about() : about.getValue();
        var bounds = layout.aboutField();
        about = new EditBox(font, bounds.x() + 5, bounds.y() + 7, bounds.width() - 10, 12, tr("about"));
        about.setMaxLength(Settings.MAX_ABOUT); about.setBordered(false); about.setTextColor(TEXT); about.setValue(draft);
        about.setVisible(editing); addWidget(about);
        String query = search == null ? "" : search.getValue();
        var field = layout.searchField();
        search = new EditBox(font, field.x() + 8, field.y() + 9, field.width() - 16, 12, tr("search_placeholder"));
        search.setMaxLength(16); search.setBordered(false); search.setTextColor(TEXT);
        search.setFilter(value -> value.matches("[A-Za-z0-9_]{0,16}"));
        search.setHint(tr("search_placeholder")); search.setValue(query); search.setVisible(searching); addWidget(search);
        search.setResponder(value -> {
            searchDirty = true; searchChangedAt = net.minecraft.Util.getMillis(); directory = null; scroll = 0; searchResult = "result.loading";
        });
        if (searching) setInitialFocus(search);
        if (openedAt == 0) { com.example.examplemod.client.CommunityFx.sound("open");openedAt = switchedAt = net.minecraft.Util.getMillis(); request(false, 0); }
        else if (profile == null && pending == null) request(false, 0);
        if (searching && directory == null && searchPending == null) requestSearch(0);
    }
    private void requestSearch(int page) {
        if (searchPending != null) return;
        searchPending = UUID.randomUUID(); searchRequestedAt = net.minecraft.Util.getMillis();
        searchDirty = false; searchResult = "result.loading";
        PacketDistributor.sendToServer(new ProfileSearchPayload(searchPending, search.getValue(), page));
    }
    private void showSearch(boolean show) {
        searching = show; search.setVisible(show); scroll = 0; switchedAt = net.minecraft.Util.getMillis();
        setFocused(show ? search : null); search.setFocused(show);
        if (show && directory == null) requestSearch(0);
    }
    private void request(boolean save, int page) {
        if (pending != null) return;
        Settings settings;
        try { settings = save ? new Settings(about.getValue(), showcase, profile.settings().revision()) : Settings.defaults(); }
        catch (IllegalArgumentException invalid) { result = "invalid"; return; }
        pending = UUID.randomUUID(); requestedAt = net.minecraft.Util.getMillis(); result = save ? "saving" : "loading";
        PacketDistributor.sendToServer(new ProfileRequestPayload(pending, target, save, page, settings));
    }
    private void updateSkin() {
        String next = profile.texture() + profile.signature() + profile.skinName();
        if (skin != null && next.equals(skinKey)) return;
        skinKey = next;
        GameProfile gameProfile = new GameProfile(target, profile.skinName().isEmpty() ? profile.name() : profile.skinName());
        if (!profile.texture().isEmpty()) gameProfile.getProperties().put("textures", new Property("textures", profile.texture(), profile.signature().isEmpty() ? null : profile.signature()));
        skin = minecraft.getSkinManager().lookupInsecure(gameProfile);
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.getConnection() == null) { minecraft.setScreen(null); return; }
        long now = net.minecraft.Util.getMillis();
        if (pending != null && now - requestedAt >= 8000) { pending = null; result = "timeout"; }
        if (searchPending != null && now - searchRequestedAt >= 8000) { searchPending = null; searchResult = "result.timeout"; }
        if (searching && searchDirty && searchPending == null && now - searchChangedAt >= 350) requestSearch(0);
        if (profile != null && !editing && !searching && pending == null && (result.equals("ok") || result.equals("saved")) && now - refreshedAt >= 30_000) request(false, profile.page());
    }
    private float entranceOffset() { if(com.example.examplemod.client.CommunityFx.reduced)return 0; return 8 * (1 - ease((net.minecraft.Util.getMillis() - openedAt) / 180f)); }
    private static float ease(float value) { float t = Math.max(0, Math.min(1, value)); return 1 - (1 - t) * (1 - t) * (1 - t); }
    private PlayerProfileLayout.Rect shifted(PlayerProfileLayout.Rect r) { return new PlayerProfileLayout.Rect(r.x(), r.y() - scroll, r.width(), r.height()); }
    private void text(GuiGraphics g, Component label, int x, int y, int w, int color) { g.drawString(font, fit(font, label, Math.max(0, w)), x, y, color, false); }
    private void plate(GuiGraphics g, PlayerProfileLayout.Rect r, int colour) {
        rounded(g, r.x(), r.y(), r.width(), r.height(), 5, EDGE);
        rounded(g, r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, 4, colour);
    }
    private void button(GuiGraphics g, PlayerProfileLayout.Rect r, Component label, boolean primary, boolean active, boolean enabled, double mx, double my) {
        WorldMapChrome.control(g, new WorldMapUiLayout.Rect(r.x(), r.y(), r.width(), r.height()), active, r.contains(mx, my), enabled, primary);
        text(g, label, r.x() + 7, r.y() + (r.height() - 8) / 2, r.width() - 14, enabled ? TEXT : 0xFF6F7D80);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = net.minecraft.Util.getMillis(); float offset = entranceOffset();
        double mx = layout.localX(mouseX), my = layout.localY(mouseY, offset);
        float delta = com.example.examplemod.client.CommunityFx.reduced||previousFrame == 0 ? 1 : Math.min(1, (now - previousFrame) / 95f); previousFrame = now;
        tooltip = null;
        if(!SocialHubScreen.embedded(this))g.fill(0, 0, width, height, 0x87080D10);
        g.pose().pushPose(); g.pose().translate(layout.x(), layout.y(), 0); g.pose().scale(layout.scale(), layout.scale(), 1); g.pose().translate(0, offset, 0);
        panel(g, 0, 0, PlayerProfileLayout.WIDTH, layout.height());
        var frameClip = layout.screenBounds(new PlayerProfileLayout.Rect(1, 1, PlayerProfileLayout.WIDTH - 2, layout.height() - 2), offset);
        g.flush();
        g.enableScissor(frameClip.x(), frameClip.y(), frameClip.right(), frameClip.bottom());
        try {
            g.fillGradient(2, 2, PlayerProfileLayout.WIDTH - 2, 88, 0xFF303737, SURFACE);
            g.fill(15, 14, 17, 23, ACCENT);
            text(g, tr(searching ? "search_title" : "title"), 24, 14, 178, ACCENT);
            if(!SocialHubScreen.embedded(this)){
            button(g,layout.socialToggle(),Component.translatable("gui.createcityexpansioncore.social.friends"),false,false,!editing,mx,my);
            button(g, layout.searchToggle(), tr(searching ? "search_profile" : "search_button"), false, searching, !editing, mx, my);
            button(g, layout.close(), Component.literal("×"), false, false, true, mx, my);
            }
            if (searching) {
                g.renderItem(new ItemStack(Items.COMPASS), 24, 47);
                text(g, tr("search_intro"), 54, 43, 350, TEXT);
                text(g, tr("search_subtitle"), 54, 61, 350, MUTED);
                plate(g, layout.searchField(), search.isFocused() ? RAISED : INSET);
                search.render(g, (int) mx, (int) my, partialTick);
                button(g, layout.searchSubmit(), tr("search_submit"), true, false, searchPending == null, mx, my);
            } else {
                header(g);
                String[] tabs = {"overview", "statistics", "projects"};
                for (int i = 0; i < 3; i++) button(g, layout.tab(i), tr(tabs[i]), false, tab == i, !editing, mx, my);
            }
            var body = layout.body();
            var bodyClip = layout.screenBounds(body, offset);
            g.flush();
            g.enableScissor(bodyClip.x(), bodyClip.y(), bodyClip.right(), bodyClip.bottom());
            try {
                if (searching) searchResults(g, mx, my);
                else if (profile == null) {
                    plate(g, body, INSET);
                    text(g, tr("result." + result), body.x() + 16, body.y() + 24, body.width() - 32, MUTED); contentHeight = 0;
                } else if (editing) editor(g, mx, my, partialTick);
                else if (tab == 0) overview(g, mx, my, delta);
                else if (tab == 1) statistics(g, mx, my);
                else projects(g, mx, my);
                float transition = com.example.examplemod.client.CommunityFx.reduced?0:1 - ease((now - switchedAt) / 150f);
                if (transition > 0) g.fill(body.x(), body.y(), body.right(), body.bottom(), ((int) (transition * 110) << 24) | (SURFACE & 0xFFFFFF));
            } finally { g.flush(); g.disableScissor(); }
            scroll = Math.max(0, Math.min(scroll, layout.scrollMaximum(contentHeight)));
            if (!editing && layout.scrollMaximum(contentHeight) > 0) {
                var thumb = layout.scrollThumb(scroll, contentHeight);
                g.fill(body.right() + 3, body.y(), body.right() + 5, body.bottom(), INSET);
                g.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), ACCENT);
            }
            footer(g, mx, my);
        } finally { g.flush(); g.disableScissor(); g.pose().popPose(); }
        if (tooltip != null) g.renderTooltip(font, font.split(tooltip, 240), mouseX, mouseY);
    }
    private void header(GuiGraphics g) {
        rounded(g, 14, 34, 52, 52, 6, 0xFF151D21);
        g.renderOutline(17, 37, 46, 46, 0xFF806747);
        var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(target);
        PlayerSkin current;
        try { current = info != null ? info.getSkin() : skin == null ? DefaultPlayerSkin.get(target) : skin.get(); }
        catch (RuntimeException unavailable) { current = DefaultPlayerSkin.get(target); }
        PlayerFaceRenderer.draw(g, current, 20, 40, 40);
        String name = profile == null ? "…" : profile.name();
        g.pose().pushPose(); g.pose().translate(78, 37, 0); g.pose().scale(1.35f, 1.35f, 1);
        text(g, Component.literal(name), 0, 0, 240, TEXT); g.pose().popPose();
        int status = profile == null ? MUTED : profile.online() ? 0xFF88C4A9 : 0xFF839399;
        rounded(g, 78, 56, 5, 5, 2, status);
        text(g, tr(profile == null ? "result.loading" : profile.online() ? "online" : "offline"), 88, 55, 300, status);
        Component city = profile == null ? Component.literal("…") : profile.city().isEmpty() ? tr("no_city")
                : Component.literal(profile.city()).append(" · ").append(Component.translatable("role.createcityexpansioncore." + profile.cityRole().toLowerCase(Locale.ROOT)));
        var alliance=com.example.examplemod.client.AllianceClient.forPlayer(target);
        text(g, city, 78, 73, alliance==null?330:215, MUTED);
        if(alliance!=null){var r=layout.allianceBadge();rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);AllianceScreen.drawEmblem(g,r.x()+2,r.y()+2,16,alliance.getInt("Color"),alliance.getInt("Emblem"));text(g,Component.literal(alliance.getString("Name")),r.x()+22,r.y()+6,r.width()-26,0xFF000000|alliance.getInt("Color"));}
    }
    private void overview(GuiGraphics g, double mx, double my, float delta) {
        var b = layout.body(); int y = b.y() - scroll;
        plate(g, new PlayerProfileLayout.Rect(b.x(), y, b.width(), 54), INSET);
        text(g, tr("about"), b.x() + 12, y + 9, b.width() - 24, ACCENT);
        Component bio = profile.settings().about().isBlank() ? tr("about_empty") : Component.literal(profile.settings().about());
        var lines = font.split(bio, b.width() - 24);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), b.x() + 12, y + 25 + i * 11, profile.settings().about().isBlank() ? MUTED : TEXT, false);
        if (new PlayerProfileLayout.Rect(b.x(), y, b.width(), 54).contains(mx, my) && lines.size() > 2) tooltip = bio;
        for (int i = 0; i < 3; i++) {
            var r = shifted(layout.metric(i)); Metric metric = profile.settings().showcase().get(i);
            boolean over = r.contains(mx, my) && b.contains(mx, my);
            hover[i] += ((over ? 1 : 0) - hover[i]) * delta;
            plate(g, r, mix(INSET, RAISED, hover[i]));
            g.fill(r.x() + 10, r.y() + 1, r.right() - 10, r.y() + 2, mix(0xFF5A5344, ACCENT, hover[i]));
            g.renderItem(new ItemStack(icon(metric)), r.x() + 9, r.y() + 8);
            text(g, metricLabel(metric), r.x() + 9, r.y() + 29, r.width() - 18, MUTED);
            text(g, Component.literal(format(metric, profile.values().get(metric.ordinal()))), r.x() + 9, r.y() + 46, r.width() - 18, TEXT);
            if (over) tooltip = metricHint(metric);
        }
        y += 134;
        if (!profile.projects().isEmpty()) {
            var entry = profile.projects().getFirst();
            plate(g, new PlayerProfileLayout.Rect(b.x(), y, b.width(), 54), 0xFF242D2B);
            text(g, tr("verified_project"), b.x() + 12, y + 9, b.width() - 24, 0xFF96C8AE);
            text(g, Component.literal(entry.title()), b.x() + 12, y + 25, b.width() - 24, TEXT);
            text(g, roles(entry), b.x() + 12, y + 39, b.width() - 24, MUTED);
        } else {
            plate(g, new PlayerProfileLayout.Rect(b.x(), y, b.width(), 54), INSET);
            text(g, tr("career_start"), b.x() + 12, y + 10, b.width() - 24, TEXT);
            text(g, tr("no_projects"), b.x() + 12, y + 29, b.width() - 24, MUTED);
        }
        text(g, tr("public_showcase"), b.x() + 2, y + 64, b.width() - 4, MUTED); contentHeight = 210;
    }
    private void statistics(GuiGraphics g, double mx, double my) {
        var b = layout.body(); int y = b.y() - scroll, count = 0;
        for (Metric metric : Metric.values()) {
            long value = profile.values().get(metric.ordinal()); if (value == HIDDEN) continue;
            var row = new PlayerProfileLayout.Rect(b.x(), y, b.width(), 34); boolean over = row.contains(mx, my) && b.contains(mx, my);
            plate(g, row, over ? RAISED : INSET); g.renderItem(new ItemStack(icon(metric)), b.x() + 10, y + 9);
            text(g, metricLabel(metric), b.x() + 35, y + 13, 216, TEXT);
            String formatted = format(metric, value);
            text(g, Component.literal(formatted), b.right() - 130, y + 13, 118, value < 0 ? MUTED : ACCENT);
            if (over) tooltip = metricHint(metric);
            y += 40; count++;
        }
        Component note = tr(owner() ? "statistics_source" : "statistics_private");
        var lines = font.split(note, b.width() - 20);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), b.x() + 10, y + 6 + i * 11, MUTED, false);
        contentHeight = count * 40 + 12 + lines.size() * 11;
    }
    private void projects(GuiGraphics g, double mx, double my) {
        var b = layout.body(); int y = b.y() - scroll;
        if (profile.projects().isEmpty()) {
            plate(g, b, INSET); text(g, tr("career_start"), b.x() + 16, b.y() + 24, b.width() - 32, ACCENT);
            g.drawWordWrap(font, tr("project_empty_detail"), b.x() + 16, b.y() + 47, b.width() - 32, MUTED); contentHeight = 0; return;
        }
        for (var entry : profile.projects()) {
            var row = new PlayerProfileLayout.Rect(b.x(), y, b.width(), 49); boolean over = row.contains(mx, my) && b.contains(mx, my);
            plate(g, row, over ? RAISED : INSET);
            g.fill(b.x() + 1, y + 8, b.x() + 3, y + 41, entry.server() ? ACCENT : 0xFF76B9C3);
            text(g, Component.literal(entry.title()), b.x() + 12, y + 8, b.width() - 110, TEXT);
            text(g, tr(entry.server() ? "server_project" : "city_project"), b.x() + 12, y + 22, 170, entry.server() ? ACCENT : 0xFF84C3CC);
            text(g, roles(entry), b.x() + 12, y + 35, b.width() - 24, MUTED);
            text(g, tr("completed"), b.right() - 96, y + 9, 84, 0xFF96C8AE);
            if (over) tooltip = Component.literal(entry.title()).append("\n").append(roles(entry)).append("\n").append(tr("participation_note"));
            y += 55;
        }
        contentHeight = profile.projects().size() * 55;
    }
    private void editor(GuiGraphics g, double mx, double my, float pt) {
        var b = layout.body(); plate(g, b, INSET);
        text(g, tr("about"), b.x() + 12, b.y() + 9, b.width() - 60, ACCENT);
        text(g, Component.literal(about.getValue().length() + "/" + Settings.MAX_ABOUT), b.right() - 62, b.y() + 9, 50, MUTED);
        var field = layout.aboutField(); plate(g, field, about.isFocused() ? RAISED : SURFACE);
        about.render(g, (int) mx, (int) my, pt);
        text(g, tr("choose_showcase"), b.x() + 12, b.y() + 59, b.width() - 24, MUTED);
        for (int i = 0; i < 3; i++) {
            var r = layout.picker(i); button(g, r, Component.literal((i + 1) + "  ").append(metricLabel(showcase.get(i))), false, false, pending == null, mx, my);
            text(g, Component.literal("›"), r.right() - 17, r.y() + 8, 9, ACCENT);
            if (r.contains(mx, my)) tooltip = tr("choose_hint");
        }
        contentHeight = 0;
    }
    private void footer(GuiGraphics g, double mx, double my) {
        g.hLine(14, PlayerProfileLayout.WIDTH - 15, layout.height() - 39, EDGE);
        if (searching) {
            button(g, layout.footerAction(), tr("search_back"), false, false, true, mx, my);
            text(g, directory != null && directory.result().equals("ok") ? tr("search_count", directory.total()) : tr(searchResult),
                    147, layout.height() - 24, 145, MUTED);
            if (directory != null && directory.total() > ProfileDirectoryPayload.PAGE_SIZE) {
                button(g, layout.pageButton(false), Component.literal("‹"), false, false, searchPending == null && directory.page() > 0, mx, my);
                text(g, Component.literal((directory.page()+1) + "/" + ((directory.total()+ProfileDirectoryPayload.PAGE_SIZE-1)/ProfileDirectoryPayload.PAGE_SIZE)),
                        342, layout.height()-24, 42, MUTED);
                button(g, layout.pageButton(true), Component.literal("›"), false, false,
                        searchPending == null && (directory.page()+1)*ProfileDirectoryPayload.PAGE_SIZE < directory.total(), mx, my);
            } else button(g, layout.refresh(), Component.literal("↻"), false, false, searchPending == null, mx, my);
            return;
        }
        if (editing) {
            button(g, layout.footerAction(), tr("cancel"), false, false, pending == null, mx, my);
            button(g, layout.save(), tr(pending == null ? "save" : "saving"), true, false, pending == null, mx, my);
            text(g, tr("result." + (result.equals("ok") || result.equals("loading") ? "edit" : result)), 145, layout.height() - 24, 155, MUTED);
            if (my >= layout.height() - 33 && mx >= 145 && mx < 300) tooltip = tr("result." + result);
            return;
        }
        if (owner() && profile != null) button(g, layout.footerAction(), tr("edit"), false, false, pending == null, mx, my);
        else {
            button(g, layout.footerAction(), Component.translatable("gui.createcityexpansioncore.social.open_peer"), false, false, profile!=null, mx, my);
        }
        if(!owner()&&profile!=null&&profile.city().isBlank()&&com.example.examplemod.client.CommunityClient.canInvite())button(g,layout.invite(),Component.translatable("gui.createcityexpansioncore.community.invite_player"),false,false,true,mx,my);
        else text(g, tr("result." + (result.equals("ok") ? "public" : result)), 147, layout.height() - 24, tab == 2 && profile != null && profile.completed() > PAGE_SIZE ? 140 : 230, MUTED);
        if (tab == 2 && profile != null && profile.completed() > PAGE_SIZE) {
            button(g, layout.pageButton(false), Component.literal("‹"), false, false, pending == null && profile.page() > 0, mx, my);
            text(g, Component.literal((profile.page() + 1) + "/" + ((profile.completed() + PAGE_SIZE - 1) / PAGE_SIZE)), 342, layout.height() - 24, 42, MUTED);
            button(g, layout.pageButton(true), Component.literal("›"), false, false, pending == null && (profile.page() + 1) * PAGE_SIZE < profile.completed(), mx, my);
        } else {
            button(g, layout.refresh(), Component.literal("↻"), false, false, pending == null, mx, my);
            if (layout.refresh().contains(mx, my)) tooltip = tr("refresh");
        }
    }
    private void searchResults(GuiGraphics g, double mx, double my) {
        var b = layout.body();
        if (directory == null || directory.entries().isEmpty()) {
            plate(g, b, INSET);
            text(g, tr(searchPending != null ? "result.loading" : searchResult), b.x()+16, b.y()+24, b.width()-32, MUTED);
            contentHeight = 0; return;
        }
        for (int i=0; i<directory.entries().size(); i++) {
            var entry=directory.entries().get(i); var row=shifted(layout.searchRow(i));
            boolean over=b.contains(mx,my) && row.contains(mx,my);
            plate(g,row,over ? RAISED : INSET);
            var info=minecraft.getConnection()==null ? null : minecraft.getConnection().getPlayerInfo(entry.id());
            PlayerSkin face;
            try { face=info==null ? DefaultPlayerSkin.get(entry.id()) : info.getSkin(); }
            catch (RuntimeException unavailable) { face=DefaultPlayerSkin.get(entry.id()); }
            PlayerFaceRenderer.draw(g,face,row.x()+9,row.y()+8,26);
            text(g,Component.literal(entry.name()),row.x()+45,row.y()+8,row.width()-150,TEXT);
            text(g,entry.city().isEmpty() ? tr("no_city") : Component.literal(entry.city()),row.x()+45,row.y()+24,row.width()-90,MUTED);
            text(g,tr(entry.online() ? "online" : "offline"),row.right()-95,row.y()+8,85,entry.online() ? 0xFF88C4A9 : MUTED);
            if (over) tooltip=tr("open_hint");
        }
        contentHeight=directory.entries().size()*46;
    }
    private Component roles(ProjectEntry entry) {
        Component text = Component.empty();
        for (int flag : List.of(ProjectEntry.AUTHOR, ProjectEntry.EXECUTOR, ProjectEntry.REPRESENTATIVE)) if ((entry.roles() & flag) != 0) {
            if (!text.getString().isEmpty()) text = text.copy().append(" · ");
            text = text.copy().append(tr("role." + (flag == 1 ? "author" : flag == 2 ? "executor" : "representative")));
        }
        return text;
    }
    private Component metricLabel(Metric metric) { return tr("metric." + metric.name().toLowerCase(Locale.ROOT)); }
    private Component metricHint(Metric metric) { return metricLabel(metric).copy().append("\n").append(tr("hint." + metric.name().toLowerCase(Locale.ROOT))); }
    private String format(Metric metric, long value) {
        if (value < 0) return tr("unavailable").getString();
        Locale locale = Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_', '-'));
        if (metric == Metric.PLAY_TIME) return tr("hours", NumberFormat.getNumberInstance(locale).format(Math.floor(value / 7200.0) / 10.0)).getString();
        if (metric == Metric.DISTANCE) return tr("km", NumberFormat.getNumberInstance(locale).format(Math.floor(value / 10000.0) / 10.0)).getString();
        return NumberFormat.getIntegerInstance(locale).format(value);
    }
    private static Item icon(Metric metric) { return switch (metric) {
        case PLAY_TIME -> Items.CLOCK; case MINED -> Items.IRON_PICKAXE; case DISTANCE -> Items.COMPASS;
        case PROJECTS -> Items.BRICKS; case JUMPS -> Items.RABBIT_FOOT; case MOB_KILLS -> Items.IRON_SWORD;
    }; }
    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int green = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | green << 8 | blue;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double mx = layout.localX(x), my = layout.localY(y, entranceOffset());
        if (button != 0) return false;
        com.example.examplemod.client.CommunityFx.sound("click");
        if (!SocialHubScreen.embedded(this)&&layout.close().contains(mx, my)) { onClose(); return true; }
        if(!editing&&!searching&&!owner()&&profile!=null&&profile.city().isBlank()&&com.example.examplemod.client.CommunityClient.canInvite()&&layout.invite().contains(mx,my)){CommunityScreen.invite(this,target);return true;}
        if(!editing&&!searching&&layout.allianceBadge().contains(mx,my)){var alliance=com.example.examplemod.client.AllianceClient.forPlayer(target);if(alliance!=null){AllianceScreen.openAlliance(this,alliance.getUUID("Id"));return true;}}
        if (!SocialHubScreen.embedded(this)&&!editing && layout.socialToggle().contains(mx,my)) { CommunityScreen.open(this); return true; }
        if (!editing && !searching && !owner() && profile!=null && layout.footerAction().contains(mx,my)) { SocialScreen.open(this,target); return true; }
        if (!SocialHubScreen.embedded(this)&&!editing && layout.searchToggle().contains(mx,my)) { showSearch(!searching); return true; }
        if (!editing && layout.scrollMaximum(contentHeight) > 0 && layout.scrollTrack().contains(mx, my)) {
            draggingScroll = true; moveScroll(my); return true;
        }
        if (searching) {
            if (layout.footerAction().contains(mx,my)) { showSearch(false); return true; }
            if (layout.searchSubmit().contains(mx,my)) { requestSearch(0); return true; }
            if (layout.searchField().contains(mx,my)) {
                setFocused(search); search.setFocused(true);
                search.mouseClicked(Math.max(search.getX(),Math.min(search.getX()+search.getWidth()-1,mx)),search.getY()+5,button);
                return true;
            }
            if (directory != null && searchPending == null && directory.total() > ProfileDirectoryPayload.PAGE_SIZE) {
                if (directory.page()>0 && layout.pageButton(false).contains(mx,my)) { scroll=0; requestSearch(directory.page()-1); return true; }
                if ((directory.page()+1)*ProfileDirectoryPayload.PAGE_SIZE<directory.total() && layout.pageButton(true).contains(mx,my)) {
                    scroll=0; requestSearch(directory.page()+1); return true;
                }
            } else if (layout.refresh().contains(mx,my)) { requestSearch(0); return true; }
            if (directory != null && layout.body().contains(mx,my)) {
                for (int i=0;i<directory.entries().size();i++) if (shifted(layout.searchRow(i)).contains(mx,my)) {
                    if(!SocialHubScreen.inspect(directory.entries().get(i).id()))open(this,directory.entries().get(i).id()); return true;
                }
            }
            return layout.frame().contains(mx,my);
        }
        if (profile != null && !editing) for (int i = 0; i < 3; i++) if (layout.tab(i).contains(mx, my)) {
            tab = i; scroll = 0; switchedAt = net.minecraft.Util.getMillis();
            if (i == 0 && profile.page() != 0) request(false, 0);
            return true;
        }
        if (editing) {
            if (pending != null) return true;
            if (layout.footerAction().contains(mx, my)) { editing = false; about.setVisible(false); setFocused(null); return true; }
            if (layout.save().contains(mx, my)) { request(true, 0); return true; }
            for (int i = 0; i < 3; i++) if (layout.picker(i).contains(mx, my)) {
                int next = showcase.get(i).ordinal();
                do { next = (next + 1) % Metric.values().length; } while (showcase.contains(Metric.values()[next]));
                showcase.set(i, Metric.values()[next]); return true;
            }
            if (layout.aboutField().contains(mx, my)) {
                setFocused(about); about.setFocused(true);
                about.mouseClicked(Math.max(about.getX(), Math.min(about.getX() + about.getWidth() - 1, mx)), about.getY() + 5, button);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (pending == null && profile != null && owner() && layout.footerAction().contains(mx, my)) {
            editing = true; tab = 0; scroll = 0; result = "edit"; about.setValue(profile.settings().about()); showcase = new ArrayList<>(profile.settings().showcase());
            about.setVisible(true); setInitialFocus(about); return true;
        }
        if (pending == null && tab == 2 && profile != null && profile.completed() > PAGE_SIZE) {
            if (profile.page() > 0 && layout.pageButton(false).contains(mx, my)) { scroll = 0; request(false, profile.page() - 1); return true; }
            if ((profile.page() + 1) * PAGE_SIZE < profile.completed() && layout.pageButton(true).contains(mx, my)) { scroll = 0; request(false, profile.page() + 1); return true; }
        } else if (layout.refresh().contains(mx, my)) { request(false, profile == null ? 0 : profile.page()); return true; }
        return layout.frame().contains(mx, my);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!editing && layout.body().contains(layout.localX(x), layout.localY(y, entranceOffset()))) {
            scroll = Math.max(0, Math.min(layout.scrollMaximum(contentHeight), scroll - (int) (dy * 24))); return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll) { moveScroll(layout.localY(y, entranceOffset())); return true; }
        return (editing || searching) && super.mouseDragged(layout.localX(x), layout.localY(y, entranceOffset()), button, dx / layout.scale(), dy / layout.scale());
    }
    private void moveScroll(double y) {
        var thumb = layout.scrollThumb(scroll, contentHeight);
        double fraction = (y - layout.body().y() - thumb.height() / 2.0) / Math.max(1, layout.body().height() - thumb.height());
        scroll = (int) (Math.max(0, Math.min(1, fraction)) * layout.scrollMaximum(contentHeight));
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(layout.localX(x), layout.localY(y, entranceOffset()), button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (searching && (key == 257 || key == 335)) { requestSearch(0); return true; }
        if (editing && pending == null && hasControlDown() && (key == 257 || key == 335)) { request(true, 0); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() {
        if (searching) { showSearch(false); return; }
        if (editing && pending == null) { editing = false; about.setVisible(false); setFocused(null); return; }
        if(!SocialHubScreen.back(this))minecraft.setScreen(parent);
    }
    @Override public void removed() {
        // Responses for an inactive parent are ignored. Reopening must not retain a pending nonce.
        pending = null; searchPending = null;
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
