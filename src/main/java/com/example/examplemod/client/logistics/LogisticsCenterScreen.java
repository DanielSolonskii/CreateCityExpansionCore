package com.example.examplemod.client.logistics;

import com.example.examplemod.cargo.center.CenterRequest;
import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Standalone dispatch workspace. Selection has no side effects; forms confirm every mutation. */
public final class LogisticsCenterScreen extends Screen implements CenterResponseScreen {
    private static final String[] IDS={"dashboard","map","trains","lines","points","warehouses","trade","shipments","access"};
    private boolean networkMode,openNetwork;
    private int section,sidebar,scroll,page,detailScroll,detailContentHeight,navStep;
    private UUID selected,lastRequest;
    private CompoundTag state=new CompoundTag();
    private long sent,received,searchChanged,sectionChanged=net.minecraft.Util.getMillis();
    private boolean pending,reverse,onlyProblems,readTimedOut;
    private EditBox search;
    private ActionButton previousPage,nextPage,actionsButton,sortButton,problemButton,mapLaunchButton,networkButton;
    private final List<ActionButton> dashboardButtons=new ArrayList<>();
    private String query="",lastResult="";
    public LogisticsCenterScreen(){super(tr("title"));}
    public void focusObject(String kind,UUID id){query="";reverse=onlyProblems=false;page=scroll=detailScroll=0;state=new CompoundTag();received=0;section=switch(kind){case "train"->2;case "line"->3;case "point","station"->4;case "warehouse"->5;case "offer"->6;case "shipment"->7;default->0;};selected=id;if(section==2||section==3||section==4||section==8)networkMode=true;}
    @Override protected void init(){
        var layout=CenterLayout.of(width,height);sidebar=layout.sidebar();navStep=layout.navigationStep();
        addRenderableWidget(new ActionButton(7,40,sidebar-14,18,tr(networkMode?"mode.network":"mode.player"),()->{networkMode=!networkMode;selectSection(0);rebuildWidgets();})).setTooltip(Tooltip.create(tr("mode.help")));
        search=new EditBox(font,sidebar+18,60,Math.max(60,width-sidebar-142),20,tr("search"));
        search.setHint(tr("search"));search.setMaxLength(96);search.setValue(query);
        search.setResponder(value->{query=value;searchChanged=net.minecraft.Util.getMillis();});addRenderableWidget(search);
        addRenderableWidget(new ActionButton(width-112,60,28,20,net.minecraft.network.chat.Component.literal("↻"),this::refresh)).setTooltip(Tooltip.create(tr("refresh")));
        sortButton=addRenderableWidget(new ActionButton(width-78,60,28,20,net.minecraft.network.chat.Component.literal("↕"),()->{reverse=!reverse;scroll=0;}));sortButton.setTooltip(Tooltip.create(tr("sort")));
        problemButton=addRenderableWidget(new ActionButton(width-44,60,28,20,net.minecraft.network.chat.Component.literal("!"),()->{onlyProblems=!onlyProblems;scroll=0;}));problemButton.setTooltip(Tooltip.create(tr("problems")));
        var r=layout.map();addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),tr("map"),this::openMap));
        r=layout.previous();previousPage=addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),net.minecraft.network.chat.Component.literal("‹"),()->{if(page>0){page--;scroll=0;refresh();}}));
        r=layout.next();nextPage=addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),net.minecraft.network.chat.Component.literal("›"),()->{if((page+1)*32<state.getInt("Total")){page++;scroll=0;refresh();}}));
        previousPage.active=page>0;nextPage.active=(page+1)*32<state.getInt("Total");
        r=layout.actions();actionsButton=addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),tr("actions"),()->minecraft.setScreen(new CenterActionsScreen(this,state))));
        mapLaunchButton=addRenderableWidget(new ActionButton(sidebar+18,100,Math.min(220,width-sidebar-36),26,tr("map_title"),this::openMap));
        actionsButton.setTooltip(Tooltip.create(tr("home.actions_help")));
        networkButton=addRenderableWidget(new ActionButton(sidebar+18,60,Math.min(160,width-sidebar-142),20,tr("visual.network"),()->minecraft.setScreen(new CenterNetworkScreen(this))));
        dashboardButtons.clear();
        String[] tasks={"buy","send","track","network"};int[] targets={6,5,7,3};
        for(int i=0;i<tasks.length;i++){final int target=targets[i];var box=dashboardCard(i);var button=addRenderableWidget(new ActionButton(box.x(),box.y(),box.width(),24,tr("home."+tasks[i]),()->{if(target==3){networkMode=true;selectSection(target);openNetwork=true;rebuildWidgets();}else selectSection(target);}));button.setTooltip(Tooltip.create(tr("home."+tasks[i]+".hint")));dashboardButtons.add(button);}
        updateControls();
        refresh();
    }
    private CenterLayout.Rect dashboardCard(int index){int area=width-sidebar-36,columns=area>=400?4:2,gap=10,w=(area-gap*(columns-1))/columns;int h=Math.min(84,Math.max(34,(height-164)/2));return new CenterLayout.Rect(sidebar+18+index%columns*(w+gap),90+index/columns*(h+gap),w,h);}
    private void updateControls(){boolean list=section>=2;networkButton.visible=section==3;search.visible=list&&section!=3;search.active=list;if(!list&&search.isFocused())setFocused(null);mapLaunchButton.visible=section==1;sortButton.visible=list;problemButton.visible=list;previousPage.visible=nextPage.visible=list&&state.getInt("Total")>32;actionsButton.visible=list;actionsButton.active=!pending&&!state.getList("Forms",10).isEmpty();for(var button:dashboardButtons)button.visible=section==0;}
    private void request(CompoundTag q){q.putBoolean("CenterV2",true);q.putUUID("Request",UUID.randomUUID());q.putString("Section",IDS[section]);q.putString("Search",query);q.putInt("Page",page);if(selected!=null)q.putUUID("Selected",selected);lastRequest=q.getUUID("Request");PacketDistributor.sendToServer(new CenterRequest(q));sent=net.minecraft.Util.getMillis();pending=true;readTimedOut=false;}
    private void refresh(){if(minecraft==null||minecraft.player==null)return;var q=new CompoundTag();q.putString("Action","VIEW");if(state.contains("Hash"))q.putInt("Hash",state.getInt("Hash"));request(q);}
    UUID mutate(String action,CompoundTag values){return mutate(action,values,null);}
    UUID searchStock(String value){var q=new CompoundTag();q.putString("Action","VIEW");q.putString("StockSearch",value);request(q);return lastRequest;}
    CompoundTag snapshot(){return state.copy();}
    void selectForDetails(UUID id){selected=id;detailScroll=0;refresh();}
    UUID mutate(String action,CompoundTag values,UUID quote){var q=new CompoundTag();q.putString("Action",action);q.putString("Kind",state.getCompound("SelectedRow").getString("Kind"));q.putBoolean("Confirmed",true);q.put("Values",values.copy());if(quote!=null)q.putUUID("QuoteToken",quote);request(q);return lastRequest;}
    public void accept(CompoundTag data){
        if(data.hasUUID("Request")&&!data.getUUID("Request").equals(lastRequest)&&!data.getBoolean("Open"))return;
        if(!data.getString("Section").equals(IDS[section])&&!data.getBoolean("Open"))return;
        if(!data.getBoolean("Unchanged")){state=com.example.examplemod.cargo.center.CenterDelta.apply(state,data);page=state.getInt("Page");}else if(data.contains("Hash"))state.putInt("Hash",data.getInt("Hash"));
        received=net.minecraft.Util.getMillis();pending=false;readTimedOut=false;if(!data.getString("Result").equals("ok")||data.getBoolean("Mutation"))lastResult=result(data.getString("Result"));else lastResult="";
        if(previousPage!=null){updateControls();previousPage.active=page>0;nextPage.active=(page+1)*32<state.getInt("Total");}
        if(openNetwork&&section==3&&minecraft.screen==this){openNetwork=false;minecraft.setScreen(new CenterNetworkScreen(this));}
    }
    @Override public void tick(){super.tick();long now=net.minecraft.Util.getMillis();if(pending&&CenterPresentation.timedOut(sent,now)){pending=false;readTimedOut=true;lastResult=text("timeout");}if(searchChanged>0&&now-searchChanged>350){searchChanged=0;page=0;scroll=0;refresh();}else if(!pending&&!readTimedOut&&now-sent>3000)refresh();if(actionsButton!=null)actionsButton.active=!pending&&!state.getList("Forms",10).isEmpty();}
    private List<CompoundTag> rows(){var list=new ArrayList<CompoundTag>();for(Tag raw:state.getList("Rows",10)){var row=(CompoundTag)raw;if(onlyProblems&&!CenterPresentation.problem(row.getString("Status")))continue;list.add(row);}if(reverse)Collections.reverse(list);return list;}
    private void selectSection(int next){openNetwork=false;query="";search.setValue("");searchChanged=0;reverse=onlyProblems=false;section=next;selected=null;scroll=page=detailScroll=0;state=new CompoundTag();received=0;lastResult="";sectionChanged=net.minecraft.Util.getMillis();updateControls();refresh();}
    private int[] navigation(){return networkMode?new int[]{0,2,3,4,5,8,1,6,7}:new int[]{0,6,5,7,1};}
    private int detailWidth(){return width>=720?Math.min(300,(width-sidebar)/3):0;}
    private void openMap(){LogisticsMapOverlay.open(this,state.getCompound("SelectedRow"));}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fillGradient(0,0,width,height,BG,0xff132329);g.fill(0,0,sidebar,height,0xff0c161b);g.vLine(sidebar,0,height,EDGE);
        g.drawString(font,"CCEC",17,16,TEAL,false);g.drawString(font,fit(text("control_center"),sidebar-28),17,28,TEXT,false);
        int[] nav=navigation();for(int n=0;n<nav.length;n++){int i=nav[n];int y=65+n*navStep;boolean active=i==section,hover=mx>=7&&mx<sidebar-7&&my>=y-5&&my<y+navStep-7;if(active||hover){surface(g,7,y-5,sidebar-14,navStep-2,active?0xff1d3937:0xff172a30);if(active)g.fill(7,y-3,9,y+navStep-9,TEAL);}icon(g,16,y-1,i,active?TEAL:MUTED);g.drawString(font,fit(text("tab."+IDS[i]),sidebar-46),34,y+2,active?TEXT:MUTED,false);}
        int left=sidebar+18,area=width-left-18,dw=detailWidth(),listWidth=area-(dw==0?0:dw+12),bottom=height-64;
        g.drawString(font,"CCEC / "+text("dispatch"),left,15,MUTED,false);
        g.pose().pushPose();g.pose().translate(left,31,0);g.pose().scale(1.35f,1.35f,1);g.drawString(font,fit(text("tab."+IDS[section]),(int)(area/1.35)),0,0,TEXT,false);g.pose().popPose();
        if(section>=2)g.drawString(font,fit(net.minecraft.network.chat.Component.translatable("ccec.center.count",state.getInt("Total")).getString(),area),left,47,MUTED,false);
        if(section==0)renderDashboard(g,left,area,bottom,mx,my);
        else if(section==1){g.enableScissor(left,135,left+area,bottom);g.drawWordWrap(font,tr("map_hint"),left,137,area,MUTED);g.disableScissor();}
        else{
            var rows=rows();scroll=Math.min(scroll,CenterPresentation.scrollLimit(rows.size()*60,bottom-96));
            g.enableScissor(left,94,left+listWidth,bottom);
            for(int i=0;i<rows.size();i++){var row=rows.get(i);int y=96+i*60-scroll;if(y+54<94||y>bottom)continue;boolean picked=row.getUUID("Id").equals(selected),hover=mx>=left&&mx<left+listWidth&&my>=y&&my<y+53;int card=picked?0xff203e39:hover?0xff1b3037:PANEL;surface(g,left,y,listWidth,53,card);if(picked)g.fill(left,y+3,left+2,y+50,TEAL);icon(g,left+11,y+11,section,picked?TEAL:MUTED);int sw=Math.min(92,Math.max(30,listWidth/3));g.drawString(font,fit(name(row),listWidth-sw-46),left+31,y+11,TEXT,false);String status=value(row.getString("Status"));g.drawString(font,fit(status,sw),left+listWidth-sw-8,y+11,CenterPresentation.problem(row.getString("Status"))?BRASS:TEAL,false);g.drawString(font,fit(summary(row),listWidth-78),left+31,y+32,MUTED,false);surface(g,left+listWidth-37,y+28,29,18,hover?0xff2c5148:0xff1d3036);g.drawCenteredString(font,"…",left+listWidth-23,y+33,TEAL);}
            if(rows.isEmpty()){surface(g,left,96,listWidth,Math.min(78,Math.max(40,bottom-96)),PANEL);String key=readTimedOut?"timeout":received==0?"loading":onlyProblems?"filtered_empty":!query.isBlank()?"search_empty":state.getBoolean("Implemented")?"empty":"unavailable";g.drawWordWrap(font,tr(key),left+14,110,listWidth-28,MUTED);}
            g.disableScissor();
            if(dw>0)renderDetails(g,left+listWidth+12,96,dw,bottom-96);
        }
        g.fill(sidebar+1,height-28,width,height,BG);g.hLine(left,width-18,height-29,EDGE);
        if(pending){int progress=(int)((net.minecraft.Util.getMillis()%1100)/1100.0*Math.max(1,area-30));g.fill(left+progress,height-29,Math.min(width-18,left+progress+30),height-28,TEAL);}
        String message=!lastResult.isEmpty()?lastResult:pending?text("sync"):received==0?text("loading"):text("fresh");g.drawString(font,fit(message,area),left,height-17,readTimedOut?BRASS:MUTED,false);
        if(state.getInt("Total")>32&&area>340)g.drawString(font,(page+1)+" / "+((state.getInt("Total")+31)/32),left+123,height-47,MUTED,false);
        super.render(g,mx,my,dt);
        if(section>=2&&my>=82&&my<=94&&mx>=left)g.renderTooltip(font,tr("section_hint."+IDS[section]),mx,my);
        if(mx>=7&&mx<sidebar-7&&my>=60){int n=(my-60)/navStep;if(n>=0&&n<nav.length)g.renderTooltip(font,tr("tab."+IDS[nav[n]]),mx,my);}
        if(my>height-28&&mx>sidebar&&!lastResult.isEmpty())g.renderTooltip(font,net.minecraft.network.chat.Component.literal(lastResult),mx,my);
    }
    private void renderDashboard(GuiGraphics g,int left,int area,int bottom,int mx,int my){
        g.drawString(font,fit(text(networkMode?"home.network_intro":"home.intro"),Math.max(30,area-105)),left,66,MUTED,false);
        String[] tasks={"buy","send","track","network"};
        int lastBottom=0;
        for(int i=0;i<tasks.length;i++){
            var box=dashboardCard(i);lastBottom=Math.max(lastBottom,box.bottom());
            if(box.height()>44){surface(g,box.x(),box.y()+26,box.width(),box.height()-26,PANEL);
                g.enableScissor(box.x()+6,box.y()+29,box.right()-6,box.bottom()-3);
                g.drawWordWrap(font,tr("home."+tasks[i]+".hint"),box.x()+8,box.y()+33,box.width()-16,MUTED);g.disableScissor();}
        }
        int y=lastBottom+18;
        if(y+48>bottom)return;
        g.drawString(font,text("home.available"),left,y,MUTED,false);y+=18;
        String[] names={"TrainCount","PointCount","WarehouseCount","ShipmentCount"};int[] symbols={2,4,5,7};
        int columns=area>=400?4:2,cw=(area-10*(columns-1))/columns;
        for(int i=0;i<names.length;i++){int x=left+i%columns*(cw+10),rowY=y+i/columns*30;if(rowY+24>bottom)continue;
            icon(g,x,rowY,symbols[i],TEAL);String count=received==0?"—":Integer.toString(state.getInt(names[i]));
            g.drawString(font,fit(count+"  "+text("metric."+names[i]),cw-22),x+22,rowY+3,TEXT,false);}
        int hintY=y+((4+columns-1)/columns)*30+12;
        if(hintY+36<bottom){g.hLine(left,left+area,hintY-8,EDGE);g.drawWordWrap(font,tr("home.guide"),left,hintY,area,MUTED);}
    }
    private void renderDetails(GuiGraphics g,int x,int top,int w,int h){
        surface(g,x,top,w,h,PANEL);var row=state.getCompound("SelectedRow");if(row.isEmpty())detailScroll=0;g.enableScissor(x+8,top+8,x+w-8,top+h-8);int y=top+14-detailScroll;
        if(row.isEmpty())g.drawWordWrap(font,tr("select_hint"),x+14,y,w-28,MUTED);
        else{var title=net.minecraft.network.chat.Component.literal(name(row));g.drawWordWrap(font,title,x+14,y,w-28,TEAL);y+=font.wordWrapHeight(title,w-28)+16;
            for(var field:fields(row)){icon(g,x+14,y,field.icon(),MUTED);var label=net.minecraft.network.chat.Component.literal(field.label());g.drawWordWrap(font,label,x+33,y,w-47,MUTED);y+=font.wordWrapHeight(label,w-47)+5;var value=net.minecraft.network.chat.Component.literal(field.value());g.drawWordWrap(font,value,x+14,y,w-28,TEXT);y+=font.wordWrapHeight(value,w-28)+15;}
        }
        detailContentHeight=y+detailScroll-top+14;detailScroll=Math.min(detailScroll,CenterPresentation.scrollLimit(detailContentHeight,h-16));g.disableScissor();
        if(detailContentHeight>h-16){int track=h-16,thumb=Math.max(12,track*track/detailContentHeight),sy=top+8+(track-thumb)*detailScroll/Math.max(1,detailContentHeight-track);g.fill(x+w-4,sy,x+w-2,sy+thumb,TEAL);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);
        if(button==0&&x<sidebar&&y>=60){int next=(int)((y-60)/navStep);if(next>=0&&next<navigation().length){selectSection(navigation()[next]);return true;}}
        if(section==1&&x>sidebar&&y>=100&&y<190){openMap();return true;}
        int right=width-18-(detailWidth()==0?0:detailWidth()+12);if(section>=2&&x>=sidebar+18&&x<right&&y>=96&&y<height-64){int index=(int)((y-96+scroll)/60);var rows=rows();if(index>=0&&index<rows.size()&&(y-96+scroll)%60<53){var row=rows.get(index);if(x>=right-37&&x<right-8&&(y-96+scroll)%60>=28&&(y-96+scroll)%60<46){minecraft.setScreen(new CenterDetailsScreen(this,row));return true;}selected=row.getUUID("Id");detailScroll=0;if(Set.of("warehouse","shipment","line").contains(row.getString("Kind")))minecraft.setScreen(new CenterDetailsScreen(this,row));else refresh();return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!search.isFocused()&&key==257&&!state.getCompound("SelectedRow").isEmpty()){minecraft.setScreen(new CenterDetailsScreen(this,state.getCompound("SelectedRow")));return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(section<2)return super.mouseScrolled(x,y,h,v);if(detailWidth()>0&&x>width-detailWidth()-18){detailScroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(detailContentHeight,height-176),detailScroll-(int)(v*24)));return true;}scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(rows().size()*60,height-160),scroll-(int)(v*28)));return true;}
    @Override public boolean isPauseScreen(){return false;}
}
