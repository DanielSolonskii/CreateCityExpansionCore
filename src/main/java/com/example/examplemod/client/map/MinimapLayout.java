package com.example.examplemod.client.map;
/** One source for HUD bounds and the responsive settings grid. */
public final class MinimapLayout {
 private MinimapLayout(){}
 public record View(int x,int y,int size){
  public int frameX(){return x-3;}public int frameY(){return y-3;}
  public int frameWidth(){return size+6;}public int frameHeight(){return size+32;}
  public int footerY(){return y+size+5;}
 }
 public static View view(int width,int height,int requested,int corner){int size=Math.max(0,Math.min(requested,Math.min(width/3,height-84)));return new View(corner==0?width-size-10:10,10,size);}
 public record Settings(int x,int y,int width,int height,int columns){
  public int buttonX(int index){return x+10+(index%columns)*(width-20)/columns;}
  public int buttonY(int index){return y+30+(index/columns)*23;}
  public int buttonWidth(){return (width-20)/columns-4;}
 }
 public static Settings settings(int width,int height){int columns=height<270?2:1,w=Math.min(columns==2?340:300,width-12),h=30+((8+columns-1)/columns)*23+36;return new Settings((width-w)/2,(height-h)/2,w,h,columns);}
}
