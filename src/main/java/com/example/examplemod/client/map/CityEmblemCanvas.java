package com.example.examplemod.client.map;

import com.example.examplemod.city.CityIdentity;
import java.util.*;

/** Bounded editor history; one mouse stroke is one undo step. */
public final class CityEmblemCanvas {
    private char[] pixels;
    private String stroke;
    private final Deque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    public CityEmblemCanvas(String image) { pixels = new CityIdentity("", image).pixels().toCharArray(); }
    public String image() { return new String(pixels); }
    public void begin() { if (stroke == null) stroke = image(); }
    public void end() { if (stroke != null && !stroke.equals(image())) { undo.addLast(stroke); while (undo.size() > 32) undo.removeFirst(); redo.clear(); } stroke = null; }
    public void paint(int x, int y, int ink, boolean mirror) {
        if (x < 0 || x >= 32 || y < 0 || y >= 32 || ink < 0 || ink > 15) return;
        pixels[y * 32 + x] = Character.forDigit(ink, 16);
        if (mirror) pixels[y * 32 + 31 - x] = Character.forDigit(ink, 16);
    }
    public void fill(int x, int y, int ink) {
        if (x < 0 || x >= 32 || y < 0 || y >= 32 || ink < 0 || ink > 15) return;
        char target = pixels[y * 32 + x], replacement = Character.forDigit(ink, 16);
        if (target == replacement) return;
        Deque<Integer> queue = new ArrayDeque<>(); queue.add(y * 32 + x); pixels[y * 32 + x] = replacement;
        while (!queue.isEmpty()) { int cell = queue.removeFirst(), cx = cell % 32, cy = cell / 32;
            for (int next : new int[]{cx > 0 ? cell - 1 : -1, cx < 31 ? cell + 1 : -1, cy > 0 ? cell - 32 : -1, cy < 31 ? cell + 32 : -1})
                if (next >= 0 && pixels[next] == target) { pixels[next] = replacement; queue.add(next); }
        }
    }
    public void line(int x0, int y0, int x1, int y1, int ink, boolean mirror) {
        if (x0 < 0 || y0 < 0 || x1 < 0 || y1 < 0 || x0 > 31 || y0 > 31 || x1 > 31 || y1 > 31) return;
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, error = dx + dy;
        while (true) {
            paint(x0, y0, ink, mirror);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }
    public void template(int variant) { begin(); pixels = CityIdentity.template(variant).toCharArray(); end(); }
    public void undo() { end(); if (!undo.isEmpty()) { redo.addLast(image()); pixels = undo.removeLast().toCharArray(); } }
    public void redo() { end(); if (!redo.isEmpty()) { undo.addLast(image()); pixels = redo.removeLast().toCharArray(); } }
}
