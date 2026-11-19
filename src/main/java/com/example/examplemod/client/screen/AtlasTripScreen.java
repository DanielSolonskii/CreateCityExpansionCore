package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.journey.JourneyClient;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.DispatchPalette;
import com.example.examplemod.navigation.TripNetwork;
import com.example.examplemod.navigation.TripNetwork.*;
import com.example.examplemod.navigation.TripPlanner.Mode;
import com.example.examplemod.navigation.TripRef;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** An embedded tool of the existing atlas. Only the accepted server itinerary can arm Journey. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class AtlasTripScreen extends Screen {
    private static WeakReference<AtlasTripScreen> open = new WeakReference<>(null);
    private static TripRef customOrigin;
    private static UUID originWorld;
    private static int heartbeats;
    private final PolishedWorldMapScreen atlas;
    private final ResourceLocation dimension;
    private final UUID world;
    private final CityMapPanel.Selection selected;
    private final TripRef goal;
    private TripRef origin;
    private Mode mode;
    private Query query;
    private int index, scroll, lastScroll;
    private long due, sent, lastLease;
    private boolean starting, handedOff;
    private CompoundTag response;
    private String status="loading";
    private Button begin, nextOption;
    private record TextLine(FormattedCharSequence text,int paragraph,boolean first) {}
    private List<TextLine> wrapped = List.of();
    private CompoundTag wrappedResponse;
    private String wrappedStatus;
    private net.minecraft.locale.Language wrappedLanguage;
    private int wrappedIndex, wrappedWidth;
    private static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.trip."+key,args);}

    private AtlasTripScreen(PolishedWorldMapScreen atlas, CityMapPanel map, CityMapPanel.Selection selected,
                            TripRef start,TripRef goal,Mode mode) {
        super(tr("title"));this.atlas=atlas;dimension=map.dimensionId();world=CityPublicDirectory.worldId(dimension);
        this.selected=selected;this.goal=goal;this.origin=start;this.mode=mode;
    }
    public static void open(PolishedWorldMapScreen atlas, CityMapPanel map, CityMapPanel.Selection selected) {
        var world=CityPublicDirectory.worldId(map.dimensionId());
        var origin=world!=null && world.equals(originWorld) && customOrigin!=null?customOrigin:TripRef.PLAYER;
        var view=new AtlasTripScreen(atlas,map,selected,origin,JourneyClient.routeReference(selected,map.dimensionId()),Mode.MIXED);
        atlas.openAtlasTool(view);open=new WeakReference<>(view);
    }
    public static void from(PolishedWorldMapScreen atlas, CityMapPanel map, CityMapPanel.Selection selected) {
        var key=JourneyClient.routeReference(selected,map.dimensionId());
        var mc=Minecraft.getInstance();
        if(selected.kind()==CityMapPanel.SelectionKind.PLAYER)key=TripRef.PLAYER;
        if(key==null || key.kind()==TripRef.Kind.PIN){
            if(mc.player!=null)mc.player.displayClientMessage(tr("state.entrance"),true);
            return;
        }
        customOrigin=key;originWorld=CityPublicDirectory.worldId(map.dimensionId());
        if(mc.player!=null)mc.player.displayClientMessage(tr("origin_set",selected.title()),true);
    }
    public static boolean needsRecalculation(){return JourneyClient.needsTripUpdate();}
    public static void recalculate(PolishedWorldMapScreen atlas,CityMapPanel map){
        var old=JourneyClient.itinerary();
        if(old==null)return;
        var view=new AtlasTripScreen(atlas,map,null,TripRef.PLAYER,old.goal(),old.mode());
        atlas.openAtlasTool(view);open=new WeakReference<>(view);
    }
    private static boolean available(){
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null && connection.hasChannel(Query.TYPE);
    }
    private void button(int x,int y,int w,Component name,Runnable action){
        addRenderableWidget(AtlasFormStyle.button(x,y,w,20,name,action,false,false));
    }
    @Override protected void init(){
        wrappedStatus=null;
        int x=left(),w=panelWidth(),y=33,half=(w-4)/2;
        button(x,y,half,tr("mode."+mode.name().toLowerCase(Locale.ROOT)),()->{
            mode=Mode.values()[(mode.ordinal()+1)%Mode.values().length];restart();
        });
        button(x+half+4,y,w-half-4,origin.equals(TripRef.PLAYER)?tr("player"):tr("reset_origin"),()->{origin=TripRef.PLAYER;customOrigin=null;restart();});
        nextOption=addRenderableWidget(AtlasFormStyle.button(x,height-49,half,20,tr("next"),()->{
            index=(index+1)%Math.max(1,options().size());scroll=0;updateButtons();
        },false,false));
        button(x+half+4,height-49,w-half-4,tr("refresh"),this::restart);
        button(x,height-25,half,tr("back"),this::onClose);
        begin=addRenderableWidget(AtlasFormStyle.button(x+half+4,height-25,w-half-4,20,tr("begin"),()->{
            if(query==null || options().isEmpty() || starting)return;
            starting=true;status="starting";sent=Util.getMillis();send(query.action(Action.BEGIN,index));updateButtons();
        },false,true));
        updateButtons();
    }
    private int panelWidth(){return Math.max(40,Math.min(width-26,370));}
    private int left(){return (width-panelWidth())/2;}
    private void restart(){
        cancelPreview();query=null;response=null;starting=false;index=scroll=0;
        status="loading";due=Math.max(Util.getMillis()+120,sent+850);rebuildWidgets();
    }
    private ListTag options(){return response==null?new ListTag():response.getList("Options",Tag.TAG_COMPOUND);}
    private void updateButtons(){
        if(begin!=null)begin.active=!starting && !options().isEmpty();
        if(nextOption!=null){nextOption.active=!starting && options().size()>1;
            nextOption.setMessage(options().isEmpty()?tr("next"):tr("variant",index+1,options().size()));}
    }
    private static void send(Query packet){if(available())PacketDistributor.sendToServer(packet);}
    private void cancelPreview(){if(query!=null && !handedOff)send(query.action(Action.CANCEL,0));}
    @Override public void tick(){
        if(!available() || world==null || goal==null || goal.kind()==TripRef.Kind.PIN || origin.kind()==TripRef.Kind.PIN){
            status=!available()?"unsupported":"entrance";return;
        }
        var mc=Minecraft.getInstance();
        if(mc.level==null || !dimension.equals(mc.level.dimension().location()) || !world.equals(CityPublicDirectory.worldId(dimension))){
            response=null;status="dimension";updateButtons();return;
        }
        long now=Util.getMillis();
        if(query==null && now>=due){
            query=new Query(Action.PLAN,UUID.randomUUID(),world,origin,goal,mode,0);
            sent=now;lastLease=now;send(query);
        }else if(query!=null && (starting || status.equals("loading")) && now-sent>18000){
            cancelPreview();query=null;response=null;starting=false;status="timeout";due=Long.MAX_VALUE;updateButtons();
        }else if(response!=null && now-lastLease>5000){
            response=null;status="expired";starting=false;updateButtons();
        }
    }
    private boolean accepts(CompoundTag data){
        return query!=null && data.hasUUID("Request") && data.getUUID("Request").equals(query.request()) &&
                data.hasUUID("World") && data.getUUID("World").equals(world) && atlas.ownsAtlasTool(this) &&
                Minecraft.getInstance().screen==atlas && world.equals(CityPublicDirectory.worldId(dimension));
    }
    private void accept(CompoundTag packet){
        var state=packet.getString("State");lastLease=Util.getMillis();
        if(state.equals("valid"))return;
        if(state.equals("guide") && starting){
            handedOff=JourneyClient.beginTrip(query,selected,dimension,packet);
            onClose();return;
        }
        if(state.equals("guide"))return;
        if(state.equals("options") || state.equals("partial")){response=packet;index=scroll=0;}
        else response=null;
        status=state;starting=false;updateButtons();
    }
    @Override public void onClose(){atlas.closeAtlasTool();}
    @Override public void removed(){cancelPreview();if(open.get()==this)open.clear();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x>=left() && x<left()+panelWidth() && y>=55 && y<height-50 && dy!=0 && lastScroll>0){
            scroll=Math.clamp(scroll+(dy>0?-3:3),0,lastScroll);
            return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(lastScroll>0){
            int page=Math.max(1,visibleLines()-1);
            switch(key){
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> scroll=Math.max(0,scroll-page);
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> scroll=Math.min(lastScroll,scroll+page);
                case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> scroll=0;
                case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> scroll=lastScroll;
                default -> {return super.keyPressed(key,scan,modifiers);}
            }
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private int visibleLines(){return Math.max(0,(height-112)/(font.lineHeight+3));}
    private List<Component> details(){
        var lines=new ArrayList<Component>();
        if(!status.equals("options") || options().isEmpty()){
            lines.add(tr("state."+status));
            if(response!=null && response.contains("KnownTo",Tag.TAG_LONG)){
                var last=net.minecraft.core.BlockPos.of(response.getLong("KnownTo"));
                lines.add(tr("known_to",last.getX(),last.getY(),last.getZ()));
            }
            return lines;
        }
        var option=options().getCompound(index);long time=response.getLong("Now");
        lines.add(tr("between",response.getString("Origin"),response.getString("Goal")));
        lines.add(tr("duration",seconds(option.getLong("Arrival")-time),option.getInt("Rides")));
        for(var el:option.getList("Steps",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)el;
            long start=row.getLong("Start"),end=row.getLong("End");
            if(row.contains("Train")){
                lines.add(tr("wait",row.getString("From"),seconds(start-time)));
                lines.add(tr("rail",row.getString("Line"),row.getString("Train"),row.getString("From"),row.getString("Stop"),seconds(end-start)));
            }else{
                var p=net.minecraft.core.BlockPos.of(row.getLong("To"));
                lines.add(tr("foot",p.getX(),p.getY(),p.getZ(),seconds(end-start)));
            }
            time=end;
        }
        if(response.getBoolean("Limited"))lines.add(tr("bounded"));
        return lines;
    }
    private static long seconds(long ticks){return Math.max(0,(ticks+19)/20);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        int x=left(),w=panelWidth();
        AtlasFormStyle.workspace(g,font,width,height,x,w,title,true);
        AtlasFormStyle.inset(g,x,56,w,height-108);
        var language=net.minecraft.locale.Language.getInstance();
        if(response!=wrappedResponse || !status.equals(wrappedStatus) || index!=wrappedIndex || w!=wrappedWidth || language!=wrappedLanguage){
            var lines=new ArrayList<TextLine>();
            int paragraph=0;
            for(var detail:details()){
                boolean first=true;
                for(var row:font.split(detail,w-24)){
                    lines.add(new TextLine(row,paragraph,first));first=false;
                }
                paragraph++;
            }
            wrapped=List.copyOf(lines);
            wrappedResponse=response;wrappedStatus=status;wrappedIndex=index;wrappedWidth=w;wrappedLanguage=language;
        }
        int available=visibleLines();
        lastScroll=Math.max(0,wrapped.size()-available);scroll=Math.clamp(scroll,0,lastScroll);
        for(int i=scroll;i<Math.min(wrapped.size(),scroll+available);i++){
            int y=62+(i-scroll)*(font.lineHeight+3);
            var line=wrapped.get(i);
            if(line.paragraph()>=2)g.vLine(x+7,y-3,y+7,DispatchPalette.EDGE);
            if(line.first() && line.paragraph()!=1){
                g.fill(x+6,y+3,x+9,y+6,line.paragraph()==0?DispatchPalette.ACCENT:0xFFD3A363);
            }
            g.drawString(font,line.text(),x+14,y,line.paragraph()==1?DispatchPalette.MUTED:DispatchPalette.TEXT,false);
        }
        if(lastScroll>0){
            int track=height-120,thumb=Math.max(6,track*available/Math.max(1,wrapped.size()));
            int top=62+(track-thumb)*scroll/lastScroll;
            g.fill(x+w-5,top,x+w-3,top+thumb,DispatchPalette.ACCENT);
        }
        super.render(g,mx,my,partial);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){open.clear();customOrigin=null;originWorld=null;heartbeats=0;}
    @SubscribeEvent public static void tickGuidance(ClientTickEvent.Post event){
        if(++heartbeats%30!=0)return;
        var live=JourneyClient.itinerary();
        if(live!=null)send(live.action(Action.POLL,0));
        var view=open.get();
        if(view!=null && view.response!=null && view.query!=null && !view.starting)send(view.query.action(Action.POLL,0));
    }
    @EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){
            event.enqueueWork(()->TripNetwork.receiver=(connection,reply)->{
                if(!CityPublicDirectory.currentConnection(connection))return;
                var data=reply.data();
                if(!data.hasUUID("Request"))return;
                var view=open.get();
                if(view!=null && view.accepts(data)){view.accept(data);return;}
                var live=JourneyClient.itinerary();
                if(live==null || !live.request().equals(data.getUUID("Request")) || !live.world().equals(data.getUUID("World")))return;
                String state=data.getString("State");
                if(state.equals("ended") || state.equals("done")){
                    JourneyClient.endTrip(live.request());
                    var player=Minecraft.getInstance().player;
                    var feedback=com.example.examplemod.client.CommunityFx.feedback;
                    if(player!=null && !feedback.enabled(com.example.examplemod.client.CommunityFeedbackSettings.Option.DO_NOT_DISTURB) &&
                            !feedback.snoozed(System.currentTimeMillis()))
                        player.displayClientMessage(tr("state."+state),true);
                }else if(state.equals("guide")){
                    var dim=ResourceLocation.tryParse(data.getString("Dim"));
                    if(dim!=null)JourneyClient.guideTrip(live,dim,data);
                }
            });
        }
    }
}
