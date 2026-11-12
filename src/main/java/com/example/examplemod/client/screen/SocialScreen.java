package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.network.*;
import com.example.examplemod.social.SocialBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Private social workspace. Only the server supplies relationships, history and online state. */
public final class SocialScreen extends Screen {
    private SocialRequestPayload sendAttempt;
    private UUID readPending;private long readCursor,readRequestedAt;
    private long openedAt;
    private boolean menuOpen;
    private final List<Button> menuButtons = new ArrayList<>();
    private final SocialHubIcons.HoverMotion hoverMotion = new SocialHubIcons.HoverMotion();
    private final Map<UUID, PlayerSkin> knownSkins = new LinkedHashMap<>();
    private final Map<UUID, SocialDialogPreviews.Entry> dialogPreviews = new LinkedHashMap<>();
    private UUID lastResponse;
    private List<MessageRow> displayedMessages = List.of();
    private boolean restoreMessageAnchor;

    private static final DateTimeFormatter MESSAGE_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MESSAGE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private record MessageRow(long id, long time, LocalDate day, boolean own,
                              List<FormattedCharSequence> lines, int width, int top, int height) {
        SocialLayout.MessageSpan span() {
            return new SocialLayout.MessageSpan(id, top, height);
        }
    }
    public static boolean reading(UUID id){return SocialHubScreen.active(SocialScreen.class) instanceof SocialScreen v&&id.equals(v.target)&&SocialHubScreen.unobstructed(v)&&v.before==0&&v.confirmation==null&&!v.menuOpen&&v.messageScroll>=Math.max(0,v.messageHeight-v.layout.messages().height());}
    private final Screen parent;private UUID target,pending;private SocialLayout layout;private SocialStatePayload state;
    private EditBox input,filterBox;private int filter,page,listScroll,messageScroll,messageHeight;private long before,lastReply,requestedAt,lastRead,filterChangedAt;
    private final Map<UUID,String> drafts=new HashMap<>();private net.minecraft.nbt.CompoundTag allianceChat=new net.minecraft.nbt.CompoundTag();private int groupOffset;
    private String result="loading",filterText="";private SocialBook.Action pendingAction,confirmation;private boolean scrollToEnd=true;
    private final List<SocialStatePayload.Contact> visible=new ArrayList<>();
    private record Button(SocialLayout.Rect r,Component text,Runnable run,boolean enabled,boolean primary){}
    private final List<Button> buttons=new ArrayList<>();
    public SocialScreen(Screen parent,UUID target){super(tr("title"));this.parent=parent;this.target=target;}
    boolean chats() { return filter == 2; }

    public static void open(Screen parent, UUID target) {
        var view = SocialHubScreen.find(SocialScreen.class, SocialScreen::chats);
        if (view == null) {
            view = new SocialScreen(parent, target);
            view.filter = 2;
        } else if (target != null && !target.equals(view.target)) {
            view.queuedTarget = target;
        }
        SocialHubScreen.show(parent, view, SocialHubScreen.Section.COMMUNITY);
    }

    private static void openPeople(Screen parent, int requestedFilter) {
        var view = SocialHubScreen.find(SocialScreen.class, screen -> !screen.chats());
        if (view == null) {
            view = new SocialScreen(parent, null);
            view.filter = Math.max(0, requestedFilter);
        } else if (requestedFilter >= 0 && requestedFilter != view.filter) {
            view.queuedFilter = requestedFilter;
        }
        SocialHubScreen.show(parent, view, SocialHubScreen.Section.COMMUNITY);
    }

    public static void openRequests(Screen parent) { openPeople(parent, 1); }
    public static void openDialogs(Screen parent) { open(parent, null); }
    public static void openFriends(Screen parent) { openPeople(parent, -1); }

    private UUID queuedTarget;
    private int queuedFilter = -1;

    private void peopleFilter(int next) {
        if (chats() || next == filter) return;
        filter = next;
        page = listScroll = 0;
        state = null;
        request(SocialBook.Action.LIST, "");
    }

    private SocialLayout.Rect listBounds() {
        if (chats()) return layout.list();
        return new SocialLayout.Rect(14, 108, layout.width()-28, layout.height()-152);
    }

    private SocialLayout.Rect listRow(int index, int extra) {
        var list = listBounds();
        return new SocialLayout.Rect(list.x(), list.y()+28+index*42+extra-listScroll, list.width(), 38);
    }

    private boolean requestGroupStart(int index) {
        return filter == 1 && (index == 0 || visible.get(index-1).relation() != visible.get(index).relation());
    }

    private int requestGroupSpace(int through) {
        int space = 0;
        for (int i = 0; i <= through; i++) {
            if (requestGroupStart(i)) space += 20;
        }
        return space;
    }

    private void peopleAction(UUID peer, SocialBook.Action action) {
        if (pending != null || state == null) return;
        pending = UUID.randomUUID();
        pendingAction = action;
        requestedAt = net.minecraft.Util.getMillis();
        PacketDistributor.sendToServer(new SocialRequestPayload(pending, peer, action, state.revision(),
                "", filterText, filter, page, 0));
    }

    public static void accept(SocialStatePayload p){
        if(SocialHubScreen.acceptSocial(p))return;var s=SocialHubScreen.find(SocialScreen.class,v->p.request().equals(v.pending)||p.request().equals(v.readPending));if(s==null)return;
        if(s.readPending!=null&&s.readPending.equals(p.request())){if(p.result().equals("ok"))s.lastRead=Math.max(s.lastRead,s.readCursor);s.readPending=null;return;}
        if(s.pending==null||!s.pending.equals(p.request()))return;
        s.pending=null;s.result=p.result().equals("duplicate")?"ok":p.result();s.lastReply=net.minecraft.Util.getMillis();
        if(p.result().equals("busy")){s.updateInputs();return;}
        if(s.pendingAction!=SocialBook.Action.LIST&&s.pendingAction!=SocialBook.Action.READ)com.example.examplemod.client.CommunityFx.sound(p.result().equals("ok")||p.result().equals("duplicate")?"success":"error");
        if (s.pendingAction == SocialBook.Action.SEND) {
            if (p.result().equals("ok") || p.result().equals("duplicate")) s.input.setValue("");
            s.sendAttempt = null;
        }
        boolean atEnd = s.before == 0 && s.messageScroll >= s.maxMessageScroll();
        s.restoreMessageAnchor = !s.scrollToEnd && !atEnd && s.state != null
                && Objects.equals(s.state.target(), p.target())
                && (s.restoreMessageAnchor || !s.state.messages().equals(p.messages()));
        s.state = p;
        s.lastResponse = p.request();
        s.page = p.page();
        if (s.before == 0 && p.target() != null && !p.messages().isEmpty()) {
            var last = p.messages().getLast();
            s.rememberPreview(new SocialDialogPreviews.Entry(p.target(),last.id(),last.time(),last.text()));
        }
        if (s.scrollToEnd || atEnd) s.messageScroll = Integer.MAX_VALUE;
        s.scrollToEnd = false;
        s.updateInputs();
    }
    private static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.social."+key,args);}
    @Override protected void init(){layout=SocialLayout.calculate(width,height);var hub=SocialHubScreen.geometry(this);if(hub!=null)layout=new SocialLayout(620,hub.pageHeight(),hub.scale(),hub.pageX(620),hub.pageY(),chats()&&width>=700);com.example.examplemod.client.CommunityFx.load();if(openedAt==0){openedAt=net.minecraft.Util.getMillis();com.example.examplemod.client.CommunityFx.sound("open");}String draft=input==null?"":input.getValue();
        var r=layout.input();input=new EditBox(font,r.x()+6,r.y()+8,r.width()-12,12,tr("message_hint"));input.setMaxLength(500);input.setBordered(false);input.setTextColor(TEXT);input.setHint(tr("message_hint"));input.setValue(draft);addWidget(input);
        var l=listBounds();filterBox=new EditBox(font,l.x()+28,l.y()+8,l.width()-36,12,tr("filter_hint"));filterBox.setMaxLength(16);filterBox.setBordered(false);filterBox.setTextColor(TEXT);filterBox.setHint(tr("filter_hint"));filterBox.setValue(filterText);filterBox.setResponder(v->{filterText=v;listScroll=0;page=0;filterChangedAt=net.minecraft.Util.getMillis();});addWidget(filterBox);
        updateInputs();if(pending==null)request(SocialBook.Action.LIST,"");
        if(target!=null)PacketDistributor.sendToServer(new CommunityRequestPayload(UUID.randomUUID(),CommunityRequestPayload.Action.VIEW,null,target,null,0,0,""));
    }
    private boolean detailVisible(){return target!=null;}
    private boolean listVisible(){return !chats()||layout.wide()||target==null;}
    private void updateInputs(){boolean send=state!=null&&detailVisible()&&state.relation()==SocialBook.Relation.FRIEND&&before==0;input.setVisible(send);input.setEditable((pending==null||pendingAction==SocialBook.Action.LIST)&&sendAttempt==null);filterBox.setVisible(listVisible());}
    private void request(SocialBook.Action action,String text){if(pending!=null)return;pending=UUID.randomUUID();pendingAction=action;requestedAt=net.minecraft.Util.getMillis();
        var packet=new SocialRequestPayload(pending,target,action,state==null?0:state.revision(),text,filterText,filter,page,before);
        if(action==SocialBook.Action.SEND)sendAttempt=packet;
        PacketDistributor.sendToServer(packet);updateInputs();}
    private void pick(UUID id) {
        if (Objects.equals(target, id) || pending != null) return;
        if (target != null) drafts.put(target, input.getValue());
        sendAttempt = null;
        readPending = null;
        menuOpen = false;
        setFocused(null);
        target = id;
        before = lastRead = 0;
        confirmation = null;
        state = null;
        displayedMessages = List.of();
        restoreMessageAnchor = false;
        input.setValue(drafts.getOrDefault(id, ""));
        scrollToEnd = id != null;
        messageScroll = messageHeight = 0;
        request(SocialBook.Action.LIST, "");
    }

    public static void acceptPreviews(SocialDialogPreviews payload) {
        var screen = SocialHubScreen.find(SocialScreen.class, view -> payload.request().equals(view.lastResponse));
        if (screen == null || screen.filter != 2) return;
        for (var entry : payload.entries()) screen.rememberPreview(entry);
    }

    private void rememberPreview(SocialDialogPreviews.Entry entry) {
        dialogPreviews.compute(entry.peer(), (key, previous) ->
                previous == null || entry.messageId() >= previous.messageId() ? entry : previous);
        if (dialogPreviews.size() > 128) dialogPreviews.remove(dialogPreviews.keySet().iterator().next());
    }

    private void drawPlayerHead(GuiGraphics g, UUID id, int x, int y, int size) {
        var player = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
        if (player != null) {
            knownSkins.put(id, player.getSkin());
            if (knownSkins.size() > 128) knownSkins.remove(knownSkins.keySet().iterator().next());
        }
        PlayerFaceRenderer.draw(g, knownSkins.getOrDefault(id, DefaultPlayerSkin.get(id)), x, y, size);
    }

    private Component playerName(UUID id, String serverName) {
        if (!serverName.isBlank() && !serverName.equals("Игрок " + id.toString().substring(0,8))) {
            return Component.literal(serverName);
        }
        return tr("unknown_player");
    }

    private int maxMessageScroll() {
        return Math.max(0, messageHeight - layout.messages().height());
    }

    @Override public void tick(){if(minecraft.player==null||minecraft.getConnection()==null){minecraft.setScreen(null);return;}long now=net.minecraft.Util.getMillis();
        if(pending!=null&&now-requestedAt>8000){pending=null;result="timeout";updateInputs();}
        if (pending == null && queuedTarget != null) {
            UUID next = queuedTarget;
            queuedTarget = null;
            pick(next);
            return;
        }
        if (pending == null && queuedFilter >= 0) {
            int next = queuedFilter;
            queuedFilter = -1;
            peopleFilter(next);
            return;
        }
        if(pending==null&&filterChangedAt!=0&&now-filterChangedAt>350){filterChangedAt=0;request(SocialBook.Action.LIST,"");}
        if(pending==null&&now-lastReply>3000)request(SocialBook.Action.LIST,"");
        if(SocialHubScreen.unobstructed(this)&&confirmation==null&&!menuOpen&&state!=null&&target!=null&&before==0&&!state.messages().isEmpty()&&messageScroll>=Math.max(0,messageHeight-layout.messages().height())){
            long latest=state.messages().getLast().id();if(readPending!=null&&now-readRequestedAt>8000)readPending=null;
            if(latest>lastRead&&readPending==null){readPending=UUID.randomUUID();readCursor=latest;readRequestedAt=now;PacketDistributor.sendToServer(new SocialRequestPayload(readPending,target,SocialBook.Action.READ,state.revision(),"",filterText,filter,page,latest));}
        }
    }
    private void action(SocialBook.Action action){menuOpen=false;if(action==SocialBook.Action.BLOCK||action==SocialBook.Action.REMOVE){confirmation=action;return;}request(action,"");}
    private void send(){if(pending==null&&sendAttempt!=null){pending=sendAttempt.request();pendingAction=SocialBook.Action.SEND;requestedAt=net.minecraft.Util.getMillis();PacketDistributor.sendToServer(sendAttempt);updateInputs();return;}if(pending==null&&state!=null&&state.relation()==SocialBook.Relation.FRIEND&&before==0&&!input.getValue().isBlank()){scrollToEnd=true;request(SocialBook.Action.SEND,input.getValue());}}
    private void button(GuiGraphics g, SocialLayout.Rect r, Component label, Runnable run,
            boolean enabled, boolean primary, double mx, double my) {
        paintedButton(g,r,SocialHubIcons.buttonCaption(label),SocialHubIcons.buttonIcon(label),
                run,enabled,primary,mx,my);
    }

    private void paintedButton(GuiGraphics g, SocialLayout.Rect r, Component label, SocialHubIcons.Icon icon,
            Runnable run, boolean enabled, boolean primary, double mx, double my) {
        buttons.add(new Button(r,label,run,enabled,primary));
        SocialHubIcons.paintButton(g,font,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),
                label,icon,hoverMotion,r.contains(mx,my),enabled,primary,primary);
    }

    private void text(GuiGraphics g,Component value,int x,int y,int width,int color){g.drawString(font,fit(font,value,width),x,y,color,false);}
    private void clip(GuiGraphics g,SocialLayout.Rect r){var s=layout.screen(r);g.flush();g.enableScissor(s.x(),s.y(),s.right(),s.bottom());}
    private void unclip(GuiGraphics g){g.flush();g.disableScissor();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float pt){double mx=layout.localX(mouseX),my=layout.localY(mouseY);buttons.clear();menuButtons.clear();hoverMotion.beginFrame();iconHint=null;
        if(!SocialHubScreen.embedded(this))g.fill(0,0,width,height,0x90090D10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);panel(g,0,0,layout.width(),layout.height());
        if (SocialHubScreen.embedded(this)) {
            var icon = chats() ? SocialHubIcons.Icon.MESSAGES : SocialHubIcons.Icon.FRIENDS;
            icon.draw(g,20,12,true,0);
            text(g,SocialHubScreen.tr("section.community"),42,17,layout.width()-62,ACCENT);
        } else {
        text(g,tr("title"),52,17,layout.width()-210,ACCENT);button(g,layout.back(),Component.literal("‹"),()->{if(target!=null&&!layout.wide())pick(null);else onClose();},true,false,mx,my);
        button(g,new SocialLayout.Rect(layout.width()-182,10,24,22),Component.literal("⚙"),()->CommunityScreen.settings(this),true,false,mx,my);
        button(g,layout.search(),tr("find"),()->PlayerProfileScreen.openSearch(this),true,false,mx,my);button(g,layout.close(),Component.literal("×"),this::onClose,true,false,mx,my);
        }
        renderCommunityTabs(g,mx,my);
        if(listVisible())renderList(g,mx,my,pt);if(detailVisible())renderDetail(g,mx,my,pt);
        else if(chats()&&layout.wide()){var r=layout.detail();rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);text(g,tr("choose"),r.x()+16,r.y()+20,r.width()-32,ACCENT);g.drawWordWrap(font,tr("privacy_note"),r.x()+16,r.y()+44,r.width()-32,MUTED);}
        if(chats()&&!input.isVisible()&&before==0)text(g,tr("result."+result),layout.wide()&&target!=null?202:86,layout.height()-25,layout.width()-(layout.wide()&&target!=null?216:100),MUTED);
        if(menuOpen && confirmation==null && detailVisible() && state!=null) renderChatMenu(g,mx,my);
        if(confirmation!=null){input.setFocused(false);var r=layout.detail();g.fill(r.x(),r.y(),r.right(),r.bottom(),0xF01B2327);g.drawWordWrap(font,tr("confirm."+confirmation.name().toLowerCase(Locale.ROOT)),r.x()+12,r.y()+20,r.width()-24,ACCENT);
            button(g,new SocialLayout.Rect(r.x()+12,r.y()+56,110,24),tr("confirm"),()->{var a=confirmation;confirmation=null;request(a,"");},pending==null,true,mx,my);
            button(g,new SocialLayout.Rect(r.x()+128,r.y()+56,110,24),tr("cancel"),()->confirmation=null,true,false,mx,my);}
        float fade=CommunityFx.reduced?0:1-CommunityFx.ease((net.minecraft.Util.getMillis()-openedAt)/180.0);
        if(fade>0)g.fill(1,1,layout.width()-1,layout.height()-1,((int)(110*fade)<<24)|(SURFACE&0xFFFFFF));
        g.flush();g.pose().popPose();
        if(iconHint!=null)g.renderTooltip(font,iconHint,mouseX,mouseY);
    }
    private void renderCommunityTabs(GuiGraphics g, double mx, double my) {
        int unread = Math.max(SocialHubScreen.unreadChats(), state==null?0:state.unread());
        int requests = SocialHubScreen.friendRequests();
        if (state != null && filter == 1) requests = state.requests();
        modeButton(g,layout.tab(0,2),tr("chats"),SocialHubIcons.Icon.MESSAGES,unread,chats(),
                ()->openDialogs(this),mx,my);
        modeButton(g,layout.tab(1,2),tr("friends"),SocialHubIcons.Icon.FRIENDS,requests,!chats(),
                ()->openFriends(this),mx,my);
        if (!chats()) {
            int[] filters = {0,1,3};
            String[] labels = {"friends","requests","blocked"};
            var icons = new SocialHubIcons.Icon[]{SocialHubIcons.Icon.FRIENDS,SocialHubIcons.Icon.NOTIFICATIONS,SocialHubIcons.Icon.CHAT_BLOCK};
            int width = (layout.width()-28)/filters.length;
            for (int i=0;i<filters.length;i++) {
                int next = filters[i];
                modeButton(g,new SocialLayout.Rect(14+i*width,78,width-4,24),tr(labels[i]),icons[i],
                        next==1?requests:0,filter==next,()->{if(pending==null)peopleFilter(next);},mx,my);
            }
        }
    }

    private void modeButton(GuiGraphics g, SocialLayout.Rect r, Component label, SocialHubIcons.Icon icon,
            int count, boolean selected, Runnable action, double mx, double my) {
        Component caption = count > 0 ? label.copy().append("  "+count) : label;
        paintedButton(g,r,caption,icon,action,true,selected,mx,my);
    }

    private void drawPersonActions(GuiGraphics g, SocialLayout.Rect row, SocialStatePayload.Contact contact,
            double mx, double my) {
        int x = row.right()-112;
        int y = row.y()+7;
        boolean ready = pending==null;
        switch (contact.relation()) {
            case FRIEND -> paintedButton(g,new SocialLayout.Rect(x,y,106,24),SocialHubScreen.tr("write"),
                    SocialHubIcons.Icon.MESSAGES,()->open(this,contact.id()),true,false,mx,my);
            case INCOMING -> {
                paintedButton(g,new SocialLayout.Rect(x-108,y,106,24),tr("action.decline"),
                        SocialHubIcons.Icon.CLOSE,()->peopleAction(contact.id(),SocialBook.Action.DECLINE),ready,false,mx,my);
                paintedButton(g,new SocialLayout.Rect(x,y,106,24),tr("action.accept"),
                        SocialHubIcons.Icon.CHECK,()->peopleAction(contact.id(),SocialBook.Action.ACCEPT),ready,true,mx,my);
            }
            case OUTGOING -> paintedButton(g,new SocialLayout.Rect(x-20,y,126,24),tr("action.cancel"),
                    SocialHubIcons.Icon.CLOSE,()->peopleAction(contact.id(),SocialBook.Action.CANCEL),ready,false,mx,my);
            case BLOCKED -> paintedButton(g,new SocialLayout.Rect(x-20,y,126,24),tr("action.unblock"),
                    SocialHubIcons.Icon.CHAT_BLOCK,()->peopleAction(contact.id(),SocialBook.Action.UNBLOCK),ready,false,mx,my);
            default -> {}
        }
    }

    private void renderList(GuiGraphics g,double mx,double my,float pt){var r=listBounds();rounded(g,r.x(),r.y(),r.width(),r.height(),4,INSET);
        rounded(g,r.x()+3,r.y()+3,r.width()-6,22,3,filterBox.isFocused()?RAISED:SURFACE);
        SocialHubIcons.Icon.SEARCH.draw(g,r.x()+7,r.y()+6,filterBox.isFocused(),0);
        filterBox.render(g,(int)mx,(int)my,pt);visible.clear();if(state!=null)state.contacts().stream().filter(c->c.name().toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))).forEach(visible::add);
        if (filter == 1) visible.sort(Comparator.comparingInt(c -> c.relation() == SocialBook.Relation.INCOMING ? 0 : 1));
        allianceChat=com.example.examplemod.client.AllianceClient.ownSummary();groupOffset=filter==2&&allianceChat.hasUUID("Id")&&allianceChat.getString("Name").toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))?1:0;int max=Math.max(0,(visible.size()+groupOffset)*42+requestGroupSpace(visible.size()-1)-(r.height()-29));listScroll=Math.clamp(listScroll,0,max);var body=new SocialLayout.Rect(r.x(),r.y()+28,r.width(),r.height()-28);clip(g,body);
        if(groupOffset==1){
            var row=listRow(0,0);
            boolean hovered=body.contains(mx,my)&&row.contains(mx,my);
            rounded(g,row.x(),row.y(),row.width(),row.height(),4,hovered?RAISED:SURFACE);
            AllianceScreen.drawEmblem(g,row.x()+5,row.y()+7,24,allianceChat.getInt("Color"),allianceChat.getInt("Emblem"));
            SocialHubIcons.Icon.MESSAGES.draw(g,row.x()+34,row.y()+11,hovered,0);
            text(g,Component.literal(allianceChat.getString("Name")),row.x()+54,row.y()+7,row.width()-60,ACCENT);
            text(g,SocialHubScreen.tr("alliance_chat"),row.x()+54,row.y()+23,row.width()-60,MUTED);
        }
        if(visible.isEmpty()&&groupOffset==0)text(g,tr(pending!=null?"result.loading":"empty."+filter),r.x()+10,r.y()+45,r.width()-20,MUTED);
        for(int i=0;i<visible.size();i++){var c=visible.get(i);var row=listRow(i+groupOffset,requestGroupSpace(i));
            if (requestGroupStart(i)) text(g,tr(c.relation()==SocialBook.Relation.INCOMING?"incoming_requests":"outgoing_requests"),
                    row.x()+8,row.y()-17,row.width()-16,ACCENT);
            boolean over=body.contains(mx,my)&&row.contains(mx,my);rounded(g,row.x(),row.y(),row.width(),row.height(),3,Objects.equals(c.id(),target)?RAISED:over?SURFACE:INSET);
            drawPlayerHead(g, c.id(), row.x()+6, row.y()+7, 24);
            g.fill(row.x()+25,row.y()+26,row.x()+30,row.y()+31,c.online()?0xFF8EC7AA:0xFF68777B);
            var last = filter == 2 ? dialogPreviews.get(c.id()) : null;
            int actionSpace = chats() ? 0 : c.relation() == SocialBook.Relation.INCOMING ? 226 : 138;
            int labelInset = chats() ? 54 : 37;
            if (chats()) SocialHubIcons.Icon.MESSAGES.draw(g,row.x()+34,row.y()+11,
                    over||Objects.equals(c.id(),target),0);
            text(g, playerName(c.id(),c.name()), row.x()+labelInset, row.y()+7,
                    row.width()-labelInset-(last==null?6:47)-actionSpace, TEXT);
            if (last != null) {
                var time = Instant.ofEpochMilli(last.time()).atZone(ZoneId.systemDefault());
                g.drawString(font, MESSAGE_TIME.format(time), row.right()-34, row.y()+7, MUTED, false);
            }
            Component preview = last != null ? Component.literal(last.text()) : c.city().isEmpty()
                    ? tr("relation."+c.relation().name().toLowerCase(Locale.ROOT))
                    : Component.literal(c.city()+" · ").append(Component.translatable("role.createcityexpansioncore."+c.role().toLowerCase(Locale.ROOT)));
            text(g, preview, row.x()+labelInset, row.y()+22,
                    row.width()-labelInset-(c.unread()>0?29:6)-actionSpace, MUTED);
            if (chats() && c.unread()>0) {
                rounded(g,row.right()-25,row.y()+20,20,13,4,0xFF52412B);
                g.drawCenteredString(font,Integer.toString(c.unread()),row.right()-15,row.y()+23,ACCENT);
            }
            if (!chats() && row.y()>=body.y() && row.bottom()<=body.bottom()) drawPersonActions(g,row,c,mx,my);
        }unclip(g);
        button(g,layout.previous(),Component.literal("‹"),()->{page--;request(SocialBook.Action.LIST,"");},pending==null&&page>0,false,mx,my);button(g,layout.next(),Component.literal("›"),()->{page++;request(SocialBook.Action.LIST,"");},pending==null&&state!=null&&(page+1)*16<state.total(),false,mx,my);
    }
    private List<SocialBook.Action> actions(){if(state==null||state.target()==null)return List.of();return switch(state.relation()){
        case NONE->List.of(SocialBook.Action.REQUEST,SocialBook.Action.BLOCK);case INCOMING->List.of(SocialBook.Action.ACCEPT,SocialBook.Action.DECLINE,SocialBook.Action.BLOCK);
        case OUTGOING->List.of(SocialBook.Action.CANCEL,SocialBook.Action.BLOCK);case FRIEND->List.of(SocialBook.Action.REMOVE,SocialBook.Action.BLOCK);
        case BLOCKED->List.of(SocialBook.Action.UNBLOCK);case UNAVAILABLE->List.of();};}
    private void iconButton(GuiGraphics g, SocialLayout.Rect r, SocialHubIcons.Icon icon, Component label,
            Runnable run, boolean enabled, boolean primary, double mx, double my) {
        buttons.add(new Button(r, label, run, enabled, primary));
        var bounds = new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height());
        boolean hovered = enabled && r.contains(mx,my);
        float motion = hoverMotion.amount(bounds, hovered);
        g.pose().pushPose();
        try {
            SocialHubIcons.scaleButton(g, bounds, motion);
            control(g,bounds,false,hovered,enabled,primary);
            icon.draw(g,r.x()+(r.width()-16)/2,r.y()+(r.height()-16)/2,hovered,motion);
        } finally {
            g.pose().popPose();
        }
        if (r.contains(mx,my) && !menuOpen && confirmation==null) {
            iconHint = label;
        }
    }

    private Component iconHint;

    private void renderDetail(GuiGraphics g, double mx, double my, float pt) {
        var header = layout.chatHeader();
        var detail = layout.detail();
        rounded(g, detail.x(), detail.y(), detail.width(), detail.height(), 4, INSET);
        rounded(g, header.x(), header.y(), header.width(), header.height(), 4, SURFACE);
        int avatarX = header.x()+8;
        if (!layout.wide()) {
            iconButton(g,layout.chatBack(),SocialHubIcons.Icon.CHAT_BACK,tr("dialogs"),()->pick(null),
                    pending==null,false,mx,my);
            avatarX = layout.chatBack().right()+7;
        }
        drawPlayerHead(g,target,avatarX,header.y()+6,28);
        int labelX = avatarX+36;
        var player = minecraft.getConnection().getPlayerInfo(target);
        Component name = state==null ? tr("result.loading") : playerName(target,state.name());
        text(g,name,labelX,header.y()+8,layout.chatMenu().x()-labelX-8,TEXT);
        if (state==null) return;
        boolean online = player!=null;
        Component presence = Component.translatable("gui.createcityexpansioncore.profile."+(online?"online":"offline"));
        if (!state.city().isEmpty()) presence = presence.copy().append(" · ").append(state.city())
                .append(" · ").append(Component.translatable("role.createcityexpansioncore."
                        + state.role().toLowerCase(Locale.ROOT)));
        text(g,presence,labelX,header.y()+22,layout.chatMenu().x()-labelX-8,online?0xFF91C6AE:MUTED);
        iconButton(g,layout.chatMenu(),SocialHubIcons.Icon.CHAT_MORE,tr("chat_actions"),()->{
            menuOpen=!menuOpen;
            if (menuOpen) input.setFocused(false);
        },true,false,mx,my);

        var body = layout.messages();
        drawMessages(g, body);
        if(input.isVisible()){
            var field=layout.input();
            rounded(g,field.x(),field.y(),field.width(),field.height(),3,input.isFocused()?RAISED:INSET);
            input.render(g,(int)mx,(int)my,pt);
            iconButton(g,layout.send(),SocialHubIcons.Icon.CHAT_SEND,tr("send"),this::send,
                    pending==null&&!input.getValue().isBlank(),true,mx,my);
        } else if (before!=0) {
            button(g,layout.input(),tr("latest"),this::latest,pending==null,false,mx,my);
        }
        if(before==0&&messageScroll<maxMessageScroll()-12){
            button(g,new SocialLayout.Rect(body.right()-146,body.bottom()-28,140,23),
                    SocialHubScreen.tr("new_messages"),()->messageScroll=maxMessageScroll(),true,true,mx,my);
        }
    }

    private void drawMessages(GuiGraphics g, SocialLayout.Rect body) {
        int top = 6;
        LocalDate previous = null;
        var rows = new ArrayList<MessageRow>();
        var zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        int maxBubbleWidth = body.width()*4/5;
        for (var message : state.messages()) {
            LocalDate day = Instant.ofEpochMilli(message.time()).atZone(zone).toLocalDate();
            if (!day.equals(previous)) top += 19;
            previous = day;
            var lines = font.split(Component.literal(message.text()), maxBubbleWidth-16);
            int bubbleWidth = Math.max(56, lines.stream().mapToInt(font::width).max().orElse(0)+16);
            int bubbleHeight = lines.size()*11+21;
            rows.add(new MessageRow(message.id(),message.time(),day,message.sender().equals(minecraft.player.getUUID()),
                    lines,bubbleWidth,top,bubbleHeight));
            top += bubbleHeight+7;
        }
        messageHeight = top;
        if (restoreMessageAnchor) {
            messageScroll = SocialLayout.anchorMessages(messageScroll,
                    displayedMessages.stream().map(MessageRow::span).toList(),
                    rows.stream().map(MessageRow::span).toList());
            restoreMessageAnchor = false;
        }
        displayedMessages = rows;
        messageScroll = Math.clamp(messageScroll,0,maxMessageScroll());
        clip(g,body);
        try {
            if (rows.isEmpty()) {
                text(g,tr("relation."+state.relation().name().toLowerCase(Locale.ROOT)),
                        body.x()+12,body.y()+14,body.width()-24,TEXT);
                g.drawWordWrap(font,tr(state.relation()==SocialBook.Relation.FRIEND?"first_message":"friends_only"),
                        body.x()+12,body.y()+37,body.width()-24,MUTED);
            }
            previous = null;
            for (var row : rows) {
                int y = body.y()+row.top()-messageScroll;
                if (!row.day().equals(previous)) {
                    Component day = row.day().equals(today) ? tr("today")
                            : row.day().equals(today.minusDays(1)) ? tr("yesterday")
                            : Component.literal(MESSAGE_DATE.format(row.day()));
                    g.drawCenteredString(font,day,body.x()+body.width()/2,y-16,MUTED);
                }
                previous = row.day();
                int x = row.own()?body.right()-row.width()-8:body.x()+8;
                rounded(g,x,y,row.width(),row.height(),5,row.own()?0xFF2B4139:RAISED);
                for (int i=0;i<row.lines().size();i++) {
                    g.drawString(font,row.lines().get(i),x+8,y+6+i*11,TEXT,false);
                }
                String time = MESSAGE_TIME.format(Instant.ofEpochMilli(row.time()).atZone(zone));
                g.drawString(font,time,x+row.width()-font.width(time)-8,y+row.height()-11,MUTED,false);
            }
        } finally {
            unclip(g);
        }
    }

    private void latest() {
        before=0;
        scrollToEnd=true;
        request(SocialBook.Action.LIST,"");
    }

    private void older() {
        before=state.messages().getFirst().id();
        messageScroll=0;
        scrollToEnd=false;
        restoreMessageAnchor=false;
        request(SocialBook.Action.LIST,"");
    }

    private void renderChatMenu(GuiGraphics g, double mx, double my) {
        boolean enabled = pending==null;
        var relationship = actions();
        boolean hasOlder = state.older() && !state.messages().isEmpty();
        int count = 2 + relationship.size() + (before != 0 ? 1 : 0) + (hasOlder ? 1 : 0);
        var first = layout.chatMenuItem(0);
        rounded(g,first.x()-4,first.y()-4,first.width()+8,count*first.height()+8,5,EDGE);
        rounded(g,first.x()-3,first.y()-3,first.width()+6,count*first.height()+6,4,SURFACE);
        menuItem(g,SocialHubIcons.Icon.PROFILE,tr("profile"),()->{
            if(!SocialHubScreen.inspect(target))PlayerProfileScreen.open(this,target);
        },enabled,false,mx,my);
        menuItem(g,SocialHubIcons.Icon.NOTIFICATIONS,tr(CommunityFx.muted(target)?"unmute":"mute"),()->{
            CommunityFx.toggleMute(target);
            PacketDistributor.sendToServer(new CommunityRequestPayload(UUID.randomUUID(),CommunityRequestPayload.Action.MUTE,
                    null,target,null,CommunityFx.muted(target)?1:0,0,""));
        },enabled,false,mx,my);
        for(var action:relationship){
            boolean destructive=action==SocialBook.Action.BLOCK||action==SocialBook.Action.REMOVE;
            var icon = action==SocialBook.Action.BLOCK||action==SocialBook.Action.UNBLOCK
                    ? SocialHubIcons.Icon.CHAT_BLOCK : SocialHubIcons.Icon.FRIENDS;
            menuItem(g,icon,tr("action."+action.name().toLowerCase(Locale.ROOT)),()->action(action),
                    enabled,destructive,mx,my);
        }
        if (before!=0) menuItem(g,SocialHubIcons.Icon.MESSAGES,tr("latest"),this::latest,enabled,false,mx,my);
        if (hasOlder) {
            menuItem(g,SocialHubIcons.Icon.PROJECTS,tr("older"),this::older,enabled,false,mx,my);
        }
    }

    private void menuItem(GuiGraphics g, SocialHubIcons.Icon icon, Component label, Runnable action,
            boolean enabled, boolean destructive, double mx, double my) {
        var bounds = layout.chatMenuItem(menuButtons.size());
        menuButtons.add(new Button(bounds,label,action,enabled,false));
        boolean hovered=enabled&&bounds.contains(mx,my);
        var rect = new WorldMapUiLayout.Rect(bounds.x(),bounds.y(),bounds.width(),bounds.height());
        SocialHubIcons.paintButton(g,font,rect,label,icon,hoverMotion,hovered,enabled,false,destructive);
    }
    @Override public boolean mouseClicked(double x,double y,int button){double mx=layout.localX(x),my=layout.localY(y);if(button!=0)return false;
        if(confirmation!=null){for(int i=buttons.size()-1;i>=0;i--){var b=buttons.get(i);if(b.r.y()==layout.detail().y()+56&&b.r.contains(mx,my)){if(b.enabled){com.example.examplemod.client.CommunityFx.sound("click");b.run.run();}return true;}}return true;}
        if (menuOpen) {
            for (var item : menuButtons) {
                if (item.r().contains(mx,my)) {
                    if (item.enabled()) {
                        menuOpen=false;
                        CommunityFx.sound("click");
                        item.run().run();
                    }
                    return true;
                }
            }
            menuOpen=false;
            return true;
        }
        for(int i=buttons.size()-1;i>=0;i--){var b=buttons.get(i);if(b.r.contains(mx,my)){if(b.enabled){com.example.examplemod.client.CommunityFx.sound("click");b.run.run();}return true;}}
        if(groupOffset==1&&listVisible()&&listBounds().contains(mx,my)&&my>=listBounds().y()+28&&listRow(0,0).contains(mx,my)){AllianceScreen.openChat(this,allianceChat.getUUID("Id"));return true;}
        if(pending==null&&listVisible()&&listBounds().contains(mx,my)&&my>=listBounds().y()+28)for(int i=0;i<visible.size();i++)if(listRow(i+groupOffset,requestGroupSpace(i)).contains(mx,my)){if(chats())pick(visible.get(i).id());else SocialHubScreen.inspect(visible.get(i).id());return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(menuOpen||confirmation!=null)return true;double mx=layout.localX(x),my=layout.localY(y);if(listVisible()&&listBounds().contains(mx,my)){listScroll=Math.max(0,listScroll-(int)(dy*24));return true;}if(target!=null&&layout.messages().contains(mx,my)){messageScroll=Math.clamp(messageScroll-(int)(dy*24),0,Math.max(0,messageHeight-layout.messages().height()));return true;}return false;}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return super.mouseDragged(layout.localX(x),layout.localY(y),b,dx/layout.scale(),dy/layout.scale());}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(menuOpen){if(key==256)menuOpen=false;return true;}if(confirmation!=null&&key==256){confirmation=null;return true;}if(confirmation==null&&(key==257||key==335)&&input.isFocused()){send();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){if(!SocialHubScreen.back(this))minecraft.setScreen(parent);}
    @Override public void removed(){pending=null;readPending=null;super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
