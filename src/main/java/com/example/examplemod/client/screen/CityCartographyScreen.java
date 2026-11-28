package com.example.examplemod.client.screen;

import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.citymap.CityMapFeature.Kind;
import com.example.examplemod.citymap.CityMapFeature.Shape;
import com.example.examplemod.citymap.CityMapNetwork;
import com.example.examplemod.citymap.CityMapNetwork.Action;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.map.CityCartographyLayer;
import com.example.examplemod.client.map.CityMapFeatures;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.DispatchPalette;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import com.example.examplemod.passenger.PassengerService;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** A city's survey on the existing atlas camera. Only an acknowledged server revision becomes the saved draft. */
public final class CityCartographyScreen extends Screen {
    private enum Page { CHOOSE, DRAW, DETAILS, ENTRANCES, STREETS, ENDPOINT, PLATFORMS, CITIES, CONFIRM_CITY }
    private static final UUID ZERO = new UUID(0, 0);
    private static CityCartographyScreen open;
    private final PolishedWorldMapScreen atlas;
    private final CityMapPanel map;
    private final UUID city, id;
    private final List<BlockPos> vertices = new ArrayList<>(), entrances = new ArrayList<>();
    private record Edit(Shape shape, List<BlockPos> points, int selected) {}
    private final ArrayDeque<Edit> drawingHistory = new ArrayDeque<>(), entranceHistory = new ArrayDeque<>();
    private CityMapFeature saved;
    private Button newObject;
    private List<CityMapFeature> catalog = List.of();
    private List<PassengerService.Stop> platforms = List.of();
    private UUID street, platformChoice;
    private Action platformAction;
    private CityMapFeature.Endpoint start, end;
    private boolean linkStart, rectangle;
    private List<BlockPos> beforeAdjust;
    private boolean dirtyBeforeAdjust;
    private int scroll, maxScroll, contentY;
    private final List<Caption> captions = new ArrayList<>();
    private record Caption(int y, Component text) {}
    private Kind kind = Kind.BUILDING;
    private CityMapFeature.Icon icon = CityMapFeature.Icon.HOME;
    private final java.util.Map<Button, CityMapFeature.Icon> iconButtons = new java.util.LinkedHashMap<>();
    private Shape shape = Shape.POINT;
    private String name = "", number = "", description = "", elevation, lineHeight = "";
    private Page page = Page.CHOOSE;
    private Page returnPage;
    private int returnScroll;
    private String cityFilter = "";
    private UUID nextCity;
    private List<CityPublicProfile> choices = List.of();
    private EditBox citySearch;
    private int split, selected = -1;
    private boolean initialized, allowed, dirty, confirmDelete, confirmClose, needsReload, stationRegistered;
    private BlockPos dragPreview;
    private boolean dragging, panning;
    private UUID pending;
    private Action operation;
    private long deadline;
    private Component notice = Component.empty();

    public CityCartographyScreen(PolishedWorldMapScreen atlas, CityMapPanel map, UUID city, CityMapFeature initial) {
        super(label("title"));
        this.atlas = atlas;
        this.map = map;
        this.city = city;
        id = initial == null ? UUID.randomUUID() : initial.id();
        if (city == null) page = Page.CITIES;
        if (initial != null) { load(initial); page = Page.DETAILS; }
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("gui.createcityexpansioncore.city_editor." + key, args);
    }

    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }

    private void load(CityMapFeature value) {
        saved = value;
        kind = value.kind();
        icon = value.icon();
        shape = value.shape();
        name = value.name();
        number = value.houseNumber();
        description = value.description();
        street = value.links().street();
        start = value.links().start();
        end = value.links().end();
        rectangle = false;
        vertices.clear();
        vertices.addAll(value.vertices());
        entrances.clear();
        entrances.addAll(value.entrances());
        drawingHistory.clear();
        entranceHistory.clear();
        selected = -1;
        dirty = false;
        confirmDelete = confirmClose = false;
    }

    private void changed() {
        dirty = true;
        stationRegistered = false;
        if (newObject != null) newObject.active = false;
        notice = Component.empty();
        confirmDelete = confirmClose = false;
    }

    private boolean editable() { return allowed && pending == null && !needsReload && atlas.canDraw(city); }

    @Override protected void init() {
        if (!initialized) {
            initialized = true;
            open = this;
            elevation = minecraft.player == null ? "" : Integer.toString(minecraft.player.getBlockY());
            if (city != null) send(Action.LIST, null);
        }
        split = width - Math.clamp(width * 2 / 5, 164, 254);
        newObject = null;
        captions.clear();
        iconButtons.clear();
        int x = split + 5, w = width - x - 5;
        citySearch = null;
        if (page == Page.CITIES) choices = atlas.drawingCities();
        contentY = bodyTop();
        if (showSwitch())
            addButton(x,21,w,label("switch_city"),this::pickCity,pending==null,false,false);
        switch (page) {
            case CITIES -> {
                if (choices.size()>7) {
                    citySearch=AtlasFormStyle.field(font,x,21,w,18,label("find_city"));
                    citySearch.setHint(label("find_city"));
                    citySearch.setMaxLength(64);
                    citySearch.setValue(cityFilter);
                    citySearch.setResponder(this::filterCities);
                    addRenderableWidget(citySearch);
                }
                String query=cityFilter.strip().toLowerCase(Locale.ROOT);
                boolean found=atlas.canDraw(CityMapNetwork.OUTSIDE) && label("outside").getString().toLowerCase(Locale.ROOT).contains(query);
                if (found) control(label("outside"), () -> chooseCity(CityMapNetwork.OUTSIDE), true);
                for (var profile:choices) {
                    if (!profile.name().toLowerCase(Locale.ROOT).contains(query)) continue;
                    found=true;
                    int y=row(21);
                    if (visible(y,18)) addButton(x,y,w,Component.literal(profile.name()),
                            ()->chooseCity(profile.id()),true,profile.id().equals(city),false);
                }
                if (!found) hint(label("no_matching_city"));
            }
            case CONFIRM_CITY -> {
                var target=CityPublicDirectory.profile(nextCity);
                hint(label("switch_draft",target==null?label("outside"):target.name()));
                control(label("discard_switch"),()->atlas.openDrawingCity(nextCity,null),atlas.canDraw(nextCity));
            }
            case CHOOSE -> {
                caption(label("choose_kind"));
                for (Kind option : List.of(Kind.BUILDING, Kind.STATION, Kind.STREET, Kind.PATH))
                    control(label("kind." + key(option)), () -> begin(option), editable());
                if (!catalog.isEmpty()) {
                    caption(label("places"));
                    for (var f : catalog) control(Component.literal(featureName(f)), () -> open(f), pending == null);
                }
            }
            case DRAW -> {
                caption(label("kind." + key(kind)));
                if (!linear() && vertices.isEmpty())
                    pair(label("rect"), () -> { rectangle = true; shape = Shape.POLYGON; rebuildWidgets(); }, !rectangle,
                            label("corners"), () -> { rectangle = false; shape = Shape.POLYGON; rebuildWidgets(); }, rectangle);
                hint(label(rectangle ? vertices.isEmpty() ? "first_corner" : "second_corner" : linear() ? "trace_line" : "trace_outline"));
                if (linear()) editField("line_elevation", 6, lineHeight, value -> lineHeight = value);
                caption(label("vertices_count", vertices.size()));
                pointButtons();
                if (selected >= 0 && selected < vertices.size()) pointCaption(vertices.get(selected));
            }
            case DETAILS -> {
                caption(label("kind." + key(kind)));
                editField(kind == Kind.BUILDING ? "optional_name" : "name", CityMapFeature.NAME, name, v -> { name = v; changed(); });
                if (kind == Kind.BUILDING) {
                    if (CityMapFeatures.supportsIcons()) iconPicker();
                    if (modern()) control(label("street_choice", street == null ? label("not_selected") : linkedName(street)),
                            () -> show(Page.STREETS), editable());
                    editField("number", CityMapFeature.HOUSE_NUMBER, number, v -> { number = v; changed(); });
                }
                control(label("adjust"), () -> {
                    beforeAdjust = List.copyOf(vertices);
                    dirtyBeforeAdjust = dirty;
                    rectangle = false;
                    drawingHistory.clear();
                    show(Page.DRAW);
                }, editable());
                if (!linear() || !entrances.isEmpty())
                    control(label("entrance_action", entrances.size()), () -> show(Page.ENTRANCES), editable());
                if (linear() && modern()) {
                    control(endpointLabel(true), () -> pickEnd(true), editable());
                    control(endpointLabel(false), () -> pickEnd(false), editable());
                }
                if (kind == Kind.STATION) stationControls();
                editField("description", CityMapFeature.DESCRIPTION, description, v -> { description = v; changed(); });
                if (saved != null) {
                    newObject = control(label("new"), () -> open(null), editable() && !dirty);
                    control(label(confirmDelete ? "confirm_delete" : "delete"), () -> {
                        if (confirmDelete) submit(Action.DELETE);
                        else { confirmDelete = true; rebuildWidgets(); }
                    }, editable());
                }
            }
            case ENTRANCES -> {
                hint(label("pick_entrance"));
                editField("elevation", 6, elevation, value -> elevation = value);
                control(label("at_feet"), this::captureFeet, editable());
                caption(label("entrances_count", entrances.size()));
                for (int i = 0; i < entrances.size(); i++) {
                    int index = i;
                    BlockPos p = entrances.get(i);
                    control(label("coords", i + 1, p.getX(), p.getY(), p.getZ()), () -> selectPoint(index), true);
                }
                pointButtons();
            }
            case STREETS -> {
                hint(label("pick_street"));
                control(label("no_street"), () -> chooseStreet(null), editable());
                for (var f : catalog) if (f.kind() == Kind.STREET)
                    control(Component.literal(f.name()), () -> chooseStreet(f.id()), editable());
            }
            case ENDPOINT -> {
                caption(label(linkStart ? "start" : "end"));
                hint(label("pick_endpoint"));
                control(label("detach"), () -> chooseEndpoint(null), editable());
            }
            case PLATFORMS -> platformPicker();
        }
        maxScroll = Math.max(0, contentY - bodyBottom());
        if (scroll > maxScroll) { scroll = maxScroll; rebuildWidgets(); return; }
        if (maxScroll > 0) {
            addButton(width - 45, 2, 19, Component.literal("▲"), () -> scrollBy(-58), scroll > 0, false, false);
            addButton(width - 24, 2, 19, Component.literal("▼"), () -> scrollBy(58), scroll < maxScroll, false, false);
        }
        if (needsReload && !choosingCity()) addButton(x, height - 43, w, label("reload"), () -> { send(Action.LIST, null); rebuildWidgets(); }, pending == null, false, false);
        int half = (w - 2) / 2;
        if (page == Page.DETAILS)
            addButton(x, height - 22, half, label("save"), () -> submit(Action.SAVE), editable(), false, true);
        if (page == Page.DRAW)
            addButton(x, height - 22, half, label("done"), () -> { beforeAdjust=null; show(Page.DETAILS); }, editable() && complete(), false, true);
        if (page == Page.ENTRANCES)
            addButton(x, height - 22, half, label("done"), () -> show(Page.DETAILS), editable(), false, true);
        addButton(x + half + 2, height - 22, w - half - 2, label(confirmClose ? "discard" : "back"), this::onClose, true, false, false);
    }

    private void begin(Kind option) {
        kind = option;
        rectangle = !linear();
        shape = rectangle ? Shape.POLYGON : Shape.LINE;
        show(Page.DRAW);
    }

    private void open(CityMapFeature value) { atlas.openAtlasTool(new CityCartographyScreen(atlas, map, city, value)); }

    private boolean choosingCity() { return page==Page.CITIES || page==Page.CONFIRM_CITY; }
    private boolean showSwitch() { return !choosingCity() && minecraft.player!=null && minecraft.player.hasPermissions(2); }
    private int bodyTop() { return showSwitch() || page==Page.CITIES && choices.size()>7 ? 43 : 23; }
    private void pickCity() {
        if (!showSwitch() || pending!=null) return;
        returnPage=page;
        returnScroll=scroll;
        scroll=0;
        cityFilter="";
        dragging=panning=confirmDelete=confirmClose=false;
        dragPreview=null;
        page=Page.CITIES;
        rebuildWidgets();
    }
    private void filterCities(String query) {
        int position=citySearch.getCursorPosition();
        cityFilter=query;
        scroll=0;
        rebuildWidgets();
        if (citySearch!=null) {
            setFocused(citySearch);
            citySearch.setCursorPosition(Math.min(position,query.length()));
            citySearch.setHighlightPos(citySearch.getCursorPosition());
        }
    }
    private void chooseCity(UUID target) {
        if (!atlas.canDraw(target)) { rebuildWidgets(); return; }
        if (target.equals(city)) { onClose(); return; }
        if (dirty) {
            nextCity=target;
            page=Page.CONFIRM_CITY;
            scroll=0;
            rebuildWidgets();
        } else atlas.openDrawingCity(target,null);
    }

    private void show(Page next) {
        page = next;
        scroll = 0;
        selected = -1;
        dragging = panning = confirmDelete = confirmClose = false;
        dragPreview = null;
        rebuildWidgets();
    }

    private boolean linear() { return kind == Kind.STREET || kind == Kind.PATH; }
    private boolean complete() {
        return switch (shape) {
            case POINT -> vertices.size() == 1;
            case LINE -> vertices.size() >= 2;
            case POLYGON -> vertices.size() >= 3;
        };
    }

    private int bodyBottom() { return height - (needsReload ? 60 : 40); }
    private boolean visible(int y, int h) { return y >= bodyTop() && y + h <= bodyBottom(); }
    private int row(int h) { int y = contentY - scroll; contentY += h; return y; }
    private void scrollBy(int dy) { scroll = Math.clamp(scroll + dy, 0, maxScroll); rebuildWidgets(); }
    private void caption(Component text) {
        int y = row(13);
        if (visible(y, font.lineHeight)) captions.add(new Caption(y, text));
    }
    private void hint(Component text) {
        for (var line : font.getSplitter().splitLines(text,width-split-10,net.minecraft.network.chat.Style.EMPTY))
            caption(Component.literal(line.getString()));
    }
    private Button control(Component text, Runnable action, boolean active) {
        int y = row(21);
        return visible(y,18) ? addButton(split+5,y,width-split-10,text,action,active,false,false) : null;
    }
    private void iconPicker() {
        caption(label("icon"));
        int columns = Math.max(1, (width - split - 10) / 24);
        int index = 0, y = 0;
        for (var choice : CityMapFeature.Icon.values()) {
            int column = index++ % columns;
            if (column == 0) y = row(24);
            if (!visible(y, 22)) continue;
            var title = label("icon." + key(choice));
            Button button = addButton(split + 5 + column * 24, y, 22, Component.empty(), () -> {
                icon = choice;
                changed();
                rebuildWidgets();
            }, editable(), choice == icon, false);
            button.setTooltip(Tooltip.create(title));
            iconButtons.put(button, choice);
        }
    }

    private void pair(Component left, Runnable a, boolean enabledA, Component right, Runnable b, boolean enabledB) {
        int y = row(21), x = split + 5, w = width - split - 10, half = (w-2)/2;
        if (!visible(y,18)) return;
        addButton(x,y,half,left,a,editable() && enabledA,false,false);
        addButton(x+half+2,y,w-half-2,right,b,editable() && enabledB,false,false);
    }
    private void editField(String key, int limit, String value, Consumer<String> change) {
        caption(label(key));
        int y = row(20);
        if (visible(y,17)) field(split+5,y,width-split-10,key,limit,value,change);
    }

    private boolean modern() {
        var link = minecraft.getConnection();
        return link != null && link.hasChannel(CityMapNetwork.RequestV2.TYPE);
    }
    private String featureName(CityMapFeature f) { return f.label(this::lookup); }
    private CityMapFeature lookup(UUID target) {
        for (var f : catalog) if (f.id().equals(target)) return f;
        return null;
    }
    private Component linkedName(UUID target) {
        var f = lookup(target);
        return f == null ? label("not_selected") : Component.literal(featureName(f));
    }
    private void chooseStreet(UUID value) { street = value; changed(); show(Page.DETAILS); }
    private Component endpointLabel(boolean first) {
        var endpoint = first ? aligned(start,true) : aligned(end,false);
        return label(first ? "from_link" : "to_link", endpoint == null ? label("not_selected") : linkedName(endpoint.feature()));
    }
    private void pickEnd(boolean first) { linkStart = first; show(Page.ENDPOINT); }
    private void chooseEndpoint(CityMapFeature.Endpoint target) {
        if (vertices.isEmpty()) return;
        if (target != null) vertices.set(linkStart ? 0 : vertices.size()-1, target.point());
        if (linkStart) start = target; else end = target;
        changed();
        show(Page.DETAILS);
    }
    private void pickEndpointAt(double mx, double my) {
        CityMapFeature.Endpoint best = null;
        double distance = 8;
        for (var f : catalog) {
            if (f.id().equals(id)) continue;
            for (var p : targets(f)) {
                double d = Math.hypot(sx(p)-mx,sy(p)-my);
                if (d < distance) { distance = d; best = new CityMapFeature.Endpoint(f.id(), p); }
            }
        }
        if (best != null) chooseEndpoint(best); else notice = label("pick_endpoint");
    }
    private static List<BlockPos> targets(CityMapFeature f) {
        return f.kind() == Kind.PATH || f.kind() == Kind.STREET ? f.vertices() : f.entrances();
    }
    private void selectPoint(int index) {
        selected = index;
        BlockPos p = points().get(index);
        if (page == Page.ENTRANCES) elevation = Integer.toString(p.getY());
        map.restoreView(p.getX()+.5,p.getZ()+.5,map.zoom());
        rebuildWidgets();
    }
    private void pointCaption(BlockPos p) { caption(label("coords",selected+1,p.getX(),p.getY(),p.getZ())); }

    private void stationControls() {
        var linked = platforms.stream().filter(p -> id.equals(p.feature())).toList();
        for (var p : linked) caption(label("platform_name", p.name()));
        if (minecraft.player == null || !minecraft.player.hasPermissions(2)) return;
        if (modern()) {
            if (platformChoice != null) {
                var target = platforms.stream().filter(p -> platformChoice.equals(p.id())).findFirst();
                target.ifPresent(p -> caption(label(platformAction == Action.UNBIND_PLATFORM ? "detach_platform" : "attach_platform",p.name())));
            }
            control(label("platforms",linked.size()), () -> show(Page.PLATFORMS), editable());
        } else {
            control(label("register"), () -> submit(Action.REGISTER_STATION), editable() && saved != null && !dirty && !entrances.isEmpty());
        }
        if ((stationRegistered || !linked.isEmpty()) && PassengerLinesScreen.supported())
            control(PassengerLinesScreen.text("title"), () -> atlas.openAtlasTool(new PassengerLinesScreen(atlas)), editable() && !dirty);
    }

    private boolean selectable(PassengerService.Stop stop) {
        return stop.dimension().equals(map.dimensionId()) && (stop.feature()==null || id.equals(stop.feature()));
    }
    private void platformPicker() {
        hint(label("platform_pick"));
        boolean found = false;
        for (var p : platforms) if (selectable(p)) {
            found = true;
            control(label(p.feature()==null ? "attach_platform" : "detach_platform",p.name()), () -> selectPlatform(p), editable());
        }
        if (!found) hint(label("no_platform"));
        if (platformChoice!=null) control(label("cancel_platform"), () -> {platformChoice=null;platformAction=null;show(Page.DETAILS);},editable());
    }
    private void selectPlatform(PassengerService.Stop stop) {
        platformChoice = stop.id();
        platformAction = stop.feature()==null ? Action.BIND_PLATFORM : Action.UNBIND_PLATFORM;
        changed();
        notice = label(platformAction == Action.BIND_PLATFORM ? "attach_platform" : "detach_platform",stop.name());
        show(Page.DETAILS);
    }
    private void drawPlatformChoices(GuiGraphics g) {
        for (var stop : platforms) if (selectable(stop)) {
            int x=sx(stop.stationPos()), y=sy(stop.stationPos());
            com.example.examplemod.client.map.AtlasPlaceIcon.STATION.draw(g,x-7,y-7,14);
        }
    }
    private void choosePlatformAt(double x,double y) {
        PassengerService.Stop best=null;
        double nearest=9;
        for (var p:platforms) if (selectable(p)) {
            double d=Math.hypot(sx(p.stationPos())-x,sy(p.stationPos())-y);
            if (d<nearest) { best=p;nearest=d; }
        }
        if (best!=null) selectPlatform(best);
    }

    private Button addButton(int x, int y, int w, Component text, Runnable run, boolean active, boolean selectedTab, boolean primary) {
        var button = addRenderableWidget(AtlasFormStyle.button(x, y, w, 18, text, run, selectedTab, primary));
        button.active = active;
        if (font.width(text) > w - 12) button.setTooltip(Tooltip.create(text));
        return button;
    }

    private void field(int x, int y, int w, String key, int max, String value, Consumer<String> onChange) {
        var box = AtlasFormStyle.field(font, x, y, w, 17, label(key));
        box.setMaxLength(max);
        box.setFilter(s -> s.chars().noneMatch(c -> Character.isISOControl(c) || c == 0xA7));
        box.setValue(value);
        box.setEditable(editable());
        box.setResponder(onChange);
        addRenderableWidget(box);
    }

    private List<BlockPos> points() { return page == Page.ENTRANCES ? entrances : vertices; }

    private ArrayDeque<Edit> history() { return page == Page.ENTRANCES ? entranceHistory : drawingHistory; }

    private void remember() {
        var edits = history();
        if (edits.size() == 64) edits.removeLast();
        edits.addFirst(new Edit(shape, List.copyOf(points()), selected));
    }

    private void undo() {
        if (!editable() || history().isEmpty()) return;
        var edit = history().removeFirst();
        if (page == Page.DRAW) shape = edit.shape();
        points().clear();
        points().addAll(edit.points());
        selected = edit.selected();
        dragging = false;
        dragPreview = null;
        changed();
        rebuildWidgets();
    }

    private void pointButtons() {
        pair(label("remove"), this::removePoint, selected >= 0, label("undo"), this::undo, !history().isEmpty());
    }

    private void removePoint() {
        if (selected < 0 || selected >= points().size()) return;
        remember();
        points().remove(selected);
        selected = Math.min(selected, points().size() - 1);
        changed();
        rebuildWidgets();
    }

    private void addPoint(BlockPos point) {
        if (point == null) return;
        var list = points();
        if (page == Page.DRAW && rectangle && vertices.size() == 1) {
            var a = vertices.getFirst();
            if (a.getX() == point.getX() || a.getZ() == point.getZ()) { notice = label("second_corner"); return; }
            remember();
            vertices.add(new BlockPos(point.getX(), a.getY(), a.getZ()));
            vertices.add(point);
            vertices.add(new BlockPos(a.getX(), point.getY(), point.getZ()));
            rectangle = false;
            changed();
            show(Page.DETAILS);
            return;
        }
        int duplicate = list.indexOf(point);
        if (duplicate >= 0) {
            selected = duplicate;
        } else if (page == Page.DRAW && shape == Shape.POINT && list.size() == 1) {
            remember();
            list.set(0, point);
            selected = 0;
            changed();
        } else if (list.size() < (page == Page.ENTRANCES ? CityMapFeature.MAX_ENTRANCES : CityMapFeature.MAX_VERTICES)) {
            remember();
            list.add(point);
            selected = list.size() - 1;
            changed();
        } else { notice = label("status.limit"); return; }
        if (page == Page.ENTRANCES) show(Page.DETAILS);
        else rebuildWidgets();
    }

    private boolean playerHere() {
        return minecraft.player != null && minecraft.player.level().dimension().location().equals(map.dimensionId());
    }

    private void captureFeet() {
        if (!playerHere()) { notice = label("status.dimension"); return; }
        var feet = minecraft.player.blockPosition();
        elevation = Integer.toString(feet.getY());
        addPoint(feet);
    }

    private BlockPos mapPoint(double x, double y) {
        int fallbackY = minecraft.player == null ? 0 : minecraft.player.getBlockY();
        var projected = map.placeIconAt(x, y, fallbackY);
        if (projected == null) return null;
        if (page == Page.DRAW && linear() && lineHeight.isBlank()) {
            var level = minecraft.level;
            if (level != null && level.dimension().location().equals(map.dimensionId()) && level.hasChunkAt(projected)) {
                int floor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        projected.getX(),projected.getZ());
                return new BlockPos(projected.getX(),floor,projected.getZ());
            }
        }
        if (page != Page.ENTRANCES && (page != Page.DRAW || !linear() || lineHeight.isBlank())) return projected;
        try {
            return new BlockPos(projected.getX(), Integer.parseInt(page == Page.ENTRANCES ? elevation : lineHeight), projected.getZ());
        } catch (NumberFormatException invalid) {
            notice = label("status.elevation");
            return null;
        }
    }

    private void submit(Action action) {
        if (!editable()) return;
        CityMapFeature value = null;
        if (action == Action.SAVE) {
            if (name.isBlank() && !(kind == Kind.BUILDING && street != null && !number.isBlank())) {
                notice = label(kind == Kind.BUILDING && modern() ? "name_or_address" : "status.name");
                show(Page.DETAILS);
                return;
            }
            boolean complete = switch (shape) {
                case POINT -> vertices.size() == 1;
                case LINE -> vertices.size() >= 2;
                case POLYGON -> vertices.size() >= 3;
            };
            if (!complete) {
                page = Page.DRAW;
                selected = -1;
                notice = label("status." + key(shape));
                rebuildWidgets();
                return;
            }
            try {
                value = new CityMapFeature(id, city, saved == null ? 0 : saved.revision(), map.dimensionId(), kind, shape,
                        name.strip(), kind == Kind.BUILDING ? number.strip() : "", description.strip(), List.copyOf(vertices), List.copyOf(entrances),
                        new CityMapFeature.Links(street, aligned(start,true), aligned(end,false)), icon);
            } catch (IllegalArgumentException invalid) {
                notice = label("status.invalid");
                return;
            }
        }
        send(action, value);
        rebuildWidgets();
    }

    private CityMapFeature.Endpoint aligned(CityMapFeature.Endpoint endpoint, boolean first) {
        return endpoint != null && !vertices.isEmpty() && endpoint.point().equals(first ? vertices.getFirst() : vertices.getLast()) ? endpoint : null;
    }

    private void send(Action action, CityMapFeature value) {
        var dim = map.dimensionId();
        var view = CityPublicDirectory.viewId();
        var world = CityPublicDirectory.worldId(dim);
        if (pending != null) return;
        if (!CityMapFeatures.supported() || view == null || world == null) {
            needsReload = true;
            notice = label("status.unavailable");
            return;
        }
        operation = action;
        pending = UUID.randomUUID();
        deadline = Util.getMillis() + 8000;
        notice = label("status.waiting");
        var request = new CityMapNetwork.Request(pending, view, world, dim, city, action,
                action == Action.LIST ? ZERO : id, action == Action.LIST || saved == null ? 0 : saved.revision(), value,
                action == Action.BIND_PLATFORM || action == Action.UNBIND_PLATFORM ? platformChoice : null);
        PacketDistributor.sendToServer(CityMapFeatures.supportsIcons() ? new CityMapNetwork.RequestV3(request) : CityMapNetwork.outbound(request, modern()));
    }

    public static void receive(CityMapNetwork.Reply reply) {
        var editor = open;
        if (editor == null || !editor.atlas.ownsAtlasTool(editor) || Minecraft.getInstance().screen != editor.atlas
                || !reply.nonce().equals(editor.pending) || !reply.city().equals(editor.city)) return;
        editor.accept(reply);
    }

    private void accept(CityMapNetwork.Reply reply) {
        pending = null;
        allowed = reply.canEdit();
        if (!"ok".equals(reply.status())) {
            String status = reply.status();
            if (operation == Action.BIND_PLATFORM || operation == Action.UNBIND_PLATFORM) dirty = true;
            needsReload = operation == Action.LIST || status.equals("stale") || status.equals("unavailable") || !allowed;
            notice = switch (status) {
                case "in_use" -> operation == Action.SAVE || operation == Action.DELETE ? label("link_in_use") : Component.translatable("ccec.passenger.in_use");
                case "denied" -> label("status.permission");
                case "platform" -> Component.translatable("ccec.passenger.platform");
                default -> label("status." + status);
            };
        } else if (operation == Action.DELETE) {
            map.clearSelection();
            atlas.closeAtlasTool();
            return;
        } else {
            catalog = reply.features().stream().filter(f -> city.equals(f.city()) && map.dimensionId().equals(f.dimension()))
                    .sorted(Comparator.comparing(CityMapFeature::name, String.CASE_INSENSITIVE_ORDER)).toList();
            platforms = reply.platforms();
            CityMapFeature current = catalog.stream().filter(f -> id.equals(f.id()) && city.equals(f.city())
                    && map.dimensionId().equals(f.dimension())).findFirst().orElse(null);
            needsReload = false;
            stationRegistered = operation == Action.REGISTER_STATION || operation == Action.BIND_PLATFORM;
            if (current != null) { load(current); page = Page.DETAILS; scroll = 0; }
            else if (saved != null || operation == Action.SAVE) {
                allowed = false;
                needsReload = true;
                notice = label("status.missing");
                rebuildWidgets();
                return;
            }
            if (operation == Action.SAVE && platformChoice != null && platformAction != null && allowed) {
                send(platformAction,null);
                rebuildWidgets();
                return;
            }
            platformAction = null;
            platformChoice = null;
            notice = !allowed ? label("status.permission") : switch (operation) {
                case REGISTER_STATION, BIND_PLATFORM -> label("status.registered");
                case UNBIND_PLATFORM -> label("platform_detached");
                case SAVE -> label("status.saved");
                default -> Component.empty();
            };
        }
        rebuildWidgets();
    }

    @Override public void tick() {
        if (choosingCity() && (minecraft.player==null || !minecraft.player.hasPermissions(2))) {
            if (returnPage!=null && atlas.canDraw(city)) onClose();
            else atlas.closeAtlasTool();
            return;
        }
        if (pending != null && Util.getMillis() >= deadline) {
            pending = null;
            needsReload = true;
            notice = label("status.timeout");
            rebuildWidgets();
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0xFF20272C);
        map.setChromeAvoidRight(split);
        map.setProjectFocus(false, false);
        map.setPrecisionInput(true);
        map.setVisualOverlay(null);
        map.render(g, 3, 3, split - 6, height - 6, -10000, -10000);
        g.enableScissor(3, 3, split - 3, height - 3);
        try {
            List<BlockPos> geometry = vertices, doors = entrances;
            if (dragging && dragPreview != null && selected >= 0) {
                var preview = new ArrayList<>(points());
                preview.set(selected, dragPreview);
                if (page == Page.DRAW) geometry = preview;
                else doors = preview;
            }
            if (page != Page.CHOOSE && !choosingCity()) CityCartographyLayer.drawDraft(g, map, kind, shape, geometry, doors);
            if (rectangle && page == Page.DRAW && geometry.size()==1 && canvas(mx,my)) {
                var corner = geometry.getFirst();
                int ax=sx(corner), ay=sy(corner);
                g.renderOutline(Math.min(ax,mx), Math.min(ay,my), Math.abs(ax-mx)+1, Math.abs(ay-my)+1, 0xFFFFDC88);
            }
            if (page == Page.ENDPOINT) {
                for (var f : catalog) if (!f.id().equals(id))
                    for (var p : targets(f)) g.renderOutline(sx(p)-3,sy(p)-3,7,7,0xFF82EACC);
            }
            if (page == Page.PLATFORMS) drawPlatformChoices(g);
            if ((page == Page.DRAW || page == Page.ENTRANCES) && selected >= 0 && selected < points().size()) {
                BlockPos p = dragging && dragPreview != null ? dragPreview : points().get(selected);
                g.renderOutline(sx(p) - 5, sy(p) - 5, 11, 11, 0xFFFFFFFF);
            }
        } finally { g.disableScissor(); }
        int x = split + 5, w = width - x - 5;
        WorldMapChrome.dispatchPanel(g, split, 1, width - split - 1, height - 2);
        var cityProfile = CityPublicDirectory.profile(city);
        var heading = choosingCity() ? label("choose_city") : CityMapNetwork.OUTSIDE.equals(city) ? label("outside") : cityProfile == null ? getTitle() : label("city_title", cityProfile.name());
        g.drawString(font, WorldMapChrome.fit(font, heading, maxScroll>0?w-43:w), x, 5, DispatchPalette.ACCENT, false);
        for (var c : captions)
            g.drawString(font, WorldMapChrome.fit(font,c.text(),w),x,c.y(),DispatchPalette.TEXT,false);
        int statusY = height - (needsReload ? 55 : 35);
        if (!choosingCity() && !notice.getString().isEmpty()) {
            g.drawString(font, WorldMapChrome.fit(font, notice, w), x, statusY, 0xFFEFC895, false);
        }
        super.render(g, mx, my, tick);
        iconButtons.forEach((button, choice) -> com.example.examplemod.client.map.AtlasPlaceIcon.valueOf(choice.name())
                .draw(g, button.getX()+3, button.getY()+1, 16));
        if (!choosingCity() && mx >= x && mx < x + w && my >= statusY && my < statusY + 10 && font.width(notice) > w)
            g.renderTooltip(font, font.split(notice, Math.min(width - 20, 240)), mx, my);
    }

    private int sx(BlockPos point) { return map.worldToScreenX(point.getX() + .5); }
    private int sy(BlockPos point) { return map.worldToScreenY(point.getZ() + .5); }
    private boolean canvas(double x, double y) { return x >= 3 && x < split - 3 && y >= 3 && y < height - 3; }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!canvas(x, y)) return super.mouseClicked(x, y, button);
        if (choosingCity()) return true;
        setFocused(null);
        if (button == 1 || button == 2) { panning = true; return true; }
        if (button == 0 && (page == Page.CHOOSE || page == Page.DETAILS || page == Page.STREETS)) {
            var hit = CityCartographyLayer.hit(map,x,y);
            var other = hit == null ? null : lookup(hit.id());
            if (other != null && page == Page.STREETS && other.kind() == Kind.STREET) chooseStreet(other.id());
            else if (other != null && !other.id().equals(id) && page != Page.STREETS && !dirty && pending == null) open(other);
            else panning = true;
            return true;
        }
        if (button == 0 && editable() && page == Page.ENDPOINT) { pickEndpointAt(x,y); return true; }
        if (button == 0 && editable() && page == Page.PLATFORMS) { choosePlatformAt(x,y); return true; }
        if (button != 0 || !editable()) return true;
        for (int i = points().size() - 1; i >= 0; i--) {
            var point = points().get(i);
            if (Math.hypot(sx(point) - x, sy(point) - y) > 5) continue;
            selected = i;
            dragging = true;
            dragPreview = null;
            if (page == Page.ENTRANCES) elevation = Integer.toString(point.getY());
            rebuildWidgets();
            return true;
        }
        addPoint(mapPoint(x, y));
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (panning) {
            map.restoreView(map.centerX() - dx / map.zoom(), map.centerZ() - dy / map.zoom(), map.zoom());
            return true;
        }
        if (dragging && canvas(x, y)) { dragPreview = mapPoint(x, y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (panning) { panning = false; return true; }
        if (dragging && button == 0) {
            if (editable() && canvas(x, y) && dragPreview != null && !points().contains(dragPreview)) {
                remember();
                points().set(selected, dragPreview);
                changed();
            }
            dragging = false;
            dragPreview = null;
            rebuildWidgets();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (canvas(x,y)) return map.mouseScrolled(x,y,dy);
        if (maxScroll > 0 && dy != 0) { scrollBy(dy > 0 ? -30 : 30); return true; }
        return super.mouseScrolled(x,y,dx,dy);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (page == Page.DETAILS && hasControlDown() && key == GLFW.GLFW_KEY_S) { submit(Action.SAVE); return true; }
        if ((hasControlDown() || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) && key == GLFW.GLFW_KEY_Z
                && (page == Page.DRAW || page == Page.ENTRANCES) && !(getFocused() instanceof EditBox)) {
            undo();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() {
        if (choosingCity()) {
            if (returnPage==null) atlas.closeAtlasTool();
            else {
                page=returnPage;
                scroll=returnScroll;
                returnPage=null;
                nextCity=null;
                rebuildWidgets();
            }
            return;
        }
        if (page == Page.DRAW && beforeAdjust != null) {
            vertices.clear();
            vertices.addAll(beforeAdjust);
            beforeAdjust = null;
            dirty = dirtyBeforeAdjust;
            drawingHistory.clear();
            show(Page.DETAILS);
            return;
        }
        if (page == Page.STREETS || page == Page.ENDPOINT || page == Page.PLATFORMS || page == Page.ENTRANCES
                || page == Page.DRAW && saved != null) { show(Page.DETAILS); return; }
        if (page == Page.DRAW && !dirty) { show(Page.CHOOSE); return; }
        if (dirty && !confirmClose) {
            confirmClose = true;
            notice = label("status.discard");
            rebuildWidgets();
        } else atlas.closeAtlasTool();
    }

    @Override public void removed() {
        if (open == this) open = null;
        map.setPrecisionInput(false);
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }
}
