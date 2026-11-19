package com.example.examplemod.client.map;

/** Drives the same camera sampler, input and projection used by CityMapPanel. */
final class MapCameraCheck {
    public static void main(String[] args) { run(); }
    private static void near(double actual, double expected, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual-expected) > 1e-7)
            throw new AssertionError(message+": "+actual+" != "+expected);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run() {
        long start=1_000_000_000L;
        var pose = new MapCameraMotion.Pose(-29_900_000.3125,29_900_000.875,1.25);
        var projection = new MapProjection();
        for (int scale=1;scale<=4;scale++) {
            int w=1473/scale, h=899/scale;
            projection.frame(pose.x(),pose.z(),pose.zoom(),17,28,w,h);
            double x=pose.x()-.375,z=pose.z()+63.8125;
            near(projection.worldX(projection.screenX(x)),x,"world X round-trip at GUI scale "+scale);
            near(projection.worldZ(projection.screenY(z)),z,"world Z round-trip at GUI scale "+scale);
        }
        double cursorX=127.8125,cursorY=83.125;
        double anchorX=projection.worldX(cursorX),anchorZ=projection.worldZ(cursorY);
        double offsetX=projection.offsetX(cursorX),offsetY=projection.offsetY(cursorY);
        var wheel=new MapCameraMotion();
        for (int i=0;i<4;i++)
            wheel.scroll(pose,anchorX,anchorZ,offsetX,offsetY,.25,true,start);
        var mid=wheel.sample(start+65_000_000L);
        require(mid.zoom()>pose.zoom() && mid.zoom()<pose.zoom()*1.25,"transitional fractional zoom");
        near(mid.x()+offsetX/mid.zoom(),anchorX,"transition anchored in X");
        near(mid.z()+offsetY/mid.zoom(),anchorZ,"transition anchored in Z");
        var end=wheel.sample(start+140_000_000L);
        near(end.zoom(),pose.zoom()*1.25,"four quarter wheel steps equal one notch");
        require(wheel.sample(start+1_000_000_000L)==null,"no queued zooms");
        wheel.scroll(end,anchorX,anchorZ,offsetX,offsetY,.7,true,start);
        var back=wheel.scroll(end,anchorX,anchorZ,offsetX,offsetY,-.7,true,start);
        near(back.zoom(),end.zoom(),"opposing high-resolution input cancels");
        var immediate=wheel.scroll(pose,anchorX,anchorZ,offsetX,offsetY,.5,false,start);
        near(immediate.zoom(),pose.zoom()*Math.sqrt(1.25),"reduced motion uses the same fractional target");
        require(wheel.sample(start+10_000_000L)==null,"precision zoom completes at input");
        for (int fps:new int[]{8,30,60,144}) {
            double followed=0;
            for (int frame=0;frame<fps;frame++)
                followed=MapCameraMotion.followAxis(followed,100,12,MapMotion.blend(1d/fps,18));
            near(followed,88*(1-Math.exp(-18)),"follow dead zone and convergence at "+fps+" fps");
            var motion=new MapCameraMotion();
            var current=new MapCameraMotion.Pose(10,-40,.5);
            motion.focus(current,pose,start);
            motion.beginDrag(start);
            require(motion.sample(start+1)==null,"manual drag cancels transition at its drawn position");
            for (int n=1;n<=fps;n++)
                current=motion.drag(current,120d/fps,-60d/fps,start+Math.round(n*1e9/fps));
            near(current.x(),-230,"direct drag X at "+fps+" fps");
            near(current.z(),80,"direct drag Z at "+fps+" fps");
            motion.release(current,start+1_000_000_000L,true);
            MapCameraMotion.Pose coast=null;
            for (int n=1;n<=fps;n++) {
                var sample=motion.sample(start+1_000_000_000L+Math.round(n*1e9/fps));
                if (sample!=null) coast=sample;
            }
            require(coast!=null,"coast starts on release");
            near(coast.x(),-230-240*.14/3,"bounded inertia at "+fps+" fps");
            near(coast.z(),80+120*.14/3,"bounded inertia Z at "+fps+" fps");
            motion.beginDrag(start);
            var dragged=motion.drag(current,8,0,start+8_000_000L);
            motion.release(dragged,start+8_000_000L,false);
            require(motion.sample(start+18_000_000L)==null,"disabled inertia stays still");
        }
        System.out.println("PASS: fractional projection, cursor zoom, direct drag and elapsed-time inertia at 8/30/60/144 fps");
    }
}
