package com.example.examplemod.client.screen;

/**
 * Раскладка базы знаний: левый список файлов/групп, правый просмотр.
 *
 * <p>Арифметка общая с Community/World Map окнами: масштаб выбирается по размеру
 * рабочего стола, координаты внутри — в масштабных единицах, hit-test и scissor
 * по-разному с локальными и экранными координатами. Группа занимает строку; файлы
 * группы рендерятся под ней только в развёрнутом состоянии.
 */
public record KnowledgeBaseLayout(int width, int height, float scale, float x, float y) {

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double a, double b) {
            return a >= x && a < right() && b >= y && b < bottom();
        }
    }

    public static KnowledgeBaseLayout calculate(int screenW, int screenH) {
        int width = screenW >= 720 ? 660 : 468;
        float scale = Math.min(1, Math.min(Math.max(1, screenW - 24) / (float) width,
                Math.max(1, screenH - 24) / 360f));
        int height = Math.clamp((int) ((screenH - 24) / scale), 360, 480);
        return new KnowledgeBaseLayout(width, height, scale, (screenW - width * scale) / 2, (screenH - height * scale) / 2);
    }

    public Rect close() {
        return new Rect(width - 36, 10, 24, 22);
    }

    /** Шапка: заголовок базы. */
    public Rect title() {
        return new Rect(25, 15, width - 60, 24);
    }

    /** Меню навигации — разделитель листа и просмотрщика. */
    public Rect list() {
        return new Rect(14, 46, 190, height - 92);
    }

    public Rect viewer() {
        Rect l = list();
        return new Rect(l.right() + 10, l.y(), width - l.width - 44, l.height());
    }

    /** Разделительная полоса между списком и просмотрщиком. */
    public Rect divider() {
        Rect l = list();
        return new Rect(l.right() + 4, l.y(), 2, l.height());
    }

    public Rect footer() {
        return new Rect(14, height - 40, width - 28, 24);
    }

    /** Строка файла/группы с фиксированной высотой. */
    public Rect row(int y, int h) {
        Rect l = list();
        return new Rect(l.x() + 4, y, l.width() - 8, h);
    }

    /** Экранные координаты панельной области для scissor. */
    public Rect screen(Rect r) {
        return screen(r, 0);
    }

    public Rect screen(Rect r, float offset) {
        int a = (int) Math.floor(x + r.x() * scale);
        int b = (int) Math.floor(y + (r.y() + offset) * scale);
        return new Rect(a, b, (int) Math.ceil(x + r.right() * scale) - a,
                (int) Math.ceil(y + (r.bottom() + offset) * scale) - b);
    }

    public boolean hit(Rect r, boolean content, double localX, double localY) {
        return r.contains(localX, localY) && (!content || viewer().contains(localX, localY));
    }

    public double localX(double screenX) {
        return (screenX - x) / scale;
    }

    public double localY(double screenY) {
        return (screenY - y) / scale;
    }
}
