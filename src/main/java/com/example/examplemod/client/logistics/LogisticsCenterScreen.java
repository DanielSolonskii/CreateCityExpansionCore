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
    private int section,sidebar,scroll,page,detailScroll,detailContentHeight,navStep;
    private UUID selected,lastRequest;
    private CompoundTag state=new CompoundTag();
    private long sent,received,searchChanged,sectionChanged=net.minecraft.Util.getMillis();
    private boolean pending,reverse,onlyProblems,readTimedOut;
    private EditBox search;
    private ActionButton previousPage,nextPage,actionsButton;
    private String query="",lastResult="";
    public LogisticsCenterScreen(){super(tr("title"));}
    public void focusObject(String kind,UUID id){section=switch(kind){case "train"->2;case "line"->3;case "point","station"->4;case "warehouse"->5;case "offer"->6;case "shipment"->7;default->0;};selected=id;}
    @Override protected void init(){
        var layout=CenterLayout.of(width,height);sidebar=layout.sidebar();navStep=layout.navigationStep();
        search=new EditBox(font,sidebar+18,60,Math.max(60,width-sidebar-142),20,tr("search"));
        search.setHint(tr("search"));search.setMaxLength(96);search.setValue(query);
        search.setResponder(value->{query=value;searchChanged=net.minecraft.Util.getMillis();});addRenderableWidget(search);
        addRenderableWidget(new ActionButton(width-112,60,28,20,net.minecraft.network.chat.Component.literal("↻"),this::refresh)).setTooltip(Tooltip.create(tr("refresh")));
        addRenderableWidget(new ActionButton(width-78,60,28,20,net.minecraft.network.chat.Component.literal("↕"),()->{reverse=!reverse;scroll=0;})).setTooltip(Tooltip.create(tr("sort")));
        addRenderableWidget(new ActionButton(width-44,60,28,20,net.minecraft.network.chat.Component.literal("!"),()->{onlyProblems=!onlyProblems;scroll=0;})).setTooltip(Tooltip.create(tr("problems")));
        var r=layout.map();addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),tr("map"),this::openMap));
        r=layout.previous();previousPage=addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),net.minecraft.network.chat.Component.literal("‹"),()->{if(page>0){page--;scroll=0;refresh();}}));
        r=layout.next();nextPage=addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),net.minecraft.network.chat.Component.literal("›"),()->{if((page+1)*32<state.getInt("Total")){page++;scroll=0;refresh();}}));
        previousPage.active=page>0;nextPage.active=(page+1)*32<state.getInt("Total");
        r=layout.actions();actionsButton=addRenderableWidget(new ActionButton(r.x(),r.y(),r.width(),r.height(),tr("actions"),()->minecraft.setScreen(new CenterActionsScreen(this,state)),12));
        refresh();
    }
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
        if(previousPage!=null){previousPage.active=page>0;nextPage.active=(page+1)*32<state.getInt("Total");}
    }
    @Override public void tick(){super.tick();long now=net.minecraft.Util.getMillis();if(pending&&CenterPresentation.timedOut(sent,now)){pending=false;readTimedOut=true;lastResult=text("timeout");}if(searchChanged>0&&now-searchChanged>350){searchChanged=0;page=0;scroll=0;refresh();}else if(!pending&&!readTimedOut&&now-sent>3000)refresh();if(actionsButton!=null)actionsButton.active=!pending&&!state.getList("Forms",10).isEmpty();}
    private List<CompoundTag> rows(){var list=new ArrayList<CompoundTag>();for(Tag raw:state.getList("Rows",10)){var row=(CompoundTag)raw;if(onlyProblems&&!CenterPresentation.problem(row.getString("Status")))continue;list.add(row);}if(reverse)Collections.reverse(list);return list;}
    private void selectSection(int next){section=next;selected=null;scroll=page=detailScroll=0;state=new CompoundTag();received=0;lastResult="";sectionChanged=net.minecraft.Util.getMillis();refresh();}
    private int detailWidth(){return width>=720?Math.min(300,(width-sidebar)/3):0;}
    private void openMap(){LogisticsMapOverlay.open(this,state.getCompound("SelectedRow"));}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fillGradient(0,0,width,height,BG,0xff132329);g.fill(0,0,sidebar,height,0xff0c161b);g.vLine(sidebar,0,height,EDGE);
        g.drawString(font,"CCEC",17,16,TEAL,false);g.drawString(font,fit(text("control_center"),sidebar-28),17,32,TEXT,false);
        for(int i=0;i<IDS.length;i++){int y=65+i*navStep;boolean active=i==section,hover=mx>=7&&mx<sidebar-7&&my>=y-5&&my<y+navStep-7;if(active||hover){surface(g,7,y-5,sidebar-14,navStep-2,active?0xff1d3937:0xff172a30);if(active)g.fill(7,y-3,9,y+navStep-9,TEAL);}icon(g,16,y-1,i,active?TEAL:MUTED);g.drawString(font,fit(text("tab."+IDS[i]),sidebar-46),34,y+2,active?TEXT:MUTED,false);}
        int left=sidebar+18,area=width-left-18,dw=detailWidth(),listWidth=area-(dw==0?0:dw+12),bottom=height-64;
        g.drawString(font,"CCEC / "+text("dispatch"),left,15,MUTED,false);
        g.pose().pushPose();g.pose().translate(left,31,0);g.pose().scale(1.35f,1.35f,1);g.drawString(font,fit(text("tab."+IDS[section]),(int)(area/1.35)),0,0,TEXT,false);g.pose().popPose();
        if(section>=2)g.drawString(font,fit(net.minecraft.network.chat.Component.translatable("ccec.center.count",state.getInt("Total")).getString(),area),left,47,MUTED,false);
        if(section==0)renderDashboard(g,left,area,bottom,mx,my);
        else if(section==1){surface(g,left,96,area,Math.max(40,bottom-96),PANEL);icon(g,left+16,110,1,TEAL);g.drawString(font,fit(text("map_title"),area-52),left+38,112,TEXT,false);g.drawWordWrap(font,tr("map_hint"),left+16,137,area-32,MUTED);}
        else{
            var rows=rows();scroll=Math.min(scroll,CenterPresentation.scrollLimit(rows.size()*60,bottom-96));
            g.enableScissor(left,94,left+listWidth,bottom);
            for(int i=0;i<rows.size();i++){var row=rows.get(i);int y=96+i*60-scroll;if(y+54<94||y>bottom)continue;boolean picked=row.getUUID("Id").equals(selected),hover=mx>=left&&mx<left+listWidth&&my>=y&&my<y+53;int card=picked?0xff203e39:hover?0xff1b3037:PANEL;surface(g,left,y,listWidth,53,card);if(picked)g.fill(left,y+3,left+2,y+50,TEAL);icon(g,left+11,y+11,section,picked?TEAL:MUTED);int sw=Math.min(92,Math.max(30,listWidth/3));g.drawString(font,fit(name(row),listWidth-sw-46),left+31,y+11,TEXT,false);String status=value(row.getString("Status"));g.drawString(font,fit(status,sw),left+listWidth-sw-8,y+11,CenterPresentation.problem(row.getString("Status"))?BRASS:TEAL,false);g.drawString(font,fit(summary(row),listWidth-78),left+31,y+32,MUTED,false);surface(g,left+listWidth-37,y+28,29,18,hover?0xff2c5148:0xff1d3036);g.drawCenteredString(font,"…",left+listWidth-23,y+33,TEAL);}
            if(rows.isEmpty()){surface(g,left,96,listWidth,Math.max(40,bottom-96),PANEL);String key=readTimedOut?"timeout":received==0?"loading":onlyProblems?"filtered_empty":!query.isBlank()?"search_empty":state.getBoolean("Implemented")?"empty":"unavailable";g.drawWordWrap(font,tr(key),left+14,110,listWidth-28,MUTED);if(received!=0&&state.getBoolean("Implemented")&&query.isBlank()&&!onlyProblems&&bottom>175)g.drawWordWrap(font,tr("section_hint."+IDS[section]),left+14,150,listWidth-28,MUTED);}
            g.disableScissor();
            if(dw>0)renderDetails(g,left+listWidth+12,96,dw,bottom-96);
        }
        g.fill(sidebar+1,height-28,width,height,BG);g.hLine(left,width-18,height-29,EDGE);
        if(pending){int progress=(int)((net.minecraft.Util.getMillis()%1100)/1100.0*Math.max(1,area-30));g.fill(left+progress,height-29,Math.min(width-18,left+progress+30),height-28,TEAL);}
        String message=!lastResult.isEmpty()?lastResult:pending?text("sync"):received==0?text("loading"):text("fresh");g.drawString(font,fit(message,area),left,height-17,readTimedOut?BRASS:MUTED,false);
        if(state.getInt("Total")>32&&area>340)g.drawString(font,(page+1)+" / "+((state.getInt("Total")+31)/32),left+123,height-47,MUTED,false);
        super.render(g,mx,my,dt);
        if(my>height-28&&mx>sidebar&&!lastResult.isEmpty())g.renderTooltip(font,net.minecraft.network.chat.Component.literal(lastResult),mx,my);
    }
    private void renderDashboard(GuiGraphics g,int left,int area,int bottom,int mx,int my){
        String[] names={"TrainCount","PointCount","WarehouseCount","ShipmentCount"};int[] symbols={2,4,5,7};int columns=area>=400?4:2,cw=(area-(columns-1)*10)/columns;
        int ch=Math.max(30,Math.min(66,(bottom-100-10)/2));
        for(int i=0;i<names.length;i++){int x=left+i%columns*(cw+10),y=100+i/columns*(ch+10);if(y+ch>bottom)continue;boolean hover=mx>=x&&mx<x+cw&&my>=y&&my<y+ch;surface(g,x,y,cw,ch,hover?0xff1d343a:PANEL);icon(g,x+10,y+9,symbols[i],TEAL);g.drawString(font,fit(text("metric."+names[i]),cw-34),x+28,y+10,MUTED,false);if(ch>=46){g.pose().pushPose();g.pose().translate(x+12,y+32,0);g.pose().scale(1.5f,1.5f,1);g.drawString(font,received==0?"—":""+state.getInt(names[i]),0,0,TEXT,false);g.pose().popPose();}else g.drawString(font,received==0?"—":""+state.getInt(names[i]),x+12,y+ch-11,TEXT,false);}
        int y=100+((names.length+columns-1)/columns)*(ch+10);if(y+35<bottom){surface(g,left,y,area,bottom-y,PANEL);g.drawWordWrap(font,tr("dashboard_hint"),left+14,y+14,area-28,MUTED);}
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
        if(button==0&&x<sidebar&&y>=60){int next=(int)((y-60)/navStep);if(next>=0&&next<IDS.length){selectSection(next);return true;}}
        if(section==0&&y>=100&&y<height-64&&x>=sidebar+18&&x<width-18){int area=width-sidebar-36,columns=area>=400?4:2,cw=(area-(columns-1)*10)/columns,ch=Math.max(30,Math.min(66,(height-64-100-10)/2));int col=(int)((x-sidebar-18)/(cw+10)),line=(int)((y-100)/(ch+10)),index=line*columns+col;int[] sections={2,4,5,7};if(index>=0&&index<4&&(x-sidebar-18)%(cw+10)<cw&&(y-100)%(ch+10)<ch)selectSection(sections[index]);return true;}
        if(section==1&&x>sidebar&&y>=100&&y<190){openMap();return true;}
        int right=width-18-(detailWidth()==0?0:detailWidth()+12);if(section>=2&&x>=sidebar+18&&x<right&&y>=96&&y<height-64){int index=(int)((y-96+scroll)/60);var rows=rows();if(index>=0&&index<rows.size()){var row=rows.get(index);if(x>=right-37&&x<right-8&&(y-96+scroll)%60>=28&&(y-96+scroll)%60<46){minecraft.setScreen(new CenterDetailsScreen(this,row));return true;}selected=row.getUUID("Id");detailScroll=0;refresh();return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!search.isFocused()&&key==257&&!state.getCompound("SelectedRow").isEmpty()){minecraft.setScreen(new CenterDetailsScreen(this,state.getCompound("SelectedRow")));return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(detailWidth()>0&&x>width-detailWidth()-18){detailScroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(detailContentHeight,height-176),detailScroll-(int)(v*24)));return true;}scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(rows().size()*60,height-160),scroll-(int)(v*28)));return true;}
    @Override public boolean isPauseScreen(){return false;}
}
