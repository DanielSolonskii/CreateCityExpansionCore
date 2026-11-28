package com.example.examplemod.client.screen;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.map.*;
import com.example.examplemod.projects.EngineeringPlan;
import com.example.examplemod.projects.EngineeringPlan.Node;
import com.example.examplemod.network.CityMapPeersPayload.Peer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.util.*;

/** Embedded tool. The owner keeps ticking the existing atlas session, terrain pipeline and camera. */
final class AtlasPlanEditor extends Screen {
    enum Page { DRAW, DETAILS, LIBRARY, PROJECT }
    private static final int INK=0xFFE9E6D5, BLUE=0xFF7EE7FF;
    final CityMapPanel map;
    private final PlanDraftStore store;
    private final SharedPlanPanel shared;
    private final Runnable close;
    private PlanHistory history, personal;
    private boolean projectDraft, dirty, confirmDelete;
    private Page page=Page.DRAW;
    private int split, contentOffset, selected=-1, dragging=-1, listOffset;
    private Node preview;
    private String notice="";
    private EngineeringPlan surveyed;
    private List<Peer> territorySnapshot;
    private PlanSurvey.Territories territories;
    private PlanSurvey.Profile profile;
    private long nextProfile;
    private double length, area;
    private final AtlasHoverDelay labelDelay=new AtlasHoverDelay(350);
    private Component clippedLabel;
    private int mouseX,mouseY;
    private boolean complete;
    private EditBox nameBox, noteBox;

    AtlasPlanEditor(CityMapPanel map, PlanDraftStore store, Runnable close) {
        super(text("title"));
        this.map=map; this.store=store; this.close=close;
        this.shared=new SharedPlanPanel(this,store.sharedDrafts());
        var saved=store.selected();
        history=new PlanHistory(saved==null?blank(EngineeringPlan.Kind.LINE):saved);
        if(store.unreadable)notice="damaged";
    }
    private EngineeringPlan blank(EngineeringPlan.Kind kind) {
        return new EngineeringPlan(UUID.randomUUID(),map.dimensionId(),kind,text("untitled").getString(),"",List.of());
    }
    static Component text(String key,Object... args) {
        return Component.translatable("gui.createcityexpansioncore.atlas.plan."+key,args);
    }
    void sharedRefresh(){
        if(page!=Page.PROJECT)return;
        int focused=children().indexOf(getFocused());
        rebuildWidgets();
        if(focused>=0 && focused<children().size())setFocused(children().get(focused));
    }
    void sharedPage(){page=Page.PROJECT;rebuildWidgets();}
    Button sharedButton(int x,int y,int w,Component label,Runnable run){
        return addRenderableWidget(AtlasFormStyle.button(x,y,w,18,label,run,false,false));
    }
    EngineeringPlan current() {return history.current();}
    boolean projectDraft() {return projectDraft;}
    void loadProjectDraft(EngineeringPlan value) {
        if(!map.dimensionId().equals(value.dimension()))return;
        if(!projectDraft)personal=history;
        history=new PlanHistory(value);projectDraft=true;resetSelection();showDetails();
    }
    void discardProjectDraft() {
        if(!projectDraft)return;
        history=personal;personal=null;projectDraft=false;resetSelection();showDetails();
    }
    private void resetSelection(){
        selected=dragging=-1;preview=null;surveyed=null;profile=null;territories=null;territorySnapshot=null;
        notice="";confirmDelete=false;
    }
    void showDetails(){page=Page.DETAILS;rebuildWidgets();}
    @Override protected void init() {
        split=width-Math.clamp(width*2/5,148,218);
        nameBox=null;noteBox=null;
        int x=split+4,w=width-x-4;
        var pages=Page.values();
        int required=0;
        for(Page item:pages)required=Math.max(required,font.width(text("page."+item.name().toLowerCase(Locale.ROOT)))+12);
        int columns=(w-6)/pages.length>=required?pages.length:2;
        int tabWidth=(w-(columns-1)*2)/columns;
        contentOffset=columns<pages.length?20:0;
        for(Page item:Page.values()) {
            var b=addRenderableWidget(AtlasFormStyle.button(x+(item.ordinal()%columns)*(tabWidth+2),19+(item.ordinal()/columns)*20,tabWidth,18,
                    text("page."+item.name().toLowerCase(Locale.ROOT)),()->{
                page=item;confirmDelete=false;rebuildWidgets();
            },item==page,false));
            b.active=item!=page;
        }
        switch(page) {
            case PROJECT -> shared.widgets(x,43+contentOffset,w,height-44);
            case DRAW -> {
                int half=(w-2)/2;
                button("undo",x,43+contentOffset,half,()->rewind(false));
                button("redo",x+half+2,43+contentOffset,half,()->rewind(true));
                button("remove_node",x,65+contentOffset,w,this::removeNode);
            }
            case DETAILS -> {
                nameBox=field("name",x,52+contentOffset,w,EngineeringPlan.MAX_NAME,current().name());
                noteBox=field("note",x,84+contentOffset,w,EngineeringPlan.MAX_NOTE,current().note());
                nameBox.setResponder(v->describe(v,current().note()));
                noteBox.setResponder(v->describe(current().name(),v));
            }
            case LIBRARY -> {
                int thirdW=(w-4)/3;
                for(var kind:EngineeringPlan.Kind.values())
                    button("kind."+kind.name().toLowerCase(Locale.ROOT),x+kind.ordinal()*(thirdW+2),47+contentOffset,thirdW,()->create(kind)).active=!projectDraft;
                int rows=libraryRows();
                var all=store.all();
                listOffset=Math.clamp(listOffset,0,Math.max(0,all.size()-rows));
                for(int i=0;i<rows && i+listOffset<all.size();i++) {
                    var doc=all.get(listOffset+i);
                    addRenderableWidget(AtlasFormStyle.button(x,69+contentOffset+i*19,w,18,Component.literal(doc.name().isBlank()?"…":doc.name()),
                            ()->open(doc),false,false)).active=!projectDraft;
                }
                int half=(w-2)/2;
                button("prev",x,69+contentOffset+rows*19,half,()->pageLibrary(-rows)).active=listOffset>0;
                button("next",x+half+2,69+contentOffset+rows*19,half,()->pageLibrary(rows)).active=listOffset+rows<all.size();
                button(confirmDelete?"confirm_delete":"delete",x,90+contentOffset+rows*19,w,this::delete).active=!projectDraft && store.selected()!=null;
            }
        }
        int half=(w-2)/2;
        button("save",x,height-22,half,()->save(true)).active=!projectDraft && !store.unreadable;
        button("back",x+half+2,height-22,half,this::onClose);
    }
    private int libraryRows(){return Math.clamp((height-contentOffset-156)/19,1,8);}
    private Button button(String key,int x,int y,int w,Runnable run) {
        return addRenderableWidget(AtlasFormStyle.button(x,y,w,18,text(key),run,false,key.equals("save")));
    }
    private EditBox field(String key,int x,int y,int w,int max,String value) {
        var box=AtlasFormStyle.field(font,x,y,w,17,text(key));box.setMaxLength(max);box.setValue(value);
        box.setFilter(v->v.chars().noneMatch(c->Character.isISOControl(c)||c==0x00a7));return addRenderableWidget(box);
    }
    private void describe(String name,String note) { change(current().describe(name,note)); }
    private void change(EngineeringPlan value) {
        if(history.change(value)) {if(!projectDraft)dirty=true;notice="";}
    }
    private void rewind(boolean forward) {
        if(forward?history.redo():history.undo()){
            selected=-1;if(!projectDraft)dirty=true;notice=forward?"redone":"undone";rebuildWidgets();
        }
    }
    private void removeNode() {
        if(selected<0 || selected>=current().nodes().size()){notice="pick_node";return;}
        var nodes=new ArrayList<>(current().nodes());nodes.remove(selected);change(current().geometry(nodes));selected=-1;
    }
    private boolean save(boolean feedback) {
        if(projectDraft)return true;
        if(!store.put(current())) {notice="limit";return false;}
        try {store.save();dirty=false;if(feedback){notice="saved";CommunityFx.sound("map_save");}return true;}
        catch(IOException e){notice=store.unreadable?"damaged":"save_failed";return false;}
    }
    private void create(EngineeringPlan.Kind kind) {
        if(projectDraft || dirty && !save(false))return;
        if(store.all().size()>=PlanDraftStore.LIMIT){notice="limit";return;}
        history=new PlanHistory(blank(kind));dirty=true;resetSelection();page=Page.DRAW;rebuildWidgets();
    }
    private void open(EngineeringPlan doc) {
        if(projectDraft || dirty && !save(false))return;
        store.choose(doc.id());history=new PlanHistory(doc);dirty=true;resetSelection();page=Page.DRAW;rebuildWidgets();
    }
    private void delete() {
        if(projectDraft || store.selected()==null)return;
        if(!confirmDelete){confirmDelete=true;rebuildWidgets();return;}
        store.remove(current().id());
        try {store.save();dirty=false;history=new PlanHistory(store.selected()==null?blank(EngineeringPlan.Kind.LINE):store.selected());
            resetSelection();rebuildWidgets();}
        catch(IOException e){notice="save_failed";confirmDelete=false;}
    }
    private void pageLibrary(int delta){listOffset+=delta;rebuildWidgets();}
    @Override public void onClose() {
        if(projectDraft && !shared.saveLocal())return;
        shared.close();
        if(!dirty || save(false))close.run();
    }
    void detach(){shared.close();if(dirty)save(false);}
    @Override public void tick() {
        shared.tick(page==Page.PROJECT);
        var doc=current();
        boolean changed=surveyed!=doc;
        var peers=map.planningTerritories();
        if(changed || peers!=territorySnapshot) {
            territorySnapshot=peers;territories=new PlanSurvey.Territories(doc,peers);
        }
        if(territories!=null && !territories.done)territories.advance();
        long now=Util.getMillis();
        if(changed || now>=nextProfile) {
            profile=PlanSurvey.profile(doc,map::planningHeight);nextProfile=now+1000;
            if(changed){length=doc.length();area=doc.area();complete=doc.complete();}
            surveyed=doc;
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partialTick) {
        mouseX=mx; mouseY=my; clippedLabel=null;
        g.fill(0,0,width,height,0xFF20272C);
        map.setChromeAvoidRight(split);map.setProjectFocus(false,false);map.setPrecisionInput(true);
        map.setVisualOverlay(null);
        map.render(g,3,3,Math.max(1,split-6),Math.max(1,height-6),-10000,-10000);
        g.enableScissor(3,3,Math.max(4,split-3),height-3);
        try {g.drawManaged(()->drawGeometry(g));} finally {g.disableScissor();}
        int x=split+5,w=width-x-4;
        WorldMapChrome.dispatchPanel(g,split,1,width-split-1,height-2);
        g.drawString(font,text(projectDraft?"shared_title":"title"),x,5,BLUE,false);
        if(page==Page.DETAILS) {
            g.drawString(font,text("name"),x,41+contentOffset,INK,false);g.drawString(font,text("note"),x,73+contentOffset,INK,false);
            drawSurvey(g,x,106+contentOffset,w,height-40);
        } else if(page==Page.DRAW) {
            int y=86+contentOffset;
            g.drawString(font,WorldMapChrome.fit(font,text("nodes",current().nodes().size(),EngineeringPlan.MAX_NODES),w),x,y,INK,false);
            y+=12;Component hint=text("draw_hint");g.drawWordWrap(font,hint,x,y,w,INK);
            drawSurvey(g,x,y+font.wordWrapHeight(hint,w)+6,w,height-40);
        } else if(page==Page.PROJECT) {
            if(height>=170+contentOffset)g.drawWordWrap(font,shared.info(),x,125+contentOffset,w,INK);
        } else g.drawString(font,text("create"),x,37+contentOffset,INK,false);
        if(!notice.isBlank())label(g,text(notice),x,height-35,w,BLUE);
        else if(page!=Page.PROJECT && !shared.status.isBlank())
            label(g,shared.info(),x,height-35,w,BLUE);
        super.render(g,mx,my,partialTick);
        if(clippedLabel!=null && (dragging>=0 || map.isMapDragging() || pointerHeld())) clippedLabel=null;
        var tooltip=labelDelay.sample(clippedLabel,Util.getMillis());
        if(tooltip!=null) MapPresentationDraw.foreground(g,350,1,()->g.renderTooltip(font,font.split(tooltip,Math.clamp(width-24,1,240)),mx,my));
    }
    private boolean pointerHeld() {
        long window=minecraft.getWindow().getWindow();
        for(int button=0;button<=2;button++) if(GLFW.glfwGetMouseButton(window,button)==GLFW.GLFW_PRESS)return true;
        return false;
    }
    private void label(GuiGraphics g,Component full,int x,int y,int width,int color) {
        g.drawString(font,WorldMapChrome.fit(font,full,width),x,y,color,false);
        if(mouseX>=x && mouseX<x+width && mouseY>=y && mouseY<y+font.lineHeight && font.width(full)>width) clippedLabel=full;
    }
    private void drawSurvey(GuiGraphics g,int x,int y,int w,int end) {
        if(profile==null || y+10>end)return;
        String metric=current().kind()==EngineeringPlan.Kind.AREA?(complete?"area":"invalid_area"):"length";
        label(g,text(metric,format(metric.equals("area")?area:length)),x,y,w,INK);y+=12;
        if(y+10>end)return;
        String hits=territories==null || territories.hits.isEmpty()?text("none").getString():String.join(", ",territories.hits);
        Component crossed=text(territories!=null && !territories.done?"examining":"territories",hits);
        label(g,crossed,x,y,w,INK);y+=12;
        if(y+40>end)return;
        int base=y+24;
        g.fill(x,y,x+w,base,0xFF141C22);
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE,known=0;
        boolean stale=false;
        for(var sample:profile.samples())if(sample.height()!=null){
            int h=sample.height().y();low=Math.min(low,h);high=Math.max(high,h);known++;stale|=sample.height().stale();
        }
        int i=0;
        for(var sample:profile.samples()){
            int px=x+1+(w-3)*i++/Math.max(1,profile.samples().size()-1);
            if(sample.height()==null){g.fill(px,base-2,px+1,base,0xFF73818A);continue;}
            int py=base-4-(sample.height().y()-low)*17/Math.max(1,high-low);
            g.fill(px,py,px+2,py+2,sample.height().stale()?0xFFE7B881:BLUE);
        }
        Component sampling=text("sample",format(profile.step()),known,profile.samples().size());
        label(g,sampling,x,base+2,w,INK);
        if(base+22<=end)label(g,text(known==0?"unknown":stale?"cached":"checked"),x,base+13,w,INK);
        if(base+33<=end && known>0)label(g,text("range",low,high),x,base+24,w,INK);
    }
    private static String format(double n){return String.format(Locale.ROOT,"%.1f",n);}
    private Node node(int i) {return i==dragging && preview!=null?preview:current().nodes().get(i);}
    private void drawGeometry(GuiGraphics g) {
        var doc=current();
        for(int i=0;i<doc.edges();i++) {
            var a=node(i);var b=node((i+1)%doc.nodes().size());
            var seg=MapLineClip.clip(sx(a),sz(a),sx(b),sz(b),2,2,split-2,height-2);
            if(seg==null)continue;
            double dx=seg.x2()-seg.x1(), dy=seg.y2()-seg.y1(),len=Math.hypot(dx,dy);
            if(len<1)continue;
            g.pose().pushPose();
            try {
                g.pose().translate(seg.x1(),seg.y1(),0);
                g.pose().mulPose(Axis.ZP.rotation((float)Math.atan2(dy,dx)));
                for(int d=0;d<len;d+=10)g.fill(d,-1,Math.min(d+6,(int)Math.ceil(len)),1,BLUE);
            } finally {g.pose().popPose();}
        }
        for(int i=0;i<doc.nodes().size();i++){
            int x=sx(node(i)),y=sz(node(i));
            if(x<0 || x>split || y<0 || y>height)continue;
            g.fill(x-3,y-3,x+4,y+4,0xFF193542);g.renderOutline(x-3,y-3,7,7,i==selected?0xFFFFFFFF:BLUE);
        }
    }
    private int sx(Node n){return map.worldToScreenX(n.x()+.5);}
    private int sz(Node n){return map.worldToScreenY(n.z()+.5);}
    private boolean canvas(double x,double y){return x>=3 && x<split-3 && y>=3 && y<height-3;}
    private Node cursor(double x,double y){
        return new Node((int)Math.clamp(Math.floor(map.screenToWorldX(x)),-30_000_000,30_000_000),
                (int)Math.clamp(Math.floor(map.screenToWorldZ(y)),-30_000_000,30_000_000));
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(!canvas(x,y))return super.mouseClicked(x,y,button);
        setFocused(null);
        if(button!=0 || page!=Page.DRAW)return true;
        var nodes=new ArrayList<>(current().nodes());
        for(int i=nodes.size()-1;i>=0;i--) {
            if(Math.hypot(sx(nodes.get(i))-x,sz(nodes.get(i))-y)<=6){selected=dragging=i;preview=null;return true;}
        }
        if(current().kind()==EngineeringPlan.Kind.POINT)nodes.clear();
        if(nodes.size()>=EngineeringPlan.MAX_NODES){notice="limit";return true;}
        nodes.add(cursor(x,y));selected=nodes.size()-1;change(current().geometry(nodes));return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(button==0 && dragging>=0){if(canvas(x,y))preview=cursor(x,y);return true;}
        if((button==1 || button==2) && canvas(x,y)){
            map.restoreView(map.centerX()-dx/map.zoom(),map.centerZ()-dy/map.zoom(),map.zoom());return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(button==0 && dragging>=0){
            if(preview!=null){
                var nodes=new ArrayList<>(current().nodes());nodes.set(dragging,preview);change(current().geometry(nodes));
            }
            dragging=-1;preview=null;return true;
        }
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(canvas(x,y))return map.mouseScrolled(x,y,dy);
        if(page==Page.LIBRARY && dy!=0){pageLibrary(dy>0?-1:1);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        if(hasControlDown() && key==GLFW.GLFW_KEY_S){save(true);return true;}
        if(hasControlDown() && (key==GLFW.GLFW_KEY_Z || key==GLFW.GLFW_KEY_Y)){rewind(key==GLFW.GLFW_KEY_Y || hasShiftDown());return true;}
        if(page==Page.DRAW && key==GLFW.GLFW_KEY_DELETE){removeNode();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
