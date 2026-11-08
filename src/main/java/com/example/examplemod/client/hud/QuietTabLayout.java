package com.example.examplemod.client.hud;

/** Screen-space geometry shared by the renderer and headless boundary checks. */
public record QuietTabLayout(int columns, int rows, int cellWidth, int width, int height,
                             int x, int y, float scale) {
    public static QuietTabLayout calculate(int screenWidth, int screenHeight, int count, int cellWidth, int bossCount) {
        int top = bossCount == 0 ? 12 : Math.min(screenHeight / 3 + 19, 12 + bossCount * 19) + 8;
        int available = Math.max(1, screenHeight - top - 8);
        int columns = 1, rows = Math.max(1, count);
        float best = 0;
        for (int c = 1; c <= Math.min(4, Math.max(1, count)); c++) {
            int r = Math.max(1, (count + c - 1) / c);
            float scale = Math.min(1f, Math.min((screenWidth - 16f) / (c * cellWidth + 24), available / (r * 15f + 64)));
            if (scale > best) { best = scale; columns = c; rows = r; }
        }
        int width = columns * cellWidth + 24, height = rows * 15 + 64;
        return new QuietTabLayout(columns, rows, cellWidth, width, height,
                (screenWidth - (int)Math.ceil(width * best)) / 2, top, best);
    }
}
