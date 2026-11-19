package com.example.examplemod.client.map;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/** One GUI-space pass for captions from all visible map layers of this panel. */
public final class MapLabelLayout {
    public enum Priority { SELECTED, TARGET, HOVER, NODE, NORMAL }
    private record Label(String id, String text, int color, int x, int y, int gap, int width, Priority priority) {}
    @FunctionalInterface interface Paint { void draw(String text, int color, int x, int y); }

    private static final int LIMIT = 256;
    private final TreeSet<Label> candidates = new TreeSet<>(Comparator.comparing(Label::priority).thenComparing(Label::id));
    private Map<String, Integer> previous = new HashMap<>();
    private Map<String, Integer> next = new HashMap<>();
    private final BitSet occupied = new BitSet();
    private int left, top, width, height, columns;

    void begin(int left, int top, int width, int height) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        columns = (width + 1) / 2;
        candidates.clear();
        occupied.clear();
    }

    public void reserve(int x, int y, int w, int h) {
        int x1 = Math.max(0, x-left), y1 = Math.max(0, y-top);
        int x2 = Math.min(width, x-left+w), y2 = Math.min(height, y-top+h);
        if (x1 >= x2 || y1 >= y2) return;
        for (int row = y1/2; row <= (y2-1)/2; row++)
            occupied.set(row*columns + x1/2, row*columns + (x2-1)/2 + 1);
    }

    public void offer(String id, String text, int color, int x, int y, int gap, int textWidth, Priority priority) {
        if (text.isEmpty() || textWidth + 4 > width || x<left || y<top || x>=left+width || y>=top+height) return;
        candidates.add(new Label(id, text, color, x, y, gap, textWidth, priority));
        if (candidates.size() > LIMIT) candidates.pollLast();
    }

    boolean fits(int x, int y, int w, int h) {
        int x1 = x-left, y1 = y-top;
        if (x1<0 || y1<0 || x1+w>width || y1+h>height) return false;
        for (int row = y1/2; row <= (y1+h-1)/2; row++) {
            int bit = occupied.nextSetBit(row*columns + x1/2);
            if (bit >= 0 && bit <= row*columns + (x1+w-1)/2) return false;
        }
        return true;
    }

    void paint(Paint paint) {
        next.clear();
        for (var label : candidates) {
            int remembered = previous.getOrDefault(label.id(), -1);
            for (int attempt = -1; attempt < 4; attempt++) {
                int side = attempt == -1 ? remembered : attempt;
                if (side < 0 || attempt >= 0 && side == remembered) continue;
                int x = switch (side) {
                    case 0 -> label.x + label.gap;
                    case 1 -> label.x - label.gap - label.width;
                    default -> label.x - label.width/2;
                };
                int y = switch (side) {
                    case 2 -> label.y - label.gap - 9;
                    case 3 -> label.y + label.gap;
                    default -> label.y - 4;
                };
                if (!fits(x-2, y-1, label.width+4, 11)) continue;
                reserve(x-2, y-1, label.width+4, 11);
                paint.draw(label.text, label.color, x, y);
                next.put(label.id, side);
                break;
            }
        }
        var swap = previous;
        previous = next;
        next = swap;
        candidates.clear();
    }
}
