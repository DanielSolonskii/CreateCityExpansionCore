package com.example.examplemod.client.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Groups existing map symbols at the position of the first (highest-priority) member. */
public final class MarkerGroups<T> {
    public record Group<T>(int x, int y, int radius, ArrayList<T> members) {}
    private static final int CELL = 32;
    private final Map<Long,ArrayList<Group<T>>> buckets = new HashMap<>();
    private final ArrayList<Group<T>> visible = new ArrayList<>();
    private int maxRadius;

    public Iterable<Group<T>> visible() { return visible; }

    public void add(T value, int x, int y, int radius, int spacing) {
        int reach = radius + maxRadius + spacing;
        for (int row=Math.floorDiv(y-reach,CELL); row<=Math.floorDiv(y+reach,CELL); row++)
            for (int col=Math.floorDiv(x-reach,CELL); col<=Math.floorDiv(x+reach,CELL); col++) {
                var groups = buckets.get(key(col,row));
                if (groups == null) continue;
                for (var group : groups) {
                    long dx = x-group.x, dy = y-group.y, distance = radius+group.radius+spacing;
                    if (dx*dx + dy*dy < distance*distance) {
                        group.members.add(value);
                        return;
                    }
                }
            }
        var group = new Group<T>(x,y,radius,new ArrayList<>());
        group.members.add(value);
        visible.add(group);
        buckets.computeIfAbsent(key(Math.floorDiv(x,CELL),Math.floorDiv(y,CELL)), ignored -> new ArrayList<>()).add(group);
        maxRadius = Math.max(radius,maxRadius);
    }
    private static long key(int x,int y) { return ((long)x<<32) | (y & 0xffffffffL); }
}
