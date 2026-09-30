package com.example.examplemod.client.screen;

import com.example.examplemod.economy.FreightPoint;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.example.examplemod.client.screen.LogisticsStyle.*;

/**
 * Client-only operations console. Selection/navigation never sends a mutating command.
 * All economic actions still go through the existing server-validated payload.
 */
public final class LogisticsScreen extends Screen {
    private enum View { LIST, REGISTER, POINT, SHIPMENT, SOURCE, RENAME, REMOVE, LEGACY }
    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private final LogisticsBlueprint blueprint=new LogisticsBlueprint();
    private final List<Runnable> enabledChecks=new ArrayList<>();
    private final List<Hit> hits=new ArrayList<>();
    private final Map<UUID,FreightPoint> pointById=new HashMap<>();
    private List<FreightPoint> ownPoints=List.of();
    private List<CompoundTag> allAssignments=List.of(), candidates=List.of(), legacy=List.of();
    private EconomyStatePayload snapshot;
    private LogisticsLayout layout;
    private View view=View.LIST;
    private int page,scroll,ticks,contentHeight,filter,step,mode=2,submittedAt=-1000;
    private UUID selected,chosenSource,legacySubject;
    private CompoundTag chosenPair;
    private String draft="";
    private EditBox nameBox;
    private boolean building,waitingRegistration,registrationFailed,diagnostics;
    private record Hit(int x,int y,int w,int h,Runnable action) {
        boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }

    public LogisticsScreen(CityPylonScreen parent) {
        super(FreightUi.tr("title"));this.parent=parent;menu=parent.getMenu();
    }
    @Override public boolean isPauseScreen() { return false; }
    private static List<CompoundTag> tags(CompoundTag tag,String key) {
        List<CompoundTag> result=new ArrayList<>();
        for(Tag raw:tag.getList(key,Tag.TAG_COMPOUND))result.add((CompoundTag)raw);
        return result;
    }
    private void request() {
        PacketDistributor.sendToServer(EconomyCommandPayload.simple(menu.getPylonPos(),EconomyCommandPayload.Action.REQUEST_STATE,null));
    }
    private void requestRails(boolean map) {
        PacketDistributor.sendToServer(RailwayCommandPayload.simple(menu.getPylonPos(),
                map?RailwayCommandPayload.Action.REQUEST_MAP:RailwayCommandPayload.Action.REQUEST_STATE,null,null));
    }
    @Override protected void init() {
        layout=LogisticsLayout.of(width,height);
        refresh();rebuild();request();requestRails(false);requestRails(true);
    }
    private void refresh() {
        snapshot=menu.getEconomyState();pointById.clear();
        for(FreightPoint p:FreightUi.points(snapshot))pointById.put(p.id,p);
        ownPoints=snapshot==null?List.of():pointById.values().stream()
                .filter(p->p.cityId.equals(snapshot.cityId()))
                .sorted(Comparator.comparing((FreightPoint p)->p.removed).thenComparing(p->!p.online()).thenComparing(p->p.name))
                .toList();
        allAssignments=snapshot==null?List.of():tags(snapshot.logistics(),"Assignments");
        candidates=snapshot==null?List.of():tags(snapshot.logistics(),"Candidates");
        legacy=snapshot==null?List.of():tags(snapshot.logistics(),"Legacy");
        blueprint.update(menu.getPylonPos(),menu.getDimensionId(),ownPoints,menu.getRailwayStations());
    }
    @Override public void tick() {
        ticks++;
        if(ticks%40==0){request();requestRails(false);}
        if(ticks%600==0)requestRails(true);
        var map=menu.drainRailwayMap();if(map!=null)blueprint.receive(map);
        if(snapshot!=menu.getEconomyState()){
            refresh();
            if(waitingRegistration&&chosenPair!=null) {
                FreightPoint added=ownPoints.stream().filter(p->p.postbox.asLong()==chosenPair.getLong("Postbox")
                        &&p.packager.asLong()==chosenPair.getLong("Packager")&&!p.removed).findFirst().orElse(null);
                if(added!=null){waitingRegistration=false;selected=added.id;view=View.POINT;scroll=0;}
            }
            rebuild();
        }
        if(waitingRegistration&&ticks-submittedAt>=100){
            waitingRegistration=false;registrationFailed=true;rebuild();
        }
    }
    private void send(EconomyCommandPayload.Action action,UUID id,UUID other,long a,long b,int type,String title,String note) {
        PacketDistributor.sendToServer(new EconomyCommandPayload(menu.getPylonPos(),action,id,other,null,null,a,b,0,type,title,"",note));
        request();
    }
    private void changePage(int target) {
        page=target;view=View.LIST;selected=null;scroll=0;filter=0;diagnostics=false;rebuild();
    }
    private void open(View target,UUID id) {
        view=target;selected=id;scroll=0;diagnostics=false;rebuild();
    }
    private void startSetup() {
        page=1;view=View.REGISTER;step=0;scroll=0;draft="";chosenPair=null;
        waitingRegistration=false;registrationFailed=false;rebuild();request();
    }
    @Override public void onClose() {
        if(waitingRegistration)return;
        if(view==View.REGISTER&&step>0){step--;scroll=0;rebuild();return;}
        if(view==View.RENAME||view==View.REMOVE){open(View.POINT,selected);return;}
        if(view==View.SOURCE){open(View.SHIPMENT,selected);return;}
        if(view!=View.LIST){view=View.LIST;scroll=0;rebuild();return;}
        minecraft.setScreen(parent);
    }

    private void rebuild() {
        if(layout==null)return;
        boolean focused=nameBox!=null&&nameBox.isFocused();
        int cursor=nameBox==null?0:nameBox.getCursorPosition();
        clearWidgets();enabledChecks.clear();hits.clear();nameBox=null;
        String[] tabs={"overview","points","outgoing","incoming"};
        int tabWidth=layout.width()/4;
        for(int i=0;i<4;i++){final int p=i;addRenderableWidget(new Action(layout.left()+i*tabWidth,44,tabWidth-3,
                FreightUi.tr(tabs[i]),page==i,()->changePage(p))).active=!waitingRegistration;}
        int fw=Math.min(180,(layout.width()-8)/2);
        addRenderableWidget(new Action(layout.left(),layout.footer(),fw,FreightUi.tr("back"),false,this::onClose)).active=!waitingRegistration;
        addRenderableWidget(new Action(layout.right()-fw,layout.footer(),fw,FreightUi.tr("market"),false,
                ()->minecraft.setScreen(new MarketScreen(parent)))).active=!waitingRegistration;
        building=true;
        contentHeight=compose(null,0,0);
        int clamped=layout.clampScroll(scroll,contentHeight);
        building=false;
        if(clamped!=scroll){scroll=clamped;rebuild();return;}
        if(focused&&nameBox!=null){setInitialFocus(nameBox);nameBox.moveCursorTo(Math.min(cursor,draft.length()),false);}
    }
    private void button(int x,int y,int w,Component label,boolean primary,BooleanSupplier enabled,Runnable run) {
        if(!building||y<layout.top()||y+22>layout.bottom())return;
        Action action=addRenderableWidget(new Action(x,y,w,label,primary,run));
        enabledChecks.add(()->action.active=enabled.getAsBoolean()&&!waitingRegistration);
        action.active=enabled.getAsBoolean()&&!waitingRegistration;
    }
    private void button(int x,int y,int w,Component label,boolean primary,Runnable run) {
        button(x,y,w,label,primary,()->true,run);
    }
    private void hit(int x,int y,int w,int h,Runnable run) {
        if(building)hits.add(new Hit(x,y,w,h,run));
    }
    private void line(GuiGraphics g,Component value,int x,int y,int w,int color) {
        text(g,font,value,x,y,w,color);
    }
    private int wrap(GuiGraphics g,Component value,int x,int y,int w,int color) {
        var lines=font.split(value,Math.max(30,w));
        if(g!=null)for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),x,y+i*12,color,false);
        return lines.size()*12;
    }
    private int section(GuiGraphics g,Component heading,Component help,int y) {
        return section(g,heading,help,layout.left(),y,layout.width());
    }
    private int section(GuiGraphics g,Component heading,Component help,int x,int y,int w) {
        int headingHeight=wrap(g,heading,x,y,w,GOLD);
        return y+headingHeight+8+wrap(g,help,x,y+headingHeight+8,w,MUTED)+12;
    }
    private boolean terminal(CompoundTag a) {
        return Set.of("complete","expired").contains(a.getString("State"));
    }
    private boolean attention(CompoundTag a) {
        return Set.of("source_unavailable","destination_unavailable","route_error","requires_setup").contains(a.getString("State"));
    }
    private FreightPoint point(UUID id){return pointById.get(id);}
    private FreightPoint endpoint(CompoundTag a,String key){return a.hasUUID(key)?point(a.getUUID(key)):null;}
    private String pointName(FreightPoint p){return p==null?tr("unassigned").getString():p.name;}
    private CompoundTag assignment() {
        return allAssignments.stream().filter(a->a.getUUID("Subject").equals(selected)).findFirst().orElse(null);
    }
    private List<FreightPoint> sourceChoices() {
        return ownPoints.stream().filter(p->p.enabled&&!p.removed&&p.mode.sends()).toList();
    }
    private boolean busy(UUID source) {
        return allAssignments.stream().anyMatch(a->!terminal(a)&&a.hasUUID("Source")&&a.getUUID("Source").equals(source)&&!a.getUUID("Subject").equals(selected));
    }

    private int compose(GuiGraphics g,int mx,int my) {
        int start=layout.top()-scroll,y=start;
        if(snapshot==null){return section(g,FreightUi.tr("loading"),tr("loading_help"),y)-start;}
        switch(view) {
            case LIST -> {y=page==0?overview(g,y,mx,my):list(g,y);}
            case REGISTER -> y=registration(g,y);
            case POINT,RENAME,REMOVE -> y=pointDetails(g,y);
            case SHIPMENT,SOURCE -> y=shipmentDetails(g,y);
            case LEGACY -> y=legacySetup(g,y);
        }
        return y-start+12;
    }

    private int overview(GuiGraphics g,int y,int mx,int my) {
        long count=ownPoints.stream().filter(p->!p.removed).count();
        long out=allAssignments.stream().filter(a->!terminal(a)&&a.getBoolean("Outgoing")).count();
        long in=allAssignments.stream().filter(a->!terminal(a)&&!a.getBoolean("Outgoing")).count();
        long errors=ownPoints.stream().filter(p->!p.removed&&p.enabled&&!p.online()).count()
                +allAssignments.stream().filter(this::attention).count()+legacy.size();
        int x=layout.left(),w=layout.wide()?(layout.width()-18)/2:layout.width();
        int mapTop=y;
        if(count==0) {
            y=section(g,tr("welcome"),tr("welcome_help"),x,y,w);
            int cw=Math.min(w,330);
            button(x,y,cw,tr("first_point"),true,this::startSetup);y+=34;
            String[] icons={"create:track_station","create:package_postbox","create:packager"};
            String[] keys={"step.station","step.postbox","step.packager"};
            for(int i=0;i<3;i++){
                int h=Math.max(30,font.split(tr(keys[i]),w-42).size()*12+14);
                panel(g,x,y,w,h,PANEL);icon(g,icons[i],x+8,y+7);
                wrap(g,tr(keys[i]),x+32,y+8,w-42,TEXT);y+=h+4;
            }
            y+=6;
        }else{
            y=section(g,tr("dashboard"),tr("dashboard_help",menu.getCityName()),x,y,w);
            String[] labels={"points","outgoing","incoming","console.attention"};
            long[] values={count,out,in,errors};
            int cols=w>=500?4:2,cw=(w-6*(cols-1))/cols;
            for(int i=0;i<4;i++){
                int xx=x+(i%cols)*(cw+6),yy=y+(i/cols)*48;
                panel(g,xx,yy,cw,42,PANEL);
                line(g,Component.literal(Long.toString(values[i])),xx+9,yy+7,cw-18,i==3&&errors>0?RED:GOLD);
                line(g,FreightUi.tr(labels[i]),xx+9,yy+24,cw-18,MUTED);
                final int target=i==0?1:i==1?2:i==2?3:
                        allAssignments.stream().anyMatch(a->attention(a)&&a.getBoolean("Outgoing"))?2:
                        allAssignments.stream().anyMatch(this::attention)?3:1;
                final boolean needsAttention=i==3;
                hit(xx,yy,cw,42,()->{changePage(target);if(needsAttention){filter=1;rebuild();}});
            }
            y+=((4+cols-1)/cols)*48+4;
            button(x,y,w,tr("add_point"),true,this::startSetup);y+=29;
            int bw=(w-6)/2;
            button(x,y,bw,FreightUi.tr("outgoing"),false,()->changePage(2));
            button(x+bw+6,y,bw,FreightUi.tr("incoming"),false,()->changePage(3));y+=36;
            if(out+in==0)y+=wrap(g,tr("idle"),x,y,w,MUTED)+12;
        }
        int mapX=layout.wide()?x+w+18:x,mapW=layout.wide()?layout.width()-w-18:w;
        int mapY=layout.wide()?mapTop:y;
        mapY+=wrap(g,tr("map.title"),mapX,mapY,mapW,GOLD)+9;
        int mapH=layout.wide()?Math.max(180,Math.min(260,layout.viewport()-90)):142;
        if(g!=null)blueprint.render(g,font,mapX,mapY,mapW,mapH,ticks,mx,my);
        mapY+=mapH+8;
        mapY+=wrap(g,tr("map.legend"),mapX,mapY,mapW,MUTED)+10;
        y=Math.max(y,mapY);
        if(!legacy.isEmpty()){
            button(x,y,Math.min(w,360),tr("legacy_action",legacy.size()),false,()->{view=View.LEGACY;scroll=0;legacySubject=legacy.getFirst().getUUID("Subject");rebuild();});
            y+=32;
        }
        return y;
    }
    private int list(GuiGraphics g,int y) {
        int x=layout.left(),w=layout.width();
        y=section(g,FreightUi.tr(page==1?"points":page==2?"outgoing":"incoming"),
                tr(page==1?"points_help":page==2?"outgoing_help":"incoming_help"),y);
        int bw=(w-8)/2;
        button(x,y,bw,tr(page==1?"add_point":"find_trade"),page==1,
                ()->{if(page==1)startSetup();else minecraft.setScreen(new MarketScreen(parent));});
        button(x+bw+8,y,bw,tr("filter."+filter),false,()->{filter=(filter+1)%3;scroll=0;rebuild();});y+=34;
        if(page==1){
            List<FreightPoint> list=ownPoints.stream().filter(p->filter==2||!p.removed)
                    .filter(p->filter!=1||(!p.online()&&p.enabled)).toList();
            if(list.isEmpty())return empty(g,y,"points",filter==0?this::startSetup:()->{filter=2;rebuild();},filter==0?"add_point":"show_all");
            for(FreightPoint p:list){
                panel(g,x,y,w,84,PANEL);icon(g,"create:package_postbox",x+9,y+10);
                line(g,Component.literal(p.name),x+33,y+9,w-46,TEXT);
                badge(g,font,p.online()?tr("ready"):FreightUi.tr(p.status),x+10,y+24,w-105,statusColor(p.status));
                line(g,FreightUi.tr("mode."+p.mode.name().toLowerCase(Locale.ROOT)),x+10,y+43,w-105,MUTED);
                line(g,FreightUi.tr("station",p.stationName.isBlank()?tr("unlinked").getString():p.stationName),x+10,y+59,w-110,MUTED);
                if(p.defaultReceive)line(g,Component.literal("★"),x+w-22,y+9,16,GOLD);
                button(x+w-96,y+52,86,tr("details"),false,()->open(View.POINT,p.id));
                hit(x,y,w,84,()->open(View.POINT,p.id));y+=91;
            }
        }else{
            List<CompoundTag> list=allAssignments.stream().filter(a->a.getBoolean("Outgoing")==(page==2))
                    .filter(a->filter==2||!terminal(a)).filter(a->filter!=1||attention(a))
                    .sorted(Comparator.comparing((CompoundTag a)->!attention(a)).thenComparing(this::terminal)).toList();
            if(list.isEmpty())return empty(g,y,page==2?"outgoing":"incoming",
                    filter==0?()->minecraft.setScreen(new MarketScreen(parent)):()->{filter=2;rebuild();},filter==0?"find_trade":"show_all");
            for(CompoundTag a:list){
                panel(g,x,y,w,100,PANEL);if(g!=null)g.renderItem(item(a.getString("Item")),x+9,y+9);
                line(g,FreightUi.tr("cargo",a.getLong("Amount"),item(a.getString("Item")).getHoverName()),x+32,y+10,w-44,TEXT);
                line(g,Component.literal(pointName(endpoint(a,"Source"))+" → "+pointName(endpoint(a,"Destination"))),x+10,y+28,w-20,MUTED);
                badge(g,font,FreightUi.tr(a.getString("State")),x+10,y+42,w-110,statusColor(a.getString("State")));
                progress(g,a,x+10,y+65,w-116);
                line(g,tr("received_count",a.getLong("Delivered"),a.getLong("Amount"),a.getLong("InFlight")),x+10,y+81,w-20,MUTED);
                button(x+w-96,y+48,86,tr("details"),false,()->open(View.SHIPMENT,a.getUUID("Subject")));
                hit(x,y,w,100,()->open(View.SHIPMENT,a.getUUID("Subject")));y+=107;
            }
        }
        return y;
    }
    private int empty(GuiGraphics g,int y,String kind,Runnable next,String action) {
        int x=layout.left(),w=layout.width();
        panel(g,x,y,w,50,PANEL);icon(g,kind.equals("points")?"create:package_postbox":"minecraft:chest",x+12,y+15);
        line(g,tr("empty."+kind),x+38,y+20,w-50,GOLD);y+=62;
        y+=wrap(g,tr("empty."+kind+"_help"),x,y,w,MUTED)+14;
        button(x,y,Math.min(w,320),tr(action),true,next);return y+32;
    }
    private void progress(GuiGraphics g,CompoundTag a,int x,int y,int w) {
        if(g==null||w<1)return;
        double total=Math.max(1,a.getLong("Amount"));
        int done=(int)(w*Math.clamp(a.getLong("Delivered")/total,0,1));
        int flight=(int)(w*Math.clamp(a.getLong("InFlight")/total,0,1-done/(double)w));
        g.fill(x,y,x+w,y+5,0xFF0E1920);g.fill(x,y,x+done,y+5,GREEN);
        g.fill(x+done,y,x+done+flight,y+5,BLUE);
    }

    private int registration(GuiGraphics g,int y) {
        int x=layout.left(),w=layout.width();
        y=section(g,tr("setup.title"),tr("setup.step",step+1,3),y);
        for(int i=0;i<3;i++)if(g!=null)g.fill(x+i*(w/3),y,x+(i+1)*(w/3)-3,y+3,i<=step?GOLD:BORDER);
        y+=16;
        if(step==0){
            y+=wrap(g,tr("setup.infrastructure"),x,y,w,TEXT)+14;
            if(candidates.isEmpty()){
                y+=wrap(g,tr("setup.not_found"),x,y,w,MUTED)+16;
                button(x,y,Math.min(w,240),tr("refresh"),true,this::request);return y+34;
            }
            for(CompoundTag c:candidates){
                boolean chosen=samePair(c,chosenPair);
                panel(g,x,y,w,76,chosen?0xFF35483F:PANEL);
                icon(g,"create:package_postbox",x+10,y+9);icon(g,"create:packager",x+30,y+9);
                line(g,FreightUi.tr("station",c.getString("Station").isBlank()?tr("unlinked").getString():c.getString("Station")),x+55,y+12,w-65,TEXT);
                line(g,Component.literal(BlockPos.of(c.getLong("Postbox")).toShortString()+" → "+BlockPos.of(c.getLong("Packager")).toShortString()),x+10,y+34,w-20,MUTED);
                button(x+w-140,y+48,130,tr(chosen?"selected":"select"),chosen,()->{chosenPair=c.copy();rebuild();});
                hit(x,y,w,44,()->{chosenPair=c.copy();rebuild();});y+=82;
            }
            button(x,y,Math.min(w,300),tr("next"),true,()->chosenPair!=null&&!chosenPair.getString("Station").isBlank(),()->{step=1;scroll=0;rebuild();});
            return y+34;
        }
        if(step==1) {
            y+=wrap(g,tr("setup.identity"),x,y,w,TEXT)+14;
            line(g,FreightUi.tr("name"),x,y,w,GOLD);y+=16;
            if(building&&y>=layout.top()&&y+22<=layout.bottom()){
                nameBox=new EditBox(font,x,y,w,22,FreightUi.tr("name"));
                nameBox.setMaxLength(64);nameBox.setHint(tr("name_hint"));nameBox.setValue(draft);
                nameBox.setResponder(value->draft=value);addRenderableWidget(nameBox);
            }
            y+=36;
            for(int i=0;i<3;i++){final int m=i;
                button(x,y,w,FreightUi.tr("mode."+FreightPoint.Mode.values()[i].name().toLowerCase(Locale.ROOT)),mode==i,()->{mode=m;rebuild();});
                y+=29;
            }
            y+=wrap(g,tr("setup.receiver"),x,y,w,MUTED)+14;
            button(x,y,Math.min(w,300),tr("review_setup"),true,()->!draft.isBlank(),()->{step=2;scroll=0;rebuild();});return y+34;
        }
        line(g,Component.literal(draft),x,y,w,GOLD);y+=22;
        line(g,FreightUi.tr("mode."+FreightPoint.Mode.values()[mode].name().toLowerCase(Locale.ROOT)),x,y,w,TEXT);y+=20;
        if(chosenPair!=null){line(g,FreightUi.tr("station",chosenPair.getString("Station")),x,y,w,TEXT);y+=20;}
        y+=wrap(g,tr("setup.review"),x,y,w,MUTED)+16;
        if(waitingRegistration||registrationFailed)y+=wrap(g,tr(waitingRegistration?"setup.waiting":"setup.failed"),x,y,w,registrationFailed?RED:GOLD)+12;
        button(x,y,Math.min(w,340),FreightUi.tr("register"),true,()->chosenPair!=null&&!draft.isBlank(),()->{
            waitingRegistration=true;registrationFailed=false;submittedAt=ticks;
            send(EconomyCommandPayload.Action.REGISTER_FREIGHT,null,null,chosenPair.getLong("Postbox"),chosenPair.getLong("Packager"),mode,draft,"");rebuild();
        });
        return y+34;
    }
    private boolean samePair(CompoundTag a,CompoundTag b) {
        return a!=null&&b!=null&&a.getLong("Postbox")==b.getLong("Postbox")&&a.getLong("Packager")==b.getLong("Packager");
    }
    private int pointDetails(GuiGraphics g,int y) {
        FreightPoint p=point(selected);if(p==null)return empty(g,y,"points",()->changePage(1),"show_all");
        int x=layout.left(),w=layout.width();
        y=section(g,Component.literal(p.name),FreightUi.tr(p.status),y);
        if(view==View.RENAME){
            if(building&&y>=layout.top()&&y+22<=layout.bottom()){
                nameBox=new EditBox(font,x,y,w,22,FreightUi.tr("name"));nameBox.setMaxLength(64);
                nameBox.setValue(draft);nameBox.setResponder(value->draft=value);addRenderableWidget(nameBox);
            }
            y+=34;
            button(x,y,Math.min(w,300),tr("save_name"),true,()->!draft.isBlank(),()->{manage("rename",draft);open(View.POINT,selected);});return y+34;
        }
        if(view==View.REMOVE){
            y+=wrap(g,tr("remove_warning"),x,y,w,RED)+16;
            button(x,y,Math.min(w,320),tr("confirm_remove"),true,()->{manage("remove","");open(View.POINT,selected);});return y+34;
        }
        line(g,FreightUi.tr("station",p.stationName.isBlank()?tr("unlinked").getString():p.stationName),x,y,w,TEXT);y+=20;
        line(g,FreightUi.tr("mode."+p.mode.name().toLowerCase(Locale.ROOT)),x,y,w,MUTED);y+=22;
        String[] icons={"create:package_postbox","create:packager","create:track_station"};
        String[] keys={"check.postbox","check.packager","check.station"};
        boolean[] ok={p.postboxPresent,p.packagerPresent,p.stationPresent};
        for(int i=0;i<3;i++){
            panel(g,x,y,w,30,PANEL);icon(g,icons[i],x+8,y+7);
            line(g,tr(keys[i]),x+32,y+10,w-82,TEXT);
            line(g,tr(ok[i]?"check.ok":"check.missing"),x+w-72,y+10,64,ok[i]?GREEN:RED);y+=34;
        }
        y+=8;
        int bw=(w-8)/2;
        button(x,y,bw,FreightUi.tr("rename"),false,()->{draft=p.name;open(View.RENAME,p.id);});
        button(x+bw+8,y,bw,tr(p.enabled&&!p.removed?"disable":"enable"),false,()->manage("toggle",""));y+=30;
        button(x,y,w,FreightUi.tr("default"),p.defaultReceive,()->p.enabled&&!p.removed&&p.mode.receives(),()->manage("default",""));y+=30;
        button(x,y,bw,tr("diagnostics"),false,()->{diagnostics=!diagnostics;rebuild();});
        button(x+bw+8,y,bw,FreightUi.tr("remove"),false,()->!p.removed,()->open(View.REMOVE,p.id));y+=34;
        if(diagnostics){
            y+=wrap(g,Component.literal(p.address),x,y,w,MUTED)+8;
            y+=wrap(g,Component.literal("Postbox: "+p.postbox.toShortString()+" / Packager: "+p.packager.toShortString()),x,y,w,MUTED)+8;
            y+=wrap(g,Component.literal(p.dimension+" / "+p.id),x,y,w,MUTED)+8;
        }
        return y;
    }
    private void manage(String operation,String name) {
        if(selected!=null)send(EconomyCommandPayload.Action.MANAGE_FREIGHT,selected,null,0,0,0,name,operation);
    }

    private int shipmentDetails(GuiGraphics g,int y) {
        CompoundTag a=assignment();if(a==null)return empty(g,y,page==3?"incoming":"outgoing",()->changePage(page),"show_all");
        int x=layout.left(),w=layout.width();
        y=section(g,FreightUi.tr("cargo",a.getLong("Amount"),item(a.getString("Item")).getHoverName()),FreightUi.tr(a.getString("State")),y);
        if(view==View.SOURCE){
            y+=wrap(g,tr("source_help"),x,y,w,MUTED)+12;
            var choices=sourceChoices();
            for(FreightPoint p:choices){
                boolean occupied=busy(p.id);
                button(x,y,w,Component.literal(p.name).append(" · ").append(occupied?tr("busy"):FreightUi.tr(p.status)),p.id.equals(chosenSource),
                        ()->!occupied,()->{chosenSource=p.id;rebuild();});y+=29;
            }
            if(choices.isEmpty())y+=wrap(g,tr("no_source"),x,y,w,RED)+12;
            button(x,y,Math.min(w,340),tr("confirm_source"),true,()->chosenSource!=null&&!busy(chosenSource),()->{
                send(EconomyCommandPayload.Action.CONTROL_DISPATCH,selected,chosenSource,0,0,0,"","");
                open(View.SHIPMENT,selected);
            });return y+34;
        }
        FreightPoint source=endpoint(a,"Source"),destination=endpoint(a,"Destination");
        y+=wrap(g,FreightUi.tr("source",pointName(source)),x,y,w,TEXT)+10;
        y+=wrap(g,FreightUi.tr("destination",pointName(destination)),x,y,w,TEXT)+10;
        progress(g,a,x,y,w);y+=16;
        y+=wrap(g,FreightUi.tr("progress",a.getLong("Delivered"),a.getLong("Amount"),a.getLong("InFlight"),a.getLong("Available")),x,y,w,TEXT)+12;
        String route=source==null||destination==null?"unknown":FreightUi.route(snapshot,source.id,destination.id);
        y+=wrap(g,FreightUi.tr("route."+route),x,y,w,route.equals("connected")?GREEN:GOLD)+8;
        if(source!=null&&destination!=null)y+=wrap(g,Component.literal(source.stationName+" → "+destination.stationName),x,y,w,MUTED)+10;
        y+=wrap(g,tr(a.getBoolean("Outgoing")?"dispatch_help":"receipt_help"),x,y,w,MUTED)+14;
        if(a.getBoolean("Outgoing")&&!terminal(a)){
            int bw=(w-8)/2;
            button(x,y,bw,FreightUi.tr("start"),true,()->source!=null&&source.online()&&destination!=null&&destination.online()&&route.equals("connected")&&!a.getBoolean("Running"),
                    ()->send(EconomyCommandPayload.Action.CONTROL_DISPATCH,selected,null,0,0,1,"",""));
            button(x+bw+8,y,bw,FreightUi.tr("pause"),false,()->a.getBoolean("Running"),
                    ()->send(EconomyCommandPayload.Action.CONTROL_DISPATCH,selected,null,0,0,0,"",""));y+=30;
            button(x,y,w,FreightUi.tr("change_source"),false,()->{chosenSource=source==null?null:source.id;open(View.SOURCE,selected);});y+=34;
        }
        button(x,y,Math.min(w,260),tr("diagnostics"),false,()->{diagnostics=!diagnostics;rebuild();});y+=34;
        if(diagnostics){
            y+=wrap(g,Component.literal(a.getUUID("Subject").toString()),x,y,w,MUTED)+8;
            if(destination!=null)y+=wrap(g,Component.literal(destination.address),x,y,w,MUTED)+8;
        }
        return y;
    }
    private int legacySetup(GuiGraphics g,int y) {
        int x=layout.left(),w=layout.width();
        y=section(g,tr("legacy_title"),tr("legacy_help"),y);
        var choices=FreightUi.choices(snapshot,true);
        for(var a:legacy){UUID subject=a.getUUID("Subject");
            var contract=snapshot.contracts().stream().filter(c->c.id().equals(subject)).findFirst().orElse(null);
            Component label=contract==null?tr("legacy_order"):FreightUi.tr("cargo",contract.amount(),item(contract.itemId().toString()).getHoverName());
            button(x,y,w,label,subject.equals(legacySubject),()->{legacySubject=subject;rebuild();});y+=29;
        }
        FreightPoint receive=point(chosenSource);
        if(receive==null||!choices.stream().anyMatch(p->p.id.equals(chosenSource)))receive=choices.isEmpty()?null:choices.getFirst();
        UUID receiver=receive==null?null:receive.id;
        button(x,y,w,FreightUi.tr("destination",pointName(receive)),false,()->!choices.isEmpty(),()->{
            int index=0;for(int i=0;i<choices.size();i++)if(choices.get(i).id.equals(receiver))index=i+1;
            chosenSource=choices.get(index%choices.size()).id;rebuild();
        });y+=30;
        button(x,y,w,tr("confirm_setup"),true,()->legacySubject!=null&&receiver!=null,()->{
            send(EconomyCommandPayload.Action.SETUP_FREIGHT,legacySubject,receiver,0,0,0,"","");
            changePage(0);
        });return y+34;
    }

    @Override public void render(GuiGraphics g,int mx,int my,float pt) {
        g.fill(0,0,width,height,BG);
        g.fill(0,0,width,38,0xFF192A32);g.fill(0,37,width,38,BORDER);
        icon(g,"create:package_postbox",layout.left(),10);
        line(g,title,layout.left()+24,8,layout.width()-24,GOLD);
        line(g,Component.literal(menu.getCityName()),layout.left()+24,22,layout.width()-24,MUTED);
        g.enableScissor(layout.left(),layout.top(),layout.right(),layout.bottom());
        compose(g,mx,my);
        g.disableScissor();
        if(contentHeight>layout.viewport()){
            int track=layout.viewport(),thumb=Math.max(16,track*track/contentHeight);
            int sy=layout.top()+(track-thumb)*scroll/Math.max(1,contentHeight-track);
            g.fill(layout.right()+4,layout.top(),layout.right()+6,layout.bottom(),BORDER);
            g.fill(layout.right()+4,sy,layout.right()+6,sy+thumb,GOLD);
        }
        for(Runnable check:enabledChecks)check.run();
        super.render(g,mx,my,pt);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(super.mouseClicked(x,y,button))return true;
        if(button==0&&!waitingRegistration&&layout.contains(x,y)){
            for(Hit hit:hits)if(hit.contains(x,y)){hit.action.run();return true;}
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(!layout.contains(x,y)||dy==0)return super.mouseScrolled(x,y,dx,dy);
        int next=layout.clampScroll(scroll+(dy<0?28:-28),contentHeight);
        if(next!=scroll){scroll=next;rebuild();}return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(nameBox==null||!nameBox.isFocused()) {
            int next=switch(key){
                case 266 -> scroll-layout.viewport()+24;
                case 267 -> scroll+layout.viewport()-24;
                case 268 -> 0;
                case 269 -> contentHeight;
                default -> scroll;
            };
            next=layout.clampScroll(next,contentHeight);
            if(next!=scroll){scroll=next;rebuild();return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
}
