package com.example.examplemod.client.logistics;

import com.example.examplemod.cargo.center.CenterRequest;
import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.client.map.*;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.network.RequestCityDirectoryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Existing CityMapPanel supplies projection/terrain/navigation. This is only a bounded logistics layer. */
public final class LogisticsMapOverlay {
    private static final String[] KINDS={"point","warehouse","station","train","line","shipment"};
    private static final Set<String> LAYERS=new HashSet<>(List.of(KINDS));
    record View(int x,int y,int width,int height) {
        int right(){return x+width;}
        int bottom(){return y+height;}
        boolean contains(double px,double py){return px>=x&&py>=y&&px<right()&&py<bottom();}
        View clippedTo(View clip) {
            int left=Math.max(x,clip.x), top=Math.max(y,clip.y);
            return new View(left,top,Math.max(0,Math.min(right(),clip.right())-left),
                    Math.max(0,Math.min(bottom(),clip.bottom())-top));
        }
    }
    private record MarkerDraw(CompoundTag data, String kind, int x, int y, View bounds, boolean selected, boolean node) {}
    private static final LinkedHashMap<View,CompoundTag> HITS=new LinkedHashMap<>();
    private static final List<View> panels = new ArrayList<>();
    private static View activeViewport, toolbar;
    private static View reservePanel(CityMapPanel map,View panel) {
        panels.add(panel);
        map.labels().reserve(panel.x,panel.y,panel.width,panel.height);
        return panel;
    }

    private static View topPanel(double x,double y) {
        for (var panel : panels.reversed()) if (panel.contains(x,y)) return panel;
        return null;
    }
    private static CompoundTag state=new CompoundTag(),fullState=new CompoundTag(),selection,focusLocation,route,reviewed;
    private static UUID focus,pending;
    private static UUID inspection;
    private static boolean inspectionDirty;
    private static int inspectionOffset;
    private static CompoundTag lastInspection;
    private static Tag focusedRailSource;
    private static final RailMesh.Cache focusedRailCache=new RailMesh.Cache();
    private static RailMesh focusedRailMesh=RailMesh.EMPTY;
    private static long lastInspectionSecond=-1;
    private static List<net.minecraft.network.chat.Component> inspectionRows=List.of();
    private static UUID selectedMarker;
    private static final Map<UUID, CompoundTag> directory = new LinkedHashMap<>();
    private static boolean directoryRequested, directoryReady, sentFullDirectory;
    private static long directoryRetryAt;
    private static int directoryRetries;
    private static long directoryRevision, lastDirectoryUse;
    private static long pendingAccessEpoch, confirmedAccessEpoch, reviewExpires;
    private static boolean hasSnapshot(ResourceLocation dimension) {
        return received!=0 && enabled && confirmedAccessEpoch==com.example.examplemod.client.CityPublicDirectory.accessEpoch()
                && dimension!=null && dimension.equals(mapDimension) && net.minecraft.Util.getMillis()-received<30_000;
    }
    public static boolean supportsTrade(ResourceLocation dimension) {
        return hasSnapshot(dimension) && state.getInt("TradeVersion")==1;
    }
    public static long directoryRevision() { return directoryRevision; }
    public static StationDirections.Nearby nearbyStations(ResourceLocation dimension, BlockPos from, BlockPos to) {
        return hasDirectory(dimension) ? StationDirections.nearest(directory.values(), dimension, from, to) : null;
    }
    public static boolean hasDirectory(ResourceLocation dimension) {
        return directoryReady && hasSnapshot(dimension);
    }
    public static void requestDirectoryForSearch() {
        if (!enabled || directoryRequested || session) return;
        directoryRequested = true;
        hash = 0;
        refresh();
    }
    public static void maintainDirectory(boolean inUse) {
        long now=net.minecraft.Util.getMillis();
        if (inUse) lastDirectoryUse=now;
        else if (!session && now-lastDirectoryUse>=15_000) directoryRequested=false;
        if (enabled && (session || directoryRequested)
                && (now-sent>=15_000 || directoryRetryAt!=0 && now>=directoryRetryAt)) refresh();
    }
    private static void clearDirectory() {
        directory.clear();
        inspection=null; inspectionDirty=false; inspectionOffset=0;
        lastInspection=null; inspectionRows=List.of(); focusedRailSource=null; focusedRailMesh=focusedRailCache.from(null);
        directoryRequested = directoryReady = sentFullDirectory = false;
        directoryRetryAt=0;directoryRetries=0;
        directoryRevision++;
    }
    public static String markerKind(UUID id, ResourceLocation dimension) {
        var trade=AtlasTradeDirectory.row(id,dimension);
        if(trade!=null)return trade.kind();
        var marker = hasDirectory(dimension) ? directory.get(id) : null;
        return marker == null ? "" : marker.getString("Kind");
    }
    public static String markerStatus(UUID id, ResourceLocation dimension) {
        var marker = hasDirectory(dimension) ? directory.get(id) : null;
        return marker == null ? "" : marker.getString("Status");
    }
    public static CityMapPanel.Selection resolveMapSelection(UUID id, ResourceLocation dimension) {
        var trade=AtlasTradeDirectory.row(id,dimension);
        if (trade!=null)return trade.selection();
        var marker = hasDirectory(dimension) ? directory.get(id) : null;
        if (marker == null) return null;
        var location = marker.getCompound("Location");
        var detail = marker.getString("Detail");
        return new CityMapPanel.Selection(CityMapPanel.SelectionKind.LOGISTICS, id,
                net.minecraft.network.chat.Component.literal(marker.getString("Name")), BlockPos.of(location.getLong("Pos")),
                false, detail.isBlank() ? List.of() : List.of(net.minecraft.network.chat.Component.literal(detail)),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MINECART), false);
    }
    public static List<CityMapPanel.Selection> visibleMapSelections(ResourceLocation dimension) {
        if (!hasDirectory(dimension)) return List.of();
        return directory.keySet().stream().map(id -> resolveMapSelection(id, dimension)).filter(Objects::nonNull).toList();
    }
    public static boolean canInspect(UUID id,ResourceLocation dim) {
        return id!=null && hasDirectory(dim) && state.getInt("InspectionVersion")==1 && "shipment".equals(markerKind(id,dim));
    }
    public static boolean inspecting(UUID id,ResourceLocation dim) { return id!=null && id.equals(inspection) && canInspect(id,dim); }
    public static void inspect(UUID id,ResourceLocation dim) {
        if (!canInspect(id,dim) || id.equals(inspection)) return;
        inspection=id; inspectionOffset=0;
        changeInspectionFocus(id);
    }
    public static void finishInspection() {
        if (inspection==null) return;
        inspection=null;
        changeInspectionFocus(null);
    }
    private static void changeInspectionFocus(UUID target) {
        focus=target; pending=null; hash=0; inspectionDirty=true; reviewed=null; route=null;
        state.remove("Shipment"); state.remove("RouteStops");
        // Retain ordinary rails while the newly selected section is being validated.
        var fresh=new ListTag();
        for(Tag raw:state.getList("Rails",Tag.TAG_COMPOUND)) {
            var edge=(CompoundTag)raw;
            if(edge.contains("Phase")) continue;
            if(edge.getBoolean("Focus")) {edge=edge.copy();edge.remove("Focus");}
            fresh.add(edge);
        }
        state.put("Rails",fresh);
        lastInspection=null;inspectionRows=List.of();
    }
    public static void maintainInspection(CityMapPanel.Selection selected) {
        if(inspection!=null && (selected==null || !inspection.equals(selected.id()))) finishInspection();
        if(inspectionDirty && enabled && net.minecraft.Util.getMillis()-sent>=300) refresh();
    }
    public static List<net.minecraft.network.chat.Component> inspectionLines(UUID id,ResourceLocation dim) {
        if(!inspecting(id,dim)) return List.of();
        var trace=state.getCompound("Shipment");
        if(!trace.hasUUID("Parcel") || !id.equals(trace.getUUID("Parcel"))) return List.of(ShipmentInspectionText.text("waiting"));
        return displayInspection(trace);
    }
    public static net.minecraft.network.chat.Component freshness(UUID id,ResourceLocation dim) {
        long age=replyAgeSeconds();
        var trace=state.getCompound("Shipment");
        if(inspecting(id,dim) && trace.getInt("Version")==1 && trace.hasUUID("Parcel") && id.equals(trace.getUUID("Parcel"))
                && !trace.getString("Observation").isBlank() && trace.contains("Age") && trace.getLong("Age")>=0)
            return ShipmentInspectionText.text("freshness",age,trace.getLong("Age")+age);
        return net.minecraft.network.chat.Component.translatable("gui.createcityexpansioncore.atlas3.ui.logistics_freshness",age);
    }
    private static List<net.minecraft.network.chat.Component> displayInspection(CompoundTag trace) {
        long elapsed=replyAgeSeconds();
        if(trace!=lastInspection || elapsed!=lastInspectionSecond) {
            lastInspection=trace;lastInspectionSecond=elapsed;
            inspectionRows=ShipmentInspectionText.lines(trace,elapsed);
        }
        return inspectionRows;
    }
    public static long replyAgeSeconds() { return Math.max(0, net.minecraft.Util.getMillis() - received) / 1000; }

    record ReturnToMap(ResourceLocation dimension, UUID marker, UUID object, AtlasTradeDirectory.Reference trade, CompoundTag route,
                       LogisticsCenterScreen.View origin, java.lang.ref.WeakReference<Object> connection, boolean planning,
                       java.lang.ref.WeakReference<PolishedWorldMapScreen> source) {
        boolean restore() {
            var mc=Minecraft.getInstance();
            if (mc.player==null || mc.getConnection()==null || mc.getConnection()!=connection.get()) return false;
            if (!planning) {
                var original=source.get();
                if (original!=null && original.validReturn()) { mc.setScreen(original); AtlasTradeDirectory.restore(trade);return true; }
                com.example.examplemod.client.CityPublicDirectory.open(dimension);
                mc.setScreen(new PolishedWorldMapScreen(CityWorldMapSession.INSTANCE));
                AtlasTradeDirectory.restore(trade);
                return true;
            }
            var location=new CompoundTag();
            location.putString("Dimension",dimension.toString());
            var target=new CompoundTag();
            target.put("Location",location);
            target.putUUID("Id",object);
            open(origin==null?new LogisticsCenterScreen():origin.restore(),target,route,null);
            focusLocation=null; fitRoute=false; selectedMarker=marker;
            AtlasTradeDirectory.restore(trade);
            return true;
        }
    }

    private record TrainMotion(String dimension, double fromX, double fromZ, double toX, double toZ, long started) {}
    private static final Map<UUID,TrainMotion> trainMotion=new HashMap<>();
    private static double[] trainPosition(CompoundTag marker,long now){
        var loc=marker.getCompound("Location");var pos=BlockPos.of(loc.getLong("Pos"));
        double x=pos.getX()+.5,z=pos.getZ()+.5;
        if(!marker.getString("Kind").equals("train"))return new double[]{x,z};
        var id=marker.getUUID("Id");
        var previous=trainMotion.get(id);
        String dimension=loc.getString("Dimension");
        if (previous==null || !dimension.equals(previous.dimension) || Math.hypot(x-previous.toX,z-previous.toZ)>256) {
            trainMotion.put(id,new TrainMotion(dimension,x,z,x,z,now));
            return new double[]{x,z};
        }
        double t=Math.clamp((now-previous.started)/1000.0,0,1);
        double px=previous.fromX+(previous.toX-previous.fromX)*t;
        double pz=previous.fromZ+(previous.toZ-previous.fromZ)*t;
        if (previous.toX!=x || previous.toZ!=z)
            trainMotion.put(id,new TrainMotion(dimension,px,pz,x,z,now));
        return new double[]{px,pz};
    }
    private static boolean enabled,visible,session;
    private static ResourceLocation mapDimension;
    private static int hash,barX,barY;private static long sent,received;
    private static boolean fitRoute;
    private static final RailMesh.Cache railCache=new RailMesh.Cache();
    private static final RailBatch railBatch=new RailBatch();
    private static final DispatchMotion.Transition deliveredBar=new DispatchMotion.Transition(0);
    private LogisticsMapOverlay() {}
    public static void deactivate(){AtlasTradeDirectory.clear();clearDirectory();railCache.from(null);railBatch.clear();trainMotion.clear();enabled=visible=session=false;HITS.clear();selection=null;selectedMarker=null;reviewed=null;pending=null;state=new CompoundTag();fullState=new CompoundTag();route=focusLocation=null;focus=null;mapDimension=null;}
    public static boolean isSession(){return session;}
    public static UUID selectedTrain(ResourceLocation dimension) {
        if (!session || !hasDirectory(dimension) || !LAYERS.contains("train") || selection == null || !selection.hasUUID("Id")
                || !"train".equals(selection.getString("Kind"))
                || !dimension.toString().equals(selection.getCompound("Location").getString("Dimension"))) return null;
        return selection.getUUID("Id");
    }

    public static void updateFollow(CityMapPanel map) {
        UUID following = map.followedTrain();
        if (following == null) return;
        if (!following.equals(selectedTrain(map.dimensionId()))) {
            map.interruptCamera();
            return;
        }
        if (map.reducedMotion()) {
            var pos = BlockPos.of(selection.getCompound("Location").getLong("Pos"));
            map.followPosition(pos.getX()+.5,pos.getZ()+.5);
        } else {
            var pos = trainPosition(selection,net.minecraft.Util.getMillis());
            map.followPosition(pos[0],pos[1]);
        }
    }
    public static void reset(){deactivate();state=new CompoundTag();fullState=new CompoundTag();route=focusLocation=null;pending=focus=null;mapDimension=null;hash=0;sent=received=0;}
    /**
     * Attaches the same bounded rail feed to a normal World Map. The logistics center keeps its own
     * session, so this must not steal focus or route preview state from it.
     */
    public static void attach(ResourceLocation dimension){
        attach(dimension,false);
    }
    public static void attach(ResourceLocation dimension, boolean needsDirectory){
        if(session)return;
        if(enabled&&dimension.equals(mapDimension)){
            if(needsDirectory)requestDirectoryForSearch();
            return;
        }
        clearDirectory();enabled=true;visible=false;selection=null;focus=null;route=null;focusLocation=null;pending=null;
        state=new CompoundTag();fullState=new CompoundTag();mapDimension=dimension;hash=0;sent=received=0;
        directoryRequested=needsDirectory;
        refresh();
    }
    public static void openRoute(LogisticsCenterScreen parent,CompoundTag values,CompoundTag preview){reviewed=null;open(parent,new CompoundTag(),values,preview);}
    public static void reviewRoute(LogisticsCenterScreen parent,net.minecraft.client.gui.screens.Screen form,CompoundTag values,CompoundTag preview){reviewed=null;open(parent,new CompoundTag(),null,preview);reviewed=preview.copy();reviewExpires=net.minecraft.Util.getMillis()+15_000;route=values.copy();var mc=Minecraft.getInstance();if(mc.screen instanceof PolishedWorldMapScreen map)map.returnToCommunity(form);}
    public static void open(LogisticsCenterScreen parent,CompoundTag selected){reviewed=null;open(parent,selected,null,null);}
    private static void open(LogisticsCenterScreen parent,CompoundTag selected,CompoundTag requestedRoute,CompoundTag initial){var mc=Minecraft.getInstance();if(mc.player==null)return;clearDirectory();received=0;enabled=true;session=true;visible=false;selection=null;focus=selected.hasUUID("Id")?selected.getUUID("Id"):null;focusLocation=selected.contains("Location")?selected.getCompound("Location").copy():null;route=requestedRoute==null?null:requestedRoute.copy();fitRoute=true;state=initial==null?new CompoundTag():initial.copy();fullState=new CompoundTag();hash=0;
        ResourceLocation dimension=focusLocation==null?mc.player.level().dimension().location():ResourceLocation.parse(focusLocation.getString("Dimension"));mapDimension=dimension;com.example.examplemod.client.CityPublicDirectory.open(dimension);var map=new PolishedWorldMapScreen(CityWorldMapSession.INSTANCE);map.planningDimension(dimension);map.returnToCommunity(parent);mc.setScreen(map);refresh();}
    public static void accept(CompoundTag response) {
        if (!enabled || !response.hasUUID("Request") || !response.getUUID("Request").equals(pending)) return;
        pending=null;
        if (pendingAccessEpoch!=com.example.examplemod.client.CityPublicDirectory.accessEpoch()) { sent=0; return; }
        if(response.getBoolean("Unchanged") && !response.contains("Hash")) {
            // A throttled read did not check object visibility or observe a new handoff.
            if(inspection!=null || session && focus!=null) inspectionDirty=true;
            if(sentFullDirectory && !directoryReady && directoryRetries<3)
                directoryRetryAt=net.minecraft.Util.getMillis()+(500L<<directoryRetries++);
            return;
        }
        confirmedAccessEpoch=pendingAccessEpoch;
        if (!response.getBoolean("Unchanged")) {
            fullState=com.example.examplemod.cargo.center.CenterDelta.apply(fullState,response);
            state=fullState.getCompound("Map").copy();
            if (reviewed!=null) for (String key:List.of("Rails","RouteStops","RouteFound"))
                if (reviewed.contains(key)) state.put(key,reviewed.get(key).copy());
            directory.clear();
            if (sentFullDirectory && (session || directoryRequested)) for (Tag raw : state.getList("Markers", Tag.TAG_COMPOUND)) {
                var marker = (CompoundTag) raw;
                if (marker.hasUUID("Id") && mapDimension.toString().equals(marker.getCompound("Location").getString("Dimension")))
                    directory.putIfAbsent(marker.getUUID("Id"), marker);
            }
            directoryRevision++;
            UUID desired=selection!=null && selection.hasUUID("Id")?selection.getUUID("Id"):selectedMarker;
            selection=null;
            if (desired!=null || !trainMotion.isEmpty()) {
                var present=new HashSet<UUID>();
                for (Tag entry:state.getList("Markers",10)) {
                    var marker=(CompoundTag)entry;
                    if (!marker.hasUUID("Id")) continue;
                    UUID id=marker.getUUID("Id");
                    if ("train".equals(marker.getString("Kind"))) present.add(id);
                    if (id.equals(desired)) selection=marker.copy();
                }
                trainMotion.keySet().retainAll(present);
            }
            selectedMarker=null;
        }
        received=net.minecraft.Util.getMillis();
        directoryReady=sentFullDirectory && (session || directoryRequested) && fullState.contains("Map",Tag.TAG_COMPOUND);
        if(directoryReady)directoryRetries=0;
        if(response.contains("Hash"))hash=response.getInt("Hash");
    }
    private static void refresh(){inspectionDirty=false;pendingAccessEpoch=com.example.examplemod.client.CityPublicDirectory.accessEpoch();
        directoryRetryAt=0;sentFullDirectory=session || directoryRequested;
        if (reviewed!=null && (net.minecraft.Util.getMillis()>=reviewExpires || received!=0 && confirmedAccessEpoch!=pendingAccessEpoch)) {
            reviewed=null; received=0; hash=0; fullState=new CompoundTag(); state=new CompoundTag();
        }
        var q=new CompoundTag();pending=UUID.randomUUID();q.putUUID("Request",pending);q.putString("Action","VIEW");q.putString("Section","map");q.putInt("Hash",hash);if(mapDimension!=null)q.putString("Dimension",mapDimension.toString());if(!sentFullDirectory)q.putBoolean("RailsOnly",true);if(focus!=null && (session || inspection!=null))q.putUUID("Focus",focus);if(route!=null&&reviewed==null)q.put("MapRoute",route.copy());PacketDistributor.sendToServer(new CenterRequest(q));sent=net.minecraft.Util.getMillis();}
    public static void render(GuiGraphics g,CityMapPanel map,ResourceLocation dimension,int left,int top,int width,int height,int mx,int my,boolean active){var viewport=new View(left,top,width,height);visible=enabled&&active;HITS.clear();panels.clear();if(!visible)return;if(net.minecraft.Util.getMillis()-sent>(session && pending==null ? 1000 : 15000))refresh();
        if (!hasDirectory(dimension)) return;
        var pinned=map.getSelection();
        selection=pinned!=null && pinned.kind()==CityMapPanel.SelectionKind.LOGISTICS ? directory.get(pinned.id()) : null;
        var trade=pinned==null?null:AtlasTradeDirectory.row(pinned.id(),dimension);
        if(trade!=null && trade.pos()!=null)selection=trade.marker();
        if (!session && selection==null) return;
        activeViewport=viewport;
        barX=viewport.x()+8;barY=viewport.y()+8;
        toolbar=session ? reservePanel(map,new View(barX,barY,Math.max(0,Math.min(220,width-16)),23)) : null;

        var routeCard=routeCardBounds(viewport);
        if(inspection!=null)routeCard=null;
        if(routeCard!=null)reservePanel(map,routeCard);
        g.enableScissor(viewport.x(),viewport.y(),viewport.right(),viewport.bottom());
        if(!map.isOverlayEnabled(CityMapPanel.Overlay.TRACKS) || session && !LAYERS.contains("line")) {
            var trace=state.getCompound("Shipment");
            if("track".equals(trace.getString("Geometry"))) {
                var source=state.get("Rails");
                if(focusedRailSource!=source) {
                    focusedRailSource=source;
                    var focused=new ListTag();
                    for(var raw:state.getList("Rails",Tag.TAG_COMPOUND)) {
                        var edge=(CompoundTag)raw;
                        if(edge.getBoolean("Focus") || edge.contains("Phase"))focused.add(edge);
                    }
                    focusedRailMesh=focusedRailCache.from(focused);
                }
                railBatch.draw(g,map,focusedRailMesh.dimension(dimension.toString()),null,true);
            }
        }
        renderRoute(g,map,dimension,viewport);
        var markers = new ArrayList<MarkerDraw>();
        Iterable<CompoundTag> displayed=session ? directory.values() : List.of(selection);
        if(session && trade!=null && selection!=null) displayed=com.google.common.collect.Iterables.concat(displayed,List.of(selection));
        for(var marker:displayed){
            var location=marker.getCompound("Location");String kind=marker.getString("Kind");
            if(session && !LAYERS.contains(kind) && !(trade!=null && marker==selection))continue;
            var pos=BlockPos.of(location.getLong("Pos"));var smooth=trainPosition(marker,net.minecraft.Util.getMillis());int x=map.worldToScreenX(map.reducedMotion()?pos.getX()+.5:smooth[0]),y=map.worldToScreenY(map.reducedMotion()?pos.getZ()+.5:smooth[1]);if(!viewport.contains(x,y))continue;
            boolean selected=selection!=null && selection.hasUUID("Id") && selection.getUUID("Id").equals(marker.getUUID("Id"));
            markers.add(new MarkerDraw(marker,kind,x,y,new View(x-9,y-10,22,23).clippedTo(viewport),selected,kind.equals("station") || kind.equals("train")));
        }
        markers.sort(Comparator.comparing(MarkerDraw::selected).thenComparing(MarkerDraw::node));
        MarkerDraw hover = null;
        if (topPanel(mx,my)==null) {
            for (var marker : markers.reversed()) if (marker.bounds.contains(mx,my)) { hover=marker;break; }
        }
        for (var draw : markers) {
            var marker=draw.data;
            String kind=draw.kind;
            int x=draw.x, y=draw.y;
            boolean hovered=draw==hover, selected=draw.selected;
            var hit=draw.bounds;
            int color=marker.getString("Status").equals("LAST_OBSERVED")?BRASS:kind.equals("train")?BRASS:TEAL;
            surface(g,x-9,y-10,22,23,selected?0xff294b45:PANEL);if(hovered||selected){g.hLine(x-8,x+10,y-10,BRASS);g.hLine(x-8,x+10,y+12,BRASS);}icon(g,x-4,y-6,kindIcon(kind),color);HITS.putLast(hit,marker);
            map.labels().reserve(hit.x,hit.y,hit.width,hit.height);
            boolean node = kind.equals("station") || kind.equals("train");
            if (hovered || selected || (node && map.zoom() >= 1)) {
                var name = fit(marker.getString("Name"),160);
                if (hovered) name += " · " + value(marker.getString("Status"));
                var priority = selected ? com.example.examplemod.client.map.MapLabelLayout.Priority.SELECTED
                        : hovered ? com.example.examplemod.client.map.MapLabelLayout.Priority.HOVER
                        : com.example.examplemod.client.map.MapLabelLayout.Priority.NODE;
                map.labels().offer("cargo/"+marker.getUUID("Id"),name,color,x,y,16,Minecraft.getInstance().font.width(name),priority);
            }
        }
        if (!session) { g.disableScissor(); return; }
        surface(g,toolbar.x,toolbar.y,toolbar.width,toolbar.height,PANEL);
        g.enableScissor(toolbar.x,toolbar.y,toolbar.right(),toolbar.bottom());
        for(int i=0;i<KINDS.length;i++)
            icon(g,barX+9+i*27,barY+4,kindIcon(KINDS[i]),LAYERS.contains(KINDS[i])?TEAL:MUTED);
        g.disableScissor();
        if (topPanel(mx,my)==toolbar && mx-barX<KINDS.length*27)
            g.drawString(Minecraft.getInstance().font,text("layer."+KINDS[(mx-barX)/27]),barX,barY+30,TEXT,false);

        if(routeCard!=null)renderRouteCard(g,map,viewport,routeCard);
        long age=received==0?Long.MAX_VALUE:Math.max(0,net.minecraft.Util.getMillis()-received);
        if(age>15000)g.drawString(Minecraft.getInstance().font,text("map.stale"),barX,viewport.bottom()-15,BRASS,false);
        else g.drawString(Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("ccec.center.map.age",age/1000).getString(),barX,viewport.bottom()-15,MUTED,false);
        g.disableScissor();
    }
    private static RailMesh railMesh() { return railCache.from(state.get("Rails")); }

    public static int railCount(ResourceLocation dimension) {
        return hasSnapshot(dimension) ? railMesh().dimension(dimension.toString()).edges.size() : 0;
    }

    /** Called inside the map's clipped rail layer, before markers and labels. */
    public static void renderRails(GuiGraphics graphics,CityMapPanel map) {
        if (!hasSnapshot(map.dimensionId()) || session && !LAYERS.contains("line")) return;
        UUID selected=focus;
        if (selection!=null && "line".equals(selection.getString("Kind")) && selection.hasUUID("Id"))
            selected=selection.getUUID("Id");
        railBatch.draw(graphics,map,railMesh().dimension(map.dimensionId().toString()),selected,route!=null);
    }

    public static void cancelAutomaticFocus() { focusLocation=null; fitRoute=false; }

    /** Apply automatic framing once before terrain, rail and marker projection for this frame. */
    public static void prepareView(CityMapPanel map,ResourceLocation dimension,int width,int height) {
        if (!session || !hasSnapshot(dimension)) return;
        if (focusLocation!=null && dimension.toString().equals(focusLocation.getString("Dimension"))) {
            map.centerOn(BlockPos.of(focusLocation.getLong("Pos")));
            focusLocation=null;
        }
        if (!fitRoute) return;
        boolean exact=exactRoute();
        var points=exact?railMesh().dimension(dimension.toString()).routeEnds:routePoints(dimension);
        if (points.isEmpty()) return;
        var bounds=ShipmentRouteGeometry.fit(points,width,height,70,exact?.0001:.12,4);
        if(bounds!=null) { map.restoreView(bounds.x(),bounds.z(),bounds.zoom()); fitRoute=false; }
    }
    static int kindIcon(String kind){return switch(kind){case "point"->4;case "warehouse"->5;case "station"->11;case "train"->2;case "line"->3;case "shipment"->7;default->13;};}
    private static List<ShipmentRouteGeometry.Point> routePoints(ResourceLocation dimension){
        var points=new ArrayList<ShipmentRouteGeometry.Point>();for(Tag raw:state.getList("RouteStops",10)){var stop=(CompoundTag)raw;var location=stop.getCompound("Location");if(!location.getString("Dimension").equals(dimension.toString()))continue;var pos=BlockPos.of(location.getLong("Pos"));points.add(new ShipmentRouteGeometry.Point(pos.getX()+.5,pos.getZ()+.5));}return points;
    }
    private static void renderRoute(GuiGraphics g,CityMapPanel map,ResourceLocation dimension,View view){
        if(exactRoute()) return;
        var points=routePoints(dimension);
        if(points.isEmpty()) return;
        boolean sameDimension=points.size()==state.getList("RouteStops",10).size();
        if(sameDimension)railBatch.schematic(g,map,points);
        int index=0;for(Tag raw:state.getList("RouteStops",10)){var stop=(CompoundTag)raw;var location=stop.getCompound("Location");if(!location.getString("Dimension").equals(dimension.toString()))continue;var point=points.get(index++);int x=map.worldToScreenX(point.x()),y=map.worldToScreenY(point.z());if(!view.contains(x,y))continue;g.fill(x-7,y-7,x+8,y+8,PANEL);g.hLine(x-7,x+7,y-7,BRASS);g.hLine(x-7,x+7,y+7,BRASS);g.drawCenteredString(Minecraft.getInstance().font,Integer.toString(stop.getInt("Index")),x,y-3,BRASS);}
    }
    private static View routeCardBounds(View viewport) {
        var stops=state.getList("RouteStops",10);
        var shipment=state.getCompound("Shipment");
        int width=Math.min(252,viewport.width-20);
        if (width<150 || viewport.height<170 || stops.isEmpty()&&shipment.isEmpty()) return null;
        int shown=Math.min(stops.size(),Math.max(0,(viewport.height-190)/19));
        int height=Math.min((shipment.isEmpty()?100:158)+shown*19,viewport.height-25);
        return new View(viewport.right()-width-10,viewport.y+10,width,height);
    }

    private static void renderRouteCard(GuiGraphics g,CityMapPanel map,View view,View bounds) {
        var stops=state.getList("RouteStops",10);
        var shipment=state.getCompound("Shipment");
        int width=bounds.width, x=bounds.x, y=bounds.y;
        int shown=Math.min(stops.size(),Math.max(0,(view.height-190)/19));
        surface(g,x,y,width,bounds.height,PANEL);var font=Minecraft.getInstance().font;
        icon(g,x+11,y+10,3,BRASS);g.drawString(font,text("map.route"),x+32,y+13,BRASS,false);int dy=y+33;
        if(shipment.getInt("Version")==1) {
            var rows=displayInspection(shipment);
            int capacity=Math.max(1,(bounds.height-54)/13);
            inspectionOffset=Math.clamp(inspectionOffset,0,Math.max(0,rows.size()-capacity));
            for(int i=inspectionOffset;i<Math.min(rows.size(),inspectionOffset+capacity);i++) {
                g.drawString(font,fit(rows.get(i).getString(),width-22),x+11,dy,TEXT,false);dy+=13;
            }
            if(rows.size()>capacity)g.drawString(font,fit(ShipmentInspectionText.text("scroll",inspectionOffset+1,
                    Math.min(rows.size(),inspectionOffset+capacity),rows.size()).getString(),width-22),x+11,y+bounds.height-13,BRASS,false);
            return;
        }
        if(!shipment.isEmpty()){
            g.drawString(font,fit(shipment.getString("Name"),width-22),x+11,dy,TEXT,false);dy+=16;
            String status=shipment.getString("OrderState").equals("CANCELLING")?"CANCELLING":shipment.getString("Status");g.drawString(font,fit(value(status),width-22),x+11,dy,status.equals("CANCELLING")?BRASS:TEAL,false);dy+=18;
            int count=shipment.getInt("Quantity"),delivered=shipment.getInt("Delivered");g.drawString(font,text("delivered")+" "+delivered+" / "+count,x+11,dy,MUTED,false);dy+=14;
            int bar=width-22;g.fill(x+11,dy,x+11+bar,dy+4,EDGE);int filled=(int)(bar*deliveredBar.value(count==0?0:delivered/(double)count,System.nanoTime(),map.reducedMotion(),240));g.fill(x+11,dy,x+11+filled,dy+4,TEAL);dy+=17;
        }
        g.drawString(font,fit(text(exactRoute()?"navigator.frozen_route":"map.schematic"),width-22),x+11,dy,BRASS,false);dy+=15;
        g.drawString(font,fit(text(exactRoute()?"navigator.review_only":"map.direction_demo"),width-22),x+11,dy,MUTED,false);dy+=20;
        for(int i=0;i<shown;i++){var stop=stops.getCompound(i);g.drawString(font,fit(stop.getInt("Index")+". "+stop.getString("Name"),width-22),x+11,dy,TEXT,false);dy+=19;}
        if(stops.size()>shown)g.drawString(font,"+ "+(stops.size()-shown),x+11,dy,MUTED,false);
    }
    private static boolean exactRoute(){return reviewed!=null||railMesh().exactRoute||"track".equals(state.getCompound("Shipment").getString("Geometry"));}
    public static boolean scrollInspection(double x,double y,double dy) {
        if(!visible || !session || inspection!=null || dy==0 || !hasDirectory(mapDimension) || activeViewport==null
                || state.getCompound("Shipment").getInt("Version")!=1) return false;
        var panel=routeCardBounds(activeViewport);
        if(panel==null || !panel.contains(x,y)) return false;
        inspectionOffset=Math.max(0,inspectionOffset+(dy>0?-1:1));
        return true;
    }
    public static boolean click(double x,double y,int button) {
        if(!visible||!hasDirectory(mapDimension)||button!=0||activeViewport==null||!activeViewport.contains(x,y))return false;
        var panel=topPanel(x,y);
        if(toolbar!=null && panel==toolbar && x<barX+KINDS.length*27) {
            String kind=KINDS[(int)((x-barX)/27)];
            if(!LAYERS.remove(kind))LAYERS.add(kind);
            return true;
        }
        if (panel!=null) return true;
        for (var hit : HITS.reversed().entrySet()) {
            var marker=hit.getValue();
            if (!hit.getKey().contains(x,y)) continue;
            if (session && !LAYERS.contains(marker.getString("Kind")) && AtlasTradeDirectory.row(marker.getUUID("Id"),mapDimension)==null) continue;
            var atlas=PolishedWorldMapScreen.activeView();
            var current=resolveMapSelection(marker.getUUID("Id"),mapDimension);
            if (atlas==null || current==null) return true;
            selection=marker.copy();
            focus=selection.hasUUID("OpenId")?selection.getUUID("OpenId"):selection.getUUID("Id");
            fitRoute=false;
            atlas.pinMapObject(current);
            if(session) refresh();
            return true;
        }
        return false;
    }

    public static boolean canOpenObject(UUID id, ResourceLocation dimension) {
        if(AtlasTradeDirectory.row(id,dimension)!=null)return true;
        var marker=hasDirectory(dimension)?directory.get(id):null;
        return marker!=null && (!"station".equals(marker.getString("Kind")) || marker.hasUUID("OpenId"));
    }

    public static void openObject(UUID id, ResourceLocation dimension) {
        if (!canOpenObject(id,dimension)) return;
        var marker=directory.get(id);
        var atlas=PolishedWorldMapScreen.activeView();
        if (atlas==null) return;
        var trade=AtlasTradeDirectory.row(id,dimension);
        UUID object=trade!=null ? trade.object() : marker.hasUUID("OpenId") ? marker.getUUID("OpenId") : id;
        String targetKind=trade==null ? marker.getString("Kind") : trade.kind().equals("intake") ? "warehouse" : trade.kind();
        var center=new LogisticsCenterScreen();
        center.focusObject(targetKind,object);
        center.mapReturn=new ReturnToMap(dimension,id,object,trade==null?null:AtlasTradeDirectory.reference(trade),route==null?null:route.copy(),atlas.logisticsOrigin(),
                new java.lang.ref.WeakReference<>(Minecraft.getInstance().getConnection()), session, new java.lang.ref.WeakReference<>(atlas));
        Minecraft.getInstance().setScreen(center);
    }
}
