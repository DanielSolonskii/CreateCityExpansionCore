package com.example.examplemod.client.map;
import java.util.*;
/** Pure viewport geometry, shared by rendering and headless checks. */
public final class MapBorderGeometry {
 private MapBorderGeometry(){}
 public record Bounds(double left,double top,double right,double bottom){
  public boolean contains(double x,double z){return x>=left&&x<right&&z>=top&&z<bottom;}
  public boolean covers(double x1,double z1,double x2,double z2){return x1>=left&&z1>=top&&x2<=right&&z2<=bottom&&x2>=x1&&z2>=z1;}
  public boolean intersects(double x1,double z1,double x2,double z2){return x2>left&&x1<right&&z2>top&&z1<bottom;}
 }
 public record Rect(int x,int y,int width,int height){}
 public static List<Rect> outside(int x,int y,int width,int height,int left,int top,int right,int bottom){
  int l=Math.clamp(left,x,x+width),r=Math.clamp(right,x,x+width);
  int t=Math.clamp(top,y,y+height),b=Math.clamp(bottom,y,y+height);
  List<Rect> result=new ArrayList<>();
  add(result,x,y,width,t-y);add(result,x,b,width,y+height-b);
  add(result,x,t,l-x,b-t);add(result,r,t,x+width-r,b-t);
  return List.copyOf(result);
 }
 private static void add(List<Rect> result,int x,int y,int w,int h){if(w>0&&h>0)result.add(new Rect(x,y,w,h));}
}
