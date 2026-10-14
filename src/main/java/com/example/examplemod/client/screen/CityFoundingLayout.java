package com.example.examplemod.client.screen;

/** Shared bounds for founding fields, map clipping and input. */
public record CityFoundingLayout(WorldMapUiLayout.Rect panel, WorldMapUiLayout.Rect map,
                                WorldMapUiLayout.Rect name, WorldMapUiLayout.Rect back,
                                WorldMapUiLayout.Rect submit, int formX, int formWidth) {
    public static CityFoundingLayout compute(int width, int height) {
        int w = Math.min(620, width - 24), h = Math.min(320, height - 24);
        int x = (width-w)/2, y = (height-h)/2;
        int mapWidth = Math.max(90, (w-42)*45/100), formX = x+mapWidth+30;
        int formWidth = x+w-14-formX;
        return new CityFoundingLayout(new WorldMapUiLayout.Rect(x,y,w,h),
                new WorldMapUiLayout.Rect(x+14,y+53,mapWidth,h-120),
                new WorldMapUiLayout.Rect(formX,y+68,formWidth,20),
                new WorldMapUiLayout.Rect(x+w-190,y+h-34,70,22),
                new WorldMapUiLayout.Rect(x+w-112,y+h-34,98,22),formX,formWidth);
    }
}
