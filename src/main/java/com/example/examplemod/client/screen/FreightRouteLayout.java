package com.example.examplemod.client.screen;
/** One geometry for route rendering and selection, including narrow embedded pages. */
public record FreightRouteLayout(int x,int y,int width,int height){
 public static FreightRouteLayout of(int w,int h){int cw=Math.max(1,Math.min(560,w-12)),ch=Math.max(1,Math.min(320,h-8));return new FreightRouteLayout((w-cw)/2,(h-ch)/2,cw,ch);}
 public LogisticsLayout.Rect viewport(){return new LogisticsLayout.Rect(x+8,y+38,width-16,Math.max(1,height-70));}
 public LogisticsLayout.Rect row(int i,int scroll){var v=viewport();return new LogisticsLayout.Rect(v.x(),v.y()+i*58-scroll,v.width(),52);}
 public LogisticsLayout.Rect confirm(){return new LogisticsLayout.Rect(x+width/2+3,y+height-26,width/2-11,22);}
 public LogisticsLayout.Rect back(){return new LogisticsLayout.Rect(x+8,y+height-26,width/2-11,22);}
 public int clamp(int value,int count){return Math.clamp(value,0,Math.max(0,count*58-viewport().height()));}
}
