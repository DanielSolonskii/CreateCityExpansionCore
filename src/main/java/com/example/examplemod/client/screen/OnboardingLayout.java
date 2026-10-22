package com.example.examplemod.client.screen;
/** One source for rendering and hit boxes. */
public final class OnboardingLayout {
 private OnboardingLayout(){}
 public record Rect(int x,int y,int width,int height){public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}}
 public static Rect card(int width,int height,int lines,boolean screen){int w=Math.min(278,Math.max(0,width-24)),h=65+Math.clamp(lines,1,5)*10;return new Rect(12,screen?Math.max(12,height-h-14):Math.min(54,Math.max(12,height-h-14)),w,h);}
 public static Rect action(Rect card,int index){int w=(card.width()-20)/3;return new Rect(card.x()+10+index*w,card.y()+card.height()-25,w-3,19);}
 public static Rect guide(int width,int height){int w=Math.min(360,width-20),h=Math.min(244,height-20);return new Rect((width-w)/2,(height-h)/2,w,h);}
}
