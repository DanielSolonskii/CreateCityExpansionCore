package com.example.examplemod.client.screen;

import com.example.examplemod.client.sound.SoundHelper;
import com.example.examplemod.sound.ModSounds;

import com.example.examplemod.geology.GeologyDepositSnapshot;
import com.example.examplemod.network.GeologyAnalysisPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MagneticAnalyzerScreen extends Screen {

    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 390;
    private static final int PANEL = 0xFF141718;
    private static final int PANEL_DARK = 0xFF0C0F10;
    private static final int BORDER = 0xFF343B3C;
    private static final int GREEN = 0xFF9BE564;
    private static final int GOLD = 0xFFD6A84F;
    private static final int RED = 0xFFE45C5C;
    private static final int TEXT = 0xFFE0E5E2;
    private static final int TEXT_DIM = 0xFF858F8B;
    private static final long SCAN_DURATION_MS = 1_800L;
    private static final int VISIBLE_ROWS = 6;

    private final GeologyAnalysisPayload analysis;
    private long openedAt;
    private int selectedIndex;
    private int scrollOffset;
    private int listLeft;
    private int listTop;
    private int listWidth;

    public MagneticAnalyzerScreen(GeologyAnalysisPayload analysis) {
        super(Component.translatable(
                "gui.createcityexpansioncore.magnetic_analyzer.title"
        ));
        this.analysis = analysis;
        this.openedAt = System.currentTimeMillis();
    }

    public static void open(GeologyAnalysisPayload payload) {
        /*
         * Анализ геологии в этом проекте мгновенный: сервер считает
         * регион и сразу присылает готовый снимок. Поэтому scan_loop
         * НЕ используется — нечего поддерживать, и выдумывать
         * искусственную длительность сканирования значило бы
         * враньё игроку.
         *
         * Звучим только реальные события: открытие прибора,
         * старт сканирования и результат.
         */
        SoundHelper.playUi(ModSounds.ANALYZER_OPEN.get());
        SoundHelper.playUi(ModSounds.ANALYZER_SCAN_START.get());

        Minecraft.getInstance().setScreen(new MagneticAnalyzerScreen(payload));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        selectedIndex = 0;
        scrollOffset = 0;

        /*
         * Результат сканирования озвучивается один раз при открытии.
         * Пинг намеренно не используется: анализ мгновенный, и
         * имитировать его длительность значило бы вводить
         * искусственную задержку ради эффекта.
         */
        if (analysis.region().deposits().isEmpty()) {
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
        graphics.fill(0, 0, width, height, 0xA8070909);

        int panelWidth = Math.min(PANEL_WIDTH, width - 24);
        int panelHeight = Math.min(PANEL_HEIGHT, height - 24);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        graphics.fill(left + 7, top + 7, right + 7, bottom + 7, 0x82000000);
        graphics.fill(left, top, right, bottom, PANEL);
        drawFrame(graphics, left, top, right, bottom);
        drawHeader(graphics, left, top, right);

        int contentTop = top + 82;
        int contentBottom = bottom - 42;
        listLeft = left + 16;
        listTop = contentTop;
        listWidth = Math.min(220, panelWidth / 3 + 14);
        int detailsLeft = listLeft + listWidth + 12;
        int detailsWidth = right - 16 - detailsLeft;

        drawDepositList(
                graphics,
                listLeft,
                listTop,
                listWidth,
                contentBottom - contentTop,
                mouseX,
                mouseY
        );
        drawDepositDetails(
                graphics,
                detailsLeft,
                contentTop,
                detailsWidth,
                contentBottom - contentTop
        );
        drawFooter(graphics, left, right, bottom);

        /*
         * Виджеты рисуются ДО фона.
         *
         * super.render() внутри вызывает renderBackground(), который
         * применяет blur. Если вызвать его в конце, размытие ляжет на
         * уже нарисованные панели, а виджеты, отрисованные после blur,
         * останутся резкими. Поэтому сначала виджеты, потом фон —
         * весь готовый слой обрабатывается одинаково.
         */
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    private void drawFrame(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.hLine(left, right - 1, top, GOLD);
        graphics.hLine(left, right - 1, bottom - 1, BORDER);
        graphics.vLine(left, top, bottom - 1, BORDER);
        graphics.vLine(right - 1, top, bottom - 1, BORDER);
        graphics.hLine(left + 5, right - 6, top + 5, BORDER);
        graphics.hLine(left + 5, right - 6, bottom - 6, BORDER);
    }

    private void drawHeader(
            GuiGraphics graphics,
            int left,
            int top,
            int right
    ) {
        graphics.fill(left + 6, top + 6, right - 6, top + 70, PANEL_DARK);
        graphics.fill(left + 17, top + 18, left + 23, top + 24, GOLD);
        graphics.drawString(font, title, left + 34, top + 13, GOLD, false);
        graphics.drawString(
                font,
                "GEOLOGICAL SURVEY UNIT",
                left + 34,
                top + 29,
                TEXT_DIM,
                false
        );

        String region = "REGION " + analysis.region().regionX()
                + ", " + analysis.region().regionZ();
        graphics.drawString(font, region, left + 17, top + 48, TEXT, false);
        String id = analysis.region().regionId().toString();
        drawFitted(graphics, id, left + 145, top + 48, right - 125, TEXT_DIM);

        String status = scanComplete() ? "ANALYSIS COMPLETE" : "SCANNING...";
        int color = scanComplete() ? GREEN : GOLD;
        graphics.drawString(
                font,
                status,
                right - 17 - font.width(status),
                top + 18,
                color,
                false
        );
    }

    private void drawDepositList(
            GuiGraphics graphics,
            int x,
            int y,
            int panelWidth,
            int panelHeight,
            int mouseX,
            int mouseY
    ) {
        drawPanel(graphics, x, y, panelWidth, panelHeight);
        graphics.drawString(font, "DEPOSITS", x + 11, y + 10, GREEN, false);

        if (!scanComplete()) {
            graphics.drawString(font, "Reading server data...", x + 11, y + 38, TEXT_DIM, false);
            return;
        }

        List<GeologyDepositSnapshot> deposits = analysis.region().deposits();
        if (deposits.isEmpty()) {
            graphics.drawString(font, "NO DEPOSITS", x + 11, y + 38, RED, false);
            return;
        }

        int visible = Math.min(VISIBLE_ROWS, deposits.size() - scrollOffset);
        for (int row = 0; row < visible; row++) {
            int index = scrollOffset + row;
            int rowY = y + 29 + row * 37;
            GeologyDepositSnapshot deposit = deposits.get(index);
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= x + 7 && mouseX < x + panelWidth - 7
                    && mouseY >= rowY && mouseY < rowY + 33;
            if (selected || hovered) {
                graphics.fill(
                        x + 7,
                        rowY,
                        x + panelWidth - 7,
                        rowY + 33,
                        selected ? 0xFF1E3B2A : 0xFF182329
                );
            }
            graphics.drawString(
                    font,
                    GeologyUiFormat.resource(deposit.resourceId()),
                    x + 12,
                    rowY + 5,
                    selected ? GREEN : TEXT,
                    false
            );
            String summary = deposit.stage() + "  "
                    + GeologyUiFormat.percent(deposit.remainingRatio());
            graphics.drawString(font, summary, x + 12, rowY + 19, TEXT_DIM, false);
        }
    }

    private void drawDepositDetails(
            GuiGraphics graphics,
            int x,
            int y,
            int panelWidth,
            int panelHeight
    ) {
        drawPanel(graphics, x, y, panelWidth, panelHeight);
        graphics.drawString(font, "DEPOSIT ANALYSIS", x + 12, y + 10, GREEN, false);

        if (!scanComplete() || analysis.region().deposits().isEmpty()) {
            graphics.drawString(
                    font,
                    scanComplete() ? "No geological deposits detected." : "Awaiting scan completion...",
                    x + 12,
                    y + 40,
                    TEXT_DIM,
                    false
            );
            return;
        }

        GeologyDepositSnapshot deposit = analysis.region().deposits().get(
                Math.min(selectedIndex, analysis.region().deposits().size() - 1)
        );
        graphics.drawString(
                font,
                GeologyUiFormat.resource(deposit.resourceId()),
                x + 12,
                y + 34,
                GOLD,
                false
        );

        int barX = x + 12;
        int barY = y + 55;
        int barWidth = panelWidth - 24;
        drawProgress(graphics, barX, barY, barWidth, 13, deposit.remainingRatio());
        graphics.drawCenteredString(
                font,
                GeologyUiFormat.percent(deposit.remainingRatio()),
                barX + barWidth / 2,
                barY + 3,
                TEXT
        );

        int lineY = y + 80;
        line(graphics, "Remaining", GeologyUiFormat.count(deposit.remainingReserve()), x, lineY);
        line(graphics, "Initial", GeologyUiFormat.count(deposit.initialReserve()), x, lineY + 20);
        line(graphics, "Stage", deposit.stage().name(), x, lineY + 40);
        line(graphics, "Grade", GeologyUiFormat.decimal(deposit.grade()), x, lineY + 60);
        line(graphics, "Depth", deposit.depth() + " m", x, lineY + 80);
        line(graphics, "Hardness", GeologyUiFormat.decimal(deposit.hardness()), x, lineY + 100);
        line(
                graphics,
                "Sustainable rate",
                GeologyUiFormat.count(Math.round(deposit.sustainableExtractionRate())) + "/min",
                x,
                lineY + 120
        );
    }

    private void drawFooter(
            GuiGraphics graphics,
            int left,
            int right,
            int bottom
    ) {
        int y = bottom - 34;
        graphics.fill(left + 6, y, right - 6, bottom - 6, PANEL_DARK);
        String position = "SCAN " + analysis.position().getX() + ", "
                + analysis.position().getY() + ", " + analysis.position().getZ();
        graphics.drawString(font, position, left + 16, y + 10, TEXT_DIM, false);
        String charge = analysis.remainingUses() + " USES REMAINING";
        graphics.drawString(
                font,
                charge,
                right - 16 - font.width(charge),
                y + 10,
                TEXT_DIM,
                false
        );
    }

    private void drawPanel(
            GuiGraphics graphics,
            int x,
            int y,
            int panelWidth,
            int panelHeight
    ) {
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_DARK);
        graphics.hLine(x, x + panelWidth - 1, y, BORDER);
        graphics.hLine(x, x + panelWidth - 1, y + panelHeight - 1, BORDER);
        graphics.vLine(x, y, y + panelHeight - 1, BORDER);
        graphics.vLine(x + panelWidth - 1, y, y + panelHeight - 1, BORDER);
    }

    private void line(
            GuiGraphics graphics,
            String label,
            String value,
            int panelX,
            int y
    ) {
        graphics.drawString(font, label, panelX + 12, y, TEXT_DIM, false);
        graphics.drawString(font, value, panelX + 145, y, TEXT, false);
    }

    private void drawProgress(
            GuiGraphics graphics,
            int x,
            int y,
            int barWidth,
            int barHeight,
            double ratio
    ) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF071015);
        int fill = (int) Math.round((barWidth - 2) * clamped);
        if (fill > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fill, y + barHeight - 1, GREEN);
        }
    }

    private void drawFitted(
            GuiGraphics graphics,
            String value,
            int x,
            int y,
            int right,
            int color
    ) {
        String fitted = value;
        while (!fitted.isEmpty() && font.width(fitted) > right - x) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (!fitted.equals(value) && fitted.length() > 3) {
            fitted = fitted.substring(0, fitted.length() - 3) + "...";
        }
        graphics.drawString(font, fitted, x, y, color, false);
    }

    private boolean scanComplete() {
        return System.currentTimeMillis() - openedAt >= SCAN_DURATION_MS;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scanComplete()) {
            List<GeologyDepositSnapshot> deposits = analysis.region().deposits();
            int row = ((int) mouseY - (listTop + 29)) / 37;
            int index = scrollOffset + row;
            if (mouseX >= listLeft + 7 && mouseX < listLeft + listWidth - 7
                    && row >= 0 && row < VISIBLE_ROWS
                    && index >= 0 && index < deposits.size()) {
                selectedIndex = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int maxOffset = Math.max(0, analysis.region().deposits().size() - VISIBLE_ROWS);
        if (scrollY < 0) {
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        } else if (scrollY > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
