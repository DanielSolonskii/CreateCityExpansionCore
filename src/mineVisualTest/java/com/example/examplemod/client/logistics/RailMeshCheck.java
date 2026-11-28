package com.example.examplemod.client.logistics;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Exercises the same decoded geometry, clipping and triangle emission as LogisticsMapOverlay. */
public final class RailMeshCheck {
    private static void require(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }

    private static CompoundTag sample(String dim,double x,double z,double ex,double ez) {
        var tag=new CompoundTag();
        tag.putString("Dimension",dim);
        tag.putDouble("X",x); tag.putDouble("Z",z); tag.putDouble("ToX",ex); tag.putDouble("ToZ",ez);
        return tag;
    }

    public static void run() {
        var rails=new ListTag();
        var line=UUID.randomUUID();
        double origin=-29_999_970.25;
        var e=sample("overworld",origin,20.25,origin+20,20.25);
        e.putUUID("Line",line);
        rails.add(e);
        // Same location and dimension on disconnected legs do not cause a synthetic link.
        var active=sample("overworld",origin+35,20.25,origin+41,25.25);
        active.putString("Phase","ROAD");rails.add(active);
        rails.add(sample("nether",origin,20.25,origin+20,20.25));
        rails.add(sample("overworld",origin,20.25,origin,20.25));
        rails.add(sample("overworld",0,0,Double.NaN,0));
        rails.add(new CompoundTag());
        for(int i=0;i<200;i++) rails.add(sample("overworld",i*8,300,i*8+6,305));
        var cache=new RailMesh.Cache();
        var model=cache.from(rails);
        require(cache.from(rails)==model && cache.from(rails.copy())==model,"stable snapshots should reuse geometry");
        var surface=model.dimension("overworld");
        require(surface.edges.size()==202 && model.dimension("nether").edges.size()==1,"invalid and cross-dimensional samples");
        require(surface.routeEnds.size()==2 && model.exactRoute,"route framing must use received focused samples");
        var visible=new ArrayList<RailMesh.Edge>();
        surface.visible(origin+1,20,origin+5,21,visible);
        require(visible.size()==1 && visible.getFirst().selected(line) && visible.getFirst().x==origin,"fractional coordinates, selection and BVH crossing");
        visible.clear();
        surface.visible(origin+26,19,origin+30,21,visible);
        require(visible.isEmpty(),"disconnected samples became a track");
        var changed=rails.copy();changed.remove(0);
        require(cache.from(changed)!=model,"changed snapshot must replace the old geometry");
        require(cache.from(null)==RailMesh.EMPTY,"close must release cached snapshot");

        var clipped=RailStroke.clip(-90_000_000.25,7.625,80_000_000.5,7.625,-4,-4,404,304);
        require(clipped!=null && Math.abs(clipped.x()+4)<1e-7 && Math.abs(clipped.endX()-404)<1e-7
                && clipped.y()==7.625 && clipped.skipped()>80_000_000,"clip before floating-point vertex conversion");
        require(RailStroke.clip(-9,-6,-2,-6,0,0,9,9)==null,"off-screen geometry");
        var pen=new RailStroke();
        for(double length:new double[]{.015,20,1400}) {
            double tx=.6, ty=.8, x=50.125,y=91.375;
            int[] solid={0},clear={0};
            double[] normal={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
            pen.emit(x,y,x+tx*length,y+ty*length,tx,ty,2.4,.5,0xffbcaa79,(vx,vy,color)->{
                require(Float.isFinite(vx)&&Float.isFinite(vy),"finite geometry");
                double distance=-(vx-x)*ty+(vy-y)*tx;
                normal[0]=Math.min(normal[0],distance);
                normal[1]=Math.max(normal[1],distance);
                if ((color>>>24)==255)solid[0]++; else if((color>>>24)==0)clear[0]++;
            });
            require(solid[0]+clear[0]==84 && clear[0]>0,"bounded antialiased triangles at all zoom levels");
            require(Math.abs(normal[1]-normal[0]-2.9)<1e-4,"screen width should be independent of projected length");
        }
    }
    private RailMeshCheck() {}
}
