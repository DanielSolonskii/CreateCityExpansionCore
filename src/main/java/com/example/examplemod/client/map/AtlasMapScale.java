package com.example.examplemod.client.map;

/** Presentation-only zoom and bounded near-centre terrain loading at regional scale. */
public final class AtlasMapScale {
    public static final double MIN_ZOOM=0.0625, MAX_ZOOM=4;
    public static final int REQUEST_SIDE=80, MAX_VISIBLE_TILES=8192;
    public static final int ACTIVE_TILES=2560, ATLAS_SIZE=1024, ATLAS_CELL=34, ATLAS_PAGES=4;
    public static int terrainLevel(double zoom){return terrainLevel(zoom,0,0,1);}
    public static int terrainLevel(double zoom,int width,int height){
        return terrainLevel(zoom,width,height,1);
    }
    /** Prefer approximately one physical pixel per sample; area-averaged parents prevent aliasing. */
    public static int terrainLevel(double zoom,int width,int height,double guiScale){
        int level=0;
        while(level<7&&(1<<level)*zoom*Math.max(1,guiScale)<.8)level++;
        while(level<7&&(Math.ceil(width/(zoom*span(level)))+4)*(Math.ceil(height/(zoom*span(level)))+4)>ACTIVE_TILES)level++;
        return level;
    }
    public static int span(int level){return 32<<level;}
    private AtlasMapScale() {}
    public record Tiles(int minX,int maxX,int minZ,int maxZ) {
        public long count(){return (long)(maxX-minX+1)*(maxZ-minZ+1);}
        public boolean contains(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
    }
    public static Tiles requestWindow(int minX,int maxX,int minZ,int maxZ,int centerX,int centerZ) {
        var visible = new Tiles(minX,maxX,minZ,maxZ);
        if (visible.count() <= (long) REQUEST_SIDE * REQUEST_SIDE) return visible;
        int left=Math.max(minX,Math.min(centerX-REQUEST_SIDE/2,maxX-REQUEST_SIDE+1));
        int top=Math.max(minZ,Math.min(centerZ-REQUEST_SIDE/2,maxZ-REQUEST_SIDE+1));
        return new Tiles(left,Math.min(maxX,left+REQUEST_SIDE-1),top,Math.min(maxZ,top+REQUEST_SIDE-1));
    }
    public static double clamp(double zoom){return Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,zoom));}
}
