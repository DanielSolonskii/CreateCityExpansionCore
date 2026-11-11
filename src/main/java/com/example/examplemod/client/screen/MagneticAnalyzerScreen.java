package com.example.examplemod.client.screen;

import com.example.examplemod.client.sound.SoundHelper;
import com.example.examplemod.geology.GeologySurvey;
import com.example.examplemod.network.GeologyAnalysisPayload;
import com.example.examplemod.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Экран магнитного анализатора.
 *
 * Компоновка задаётся единственным record'ом {@link Layout},
 * который считает все прямоугольники от размеров экрана один
 * раз. Никаких россыпей magic-offset'ов: любая точка берётся
 * из именованного прямоугольника, поэтому экран корректно
 * переживает и GUI scale, и узкое окно.
 *
 * Левая зона — анимированный локатор. Он не украшает экран,
 * а сообщает состояние: чем ближе залежь, тем сильнее, чаще
 * и ярче сигнал; внутри залежи сигнал переходит в устойчивый
 * захват; в пустоте остаётся только шум.
 *
 * Намеренно НЕ показывает координаты месторождения: богатый
 * участок находится перемещением и сравнением показаний.
 */
public final class MagneticAnalyzerScreen extends Screen {

    /* Палитра прибора. */
    private static final int SHELL = 0xFF101314;
    private static final int SECTION = 0xFF171C1D;
    private static final int SECTION_DEEP = 0xFF0B0E0F;
    private static final int BORDER = 0xFF2E3536;
    private static final int ACCENT = 0xFFD6A84F;
    private static final int SIGNAL = 0xFF9BE564;
    private static final int SIGNAL_WEAK = 0xFF4C6B3A;
    private static final int ALERT = 0xFFE45C5C;
    private static final int TEXT = 0xFFE0E5E2;
    private static final int TEXT_MUTED = 0xFF8B958F;
    private static final int TEXT_FAINT = 0xFF5C6663;
    private static final int RADAR_VOID = 0xFF06090A;
    private static final int RADAR_GRID = 0xFF1F2A24;

    /** Радиус сканирования анализатора, в блоках. */
    private static final int SCAN_RADIUS = GeologySurvey.SCAN_RADIUS_BLOCKS;

    private static final long SCAN_DURATION_MS = 1_800L;
    private static final long NOW = 50L;

    private final GeologyAnalysisPayload analysis;
    private long openedAt;

    /**
     * Вся компоновка экрана, посчитанная один раз на init.
     *
     * record, а не набор полей: прямоугольники не могут
     * разъехаться между собой, потому что все они выводятся
     * из одного набора входных размеров.
     */
    private record Layout(
            int left,
            int top,
            int right,
            int bottom,
            int headerBottom,
            int bodyTop,
            int footerTop,
            int locatorLeft,
            int locatorTop,
            int locatorRight,
            int locatorBottom,
            int dataLeft,
            int dataTop,
            int dataRight,
            int dataBottom
    ) {

        /**
         * Размеры панели подобраны так, чтобы влезать
         * минимальная читаемая компоновка; всё, что не влезает,
         * сжимается пропорционально окну.
         */
        static Layout of(int screenWidth, int screenHeight) {
            int width = Math.min(PREFERRED_WIDTH, screenWidth - 16);
            int height = Math.min(PREFERRED_HEIGHT, screenHeight - 16);
            width = Math.max(width, MIN_WIDTH);
            height = Math.max(height, MIN_HEIGHT);

            int left = (screenWidth - width) / 2;
            int top = (screenHeight - height) / 2;
            int right = left + width;
            int bottom = top + height;

            int headerHeight = Math.max(HEADER_MIN, height / 5);
            int footerHeight = Math.max(FOOTER_MIN, height / 9);
            int headerBottom = top + headerHeight;
            int footerTop = bottom - footerHeight;
            int bodyTop = headerBottom;

            int padding = Math.max(PAD_MIN, width / 34);
            int gap = Math.max(GAP_MIN, width / 40);

            int bodyLeft = left + padding;
            int bodyRight = right - padding;

            /*
             * Локатор всегда квадратный и всегда первым
             * столбцом: иначе радар деформируется при
             * нестандартном GUI scale.
             */
            int bodyHeight = Math.max(1, footerTop - bodyTop - padding);
            int available = bodyRight - bodyLeft - gap;
            int locatorSide = Math.min(available, bodyHeight);
            locatorSide = Math.max(locatorSide, MIN_LOCATOR_SIDE);

            int locatorLeft = bodyLeft;
            int locatorTop = bodyTop;
            int locatorRight = bodyLeft + locatorSide;
            int locatorBottom = locatorTop + locatorSide;

            int dataLeft = locatorRight + gap;
            int dataTop = bodyTop;
            int dataRight = bodyRight;
            int dataBottom = bodyTop + bodyHeight;

            return new Layout(
                    left, top, right, bottom,
                    headerBottom, bodyTop, footerTop,
                    locatorLeft, locatorTop, locatorRight, locatorBottom,
                    dataLeft, dataTop, dataRight, dataBottom
            );
        }

        int locatorWidth() {
            return locatorRight - locatorLeft;
        }

        int locatorHeight() {
            return locatorBottom - locatorTop;
        }

        int dataWidth() {
            return dataRight - dataLeft;
        }

        int dataHeight() {
            return dataBottom - dataTop;
        }
    }

    private static final int PREFERRED_WIDTH = 520;
    private static final int PREFERRED_HEIGHT = 300;
    private static final int MIN_WIDTH = 300;
    private static final int MIN_HEIGHT = 210;
    private static final int HEADER_MIN = 46;
    private static final int FOOTER_MIN = 26;
    private static final int PAD_MIN = 10;
    private static final int GAP_MIN = 8;
    private static final int MIN_LOCATOR_SIDE = 120;

    public MagneticAnalyzerScreen(GeologyAnalysisPayload analysis) {
        super(Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.title"
        ));
        this.analysis = analysis;
        this.openedAt = System.currentTimeMillis();
    }

    public static void open(GeologyAnalysisPayload payload) {
        SoundHelper.playUi(ModSounds.ANALYZER_OPEN.get());
        SoundHelper.playUi(ModSounds.ANALYZER_SCAN_START.get());

        Minecraft.getInstance().setScreen(new MagneticAnalyzerScreen(payload));
    }

    /**
     * Публичное представление геометрии для headless-проверок.
     *
     * Экран остаётся client-only, но его компоновку нужно
     * уметь проверить без Minecraft, поэтому геометрия
     * вынесена в отдельный тип с чистой статической фабрикой.
     */
    public record LayoutResult(
            int left,
            int top,
            int right,
            int bottom,
            int headerBottom,
            int bodyTop,
            int footerTop,
            int locatorLeft,
            int locatorTop,
            int locatorRight,
            int locatorBottom,
            int dataLeft,
            int dataTop,
            int dataRight,
            int dataBottom
    ) {

        public static LayoutResult of(int screenWidth, int screenHeight) {
            Layout layout = Layout.of(screenWidth, screenHeight);
            return new LayoutResult(
                    layout.left(), layout.top(), layout.right(), layout.bottom(),
                    layout.headerBottom(), layout.bodyTop(), layout.footerTop(),
                    layout.locatorLeft(), layout.locatorTop(),
                    layout.locatorRight(), layout.locatorBottom(),
                    layout.dataLeft(), layout.dataTop(),
                    layout.dataRight(), layout.dataBottom()
            );
        }

        public int locatorWidth() {
            return locatorRight - locatorLeft;
        }

        public int locatorHeight() {
            return locatorBottom - locatorTop;
        }

        public int dataWidth() {
            return dataRight - dataLeft;
        }

        public int dataHeight() {
            return dataBottom - dataTop;
        }
    }

    /** Доступ к заглушке перевода из headless-проверок. */
    static String fallbackForTest(String suffix) {
        return fallbackFor(suffix);
    }

    private Layout layout;

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        layout = Layout.of(width, height);

        if (analysis.mode() == GeologySurvey.Mode.NO_ANOMALY) {
            SoundHelper.playUi(ModSounds.ANALYZER_NO_DEPOSIT.get());
        } else {
            SoundHelper.playUi(ModSounds.ANALYZER_DEPOSIT_FOUND.get());
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (layout == null) {
            layout = Layout.of(width, height);
        }

        graphics.fill(0, 0, width, height, 0xA8070909);

        drawShell(graphics);
        drawHeader(graphics);
        drawLocator(graphics, elapsedSeconds());
        drawData(graphics);
        drawFooter(graphics);

        /*
         * Виджеты рисуются ДО фона: super.render() вызывает
         * renderBackground(), который применяет blur.
         */
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    private void drawShell(GuiGraphics graphics) {
        graphics.fill(
                layout.left() + 6,
                layout.top() + 6,
                layout.right() + 6,
                layout.bottom() + 6,
                0x7A000000
        );
        graphics.fill(
                layout.left(),
                layout.top(),
                layout.right(),
                layout.bottom(),
                SHELL
        );

        frame(graphics, layout.left(), layout.top(), layout.right(), layout.bottom(), BORDER);
        graphics.hLine(
                layout.left() + 4,
                layout.right() - 5,
                layout.top() + 4,
                BORDER
        );
        graphics.vLine(
                layout.left() + 4,
                layout.top() + 4,
                layout.bottom() - 5,
                BORDER
        );
    }

    private void drawHeader(GuiGraphics graphics) {
        int y = layout.top();
        int bandBottom = layout.headerBottom();

        graphics.fill(
                layout.left() + 4,
                y + 4,
                layout.right() - 4,
                bandBottom - 4,
                SECTION_DEEP
        );
        graphics.hLine(
                layout.left() + 4,
                layout.right() - 5,
                bandBottom - 5,
                BORDER
        );

        int iconTop = y + Math.max(8, (bandBottom - y - 8) / 2 - 3);
        graphics.fill(
                layout.left() + 14,
                iconTop,
                layout.left() + 20,
                iconTop + 6,
                ACCENT
        );

        int textLeft = layout.left() + 28;
        graphics.drawString(font, title, textLeft, y + 12, ACCENT, false);
        graphics.drawString(
                font,
                tr("subtitle"),
                textLeft,
                y + 25,
                TEXT_MUTED,
                false
        );

        /*
         * Статус прибора: единственное место, где время
         * сканирования отражается наружу.
         */
        String status = tr(scanComplete() ? "complete" : "scanning");
        int statusColor = scanComplete() ? SIGNAL : ACCENT;
        int statusRight = layout.right() - 14;
        graphics.drawString(
                font,
                status,
                statusRight - font.width(status),
                y + 12,
                statusColor,
                false
        );

        String sector = tr("sector",
                String.valueOf(analysis.origin().getX()),
                String.valueOf(analysis.origin().getZ())
        );
        graphics.drawString(
                font,
                sector,
                statusRight - font.width(sector),
                y + 25,
                TEXT_FAINT,
                false
        );
    }

    /**
     * Локатор: концентрические круги, развёртка и сигнал.
     *
     * Всё, что видно здесь, выводится из тех же данных, что и
     * правая панель, поэтому визуал и цифры не могут
     * противоречить друг другу.
     */
    private void drawLocator(GuiGraphics graphics, float time) {
        int left = layout.locatorLeft();
        int top = layout.locatorTop();
        int right = layout.locatorRight();
        int bottom = layout.locatorBottom();

        graphics.fill(left, top, right, bottom, RADAR_VOID);
        frame(graphics, left, top, right, bottom, BORDER);

        int centreX = left + layout.locatorWidth() / 2;
        int centreY = top + layout.locatorHeight() / 2;
        int radius = Math.max(
                8,
                Math.min(layout.locatorWidth(), layout.locatorHeight()) / 2 - 8
        );

        drawRings(graphics, centreX, centreY, radius);
        drawSweep(graphics, centreX, centreY, radius, time);

        double strength = signalStrength();
        drawBlips(graphics, centreX, centreY, radius, time, strength);

        graphics.fill(
                centreX - 1,
                centreY - 1,
                centreX + 2,
                centreY + 2,
                signalColor(strength)
        );

        drawSignalBar(graphics, left, bottom - 10, layout.locatorWidth(), strength);
    }

    private void drawRings(
            GuiGraphics graphics,
            int centreX,
            int centreY,
            int radius
    ) {
        for (int step = 1; step <= 3; step++) {
            int ring = radius * step / 3;
            outlineCircle(graphics, centreX, centreY, ring, RADAR_GRID);
        }
        graphics.hLine(
                centreX - radius,
                centreX + radius,
                centreY,
                RADAR_GRID
        );
        graphics.vLine(
                centreX,
                centreY - radius,
                centreY,
                RADAR_GRID
        );
    }

    /**
     * Развёртка. Всегда вращается, но в NO_ANOMALY почти
     * не читается — прибор как бы «не держит» сигнал.
     */
    private void drawSweep(
            GuiGraphics graphics,
            int centreX,
            int centreY,
            int radius,
            float time
    ) {
        double angle = time * 1.35;
        int brightness = analysis.mode() == GeologySurvey.Mode.NO_ANOMALY
                ? SIGNAL_WEAK
                : mixColor(SIGNAL_WEAK, SIGNAL, signalStrength());

        for (int step = 0; step <= radius; step++) {
            double x = centreX + Math.cos(angle) * step;
            double y = centreY + Math.sin(angle) * step;
            graphics.fill(
                    (int) Math.round(x),
                    (int) Math.round(y),
                    (int) Math.round(x) + 2,
                    (int) Math.round(y) + 2,
                    brightness
            );
        }
    }

    /**
     * Блипы сигнала.
     *
     * В OUTSIDE луч направлен на аномалию, а число и яркость
     * импульсов растут по мере приближения. В INSIDE импульсы
     * стабильно сбиваются вокруг центра — это читается как
     * «захват». В NO_ANOMALY остаётся только редкий шум.
     */
    private void drawBlips(
            GuiGraphics graphics,
            int centreX,
            int centreY,
            int radius,
            float time,
            double strength
    ) {
        if (strength <= 0.01) {
            drawNoise(graphics, centreX, centreY, radius, time);
            return;
        }

        int pulses = 2 + (int) Math.round(strength * 5.0);
        double baseAngle = bearingAngle();

        for (int index = 0; index < pulses; index++) {
            /*
             * Импульсы расходятся от центра со временем:
             * чем сильнее сигнал, тем короче период, поэтому
             * близость читается по частоте.
             */
            double phase = (time * (0.55 + strength * 1.5) + index * 0.37) % 1.0;
            double distance = radius * (0.25 + 0.65 * phase);

            /*
             * Вне залежи сигнал идёт в направлении аномалии,
             * внутри — расходится симметрично, что и читается
             * как устойчивый захват.
             */
            double offset = analysis.mode() == GeologySurvey.Mode.OUTSIDE
                    ? 0.0
                    : (index - (pulses - 1) / 2.0) * 0.45;

            double x = centreX + Math.cos(baseAngle + offset) * distance;
            double y = centreY + Math.sin(baseAngle + offset) * distance;
            int size = 1 + (int) Math.round(strength * 2.0);

            graphics.fill(
                    (int) Math.round(x),
                    (int) Math.round(y),
                    (int) Math.round(x) + size,
                    (int) Math.round(y) + size,
                    mixColor(SIGNAL_WEAK, SIGNAL, strength * (1.0 - phase * 0.55))
            );
        }
    }

    /**
     * Шум пустоты: редкие слабые точки без структуры.
     * Детерминирован по времени, поэтому не дрожит хаотично.
     */
    private void drawNoise(
            GuiGraphics graphics,
            int centreX,
            int centreY,
            int radius,
            float time
    ) {
        int ticks = (int) (time * 6.0);
        for (int index = 0; index < 5; index++) {
            long hash = index * 0x9E3779B97F4A7C15L + ticks * 0x632BE59BD9B4E019L;
            double angle = ((hash >>> 17) & 0xFFFF) / 65535.0 * Math.PI * 2.0;
            double distance = radius * (0.25 + (((hash >>> 7) & 0xFF) / 255.0) * 0.65);
            int x = centreX + (int) Math.round(Math.cos(angle) * distance);
            int y = centreY + (int) Math.round(Math.sin(angle) * distance);
            graphics.fill(x, y, x + 1, y + 1, TEXT_FAINT);
        }
    }

    /**
     * Полоса силы сигнала под локатором.
     */
    private void drawSignalBar(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            double strength
    ) {
        int inner = width - 16;
        graphics.fill(left + 8, top, left + 8 + inner, top + 3, 0xFF11171A);

        int filled = (int) Math.round(inner * Math.max(0.0, Math.min(1.0, strength)));
        if (filled > 0) {
            graphics.fill(
                    left + 8,
                    top,
                    left + 8 + filled,
                    top + 3,
                    signalColor(strength)
            );
        }

        String label = tr("signal") + " " + Math.round(strength * 100.0) + "%";
        graphics.drawString(
                font,
                label,
                left + 8,
                top - 11,
                TEXT_MUTED,
                false
        );
    }

    /**
     * Правая панель: режим, ресурс и цифры замера.
     */
    private void drawData(GuiGraphics graphics) {
        int left = layout.dataLeft();
        int top = layout.dataTop();
        int right = layout.dataRight();
        int bottom = layout.dataBottom();

        graphics.fill(left, top, right, bottom, SECTION);
        frame(graphics, left, top, right, bottom, BORDER);

        int x = left + 12;
        int width = layout.dataWidth() - 24;

        /*
         * Заголовок режима: переводится через Component,
         * а не выводится как строка-ключ.
         */
        String mode = tr(modeKey());
        graphics.drawString(font, mode, x, top + 10, accentColor(), false);
        graphics.hLine(x, right - 12, top + 22, BORDER);

        if (!scanComplete()) {
            graphics.drawString(font, tr("reading"), x, top + 32, TEXT_MUTED, false);
            return;
        }

        int y = top + 32;
        int step = Math.max(14, (bottom - y - 24) / 7);

        switch (analysis.mode()) {
            case NO_ANOMALY -> {
                row(graphics, x, width, y, tr("no_anomaly"), tr("no_anomaly_hint"));
                y += step + 6;
                bar(graphics, x, y, width, 0.0, ALERT);
            }
            case OUTSIDE -> drawOutsideData(graphics, x, width, y, step, bottom);
            case INSIDE -> drawInsideData(graphics, x, width, y, step, bottom);
            default -> {
            }
        }
    }

    private void drawOutsideData(
            GuiGraphics graphics,
            int x,
            int width,
            int y,
            int step,
            int bottom
    ) {
        row(graphics, x, width, y, tr("resource"), resourceName());
        y += step;
        row(
                graphics, x, width, y, tr("direction"),
                tr("direction." + directionKey())
        );
        y += step;
        row(
                graphics, x, width, y, tr("distance"),
                tr("distance." + distanceBand())
        );
        y += step;
        row(
                graphics, x, width, y, tr("size"),
                tr("size_class." + sizeKey())
        );
        y += step;
        row(
                graphics, x, width, y, tr("peak_potential"),
                count(analysis.peakPotentialPerHour()) + "/h", TEXT_MUTED
        );

        double strength = signalStrength();
        bar(graphics, x, bottom - 24, width, strength, signalColor(strength));
    }

    private void drawInsideData(
            GuiGraphics graphics,
            int x,
            int width,
            int y,
            int step,
            int bottom
    ) {
        row(graphics, x, width, y, tr("resource"), resourceName());
        y += step;
        row(
                graphics, x, width, y, tr("local_potential"),
                count(analysis.localPotentialPerHour()) + "/h", SIGNAL
        );
        y += step;
        row(
                graphics, x, width, y, tr("peak_potential"),
                count(analysis.peakPotentialPerHour()) + "/h", TEXT_MUTED
        );
        y += step;
        row(
                graphics, x, width, y, tr("remaining"),
                "~" + count(analysis.remainingReserve())
        );
        y += step;
        row(graphics, x, width, y, tr("size"), tr("size_class." + sizeKey()));
        y += step;
        row(
                graphics, x, width, y, tr("lifetime"),
                tr("lifetime_hours", analysis.estimatedLifetimeHours()),
                TEXT_MUTED
        );

        double strength = signalStrength();
        bar(graphics, x, bottom - 24, width, strength, signalColor(strength));
    }

    /**
     * Строка «подпись: значение» с фиксированной колонкой
     * значений, чтобы цифры не прыгали между строками.
     */
    private void row(
            GuiGraphics graphics,
            int x,
            int width,
            int y,
            String label,
            String value
    ) {
        row(graphics, x, width, y, label, value, TEXT);
    }

    private void row(
            GuiGraphics graphics,
            int x,
            int width,
            int y,
            String label,
            String value,
            int valueColor
    ) {
        graphics.drawString(font, label, x, y, TEXT_MUTED, false);

        int valueX = x + Math.max(60, width / 2);
        graphics.drawString(font, value, valueX, y, valueColor, false);
    }

    private void bar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            double ratio,
            int color
    ) {
        graphics.fill(x, y, x + width, y + 4, 0xFF10171A);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, ratio)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 4, color);
        }
    }

    private void drawFooter(GuiGraphics graphics) {
        int y = layout.footerTop();
        graphics.fill(
                layout.left() + 4,
                y + 4,
                layout.right() - 4,
                layout.bottom() - 5,
                SECTION_DEEP
        );
        graphics.hLine(
                layout.left() + 4,
                layout.right() - 5,
                y + 4,
                BORDER
        );

        String hint = tr(hintKey());
        graphics.drawString(font, hint, layout.left() + 14, y + 12, TEXT_FAINT, false);

        String charge = tr("uses", analysis.remainingUses());
        graphics.drawString(
                font,
                charge,
                layout.right() - 14 - font.width(charge),
                y + 12,
                TEXT_MUTED,
                false
        );
    }

    /* ---------------------------------------------------------------
     *  СИГНАЛ
     * --------------------------------------------------------------- */

    /**
     * Сила сигнала 0..1.
     *
     * Единственный источник истины для локатора, полосы внизу
     * и подсветки панели. Считается только из данных payload,
     * поэтому анимация не может разойтись с текстом.
     */
    double signalStrength() {
        return switch (analysis.mode()) {
            case NO_ANOMALY -> 0.0;
            case OUTSIDE -> outsideStrength();
            case INSIDE -> insideStrength();
        };
    }

    /**
     * Снаружи: чем ближе граница залежи, тем сильнее сигнал.
     * Дальше 200 блоков сигнала уже нет вовсе.
     */
    private double outsideStrength() {
        int distance = Math.max(0, analysis.boundaryDistanceBlocks());
        if (distance >= SCAN_RADIUS) {
            return 0.0;
        }
        return Math.max(0.08, 1.0 - (double) distance / SCAN_RADIUS);
    }

    /**
     * Внутри: устойчивый захват. Сила всегда высокая и
     * зависит от того, насколько богата точка под игроком
     * относительно пика залежи.
     */
    private double insideStrength() {
        double peak = Math.max(1.0, analysis.peakPotentialPerHour());
        double share = Math.max(
                0.0,
                Math.min(1.0, analysis.localPotentialPerHour() / peak)
        );
        return 0.82 + share * 0.18;
    }

    /**
     * Угол направления на аномалию. Minecraft: север = -Z,
     * восток = +X, поэтому угол считается от положительной
     * оси X и растёт по часовой стрелке на экране.
     */
    private double bearingAngle() {
        int index = analysis.direction();
        if (index < 0 || index >= GeologySurvey.DIRECTION_VECTORS.length) {
            return 0.0;
        }
        double dx = GeologySurvey.DIRECTION_VECTORS[index * 2];
        double dz = GeologySurvey.DIRECTION_VECTORS[index * 2 + 1];
        if (dx == 0.0 && dz == 0.0) {
            return 0.0;
        }
        return Math.atan2(dz, dx);
    }

    private int signalColor(double strength) {
        if (analysis.mode() == GeologySurvey.Mode.NO_ANOMALY) {
            return SIGNAL_WEAK;
        }
        return mixColor(SIGNAL_WEAK, SIGNAL, strength);
    }

    private int accentColor() {
        return switch (analysis.mode()) {
            case NO_ANOMALY -> ALERT;
            case OUTSIDE -> ACCENT;
            case INSIDE -> SIGNAL;
        };
    }

    private String hintKey() {
        return switch (analysis.mode()) {
            case INSIDE -> "search_hint";
            case OUTSIDE -> "no_core_hint";
            case NO_ANOMALY -> "no_anomaly_hint";
        };
    }

    /* ---------------------------------------------------------------
     *  ТЕКСТ И ЛОКАЛИЗАЦИЯ
     * --------------------------------------------------------------- */

    /**
     * Единая точка перевода для экрана.
     *
     * Все подписи и режимы проходят через неё, поэтому
     * сырой localization key попасть на экран не может: если
     * ключа нет, возвращается человекочитаемая заглушка, а не
     * строка вида gui.createcityexpansioncore....
     */
    private static String tr(String suffix, Object... args) {
        String key = "gui.createcityexpansioncore.magnetic_analyzer." + suffix;
        try {
            return Component.translatable(key, args).getString();
        } catch (RuntimeException exception) {
            return fallbackFor(suffix);
        }
    }

    /**
     * Заглушки на случай отсутствующего ключа: короткие
     * английские подписи вместо сырых ключей.
     */
    private static String fallbackFor(String suffix) {
        String last = suffix.contains(".")
                ? suffix.substring(suffix.lastIndexOf('.') + 1)
                : suffix;
        return switch (last) {
            case "inside" -> "IN-SITU";
            case "outside" -> "NEAREST";
            case "none" -> "NO SIGNAL";
            default -> last.toUpperCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private String resourceName() {
        if (!analysis.hasResource()) {
            return "-";
        }
        String path = analysis.resourceId().getPath();
        String key = "resource.createcityexpansioncore." + path;
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? path.toUpperCase(Locale.ROOT) : translated;
    }

    private String modeKey() {
        return switch (analysis.mode()) {
            case OUTSIDE -> "mode.outside";
            case INSIDE -> "mode.inside";
            default -> "mode.none";
        };
    }

    private String directionKey() {
        int index = analysis.direction();
        return index < 0 || index >= GeologySurvey.DIRECTION_KEYS.length
                ? "unknown"
                : GeologySurvey.DIRECTION_KEYS[index];
    }

    private String sizeKey() {
        return analysis.sizeClass().name().toLowerCase(Locale.ROOT);
    }

    /**
     * Грубая дистанция до границы залежи, а не точный
     * измеренный диапазон: игрок получает ориентир, а не
     * координаты ядра.
     */
    private String distanceBand() {
        int distance = Math.max(0, analysis.boundaryDistanceBlocks());
        if (distance < 50) {
            return "very_close";
        }
        if (distance < 100) {
            return "close";
        }
        if (distance < 150) {
            return "medium";
        }
        return "far";
    }

    private static String count(long value) {
        return GeologyUiFormat.count(value);
    }

    /* ---------------------------------------------------------------
     *  ГРАФИКА
     * --------------------------------------------------------------- */

    private static void frame(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        graphics.hLine(left, right - 1, top, color);
        graphics.hLine(left, right - 1, bottom - 1, color);
        graphics.vLine(left, top, bottom - 1, color);
        graphics.vLine(right - 1, top, bottom - 1, color);
    }

    /**
     * Контур окружности из коротких отрезков: у GuiGraphics
     * нет эллипса, а полноценный треугольник здесь был бы
     * дорогой и всё равно ступенчатым.
     */
    private static void outlineCircle(
            GuiGraphics graphics,
            int centreX,
            int centreY,
            int radius,
            int color
    ) {
        if (radius <= 0) {
            return;
        }
        int steps = Math.max(16, radius);
        int previousX = centreX + radius;
        int previousY = centreY;
        for (int step = 1; step <= steps; step++) {
            double angle = 2.0 * Math.PI * step / steps;
            int x = centreX + (int) Math.round(Math.cos(angle) * radius);
            int y = centreY + (int) Math.round(Math.sin(angle) * radius);
            graphics.hLine(
                    Math.min(previousX, x),
                    Math.max(previousX, x),
                    y,
                    color
            );
            if (previousY != y) {
                graphics.vLine(x, Math.min(previousY, y), Math.max(previousY, y), color);
            }
            previousX = x;
            previousY = y;
        }
    }

    private static int mixColor(int from, int to, double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int a = mix(from >> 24 & 0xFF, to >> 24 & 0xFF, clamped);
        int r = mix(from >> 16 & 0xFF, to >> 16 & 0xFF, clamped);
        int g = mix(from >> 8 & 0xFF, to >> 8 & 0xFF, clamped);
        int b = mix(from & 0xFF, to & 0xFF, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int from, int to, double ratio) {
        return (int) Math.round(from + (to - from) * ratio);
    }

    private float elapsedSeconds() {
        return (System.currentTimeMillis() - openedAt) / 1_000.0f;
    }

    private boolean scanComplete() {
        return System.currentTimeMillis() - openedAt >= SCAN_DURATION_MS;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}