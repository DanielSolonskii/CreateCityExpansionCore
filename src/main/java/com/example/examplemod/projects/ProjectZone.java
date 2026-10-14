package com.example.examplemod.projects;

import com.example.examplemod.city.CityPlot;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** A horizontal construction footprint. The same geometry drives validation, paint and picking. */
public record ProjectZone(ResourceLocation dimension, Shape shape, int x1, int z1, int x2, int z2) {
    public enum Shape { POINT, RECTANGLE, LINE }
    public static final int MAX_SPAN = 4096;
    public ProjectZone {
        Objects.requireNonNull(dimension); Objects.requireNonNull(shape);
        if (Math.abs((long)x1)>29_999_984 || Math.abs((long)x2)>29_999_984
                || Math.abs((long)z1)>29_999_984 || Math.abs((long)z2)>29_999_984
                || Math.abs((long)x2-x1)>MAX_SPAN || Math.abs((long)z2-z1)>MAX_SPAN)
            throw new IllegalArgumentException("Invalid project footprint");
        if (shape==Shape.POINT) { x2=x1; z2=z1; }
    }
    public int minX(){return Math.min(x1,x2);} public int maxX(){return Math.max(x1,x2);}
    public int minZ(){return Math.min(z1,z2);} public int maxZ(){return Math.max(z1,z2);}
    public Set<CityPlot> plots() {
        Set<CityPlot> result=new LinkedHashSet<>();
        for(int x=CityPlot.plotXOf(minX());x<=CityPlot.plotXOf(maxX());x++)
            for(int z=CityPlot.plotZOf(minZ());z<=CityPlot.plotZOf(maxZ());z++) {
                CityPlot p=new CityPlot(dimension,x,z);
                if(shape!=Shape.LINE || intersects(p.minX(),p.minZ(),p.maxX(),p.maxZ())) result.add(p);
            }
        return Set.copyOf(result);
    }
    // Slab clipping includes corner touches: no diagonal shortcut across an unowned plot.
    private boolean intersects(double left,double top,double right,double bottom) {
        double low=0,high=1;
        double[] origin={x1,z1},delta={(double)x2-x1,(double)z2-z1};
        double[] min={left-.5,top-.5},max={right+.5,bottom+.5};
        for(int axis=0;axis<2;axis++) {
            if(delta[axis]==0) { if(origin[axis]<min[axis]||origin[axis]>max[axis])return false; }
            else { double a=(min[axis]-origin[axis])/delta[axis],b=(max[axis]-origin[axis])/delta[axis];
                low=Math.max(low,Math.min(a,b));high=Math.min(high,Math.max(a,b));if(low>high)return false; }
        }
        return true;
    }
    public double distance(double x,double z) {
        if(shape==Shape.RECTANGLE)return Math.hypot(Math.max(minX()-x,Math.max(0,x-maxX())),Math.max(minZ()-z,Math.max(0,z-maxZ())));
        double dx=(double)x2-x1,dz=(double)z2-z1,length=dx*dx+dz*dz;
        double t=length==0?0:Math.clamp(((x-x1)*dx+(z-z1)*dz)/length,0,1);
        return Math.hypot(x-x1-t*dx,z-z1-t*dz);
    }
}
