package com.example.examplemod.client.map;

import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.citymap.CityMapFeature.Kind;
import com.example.examplemod.citymap.CityMapFeature.Shape;
import com.example.examplemod.client.CityPublicDirectory;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class CityCartographyLayer {
    private CityCartographyLayer() {}

    private record Drawing(CityMapFeature object, int minX, int minZ, int maxX, int maxZ, BlockPos label) {
        Drawing(CityMapFeature f) {
            this(f, f.vertices().stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                    f.vertices().stream().mapToInt(BlockPos::getZ).min().orElseThrow(),
                    f.vertices().stream().mapToInt(BlockPos::getX).max().orElseThrow(),
                    f.vertices().stream().mapToInt(BlockPos::getZ).max().orElseThrow(),anchor(f));
        }
    }
    private static List<CityMapFeature> last;
    private static List<Drawing> drawings=List.of();
    private static List<Drawing> minimap=List.of();
    static void minimapFeatures(List<CityMapFeature> features) {
        minimap=features.stream().filter(f->!f.vertices().isEmpty()).map(Drawing::new).toList();
    }
    static void drawMinimap(GuiGraphics g,CityMapPanel map,int x,int y,int size) {
        int right=x+size-1,bottom=y+size-1;
        for(var drawing:minimap) {
            var f=drawing.object;
            if(map.worldToScreenX(drawing.maxX+1)<x || map.worldToScreenX(drawing.minX)>right ||
                    map.worldToScreenY(drawing.maxZ+1)<y || map.worldToScreenY(drawing.minZ)>bottom)continue;
            var points=f.vertices();
            int color=switch(f.kind()){
                case STREET -> 0xc18a9094; case PATH -> 0xb5b0afa9;
                case BUILDING -> 0xc1dfcbb1; case STATION -> 0xc1a6cbd0;
            };
            if(f.shape()==Shape.POINT){
                var p=points.getFirst();g.renderOutline(map.worldToScreenX(p.getX()+.5)-1,map.worldToScreenY(p.getZ()+.5)-1,3,3,color);
            }else{
                int edges=f.shape()==Shape.POLYGON?points.size():points.size()-1;
                for(int i=0;i<edges;i++){
                    var a=points.get(i);var b=points.get((i+1)%points.size());
                    var edge=MapLineClip.clip(map.worldToScreenX(a.getX()+.5),map.worldToScreenY(a.getZ()+.5),
                            map.worldToScreenX(b.getX()+.5),map.worldToScreenY(b.getZ()+.5),x,y,right,bottom);
                    if(edge!=null)stroke(g,edge.x1(),edge.y1(),edge.x2(),edge.y2(),1,color);
                }
            }
        }
        for(var drawing:minimap){
            // Entrances can lie outside the building polygon.
            for(var door:drawing.object.entrances()){
                int px=map.worldToScreenX(door.getX()+.5),py=map.worldToScreenY(door.getZ()+.5);
                if(px<x || px>right || py<y || py>bottom)continue;
                g.fill(px-1,py-1,px+2,py+2,0xb22f423a);
                g.fill(px,py,px+1,py+1,0xffbcf1ca);
            }
        }
    }
    private static List<Drawing> drawings(ResourceLocation dim) {
        var list=CityMapFeatures.all(dim);
        if(list!=last) {
            last=list;
            drawings=list.stream().filter(f->!f.vertices().isEmpty()).map(Drawing::new).toList();
        }
        return drawings;
    }
    private static BlockPos anchor(CityMapFeature f) {
        var points=f.vertices();
        if(f.shape()==Shape.POINT) return points.getFirst();
        if(f.shape()==Shape.LINE) {
            int i=points.size()/2;
            var a=points.get(Math.max(0,i-1)); var b=points.get(i);
            return new BlockPos((a.getX()+b.getX())/2,a.getY(),(a.getZ()+b.getZ())/2);
        }
        double x=0,z=0;
        for(var p:points){x+=p.getX();z+=p.getZ();}
        return BlockPos.containing(x/points.size(),points.getFirst().getY(),z/points.size());
    }
    private static int color(Kind kind) {
        return switch(kind) {case STATION -> 0xff67bcd8; case BUILDING -> 0xffdfb98d;
            case STREET -> 0xffecc570; case PATH -> 0xff99d6aa;};
    }
    private static Component title(CityMapFeature f) {
        return Component.literal(f.label(CityMapFeatures::get));
    }
    public static CityMapPanel.Selection selection(CityMapFeature f) {
        if(f==null || f.vertices().isEmpty())return null;
        var city=CityPublicDirectory.profile(f.city());
        var details=new ArrayList<Component>();
        if(f.kind()==Kind.BUILDING && !f.houseNumber().isBlank()){
            var address=Component.translatable("gui.createcityexpansioncore.cartography.building_number",f.houseNumber());
            details.add(city==null?address:Component.literal(city.name()+" · ").append(address));
        }else if(city!=null)details.add(Component.literal(city.name()));
        if(!f.description().isBlank()) details.add(Component.literal(f.description()));
        details.add(Component.translatable("gui.createcityexpansioncore.cartography.entrance_count",f.entrances().size()));
        var icon=switch(f.kind()) {case STATION -> AtlasPlaceIcon.STATION; case BUILDING -> AtlasPlaceIcon.valueOf(f.icon().name()); default -> null;};
        return new CityMapPanel.Selection(CityMapPanel.SelectionKind.CARTOGRAPHY,f.id(),title(f),anchor(f),
                city!=null && city.own(),details,ItemStack.EMPTY,false,icon);
    }
    public static CityMapPanel.Selection selection(UUID id,ResourceLocation dim) {
        var f=CityMapFeatures.get(id);
        return f!=null && f.dimension().equals(dim)?selection(f):null;
    }
    private static boolean visible(CityMapPanel m,CityMapFeature f) {
        return m.zoom()>=.55 || f.kind()==Kind.STATION ||
                (m.getSelection()!=null && f.id().equals(m.getSelection().id()));
    }
    public static void draw(GuiGraphics g,CityMapPanel m,ResourceLocation dim,int x,int y,int w,int h) {
        for(var d:drawings(dim)) {
            var f=d.object;
            if(!visible(m,f) || m.worldToScreenX(d.maxX+1)<x-12 || m.worldToScreenY(d.maxZ+1)<y-12 ||
                    m.worldToScreenX(d.minX)>x+w+12 || m.worldToScreenY(d.minZ)>y+h+12)continue;
            boolean selected=m.getSelection()!=null && f.id().equals(m.getSelection().id());
            paint(g,m,f.kind(),f.shape(),f.vertices(),selected?f.entrances():List.of(),selected,false);
            int sx=m.worldToScreenX(d.label.getX()+.5), sy=m.worldToScreenY(d.label.getZ()+.5);
            if(isRoad(f.kind()) && f.shape()==Shape.LINE) {
                if(selected || m.zoom()>=1)offerRoadName(g,m,f,selected);
                continue;
            }
            var icon=f.kind()==Kind.STATION?AtlasPlaceIcon.STATION:
                    f.kind()==Kind.BUILDING?AtlasPlaceIcon.valueOf(f.icon().name()):null;
            int radius=icon==null?5:AtlasPlaceIcon.radius(m.zoom());
            if(icon!=null) {
                var hovered=m.getHoveredSelection();
                icon.badge(g,sx,sy,radius,selected,hovered!=null && f.id().equals(hovered.id()));
                m.labels().reserve(sx-radius,sy-radius,2*radius,2*radius);
            }
            if(sx<x || sx>x+w || sy<y || sy>y+h || !selected && m.zoom()<1.0)continue;
            String label=f.kind()==Kind.BUILDING && !selected && !f.houseNumber().isBlank()?f.houseNumber():title(f).getString();
            m.labels().offer("cartography/"+f.id(),label,color(f.kind()),sx,sy,radius+2,
                    Minecraft.getInstance().font.width(label),selected?MapLabelLayout.Priority.SELECTED:MapLabelLayout.Priority.NORMAL);
        }
    }
    private static boolean isRoad(Kind kind) { return kind==Kind.STREET || kind==Kind.PATH; }

    private static void offerRoadName(GuiGraphics g,CityMapPanel map,CityMapFeature feature,boolean selected) {
        String name=feature.name();
        if(name.isBlank())return;
        var font=Minecraft.getInstance().font;
        int width=font.width(name),height=font.lineHeight;
        var points=feature.vertices();
        // Join consecutive collinear segments before fitting the name. A vertex added on
        // a straight street should not cut its available lettering space in half.
        MapLineClip.Line best=null;
        double longest=width+6;
        for(int i=1;i<points.size();i++){
            var a=points.get(i-1);
            var b=points.get(i);
            while(i+1<points.size()){
                var next=points.get(i+1);
                long dx=(long)b.getX()-a.getX(),dz=(long)b.getZ()-a.getZ();
                long nx=(long)next.getX()-b.getX(),nz=(long)next.getZ()-b.getZ();
                if(dx*nz!=dz*nx || dx*nx+dz*nz<=0)break;
                b=next;i++;
            }
            var part=MapLineClip.clip(map.worldToScreenX(a.getX()+.5),map.worldToScreenY(a.getZ()+.5),
                    map.worldToScreenX(b.getX()+.5),map.worldToScreenY(b.getZ()+.5),
                    map.viewportLeft(),map.viewportTop(),map.viewportRight(),map.viewportBottom());
            if(part==null)continue;
            double length=Math.hypot(part.x2()-part.x1(),part.y2()-part.y1());
            if(length>=longest){longest=length;best=part;}
        }
        if(best==null)return;
        int dx=best.x2()-best.x1(),dy=best.y2()-best.y1();
        if(dx<0 || dx==0 && dy>0){dx=-dx;dy=-dy;}
        float angle=(float)Math.atan2(dy,dx);
        int x=(best.x1()+best.x2())/2,y=(best.y1()+best.y2())/2;
        int boxW=(int)Math.ceil((Math.abs(dx)*width+Math.abs(dy)*height)/longest)+4;
        int boxH=(int)Math.ceil((Math.abs(dy)*width+Math.abs(dx)*height)/longest)+4;
        map.labels().offerRoad(selected,feature.id().toString(),x-boxW/2,y-boxH/2,boxW,boxH,()->{
            var pose=g.pose();
            pose.pushPose();pose.translate(x,y,0);pose.mulPose(Axis.ZP.rotation(angle));
            int tx=-width/2,ty=-height/2;
            g.drawString(font,name,tx-1,ty,0xff23282a,false);
            g.drawString(font,name,tx+1,ty,0xff23282a,false);
            g.drawString(font,name,tx,ty-1,0xff23282a,false);
            g.drawString(font,name,tx,ty+1,0xff23282a,false);
            g.drawString(font,name,tx,ty,color(feature.kind()),false);
            pose.popPose();
        });
    }

    public static void drawDraft(GuiGraphics g,CityMapPanel m,Kind kind,Shape shape,List<BlockPos> vertices,List<BlockPos> entrances) {
        g.enableScissor(m.viewportLeft(),m.viewportTop(),m.viewportRight(),m.viewportBottom());
        try { paint(g,m,kind,shape,vertices,entrances,true,true); }
        finally { g.disableScissor(); }
    }
    private static void paint(GuiGraphics g,CityMapPanel m,Kind kind,Shape shape,List<BlockPos> vertices,List<BlockPos> entrances,boolean selected,boolean draft) {
        int n=vertices.size(), color=selected?0xffffe598:color(kind);
        int[] xs=new int[n],ys=new int[n];
        for(int i=0;i<n;i++){var p=vertices.get(i); xs[i]=m.worldToScreenX(p.getX()+.5);ys[i]=m.worldToScreenY(p.getZ()+.5);}
        if(shape==Shape.POLYGON && n>=3){
            int lo=Math.max(m.viewportTop(),Arrays.stream(ys).min().orElseThrow());
            int hi=Math.min(m.viewportBottom(),Arrays.stream(ys).max().orElseThrow());
            int[] cuts=new int[n];
            for(int y=lo;y<hi;y++){
                int count=0;
                for(int i=0,j=n-1;i<n;j=i++)if((ys[i]>y)!=(ys[j]>y))
                    cuts[count++]=(int)Math.round(xs[i]+(y-ys[i])*(double)(xs[j]-xs[i])/(ys[j]-ys[i]));
                Arrays.sort(cuts,0,count);
                for(int i=1;i<count;i+=2)g.fill(cuts[i-1],y,cuts[i],y+1,(selected?0x60000000:0x40000000)|(color&0xffffff));
            }
        }
        if(isRoad(kind) && shape==Shape.LINE) {
            boolean street=kind==Kind.STREET;
            int edge=selected?0xffe0e3de:street?0xff4c5053:0xff62645f;
            for(int i=1;i<n;i++)stroke(g,xs[i-1],ys[i-1],xs[i],ys[i],street?5:3,edge);
            color=street?0xff858a8d:0xffb0afa9;
        }
        for(int i=1;i<n;i++)stroke(g,xs[i-1],ys[i-1],xs[i],ys[i],kind==Kind.STREET?3:2,color);
        if(shape==Shape.POLYGON && n>=3)stroke(g,xs[n-1],ys[n-1],xs[0],ys[0],2,color);
        if(draft || shape==Shape.POINT)for(int i=0;i<n;i++)g.fill(xs[i]-2,ys[i]-2,xs[i]+3,ys[i]+3,color);
        for(var e:entrances){
            int ex=m.worldToScreenX(e.getX()+.5),ey=m.worldToScreenY(e.getZ()+.5);
            g.fill(ex-3,ey-3,ex+4,ey+4,0xff172e2d);g.renderOutline(ex-3,ey-3,7,7,0xff9ff2bc);
        }
    }
    static void stroke(GuiGraphics g,int ax,int ay,int bx,int by,int width,int color) {
        double dx=bx-ax,dy=by-ay;
        var pose=g.pose();pose.pushPose();pose.translate(ax,ay,0);
        pose.mulPose(Axis.ZP.rotation((float)Math.atan2(dy,dx)));
        g.fill(0,-width/2,(int)Math.ceil(Math.hypot(dx,dy))+1,(width+1)/2,color);pose.popPose();
    }
    public static void drawPassengerRails(GuiGraphics g,CityMapPanel map) {
        PassengerMapLayer.drawRails(g,map);
    }
    public static CityMapPanel.Selection hit(CityMapPanel m,double mouseX,double mouseY) {
        double x=m.screenToWorldX(mouseX)-.5,z=m.screenToWorldZ(mouseY)-.5;
        double radius=6/Math.max(.01,Math.abs(m.worldToScreenXExact(1)-m.worldToScreenXExact(0)));
        CityMapFeature best=null;double nearest=Double.POSITIVE_INFINITY;
        for(var d:drawings(m.dimensionId())){
            var f=d.object;
            if(!visible(m,f))continue;
            if((f.kind()==Kind.STATION || f.kind()==Kind.BUILDING) &&
                    AtlasPlaceIcon.contains(mouseX,mouseY,m.worldToScreenX(d.label.getX()+.5),
                    m.worldToScreenY(d.label.getZ()+.5),AtlasPlaceIcon.radius(m.zoom())))return selection(f);
            if(x<d.minX-radius || x>d.maxX+radius || z<d.minZ-radius || z>d.maxZ+radius)continue;
            var pts=f.vertices();
            boolean inside=false;double distance=Double.POSITIVE_INFINITY;
            for(int i=0;i<pts.size();i++){
                var a=pts.get(i);var b=i>0?pts.get(i-1):f.shape()==Shape.POLYGON?pts.getLast():a;
                double ax=a.getX(),az=a.getZ(),dx=b.getX()-ax,dz=b.getZ()-az, len=dx*dx+dz*dz;
                double t=len==0?0:Math.clamp(((x-ax)*dx+(z-az)*dz)/len,0,1);
                distance=Math.min(distance,Math.hypot(x-ax-t*dx,z-az-t*dz));
                if(f.shape()==Shape.POLYGON && (az>z)!=(b.getZ()>z) && x<ax+(z-az)*dx/dz)inside=!inside;
            }
            double priority=distance<=radius?distance:inside?radius+1:Double.POSITIVE_INFINITY;
            if(priority<nearest){nearest=priority;best=f;}
        }
        return selection(best);
    }
}
