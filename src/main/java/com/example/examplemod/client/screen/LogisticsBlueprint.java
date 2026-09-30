package com.example.examplemod.client.screen;

import com.example.examplemod.economy.FreightPoint;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.railway.RailwayStationSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Actual server rail geometry, projected into a local blueprint. Never a route planner. */
final class LogisticsBlueprint {
    private List<RailwayMapPayload.TrackSegment> tracks=List.of();
    private List<int[]> spans=List.of();
    private List<Marker> markers=List.of();
    private int cachedW=-1,cachedH=-1;
    private boolean received;
    private double centerX,centerZ,extentX=192,extentZ=192;
    private record Marker(double x,double z,Component label,boolean freight,int color) {}

    void receive(RailwayMapPayload payload) {
        tracks=payload.segments();received=true;cachedW=-1;
    }
    void update(BlockPos center,ResourceLocation dimension,List<FreightPoint> points,
                List<RailwayStationSnapshot> stations) {
        List<Marker> next=new ArrayList<>();
        double minX=center.getX()-96,maxX=center.getX()+96,minZ=center.getZ()-96,maxZ=center.getZ()+96;
        for(FreightPoint p:points)if(!p.removed&&p.dimension.equals(dimension)) {
            next.add(new Marker(p.postbox.getX(),p.postbox.getZ(),Component.literal(p.name),true,LogisticsStyle.statusColor(p.status)));
            minX=Math.min(minX,p.postbox.getX()-32);maxX=Math.max(maxX,p.postbox.getX()+32);
            minZ=Math.min(minZ,p.postbox.getZ()-32);maxZ=Math.max(maxZ,p.postbox.getZ()+32);
        }
        for(var s:stations)if(s.cityOwned()&&s.dimension().equals(dimension)) {
            next.add(new Marker(s.pos().getX(),s.pos().getZ(),Component.literal(s.name()),false,LogisticsStyle.BLUE));
            minX=Math.min(minX,s.pos().getX()-32);maxX=Math.max(maxX,s.pos().getX()+32);
            minZ=Math.min(minZ,s.pos().getZ()-32);maxZ=Math.max(maxZ,s.pos().getZ()+32);
        }
        double cx=(minX+maxX)/2,cz=(minZ+maxZ)/2,ex=maxX-minX,ez=maxZ-minZ;
        if(!next.equals(markers)||cx!=centerX||cz!=centerZ||ex!=extentX||ez!=extentZ)cachedW=-1;
        markers=List.copyOf(next);centerX=cx;centerZ=cz;extentX=ex;extentZ=ez;
    }
    private double scale(int w,int h) { return Math.min((w-24)/extentX,(h-24)/extentZ); }
    private int px(double x,int w,int h) { return (int)Math.round(w/2.0+(x-centerX)*scale(w,h)); }
    private int pz(double z,int w,int h) { return (int)Math.round(h/2.0+(z-centerZ)*scale(w,h)); }

    /** Rasterise only when geometry/size changes, then batch contiguous horizontal pixels. */
    private void prepare(int w,int h) {
        if(cachedW==w&&cachedH==h)return;
        cachedW=w;cachedH=h;
        boolean[] pixels=new boolean[w*h];
        for(var s:tracks) {
            if(!Float.isFinite(s.x1())||!Float.isFinite(s.z1())||!Float.isFinite(s.x2())||!Float.isFinite(s.z2()))continue;
            double x1=w/2.0+(s.x1()-centerX)*scale(w,h),y1=h/2.0+(s.z1()-centerZ)*scale(w,h);
            double dx=(s.x2()-s.x1())*scale(w,h),dy=(s.z2()-s.z1())*scale(w,h);
            double lo=0,hi=1;double[] p={-dx,dx,-dy,dy},q={x1,w-1-x1,y1,h-1-y1};boolean reject=false;
            for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)reject=true;}else{double t=q[i]/p[i];if(p[i]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);}}
            if(reject||lo>hi)continue;
            double ax=x1+lo*dx,ay=y1+lo*dy,bx=x1+hi*dx,by=y1+hi*dy;
            int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(bx-ax),Math.abs(by-ay))));
            for(int i=0;i<=steps;i++){int x=(int)Math.round(ax+(bx-ax)*i/steps),y=(int)Math.round(ay+(by-ay)*i/steps);if(x>=0&&x<w&&y>=0&&y<h)pixels[y*w+x]=true;}
        }
        List<int[]> next=new ArrayList<>();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(pixels[y*w+x]){int first=x;while(x+1<w&&pixels[y*w+x+1])x++;next.add(new int[]{first,y,x+1});}
        spans=next;
    }
    void render(GuiGraphics g,Font font,int x,int y,int w,int h,int ticks,int mx,int my) {
        if(w<32||h<32)return;
        prepare(w,h);g.fill(x,y,x+w,y+h,0xFF102F46);
        for(int xx=0;xx<w;xx+=20)g.fill(x+xx,y,x+xx+1,y+h,0xFF193F56);
        for(int yy=0;yy<h;yy+=20)g.fill(x,y+yy,x+w,y+yy+1,0xFF193F56);
        for(int[] span:spans)g.fill(x+span[0],y+span[1],x+span[2],y+span[1]+1,0xFF78ADC6);
        Component hover=null;
        for(Marker m:markers) {
            int xx=x+px(m.x,w,h),yy=y+pz(m.z,w,h),r=m.freight?3:2;
            if(xx<x+5||xx>x+w-5||yy<y+5||yy>y+h-5)continue;
            if(m.freight){
                int alpha=m.color==LogisticsStyle.GREEN?45+(int)(20*(1+Math.sin(ticks/12.0))):55;
                g.renderOutline(xx-6,yy-6,13,13,(alpha<<24)|(m.color&0xFFFFFF));
            }
            g.fill(xx-r,yy-r,xx+r+1,yy+r+1,m.color);
            if(Math.abs(mx-xx)<7&&Math.abs(my-yy)<7)hover=m.label;
        }
        g.drawString(font,"N ↑",x+w-28,y+7,LogisticsStyle.MUTED,false);
        if(!received||spans.isEmpty())LogisticsStyle.text(g,font,LogisticsStyle.tr(received?"map.no_tracks":"map.waiting"),x+8,y+h-17,w-16,LogisticsStyle.MUTED);
        if(hover!=null){int tw=Math.min(w-12,font.width(hover)+10);g.fill(x+6,y+6,x+6+tw,y+23,0xEF101A21);LogisticsStyle.text(g,font,hover,x+11,y+11,tw-10,LogisticsStyle.TEXT);}
    }
}
