package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.map.AtlasPlaceIcon;
import com.example.examplemod.passenger.PassengerEditorNetwork;
import com.example.examplemod.passenger.PassengerEditorNetwork.Action;
import com.example.examplemod.passenger.PassengerService;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;
import static com.example.examplemod.client.screen.SocialHubIcons.Icon;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

public final class PassengerLinesScreen extends Screen {
    private enum Page { DIRECTORY, CREATE, LINE, PLATFORMS, TRAIN, SERVICE, SETTINGS, COLORS, DESCRIPTION, CONFIRM }
    private static final UUID NONE = new UUID(0, 0);
    private static final int PAGE_SIZE = 4, BUTTON_HEIGHT = 20;
    private static final WeakHashMap<PolishedWorldMapScreen, UUID> lastLine = new WeakHashMap<>();
    private static WeakReference<PassengerLinesScreen> opened = new WeakReference<>(null);
    private final PolishedWorldMapScreen atlas;
    private final SocialHubIcons.HoverMotion hover = new SocialHubIcons.HoverMotion();
    private ListTag lines = new ListTag(), stations = new ListTag(), trains = new ListTag();
    private UUID selected, pending;
    private CustomPacketPayload request;
    private long sent, retryAt, refreshed, transition;
    private int retries, listPage;
    private boolean canClearAndDelete, canBindAndColor, canEditDescription, initial = true, background;
    private String status = "ok", draft = "", seconds = "20", search = "", renderedSearch = "";
    private String description = "", originalDescription = "";
    private EditBox stationFilter;
    private boolean noMatches;
    private Page page = Page.DIRECTORY;
    private Action confirmation;

    PassengerLinesScreen(PolishedWorldMapScreen atlas) {
        super(text("title"));
        this.atlas = atlas;
        selected = lastLine.getOrDefault(atlas, NONE);
    }
    static Component text(String key, Object... args) {
        return Component.translatable("gui.createcityexpansioncore.passenger_editor." + key, args);
    }
    static boolean supported() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2) && mc.getConnection() != null
                && mc.getConnection().hasChannel(PassengerEditorNetwork.Request.TYPE);
    }
    public static void receive(CompoundTag data) {
        var screen = opened.get();
        if (screen != null && screen.minecraft.screen == screen.atlas && screen.atlas.ownsAtlasTool(screen)
                && screen.pending != null && data.hasUUID("Nonce") && screen.pending.equals(data.getUUID("Nonce"))) {
            screen.reply(data);
        }
    }
    private void reply(CompoundTag data) {
        status = data.getString("Status");
        // Only an explicit rejection before execution is safe to retry.
        if (status.equals("rate") && retries < 3 && request != null) {
            retryAt = Util.getMillis() + (300L << retries++);
            return;
        }
        var completed = request;
        boolean silent = background;
        pending = null;
        request = null;
        retryAt = 0;
        background = false;
        refreshed = Util.getMillis();
        if (data.contains("Lines")) {
            lines = data.getList("Lines", 10);
            stations = data.getList("Stations", 10);
            trains = data.getList("Trains", 10);
            canClearAndDelete = data.getBoolean("CanClearAndDelete");
            canBindAndColor = data.getBoolean("CanBindAndColor");
            canEditDescription = data.getBoolean("CanEditDescription")
                    && minecraft.getConnection().hasChannel(PassengerEditorNetwork.DescriptionRequest.TYPE);
            selected = data.getUUID("Selected");
            if (index() < 0) {
                selected = NONE;
                if (page != Page.CREATE) page = Page.DIRECTORY;
            } else if (initial) page = Page.LINE;
            initial = false;
            lastLine.put(atlas, selected);
        }
        if (status.equals("ok")) {
            if (completed instanceof PassengerEditorNetwork.DescriptionRequest) {
                page = Page.LINE;
                status = "description_saved";
            } else if (completed instanceof PassengerEditorNetwork.Request command) {
                switch (command.action()) {
                    case CREATE -> { draft = ""; page = Page.LINE; }
                    case APPEND, APPEND_REGISTERED -> {
                        search = ""; listPage = 0;
                        if (line().getBoolean("StopsFull")) page = Page.LINE;
                    }
                    case SELECT_TRAIN, STOP, START, START_TRAIN -> page = Page.SERVICE;
                    case SET_COLOR -> page = Page.SETTINGS;
                    case POP, CLEAR_STOPS -> page = Page.LINE;
                    case DELETE -> { selected = NONE; lastLine.remove(atlas); page = Page.DIRECTORY; }
                    case GET_BINDER -> status = "binder_ready";
                    default -> {}
                }
            }
        }
        if (!silent && !(completed instanceof PassengerEditorNetwork.Request r && r.action() == Action.LIST))
            CommunityFx.sound(status.equals("ok") || status.equals("description_saved") || status.equals("binder_ready") ? "map_save" : "map_denied");
        rebuildWidgets();
    }
    private int index() {
        for (int i = 0; i < lines.size(); i++) if (lines.getCompound(i).getUUID("Id").equals(selected)) return i;
        return -1;
    }
    private CompoundTag line() {
        int row = index();
        return row < 0 ? new CompoundTag() : lines.getCompound(row);
    }
    private int panelWidth() { return Math.min(480, width - 12); }
    private int panelHeight() { return Math.min(258, height - 8); }
    private int x() { return (width - panelWidth()) / 2 + 10; }
    private int y() { return (height - panelHeight()) / 2; }
    private int w() { return panelWidth() - 20; }
    private int footer() { return y() + panelHeight() - BUTTON_HEIGHT - 8; }
    private int half() { return (w() - 5) / 2; }
    private boolean idle() { return pending == null || background; }
    private boolean mutableStops() { return !line().getBoolean("Assigned") && !line().getBoolean("StopsFull"); }
    private Button button(int x, int y, int w, Component label, Icon icon, Runnable action, boolean enabled, boolean primary) {
        var control = new Button(Button.builder(label, ignored -> action.run()).bounds(x, y, w, BUTTON_HEIGHT)) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
                var bounds = new WorldMapUiLayout.Rect(getX(), getY(), getWidth(), getHeight());
                SocialHubIcons.paintButton(g, font, bounds, getMessage(), icon, hover, isHoveredOrFocused(), active, false, primary);
                if (isFocused()) g.renderOutline(getX()+2, getY()+2, getWidth()-4, getHeight()-4, ACCENT);
            }
        };
        control.active = enabled;
        control.setTooltip(Tooltip.create(label));
        return addRenderableWidget(control);
    }
    private Button button(int x, int y, int w, String key, Icon icon, Runnable action, boolean enabled, boolean primary) {
        return button(x, y, w, text(key), icon, action, enabled, primary);
    }
    private EditBox field(int y, int width, String key, String initialValue, int limit, Consumer<String> changed) {
        var box = AtlasFormStyle.field(font, x(), y, width, BUTTON_HEIGHT, text(key));
        box.setMaxLength(limit);
        box.setHint(text(key));
        box.setValue(initialValue);
        box.setResponder(changed);
        return addRenderableWidget(box);
    }
    private void show(Page next) {
        cancelRefresh();
        page = next;
        listPage = 0;
        search = "";
        status = "ok";
        transition = Util.getMillis();
        rebuildWidgets();
    }
    @Override protected void init() {
        if (opened.get() != this) {
            opened = new WeakReference<>(this);
            CommunityFx.load();
            transition = Util.getMillis();
            send(Action.LIST, "");
        }
        var row = line();
        boolean enabled = idle();
        int x = x(), y = y(), w = w(), footer = footer();
        int backWidth = page==Page.LINE ? Math.min(94,font.width(text("all_lines"))+32) : 23;
        button(x, y+8, backWidth, page==Page.LINE?"all_lines":"back", Icon.CHAT_BACK, this::onClose, true, false);
        if (page == Page.DIRECTORY || page == Page.LINE || page == Page.SERVICE)
            button(x+w-23, y+8, 23, "refresh", Icon.REFRESH, () -> send(Action.LIST, ""), enabled, false);
        switch (page) {
            case DIRECTORY -> {
                buildDirectory();
                button(x, footer, w, "create", Icon.EDIT, () -> show(Page.CREATE), enabled, true);
            }
            case CREATE -> {
                var name = field(y+69, w, "name", draft, 64, value -> draft = value);
                setInitialFocus(name);
                button(x, footer, w, "create", Icon.SAVE, () -> {
                    if (draft.isBlank()) status = "line_name";
                    else send(Action.CREATE, draft.strip());
                }, enabled, true);
            }
            case LINE -> {
                button(x+w-50, y+8, 23, "settings", Icon.SETTINGS, () -> show(Page.SETTINGS), enabled, false);
                int half = half();
                boolean assigned = row.getBoolean("Assigned");
                button(x, footer, half, "choose_station", Icon.EDIT, () -> openPicker(Page.PLATFORMS),
                        enabled && mutableStops(), !assigned && row.getList("Stops",8).size()<2);
                button(x+half+5, footer, w-half-5, "service", Icon.NEXT, () -> show(Page.SERVICE), enabled,
                        assigned || row.getList("Stops",8).size()>=2);
            }
            case PLATFORMS, TRAIN -> buildPicker();
            case SERVICE -> {
                boolean assigned = row.getBoolean("Assigned");
                if (!assigned) {
                    var dwell = field(y+110, 42, "seconds", seconds, 3, value -> seconds = value);
                    dwell.setFilter(value -> value.matches("[0-9]{0,3}"));
                    if (canBindAndColor) button(x, y+68, w, "choose_train", Icon.SEARCH, () -> openPicker(Page.TRAIN), enabled, false);
                }
                button(x, footer, w, assigned ? "stop" : "assign", assigned ? Icon.CLOSE : Icon.CHECK, () -> {
                    if (assigned) { send(Action.STOP, ""); return; }
                    int value = dwellSeconds();
                    if (value<10 || value>120) { status = "seconds"; return; }
                    if (row.hasUUID("Train")) send(Action.START_TRAIN, row.getUUID("Train").toString());
                    else send(Action.START, "");
                }, enabled && (assigned || row.getList("Stops",8).size()>=2), true);
            }
            case SETTINGS -> buildSettings();
            case COLORS -> buildColors();
            case DESCRIPTION -> {
                setInitialFocus(field(y+68, w, "description", description, PassengerService.MAX_DESCRIPTION, value -> description = value));
                boolean stale = !row.getString("Description").equals(originalDescription);
                if (stale) button(x, y+140, w, "load_description", Icon.REFRESH, this::editDescription, enabled, false);
                button(x, footer, w, "save_description", Icon.SAVE, this::saveDescription, enabled && !stale, true);
            }
            case CONFIRM -> {
                int half = half();
                button(x, footer, half, "cancel", Icon.CHAT_BACK, this::onClose, enabled, false);
                button(x+half+5, footer, w-half-5, confirmation==Action.DELETE?"delete":"clear", Icon.CHECK,
                        () -> send(confirmation, ""), enabled, true);
            }
        }
    }
    private void openPicker(Page picker) {
        show(picker);
        setFocused(stationFilter);
    }
    private int pageCount(int size) { return Math.max(1, (size+PAGE_SIZE-1)/PAGE_SIZE); }
    private void pages(int size, int y) {
        int count = pageCount(size);
        if (count == 1) return;
        button(x()+w()-54, y, 24, "previous", Icon.CHAT_BACK, () -> { listPage--; rebuildWidgets(); }, listPage>0 && idle(), false);
        button(x()+w()-26, y, 24, "next", Icon.NEXT, () -> { listPage++; rebuildWidgets(); }, listPage+1<count && idle(), false);
    }
    private void buildDirectory() {
        listPage = Math.clamp(listPage, 0, pageCount(lines.size())-1);
        for (int i=listPage*PAGE_SIZE; i<Math.min(lines.size(),(listPage+1)*PAGE_SIZE); i++) {
            var entry = lines.getCompound(i);
            colorButton(x(), y()+62+(i%PAGE_SIZE)*24, w(), Component.literal(entry.getString("Name")), dye(entry), () -> {
                selected=entry.getUUID("Id"); lastLine.put(atlas,selected); show(Page.LINE);
            });
        }
        pages(lines.size(), y()+37);
    }
    private ArrayList<CompoundTag> matches() {
        boolean train = page == Page.TRAIN;
        return matchingChoices(train?trains:stations,line().getList("StopIds",11),train,search);
    }
    static ArrayList<CompoundTag> matchingChoices(ListTag entries,ListTag stops,boolean train,String query) {
        var used = new HashSet<UUID>();
        for (var tag : stops) used.add(NbtUtils.loadUUID(tag));
        var result = new ArrayList<CompoundTag>();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        for (int i=0; i<entries.size(); i++) {
            var item = entries.getCompound(i);
            if ((train || !used.contains(item.getUUID("Id"))) && (item.getString("Name")+" "+item.getString("Position")).toLowerCase(Locale.ROOT).contains(needle))
                result.add(item);
        }
        result.sort(Comparator.comparing((CompoundTag item) -> !item.getBoolean("Marked"))
                .thenComparing(item -> item.getString("Name"), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.getUUID("Id")));
        return result;
    }
    private void buildPicker() {
        boolean train = page == Page.TRAIN;
        var matches = matches();
        noMatches=matches.isEmpty();
        listPage = Math.clamp(listPage, 0, pageCount(matches.size())-1);
        int searchWidth = w() - (pageCount(matches.size())>1 ? 60 : 0);
        stationFilter = field(y()+39, searchWidth, train?"train_search":"station_search", search, 96, value -> { search=value; listPage=0; });
        pages(matches.size(),y()+39);
        for (int i=listPage*PAGE_SIZE; i<Math.min(matches.size(),(listPage+1)*PAGE_SIZE); i++) {
            var item = matches.get(i);
            String suffix = train ? item.getUUID("Id").toString().substring(0,8) : item.getString("Position");
            var label = Component.literal((item.getBoolean("Marked")?"✓ ":"")+item.getString("Name")+"  ·  "+suffix);
            button(x(), y()+65+(i%PAGE_SIZE)*22, w(), label, train?Icon.CHECK:Icon.NEXT, () ->
                    send(train?Action.SELECT_TRAIN:Action.APPEND_REGISTERED,item.getUUID("Id").toString()), idle(), false);
        }
        renderedSearch=search;
        if (train) button(x(), footer(), w(), "auto_train", Icon.CHECK, () -> send(Action.SELECT_TRAIN,""), idle(), false);
        else {
            int half=half();
            button(x(), footer(), half, "append_here", Icon.ACTION, () -> send(Action.APPEND,""), idle() && mutableStops(), false);
            button(x()+half+5, footer(), w()-half-5, "done_stops", Icon.CHECK, () -> show(Page.LINE), idle(), true);
        }
    }
    private void buildSettings() {
        int x=x(), y=y()+39, w=w(), half=half();
        if (canEditDescription) {
            button(x,y,w,"description",Icon.EDIT,this::editDescription,idle(),false);
            y+=23;
        }
        if (canBindAndColor) {
            colorButton(x,y,half,text("color"),dye(line()),() -> show(Page.COLORS));
            button(x+half+5,y,w-half-5,"binder",Icon.GIFT,()->send(Action.GET_BINDER,""),idle(),false);
        } else button(x,y,w,"platform",Icon.ACTION,()->send(Action.PLATFORM,""),idle(),false);
        y+=28;
        boolean hasStops = !line().getList("Stops",8).isEmpty();
        button(x,y,w,"pop",Icon.CHAT_BACK,()->send(Action.POP,""),idle() && !line().getBoolean("Assigned") && hasStops,false);
        if (canClearAndDelete) {
            button(x,y+23,w,"clear",Icon.CHAT_BLOCK,()->confirm(Action.CLEAR_STOPS),idle() && hasStops,false);
            button(x,footer(),w,"delete",Icon.CLOSE,()->confirm(Action.DELETE),idle(),false);
        }
    }
    private void confirm(Action action) { confirmation=action; show(Page.CONFIRM); }
    private static DyeColor dye(CompoundTag row) { return DyeColor.byName(row.getString("Color"),DyeColor.ORANGE); }
    private void buildColors() {
        int cell=(w()-12)/4;
        for (var dye : DyeColor.values()) {
            int n=dye.getId();
            colorButton(x()+(n%4)*(cell+4),y()+48+(n/4)*28,cell,Component.translatable("color.minecraft."+dye.getName()),dye,
                    () -> send(Action.SET_COLOR,dye.getName()));
        }
    }
    private void colorButton(int x, int y, int w, Component label, DyeColor dye, Runnable action) {
        var control = new Button(Button.builder(label,ignored -> action.run()).bounds(x,y,w,BUTTON_HEIGHT)) {
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
                var bounds = new WorldMapUiLayout.Rect(getX(),getY(),getWidth(),getHeight());
                float motion=hover.amount(bounds,active && isHoveredOrFocused());
                g.pose().pushPose();
                try {
                    SocialHubIcons.scaleButton(g,bounds,motion);
                    boolean selected=page==Page.COLORS && dye==dye(line());
                    WorldMapChrome.control(g,bounds,selected,isHoveredOrFocused(),active,false);
                    rounded(g,getX()+4,getY()+5,11,10,2,MUTED);
                    rounded(g,getX()+5,getY()+6,9,8,1,0xff000000 | dye.getTextureDiffuseColor());
                    label(g,getMessage(),getX()+19,getY()+6,getWidth()-23,active?TEXT:MUTED);
                } finally { g.pose().popPose(); }
            }
        };
        control.active=idle();
        control.setTooltip(Tooltip.create(label));
        addRenderableWidget(control);
    }
    private void editDescription() {
        description=originalDescription=line().getString("Description");
        show(Page.DESCRIPTION);
    }
    private void saveDescription() {
        if (!canEditDescription || !idle() || !supported()) return;
        var nonce=UUID.randomUUID();
        submit(nonce,new PassengerEditorNetwork.DescriptionRequest(nonce,selected,originalDescription,description));
    }
    private int dwellSeconds() { return seconds.isEmpty()?0:Integer.parseInt(seconds); }
    private void cancelRefresh() {
        if (background) {
            background=false; pending=null; request=null; retryAt=0;
        }
    }
    private void send(Action action,String argument) {
        if (!idle() || !supported()) return;
        cancelRefresh();
        UUID nonce=UUID.randomUUID();
        submit(nonce,new PassengerEditorNetwork.Request(nonce,action,selected,argument,dwellSeconds()));
    }
    private void submit(UUID nonce,CustomPacketPayload payload) {
        pending=nonce; request=payload; sent=Util.getMillis(); retryAt=0; retries=0;
        PacketDistributor.sendToServer(payload);
        if(!background) for(var child:children()) if (child instanceof Button button && button.getWidth()>23) button.active=false;
    }
    @Override public void tick() {
        if (!supported()) { atlas.closeAtlasTool(); return; }
        long now=Util.getMillis();
        if (retryAt!=0 && now>=retryAt && request!=null) {
            sent=now; retryAt=0; PacketDistributor.sendToServer(request);
        }
        if (pending!=null && now-sent>5000) {
            pending=null; request=null; retryAt=0; background=false; status="timeout"; refreshed=now; rebuildWidgets();
        }
        if ((page==Page.PLATFORMS || page==Page.TRAIN) && !search.equals(renderedSearch)) {
            int cursor=stationFilter.getCursorPosition();
            boolean focused=stationFilter.isFocused();
            rebuildWidgets();
            if (focused) setFocused(stationFilter);
            stationFilter.setCursorPosition(cursor); stationFilter.setHighlightPos(cursor);
        }
        if (pending==null && supported() && status.equals("ok") && (page==Page.LINE || page==Page.SERVICE)
                && line().getBoolean("Assigned") && now-refreshed>=8000) {
            background=true;
            UUID nonce=UUID.randomUUID();
            submit(nonce,new PassengerEditorNetwork.Request(nonce,Action.LIST,selected,"",0));
        }
    }
    private void label(GuiGraphics g,Component value,int x,int y,int width,int color) {
        g.drawString(font,WorldMapChrome.fit(font,value,Math.max(1,width)),x,y,color,false);
    }
    private void paragraph(GuiGraphics g,Component value,int x,int y,int width,int maxLines,int color) {
        int i=0;
        for(var part:font.split(value,width)) {
            if(i++>=maxLines) break;
            g.drawString(font,part,x,y,color,false); y+=font.lineHeight+1;
        }
    }
    private Component heading() {
        return switch (page) {
            case DIRECTORY -> title;
            case LINE -> Component.literal(line().getString("Name"));
            case CREATE -> text("create");
            case SERVICE -> text("service");
            case SETTINGS -> text("settings");
            case COLORS -> text("color");
            case PLATFORMS -> text("next_stop",line().getList("Stops",8).size()+1);
            case TRAIN -> text("choose_train");
            case DESCRIPTION -> text("description");
            case CONFIRM -> text(confirmation==Action.DELETE?"delete":"clear");
        };
    }
    private Component serviceStatus(CompoundTag row) {
        return Component.translatable("ccec.passenger."+row.getString("Status"));
    }
    static String forecastDetail(String status) {
        return switch(status) {
            case "checking" -> "checking_detail";
            case "verified" -> "verified_detail";
            case "late" -> "late_detail";
            default -> "";
        };
    }
    private void route(GuiGraphics g,CompoundTag row) {
        int x=x(), y=y()+40, w=w();
        var stops=row.getList("Stops",8);
        var description=row.getString("Description");
        if (!description.isBlank()) { label(g,Component.literal(description),x,y,w,MUTED); y+=15; }
        label(g,text("ordered_stops",stops.size()),x,y,w,TEXT);
        y+=15;
        if (stops.isEmpty()) { paragraph(g,text("step_first"),x,y,w,2,MUTED); return; }
        int pitch=Math.min(19,(footer()-35-y)/Math.max(1,stops.size()));
        int color=0xff000000|dye(row).getTextureDiffuseColor();
        g.vLine(x+6,y+5,y+(stops.size()-1)*pitch+5,color);
        for(int i=0;i<stops.size();i++) {
            int sy=y+i*pitch;
            rounded(g,x,sy-1,14,13,4,EDGE);
            rounded(g,x+1,sy,12,11,3,INSET);
            String number=Integer.toString(i+1);
            g.drawString(font,number,x+(14-font.width(number))/2,sy+1,TEXT,false);
            label(g,Component.literal(stops.getString(i)),x+22,sy+1,w-22,TEXT);
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        int x=x(), y=y(), w=w();
        var row=line();
        hover.beginFrame();
        g.fill(0,0,width,height,0x99090E10);
        WorldMapChrome.panel(g,x-10,y,panelWidth(),panelHeight());
        boolean lineBound=page!=Page.DIRECTORY && page!=Page.CREATE;
        int color=lineBound?0xff000000 | dye(row).getTextureDiffuseColor():ACCENT;
        int iconX=x+(page==Page.LINE?Math.min(94,font.width(text("all_lines"))+32):23)+7;
        (page==Page.PLATFORMS?AtlasPlaceIcon.STATION:AtlasPlaceIcon.TRAIN).draw(g,iconX,y+9,16);
        label(g,heading(),iconX+22,y+14,x+w-iconX-22-(page==Page.LINE?54:page==Page.SERVICE||page==Page.DIRECTORY?27:0),TEXT);
        g.hLine(x,x+w-1,y+32,EDGE);
        g.hLine(x,x+30,y+32,color);
        switch(page) {
            case DIRECTORY -> {
                label(g,text("line_count",lines.size()),x,y+45,w-64,MUTED);
                if (lines.isEmpty() && idle()) paragraph(g,text("empty_guide"),x,y+76,w,3,TEXT);
            }
            case CREATE -> label(g,text("name"),x,y+54,w,TEXT);
            case LINE -> route(g,row);
            case SERVICE -> {
                Component train=row.hasUUID("Train")?text("train_name",row.getString("TrainName"))
                        :row.getBoolean("Assigned")?text("missing_train"):row.getList("Stops",8).isEmpty()?text("step_first")
                        :text("dock_at",row.getList("Stops",8).getString(0));
                label(g,train,x,y+47,w,TEXT);
                if(row.getBoolean("Assigned")) {
                    label(g,serviceStatus(row),x,y+73,w,ACCENT);
                    String key=forecastDetail(row.getString("Status"));
                    if(!key.isEmpty()) paragraph(g,text(key),x,y+90,w,6,MUTED);
                } else {
                    label(g,text("dwell_label"),x,y+97,w,TEXT);
                    label(g,text("dwell_range"),x+48,y+116,w-48,MUTED);
                    paragraph(g,text(row.getList("Stops",8).size()<2?"step_second":"before_launch"),x,y+142,w,4,MUTED);
                }
            }
            case DESCRIPTION -> {
                label(g,text("description_label"),x,y+51,w,TEXT);
                paragraph(g,Component.literal(description),x,y+97,w,4,MUTED);
            }
            case PLATFORMS, TRAIN -> {
                if (noMatches) paragraph(g,text(page==Page.TRAIN?"no_trains":"no_matches"),x,y+74,w,5,MUTED);
            }
            case CONFIRM -> paragraph(g,text(confirmation==Action.DELETE?"confirm_delete":"confirm_clear",row.getString("Name")),x,y+64,w,7,TEXT);
            default -> {}
        }
        Component message=null;
        if (pending!=null && !background) message=text("loading");
        else if (!status.equals("ok") && !(background && status.equals("rate"))) message=switch(status) {
            case "seconds","timeout","description_saved" -> text(status);
            default -> Component.translatable("ccec.passenger."+status);
        };
        else if (page==Page.LINE && row.getBoolean("Assigned")) message=serviceStatus(row);
        if(message!=null) paragraph(g,message,x,footer()-25,w,2,ACCENT);
        super.render(g,mx,my,partial);
        float fade=1-CommunityFx.ease((Util.getMillis()-transition)/160d);
        if(fade>0) g.fill(x,y+34,x+w,footer()+BUTTON_HEIGHT,((int)(70*fade)<<24)|(SURFACE&0xffffff));
        // The atlas renders its embedded tools directly, outside Screen.renderWithTooltip.
        for (var child : children()) {
            if (child instanceof Button button && button.isMouseOver(mx,my)
                    && (button.getWidth()<32 || font.width(button.getMessage())>button.getWidth()-29)) {
                g.renderTooltip(font,button.getMessage(),mx,my);
                break;
            }
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if (idle() && dy!=0 && x>=x() && x<x()+w() && y>y()+32 && y<footer()
                && (page==Page.DIRECTORY || page==Page.PLATFORMS || page==Page.TRAIN)) {
            int size=page==Page.DIRECTORY?lines.size():matches().size();
            int next=Math.clamp(listPage+(dy<0?1:-1),0,pageCount(size)-1);
            if(next!=listPage){listPage=next;rebuildWidgets();return true;}
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public void removed() {
        if(opened.get()==this) opened.clear();
        pending=null; request=null; retryAt=0;
    }
    @Override public void onClose() {
        if(!idle() || page==Page.DIRECTORY) { atlas.closeAtlasTool(); return; }
        switch(page) {
            case CREATE, LINE -> show(Page.DIRECTORY);
            case SERVICE, PLATFORMS, SETTINGS -> show(Page.LINE);
            case TRAIN -> show(Page.SERVICE);
            case COLORS, DESCRIPTION, CONFIRM -> show(Page.SETTINGS);
            default -> atlas.closeAtlasTool();
        }
    }
}
