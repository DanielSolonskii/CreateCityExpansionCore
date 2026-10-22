package com.example.examplemod.client.map;
public final class MapLineClip {
 private MapLineClip(){}
 public record Line(int x1,int y1,int x2,int y2){}
 public static Line clip(int x1,int y1,int x2,int y2,int left,int top,int right,int bottom){
  double dx=(double)x2-x1,dy=(double)y2-y1,low=0,high=1;

  for(int i=0;i<4;i++){
   double p=switch(i){case 0->-dx;case 1->dx;case 2->-dy;default->dy;};
   double q=switch(i){case 0->(double)x1-left;case 1->(double)right-x1;case 2->(double)y1-top;default->(double)bottom-y1;};
   if(p==0){if(q<0)return null;continue;}
   double t=q/p;if(p<0)low=Math.max(low,t);else high=Math.min(high,t);
   if(low>high)return null;
  }
  return new Line((int)Math.round(x1+low*dx),(int)Math.round(y1+low*dy),(int)Math.round(x1+high*dx),(int)Math.round(y1+high*dy));
 }
}
