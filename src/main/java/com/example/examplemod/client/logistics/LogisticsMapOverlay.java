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
    private record View(int x,int y,int width,int height){int right(){return x+width;}int bottom(){return y+height;}boolean contains(double px,double py){return px>=x&&py>=y&&px<right()&&py<bottom();}}
    private static final Map<View,CompoundTag> HITS=new LinkedHashMap<>();
    private static CompoundTag state=new CompoundTag(),fullState=new CompoundTag(),selection,focusLocation,route;
    private static UUID focus,pending;

    private static final Map<UUID,double[]> trainMotion=new HashMap<>();
    private static double[] trainPosition(CompoundTag marker,long now){
        var loc=marker.getCompound("Location");var pos=BlockPos.of(loc.getLong("Pos"));
        double x=pos.getX()+.5,z=pos.getZ()+.5;
        if(!marker.getString("Kind").equals("train"))return new double[]{x,z};
        var id=marker.getUUID("Id");var previous=trainMotion.get(id);int dimension=loc.getString("Dimension").hashCode();
        if(previous==null||previous[5]!=dimension||Math.hypot(x-previous[2],z-previous[3])>256){previous=new double[]{x,z,x,z,now,dimension};trainMotion.put(id,previous);}
        double t=Math.clamp((now-previous[4])/1000.0,0,1);double px=previous[0]+(previous[2]-previous[0])*t,pz=previous[1]+(previous[3]-previous[1])*t;
        if(previous[2]!=x||previous[3]!=z){previous[0]=px;previous[1]=pz;previous[2]=x;previous[3]=z;previous[4]=now;}
        return new double[]{px,pz};
    }
    private static boolean enabled,visible,session;
    private static ResourceLocation mapDimension;
    private static int hash,barX,barY;private static long sent,received;
    private static boolean fitRoute;
    private static final DispatchMotion.Transition deliveredBar=new DispatchMotion.Transition(0);
    private LogisticsMapOverlay() {}
    public static void deactivate(){trainMotion.clear();enabled=visible=session=false;HITS.clear();selection=null;}
    public static boolean isSession(){return session;}
    public static void reset(){deactivate();state=new CompoundTag();fullState=new CompoundTag();route=focusLocation=null;pending=focus=null;mapDimension=null;hash=0;sent=received=0;}
    /**
     * Attaches the same bounded rail feed to a normal World Map. The logistics center keeps its own
     * session, so this must not steal focus or route preview state from it.
     */
    public static void attach(ResourceLocation dimension){
        if(session)return;
        if(enabled&&dimension.equals(mapDimension))return;
        enabled=true;visible=false;selection=null;focus=null;route=null;focusLocation=null;pending=null;
        state=new CompoundTag();fullState=new CompoundTag();mapDimension=dimension;hash=0;sent=received=0;
        refresh();
    }
    public static void openRoute(LogisticsCenterScreen parent,CompoundTag values,CompoundTag preview){open(parent,new CompoundTag(),values,preview);}
    public static void reviewRoute(LogisticsCenterScreen parent,net.minecraft.client.gui.screens.Screen form,CompoundTag values,CompoundTag preview){openRoute(parent,values,preview);var mc=Minecraft.getInstance();if(mc.screen instanceof PolishedWorldMapScreen map)map.returnToCommunity(form);}
    public static void open(LogisticsCenterScreen parent,CompoundTag selected){open(parent,selected,null,null);}
    private static void open(LogisticsCenterScreen parent,CompoundTag selected,CompoundTag requestedRoute,CompoundTag initial){var mc=Minecraft.getInstance();if(mc.player==null)return;enabled=true;session=true;visible=false;selection=null;focus=selected.hasUUID("Id")?selected.getUUID("Id"):null;focusLocation=selected.contains("Location")?selected.getCompound("Location").copy():null;route=requestedRoute==null?null:requestedRoute.copy();fitRoute=true;state=initial==null?new CompoundTag():initial.copy();fullState=new CompoundTag();hash=0;
        ResourceLocation dimension=focusLocation==null?mc.player.level().dimension().location():ResourceLocation.parse(focusLocation.getString("Dimension"));mapDimension=dimension;CityWorldMapSession.INSTANCE.awaitAnchor();var map=new PolishedWorldMapScreen(CityWorldMapSession.INSTANCE);map.planningDimension(dimension);map.returnToCommunity(parent);mc.setScreen(map);PacketDistributor.sendToServer(new RequestCityDirectoryPayload(dimension,RequestCityDirectoryPayload.NEW,0));refresh();}
    public static void accept(CompoundTag response){if(!enabled||!response.hasUUID("Request")||!response.getUUID("Request").equals(pending))return;if(!response.getBoolean("Unchanged")){fullState=com.example.examplemod.cargo.center.CenterDelta.apply(fullState,response);state=fullState.getCompound("Map").copy();}received=net.minecraft.Util.getMillis();if(response.contains("Hash"))hash=response.getInt("Hash");}
    private static void refresh(){var q=new CompoundTag();pending=UUID.randomUUID();q.putUUID("Request",pending);q.putString("Action","VIEW");q.putString("Section","map");q.putInt("Hash",hash);if(mapDimension!=null)q.putString("Dimension",mapDimension.toString());if(!session)q.putBoolean("RailsOnly",true);if(focus!=null)q.putUUID("Focus",focus);if(route!=null)q.put("MapRoute",route.copy());PacketDistributor.sendToServer(new CenterRequest(q));sent=net.minecraft.Util.getMillis();}
    public static void render(GuiGraphics g,CityMapPanel map,ResourceLocation dimension,int left,int top,int width,int height,int mx,int my,boolean active){var viewport=new View(left,top,width,height);visible=enabled&&active;HITS.clear();if(!visible)return;if(net.minecraft.Util.getMillis()-sent>(session?1000:15000))refresh();
        /*
         * A normal World Map borrows only the rail feed. The chips, markers and object card are
         * logistics-center controls: showing them here would pretend the atlas is the center.
         */
        if(!session)return;
        if(focusLocation!=null&&focusLocation.getString("Dimension").equals(dimension.toString())){map.centerOn(BlockPos.of(focusLocation.getLong("Pos")));focusLocation=null;}
        g.enableScissor(viewport.x(),viewport.y(),viewport.right(),viewport.bottom());
        renderRoute(g,map,dimension,viewport);
        for(Tag raw:state.getList("Markers",10)){
            var marker=(CompoundTag)raw;var location=marker.getCompound("Location");String kind=marker.getString("Kind");
            if(!LAYERS.contains(kind)||!location.getString("Dimension").equals(dimension.toString()))continue;
            var pos=BlockPos.of(location.getLong("Pos"));var smooth=trainPosition(marker,net.minecraft.Util.getMillis());int x=map.worldToScreenX(map.reducedMotion()?pos.getX()+.5:smooth[0]),y=map.worldToScreenY(map.reducedMotion()?pos.getZ()+.5:smooth[1]);if(!viewport.contains(x,y))continue;
            var hit=new View(x-10,y-11,24,25);boolean hovered=hit.contains(mx,my),selected=selection!=null&&selection.hasUUID("Id")&&selection.getUUID("Id").equals(marker.getUUID("Id"));
            int color=marker.getString("Status").equals("LAST_OBSERVED")?BRASS:kind.equals("train")?BRASS:TEAL;
            surface(g,x-9,y-10,22,23,selected?0xff294b45:PANEL);if(hovered||selected){g.hLine(x-8,x+10,y-10,BRASS);g.hLine(x-8,x+10,y+12,BRASS);}icon(g,x-4,y-6,kindIcon(kind),color);HITS.put(hit,marker);
            if(hovered){int tx=Math.clamp(x+17,viewport.x()+4,Math.max(viewport.x()+4,viewport.right()-224)),ty=Math.clamp(y-27,viewport.y()+4,Math.max(viewport.y()+4,viewport.bottom()-47));surface(g,tx,ty,220,41,PANEL);g.drawString(Minecraft.getInstance().font,fit(marker.getString("Name"),202),tx+9,ty+8,TEXT,false);g.drawString(Minecraft.getInstance().font,fit(value(marker.getString("Status")),202),tx+9,ty+23,MUTED,false);}
        }
        g.disableScissor();barX=viewport.x()+8;barY=viewport.y()+8;surface(g,barX,barY,Math.min(220,viewport.width()-16),23,PANEL);for(int i=0;i<KINDS.length;i++){icon(g,barX+9+i*27,barY+4,kindIcon(KINDS[i]),LAYERS.contains(KINDS[i])?TEAL:MUTED);if(mx>=barX+i*27&&mx<barX+(i+1)*27&&my>=barY&&my<barY+23)g.drawString(Minecraft.getInstance().font,text("layer."+KINDS[i]),barX,barY+30,TEXT,false);}
        if(selection!=null){int y=barY+44;surface(g,barX,y,Math.min(240,viewport.width()-16),69,PANEL);g.drawString(Minecraft.getInstance().font,fit(selection.getString("Name"),220),barX+9,y+9,TEAL,false);g.drawString(Minecraft.getInstance().font,fit(selection.getString("Detail"),220),barX+9,y+25,MUTED,false);g.drawString(Minecraft.getInstance().font,text("open_object"),barX+9,y+48,BRASS,false);}
        renderRouteCard(g,map,viewport);
        long age=received==0?Long.MAX_VALUE:Math.max(0,net.minecraft.Util.getMillis()-received);
        if(age>15000)g.drawString(Minecraft.getInstance().font,text("map.stale"),barX,viewport.bottom()-15,BRASS,false);
        else g.drawString(Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("ccec.center.map.age",age/1000).getString(),barX,viewport.bottom()-15,MUTED,false);
    }
    /** Number of rail samples for the legend; filtered to the map's dimension. */
    public static int railCount(ResourceLocation dimension){
        int count=0;String key=dimension.toString();
        for(Tag raw:state.getList("Rails",10))if(((CompoundTag)raw).getString("Dimension").equals(key))count++;
        return count;
    }
    /**
     * Paints the real Create track samples as part of the map paper. CityMapPanel calls this before
     * its markers, so rails never cover cities, trains or the selection ring.
     */
    public static void renderRails(GuiGraphics g,CityMapPanel map){
        if(!enabled||state==null)return;
        if(session&&!LAYERS.contains("line"))return;
        String dimension=map.dimensionId().toString();
        boolean outline=map.zoom()>=0.35,thick=map.zoom()>=1.1;
        UUID highlighted=focus;
        if(selection!=null&&"line".equals(selection.getString("Kind"))&&selection.hasUUID("Id"))highlighted=selection.getUUID("Id");
        boolean routeView=route!=null;
        /*
         * Two passes: the dark bed under every segment first, then all brass cores. Painting a
         * segment's outline and core together makes the next segment's outline bite into the
         * previous core at every joint, which reads as a dashed railway.
         */
        if(outline)railPass(g,map,dimension,highlighted,routeView,thick,false);
        railPass(g,map,dimension,highlighted,routeView,thick,true);
    }
    private static void railPass(GuiGraphics g,CityMapPanel map,String dimension,UUID highlighted,boolean routeView,boolean thick,boolean core){
        for(Tag raw:state.getList("Rails",10)){
            var rail=(CompoundTag)raw;
            if(!rail.getString("Dimension").equals(dimension))continue;
            UUID line=rail.hasUUID("Line")?rail.getUUID("Line"):null;
            int color=highlighted!=null&&highlighted.equals(line)?MapPalette.SELECTED:rail.getBoolean("Focus")?(routeView?0xFFE0A776:0xFFE4C48F):0xFFB7A27C;
            var clipped=MapLineClip.clip(map.worldToScreenX(rail.getDouble("X")),map.worldToScreenY(rail.getDouble("Z")),
                    map.worldToScreenX(rail.getDouble("ToX")),map.worldToScreenY(rail.getDouble("ToZ")),
                    map.viewportLeft(),map.viewportTop(),map.viewportRight(),map.viewportBottom());
            if(clipped==null)continue;
            railSegment(g,clipped,color,thick,core);
        }
    }
    private static void railSegment(GuiGraphics g,MapLineClip.Line line,int color,boolean thick,boolean core){
        int steps=Math.max(Math.abs(line.x2()-line.x1()),Math.abs(line.y2()-line.y1()));
        for(int i=0;i<=steps;i++){
            double t=steps==0?0:(double)i/steps;
            int x=(int)Math.round(line.x1()+t*(line.x2()-line.x1()));
            int y=(int)Math.round(line.y1()+t*(line.y2()-line.y1()));
            if(core)g.fill(x,y,x+(thick?2:1),y+(thick?2:1),color);
            else g.fill(x-1,y-1,x+(thick?3:2),y+(thick?3:2),0x99070C0F);
        }
    }
    static int kindIcon(String kind){return switch(kind){case "point"->4;case "warehouse"->5;case "station"->11;case "train"->2;case "line"->3;case "shipment"->7;default->13;};}
    private static List<ShipmentRouteGeometry.Point> routePoints(ResourceLocation dimension){
        var points=new ArrayList<ShipmentRouteGeometry.Point>();for(Tag raw:state.getList("RouteStops",10)){var stop=(CompoundTag)raw;var location=stop.getCompound("Location");if(!location.getString("Dimension").equals(dimension.toString()))continue;var pos=BlockPos.of(location.getLong("Pos"));points.add(new ShipmentRouteGeometry.Point(pos.getX()+.5,pos.getZ()+.5));}return points;
    }
    private static void renderRoute(GuiGraphics g,CityMapPanel map,ResourceLocation dimension,View view){
        var points=routePoints(dimension);if(points.isEmpty())return;
        if(fitRoute){var bounds=ShipmentRouteGeometry.fit(points,view.width(),view.height(),70,.12,4);if(bounds!=null)map.restoreView(bounds.x(),bounds.z(),bounds.zoom());fitRoute=false;}
        boolean sameDimension=points.size()==state.getList("RouteStops",10).size();
        for(int i=1;sameDimension&&i<points.size();i++){
            var a=points.get(i-1);var b=points.get(i);var clipped=MapLineClip.clip(map.worldToScreenX(a.x()),map.worldToScreenY(a.z()),map.worldToScreenX(b.x()),map.worldToScreenY(b.z()),view.x(),view.y(),view.right(),view.bottom());if(clipped==null)continue;
            int steps=Math.max(Math.abs(clipped.x2()-clipped.x1()),Math.abs(clipped.y2()-clipped.y1()));
            for(int n=0;n<=steps;n++)if(n%10<4){double t=steps==0?0:(double)n/steps;int x=(int)Math.round(clipped.x1()+t*(clipped.x2()-clipped.x1())),y=(int)Math.round(clipped.y1()+t*(clipped.y2()-clipped.y1()));g.fill(x,y,x+1,y+1,0x99DBBA7B);}
            double length=Math.hypot(clipped.x2()-clipped.x1(),clipped.y2()-clipped.y1());if(length>=24)arrow(g,(clipped.x1()+clipped.x2())/2,(clipped.y1()+clipped.y2())/2,(clipped.x2()-clipped.x1())/length,(clipped.y2()-clipped.y1())/length,BRASS);
        }
        int index=0;for(Tag raw:state.getList("RouteStops",10)){var stop=(CompoundTag)raw;var location=stop.getCompound("Location");if(!location.getString("Dimension").equals(dimension.toString()))continue;var point=points.get(index++);int x=map.worldToScreenX(point.x()),y=map.worldToScreenY(point.z());if(!view.contains(x,y))continue;g.fill(x-7,y-7,x+8,y+8,PANEL);g.hLine(x-7,x+7,y-7,BRASS);g.hLine(x-7,x+7,y+7,BRASS);g.drawCenteredString(Minecraft.getInstance().font,Integer.toString(stop.getInt("Index")),x,y-3,BRASS);}
        if(sameDimension&&points.size()>1&&!map.reducedMotion()){
            var cursor=ShipmentRouteGeometry.sample(points,(net.minecraft.Util.getMillis()%6000)/6000.0);if(cursor==null)return;int x=map.worldToScreenX(cursor.point().x()),y=map.worldToScreenY(cursor.point().z());if(view.contains(x,y)){surface(g,x-7,y-7,18,20,PANEL);icon(g,x-4,y-4,7,BRASS);}
        }
    }
    private static void renderRouteCard(GuiGraphics g,CityMapPanel map,View view){
        var stops=state.getList("RouteStops",10);var shipment=state.getCompound("Shipment");if(stops.isEmpty()&&shipment.isEmpty())return;
        int width=Math.min(252,view.width()-20);if(width<150||view.height()<170)return;int x=view.right()-width-10,y=view.y()+10;
        int shown=Math.min(stops.size(),Math.max(0,(view.height()-190)/19)),height=shipment.isEmpty()?100:158;height+=shown*19;
        surface(g,x,y,width,Math.min(height,view.height()-25),PANEL);var font=Minecraft.getInstance().font;
        icon(g,x+11,y+10,3,BRASS);g.drawString(font,text("map.route"),x+32,y+13,BRASS,false);int dy=y+33;
        if(!shipment.isEmpty()){
            g.drawString(font,fit(shipment.getString("Name"),width-22),x+11,dy,TEXT,false);dy+=16;
            String status=shipment.getString("OrderState").equals("CANCELLING")?"CANCELLING":shipment.getString("Status");g.drawString(font,fit(value(status),width-22),x+11,dy,status.equals("CANCELLING")?BRASS:TEAL,false);dy+=18;
            int count=shipment.getInt("Quantity"),delivered=shipment.getInt("Delivered");g.drawString(font,text("delivered")+" "+delivered+" / "+count,x+11,dy,MUTED,false);dy+=14;
            int bar=width-22;g.fill(x+11,dy,x+11+bar,dy+4,EDGE);int filled=(int)(bar*deliveredBar.value(count==0?0:delivered/(double)count,System.nanoTime(),map.reducedMotion(),240));g.fill(x+11,dy,x+11+filled,dy+4,TEAL);dy+=17;
        }
        g.drawString(font,fit(text("map.schematic"),width-22),x+11,dy,BRASS,false);dy+=15;
        g.drawString(font,fit(text("map.direction_demo"),width-22),x+11,dy,MUTED,false);dy+=20;
        for(int i=0;i<shown;i++){var stop=stops.getCompound(i);g.drawString(font,fit(stop.getInt("Index")+". "+stop.getString("Name"),width-22),x+11,dy,TEXT,false);dy+=19;}
        if(stops.size()>shown)g.drawString(font,"+ "+(stops.size()-shown),x+11,dy,MUTED,false);
    }
    private static void arrow(GuiGraphics g,int x,int y,double dx,double dy,int color){
        for(int i=0;i<5;i++){int ax=(int)Math.round(x-dx*i-dy*i*.65),ay=(int)Math.round(y-dy*i+dx*i*.65);g.fill(ax,ay,ax+2,ay+2,color);int bx=(int)Math.round(x-dx*i+dy*i*.65),by=(int)Math.round(y-dy*i-dx*i*.65);g.fill(bx,by,bx+2,by+2,color);}
    }
    public static boolean click(double x,double y,int button){if(!visible||!session||button!=0)return false;if(y>=barY&&y<barY+23&&x>=barX&&x<barX+KINDS.length*27){String kind=KINDS[(int)((x-barX)/27)];if(!LAYERS.remove(kind))LAYERS.add(kind);return true;}
        if(selection!=null&&x>=barX&&x<barX+240&&y>=barY+86&&y<barY+113){var center=new LogisticsCenterScreen();center.focusObject(selection.getString("Kind"),selection.hasUUID("OpenId")?selection.getUUID("OpenId"):selection.getUUID("Id"));Minecraft.getInstance().setScreen(center);visible=false;return true;}
        for(var hit:HITS.entrySet())if(hit.getKey().contains(x,y)){selection=hit.getValue().copy();focus=selection.hasUUID("OpenId")?selection.getUUID("OpenId"):selection.getUUID("Id");fitRoute=false;refresh();return true;}return false;}
}
