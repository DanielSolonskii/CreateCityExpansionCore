package com.example.examplemod.client.screen;

import com.example.examplemod.client.uiedit.UiEditLayout;

/** All profile painting, scrolling, widgets and hit-testing use this one logical coordinate space. */
public record PlayerProfileLayout(int screenWidth, int screenHeight, int height, float scale, float x, float y) {
    public static final int WIDTH = 432;
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }
    public static PlayerProfileLayout calculate(int screenWidth, int screenHeight) {
        float scale = Math.min(1, Math.min(Math.max(1, screenWidth - 24) / (float) WIDTH, Math.max(1, screenHeight - 24) / 320f));
        int height = Math.max(320, Math.min(376, (int) ((screenHeight - 24) / scale)));
        return new PlayerProfileLayout(screenWidth, screenHeight, height, scale, (screenWidth - WIDTH * scale) / 2, (screenHeight - height * scale) / 2);
    }
    public static Rect workspaceEntry(int frameX, int frameY, int frameWidth) {
        // UI-редактор (F7): правится как элемент того экрана, где кнопка
        // нарисована (в игре это CityPylonWorkspace — хук сам берёт его ключ).
        return UiEditLayout.apply("profileEntry", frameX + frameWidth - 154, frameY + 4, 112, 22,
                Rect::new);
    }
    // Хук UI-редактора (F7): правки элементов применяются здесь и регистрируются
    // для оверлея; при пустых правках возвращает ровно те же координаты.
    // searchRow/scrollThumb намеренно без хука — их позиция зависит от скролла,
    // который считает экран, а не layout.
    private Rect hook(String name, Rect r) {
        // Ключ экрана — активный (Init.Pre), не строка: см. UiEditLayout javadoc.
        return UiEditLayout.apply(name, r.x(), r.y(), r.width(), r.height(),
                scale, x, y, Rect::new);
    }
    public Rect frame() { return hook("frame", new Rect(0, 0, WIDTH, height)); }
    public Rect close() { return hook("close", new Rect(WIDTH - 36, 8, 24, 22)); }
    public Rect socialToggle() { return hook("socialToggle", new Rect(208,8,86,22)); }
    public Rect searchToggle() { return hook("searchToggle", new Rect(WIDTH - 132, 8, 90, 22)); }
    public Rect searchField() { return hook("searchField", new Rect(14, 92, 292, 26)); }
    public Rect searchSubmit() { return hook("searchSubmit", new Rect(312, 92, 106, 26)); }
    public Rect searchRow(int index) { return new Rect(body().x(), body().y() + index * 46, body().width(), 42); }
    public Rect tab(int index) { return hook("tab[" + index + "]", new Rect(14 + index * 136, 92, 132, 26)); }
    public Rect body() { return hook("body", new Rect(14, 128, WIDTH - 28, height - 168)); }
    public Rect footerAction() { return hook("footerAction", new Rect(14, height - 31, 124, 22)); }
    public Rect save() { return hook("save", new Rect(WIDTH - 118, height - 31, 104, 22)); }
    public Rect refresh() { return hook("refresh", new Rect(WIDTH - 38, height - 31, 24, 22)); }
    public Rect aboutField() { return hook("aboutField", new Rect(body().x() + 12, body().y() + 26, body().width() - 24, 22)); }
    public Rect picker(int index) { return hook("picker[" + index + "]", new Rect(body().x() + 12, body().y() + 77 + index * 26, body().width() - 24, 22)); }
    public Rect metric(int index) { return hook("metric[" + index + "]", new Rect(body().x() + index * 137, body().y() + 62, 130, 64)); }
    public Rect pageButton(boolean next) { return hook("pageButton[" + next + "]", new Rect(next ? WIDTH - 44 : WIDTH - 126, height - 31, 30, 22)); }
    public Rect scrollTrack() { return hook("scrollTrack", new Rect(body().right() + 1, body().y(), 10, body().height())); }
    public Rect allianceBadge() { return hook("allianceBadge", new Rect(302,68,112,20)); }
    public Rect invite() { return hook("invite", new Rect(148,height-31,140,22)); }
    public Rect scrollThumb(int scroll, int contentHeight) {
        int h = Math.min(body().height(), Math.max(18, body().height() * body().height() / Math.max(1, contentHeight)));
        int y = body().y() + (body().height() - h) * Math.max(0, Math.min(scrollMaximum(contentHeight), scroll)) / Math.max(1, scrollMaximum(contentHeight));
        return new Rect(body().right() + 3, y, 2, h);
    }
    public int scrollMaximum(int contentHeight) { return Math.max(0, contentHeight - body().height()); }
    public double localX(double mouseX) { return (mouseX - x) / scale; }
    public double localY(double mouseY, float entranceOffset) { return (mouseY - y) / scale - entranceOffset; }
    /** GuiGraphics scissor uses absolute GUI coordinates and does not apply the pose matrix. */
    public Rect screenBounds(Rect logical, float entranceOffset) {
        int left = (int) Math.floor(x + logical.x() * scale);
        int top = (int) Math.floor(y + (logical.y() + entranceOffset) * scale);
        int right = (int) Math.ceil(x + logical.right() * scale);
        int bottom = (int) Math.ceil(y + (logical.bottom() + entranceOffset) * scale);
        return new Rect(left, top, right - left, bottom - top);
    }
}
