package com.example.examplemod.client.map;

import java.util.List;

/** Timelines and route continuity without a render context. Exercises the production presentation math. */
public final class DispatchPresentationCheck {
    private static int checks;
    private static void require(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-8;}
    private static RouteRevealGeometry.Point point(double x,double z){return new RouteRevealGeometry.Point(x,z);}
    private static RouteRevealGeometry.Segment edge(double x1,double z1,double x2,double z2){return new RouteRevealGeometry.Segment(point(x1,z1),point(x2,z2));}

    public static void main(String[] args) {
        long start=1_000_000_000L;
        for(int fps:new int[]{30,60,144}) {
            var fade=new DispatchMotion.Transition(0);
            require(fade.value(1,start,false,180)==0,"opening starts at the previous opacity");
            double previous=0;
            for(int frame=1;frame<=fps;frame++) {
                long now=start+(long)(frame*1_000_000_000.0/fps);
                double value=fade.value(1,now,false,180);
                require(value>=previous&&value<=1,"opacity is monotonic and bounded at "+fps+" fps");previous=value;
            }
            require(previous==1,"opening finishes independently of frame rate");
        }
        var fade=new DispatchMotion.Transition(0);
        fade.value(1,start,false,180);
        double half=fade.value(1,start+90_000_000L,false,180);
        require(near(half,MapMotion.ease(.5)),"one timeline at an exact timestamp");
        require(near(fade.value(0,start+90_000_000L,false,180),half),"reversing a fade is continuous");
        require(fade.value(0,start+270_000_000L,false,180)==0,"closed layer reaches exactly zero");
        require(fade.value(1,start+271_000_000L,true,180)==1,"reduced motion immediately shows the target");
        require(fade.value(0,start+272_000_000L,true,180)==0,"reduced motion never leaves a disappearing surface");
        var change=new DispatchMotion.Change();
        require(change.observe(10,start,false,180)==0,"initial server data does not flash");
        require(change.observe(10,start+10_000_000L,false,180)==0,"identical snapshots do not flash");
        require(change.observe(11,start+20_000_000L,false,180)==1,"a confirmed value change flashes once");
        require(change.observe(11,start+200_000_000L,false,180)==0,"a stable value stops flashing");
        require(change.observe(12,start+210_000_000L,true,180)==0,"reduced motion suppresses change effects");
        require(change.observe(12,start+220_000_000L,false,180)==0,"disabling reduced motion never replays a suppressed flash");

        var chain=RouteRevealGeometry.order(List.of(edge(3,0,1,0),edge(0,0,1,0),edge(3,0,3,4)));
        require(chain.size()==3,"unordered reversed edges form a verified chain");
        for(int i=1;i<chain.size();i++)require(chain.get(i-1).to().equals(chain.get(i).from()),"reveal never jumps across a missing connection");
        require(chain.getFirst().from().equals(point(0,0)),"visual direction is deterministic");
        var halfPath=RouteRevealGeometry.prefix(chain,.5);
        require(near(halfPath.stream().mapToDouble(RouteRevealGeometry.Segment::length).sum(),3.5),"reveal advances by distance across unequal edges");
        require(halfPath.getLast().to().equals(point(3,.5)),"last visible segment is cropped at the reveal position");
        require(RouteRevealGeometry.prefix(chain,0).isEmpty(),"zero reveal has no bright segment");
        require(RouteRevealGeometry.prefix(chain,1).equals(chain),"full reveal preserves all real edges");
        require(RouteRevealGeometry.order(List.of(edge(0,0,1,0),edge(2,0,3,0))).isEmpty(),"disconnected geometry stays static");
        require(RouteRevealGeometry.order(List.of(edge(0,0,1,0),edge(1,0,2,0),edge(1,0,1,1))).isEmpty(),"branches are never invented into an itinerary");
        require(RouteRevealGeometry.order(List.of(edge(0,0,1,0),edge(1,0,0,0))).isEmpty(),"ambiguous cycles stay static");
        require(RouteRevealGeometry.order(List.of(edge(0,0,Double.NaN,1))).isEmpty(),"nonfinite input is rejected");
        require(RouteRevealGeometry.order(List.of(edge(0,0,0,0))).isEmpty(),"zero length input stays static");
        require(RouteRevealGeometry.prefix(chain,Double.NaN).isEmpty(),"invalid progress cannot enter drawing");
        require(MapPalette.luminance(DispatchPalette.TEXT)-MapPalette.luminance(DispatchPalette.SURFACE)>150,"dispatcher text retains contrast");
        require(MapPalette.luminance(DispatchPalette.ACCENT)-MapPalette.luminance(DispatchPalette.SURFACE)>100,"selected controls remain readable");
        System.out.println("PASS: "+checks+" dispatcher motion, route continuity and palette checks");
    }
}
