package com.example.examplemod.client.map;

import java.util.List;
import static com.example.examplemod.client.map.ShipmentRouteGeometry.*;

/** Checks schematic presentation; does not establish live Minecraft rendering or cargo position. */
public final class ShipmentRouteGeometryCheck {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args) {
        var path=List.of(new Point(0,0),new Point(100,0),new Point(100,300));
        var fit=fit(path,600,400,40,.0625,4);
        require(fit.x()==50&&fit.z()==150,"frame contains source, transfer and destination");
        require(Math.abs(fit.zoom()-320.0/300)<1e-9,"fit respects the tighter viewport axis");
        require(sample(path,.25).point().equals(new Point(100,0)),"first leg takes its distance share");
        var cursor=sample(path,.5);
        require(cursor.point().equals(new Point(100,100))&&cursor.leg()==1,"cursor follows the second leg, never a source-to-destination shortcut");
        require(cursor.dx()==0&&cursor.dz()==1,"direction follows the current leg");
        require(sample(path,1).point().equals(path.getLast()),"last frame reaches the destination");
        require(sample(path,-1).point().equals(path.getFirst()),"negative phase stays at source");
        var repeated=List.of(new Point(5,5),new Point(5,5),new Point(15,5));
        require(sample(repeated,.5).point().equals(new Point(10,5)),"duplicate stops do not divide by zero");
        require(sample(List.of(new Point(5,5),new Point(5,5)),.7).point().equals(new Point(5,5)),"stationary route is stable");
        require(sample(List.of(),.5)==null&&sample(path,Double.NaN)==null,"invalid cursors are absent");
        require(fit(List.of(new Point(Double.NaN,0)),400,300,40,.0625,4)==null,"invalid geometry does not move camera");
        require(fit(List.of(new Point(0,0)),1,1,40,.0625,4).zoom()>=.0625,"tiny viewport has a bounded camera");
        require(cardHeaderHeight(36)==0&&cardHeaderHeight(81)==0&&cardHeaderHeight(82)==58,"short cards scroll their title instead of hiding their body");
        require(Double.isFinite(fit(List.of(new Point(Double.MAX_VALUE,0),new Point(Double.MAX_VALUE,100)),600,400,40,.0625,4).x()),"large finite coordinates keep a finite camera center");
        require(sample(List.of(new Point(-Double.MAX_VALUE,0),new Point(Double.MAX_VALUE,0)),.5)==null,"unrepresentable travel distance does not create invalid animation");
        System.out.println("PASS: "+checks+" shipment route geometry scenarios");
    }
}
