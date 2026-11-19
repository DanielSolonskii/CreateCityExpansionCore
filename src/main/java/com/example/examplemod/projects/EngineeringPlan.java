package com.example.examplemod.projects;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Editable map geometry in block coordinates. A local draft may still be incomplete. */
public record EngineeringPlan(UUID id, ResourceLocation dimension, Kind kind, String name, String note, List<Node> nodes) {
    public static final int MAX_NODES = 64, MAX_NAME = 48, MAX_NOTE = 256;
    public enum Kind { POINT, LINE, AREA }
    public record Node(int x, int z) {
        public Node {
            if (Math.abs((long)x) > 30_000_000 || Math.abs((long)z) > 30_000_000)
                throw new IllegalArgumentException("coordinate");
        }
        public double distance(Node other) { return Math.hypot(x - other.x, z - other.z); }
    }
    public EngineeringPlan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(kind);
        if (dimension.toString().length() > 160 || !validText(name, MAX_NAME) || !validText(note, MAX_NOTE)
                || nodes.size() > MAX_NODES || kind == Kind.POINT && nodes.size() > 1)
            throw new IllegalArgumentException("invalid plan");
        nodes = List.copyOf(nodes);
    }
    private static boolean validText(String text, int max) {
        return text != null && text.length() <= max && text.chars().noneMatch(c -> Character.isISOControl(c) || c == 0x00a7);
    }
    public EngineeringPlan geometry(List<Node> value) { return new EngineeringPlan(id, dimension, kind, name, note, value); }
    public EngineeringPlan describe(String title, String memo) { return new EngineeringPlan(id, dimension, kind, title, memo, nodes); }

    public int edges() { return Math.max(0, nodes.size() - 1) + (kind == Kind.AREA && nodes.size() >= 3 ? 1 : 0); }
    public Node end(int edge) { return nodes.get((edge + 1) % nodes.size()); }
    public double length() {
        double result = 0;
        for (int i = 0; i < edges(); i++) result += nodes.get(i).distance(end(i));
        return result;
    }
    public double area() {
        if (kind != Kind.AREA || nodes.size() < 3) return 0;
        long twice = 0;
        for (int i = 1; i < nodes.size() - 1; i++) twice += cross(nodes.getFirst(), nodes.get(i), nodes.get(i+1));
        return Math.abs(twice) * .5;
    }
    public boolean complete() {
        if (name.isBlank() || nodes.isEmpty()) return false;
        if (kind == Kind.POINT) return true;
        if (nodes.size() < 2) return false;
        for (int i=0; i<edges(); i++) if (nodes.get(i).equals(end(i))) return false;
        if (kind == Kind.LINE) return true;
        if (nodes.size() < 3 || area() == 0) return false;
        for (int i=0; i<edges(); i++) {
            Node a=nodes.get(i), b=end(i), c=end((i+1)%edges());
            if (cross(a,b,c)==0 && (between(a,b,c) || between(b,c,a))) return false;
            for (int j=i+2; j<edges(); j++) {
                if (i==0 && j==edges()-1) continue;
                if (intersect(a,b,nodes.get(j),end(j))) return false;
            }
        }
        return true;
    }
    private static long cross(Node a, Node b, Node c) {
        return (long)(b.x-a.x)*(c.z-a.z) - (long)(b.z-a.z)*(c.x-a.x);
    }
    private static boolean between(Node a, Node b, Node p) {
        return p.x >= Math.min(a.x,b.x) && p.x <= Math.max(a.x,b.x) && p.z >= Math.min(a.z,b.z) && p.z <= Math.max(a.z,b.z);
    }
    private static boolean intersect(Node a, Node b, Node c, Node d) {
        long p=cross(a,b,c), q=cross(a,b,d), r=cross(c,d,a), s=cross(c,d,b);
        if (p==0 && between(a,b,c) || q==0 && between(a,b,d) || r==0 && between(c,d,a) || s==0 && between(c,d,b)) return true;
        return Long.signum(p)*Long.signum(q)<0 && Long.signum(r)*Long.signum(s)<0;
    }
    /** Test a block-centred geometry against a plot with inclusive block coordinates. */
    public boolean touches(int minX, int minZ, int maxX, int maxZ) {
        double left=minX-.5, top=minZ-.5, right=maxX+.5, bottom=maxZ+.5;
        for (var p : nodes) if (p.x>=left && p.x<=right && p.z>=top && p.z<=bottom) return true;
        boolean enclosed=false;
        for (int i=0; i<edges(); i++) {
            Node a=nodes.get(i), b=end(i);
            double dx=b.x-a.x, dz=b.z-a.z, lo=0, hi=1;
            if (dx==0 && (a.x<left || a.x>right) || dz==0 && (a.z<top || a.z>bottom)) continue;
            if (dx!=0) { double t=(left-a.x)/dx, u=(right-a.x)/dx; lo=Math.max(lo,Math.min(t,u)); hi=Math.min(hi,Math.max(t,u)); }
            if (dz!=0) { double t=(top-a.z)/dz, u=(bottom-a.z)/dz; lo=Math.max(lo,Math.min(t,u)); hi=Math.min(hi,Math.max(t,u)); }
            if (lo<=hi) return true;
        }
        if (kind == Kind.AREA && nodes.size()>=3) {
            for (int i=0; i<edges(); i++) {
                Node a=nodes.get(i), b=end(i);
                if ((a.z>top)!=(b.z>top) && left < a.x + (b.x-a.x)*(top-a.z)/(b.z-a.z)) enclosed=!enclosed;
            }
        }
        return enclosed;
    }
}
