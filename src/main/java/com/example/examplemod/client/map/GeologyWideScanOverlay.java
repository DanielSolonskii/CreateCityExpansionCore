package com.example.examplemod.client.map;

import com.example.examplemod.geology.VeinFootprint;
import com.example.examplemod.network.GeologyWideScanPayload;
import com.example.examplemod.network.GeologyWideScanPayload.Contour;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Заливка залежей широкого спектра магнитного анализатора
 * на глобальной карте.
 *
 * <p>Класс живёт как пара «клиентское состояние + слой
 * отрисовки» по образцу {@code LogisticsMapOverlay}:
 * проекцию и навигацию предоставляет {@link CityMapPanel},
 * здесь только приём пейлоада и рисование заливок.
 *
 * <p>Заливка полупрозрачная и окрашена по ресурсу залежи:
 * уголь — чёрный, железо — бирюзовый, медь — оранжевый,
 * известняк — белый; прочие ресурсы получают нейтральный
 * слой инструмента. Карта заливает полигон залежи по маске сервера.
 *
 * <p>Снимок держится до следующего скана и до десяти минут
 * с момента получения: геология устаёт, и карта не должна
 * обещать активность устаревших границ. Пейлоад привязан
 * к измерению — снимки другого мира не рисуются.
 */
public final class GeologyWideScanOverlay {

    /**
     * Срок годности снимка: игрок получал контуры у себя,
     * а не по репозиторию, поэтому хранить их вечно нельзя.
     */
    private static final long SNAPSHOT_LIFETIME_MILLIS = 10 * 60 * 1000L;

    /** Сколько точек эллипса сэмплируется на один контур. */
    private static final int CONTOUR_POINTS = 64;

    /** Прозрачность заливки: читается и terrain, и соседние залежи. */
    private static final int FILL_ALPHA = 0x59000000;

    /** Базовые цвета ресурсов: старшие байты задаёт FILL_ALPHA. */
    private static final int COAL_RGB = 0x000000;
    private static final int IRON_RGB = 0x40E0D0;
    private static final int COPPER_RGB = 0xE89338;
    private static final int LIMESTONE_RGB = 0xFFFFFF;

    /** Белизна на светлой бумаге карты слепит — ей отдельный туман. */
    private static final int LIMESTONE_FILL = 0x40FFFFFF;

    /** Ресурс вне палитры (например, цинк) — нейтральный слой. */
    private static final int OTHER_FILL = 0x4AD1A85A;

    private static final int MUTED = 0xFF8A8F8F;

    /**
     * Цвет заливки по ресурсу. Собирается из FILL_ALPHA и
     * базового RGB один раз на ресурс; проблема «знать ID
     * на клиенте» здесь не возникает — идёт уже пейлоад.
     */
    private static Map<String, Integer> resourceFills() {
        Map<String, Integer> fills = new HashMap<>();
        fills.put("coal", FILL_ALPHA | COAL_RGB);
        fills.put("iron", FILL_ALPHA | IRON_RGB);
        fills.put("copper", FILL_ALPHA | COPPER_RGB);
        fills.put("limestone", LIMESTONE_FILL);
        return Map.copyOf(fills);
    }

    private static final Map<String, Integer> FILLS_BY_RESOURCE = resourceFills();

    private static ResourceLocation snapshotDimension;
    private static List<Contour> contours = List.of();
    private static int foundTotal;
    private static long receivedAt;

    private GeologyWideScanOverlay() {
    }

    /**
     * Приём пейлоада широкого спектра. Вызывается из
     * клиентского хендлера через enqueueWork.
     */
    public static void accept(GeologyWideScanPayload payload) {
        snapshotDimension = payload.dimension();
        foundTotal = payload.foundTotal();
        receivedAt = Util.getMillis();
        contours = payload.contours();
    }

    /**
     * Слой карты. Вызывается из PolishedWorldMapScreen между
     * отрисовкой панели и версткой ограничений, с тем же
     * прямоугольником проёма, что и остальные слои карты.
     */
    public static void render(
            GuiGraphics graphics,
            CityMapPanel map,
            ResourceLocation mapDimension,
            int left,
            int top,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean active
    ) {
        if (!active || contours.isEmpty() || receivedAt == 0) {
            return;
        }
        if (!mapDimension.equals(snapshotDimension)) {
            return;
        }
        long age = Util.getMillis() - receivedAt;
        if (age > SNAPSHOT_LIFETIME_MILLIS) {
            contours = List.of();
            receivedAt = 0;
            return;
        }

        for (Contour contour : contours) {
            fillDeposit(graphics, map, contour, limit(left, top, width, height));
        }

        /*
         * Подпись в углу видового окна: сколько залежей вошло
         * в снимок и сколько снимку лет. Мутный тон поверх
         * любой местности.
         */
        Component caption = Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.map.caption",
                contours.size(),
                foundTotal
        ).append(Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.map.caption.age",
                age / 1000
        ));
        graphics.drawString(mapFont(), caption, left + 8, top + 8, MUTED, false);
    }

    /** Проём карты, расширенный на толщину краевого шума. */
    private static View limit(int left, int top, int width, int height) {
        return new View(left - 4, top - 4, width + 8, height + 8);
    }

    private record View(int left, int top, int width, int height) {
        int right() {
            return left + width;
        }

        int bottom() {
            return top + height;
        }
    }

    private static net.minecraft.client.gui.Font mapFont() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    private static int fillOf(Contour contour) {
        return FILLS_BY_RESOURCE.getOrDefault(
                contour.resourceId().getPath(),
                OTHER_FILL
        );
    }

    private static void fillFootprint(GuiGraphics graphics, CityMapPanel map,
                                      VeinFootprint footprint, View view, int color) {
        int originX = footprint.originX();
        int originZ = footprint.originZ();
        int extent = VeinFootprint.SIDE * VeinFootprint.STEP;
        int top = Math.max(view.top(), map.worldToScreenY(originZ));
        int bottom = Math.min(view.bottom(), map.worldToScreenY(originZ + extent));
        if (top >= bottom || map.worldToScreenX(originX) >= view.right()
                || map.worldToScreenX(originX + extent) <= view.left()) {
            return;
        }

        // Pair crossings on each scanline so concave outlines and holes are preserved.
        List<List<Double>> rows = new ArrayList<>(Collections.nCopies(bottom - top, null));
        for (VeinFootprint.Edge edge : footprint.edges()) {
            int z0 = map.worldToScreenY(originZ + edge.z0());
            int z1 = map.worldToScreenY(originZ + edge.z1());
            if (z0 == z1) {
                continue;
            }
            int x0 = map.worldToScreenX(originX + edge.x0());
            int x1 = map.worldToScreenX(originX + edge.x1());
            double slope = (double) (x1 - x0) / (z1 - z0);
            int from = Math.max(top, Math.min(z0, z1));
            int to = Math.min(bottom, Math.max(z0, z1));
            for (int z = from; z < to; z++) {
                int row = z - top;
                List<Double> crossings = rows.get(row);
                if (crossings == null) {
                    crossings = new ArrayList<>();
                    rows.set(row, crossings);
                }
                crossings.add(x0 + (z + 0.5 - z0) * slope);
            }
        }
        for (int row = 0; row < rows.size(); row++) {
            List<Double> crossings = rows.get(row);
            if (crossings == null) {
                continue;
            }
            crossings.sort(Double::compare);
            for (int i = 1; i < crossings.size(); i += 2) {
                int left = Math.max(view.left(), (int) Math.ceil(crossings.get(i - 1) - 0.5));
                int right = Math.min(view.right(), (int) Math.ceil(crossings.get(i) - 0.5));
                if (left < right) {
                    graphics.fill(left, top + row, right, top + row + 1, color);
                }
            }
        }
    }

    /**
     * Полупрозрачная заливка одной залежи: её эллипс в
     * повороте растеризуется построчно (scanline). Выпуклый
     * эллипс даёт на каждую строку один интервал по X, поэтому
     * спаны достаточно собрать из проекций граничных точек.
     */
    private static void fillDeposit(
            GuiGraphics graphics,
            CityMapPanel map,
            Contour contour,
            View view
    ) {
        if (contour.footprint() != null) {
            fillFootprint(graphics, map, contour.footprint(), view, fillOf(contour));
            return;
        }

        /*
         * Граничные точки в экранных координатах: угол 0 и
         * 2π совпадают, поэтому первый столбец переписывает
         * последний и кольцо замыкается само.
         */
        int[] xs = new int[CONTOUR_POINTS];
        int[] ys = new int[CONTOUR_POINTS];
        double cos = Math.cos(contour.rotationRadians());
        double sin = Math.sin(contour.rotationRadians());

        for (int index = 0; index < CONTOUR_POINTS; index++) {
            double angle = Math.PI * 2 * index / CONTOUR_POINTS;
            double localX = contour.radiusX() * Math.cos(angle);
            double localZ = contour.radiusZ() * Math.sin(angle);

            double worldX = contour.centerX() + localX * cos - localZ * sin;
            double worldZ = contour.centerZ() + localX * sin + localZ * cos;

            xs[index] = map.worldToScreenX(worldX);
            ys[index] = map.worldToScreenY(worldZ);
        }

        int color = fillOf(contour);
        int minY = Math.max(view.top(), minY(ys));
        int maxY = Math.min(view.bottom() - 1, maxY(ys));
        if (minY > maxY) {
            return;
        }

        /*
         * Спан строки: минимальный и максимальный X среди точек
         * границы на этой строке и на рёбрах, проходящих через
         * неё. Контур замкнут — ведущего ребра не требуется.
         */
        int[] minX = new int[maxY - minY + 1];
        int[] maxX = new int[maxY - minY + 1];
        java.util.Arrays.fill(minX, Integer.MAX_VALUE);
        java.util.Arrays.fill(maxX, Integer.MIN_VALUE);

        for (int index = 0; index < CONTOUR_POINTS; index++) {
            int next = (index + 1) % CONTOUR_POINTS;
            addEdge(xs, ys, minX, maxX, minX.length, minY, xs[index], ys[index],
                    xs[next], ys[next]);
        }

        for (int row = 0; row < minX.length; row++) {
            if (minX[row] > maxX[row]) {
                continue;
            }
            /*
             * Строки уже обрезаны по вертикали проёма, спаны —
             * по горизонтали: заливка не должна вылезать на
             * рамку и колонку карты.
             */
            int fromX = Math.max(minX[row], view.left());
            int toX = Math.min(maxX[row], view.right() - 1);
            if (fromX > toX) {
                continue;
            }
            graphics.fill(
                    fromX,
                    minY + row,
                    toX + 1,
                    minY + row + 1,
                    color
            );
        }
    }

    private static int minY(int[] ys) {
        int result = ys[0];
        for (int y : ys) {
            result = Math.min(result, y);
        }
        return result;
    }

    private static int maxY(int[] ys) {
        int result = ys[0];
        for (int y : ys) {
            result = Math.max(result, y);
        }
        return result;
    }

    /**
     * Добавляет в спаны строк всех пиксели ребра между двумя
     * граничными точками. Растровая раскладка ребра идёт по
     * Брезенхэму — той же разметке, что и другие линии карты,
     * поэтому заливка согласована с линией ограничения.
     */
    private static void addEdge(
            int[] xs,
            int[] ys,
            int[] minX,
            int[] maxX,
            int rows,
            int minY,
            int x1,
            int y1,
            int x2,
            int y2
    ) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int error = dx + dy;
        int x = x1;
        int y = y1;
        while (true) {
            int row = y - minY;
            if (row >= 0 && row < rows) {
                minX[row] = Math.min(minX[row], x);
                maxX[row] = Math.max(maxX[row], x);
            }
            if (x == x2 && y == y2) {
                break;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y += sy;
            }
        }
    }
}
