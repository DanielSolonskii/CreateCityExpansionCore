package com.example.examplemod.client.screen;

/** Shared render/input geometry. Scrollable body keeps forms and actions usable at small GUI sizes. */
final class ProjectLayout {
    private ProjectLayout(){}
    static WorldMapUiLayout.Rect toggle(int width,int height){return new WorldMapUiLayout.Rect(WorldMapUiLayout.contextPanelLeft(width),height-26,WorldMapUiLayout.contextPanelWidth(width),22);}
    static WorldMapUiLayout.Rect panel(int width,int height,int top){return new WorldMapUiLayout.Rect(WorldMapUiLayout.contextPanelLeft(width),top,WorldMapUiLayout.contextPanelWidth(width),Math.max(1,height-top-34));}
    static WorldMapUiLayout.Rect body(WorldMapUiLayout.Rect panel){return new WorldMapUiLayout.Rect(panel.x()+10,panel.y()+38,Math.max(1,panel.width()-20),Math.max(1,panel.height()-66));}
    static WorldMapUiLayout.Rect row(WorldMapUiLayout.Rect body,int offset,int y,int height){return new WorldMapUiLayout.Rect(body.x(),body.y()+y-offset,body.width(),height);}
    static int clampScroll(int scroll,int content,int viewport){return Math.clamp(scroll,0,Math.max(0,content-viewport));}
}
