package com.example.examplemod.client.logistics;

import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.MapPalette;
import com.example.examplemod.client.map.ShipmentRouteGeometry;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One GUI triangle batch per rail layer. World edge bounds and normals belong to RailMesh. */
final class RailBatch {
    private static final double PADDING=4;
    private static final int BED=0xff28302f, CORE=0xffbba784, FOCUSED=0xffe3b080;
    private record Projected(RailMesh.Edge edge,RailStroke.Line line) {}
    private final ArrayList<RailMesh.Edge> candidates=new ArrayList<>();
    private final ArrayList<Projected> visible=new ArrayList<>();
    private final RailStroke stroke=new RailStroke();
    private RailMesh.Layer previous;
    private float[] vertices=new float[0];
    private UUID styledSelection;
    private double styledFeather;
    private boolean geometryDirty=true;
    private double x0,z0,x1,z1;
    private int left,top,right,bottom;

    void clear() {
        previous=null; styledSelection=null; geometryDirty=true;
        candidates.clear(); visible.clear(); vertices=new float[0];
    }

    private void project(CityMapPanel map,RailMesh.Layer layer) {
        int l=map.viewportLeft(),t=map.viewportTop(),r=map.viewportRight(),b=map.viewportBottom();
        double wx=map.screenToWorldX(l-PADDING),wz=map.screenToWorldZ(t-PADDING);
        double ex=map.screenToWorldX(r+PADDING),ez=map.screenToWorldZ(b+PADDING);
        if (layer==previous && l==left && t==top && r==right && b==bottom &&
                wx==x0 && wz==z0 && ex==x1 && ez==z1) return;
        previous=layer; left=l;top=t;right=r;bottom=b; x0=wx;z0=wz;x1=ex;z1=ez;
        geometryDirty=true;
        candidates.clear(); visible.clear();
        layer.visible(wx,wz,ex,ez,candidates);
        for (var edge:candidates) {
            RailStroke.Line line=RailStroke.clip(map.worldToScreenXExact(edge.x),map.worldToScreenZExact(edge.z),
                    map.worldToScreenXExact(edge.endX),map.worldToScreenZExact(edge.endZ),l-PADDING,t-PADDING,r+PADDING,b+PADDING);
            if (line!=null) visible.add(new Projected(edge,line));
        }
        candidates.clear();
    }

    private void prepareStrokes(UUID selected,double feather) {
        if (!geometryDirty && Objects.equals(styledSelection,selected) && styledFeather==feather) return;
        int stride=RailStroke.FLOATS_PER_STROKE;
        int required=visible.size()*2*stride;
        if (vertices.length<required) vertices=new float[required];
        for (int i=0;i<visible.size();i++) {
            Projected piece=visible.get(i);
            RailMesh.Edge edge=piece.edge;
            RailStroke.Line line=piece.line;
            double coreWidth=edge.selected(selected)?2.3:1.15;
            int start=i*2*stride;
            RailStroke.prepare(vertices,start,line.x(),line.y(),line.endX(),line.endY(),edge.dx,edge.dz,coreWidth+1.5,feather);
            RailStroke.prepare(vertices,start+stride,line.x(),line.y(),line.endX(),line.endY(),edge.dx,edge.dz,coreWidth,feather);
        }
        geometryDirty=false; styledSelection=selected; styledFeather=feather;
    }

    void draw(GuiGraphics graphics,CityMapPanel map,RailMesh.Layer layer,UUID selected,boolean route) {
        project(map,layer);
        if (visible.isEmpty()) return;
        double feather=1/Minecraft.getInstance().getWindow().getGuiScale();
        prepareStrokes(selected,feather);
        RailStroke.Sink sink=begin(graphics);
        try {
            // All beds first, then ordinary cores, then selected cores. Duplicate endpoints use
            // the same opaque interior, so the next sample cannot cut a gap across a junction.
            for(int pass=0;pass<3;pass++) for(int i=0;i<visible.size();i++) {
                RailMesh.Edge edge=visible.get(i).edge;
                boolean highlighted=edge.selected(selected);
                if (pass==1 && highlighted || pass==2 && !highlighted) continue;
                int color=CORE;
                if(pass==0)color=BED;
                else if(highlighted)color=route&&edge.focus?FOCUSED:MapPalette.SELECTED;
                int slot=2*i+(pass==0?0:1);
                RailStroke.submit(vertices,slot*RailStroke.FLOATS_PER_STROKE,color,sink);
            }
        } finally { graphics.bufferSource().endBatch(Layer.TYPE); }
    }

    /** The existing stop schematic is dashed. Its direction arrows describe only stop order. */
    void schematic(GuiGraphics graphics,CityMapPanel map,List<ShipmentRouteGeometry.Point> points) {
        if(points.size()<2)return;
        double feather=1/Minecraft.getInstance().getWindow().getGuiScale();
        RailStroke.Sink sink=begin(graphics);
        try {
            for(int i=1;i<points.size();i++) {
                var a=points.get(i-1);var b=points.get(i);
                var line=RailStroke.clip(map.worldToScreenXExact(a.x()),map.worldToScreenZExact(a.z()),
                        map.worldToScreenXExact(b.x()),map.worldToScreenZExact(b.z()),
                        map.viewportLeft()-PADDING,map.viewportTop()-PADDING,map.viewportRight()+PADDING,map.viewportBottom()+PADDING);
                if(line==null)continue;
                double length=Math.hypot(line.endX()-line.x(),line.endY()-line.y());
                if(length<.001)continue;
                double dx=(line.endX()-line.x())/length, dy=(line.endY()-line.y())/length;
                for(double pos=-(line.skipped()%10);pos<length;pos+=10) {
                    double start=Math.max(0,pos), end=Math.min(length,pos+4);
                    if(end>start)stroke.emit(line.x()+dx*start,line.y()+dy*start,line.x()+dx*end,line.y()+dy*end,
                            dx,dy,1.2,feather,0xbdDBBA7B,sink);
                }
                if(length>=24) {
                    double x=(line.x()+line.endX())/2,y=(line.y()+line.endY())/2;
                    sink.vertex((float)(x+3*dx),(float)(y+3*dy),MapPalette.SELECTED);
                    sink.vertex((float)(x-4*dx+3*dy),(float)(y-4*dy-3*dx),MapPalette.SELECTED);
                    sink.vertex((float)(x-4*dx-3*dy),(float)(y-4*dy+3*dx),MapPalette.SELECTED);
                }
            }
        } finally { graphics.bufferSource().endBatch(Layer.TYPE); }
    }

    private static RailStroke.Sink begin(GuiGraphics graphics) {
        graphics.flush();
        var pose=graphics.pose().last().pose();
        var buffer=graphics.bufferSource().getBuffer(Layer.TYPE);
        return (x,y,color)->buffer.addVertex(pose,x,y,0).setColor(color);
    }

    private static final class Layer extends RenderType {
        static final RenderType TYPE=create("ccec_map_rail",DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES,65536,false,false,CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).createCompositeState(false));
        private Layer() {
            super("ccec_map_rail",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.TRIANGLES,
                    65536,false,false,()->{},()->{});
        }
    }
}
