package com.example.examplemod.client.screen;

/** GUI-scaled dimensions, shared by rendering, hit testing and headless layout checks. */
public record LogisticsLayout(int left, int width, int top, int bottom, int footer) {
    public static LogisticsLayout of(int screenWidth, int screenHeight) {
        int width = Math.max(1, Math.min(940, screenWidth - 24));
        return new LogisticsLayout((screenWidth - width) / 2, width, screenHeight<200?53:78,
                Math.max(screenHeight<200?54:79, screenHeight - 30), screenHeight - 24);
    }
    public record Rect(int x,int y,int width,int height){public boolean contains(double x0,double y0){return x0>=x&&x0<x+width&&y0>=y&&y0<y+height;}}
    public Rect body(){return new Rect(left,top,width,viewport());}
    public Rect footerButton(int index,int count){int gap=5,w=(width-gap*(count-1))/count;return new Rect(left+index*(w+gap),footer,w,22);}
    public Rect tab(int index){int w=(width-10)/3;return new Rect(left+index*(w+5),top-29,w,22);}
    public int right() { return left + width; }
    public int viewport() { return bottom - top; }
    public boolean wide() { return width >= 590; }
    public int clampScroll(int scroll, int contentHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport())));
    }
    public boolean contains(double x, double y) {
        return x >= left && x < right() && y >= top && y < bottom;
    }
}
