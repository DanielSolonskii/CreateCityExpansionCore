package com.example.examplemod.client.screen;

import com.example.examplemod.network.GeologyAnalysisPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MagneticAnalyzerScreen extends Screen {

    private final GeologyAnalysisPayload analysis;

    private enum State {
        INITIALIZING,
        SCANNING,
        ANALYZING,
        COMPLETE
    }

    private State state = State.INITIALIZING;

    private long openedAt;

    /*
     * =========================================
     * TIMING
     * =========================================
     */

    private static final long INIT_TIME = 700;
    private static final long SCAN_TIME = 1700;
    private static final long ANALYZE_TIME = 1900;

    /*
     * =========================================
     * PANEL
     * =========================================
     */

    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 350;

    /*
     * =========================================
     * COLORS
     * =========================================
     */

    private static final int OVERLAY = 0x8A070909;

    private static final int PANEL = 0xFF141718;
    private static final int PANEL_DARK = 0xFF0C0F10;
    private static final int PANEL_LIGHT = 0xFF202526;

    private static final int BORDER = 0xFF343B3C;
    private static final int BORDER_LIGHT = 0xFF596160;

    private static final int GREEN = 0xFF9BE564;
    private static final int GREEN_BRIGHT = 0xFFC4FF83;
    private static final int GREEN_DARK = 0xFF365A32;
    private static final int GREEN_DIM = 0xFF1F3323;

    private static final int GOLD = 0xFFD6A84F;
    private static final int GOLD_DARK = 0xFF604B27;

    private static final int RED = 0xFFE45C5C;

    private static final int TEXT = 0xFFE0E5E2;
    private static final int TEXT_DIM = 0xFF858F8B;

    private static final int COPPER = 0xFFE59B5C;
    private static final int IRON = 0xFFE1C76B;
    private static final int COAL = 0xFFD95C5C;
    private static final int ZINC = 0xFF80B8DE;


    public MagneticAnalyzerScreen(
            GeologyAnalysisPayload analysis
    ) {
        super(
                Component.translatable(
                        "gui.createcityexpansioncore.magnetic_analyzer.title"
                )
        );

        this.analysis = analysis;
        this.openedAt = System.currentTimeMillis();
    }


    public static void open(
            GeologyAnalysisPayload payload
    ) {
        Minecraft.getInstance().setScreen(
                new MagneticAnalyzerScreen(payload)
        );
    }


    @Override
    protected void init() {
        super.init();

        openedAt = System.currentTimeMillis();
        state = State.INITIALIZING;
    }


    @Override
    public void tick() {

        long elapsed =
                System.currentTimeMillis() - openedAt;

        if (elapsed < INIT_TIME) {

            state = State.INITIALIZING;

        } else if (elapsed < INIT_TIME + SCAN_TIME) {

            state = State.SCANNING;

        } else if (
                elapsed <
                        INIT_TIME
                                + SCAN_TIME
                                + ANALYZE_TIME
        ) {

            state = State.ANALYZING;

        } else {

            state = State.COMPLETE;
        }
    }


    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        long elapsed =
                System.currentTimeMillis() - openedAt;


        /*
         * =========================================
         * СОБСТВЕННОЕ ЗАТЕМНЕНИЕ
         * =========================================
         */

        graphics.fill(
                0,
                0,
                width,
                height,
                OVERLAY
        );


        /*
         * =========================================
         * ГЕОМЕТРИЯ ПАНЕЛИ
         * =========================================
         */

        int panelWidth =
                Math.min(
                        PANEL_WIDTH,
                        width - 30
                );

        int panelHeight =
                Math.min(
                        PANEL_HEIGHT,
                        height - 30
                );

        int left =
                (width - panelWidth) / 2;

        int top =
                (height - panelHeight) / 2;

        int right =
                left + panelWidth;

        int bottom =
                top + panelHeight;


        /*
         * =========================================
         * ПОЯВЛЕНИЕ
         * =========================================
         */

        float appear =
                clamp(
                        elapsed / 280.0f
                );

        int shadowAlpha =
                (int) (130 * appear);

        graphics.fill(
                left + 7,
                top + 7,
                right + 7,
                bottom + 7,
                shadowAlpha << 24
        );


        /*
         * =========================================
         * ОСНОВНАЯ ПАНЕЛЬ
         * =========================================
         */

        graphics.fill(
                left,
                top,
                right,
                bottom,
                PANEL
        );

        drawFrame(
                graphics,
                left,
                top,
                right,
                bottom
        );


        /*
         * =========================================
         * HEADER
         * =========================================
         */

        drawHeader(
                graphics,
                left,
                top,
                right,
                elapsed
        );


        /*
         * =========================================
         * CONTENT
         * =========================================
         */

        int contentLeft =
                left + 16;

        int contentRight =
                right - 16;

        int contentTop =
                top + 58;

        int contentBottom =
                bottom - 47;


        int radarWidth = 292;
        int gap = 12;

        int resourcesLeft =
                contentLeft
                        + radarWidth
                        + gap;

        int resourcesWidth =
                contentRight
                        - resourcesLeft;


        drawRadarPanel(
                graphics,
                contentLeft,
                contentTop,
                radarWidth,
                contentBottom - contentTop,
                elapsed
        );


        drawResourcePanel(
                graphics,
                resourcesLeft,
                contentTop,
                resourcesWidth,
                contentBottom - contentTop,
                elapsed
        );


        /*
         * =========================================
         * FOOTER
         * =========================================
         */

        drawFooter(
                graphics,
                left,
                bottom,
                right,
                elapsed
        );


        /*
         * =========================================
         * CRT EFFECT
         * =========================================
         */

        drawScanlines(
                graphics,
                left + 5,
                top + 5,
                right - 5,
                bottom - 5,
                elapsed
        );


        /*
         * =========================================
         * ПЛАВНОЕ ПОЯВЛЕНИЕ
         * =========================================
         */

        if (appear < 1.0f) {

            int alpha =
                    (int)
                            ((1.0f - appear)
                                    * 150);

            graphics.fill(
                    left,
                    top,
                    right,
                    bottom,
                    alpha << 24
            );
        }


        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }


    /*
     * =========================================
     * FRAME
     * =========================================
     */

    private void drawFrame(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {

        graphics.hLine(
                left,
                right - 1,
                top,
                BORDER_LIGHT
        );

        graphics.hLine(
                left,
                right - 1,
                bottom - 1,
                BORDER
        );

        graphics.vLine(
                left,
                top,
                bottom - 1,
                BORDER
        );

        graphics.vLine(
                right - 1,
                top,
                bottom - 1,
                BORDER
        );


        graphics.hLine(
                left + 4,
                right - 5,
                top + 4,
                BORDER
        );

        graphics.hLine(
                left + 4,
                right - 5,
                bottom - 5,
                BORDER
        );

        graphics.vLine(
                left + 4,
                top + 4,
                bottom - 5,
                BORDER
        );

        graphics.vLine(
                right - 5,
                top + 4,
                bottom - 5,
                BORDER
        );


        int corner = 8;

        graphics.hLine(
                left + 8,
                left + 8 + corner,
                top + 8,
                GOLD
        );

        graphics.vLine(
                left + 8,
                top + 8,
                top + 8 + corner,
                GOLD
        );


        graphics.hLine(
                right - 8 - corner,
                right - 8,
                top + 8,
                GOLD
        );

        graphics.vLine(
                right - 8,
                top + 8,
                top + 8 + corner,
                GOLD
        );


        graphics.hLine(
                left + 8,
                left + 8 + corner,
                bottom - 9,
                GOLD_DARK
        );

        graphics.vLine(
                left + 8,
                bottom - 9 - corner,
                bottom - 9,
                GOLD_DARK
        );


        graphics.hLine(
                right - 8 - corner,
                right - 8,
                bottom - 9,
                GOLD_DARK
        );

        graphics.vLine(
                right - 8,
                bottom - 9 - corner,
                bottom - 9,
                GOLD_DARK
        );
    }


    /*
     * =========================================
     * HEADER
     * =========================================
     */

    private void drawHeader(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            long elapsed
    ) {

        graphics.fill(
                left + 6,
                top + 6,
                right - 6,
                top + 48,
                PANEL_DARK
        );


        graphics.fill(
                left + 17,
                top + 18,
                left + 23,
                top + 24,
                GOLD
        );


        graphics.drawString(
                font,
                tr("title"),
                left + 34,
                top + 13,
                GOLD,
                false
        );


        graphics.drawString(
                font,
                tr("subtitle"),
                left + 34,
                top + 29,
                TEXT_DIM,
                false
        );


        Component status =
                getStatusText();


        int statusWidth =
                font.width(status);


        int statusX =
                right - 118 - statusWidth;


        boolean blink =
                (elapsed / 350) % 2 == 0;


        if (
                state != State.COMPLETE
                        && blink
        ) {

            graphics.fill(
                    statusX - 10,
                    top + 20,
                    statusX - 6,
                    top + 24,
                    getStatusColor()
            );
        }


        graphics.drawString(
                font,
                status,
                statusX,
                top + 18,
                getStatusColor(),
                false
        );


        String charge =
                String.format(
                        "%02d/10",
                        analysis.remainingUses()
                );


        graphics.drawString(
                font,
                Component.literal(charge),
                right - 55,
                top + 14,
                TEXT,
                false
        );


        graphics.drawString(
                font,
                tr("charge"),
                right - 55,
                top + 29,
                TEXT_DIM,
                false
        );
    }


    /*
     * =========================================
     * RADAR
     * =========================================
     */

    private void drawRadarPanel(
            GuiGraphics graphics,
            int x,
            int y,
            int panelWidth,
            int panelHeight,
            long elapsed
    ) {

        graphics.fill(
                x,
                y,
                x + panelWidth,
                y + panelHeight,
                PANEL_DARK
        );


        drawPanelBorder(
                graphics,
                x,
                y,
                panelWidth,
                panelHeight
        );


        graphics.drawString(
                font,
                tr("field"),
                x + 12,
                y + 9,
                TEXT_DIM,
                false
        );


        int centerX =
                x + panelWidth / 2;

        int centerY =
                y + 105;

        int radius =
                Math.min(
                        82,
                        panelWidth / 2 - 25
                );


        drawCircle(
                graphics,
                centerX,
                centerY,
                radius,
                GREEN_DARK
        );


        drawCircle(
                graphics,
                centerX,
                centerY,
                radius * 2 / 3,
                GREEN_DIM
        );


        drawCircle(
                graphics,
                centerX,
                centerY,
                radius / 3,
                GREEN_DIM
        );


        graphics.hLine(
                centerX - radius,
                centerX + radius,
                centerY,
                GREEN_DIM
        );


        graphics.vLine(
                centerX,
                centerY - radius,
                centerY + radius,
                GREEN_DIM
        );


        /*
         * Сетка.
         */

        for (
                int offset = -radius;
                offset <= radius;
                offset += 20
        ) {

            graphics.hLine(
                    centerX - radius,
                    centerX + radius,
                    centerY + offset,
                    0x302F6734
            );

            graphics.vLine(
                    centerX + offset,
                    centerY - radius,
                    centerY + radius,
                    0x302F6734
            );
        }


        /*
         * Луч.
         */

        float scanProgress =
                clamp(
                        (
                                elapsed
                                        - INIT_TIME
                        )
                                / (float) SCAN_TIME
                );


        double angle =
                -Math.PI / 2
                        + scanProgress
                        * Math.PI
                        * 2;


        int beamX =
                centerX
                        + (int)
                        (
                                Math.cos(angle)
                                        * radius
                        );


        int beamY =
                centerY
                        + (int)
                        (
                                Math.sin(angle)
                                        * radius
                        );


        if (
                state == State.SCANNING
                        || state == State.ANALYZING
        ) {

            drawThickLine(
                    graphics,
                    centerX,
                    centerY,
                    beamX,
                    beamY,
                    2,
                    GREEN
            );
        }


        /*
         * Сигнальные точки.
         */

        drawResourceSignal(
                graphics,
                centerX,
                centerY,
                radius,
                analysis.copper(),
                0.2,
                COPPER,
                elapsed
        );


        drawResourceSignal(
                graphics,
                centerX,
                centerY,
                radius,
                analysis.iron(),
                2.1,
                IRON,
                elapsed
        );


        drawResourceSignal(
                graphics,
                centerX,
                centerY,
                radius,
                analysis.coal(),
                3.7,
                COAL,
                elapsed
        );


        drawResourceSignal(
                graphics,
                centerX,
                centerY,
                radius,
                analysis.zinc(),
                5.2,
                ZINC,
                elapsed
        );


        /*
         * Центральный импульс.
         */

        int pulse =
                (int)
                        (
                                (
                                        Math.sin(
                                                elapsed / 130.0
                                        ) + 1
                                )
                                        * 2
                        );


        graphics.fill(
                centerX - 3 - pulse,
                centerY - 3 - pulse,
                centerX + 4 + pulse,
                centerY + 4 + pulse,
                GREEN_BRIGHT
        );


        /*
         * Координаты.
         */

        int coordinateY =
                y + panelHeight - 40;


        graphics.drawString(
                font,
                Component.literal(
                        "X " + analysis.position().getX()
                ),
                x + 12,
                coordinateY,
                TEXT,
                false
        );


        graphics.drawString(
                font,
                Component.literal(
                        "Y " + analysis.position().getY()
                ),
                x + 12,
                coordinateY + 12,
                TEXT,
                false
        );


        graphics.drawString(
                font,
                Component.literal(
                        "Z " + analysis.position().getZ()
                ),
                x + 12,
                coordinateY + 24,
                TEXT,
                false
        );


        /*
         * Сила сигнала.
         */

        int average =
                getAverage();


        Component signal;


        if (average >= 70) {

            signal = tr("signal.excellent");

        } else if (average >= 40) {

            signal = tr("signal.stable");

        } else if (average > 5) {

            signal = tr("signal.weak");

        } else {

            signal = tr("signal.none");
        }


        graphics.drawString(
                font,
                signal,
                x + 112,
                coordinateY + 12,
                average > 5
                        ? GREEN
                        : RED,
                false
        );
    }


    /*
     * =========================================
     * RESOURCE PANEL
     * =========================================
     */

    private void drawResourcePanel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            long elapsed
    ) {

        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                PANEL_DARK
        );


        drawPanelBorder(
                graphics,
                x,
                y,
                width,
                height
        );


        graphics.drawString(
                font,
                tr("resources"),
                x + 12,
                y + 9,
                TEXT_DIM,
                false
        );


        int rowHeight = 41;

        int rowY = y + 31;


        drawAnimatedResourceRow(
                graphics,
                x,
                rowY,
                width,
                tr("copper").getString(),
                analysis.copper(),
                COPPER,
                elapsed,
                0
        );


        drawAnimatedResourceRow(
                graphics,
                x,
                rowY + rowHeight,
                width,
                tr("iron").getString(),
                analysis.iron(),
                IRON,
                elapsed,
                1
        );


        drawAnimatedResourceRow(
                graphics,
                x,
                rowY + rowHeight * 2,
                width,
                tr("coal").getString(),
                analysis.coal(),
                COAL,
                elapsed,
                2
        );


        drawAnimatedResourceRow(
                graphics,
                x,
                rowY + rowHeight * 3,
                width,
                tr("zinc").getString(),
                analysis.zinc(),
                ZINC,
                elapsed,
                3
        );


        graphics.hLine(
                x + 10,
                x + width - 10,
                y + height - 14,
                GREEN_DARK
        );
    }


    /*
     * =========================================
     * RESOURCE ROW
     * =========================================
     */

    private void drawAnimatedResourceRow(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String name,
            int value,
            int color,
            long elapsed,
            int index
    ) {

        long revealStart =
                INIT_TIME
                        + 250
                        + index * 260L;


        float reveal =
                clamp(
                        (
                                elapsed
                                        - revealStart
                        )
                                / 420.0f
                );


        if (reveal <= 0.0f) {

            graphics.drawString(
                    font,
                    Component.literal(name),
                    x + 10,
                    y + 8,
                    TEXT_DIM,
                    false
            );


            graphics.drawString(
                    font,
                    tr("searching"),
                    x + 79,
                    y + 8,
                    GREEN_DARK,
                    false
            );

            return;
        }


        float eased =
                easeOutCubic(reveal);


        float animatedValue =
                value * eased;


        int displayedValue =
                Math.round(animatedValue);


        int padding = 10;

        int percentWidth = 32;


        int barLeft =
                x + 67;


        int percentRight =
                x + width - padding;


        int barRight =
                percentRight
                        - percentWidth
                        - 8;


        graphics.drawString(
                font,
                Component.literal(name),
                x + 10,
                y + 8,
                TEXT,
                false
        );


        graphics.fill(
                barLeft,
                y + 5,
                barRight,
                y + 17,
                PANEL_LIGHT
        );


        int barWidth =
                barRight - barLeft;


        /*
         * Деления.
         */

        for (
                int tick = 20;
                tick < 100;
                tick += 20
        ) {

            int tickX =
                    barLeft
                            + barWidth
                            * tick
                            / 100;


            graphics.fill(
                    tickX,
                    y + 5,
                    tickX + 1,
                    y + 17,
                    0x55343B3C
            );
        }


        /*
         * Заполнение.
         */

        int fillWidth =
                (int)
                        (
                                barWidth
                                        * animatedValue
                                        / 100.0f
                        );


        if (fillWidth > 0) {

            graphics.fill(
                    barLeft,
                    y + 5,
                    barLeft + fillWidth,
                    y + 17,
                    color
            );
        }


        if (fillWidth > 3) {

            graphics.fill(
                    barLeft,
                    y + 5,
                    barLeft + fillWidth,
                    y + 7,
                    lighten(color)
            );
        }


        /*
         * Процент.
         */

        String percent =
                displayedValue + "%";


        int textWidth =
                font.width(percent);


        graphics.drawString(
                font,
                Component.literal(percent),
                percentRight - textWidth,
                y + 7,
                color,
                false
        );


        /*
         * Статус.
         */

        Component status;


        if (displayedValue >= 70) {

            status = tr("rich");

        } else if (displayedValue >= 40) {

            status = tr("good");

        } else if (displayedValue >= 15) {

            status = tr("low");

        } else {

            status = tr("trace");
        }


        int statusWidth =
                font.width(status);


        graphics.drawString(
                font,
                status,
                x + width - statusWidth - 10,
                y + 21,
                TEXT_DIM,
                false
        );
    }


    /*
     * =========================================
     * FOOTER
     * =========================================
     */

    private void drawFooter(
            GuiGraphics graphics,
            int left,
            int bottom,
            int right,
            long elapsed
    ) {

        int y =
                bottom - 35;


        graphics.fill(
                left + 6,
                y,
                right - 6,
                bottom - 6,
                PANEL_DARK
        );


        graphics.hLine(
                left + 6,
                right - 7,
                y,
                BORDER
        );


        Component verdict =
                getVerdict();


        int verdictColor =
                getVerdictColor();


        if (state == State.COMPLETE) {

            graphics.fill(
                    left + 12,
                    y + 7,
                    left + 16,
                    y + 23,
                    verdictColor
            );


            drawFittedText(
                    graphics,
                    verdict,
                    left + 23,
                    y + 10,
                    right - 180,
                    verdictColor
            );

        } else {

            drawFittedText(
                    graphics,
                    getProcessingText(elapsed),
                    left + 18,
                    y + 10,
                    right - 160,
                    TEXT_DIM
            );
        }


        /*
         * Использований осталось.
         */

        String charge =
                analysis.remainingUses()
                        + " "
                        + tr("uses_remaining").getString();


        int chargeWidth =
                font.width(charge);


        graphics.drawString(
                font,
                Component.literal(charge),
                right - 18 - chargeWidth,
                y + 10,
                TEXT_DIM,
                false
        );
    }


    /*
     * =========================================
     * STATUS
     * =========================================
     */

    private Component getStatusText() {

        return switch (state) {

            case INITIALIZING ->
                    tr("initializing");

            case SCANNING ->
                    tr("scanning");

            case ANALYZING ->
                    tr("analyzing");

            case COMPLETE ->
                    tr("complete");
        };
    }


    private int getStatusColor() {

        return switch (state) {

            case INITIALIZING ->
                    TEXT_DIM;

            case SCANNING ->
                    GOLD;

            case ANALYZING ->
                    GREEN;

            case COMPLETE ->
                    GREEN;
        };
    }


    /*
     * =========================================
     * VERDICT
     * =========================================
     */

    private Component getVerdict() {

        int average =
                getAverage();


        if (average >= 70) {

            return tr(
                    "verdict.high"
            );

        } else if (average >= 40) {

            return tr(
                    "verdict.promising"
            );

        } else if (average >= 15) {

            return tr(
                    "verdict.low"
            );

        } else {

            return tr(
                    "verdict.empty"
            );
        }
    }


    private int getVerdictColor() {

        int average =
                getAverage();


        if (average >= 70) {

            return GREEN_BRIGHT;

        } else if (average >= 40) {

            return GREEN;

        } else if (average >= 15) {

            return GOLD;

        } else {

            return RED;
        }
    }


    /*
     * =========================================
     * PROCESSING TEXT
     * =========================================
     */

    private Component getProcessingText(
            long elapsed
    ) {

        if (
                state
                        == State.INITIALIZING
        ) {

            return tr(
                    "calibrating"
            );
        }


        if (
                state
                        == State.SCANNING
        ) {

            int dots =
                    (int)
                            (
                                    (
                                            elapsed
                                                    / 350
                                    )
                                            % 4
                            );


            return Component.literal(
                    tr(
                            "scanning_field"
                    ).getString()
                            + ".".repeat(dots)
            );
        }


        return tr(
                "processing"
        );
    }


    /*
     * =========================================
     * RADAR SIGNAL
     * =========================================
     */

    private void drawResourceSignal(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int value,
            double angleOffset,
            int color,
            long elapsed
    ) {

        if (value <= 5) {
            return;
        }


        float strength =
                value / 100.0f;


        double angle =
                angleOffset;


        int distance =
                (int)
                        (
                                radius
                                        * (
                                        0.25
                                                + strength
                                                * 0.60
                                )
                        );


        int x =
                centerX
                        + (int)
                        (
                                Math.cos(angle)
                                        * distance
                        );


        int y =
                centerY
                        + (int)
                        (
                                Math.sin(angle)
                                        * distance
                        );


        int pulse =
                (int)
                        (
                                (
                                        Math.sin(
                                                elapsed
                                                        / 160.0
                                                        + angleOffset
                                        )
                                                + 1
                                )
                                        * 1.5
                        );


        graphics.fill(
                x - 2 - pulse,
                y - 2 - pulse,
                x + 3 + pulse,
                y + 3 + pulse,
                color
        );
    }


    /*
     * =========================================
     * PANEL BORDER
     * =========================================
     */

    private void drawPanelBorder(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {

        graphics.hLine(
                x,
                x + width - 1,
                y,
                BORDER
        );

        graphics.hLine(
                x,
                x + width - 1,
                y + height - 1,
                BORDER
        );

        graphics.vLine(
                x,
                y,
                y + height - 1,
                BORDER
        );

        graphics.vLine(
                x + width - 1,
                y,
                y + height - 1,
                BORDER
        );
    }


    /*
     * =========================================
     * CRT SCANLINES
     * =========================================
     */

    private void drawScanlines(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            long elapsed
    ) {

        int offset =
                (int)
                        (
                                (
                                        elapsed
                                                / 80
                                )
                                        % 4
                        );


        for (
                int y = top + offset;
                y < bottom;
                y += 4
        ) {

            graphics.hLine(
                    left,
                    right,
                    y,
                    0x08000000
            );
        }
    }


    /*
     * =========================================
     * CIRCLE
     * =========================================
     */

    private void drawCircle(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int color
    ) {

        int previousX =
                centerX + radius;

        int previousY =
                centerY;


        for (
                int angle = 2;
                angle <= 360;
                angle += 2
        ) {

            double radians =
                    Math.toRadians(angle);


            int x =
                    centerX
                            + (int)
                            (
                                    Math.cos(radians)
                                            * radius
                            );


            int y =
                    centerY
                            + (int)
                            (
                                    Math.sin(radians)
                                            * radius
                            );


            drawPixelLine(
                    graphics,
                    previousX,
                    previousY,
                    x,
                    y,
                    color
            );


            previousX = x;
            previousY = y;
        }
    }


    /*
     * =========================================
     * PIXEL LINE
     * =========================================
     */

    private void drawPixelLine(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {

        int dx =
                Math.abs(x2 - x1);

        int dy =
                Math.abs(y2 - y1);

        int steps =
                Math.max(
                        dx,
                        dy
                );


        if (steps == 0) {

            graphics.fill(
                    x1,
                    y1,
                    x1 + 1,
                    y1 + 1,
                    color
            );

            return;
        }


        for (
                int i = 0;
                i <= steps;
                i++
        ) {

            int x =
                    x1
                            + (x2 - x1)
                            * i
                            / steps;

            int y =
                    y1
                            + (y2 - y1)
                            * i
                            / steps;


            graphics.fill(
                    x,
                    y,
                    x + 1,
                    y + 1,
                    color
            );
        }
    }


    /*
     * =========================================
     * THICK LINE
     * =========================================
     */

    private void drawThickLine(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int thickness,
            int color
    ) {

        int dx =
                Math.abs(x2 - x1);

        int dy =
                Math.abs(y2 - y1);

        int steps =
                Math.max(
                        dx,
                        dy
                );


        if (steps == 0) {
            return;
        }


        for (
                int i = 0;
                i <= steps;
                i++
        ) {

            int x =
                    x1
                            + (x2 - x1)
                            * i
                            / steps;

            int y =
                    y1
                            + (y2 - y1)
                            * i
                            / steps;


            graphics.fill(
                    x - thickness / 2,
                    y - thickness / 2,
                    x + thickness / 2 + 1,
                    y + thickness / 2 + 1,
                    color
            );
        }
    }


    /*
     * =========================================
     * LOCALIZATION
     * =========================================
     */

    private Component tr(
            String key
    ) {

        return Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer."
                        + key
        );
    }


    /*
     * =========================================
     * FITTED TEXT
     *
     * Если русский текст длиннее доступного
     * пространства — уменьшаем его размер,
     * а не вываливаем за рамку.
     * =========================================
     */

    private void drawFittedText(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int maxRight,
            int color
    ) {

        String value =
                text.getString();


        int textWidth =
                font.width(value);


        int maxWidth =
                maxRight - x;


        if (
                textWidth <= maxWidth
        ) {

            graphics.drawString(
                    font,
                    text,
                    x,
                    y,
                    color,
                    false
            );

            return;
        }


        /*
         * Если строка не помещается,
         * показываем укороченную версию.
         *
         * Это особенно полезно для русского,
         * где некоторые статусы длиннее.
         */

        String shortened =
                value;


        while (
                shortened.length() > 3
                        && font.width(
                        shortened + "..."
                ) > maxWidth
        ) {

            shortened =
                    shortened.substring(
                            0,
                            shortened.length() - 1
                    );
        }


        graphics.drawString(
                font,
                Component.literal(
                        shortened + "..."
                ),
                x,
                y,
                color,
                false
        );
    }


    /*
     * =========================================
     * UTILITIES
     * =========================================
     */

    private float clamp(
            float value
    ) {

        return Math.max(
                0.0f,
                Math.min(
                        1.0f,
                        value
                )
        );
    }


    private float easeOutCubic(
            float value
    ) {

        value =
                clamp(value);


        return 1.0f
                - (float)
                Math.pow(
                        1.0f - value,
                        3
                );
    }


    private int getAverage() {

        return (
                analysis.copper()
                        + analysis.iron()
                        + analysis.coal()
                        + analysis.zinc()
        ) / 4;
    }


    private int lighten(
            int color
    ) {

        int r =
                Math.min(
                        255,
                        ((color >> 16) & 0xFF) + 35
                );

        int g =
                Math.min(
                        255,
                        ((color >> 8) & 0xFF) + 35
                );

        int b =
                Math.min(
                        255,
                        (color & 0xFF) + 35
                );


        return
                0xFF000000
                        | (r << 16)
                        | (g << 8)
                        | b;
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }


    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}