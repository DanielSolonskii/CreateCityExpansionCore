package com.example.examplemod.client.map;

import com.example.examplemod.city.TerrainPyramid;

/** Pure material-preserving hillshade. Unknown neighbours never introduce artificial cliffs. */
public final class TerrainRelief {
    private TerrainRelief(){}
    public static int[] raster(int[] colors,short[] halo,int level) {
        // At block scale keep the actual samples; smoothing would erase narrow paths/buildings.
        if(level>0)colors=TerrainSurfaceFilter.smooth(colors,32);
        int[] result=colors.clone();
        for(int z=0;z<32;z++)for(int x=0;x<32;x++) {
            int i=z*32+x,c=colors[i];if((c>>>24)==0){result[i]=0;continue;}
            if(halo.length!=1156)continue;
            int at=(z+1)*34+x+1,h=halo[at];if(h==TerrainPyramid.NO_HEIGHT)continue;
            // Sobel averages the perpendicular neighbours, avoiding one-block contour-like banding.
            double dx=height(halo[at-33],h)+2*height(halo[at+1],h)+height(halo[at+35],h)
                    -height(halo[at-35],h)-2*height(halo[at-1],h)-height(halo[at+33],h);
            double dz=height(halo[at+33],h)+2*height(halo[at+34],h)+height(halo[at+35],h)
                    -height(halo[at-35],h)-2*height(halo[at-34],h)-height(halo[at-33],h);
            double slope=(dx-dz)/(8.0*(1<<level));
            double factor=1+Math.max(-.18,Math.min(.14,slope*.065));
            // Water stays quiet; cliffs in adjacent land do not turn the water into corrugated metal.
            int r=c>>16&255,g=c>>8&255,b=c&255;
            if(b>r*1.25&&b>g*1.12)factor=1+(factor-1)*.2;
            result[i]=(c&0xFF000000)|channel(r,factor)<<16|channel(g,factor)<<8|channel(b,factor);
        }
        return result;
    }
    private static int height(short value,int fallback){return value==TerrainPyramid.NO_HEIGHT?fallback:value;}
    private static int channel(int value,double factor){return Math.max(0,Math.min(255,(int)Math.round(value*factor)));}
}
