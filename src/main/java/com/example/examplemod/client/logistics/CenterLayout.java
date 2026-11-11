package com.example.examplemod.client.logistics;

/** One geometry source for drawing, keyboard navigation, hit testing and help anchors. */
public record CenterLayout(int width, int height, int sidebar, int navigationStep,
                           Rect map, Rect previous, Rect next, Rect actions) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public boolean overlaps(Rect other) { return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
    }
    public static CenterLayout of(int width, int height) {
        int sidebar = width < 580 ? 48 : 144, margin = width < 500 ? 10 : 18;
        int left = sidebar + margin, area = width - left - margin, y = height - 48;
        int mapWidth = area < 280 ? 44 : 66, actionWidth = area < 280 ? 68 : 98;
        return new CenterLayout(width, height, sidebar, height < 260 ? 23 : 30,
                new Rect(left, y, mapWidth, 22), new Rect(left + mapWidth + 7, y, 22, 22),
                new Rect(left + mapWidth + 33, y, 22, 22), new Rect(width - margin - actionWidth, y, actionWidth, 22));
    }
    public boolean compact() { return sidebar < 100; }
    public int margin() { return width < 500 ? 10 : 18; }
    public int left() { return sidebar + margin(); }
    public int area() { return width - left() - margin(); }
    public Rect navigation() { return new Rect(6, 70, sidebar - 12, Math.max(24, height - 110)); }
    public int navigationLimit(int count) { return Math.max(0, count * navigationStep - navigation().height()); }
    public Rect navigationRow(int index, int scroll) { return new Rect(6, 70 + index * navigationStep - scroll, sidebar - 12, navigationStep - 3); }
    public Rect help() { return new Rect(6, height - 30, sidebar - 12, 22); }
    public int toolbarY() { return height < 220 ? 54 : 62; }
    public Rect search() { return new Rect(left() + 8, toolbarY(), Math.max(32, area() - 106), 20); }
    public Rect toolbar(int index) { return new Rect(width - margin() - 84 + index * 29, toolbarY(), 26, 20); }
    public Rect body() { int top = height < 220 ? 80 : 94; return new Rect(left(), top, area(), Math.max(24, height - 56 - top)); }
    public int detailWidth() { return area() >= 610 ? Math.min(300, area() / 3) : 0; }
    public Rect list() { var body = body(); int detail = detailWidth(); return new Rect(body.x(), body.y(), body.width() - (detail > 0 ? detail + 12 : 0), body.height()); }
    public Rect details() { var body = body(); int detail = detailWidth(); return new Rect(body.right() - detail, body.y(), detail, body.height()); }
    public int rowStep() { return height < 260 ? 48 : 60; }
    public int listContentHeight(int count) { return Math.max(0, count * rowStep() - 6); }
    public Rect row(int index, int scroll) { var list = list(); return new Rect(list.x(), list.y() + index * rowStep() - scroll, list.width(), rowStep() - 6); }
    public Rect dashboardCard(int index) {
        int columns = area() >= 600 ? 4 : 2, gap = 10, cardWidth = (area() - gap * (columns - 1)) / columns;
        int cardHeight = height < 220 ? 24 : height < 260 ? 34 : 76, top = height < 220 ? 62 : height < 260 ? 66 : 90;
        return new Rect(left() + index % columns * (cardWidth + gap), top + index / columns * (cardHeight + gap), cardWidth, cardHeight);
    }
}
