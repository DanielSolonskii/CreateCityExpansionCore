package com.example.examplemod.client.screen;

/** GUI-scaled dimensions, shared by rendering, hit testing and headless layout checks. */
public record LogisticsLayout(int left, int width, int top, int bottom, int footer) {
    public static LogisticsLayout of(int screenWidth, int screenHeight) {
        int width = Math.max(1, Math.min(940, screenWidth - 24));
        return new LogisticsLayout((screenWidth - width) / 2, width, 78,
                Math.max(79, screenHeight - 42), screenHeight - 28);
    }
    public int right() { return left + width; }
    public int viewport() { return bottom - top; }
    public boolean wide() { return width >= 620; }
    public int clampScroll(int scroll, int contentHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport())));
    }
    public boolean contains(double x, double y) {
        return x >= left && x < right() && y >= top && y < bottom;
    }
}
