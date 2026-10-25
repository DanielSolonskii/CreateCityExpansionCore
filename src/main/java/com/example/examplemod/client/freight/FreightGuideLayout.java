package com.example.examplemod.client.freight;

/** One source of widget and content bounds, including very small GUI scales. */
public record FreightGuideLayout(int x,int y,int width,int height,int contentTop,int contentBottom,int footer,int columns,int buttonWidth) {
    public static FreightGuideLayout of(int screenWidth,int screenHeight) {
        int w=Math.max(1,Math.min(430,screenWidth-12)),h=Math.max(1,Math.min(350,screenHeight-12));
        int x=(screenWidth-w)/2,y=(screenHeight-h)/2,cols=w<340?2:3,rows=cols==2?3:2;
        int footer=y+h-12-rows*24;
        return new FreightGuideLayout(x,y,w,h,y+38,Math.max(y+38,footer-8),footer,cols,Math.max(1,(w-24-(cols-1)*5)/cols));
    }
    public int buttonX(int index){return x+12+(index%columns)*(buttonWidth+5);}
    public int buttonY(int index){return footer+(index/columns)*24;}
}
