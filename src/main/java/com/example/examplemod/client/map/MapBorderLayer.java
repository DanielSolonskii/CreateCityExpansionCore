package com.example.examplemod.client.map;
import com.example.examplemod.network.MapBorderPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public final class MapBorderLayer {
 private MapBorderLayer(){}
 private record Timed(MapBorderPayload data,long at){}
 private static final Map<ResourceLocation,Timed> snapshots=new HashMap<>();
 public static void accept(MapBorderPayload p){snapshots.put(p.dimension(),new Timed(p,net.minecraft.Util.getMillis()));}
 public static void clear(){snapshots.clear();}
 public static MapBorderGeometry.Bounds bounds(ResourceLocation dimension){
  var mc=Minecraft.getInstance();
  if(mc.level!=null&&mc.level.dimension().location().equals(dimension)){
   var b=mc.level.getWorldBorder();return new MapBorderGeometry.Bounds(b.getMinX(),b.getMinZ(),b.getMaxX(),b.getMaxZ());
  }
  var s=snapshots.get(dimension);if(s==null)return null;var p=s.data();
  double progress=p.remaining()<=0?1:Math.clamp((net.minecraft.Util.getMillis()-s.at())/(double)p.remaining(),0,1);
  double half=(p.size()+(p.target()-p.size())*progress)/2;
  return new MapBorderGeometry.Bounds(Math.max(-29999984,p.x()-half),Math.max(-29999984,p.z()-half),Math.min(29999984,p.x()+half),Math.min(29999984,p.z()+half));
 }
 public static boolean available(ResourceLocation d,double x,double z){var b=bounds(d);return b==null||b.contains(x,z);}
 public static boolean covers(ResourceLocation d,double x1,double z1,double x2,double z2){var b=bounds(d);return b==null||b.covers(x1,z1,x2,z2);}
 public static boolean intersects(ResourceLocation d,double x1,double z1,double x2,double z2){var b=bounds(d);return b==null||b.intersects(x1,z1,x2,z2);}
 public static void render(GuiGraphics g,CityMapPanel map,ResourceLocation dimension,int x,int y,int width,int height,int mx,int my){
  var b=bounds(dimension);if(b==null)return;
  int l=map.worldToScreenX(b.left()),r=map.worldToScreenX(b.right()),t=map.worldToScreenY(b.top()),bottom=map.worldToScreenY(b.bottom());
  for(var rect:MapBorderGeometry.outside(x,y,width,height,l,t,r,bottom)){
   g.fill(rect.x(),rect.y(),rect.x()+rect.width(),rect.y()+rect.height(),0x609F3038);
   for(int yy=rect.y();yy<rect.y()+rect.height();yy+=3)
    for(int xx=rect.x()+Math.floorMod(-yy-rect.x(),16);xx<rect.x()+rect.width();xx+=16)
     g.fill(xx,yy,Math.min(xx+2,rect.x()+rect.width()),Math.min(yy+3,rect.y()+rect.height()),0x388F242D);
  }
  int clippedL=Math.clamp(l,x,x+width),clippedR=Math.clamp(r,x,x+width),clippedT=Math.clamp(t,y,y+height),clippedB=Math.clamp(bottom,y,y+height);
  if(l>=x&&l<x+width)g.fill(l,clippedT,l+2,clippedB,0xFFD16B70);
  if(r>=x&&r<x+width)g.fill(r-1,clippedT,r+1,clippedB,0xFFD16B70);
  if(t>=y&&t<y+height)g.fill(clippedL,t,clippedR,t+2,0xFFD16B70);
  if(bottom>=y&&bottom<y+height)g.fill(clippedL,bottom-1,clippedR,bottom+1,0xFFD16B70);
  if(mx>=x&&my>=y&&mx<x+width&&my<y+height&&!b.contains(map.screenToWorldX(mx),map.screenToWorldZ(my))){
   var mc=Minecraft.getInstance();var title=Component.translatable("gui.createcityexpansioncore.border.unavailable");
   var hint=Component.translatable("gui.createcityexpansioncore.border.updates");
   int w=Math.min(width-12,Math.max(mc.font.width(title),mc.font.width(hint))+12),px=Math.clamp(mx+10,x+4,x+width-w-4),py=Math.clamp(my+14,y+4,y+height-34);
   g.fill(px,py,px+w,py+30,0xF21B2024);g.fill(px,py,px+2,py+30,0xFFD16B70);
   g.drawString(mc.font,mc.font.plainSubstrByWidth(title.getString(),w-10),px+6,py+5,0xFFF0B4B7,false);
   g.drawString(mc.font,mc.font.plainSubstrByWidth(hint.getString(),w-10),px+6,py+17,0xFFB9BDC0,false);
  }
 }
}
