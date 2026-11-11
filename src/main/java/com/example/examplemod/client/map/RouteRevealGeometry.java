package com.example.examplemod.client.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reveal only a verified single chain. Disconnected, branching or ambiguous geometry stays static. */
public final class RouteRevealGeometry {
    public record Point(double x, double z) {}
    public record Segment(Point from, Point to) {
        public double length() { return Math.hypot(to.x - from.x, to.z - from.z); }
    }
    private RouteRevealGeometry() {}

    public static List<Segment> order(List<Segment> segments) {
        if (segments.isEmpty()) return List.of();
        Map<Point, List<Integer>> at = new HashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment edge = segments.get(i);
            if (!Double.isFinite(edge.from.x) || !Double.isFinite(edge.from.z)
                    || !Double.isFinite(edge.to.x) || !Double.isFinite(edge.to.z)
                    || !Double.isFinite(edge.length()) || edge.length() == 0) return List.of();
            at.computeIfAbsent(edge.from, ignored -> new ArrayList<>()).add(i);
            at.computeIfAbsent(edge.to, ignored -> new ArrayList<>()).add(i);
        }
        var ends = at.entrySet().stream().filter(e -> e.getValue().size() == 1).map(Map.Entry::getKey).toList();
        if (ends.size() != 2 || at.values().stream().anyMatch(v -> v.size() > 2)) return List.of();
        // Deterministic visual direction; this is not a claim about a train's direction of travel.
        Point cursor = ends.stream().min(java.util.Comparator.comparingDouble(Point::x).thenComparingDouble(Point::z)).orElseThrow();
        List<Segment> ordered = new ArrayList<>();
        boolean[] used = new boolean[segments.size()];
        while (ordered.size() < segments.size()) {
            int index = -1;
            for (int candidate : at.getOrDefault(cursor, List.of())) if (!used[candidate]) { index = candidate; break; }
            if (index < 0) return List.of();
            used[index] = true;
            Segment edge = segments.get(index);
            Point next = edge.from.equals(cursor) ? edge.to : edge.from;
            ordered.add(new Segment(cursor, next)); cursor = next;
        }
        return List.copyOf(ordered);
    }

    public static List<Segment> prefix(List<Segment> chain, double progress) {
        if (!Double.isFinite(progress) || chain.isEmpty()) return List.of();
        double total = chain.stream().mapToDouble(Segment::length).sum();
        if (!Double.isFinite(total) || total <= 0) return List.of();
        double remaining = Math.clamp(progress, 0, 1) * total;
        List<Segment> result = new ArrayList<>();
        for (Segment edge : chain) {
            if (remaining <= 0) break;
            double length = edge.length();
            if (remaining >= length) { result.add(edge); remaining -= length; }
            else {
                double t = remaining / length;
                result.add(new Segment(edge.from, new Point(edge.from.x + (edge.to.x - edge.from.x) * t,
                        edge.from.z + (edge.to.z - edge.from.z) * t)));
                break;
            }
        }
        return result;
    }
}
