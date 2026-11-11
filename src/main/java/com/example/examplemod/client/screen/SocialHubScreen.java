package com.example.examplemod.client.screen;

import com.example.examplemod.client.*;
import com.example.examplemod.network.*;
import com.example.examplemod.profile.PlayerProfile;
import com.example.examplemod.social.SocialBook;
import com.example.examplemod.client.uiedit.UiEditLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Predicate;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** One actual Minecraft Screen; existing pages keep their widgets, drafts and authoritative protocols. */
public final class SocialHubScreen extends Screen {
    public enum Section { HOME, MESSAGES, FRIENDS, CITY, ALLIANCE, INBOX, PROFILE, FINANCES, COMMUNITY }
    private static final List<Section> NAVIGATION = List.of(
            Section.HOME, Section.COMMUNITY, Section.CITY, Section.ALLIANCE, Section.INBOX, Section.PROFILE, Section.FINANCES);

    private static Section visibleSection(Section section) {
        return section == Section.MESSAGES || section == Section.FRIENDS ? Section.COMMUNITY : section;
    }

    private static Section cacheKey(Screen screen, Section section) {
        if (screen instanceof SocialScreen social) return social.chats() ? Section.MESSAGES : Section.FRIENDS;
        return section;
    }

    static int unreadChats() {
        var hub = current();
        return hub == null ? 0 : hub.community.getInt("Unread") + hub.community.getInt("AllianceUnread");
    }

    static int friendRequests() {
        var hub = current();
        return hub == null ? 0 : hub.community.getInt("FriendRequests");
    }

    private SocialHubLayout.Rect navControl(Section section) {
        return hubControl("nav[" + section.ordinal() + "]", layout.nav(NAVIGATION.indexOf(section)));
    }

    private final Screen parent;private Screen page;private Section section=Section.HOME;
    private final EnumMap<Section,Screen> saved=new EnumMap<>(Section.class);private record Trail(Screen page,Section section){}
    private final Deque<Trail> history=new ArrayDeque<>();
    private final Set<Screen> initialized=Collections.newSetFromMap(new IdentityHashMap<>());private final Map<Screen,Section> sections=new IdentityHashMap<>();private final Map<Screen,int[]> viewport=new IdentityHashMap<>();
    private boolean suspended,summaryInbox;private int summaryPage;
    private SocialHubLayout layout;private CompoundTag community=new CompoundTag(),inspectedMembership=new CompoundTag();private UUID summaryRequest,inspectRequest,socialRequest,memberRequest,inspected;
    private long summaryAt,summarySent,opened,changed,drawerAt,profileAt,socialAt;private int inboxScroll;
    private boolean showReadNotifications;
    private ListTag firstInboxPage = new ListTag();
    private List<CompoundTag> inboxEvents = List.of();
    private List<SocialInboxLayout.Row> inboxRows = List.of();
    private final Map<UUID, UUID> pendingInboxReads = new HashMap<>();
    private static final java.time.format.DateTimeFormatter INBOX_TIME =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private java.util.function.Supplier<net.minecraft.client.resources.PlayerSkin> inspectedSkin;
    private PlayerProfile inspectedProfile;private SocialBook.Action inspectAction=SocialBook.Action.LIST;private SocialStatePayload inspectedSocial;private String inspectResult="loading";
    private float indicator=112;private long lastFrame;private final float[] hover=new float[Section.values().length];
    private SocialHubScreen(Screen parent){super(tr("title"));this.parent=parent;}
    public static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.hub."+key,args);}
    public static SocialHubScreen current(){return Minecraft.getInstance().screen instanceof SocialHubScreen h?h:null;}
    public static boolean inSection(Section section){var h=current();return h!=null&&h.section==visibleSection(section);}
    public static boolean unobstructed(Screen child){var h=current();return h==null?Minecraft.getInstance().screen==child:h.page==child&&h.inspected==null&&h.section!=Section.INBOX;}
    public static boolean embedded(Screen child){var h=current();return h!=null&&h.initialized.contains(child);}
    public static SocialHubLayout geometry(Screen child){var h=current();return h!=null&&h.initialized.contains(child)?h.layout:null;}
    public static <T extends Screen>T find(Class<T> type,Predicate<T> predicate){var screen=Minecraft.getInstance().screen;if(type.isInstance(screen)){var v=type.cast(screen);return predicate.test(v)?v:null;}var h=current();if(h==null)return null;for(var p:h.initialized)if(type.isInstance(p)&&predicate.test(type.cast(p)))return type.cast(p);return null;}
    public static <T extends Screen>T active(Class<T> type){var screen=Minecraft.getInstance().screen;if(type.isInstance(screen))return type.cast(screen);var h=current();return h!=null&&type.isInstance(h.page)?type.cast(h.page):null;}
    public static void show(Screen parent,Screen child,Section section){section=visibleSection(section);var h=current();if(h==null){h=new SocialHubScreen(parent);h.page=child;h.section=section;h.sections.put(child,section);if(h.cacheable(child,section))h.saved.put(cacheKey(child,section),child);Minecraft.getInstance().setScreen(h);}else h.mount(child,section,true);}
    public static void openDailyRewards(){var h=current();if(h!=null){h.suspended=true;DailyRewardsClient.open(h);}}
    public static boolean back(Screen child){var h=current();if(h==null||!h.initialized.contains(child))return false;h.goBack();return true;}
    private boolean cacheable(Screen p,Section s){if(p instanceof ProjectWorkspaceScreen)return false;if(p instanceof PlayerProfileScreen v)return v.ownProfileView();if(p instanceof CommunityScreen v)return s==Section.HOME?v.homeView():s!=Section.CITY||v.ownCityView();return true;}
    private void mount(Screen next,Section section,boolean remember){if(page!=next&&remember&&page!=null&&!(page instanceof SocialScreen&&next instanceof SocialScreen)){history.push(new Trail(page,this.section));while(history.size()>16){var old=history.removeLast().page();if(!saved.containsValue(old)&&old!=page&&history.stream().noneMatch(t->t.page()==old)){initialized.remove(old);sections.remove(old);viewport.remove(old);old.removed();}}}page=next;this.section=section;sections.put(next,section);if(cacheable(next,section))saved.put(cacheKey(next,section),next);inspected=null;changed=net.minecraft.Util.getMillis();if(!initialized.contains(next)){initialized.add(next);next.init(minecraft,width,height);viewport.put(next,new int[]{width,height});}else{var size=viewport.get(next);if(size==null||size[0]!=width||size[1]!=height){next.resize(minecraft,width,height);viewport.put(next,new int[]{width,height});}}CommunityFx.sound("click");}
    @Override protected void init(){layout=SocialHubLayout.calculate(width,height);CommunityFx.load();if(opened==0){opened=changed=net.minecraft.Util.getMillis();CommunityFx.sound("open");}if(page!=null){initialized.add(page);page.init(minecraft,width,height);viewport.put(page,new int[]{width,height});}requestSummary();}
    private void requestSummary() {
        if (summaryRequest == null) requestSummary(section == Section.INBOX, 0);
    }

    private void requestSummary(boolean inbox, int pageIndex) {
        summaryRequest = UUID.randomUUID();
        summarySent = net.minecraft.Util.getMillis();
        summaryInbox = inbox;
        summaryPage = pageIndex;
        var action = inbox ? CommunityRequestPayload.Action.INBOX : CommunityRequestPayload.Action.SUMMARY;
        PacketDistributor.sendToServer(new CommunityRequestPayload(summaryRequest, action, null, null, null, 0, pageIndex, ""));
    }

    public static void acceptCommunity(CommunityStatePayload payload) {
        var hub = current();
        if (hub == null) return;
        var data = payload.data();
        if (!data.hasUUID("Request")) return;
        UUID request = data.getUUID("Request");
        if (request.equals(hub.memberRequest)) {
            hub.memberRequest = null;
            hub.inspectedMembership = data;
        }
        if (hub.pendingInboxReads.containsValue(request)) {
            hub.finishInboxRead(request, data);
        }
        if (!request.equals(hub.summaryRequest)) return;
        hub.summaryRequest = null;
        hub.summaryAt = net.minecraft.Util.getMillis();
        if ("busy".equals(data.getString("Result"))) return;
        hub.community = data;
        if (hub.summaryInbox) {
            var entries = data.getList("Inbox", Tag.TAG_COMPOUND);
            if (hub.summaryPage == 0 && data.getInt("InboxTotal") > entries.size() && hub.section == Section.INBOX) {
                hub.firstInboxPage = entries;
                hub.requestSummary(true, 1);
                return;
            }
            hub.acceptInboxPages(hub.summaryPage == 1 ? hub.firstInboxPage : entries,
                    hub.summaryPage == 1 ? entries : new ListTag());
        } else if (hub.section == Section.INBOX) {
            hub.requestSummary();
        }
    }

    private void acceptInboxPages(ListTag first, ListTag second) {
        var events = new LinkedHashMap<UUID, CompoundTag>();
        var previouslyRead = new HashSet<UUID>();
        for (var event : inboxEvents) {
            if (event.getBoolean("Read")) previouslyRead.add(event.getUUID("Id"));
        }
        for (ListTag page : List.of(first, second)) {
            for (Tag raw : page) {
                var event = (CompoundTag) raw;
                if (!event.hasUUID("Id")) continue;
                UUID id = event.getUUID("Id");
                var earlier = events.putIfAbsent(id, event);
                var displayed = earlier == null ? event : earlier;
                if (event.getBoolean("Read") || previouslyRead.contains(id)) displayed.putBoolean("Read", true);
            }
        }
        inboxEvents = List.copyOf(events.values());
        rebuildInbox(true);
    }

    private void finishInboxRead(UUID request, CompoundTag reply) {
        UUID eventId = pendingInboxReads.entrySet().stream()
                .filter(entry -> entry.getValue().equals(request)).map(Map.Entry::getKey).findFirst().orElseThrow();
        pendingInboxReads.remove(eventId);
        if ("ok".equals(reply.getString("Result"))) {
            inboxEvents.stream().filter(event -> eventId.equals(event.getUUID("Id")))
                    .forEach(event -> event.putBoolean("Read", true));
        }
        rebuildInbox(true);
    }

    private void rebuildInbox(boolean keepPosition) {
        var anchor = keepPosition && inboxScroll > 0
                ? inboxRows.stream().filter(row -> row.top() + row.height() > inboxScroll).findFirst().orElse(null) : null;
        int offset = anchor == null ? 0 : inboxScroll - anchor.top();
        var displayed = new ArrayList<CompoundTag>(inboxEvents.size());
        for (var event : inboxEvents) {
            if (pendingInboxReads.containsKey(event.getUUID("Id")) && !event.getBoolean("Read")) {
                var read = event.copy();
                read.putBoolean("Read", true);
                displayed.add(read);
            } else {
                displayed.add(event);
            }
        }
        inboxRows = SocialInboxLayout.rows(displayed, showReadNotifications);
        if (anchor != null) {
            inboxRows.stream().filter(row -> row.key().equals(anchor.key())).findFirst()
                    .ifPresent(row -> inboxScroll = row.top() + offset);
        }
        if (layout != null) clampInboxScroll();
    }

    public static boolean acceptProfile(ProfileResponsePayload response){var h=current();if(h==null||h.inspectRequest==null||!h.inspectRequest.equals(response.request()))return false;h.inspectRequest=null;h.inspectResult=response.result();if(response.profile()!=null&&response.profile().id().equals(h.inspected)){h.inspectedProfile=response.profile();var p=response.profile();var gp=new com.mojang.authlib.GameProfile(p.id(),p.skinName().isBlank()?p.name():p.skinName());if(!p.texture().isBlank())gp.getProperties().put("textures",new com.mojang.authlib.properties.Property("textures",p.texture(),p.signature().isBlank()?null:p.signature()));h.inspectedSkin=h.minecraft.getSkinManager().lookupInsecure(gp);}return true;}
    public static boolean acceptSocial(SocialStatePayload response){var h=current();if(h==null||h.socialRequest==null||!h.socialRequest.equals(response.request()))return false;h.socialRequest=null;h.inspectResult=response.result();if(Objects.equals(response.target(),h.inspected)){h.inspectedSocial=response;if(h.inspectAction!=SocialBook.Action.LIST)CommunityFx.sound(response.result().equals("ok")?"success":"error");}return true;}
    public static boolean inspect(UUID id){var h=current();if(h==null)return false;h.inspected=id;h.inspectedProfile=null;h.inspectedSkin=null;h.inspectedSocial=null;h.socialRequest=null;h.inspectedMembership=new CompoundTag();h.memberRequest=UUID.randomUUID();PacketDistributor.sendToServer(new CommunityRequestPayload(h.memberRequest,CommunityRequestPayload.Action.VIEW,null,id,null,0,0,""));h.inspectResult="loading";h.drawerAt=h.profileAt=h.socialAt=net.minecraft.Util.getMillis();h.inspectRequest=UUID.randomUUID();PacketDistributor.sendToServer(new ProfileRequestPayload(h.inspectRequest,id,false,0,PlayerProfile.Settings.defaults()));AllianceClient.profile(id);if(!id.equals(h.minecraft.player.getUUID()))h.inspectSocial(SocialBook.Action.LIST);return true;}
    private void inspectSocial(SocialBook.Action action){if(socialRequest!=null)return;socialRequest=UUID.randomUUID();socialAt=net.minecraft.Util.getMillis();inspectAction=action;PacketDistributor.sendToServer(new SocialRequestPayload(socialRequest,inspected,action,inspectedSocial==null?0:inspectedSocial.revision(),"","",0,0,0));}
    private void select(Section next){inspected=null;if(next==Section.COMMUNITY){if(section!=next||!(page instanceof SocialScreen))SocialScreen.openDialogs(this);return;}if(next==Section.INBOX){if(section!=Section.INBOX&&page!=null)history.push(new Trail(page,section));section=next;changed=net.minecraft.Util.getMillis();requestSummary();return;}var cached=saved.get(next);if(cached!=null&&cacheable(cached,next)){mount(cached,next,true);return;}switch(next){case FINANCES->BankScreen.open(this,null);case HOME->CommunityScreen.open(this);case MESSAGES->SocialScreen.openDialogs(this);case FRIENDS->SocialScreen.openFriends(this);case CITY->CommunityScreen.openOwnCity(this);case ALLIANCE->AllianceScreen.open(this,null);case PROFILE->PlayerProfileScreen.open(this,minecraft.player.getUUID());default->{}}}
    private void goBack(){inspected=null;if(!history.isEmpty()){var t=history.pop();if(t.section()==Section.INBOX){page=t.page();section=Section.INBOX;changed=net.minecraft.Util.getMillis();}else mount(t.page(),t.section(),false);}else if(section==Section.HOME)onClose();else select(Section.HOME);}
    @Override public void tick(){if(minecraft.player==null||minecraft.getConnection()==null){minecraft.setScreen(null);return;}long now=net.minecraft.Util.getMillis();AllianceClient.ensure();if(summaryRequest!=null&&now-summarySent>8000)summaryRequest=null;if(now-summaryAt>5000)requestSummary();if(inspectRequest!=null&&now-profileAt>8000){inspectRequest=null;inspectResult="timeout";}if(socialRequest!=null&&now-socialAt>8000){socialRequest=null;inspectResult="timeout";}if(section!=Section.INBOX&&page!=null)page.tick();}
    private void text(GuiGraphics g,Component c,int x,int y,int w,int color){g.drawString(font,fit(font,c,Math.max(1,w)),x,y,color,false);}
    private final SocialHubIcons.HoverMotion buttonMotion = new SocialHubIcons.HoverMotion();

    private void button(GuiGraphics g, SocialHubLayout.Rect r, Component label, double mx, double my, boolean enabled) {
        button(g, r, SocialHubIcons.buttonCaption(label), SocialHubIcons.buttonIcon(label), mx, my, enabled);
    }

    private void button(GuiGraphics g, SocialHubLayout.Rect r, Component label, SocialHubIcons.Icon icon,
            double mx, double my, boolean enabled) {
        var bounds = new WorldMapUiLayout.Rect(r.x(), r.y(), r.width(), r.height());
        SocialHubIcons.paintButton(g,font,bounds,label,icon,buttonMotion,r.contains(mx,my),enabled,false,false);
    }

    private void renderNavigation(GuiGraphics g, double mx, double my, float blend) {
        for (var tab : NAVIGATION) {
            int index = tab.ordinal();
            var row = navControl(tab);
            var bounds = new WorldMapUiLayout.Rect(row.x(), row.y(), row.width(), row.height());
            boolean hovered = inspected == null && row.contains(mx, my);
            hover[index] += ((hovered ? 1 : 0) - hover[index]) * blend;
            float motion = CommunityFx.reduced ? 0 : hover[index];
            int badge = switch (tab) {
                case COMMUNITY -> unreadChats() + friendRequests();
                case INBOX -> community.getInt("InboxUnread");
                default -> 0;
            };
            g.pose().pushPose();
            try {
                SocialHubIcons.scaleButton(g, bounds, motion);
                rounded(g, row.x(), row.y(), row.width(), row.height(), 4,
                        tab == section ? RAISED : hover[index] > .1f ? SURFACE : INSET);
                SocialHubIcons.navigation(g, row, tab, tab == section || hovered, motion);
                text(g, tr("section." + tab.name().toLowerCase(Locale.ROOT)),
                        row.x() + SocialHubIcons.LABEL_OFFSET, row.y() + (row.height() - font.lineHeight) / 2,
                        row.width() - SocialHubIcons.LABEL_OFFSET - (badge > 0 ? 30 : 8), tab == section ? ACCENT : TEXT);
                if (badge > 0) {
                    text(g, Component.literal(Integer.toString(badge)), row.right() - 25,
                            row.y() + (row.height() - font.lineHeight) / 2, 23, ACCENT);
                }
            } finally {
                g.pose().popPose();
            }
        }
    }

    private double progress(){return CommunityFx.ease((net.minecraft.Util.getMillis()-drawerAt)/180.0);}
    private SocialHubLayout.Rect hubControl(String name, SocialHubLayout.Rect bounds) {
        return UiEditLayout.apply("hub." + name, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                layout.scale(), layout.x(), layout.y(), SocialHubLayout.Rect::new);
    }

    private SocialHubLayout.Rect drawerClose(SocialHubLayout.Rect drawer) {
        return hubControl("drawerClose", new SocialHubLayout.Rect(drawer.right()-34, drawer.y(), 34, 34));
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float pt){UiEditLayout.clearElements("");buttonMotion.beginFrame();double mx=layout.localX(mouseX),my=layout.localY(mouseY);g.fill(0,0,width,height,0x99090E10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);panel(g,0,0,SocialHubLayout.WIDTH,layout.height());g.fill(SocialHubLayout.SIDEBAR,9,SocialHubLayout.SIDEBAR+1,layout.height()-9,EDGE);g.pose().popPose();
        if(section!=Section.INBOX&&page!=null)page.render(g,inspected==null?mouseX:-10000,inspected==null?mouseY:-10000,pt);
        g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);
        text(g,tr("title"),16,17,140,ACCENT);button(g,hubControl("back", layout.back()),Component.literal("‹"),mx,my,true);text(g,tr("section."+section.name().toLowerCase(Locale.ROOT)),199,18,SocialHubLayout.WIDTH-505,TEXT);button(g,hubControl("search", layout.search()),tr("search"),SocialHubIcons.Icon.SEARCH,mx,my,true);button(g,hubControl("close", layout.close()),Component.empty(),SocialHubIcons.Icon.CLOSE,mx,my,true);
        var info=minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());var skin=info==null?DefaultPlayerSkin.get(minecraft.player.getUUID()):info.getSkin();rounded(g,12,47,35,35,4,INSET);PlayerFaceRenderer.draw(g,skin,15,50,29);text(g,Component.literal(minecraft.player.getGameProfile().getName()),53,51,101,TEXT);text(g,community.hasUUID("OwnCity")?Component.literal(community.getString("OwnName")):tr("no_city"),53,68,101,MUTED);
        text(g,community.hasUUID("OwnCity")?Component.translatable("role.createcityexpansioncore."+community.getString("OwnRole").toLowerCase(Locale.ROOT)):tr("independent"),16,92,138,ACCENT);
        long now=net.minecraft.Util.getMillis();float blend=CommunityFx.reduced?1:Math.min(1,(now-(lastFrame==0?now-16:lastFrame))/70f);lastFrame=now;indicator+=(navControl(section).y()-indicator)*blend;
        g.fill(9,(int)indicator+5,11,(int)indicator+25,ACCENT);
        renderNavigation(g, mx, my, blend);
        BankClient.ensure();
        var cash=hubControl("balance",layout.balance());
        button(g,cash,Component.literal(BankClient.ready()?BankClient.money(BankClient.wallet())+" CR":"— CR"),
                SocialHubIcons.Icon.BANK,mx,my,true);
        var gift=hubControl("dailyGift",layout.dailyGift());
        button(g,gift,Component.empty(),SocialHubIcons.Icon.GIFT,mx,my,true);
        if(DailyRewardsClient.available()){int alpha=CommunityFx.reduced?180:100+(int)(75*(.5+.5*Math.sin(now/650d)));g.renderOutline(gift.x()+1,gift.y()+1,gift.width()-2,gift.height()-2,(alpha<<24)|(ACCENT&0xFFFFFF));}
        button(g,hubControl("settings", layout.settings()),tr("settings"),SocialHubIcons.Icon.SETTINGS,mx,my,true);
        if(section==Section.INBOX)inbox(g,mx,my);
        float fade=1-CommunityFx.ease((now-changed)/140.0);if(fade>0){var r=layout.content();g.fill(r.x(),r.y(),r.right(),r.bottom(),((int)(95*fade)<<24)|(SURFACE&0xFFFFFF));}
        if(inspected!=null)drawer(g,mx,my);
        float opening=1-CommunityFx.ease((now-opened)/180.0);if(opening>0)g.fill(1,1,SocialHubLayout.WIDTH-1,layout.height()-1,((int)(100*opening)<<24)|(SURFACE&0xFFFFFF));g.flush();g.pose().popPose();
        if(inspected==null && hubControl("close",layout.close()).contains(mx,my)) {
            g.renderTooltip(font,Component.translatable("gui.createcityexpansioncore.common.close"),mouseX,mouseY);
        } else if(inspected==null&&hubControl("dailyGift", layout.dailyGift()).contains(mx,my)) {
            g.renderTooltip(font,DailyRewardsScreen.tr("title"),mouseX,mouseY);
        }
    }
    private SocialHubLayout.Rect inboxViewport() {
        var content = layout.content();
        return new SocialHubLayout.Rect(content.x(), content.y() + 36, content.width(), content.height() - 36);
    }

    private void clampInboxScroll() {
        inboxScroll = Math.clamp(inboxScroll, 0,
                Math.max(0, SocialInboxLayout.height(inboxRows) - inboxViewport().height() + 6));
    }

    private SocialHubLayout.Rect inboxBounds(SocialInboxLayout.Row row, SocialHubLayout.Rect viewport) {
        var bounds = new SocialHubLayout.Rect(viewport.x() + 12, viewport.y() + 6 + row.top() - inboxScroll,
                viewport.width() - 24, row.height());
        if (row.kind() == SocialInboxLayout.Kind.GROUP) return bounds;
        String key = "inbox." + row.key();
        var clip = layout.screen(viewport);
        UiEditLayout.setElementViewport("hub." + key,
                new com.example.examplemod.client.uiedit.UiEditModel.Bounds(clip.x(), clip.y(), clip.width(), clip.height()));
        return hubControl(key, bounds);
    }

    private void inbox(GuiGraphics g, double mx, double my) {
        var viewport = inboxViewport();
        text(g, tr("inbox_hint"), viewport.x() + 14, viewport.y() - 23, viewport.width() - 28, MUTED);
        if (inboxRows.isEmpty()) {
            text(g, tr(summaryInbox && summaryRequest != null ? "inbox_loading" : "inbox_empty"),
                    viewport.x() + 16, viewport.y() + 27, viewport.width() - 32, MUTED);
            return;
        }
        clampInboxScroll();
        var clip = layout.screen(viewport);
        g.flush();
        g.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            for (var row : inboxRows) {
                var bounds = inboxBounds(row, viewport);
                if (bounds.bottom() <= viewport.y() || bounds.y() >= viewport.bottom()) continue;
                boolean hovered = viewport.contains(mx, my) && bounds.contains(mx, my);
                switch (row.kind()) {
                    case GROUP -> drawInboxGroup(g, row, bounds);
                    case EVENT -> drawInboxEvent(g, row.event(), bounds, hovered);
                    case READ_TOGGLE -> drawReadToggle(g, row.count(), bounds, hovered);
                }
            }
            g.flush();
        } finally {
            g.disableScissor();
        }
    }

    private void drawInboxGroup(GuiGraphics g, SocialInboxLayout.Row row, SocialHubLayout.Rect bounds) {
        rounded(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4, SURFACE);
        int x = bounds.x() + 8;
        int y = bounds.y() + (bounds.height() - SocialHubIcons.SIZE) / 2;
        switch (row.group()) {
            case CITY -> SocialHubIcons.navigation(g, bounds, Section.CITY, false);
            case FRIENDS -> SocialHubIcons.navigation(g, bounds, Section.FRIENDS, false);
            case MESSAGES -> SocialHubIcons.navigation(g, bounds, Section.MESSAGES, false);
            case PROJECTS -> SocialHubIcons.Icon.PROJECTS.draw(g, x, y, false, 0);
            case ALLIANCE -> SocialHubIcons.navigation(g, bounds, Section.ALLIANCE, false);
            case OTHER -> SocialHubIcons.navigation(g, bounds, Section.INBOX, false);
        }
        int labelX = bounds.x() + SocialHubIcons.LABEL_OFFSET;
        text(g, tr("inbox_group." + row.group().name().toLowerCase(Locale.ROOT)),
                labelX, bounds.y() + 6, bounds.right() - labelX - 32, TEXT);
        g.drawCenteredString(font, Integer.toString(row.count()), bounds.right() - 16, bounds.y() + 6, MUTED);
    }

    private void drawInboxEvent(GuiGraphics g, CompoundTag event, SocialHubLayout.Rect bounds, boolean hovered) {
        boolean unread = !event.getBoolean("Read");
        rounded(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 5, hovered ? RAISED : INSET);
        if (unread) g.fill(bounds.x() + 1, bounds.y() + 7, bounds.x() + 3, bounds.bottom() - 7, ACCENT);
        String translation = "gui.createcityexpansioncore.hub.event." + event.getString("Kind");
        text(g, Component.translatableWithFallback(translation, tr("inbox_event").getString()),
                bounds.x() + 12, bounds.y() + 7, bounds.width() - 24, unread ? TEXT : MUTED);
        text(g, Component.literal(event.getString("Name")), bounds.x() + 12, bounds.y() + 21, bounds.width() - 24, MUTED);
        String when = INBOX_TIME.format(java.time.Instant.ofEpochMilli(event.getLong("Time"))
                .atZone(java.time.ZoneId.systemDefault()));
        text(g, Component.literal(when), bounds.x() + 12, bounds.y() + 35, bounds.width() - 24, MUTED);
    }

    private void drawReadToggle(GuiGraphics g, int count, SocialHubLayout.Rect bounds, boolean hovered) {
        var rect = new WorldMapUiLayout.Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        SocialHubIcons.paintButton(g,font,rect,tr("inbox_read",count),SocialHubIcons.Icon.NOTIFICATIONS,
                buttonMotion,hovered,true,showReadNotifications,false);
        int textY = bounds.y() + (bounds.height() - font.lineHeight) / 2;
        g.drawCenteredString(font, showReadNotifications ? "⌃" : "⌄", bounds.right() - 14, textY, ACCENT);
    }

    private boolean clickInbox(double x, double y) {
        var viewport = inboxViewport();
        if (!viewport.contains(x, y)) return true;
        for (var row : inboxRows) {
            if (row.kind() == SocialInboxLayout.Kind.GROUP || !inboxBounds(row, viewport).contains(x, y)) continue;
            if (row.kind() == SocialInboxLayout.Kind.READ_TOGGLE) {
                showReadNotifications = !showReadNotifications;
                rebuildInbox(false);
            } else {
                openInboxEvent(row.event());
            }
            return true;
        }
        return true;
    }

    private void openInboxEvent(CompoundTag event) {
        UUID eventId = event.getUUID("Id");
        if (!event.getBoolean("Read")) {
            UUID request = UUID.randomUUID();
            pendingInboxReads.put(eventId, request);
            PacketDistributor.sendToServer(new CommunityRequestPayload(request, CommunityRequestPayload.Action.READ_INBOX,
                    null, null, eventId, 0, 0, ""));
            rebuildInbox(true);
        }
        UUID peer = event.hasUUID("Peer") ? event.getUUID("Peer") : null;
        String kind = event.getString("Kind");
        if (event.hasUUID("Project")) ProjectWorkspaceScreen.open(this, event.getUUID("Project"));
        else if (event.hasUUID("Alliance")) {
            if ("alliance_message".equals(kind)) AllianceScreen.openChat(this, event.getUUID("Alliance"));
            else AllianceScreen.openCouncil(this, event.getUUID("Alliance"));
        } else if ("message".equals(kind) && peer != null) SocialScreen.open(this, peer);
        else if ("city_invite".equals(kind)) CommunityScreen.open(this);
        else if (event.hasUUID("City")) CommunityScreen.openCity(this, event.getUUID("City"));
        else if (peer != null) inspect(peer);
    }

    private boolean canManageInspected(){return inspected!=null&&inspectedMembership.hasUUID("Selected")&&inspected.equals(inspectedMembership.getUUID("Selected"))&&inspectedMembership.getBoolean("Manage");}
    private void drawer(GuiGraphics g,double mx,double my){var c=layout.content();g.fill(c.x(),c.y(),c.right(),c.bottom(),0x680A1012);var r=layout.drawer(progress());var clip=layout.screen(c);g.flush();g.enableScissor(clip.x(),clip.y(),clip.right(),clip.bottom());panel(g,r.x(),r.y(),r.width(),r.height());text(g,tr("player_card"),r.x()+13,r.y()+15,r.width()-50,ACCENT);var close=drawerClose(r);button(g,close,Component.empty(),SocialHubIcons.Icon.CLOSE,mx,my,true);
        var info=minecraft.getConnection().getPlayerInfo(inspected);var skin=info==null?(inspectedSkin==null?DefaultPlayerSkin.get(inspected):inspectedSkin.get()):info.getSkin();PlayerFaceRenderer.draw(g,skin,r.x()+14,r.y()+44,38);text(g,inspectedProfile==null?tr(inspectRequest==null?"unavailable":"loading"):Component.literal(inspectedProfile.name()),r.x()+63,r.y()+47,r.width()-75,TEXT);text(g,inspectedProfile==null?tr("loading"):tr(inspectedProfile.online()?"online":"offline"),r.x()+63,r.y()+65,r.width()-75,MUTED);
        if(inspectedProfile!=null){var p=inspectedProfile;text(g,p.city().isBlank()?tr("no_city"):Component.literal(p.city()),r.x()+14,r.y()+100,r.width()-28,TEXT);text(g,p.cityRole().isBlank()?tr("independent"):Component.translatable("role.createcityexpansioncore."+p.cityRole().toLowerCase(Locale.ROOT)),r.x()+14,r.y()+118,r.width()-28,ACCENT);var alliance=AllianceClient.forPlayer(inspected);if(alliance!=null)text(g,AllianceScreen.tr("badge",alliance.getString("Name")),r.x()+14,r.y()+138,r.width()-28,0xFF000000|alliance.getInt("Color"));var about=font.split(Component.literal(p.settings().about()),r.width()-28);for(int i=0;i<Math.min(1,about.size());i++)g.drawString(font,about.get(i),r.x()+14,r.y()+165+i*11,MUTED,false);}
        String result=inspectResult;boolean loading=inspectRequest!=null||socialRequest!=null;boolean self=inspected.equals(minecraft.player.getUUID());var relation=inspectedSocial==null?SocialBook.Relation.UNAVAILABLE:inspectedSocial.relation();Component friendship=tr("friend");if(relation==SocialBook.Relation.OUTGOING)friendship=tr("request_sent");if(relation==SocialBook.Relation.FRIEND)friendship=tr("friends_already");if(relation==SocialBook.Relation.INCOMING)friendship=tr("accept_friend");
        button(g,hubControl("drawerAction[" + 0 + "]", layout.drawerAction(progress(),0)),tr("write"),SocialHubIcons.Icon.MESSAGES,mx,my,!loading&&!self&&relation==SocialBook.Relation.FRIEND);button(g,hubControl("drawerAction[" + 1 + "]", layout.drawerAction(progress(),1)),friendship,SocialHubIcons.Icon.FRIENDS,mx,my,!loading&&!self&&(relation==SocialBook.Relation.NONE||relation==SocialBook.Relation.INCOMING));button(g,hubControl("drawerAction[" + 2 + "]", layout.drawerAction(progress(),2)),tr("full_profile"),SocialHubIcons.Icon.PROFILE,mx,my,inspectedProfile!=null);button(g,hubControl("drawerAction[" + 3 + "]", layout.drawerAction(progress(),3)),tr("invite_city"),SocialHubIcons.Icon.CITY,mx,my,inspectedProfile!=null&&inspectedProfile.city().isBlank()&&CommunityClient.canInvite()&&!self);button(g,hubControl("drawerAction[" + 4 + "]", layout.drawerAction(progress(),4)),tr("manage_member"),SocialHubIcons.Icon.EDIT,mx,my,!self&&canManageInspected());button(g,hubControl("drawerAction[" + 5 + "]", layout.drawerAction(progress(),5)),BankScreen.tr("send_money"),SocialHubIcons.Icon.BANK,mx,my,!self&&inspectedProfile!=null);
        if(!result.equals("ok")&&!result.equals("loading"))text(g,Component.translatable("gui.createcityexpansioncore.social.result."+result),r.x()+14,r.bottom()-228,r.width()-28,MUTED);g.flush();g.disableScissor();
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;double mx=layout.localX(x),my=layout.localY(y);if(inspected!=null){var r=layout.drawer(progress());if(!r.contains(mx,my)||drawerClose(r).contains(mx,my)){inspected=null;return true;}boolean busy=inspectRequest!=null||socialRequest!=null;var relation=inspectedSocial==null?SocialBook.Relation.UNAVAILABLE:inspectedSocial.relation();UUID id=inspected;
            if(hubControl("drawerAction[" + 0 + "]", layout.drawerAction(progress(),0)).contains(mx,my)&&!busy&&relation==SocialBook.Relation.FRIEND)SocialScreen.open(this,id);else if(hubControl("drawerAction[" + 1 + "]", layout.drawerAction(progress(),1)).contains(mx,my)&&!busy){if(relation==SocialBook.Relation.NONE)inspectSocial(SocialBook.Action.REQUEST);else if(relation==SocialBook.Relation.INCOMING)inspectSocial(SocialBook.Action.ACCEPT);}else if(hubControl("drawerAction[" + 2 + "]", layout.drawerAction(progress(),2)).contains(mx,my)&&inspectedProfile!=null)PlayerProfileScreen.open(this,id);else if(hubControl("drawerAction[" + 3 + "]", layout.drawerAction(progress(),3)).contains(mx,my)&&inspectedProfile!=null&&inspectedProfile.city().isBlank()&&CommunityClient.canInvite()&&!id.equals(minecraft.player.getUUID()))CommunityScreen.invite(this,id);else if(hubControl("drawerAction[" + 4 + "]", layout.drawerAction(progress(),4)).contains(mx,my)&&!id.equals(minecraft.player.getUUID())&&canManageInspected())CommunityScreen.openRoles(this,id);else if(hubControl("drawerAction[" + 5 + "]", layout.drawerAction(progress(),5)).contains(mx,my)&&!id.equals(minecraft.player.getUUID())&&inspectedProfile!=null)BankScreen.open(this,id);return true;}
        if(hubControl("balance", layout.balance()).contains(mx,my)){select(Section.FINANCES);return true;}if(hubControl("dailyGift", layout.dailyGift()).contains(mx,my)){openDailyRewards();return true;}if(hubControl("close", layout.close()).contains(mx,my)){onClose();return true;}if(hubControl("back", layout.back()).contains(mx,my)){goBack();return true;}if(hubControl("search", layout.search()).contains(mx,my)){PlayerProfileScreen.openSearch(this);return true;}if(hubControl("settings", layout.settings()).contains(mx,my)){CommunityScreen.settings(this);return true;}for(var tab:NAVIGATION)if(navControl(tab).contains(mx,my)){select(tab);return true;}
        if (section == Section.INBOX) return clickInbox(mx, my);
        return page!=null&&layout.content().contains(mx,my)&&page.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(inspected!=null)return true;if(section==Section.INBOX){inboxScroll-=(int)(dy*30);clampInboxScroll();return true;}return page!=null&&page.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return inspected==null&&section!=Section.INBOX&&page!=null&&page.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){return inspected==null&&page!=null&&page.mouseReleased(x,y,b);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(inspected!=null){if(key==256)inspected=null;return true;}if(section==Section.INBOX){if(key==256)onClose();return true;}return page!=null&&page.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){return inspected==null&&page!=null&&page.keyReleased(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){return inspected==null&&section!=Section.INBOX&&page!=null&&page.charTyped(c,mods);}
    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    public static PolishedWorldMapScreen retainMap(PolishedWorldMapScreen map){var h=current();if(h!=null){h.suspended=true;map.returnToCommunity(h);}return map;}
    @Override public void removed(){if(suspended){suspended=false;return;}for(var p:initialized)p.removed();initialized.clear();history.clear();saved.clear();viewport.clear();super.removed();}
}
