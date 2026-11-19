package com.example.examplemod.client.logistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.examplemod.client.map.ShipmentRouteGeometry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Immutable server-sampled edges. No endpoint matching or inference of track connectivity. */
final class RailMesh {
    private static final int MAX_SAMPLES = 4096; // Largest supported fleet route snapshot.
    static final RailMesh EMPTY = new RailMesh(null);

    static final class Cache {
        private Tag source;
        private RailMesh current = EMPTY;

        RailMesh from(Tag rails) {
            if (rails != source) {
                if (rails == null) current = EMPTY;
                else if (!rails.equals(source)) current = new RailMesh(rails instanceof ListTag list ? list : null);
                source = rails;
            }
            return current;
        }
    }

    static final class Edge {
        final double x, z, endX, endZ, dx, dz;
        final UUID line;
        final boolean focus;
        Edge(double x, double z, double endX, double endZ, UUID line, boolean focus) {
            this.x=x; this.z=z; this.endX=endX; this.endZ=endZ;
            this.line=line; this.focus=focus;
            double length=Math.hypot(endX-x,endZ-z);
            dx=(endX-x)/length; dz=(endZ-z)/length;
        }
        boolean selected(UUID selected) { return focus || selected != null && selected.equals(line); }
    }

    static final class Layer {
        final List<Edge> edges;
        final List<ShipmentRouteGeometry.Point> routeEnds;
        private final Bounds root;
        private Layer(List<Edge> edges) {
            this.edges=edges;
            var route=new ArrayList<ShipmentRouteGeometry.Point>();
            for (Edge edge : edges) if (edge.focus) {
                route.add(new ShipmentRouteGeometry.Point(edge.x,edge.z));
                route.add(new ShipmentRouteGeometry.Point(edge.endX,edge.endZ));
            }
            routeEnds=List.copyOf(route);
            root=edges.isEmpty() ? null : new Bounds(edges,0,edges.size());
        }

        void visible(double left, double top, double right, double bottom, List<Edge> result) {
            if (root!=null) root.collect(edges,left,top,right,bottom,result);
        }
    }

    /** Bounding volume tree over actual edges, including long edges crossing the viewport. */
    private static final class Bounds {
        final int first, end;
        double left=Double.POSITIVE_INFINITY, top=Double.POSITIVE_INFINITY;
        double right=Double.NEGATIVE_INFINITY, bottom=Double.NEGATIVE_INFINITY;
        final Bounds a, b;
        Bounds(List<Edge> edges,int first,int end) {
            this.first=first; this.end=end;
            for (int i=first;i<end;i++) {
                Edge e=edges.get(i);
                left=Math.min(left,Math.min(e.x,e.endX)); right=Math.max(right,Math.max(e.x,e.endX));
                top=Math.min(top,Math.min(e.z,e.endZ)); bottom=Math.max(bottom,Math.max(e.z,e.endZ));
            }
            if (end-first<=16) { a=b=null; return; }
            boolean splitX=right-left>=bottom-top;
            edges.subList(first,end).sort(Comparator.comparingDouble(e -> splitX ? e.x+e.endX : e.z+e.endZ));
            int middle=(first+end)>>>1;
            a=new Bounds(edges,first,middle); b=new Bounds(edges,middle,end);
        }
        void collect(List<Edge> edges,double l,double t,double r,double btm,List<Edge> result) {
            if (right<l || bottom<t || left>r || top>btm) return;
            if (a!=null) { a.collect(edges,l,t,r,btm,result); b.collect(edges,l,t,r,btm,result); return; }
            for (int i=first;i<end;i++) {
                Edge e=edges.get(i);
                if (Math.max(e.x,e.endX)>=l && Math.min(e.x,e.endX)<=r &&
                    Math.max(e.z,e.endZ)>=t && Math.min(e.z,e.endZ)<=btm) result.add(e);
            }
        }
    }

    private final Map<String,Layer> layers=new HashMap<>();
    static final Layer EMPTY_LAYER=new Layer(List.of());
    final boolean exactRoute;

    private RailMesh(ListTag rails) {
        boolean hasPhase=false;
        var groups=new HashMap<String,List<Edge>>();
        if (rails!=null) for (int i=0;i<Math.min(rails.size(),MAX_SAMPLES);i++) {
            CompoundTag sample=rails.getCompound(i);
            if (!sample.contains("X",Tag.TAG_ANY_NUMERIC) || !sample.contains("Z",Tag.TAG_ANY_NUMERIC)
                    || !sample.contains("ToX",Tag.TAG_ANY_NUMERIC) || !sample.contains("ToZ",Tag.TAG_ANY_NUMERIC)) continue;
            double x=sample.getDouble("X"), z=sample.getDouble("Z"), ex=sample.getDouble("ToX"), ez=sample.getDouble("ToZ");
            if (!Double.isFinite(Math.hypot(ex-x,ez-z)) || x==ex && z==ez) continue;
            String dimension=sample.getString("Dimension");
            if (dimension.isEmpty()) continue;
            boolean phase=sample.contains("Phase");
            hasPhase|=phase;
            groups.computeIfAbsent(dimension,ignored->new ArrayList<>()).add(new Edge(x,z,ex,ez,
                    sample.hasUUID("Line")?sample.getUUID("Line"):null,phase||sample.getBoolean("Focus")));
        }
        groups.forEach((dim,edges)->layers.put(dim,new Layer(edges)));
        exactRoute=hasPhase;
    }
    Layer dimension(String dimension) { return layers.getOrDefault(dimension,EMPTY_LAYER); }
}
