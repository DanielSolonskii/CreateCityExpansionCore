package com.example.examplemod.client.map;

import com.example.examplemod.city.CityPlot;
import java.util.*;

/** Pure atlas scale policy and exact exterior edges, shared by every city. */
public final class TerritoryPresentation {
    private TerritoryPresentation() {}
    public record Style(int fillAlpha,int borderAlpha) {}
    public record Edge(int x1,int z1,int x2,int z2) {}
    public static int cityRadius(double zoom){return zoom>=1.6?8:11;}
    public static Style style(double zoom,int mode,boolean projects,boolean picking) {
        if(mode==2&&!picking)return new Style(0,0);
        double region=smooth(.12,.38,zoom),detail=smooth(1.1,2.4,zoom);
        if(mode==1)region=1;
        int fill=(int)Math.round(40*region*(1-detail)*(projects?.45:1));
        int border=(int)Math.round(region*(190-100*detail));
        if(picking)return new Style(Math.max(fill,12),220);
        return new Style(fill,border);
    }
    private static double smooth(double start,double end,double value) {
        double t=Math.clamp((value-start)/(end-start),0,1);return t*t*(3-2*t);
    }
    public static List<Edge> boundary(Set<CityPlot> plots) {
        return com.example.examplemod.city.CityClaims.outline(plots).stream()
                .map(e->new Edge(e[0],e[1],e[2],e[3])).toList();
    }
}
