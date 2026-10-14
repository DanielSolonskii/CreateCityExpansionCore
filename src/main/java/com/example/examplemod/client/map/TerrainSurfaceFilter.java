package com.example.examplemod.client.map;

/** Small edge-aware colour filter, paid once per texture build; no invented elevation or biomes. */
final class TerrainSurfaceFilter {
    private TerrainSurfaceFilter() {}
    static int[] smooth(int[] source,int size) {
        int[] result=source.clone();
        // Keep tile edges exact: independent tiles must never acquire mismatched filtered seams.
        for(int z=1;z<size-1;z++)for(int x=1;x<size-1;x++) {
            int index=z*size+x,c=source[index];
            if((c>>>24)!=255)continue;
            int r=MapPalette.red(c)*4,g=MapPalette.green(c)*4,b=MapPalette.blue(c)*4,n=4;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
                if(dx==0&&dz==0)continue;
                int p=source[index+dz*size+dx];
                int distance=Math.max(Math.abs(MapPalette.red(p)-MapPalette.red(c)),
                        Math.max(Math.abs(MapPalette.green(p)-MapPalette.green(c)),Math.abs(MapPalette.blue(p)-MapPalette.blue(c))));
                if((p>>>24)!=255||distance>28)continue;
                r+=MapPalette.red(p);g+=MapPalette.green(p);b+=MapPalette.blue(p);n++;
            }
            result[index]=0xFF000000|(r/n)<<16|(g/n)<<8|b/n;
        }
        return result;
    }
}
