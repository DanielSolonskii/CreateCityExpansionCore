package com.example.examplemod.passenger;

/** Exercises the same observation clock used by the Create server tick listener. */
public final class PassengerTimetableCheck {
    public static void main(String[] args) { run(); }
    public static void run() {
        var timetable = new PassengerTimetable(3);
        timetable.observe(0, 100);
        timetable.observe(-1, 120); // first dwell was already in progress when the observer attached
        timetable.observe(1, 160);
        timetable.observe(-1, 190);
        timetable.observe(2, 250);
        timetable.observe(-1, 290);
        require(timetable.departures(290).isEmpty(), "rail connectivity and partial calibration do not publish service");
        timetable.observe(0, 330);
        timetable.observe(-1, 350);
        var trips = timetable.departures(350);
        require(trips.size() == 12, "two bounded cycles with two subsequent destinations per origin");
        require(trips.contains(new PassengerTimetable.Connection(1, 0, 420, 560,4,6)),
                "same-train ride includes intermediate dwell and uses physical directed travel times");
        require(trips.stream().allMatch(leg -> leg.arrival() > leg.departure() && leg.departure() >= 350),
                "no zero-time transfers or past departures");
        var samePlatform=trips.stream().filter(c->c.from()==1 && c.to()==0).toList();
        require(samePlatform.size()==2 && samePlatform.getLast().fromVisit()==samePlatform.getFirst().fromVisit()+3,
                "repeated calls to the same stop and direction have separate physical visit numbers");
        require(timetable.visitAt(1)==samePlatform.getFirst().fromVisit(),"native arrival between clock samples matches its offered visit");
        var oldRun=timetable.run();
        require(timetable.departures(420).isEmpty(), "late unobserved arrival withdraws estimates");
        timetable.observe(2, 425);
        require(timetable.departures(425).isEmpty(), "skipped stop invalidates all measurements");
        require(!oldRun.equals(timetable.run()),"old visit identities cannot match a new observation cycle");
        timetable.observe(-2, 430);
        require(!timetable.measured(430), "arrival outside the timetable cannot advance it");
        var wall=new net.minecraft.core.BlockPos(1,0,0);
        var seat=new net.minecraft.core.BlockPos(2,0,0);
        var eye=new net.minecraft.world.phys.Vec3(.2,.4,.5);
        var aim=new net.minecraft.world.phys.Vec3(2.5,.4,.5);
        var shapes=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.phys.shapes.VoxelShape>();
        shapes.put(seat,net.minecraft.world.phys.shapes.Shapes.box(.1,0,.1,.9,.6,.9));
        java.util.function.Function<net.minecraft.core.BlockPos,net.minecraft.world.phys.shapes.VoxelShape> outline=
                pos->shapes.getOrDefault(pos,net.minecraft.world.phys.shapes.Shapes.empty());
        require(PassengerSeatRay.first(eye,aim,seat,outline)==PassengerSeatRay.Reach.OPEN,"open side exposes the actual seat outline");
        shapes.put(wall,net.minecraft.world.phys.shapes.Shapes.block());
        require(PassengerSeatRay.first(eye,aim,seat,outline)==PassengerSeatRay.Reach.BLOCKED,"carriage wall before a reachable seat blocks native selection");
        shapes.put(wall,null);
        require(PassengerSeatRay.first(eye,aim,seat,outline)==PassengerSeatRay.Reach.UNKNOWN,"an unverified carriage shape cannot attest a clear ray");
        System.out.println("PASS passenger direction, physical calibration, dwell, next service, skipped stop and lateness");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
