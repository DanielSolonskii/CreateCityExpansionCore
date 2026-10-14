package com.example.examplemod.client.map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Приборная обвязка карты: миникарта, компас, масштабная
 * линейка, легенда слоёв и штамп координат.
 *
 * <p>Сделано отдельным классом, а не расширением
 * {@link CityMapPanel}: панель уже держит состояние и ввод, и
 * добавление туда ещё одной порции отрисовки снова сделало бы
 * её местом, где и состояние, и геометрия, и заливка.
 *
 * <p>Геометрия считается в {@link Geometry} — одним чистым
 * расчётом, который проверяется headless на всех
 * поддерживаемых размерах. Ни одна точка не рисуется по числу
 * из воздуха.
 *
 * <p>Обвязка ничего не знает о слоях: она получает готовый
 * список подписей со счётчиками и состоянием включения. Иначе
 * легенда и панель разошлись бы при первом же добавлении слоя.
 */
final class CityMapFrame {

    /**
     * Всё, что обвязка рисует, в одном расчёте.
     *
     * <p>Прямоугольники считаются от вьюпорта карты с учётом
     * {@code avoidRight}: правая колонка World Map перекрывает
     * карту, и приборные элементы под ней были бы наполовину
     * невидимыми. Поэтому они зажимаются в оставшуюся ширину.
     */
    record Geometry(
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            int safeRight,
            int minimapX,
            int minimapY,
            int minimapSize,
            int stampX,
            int stampY,
            int stampWidth,
            int scaleX,
            int scaleY,
            int scaleWidth,
            int legendX,
            int legendY,
            int legendWidth,
            int legendRowHeight,
            int legendRows
    ) {

        /** Ниже этого размера вьюпорта обвязка не рисуется: она только мешает карте. */
        static final int MIN_VIEWPORT_FOR_CHROME = 220;

        /**
         * Ширина вьюпорта, реально свободная под обвязку.
         *
         * <p>Никогда отрицательная: если правая колонка съела
         * вьюпорт, приборные элементы убираются совсем, а не
         * рисуются поверх чужой панели.
         */
        static int safeWidth(int viewportX, int viewportWidth, int avoidRight) {
            return Math.max(0, Math.min(viewportWidth, avoidRight - viewportX));
        }

        /**
         * Показывать ли обвязку при таких размерах.
         *
         * <p>Проверка идёт по свободной ширине, а не по полной:
         * вьюпорт может быть широким, а свободная его часть —
         * уже нет.
         */
        static boolean chromeVisible(int viewportX, int viewportWidth, int viewportHeight, int avoidRight) {
            return safeWidth(viewportX, viewportWidth, avoidRight) >= MIN_VIEWPORT_FOR_CHROME
                    && viewportHeight >= 180;
        }

        /**
         * Единый расчёт геометрии.
         *
         * @param avoidRight левая граница того, что нельзя перекрывать
         */
        static Geometry of(
                int viewportX,
                int viewportY,
                int viewportWidth,
                int viewportHeight,
                int avoidRight,
                int legendEntries
        ) {
            int safe = safeWidth(viewportX, viewportWidth, avoidRight);
            boolean visible = chromeVisible(viewportX, viewportWidth, viewportHeight, avoidRight);

            int minimapSize = Math.min(
                    104,
                    Math.max(0, Math.min(safe / 4, viewportHeight / 4))
            );
            int inset = MapPalette.SECTION;
            int stampHeight = 26;

            /*
             * Миникарта и штамп живут в левом нижнем углу свободной
             * зоны, легенда — в правом верхнем. Штамп никогда не
             * наезжает на миникарту: оба считаются от одного края.
             */
            int minimapX = viewportX + inset;
            int minimapY = viewportY + viewportHeight - inset - minimapSize;
            int stampX = minimapX;
            int stampY = minimapY - stampHeight - MapPalette.MICRO;

            int legendWidth = Math.max(0, Math.min(168, safe / 3));
            int legendRowHeight = 13;
            int legendRows = Math.max(0, Math.min(legendEntries, 8));
            int legendHeight = legendRows * legendRowHeight + 12;
            int legendX = viewportX + safe - inset - legendWidth;
            int legendY = viewportY + inset;

            int scaleWidth = Math.max(0, Math.min(132, safe / 3));
            int scaleY = viewportY + viewportHeight - inset - 10;
            int scaleX = viewportX + safe - inset - scaleWidth;

            if (!visible) {
                return new Geometry(
                        viewportX, viewportY, viewportWidth, viewportHeight, safe,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, legendRowHeight, 0
                );
            }

            return new Geometry(
                    viewportX, viewportY, viewportWidth, viewportHeight, safe,
                    minimapX, minimapY, minimapSize,
                    stampX, stampY, Math.max(0, Math.min(196, safe - inset)),
                    scaleX, scaleY, scaleWidth,
                    legendX, legendY, legendWidth, legendRowHeight, legendRows
            );
        }
    }

    /** Одна строка легенды: что за слой, сколько объектов и включён ли он. */
    record LegendEntry(String label, String count, boolean enabled) {}

    /**
     * Метка позиции для миникарты и компаса.
     *
     * @param own родной ли объект — влияет только на цвет
     */
    record MapMark(String name, int worldX, int worldZ, int color, boolean own) {}

    /** Данные, которые обвязка берёт у панели на текущий кадр. */
    record Context(
            double centerX,
            double centerZ,
            double zoom,
            int playerX,
            int playerZ,
            int pylonX,
            int pylonZ,
            List<MapMark> marks,
            List<LegendEntry> legend,
            String dimensionName
    ) {
    }

    private CityMapFrame() {
    }

    /**
     * Полная отрисовка обвязки.
     *
     * <p>Порядок: виньетка, потом легенда, миникарта, компас,
     * линейка и штамп. Виньетка идёт первой, чтобы затемняла
     * карту по краям, а приборные элементы оставались поверх.
     */
    static void render(GuiGraphics graphics, Geometry geometry, Context context) {
        render(graphics, geometry, context, true, true);
    }

    static void render(GuiGraphics graphics, Geometry geometry, Context context, boolean fullChrome, boolean minimap) {
        if (geometry.minimapSize() <= 0) {
            return;
        }

        Font font = net.minecraft.client.Minecraft.getInstance().font;

        drawVignette(graphics, geometry);
        if (fullChrome) drawLegend(graphics, font, geometry, context);
        if (minimap) drawMinimap(graphics, geometry, context);
        drawCompass(graphics, font, geometry);
        drawScaleBar(graphics, font, geometry, context);
        if (fullChrome) drawStamp(graphics, font, geometry, context);
    }

    /**
     * Виньетка по краям вьюпорта.
     *
     * <p>Полосы в несколько слоёв вместо одной заливки: линейный
     * градиент в {@code GuiGraphics} недоступен, а ступени при
     * толщине в 6–8 пикселей на глаз неразличимы и стоят в
     * разы дешевле.
     */
    private static void drawVignette(GuiGraphics graphics, Geometry geometry) {
        int x = geometry.viewportX();
        int y = geometry.viewportY();
        int width = geometry.viewportWidth();
        int height = geometry.viewportHeight();
        int band = 6;

        for (int step = 0; step < 3; step++) {
            int alpha = 0x0C + step * 0x06;
            int inset = step * band;
            int color = (alpha << 24) | 0x000000;
            graphics.fill(x, y + inset, x + width, y + inset + band, color);
            graphics.fill(x, y + height - inset - band, x + width, y + height - inset, color);
            graphics.fill(x + inset, y + inset, x + inset + band, y + height - inset, color);
            graphics.fill(x + width - inset - band, y + inset, x + width - inset, y + height - inset, color);
        }
    }

    /**
     * Легенда слоёв: подпись, счётчик и состояние.
     *
     * <p>Счётчик рядом с подписью снимает вопрос «что включено»,
     * а состояние читается яркостью строки, а не только галочкой.
     */
    private static void drawLegend(
            GuiGraphics graphics,
            Font font,
            Geometry geometry,
            Context context
    ) {
        if (geometry.legendWidth() <= 0 || context.legend().isEmpty()) {
            return;
        }

        int rows = Math.min(geometry.legendRows(), context.legend().size());
        int height = rows * geometry.legendRowHeight() + 12;
        int x = geometry.legendX();
        int y = geometry.legendY();

        panel(graphics, x, y, geometry.legendWidth(), height);

        Component title = Component.translatable(
                "gui.createcityexpansioncore.map.legend"
        );
        graphics.drawString(font, title, x + MapPalette.NORMAL, y + MapPalette.MICRO,
                MapPalette.TEXT_FAINT, false);

        for (int index = 0; index < rows; index++) {
            LegendEntry entry = context.legend().get(index);
            int rowY = y + 14 + index * geometry.legendRowHeight();
            int color = entry.enabled() ? MapPalette.TEXT_MUTED : MapPalette.TEXT_FAINT;

            /*
             * Квадрат состояния вместо галочки: он различим и при
             * мелком зуме GUI, где галочка сливается со строкой.
             */
            graphics.fill(x + MapPalette.NORMAL, rowY, x + MapPalette.NORMAL + 5, rowY + 5,
                    entry.enabled() ? MapPalette.OWN : MapPalette.INK_SOFT);
            graphics.drawString(font, entry.label(), x + MapPalette.NORMAL + 10, rowY - 2, color, false);

            if (!entry.count().isEmpty()) {
                graphics.drawString(font, entry.count(),
                        x + geometry.legendWidth() - MapPalette.NORMAL - font.width(entry.count()),
                        rowY - 2,
                        color,
                        false
                );
            }
        }
    }

    /**
     * Миникарта: где я, где мой город, где чужие города и какой
     * участок сейчас в кадре.
     *
     * <p>Схематическая, а неTerrain-копия: перерисовывать
     * местность в миникарте означало бы строить вторые
     * текстуры ради прямоугольника 104×104. Показываются
     * только отношения — а их и не хватало при работе с картой.
     */
    private static void drawMinimap(
            GuiGraphics graphics,
            Geometry geometry,
            Context context
    ) {
        int size = geometry.minimapSize();
        int x = geometry.minimapX();
        int y = geometry.minimapY();

        panel(graphics, x, y, size, size);

        /*
         * Радиус схемы: вчетверо больше текущего зума, поэтому
         * миникарта всегда показывает больше, чем главная карта,
         * и служит именно обзором.
         */
        double span = Math.max(256.0, Math.max(geometry.viewportWidth(), geometry.viewportHeight()) / context.zoom() * 4.0);
        int centre = size / 2;

        /*
         * Схема миникарты — та же линейная, что у главной карты,
         * только с другим охватом: один экран. Иначе «ты здесь»
         * на миникарте и на карте означали бы разные точки.
         */
        int centreX = x + centre;
        int centreY = y + centre;

        // Крест перекрестья миникарты.
        graphics.vLine(x + centre, y + MapPalette.NORMAL, y + size - MapPalette.NORMAL, MapPalette.INK_SOFT);
        graphics.hLine(x + MapPalette.NORMAL, y + centre, x + size - MapPalette.NORMAL, MapPalette.INK_SOFT);

        for (MapMark mark : context.marks()) {
            int markX = centreX + (int) Math.round((mark.worldX() - context.centerX()) / span * size);
            int markY = centreY + (int) Math.round((mark.worldZ() - context.centerZ()) / span * size);
            if (markX <= x + 2 || markX >= x + size - 2 || markY <= y + 2 || markY >= y + size - 2) {
                continue;
            }
            int dot = mark.own() ? 3 : 2;
            graphics.fill(markX - dot / 2, markY - dot / 2, markX + dot / 2 + 1, markY + dot / 2 + 1, mark.color());
        }

        /*
         * Рамка текущего кадра главной карты: показывает, куда
         * игрок смотрит, — единственное, чего миникарта не может
         * вывести из координат.
         */
        int frameW = Math.max(3, (int) Math.round((geometry.viewportWidth() / context.zoom()) / span * size));
        int frameH = Math.max(3, (int) Math.round((geometry.viewportHeight() / context.zoom()) / span * size));
        int frameX = centreX - frameW / 2;
        int frameY = centreY - frameH / 2;
        graphics.renderOutline(
                Math.max(x + 1, frameX),
                Math.max(y + 1, frameY),
                Math.min(size - 2, frameW),
                Math.min(size - 2, frameH),
                MapPalette.SELECTED
        );

        Component caption = Component.translatable(
                "gui.createcityexpansioncore.map.minimap"
        );
        graphics.drawString(
                font(graphics), caption,
                x + MapPalette.NORMAL, y + size + MapPalette.MICRO,
                MapPalette.TEXT_FAINT, false
        );
    }

    /**
     * Компас-роза: север сверху, восток справа.
     *
     * <p>Ось важнее гравировки букв: подпись «N» без указания
     * верх ли это даёт игроку ложную ориентировку, а сама роза
     * читается однозначно.
     */
    private static void drawCompass(GuiGraphics graphics, Font font, Geometry geometry) {
        int x = geometry.legendX();
        int y = geometry.legendY();
        if (x <= 0) {
            return;
        }

        int size = 22;
        int top = y - size - MapPalette.MICRO;
        if (top <= geometry.viewportY()) {
            return;
        }

        int centreX = x + size / 2;
        int centreY = top + size / 2;

        graphics.fill(centreX - size / 2, top, centreX + size / 2, top + size, MapPalette.PAPER_SUNK);
        graphics.renderOutline(centreX - size / 2, top, size, size, MapPalette.INK);

        // Северная стрелка.
        graphics.fill(centreX - 1, centreY - 6, centreX + 1, centreY + 6, MapPalette.TEXT_FAINT);
        graphics.fill(centreX - 2, centreY - 8, centreX + 2, centreY - 4, MapPalette.OWN);
        graphics.fill(centreX - 1, centreY + 4, centreX + 1, centreY + 8, MapPalette.TEXT_FAINT);

        graphics.drawString(font, "N", centreX - font.width("N") / 2, top + 1,
                MapPalette.TEXT_MUTED, false);
    }

    /**
     * Масштабная линейка: сколько блоков в её длине.
     *
     * <p>Без неё зум — просто число, а игрок не может оценить
     * размер видимой области. Полоса подбирается к «круглой»
     * величине, поэтому подпись остаётся честной.
     */
    private static void drawScaleBar(
            GuiGraphics graphics,
            Font font,
            Geometry geometry,
            Context context
    ) {
        int width = geometry.scaleWidth();
        if (width <= 0) {
            return;
        }

        int x = geometry.scaleX();
        int y = geometry.scaleY();

        /*
         * Шаг выбирается из «красивых» чисел: подпись вида
         * "256 блоков" читается, а "247 блоков" выглядит как
         * погрешность.
         */
        double blocksPerPixel = 1.0 / context.zoom();
        int niceStep = niceStep(blocksPerPixel * (width / 2));
        int barBlocks = niceStep / 2;
        int barPixels = (int) Math.round(barBlocks / blocksPerPixel);
        barPixels = Math.max(8, Math.min(width, barPixels));

        graphics.fill(x, y, x + barPixels, y + 1, MapPalette.TEXT);
        graphics.fill(x, y - 2, x, y + 3, MapPalette.TEXT);
        graphics.fill(x + barPixels - 1, y - 2, x + barPixels, y + 3, MapPalette.TEXT);

        String label = Component.translatable(
                "gui.createcityexpansioncore.map.scale_blocks",
                barBlocks
        ).getString();
        graphics.drawString(font, label, x, y + 5, MapPalette.TEXT_MUTED, false);
    }

    /** Ближайшая «круглая» величина сверху: 1, 2, 5 × степень двойки. */
    static int niceStep(double value) {
        if (value <= 1) {
            return 1;
        }
        int exponent = 0;
        double scaled = value;
        while (scaled >= 10) {
            scaled /= 10;
            exponent++;
        }
        while (scaled < 1) {
            scaled *= 10;
            exponent--;
        }
        double normalised = scaled < 1.5 ? 1 : scaled < 5 ? 2 : 5;
        return (int) Math.max(1, Math.round(normalised * Math.pow(10, exponent)));
    }

    /**
     * Штамп координат в духе чертежной рамки: позиция курсора,
     * текущий зум и измерение.
     */
    private static void drawStamp(
            GuiGraphics graphics,
            Font font,
            Geometry geometry,
            Context context
    ) {
        int x = geometry.stampX();
        int y = geometry.stampY();
        int width = geometry.stampWidth();
        if (width <= 0 || y < geometry.viewportY()) {
            return;
        }

        panel(graphics, x, y, width, 26);

        Component position = Component.translatable(
                "gui.createcityexpansioncore.map.coordinates",
                (int) Math.floor(context.centerX()),
                (int) Math.floor(context.centerZ()),
                String.format(Locale.ROOT, "%.2f", context.zoom())
        );
        graphics.drawString(font, position, x + MapPalette.NORMAL, y + MapPalette.MICRO,
                MapPalette.TEXT_MUTED, false);

        /*
         * Вторая строка штампа — размер захваченной области, а
         * не повтор зума. «Показан участок 512 × 288 блоков»
         * отвечает на вопрос, который зум сам по себе не
         * отвечает: сколько реально видно.
         */
        int visibleWidth = (int) Math.round(geometry.viewportWidth() / context.zoom());
        int visibleHeight = (int) Math.round(geometry.viewportHeight() / context.zoom());
        Component extent = Component.translatable(
                "gui.createcityexpansioncore.map.stamp_extent",
                visibleWidth,
                visibleHeight
        );
        graphics.drawString(font, extent, x + MapPalette.NORMAL, y + MapPalette.NORMAL + 3,
                MapPalette.TEXT_FAINT, false);
    }

    /* --------------------------------------------------------------- примитивы */

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, MapPalette.PAPER_SUNK);
        graphics.renderOutline(x, y, width, height, MapPalette.INK);
        graphics.hLine(x + 1, x + width - 1, y + 1, MapPalette.INK_SOFT);
    }

    private static Font font(GuiGraphics graphics) {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    /** Легенда по умолчанию, когда панель не передала ничего. */
    static List<LegendEntry> emptyLegend() {
        return List.of();
    }

    /** Метки по умолчанию, когда панель не передала ничего. */
    static List<MapMark> emptyMarks() {
        return new ArrayList<>();
    }
}
