package com.example.examplemod.client.screen;

/** A bounded, centred Pylon panel. Only the world map owns the whole screen. */
public record CityPylonWorkspaceLayout(int x, int y, int width, int height,
        int virtualWidth, int virtualHeight, float scale, float originX, float originY,
        int navPitch, int frameX, int frameY, int frameWidth, int frameHeight, int sidebarWidth) {
    public static CityPylonWorkspaceLayout calculate(int screenWidth, int screenHeight, boolean management) {
        int fw = Math.max(1, Math.min(760, screenWidth - 24));
        int fh = Math.max(1, Math.min(420, screenHeight - 24));
        int fx = (screenWidth-fw)/2, fy = (screenHeight-fh)/2;
        int sidebar = fw < 600 ? 102 : 138;
        int x = fx+sidebar+13, y = fy+43;
        int width = Math.max(1, fw-sidebar-26), height = Math.max(1, fh-64);
        int vw = management ? 440 : Math.max(500,width), vh = management ? 260 : Math.max(356,height);
        float scale = Math.min(1,Math.min(width/(float)vw,height/(float)vh));
        float ox = x+(width-vw*scale)/2-(management?180*scale:0);
        float oy = y+(height-vh*scale)/2-(management?76*scale:0);
        int navTop = fh < 240 ? 54 : fh < 300 ? 82 : 96;
        int pitch = Math.min(29, Math.max(16,(fh-26-navTop+4)/5));
        return new CityPylonWorkspaceLayout(x,y,width,height,vw,vh,scale,ox,oy,pitch,fx,fy,fw,fh,sidebar);
    }
    public int navY(int index) { return frameY+(frameHeight<240?54:frameHeight<300?82:96)+index*navPitch; }
    public int navHeight() { return navPitch-4; }
    public boolean contains(double px,double py) { return px>=x&&px<x+width&&py>=y&&py<y+height; }
    public double localX(double px) { return (px-originX)/scale; }
    public double localY(double py) { return (py-originY)/scale; }
}
