package com.example.examplemod.client.screen;

import com.example.examplemod.client.uiedit.UiEditLayout;

/** A bounded, centred Pylon panel. Only the world map owns the whole screen. */
public record CityPylonWorkspaceLayout(int x, int y, int width, int height,
        int virtualWidth, int virtualHeight, float scale, float originX, float originY,
        int navPitch, int frameX, int frameY, int frameWidth, int frameHeight, int sidebarWidth) {
    public static CityPylonWorkspaceLayout calculate(int screenWidth, int screenHeight, boolean management) {
        return calculate(screenWidth, screenHeight, management, false);
    }

    static CityPylonWorkspaceLayout calculate(int screenWidth, int screenHeight, boolean management, boolean center) {
        int fw = Math.max(1, Math.min(center ? 720 : 760, screenWidth - 24));
        int fh = Math.max(1, Math.min(center ? 520 : 420, screenHeight - 24));
        int fx = (screenWidth-fw)/2, fy = (screenHeight-fh)/2;
        // UI-редактор (F7): каркас правится целиком — сайдбар, контент и
        // навигация выводятся из него, поэтому едут вместе с рамкой.
        var frame = UiEditLayout.apply("frame", fx, fy, fw, fh, WorldMapUiLayout.Rect::new);
        fx = frame.x(); fy = frame.y(); fw = frame.width(); fh = frame.height();
        int sidebar = center ? 0 : fw < 600 ? 102 : 138;
        int x = fx+sidebar+13, y = fy+43;
        int width = Math.max(1, fw-sidebar-26), height = Math.max(1, fh-64);
        // Область страницы: дельта двигает окно просмотра, начало pose-стека
        // и скисор — содержимое вкладок и ввод указателя едут вместе с ней.
        var content = UiEditLayout.apply("content", x, y, width, height, WorldMapUiLayout.Rect::new);
        x = content.x(); y = content.y();
        width = Math.max(1, content.width()); height = Math.max(1, content.height());
        int vw = management ? 440 : Math.max(center ? 540 : 500,width);
        int vh = management ? 260 : Math.max(center ? 402 : 356,height);
        float scale = Math.min(1,Math.min(width/(float)vw,height/(float)vh));
        float ox = x+(width-vw*scale)/2-(management?180*scale:0);
        float oy = y+(height-vh*scale)/2-(management?76*scale:0);
        int navTop = fh < 240 ? 54 : fh < 300 ? 82 : 96;
        int pitch = Math.min(29, Math.max(16,(fh-26-navTop+4)/5));
        return new CityPylonWorkspaceLayout(x,y,width,height,vw,vh,scale,ox,oy,pitch,fx,fy,fw,fh,sidebar);
    }
    public static CityPylonWorkspaceLayout readable(int sw,int sh){
        var base=calculate(sw,sh,false);int side=base.frameWidth<500?44:102;
        int x=base.frameX+side+8,y=base.frameY+34,w=Math.max(1,base.frameWidth-side-16),h=Math.max(1,base.frameHeight-42);
        return new CityPylonWorkspaceLayout(x,y,w,h,w,h,1,x,y,22,base.frameX,base.frameY,base.frameWidth,base.frameHeight,side);
    }
    /** Пункт навигации: один прямоугольник для отрисовки, попаданий и правок. */
    public WorldMapUiLayout.Rect navRect(int index) {
        int baseY = frameY+(sidebarWidth<60?40:frameHeight<240?54:frameHeight<300?82:96)+index*navPitch;
        return UiEditLayout.apply("nav["+index+"]", frameX+7, baseY, sidebarWidth-14, navPitch-4,
                WorldMapUiLayout.Rect::new);
    }
    public int navY(int index) { return navRect(index).y(); }
    public int navHeight() { return navPitch-4; }
    /** Кнопка закрытия в правом верхнем углу рамки (26×24 — зона попадания). */
    public WorldMapUiLayout.Rect closeRect() {
        return UiEditLayout.apply("close", frameX+frameWidth-29, frameY+3, 26, 24,
                WorldMapUiLayout.Rect::new);
    }
    public boolean contains(double px,double py) { return px>=x&&px<x+width&&py>=y&&py<y+height; }
    public double localX(double px) { return (px-originX)/scale; }
    public double localY(double py) { return (py-originY)/scale; }
}
