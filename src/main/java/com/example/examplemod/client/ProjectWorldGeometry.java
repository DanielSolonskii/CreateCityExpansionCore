package com.example.examplemod.client;
import com.example.examplemod.projects.ProjectZone;
import java.util.*;
/** Ground surveying marks, clipped before sampling so a 4096-block zone has bounded cost. */
public final class ProjectWorldGeometry {
    public static final int RANGE=64,MAX_SEGMENTS=256;
    public record Segment(double x1,double z1,double x2,double z2,boolean post){public double distance(double x,double z){double dx=x2-x1,dz=z2-z1,d=dx*dx+dz*dz,t=d==0?0:Math.clamp(((x-x1)*dx+(z-z1)*dz)/d,0,1);return Math.hypot(x-x1-t*dx,z-z1-t*dz);}}
    private ProjectWorldGeometry(){}
    public static List<Segment> nearby(ProjectZone zone,double x,double z){
        var out=new ArrayList<Segment>();
        switch(zone.shape()){
            case RECTANGLE->{double l=zone.minX(),r=zone.maxX()+1d,t=zone.minZ(),b=zone.maxZ()+1d;
                dashes(out,l,t,r,t,x,z);dashes(out,r,t,r,b,x,z);dashes(out,r,b,l,b,x,z);dashes(out,l,b,l,t,x,z);
                post(out,l,t,x,z);post(out,r,t,x,z);post(out,r,b,x,z);post(out,l,b,x,z);}
            case LINE->{double ax=zone.x1()+.5,az=zone.z1()+.5,bx=zone.x2()+.5,bz=zone.z2()+.5;
                dashes(out,ax,az,bx,bz,x,z);double dx=bx-ax,dz=bz-az,length=Math.hypot(dx,dz);
                if(length>0){double centre=((x-ax)*dx+(z-az)*dz)/length;int from=Math.max(0,(int)Math.floor((centre-RANGE)/16)),to=Math.min((int)(length/16),(int)Math.ceil((centre+RANGE)/16));
                    for(int i=from;i<=to;i++){double cx=ax+dx/length*i*16,cz=az+dz/length*i*16;add(out,new Segment(cx-dz/length*.7,cz+dx/length*.7,cx+dz/length*.7,cz-dx/length*.7,false),x,z);}}
                post(out,ax,az,x,z);post(out,bx,bz,x,z);}
            case POINT->{double cx=zone.x1()+.5,cz=zone.z1()+.5;
                for(int i=0;i<16;i++){double a=i*Math.PI/8,b=a+Math.PI/12;add(out,new Segment(cx+Math.cos(a)*1.6,cz+Math.sin(a)*1.6,cx+Math.cos(b)*1.6,cz+Math.sin(b)*1.6,false),x,z);}post(out,cx,cz,x,z);}
        }
        out.sort(Comparator.comparingDouble(s->s.distance(x,z)));return List.copyOf(out.subList(0,Math.min(MAX_SEGMENTS,out.size())));
    }
    private static void post(List<Segment> out,double px,double pz,double x,double z){add(out,new Segment(px,pz,px,pz,true),x,z);}
    private static void add(List<Segment> out,Segment s,double x,double z){if(s.distance(x,z)<=RANGE)out.add(s);}
    private static void dashes(List<Segment> out,double ax,double az,double bx,double bz,double x,double z){
        double dx=bx-ax,dz=bz-az,length=Math.hypot(dx,dz);if(length==0)return;double ux=dx/length,uz=dz/length,projection=(x-ax)*ux+(z-az)*uz;
        int from=Math.max(0,(int)Math.floor((projection-RANGE-3)/4)),to=Math.min((int)Math.ceil(length/4),(int)Math.ceil((projection+RANGE+3)/4));
        for(int i=from;i<=to;i++){double start=i*4,end=Math.min(length,start+2.4);if(start>=length)break;add(out,new Segment(ax+ux*start,az+uz*start,ax+ux*end,az+uz*end,false),x,z);}
    }
}
