package com.example.examplemod.client.journey;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.map.CityMapPanel;
import net.minecraft.resources.ResourceLocation;
import com.example.examplemod.journey.*;
import com.example.examplemod.journey.JourneyModel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class JourneyClient {
    public static final Map<String,Marker> markers=new LinkedHashMap<>();
    public static List<CompoundTag> quests=List.of(),chains=List.of();
    public static String target="",activeQuest="",chain="";
    public static boolean paused,canEdit,detour;
    public static Marker completionMarker;
    public static long targetAt,completionAt;
    private static boolean ready, objectLookupSupported;
    public static final KeyMapping JOURNAL=new KeyMapping("key.createcityexpansioncore.journal",net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.createcityexpansioncore");
    private static Object connection;
    private static int ticks,waypoint;
    private static String routeTarget="",completed="";
    private static long completedAt;
    private static AtlasObjective atlasGoal;
    private static boolean previousGuidanceHidden;
    private static JourneyRequestPayload pendingChoice;
    private static long choiceExpires, lookupSequence;
    public static Component tr(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.journey."+key,args);}
    public static void request(JourneyRequestPayload.Action action,String id){
        if (Minecraft.getInstance().getConnection() == null) return;
        var request = new JourneyRequestPayload(action,id);
        if (action == JourneyRequestPayload.Action.TRACK_MARKER || action == JourneyRequestPayload.Action.TRACK_QUEST
                || action == JourneyRequestPayload.Action.START || action == JourneyRequestPayload.Action.RESUME) {
            pendingChoice = request;
            choiceExpires = net.minecraft.Util.getMillis() + 10_000;
        }
        PacketDistributor.sendToServer(request);
    }
    public static boolean canTrack(CityMapPanel panel, ResourceLocation dim, CityMapPanel.Selection value) {
        return !JourneyPlaytest.running() && sourceSupported(value) && AtlasObjective.resolve(panel, dim, value) != null;
    }
    private static boolean sourceSupported(CityMapPanel.Selection value) {
        return value != null && (value.kind() == CityMapPanel.SelectionKind.WAYPOINT || objectLookupSupported);
    }
    public static boolean track(CityMapPanel panel, ResourceLocation dim, CityMapPanel.Selection value) {
        if (JourneyPlaytest.running() || Minecraft.getInstance().getConnection() == null || !sourceSupported(value)) return false;
        var resolved = AtlasObjective.resolve(panel, dim, value);
        if (resolved == null || isTracking(dim, resolved)) return false;
        releaseTrip();
        atlasGoal = AtlasObjective.create(resolved, dim);
        previousGuidanceHidden = !paused && (!target.isEmpty() || !activeQuest.isEmpty());
        pendingChoice = null;
        CommunityFx.sound("map_navigation");
        return true;
    }
    public static boolean isTracking(ResourceLocation dim, CityMapPanel.Selection value) {
        return atlasGoal != null && atlasGoal.matches(dim, value);
    }
    public static com.example.examplemod.navigation.TripRef routeReference(CityMapPanel.Selection value, ResourceLocation dim) {
        if(value==null || value.id()==null)return null;
        var type=AtlasObjective.source(value,dim);
        if(type==null)return value.kind()==CityMapPanel.SelectionKind.WAYPOINT ?
                new com.example.examplemod.navigation.TripRef(com.example.examplemod.navigation.TripRef.Kind.PIN,value.id()):null;
        var trade=value.kind()==CityMapPanel.SelectionKind.LOGISTICS ?
                com.example.examplemod.client.logistics.AtlasTradeDirectory.row(value.id(),dim):null;
        return new com.example.examplemod.navigation.TripRef(com.example.examplemod.navigation.TripRef.Kind.valueOf(type.name()),trade==null?value.id():trade.object());
    }
    public static com.example.examplemod.navigation.TripNetwork.Query itinerary() { return atlasGoal==null?null:atlasGoal.trip; }
    public static boolean needsTripUpdate(){return atlasGoal!=null && atlasGoal.trip!=null && atlasGoal.recalculate && atlasGoal.readable();}
    public static void endTrip(UUID request) {
        if(itinerary()!=null && itinerary().request().equals(request))atlasGoal=null;
    }
    public static boolean beginTrip(com.example.examplemod.navigation.TripNetwork.Query query, CityMapPanel.Selection selection,
                                    ResourceLocation dim,CompoundTag packet) {
        if (JourneyPlaytest.running() || !query.world().equals(CityPublicDirectory.worldId(dim)) || !riderIn(dim))return false;
        if (selection!=null) atlasGoal=AtlasObjective.itinerary(selection,dim,query);
        else {
            var previous=itinerary();
            if(previous==null || !previous.world().equals(query.world()) || !previous.goal().equals(query.goal()) ||
                    !atlasGoal.dimension.equals(dim))return false;
            atlasGoal.trip=query;
        }
        previousGuidanceHidden=!paused && (!target.isEmpty() || !activeQuest.isEmpty());
        pendingChoice=null; CommunityFx.sound("map_navigation");
        guideTrip(query,dim,packet);
        return true;
    }
    private static boolean riderIn(ResourceLocation dim) {
        var player=Minecraft.getInstance().player;
        return player!=null && player.isAlive() && player.level().dimension().location().equals(dim);
    }
    public static void guideTrip(com.example.examplemod.navigation.TripNetwork.Query query,ResourceLocation dim,CompoundTag packet) {
        if (!query.equals(itinerary()) || !atlasGoal.dimension.equals(dim) || !riderIn(dim))return;
        var goal=atlasGoal;
        goal.observed=net.minecraft.Util.getMillis();goal.recalculate=packet.getBoolean("Replan");
        String phase=packet.contains("PhaseDetail",Tag.TAG_STRING)?packet.getString("PhaseDetail"):packet.getString("Phase");
        goal.instruction=Component.translatable("gui.createcityexpansioncore.trip.phase."+phase,
                packet.getString("Train"),packet.getString("From"),packet.getString("Stop"));
        var pos=net.minecraft.core.BlockPos.of(packet.getLong("Pos"));
        goal.marker.point=new Point(dim.toString(),pos.getX(),pos.getY(),pos.getZ());
        goal.marker.title=packet.getString("Goal");
        goal.marker.route.clear();
        for(long packed:packet.getLongArray("Ahead")){
            var next=net.minecraft.core.BlockPos.of(packed);
            goal.marker.route.add(new Point(dim.toString(),next.getX(),next.getY(),next.getZ()));
            if(goal.marker.route.size()>=48)break;
        }
    }
    public static void confirmedMapAccess(ResourceLocation dim, UUID world, UUID memberCity) {
        if (atlasGoal == null || !atlasGoal.dimension.equals(dim)) return;
        if (!atlasGoal.world.equals(world) || atlasGoal.kind == CityMapPanel.SelectionKind.INDUSTRY
                && com.example.examplemod.network.RequestCityDirectoryPayload.NEW.equals(memberCity)) atlasGoal = null;
    }
    public static void confirmedIndustries(ResourceLocation dim, List<com.example.examplemod.city.CityProjectSnapshot> projects) {
        if (atlasGoal != null && atlasGoal.kind == CityMapPanel.SelectionKind.INDUSTRY && atlasGoal.dimension.equals(dim)
                && projects.stream().noneMatch(project -> project.projectId().equals(atlasGoal.id))) atlasGoal = null;
    }
    static boolean hasAtlasGoal() { return atlasGoal != null; }
    static boolean serverGuidanceHidden() { return previousGuidanceHidden; }
    public static boolean canCancelNavigation() { return !JourneyPlaytest.running() && showingGuidance(); }
    public static void cancelNavigation() {
        if (JourneyPlaytest.running()) return;
        pendingChoice = null;
        if (atlasGoal != null) {
            releaseTrip();
            atlasGoal = null;
            CommunityFx.sound("map_navigation");
        } else if (!previousGuidanceHidden && !paused && (!target.isEmpty() || !activeQuest.isEmpty())) {
            request(JourneyRequestPayload.Action.PAUSE, "");
        }
    }
    private static void releaseTrip() {
        var trip=itinerary();
        var net=Minecraft.getInstance().getConnection();
        if(trip!=null && net!=null && net.hasChannel(com.example.examplemod.navigation.TripNetwork.Query.TYPE))
            PacketDistributor.sendToServer(trip.action(com.example.examplemod.navigation.TripNetwork.Action.CANCEL,0));
    }
    public static void refreshNavigation(CityMapPanel panel, ResourceLocation dim) {
        if (atlasGoal == null || panel == null || !atlasGoal.dimension.equals(dim)) return;
        UUID world = CityPublicDirectory.worldId(dim);
        if (world == null) return;
        if (!world.equals(atlasGoal.world)) { atlasGoal = null; return; }
        if (atlasGoal.kind == CityMapPanel.SelectionKind.WAYPOINT) {
            var point = atlasGoal.marker.point;
            var pin = panel.resolveSelection(atlasGoal.kind, atlasGoal.id,
                    new net.minecraft.core.BlockPos(point.x(),point.y(),point.z()));
            if (pin == null) atlasGoal = null;
            else atlasGoal.marker.title = pin.title().getString();
        }
    }
    public static Component navigationSummary() {
        if (atlasGoal != null && !atlasGoal.readable()) return tr("navigation_check");
        Marker m = displayTarget();
        if (m == null) {
            if (canCancelNavigation()) for (var q : quests) if (q.getString("Id").equals(activeQuest))
                return Component.literal(q.getString("Title")).append(chainStep());
            return Component.empty();
        }
        return Component.literal(m.title).append(" · ").append(navigationDetail(m));
    }
    private static Component chainStep() {
        if (atlasGoal != null && !JourneyPlaytest.running() && atlasGoal.totalLegs > 0)
            return Component.literal(" · ").append(tr("navigation_cargo_legs",atlasGoal.completedLegs,atlasGoal.totalLegs));
        if (atlasGoal == null && !JourneyPlaytest.running() && !chain.isEmpty() && !activeQuest.isEmpty())
            for (var c : chains) if (c.getString("Id").equals(chain)) {
                var steps = c.getList("Steps", net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < steps.size(); i++) if (steps.getString(i).equals(activeQuest))
                    return Component.literal(" · ").append(tr("navigation_quest_step", i+1, steps.size()));
            }
        return Component.empty();
    }
    static Component navigationDetail(Marker marker) {
        if(atlasGoal!=null && atlasGoal.trip!=null && marker==atlasGoal.marker)return atlasGoal.instruction;
        var player = Minecraft.getInstance().player;
        var point = marker.point;
        var path = displayPath(marker);
        var mode = !marker.route.isEmpty() ? tr("navigation_stage", marker.route.size() + 2 - path.size(),
                marker.route.size() + 1) : tr("navigation_direct");
        if (atlasGoal != null && atlasGoal.lastKnown) mode = tr("navigation_last");
        if (player == null || !player.level().dimension().location().toString().equals(point.dimension()))
            return mode.copy().append(" · ").append(tr("dimension", point.dimension())).append(chainStep());
        int blocks = (int)Math.ceil(Math.hypot(point.x() + .5 - player.getX(), point.z() + .5 - player.getZ()));
        return mode.copy().append(" · ").append(tr("navigation_distance", blocks)).append(chainStep());
    }
    private static List<CompoundTag> list(CompoundTag t,String name){var result=new ArrayList<CompoundTag>();for(var v:t.getList(name,10))result.add((CompoundTag)v);return List.copyOf(result);}
    public static void accept(JourneyStatePayload payload){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        if (connection != mc.getConnection()) { clear(); connection=mc.getConnection(); }
        var t=payload.data();
        if (t.contains("NavigationQuery")) {
            if (atlasGoal != null && !atlasGoal.apply(t)) atlasGoal = null;
            return;
        }
        var oldMarker=markers.get(target);String oldTarget=target,oldActiveQuest=activeQuest;
        objectLookupSupported = t.getInt("NavigationLookupVersion") == 1;
        var oldQuests = quests;
        canEdit=t.getBoolean("CanEdit");markers.clear();for(var raw:t.getList("Markers",10)){var m=JourneyCodec.marker((CompoundTag)raw);markers.put(m.id,m);}quests=list(t,"Quests");chains=list(t,"Chains");target=t.getString("Target");activeQuest=t.getString("ActiveQuest");chain=t.getString("Chain");paused=t.getBoolean("Paused");
        if (pendingChoice != null && net.minecraft.Util.getMillis() < choiceExpires && !paused && switch (pendingChoice.action()) {
            case TRACK_MARKER -> target.equals(pendingChoice.id());
            case TRACK_QUEST -> activeQuest.equals(pendingChoice.id());
            case START -> chain.equals(pendingChoice.id());
            case RESUME -> !activeQuest.isEmpty() || !target.isEmpty();
            default -> false;
        }) {
            releaseTrip(); atlasGoal = null; previousGuidanceHidden = false; pendingChoice = null;
        }
        detour=t.getBoolean("Detour");
        if(!target.equals(oldTarget))targetAt=net.minecraft.Util.getMillis();
        boolean newlyDone = !t.getString("Completed").isEmpty() && quests.stream().anyMatch(q -> q.getBoolean("Done")
                && oldQuests.stream().anyMatch(old -> old.getString("Id").equals(q.getString("Id")) && !old.getBoolean("Done")));
        if(newlyDone){completionMarker=quests.stream().anyMatch(q->q.getString("Id").equals(oldActiveQuest)&&q.getBoolean("Done"))?oldMarker:null;completionAt=net.minecraft.Util.getMillis();completed=t.getString("Completed");completedAt=net.minecraft.Util.getMillis();CommunityFx.sound("success");}
        ready=true;if(t.getBoolean("Open"))mc.setScreen(new JourneyScreen(mc.screen,null));
    }
    private static void clear(){atlasGoal=null;previousGuidanceHidden=objectLookupSupported=false;pendingChoice=null;markers.clear();quests=chains=List.of();target=activeQuest=chain=routeTarget=completed="";paused=ready=canEdit=detour=false;completionMarker=null;targetAt=completionAt=0;waypoint=ticks=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){connection=null;clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){clear();connection=mc.getConnection();}
        if(mc.player==null||mc.getConnection()==null)return;
        if(itinerary()!=null && !riderIn(atlasGoal.dimension)){releaseTrip();atlasGoal=null;}
        if (atlasGoal != null && atlasGoal.lease != null && !JourneyPlaytest.running()) {
            String token = atlasGoal.lease.poll(net.minecraft.Util.getMillis(), ++lookupSequence);
            if (!token.isEmpty()) request(JourneyRequestPayload.Action.STATE, token);
        }
        if(++ticks%100==1&&!ready)request(JourneyRequestPayload.Action.STATE,"");
        while(JOURNAL.consumeClick())if(mc.screen==null){
            if(JourneyPlaytest.running()) JourneyPlaytest.openGuide();
            else mc.setScreen(new JourneyScreen(null,null));
        }
        var m=markers.get(target);if(m==null)return;var path=path(m);
        String routeKey=target+":"+m.point+":"+m.route.hashCode();
        if(!routeTarget.equals(routeKey)){routeTarget=routeKey;waypoint=0;}
        while(waypoint<path.size()-1&&distance(path.get(waypoint))<3)waypoint++;
    }
    public static double distance(Point p){var mc=Minecraft.getInstance();return mc.player==null?Double.POSITIVE_INFINITY:p.distance(mc.player.level().dimension().location().toString(),mc.player.getX(),mc.player.getY(),mc.player.getZ());}
    public static List<Point> path(Marker m){var points=new ArrayList<>(m.route);points.add(m.point);return points;}
    public static List<Point> remaining(Marker m){var path=path(m);return path.subList(Math.min(waypoint,path.size()-1),path.size());}
    static Marker displayTarget() {
        if (JourneyPlaytest.running()) return JourneyPlaytest.marker();
        if (atlasGoal != null) return atlasGoal.readable() ? atlasGoal.marker : null;
        return paused || previousGuidanceHidden ? null : markers.get(target);
    }
    static List<Point> displayPath(Marker marker) {
        if (JourneyPlaytest.running()) return JourneyPlaytest.remaining(marker);
        if(atlasGoal!=null && atlasGoal.trip!=null && marker==atlasGoal.marker && !marker.route.isEmpty())return marker.route;
        return atlasGoal != null && marker == atlasGoal.marker ? List.of(marker.point) : remaining(marker);
    }
    static long displayTargetAt() {
        return JourneyPlaytest.running() ? JourneyPlaytest.targetAt() : atlasGoal != null ? atlasGoal.selectedAt : targetAt;
    }
    static Marker displayCompletion() {
        return JourneyPlaytest.running() ? JourneyPlaytest.completionMarker() : completionMarker;
    }
    static long displayCompletionAt() {
        return JourneyPlaytest.running() ? JourneyPlaytest.completionAt() : completionAt;
    }
    public static boolean showingGuidance() {
        return JourneyPlaytest.running() || atlasGoal != null || !paused && !previousGuidanceHidden && (!target.isEmpty() || !activeQuest.isEmpty());
    }
    public static boolean reduced(){CommunityFx.load();return CommunityFx.reduced || com.example.examplemod.client.map.MinimapClient.settings().reducedMotion;}
    public static String symbol(Kind kind){return switch(kind){case GUIDE->"?";case QUEST->"!";case ACTIVITY->"+";case LANDMARK->"i";};}
    public static int color(Kind kind){return switch(kind){case GUIDE->0xff78d7c1;case QUEST->0xffefc47e;case ACTIVITY->0xff89bdf0;case LANDMARK->0xffc8c5d3;};}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){JourneyHud.render(event,completed,completedAt);}
    @EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){
            event.enqueueWork(()->{
                JourneyNetwork.currentClient=CityPublicDirectory::currentConnection;
                JourneyNetwork.client=JourneyClient::accept;
                JourneyNetwork.editor=packet->{
                    var screen=Minecraft.getInstance().screen;
                    if(screen instanceof JourneyRouteEditorScreen route) route.receive(packet.data());
                    else if(screen instanceof JourneyAtlasScreen atlas) atlas.receive(packet.data());
                };
            });
        }
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(JOURNAL);}
    }
    private JourneyClient(){}
}
