package com.example.examplemod.client;
import com.example.examplemod.city.*;
import java.util.*;
/** Cached pure outline and genuine turns. No height/world reads in this class. */
public final class BoundaryGeometry {
 public static final int MAX_MARKS=320;
 public record Mark(double x,double z,boolean corner,boolean alongX){}
 private final List<int[]> edges;
 private final List<Mark> corners;
 public BoundaryGeometry(Set<CityPlot> plots){
  edges=CityClaims.outline(plots);Map<Long,Integer> axes=new HashMap<>();
  for(var e:edges){int axis=e[1]==e[3]?1:2;for(int i=0;i<=2;i+=2)axes.merge(pack(e[i],e[i+1]),axis,(a,b)->a|b);}
  corners=axes.entrySet().stream().filter(e->e.getValue()==3).map(e->new Mark((int)(e.getKey()>>32),(int)(long)e.getKey(),true,false)).toList();
 }
 private static long pack(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
 public int edgeCount(){return edges.size();}public int cornerCount(){return corners.size();}
 public List<Mark> nearby(double x,double z,int range){
  List<Mark> result=new ArrayList<>();double r2=(double)range*range;
  for(var c:corners)if(distance2(c,x,z)<=r2)result.add(c);
  for(var e:edges){
   if(x<Math.min(e[0],e[2])-range||x>Math.max(e[0],e[2])+range||z<Math.min(e[1],e[3])-range||z>Math.max(e[1],e[3])+range)continue;
   boolean horizontal=e[1]==e[3];int length=Math.abs(e[2]-e[0])+Math.abs(e[3]-e[1]);
   for(int d=2;d<length;d+=4){var mark=new Mark(e[0]+(horizontal?d:0),e[1]+(horizontal?0:d),false,horizontal);if(distance2(mark,x,z)<=r2)result.add(mark);}
  }
  result.sort(Comparator.comparingDouble(m->distance2(m,x,z)));
  return List.copyOf(result.subList(0,Math.min(MAX_MARKS,result.size())));
 }
 public static double distance2(Mark m,double x,double z){double dx=m.x-x,dz=m.z-z;return dx*dx+dz*dz;}
 public static float fade(double distance,int range){return (float)Math.clamp((range-distance)/16,0,1);}
}
