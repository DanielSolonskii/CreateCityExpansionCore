package com.example.examplemod.client.map;

import java.util.List;

/** Presentation geometry only. A cursor describes the direction of a schematic, never live cargo. */
public final class ShipmentRouteGeometry {
    public record Point(double x, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(z); }
    }
    public record View(double x, double z, double zoom) {}
    public record Cursor(Point point, double dx, double dz, int leg) {}

    private ShipmentRouteGeometry() {}

    public static View fit(List<Point> points, int width, int height, int padding, double minZoom, double maxZoom) {
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for (Point point : points) {
            if (!point.finite()) continue;
            minX=Math.min(minX,point.x());maxX=Math.max(maxX,point.x());
            minZ=Math.min(minZ,point.z());maxZ=Math.max(maxZ,point.z());
        }
        if (!Double.isFinite(minX)) return null;
        double zoom=Math.min(Math.max(1,width-2.0*padding)/Math.max(48,maxX-minX),
                Math.max(1,height-2.0*padding)/Math.max(48,maxZ-minZ));
        return new View(minX/2+maxX/2,minZ/2+maxZ/2,Math.clamp(zoom,minZoom,maxZoom));
    }

    /** Traverse by distance, so a short transfer leg does not consume as much time as a long one. */
    public static Cursor sample(List<Point> points, double progress) {
        if (points.isEmpty() || !Double.isFinite(progress) || points.stream().anyMatch(p -> !p.finite())) return null;
        double total=0;
        for (int i=1;i<points.size();i++) total+=distance(points.get(i-1),points.get(i));
        if (!Double.isFinite(total)) return null;
        if (total<=0) return new Cursor(points.getFirst(),0,0,0);
        double remaining=Math.clamp(progress,0,1)*total;
        for (int i=1;i<points.size();i++) {
            Point from=points.get(i-1),to=points.get(i);double length=distance(from,to);
            if (length<=0) continue;
            if (remaining<=length || i==points.size()-1) {
                double part=Math.clamp(remaining/length,0,1);
                return new Cursor(new Point(from.x()+(to.x()-from.x())*part,from.z()+(to.z()-from.z())*part),
                        (to.x()-from.x())/length,(to.z()-from.z())/length,i-1);
            }
            remaining-=length;
        }
        return new Cursor(points.getLast(),0,0,Math.max(0,points.size()-2));
    }

    private static double distance(Point from, Point to) { return Math.hypot(to.x()-from.x(),to.z()-from.z()); }

    /** Keep a scrollable body when the WorldMap column is shorter than its usual fixed header. */
    public static int cardHeaderHeight(int panelHeight) { return panelHeight>=82?58:0; }
}
