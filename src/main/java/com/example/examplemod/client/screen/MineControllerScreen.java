package com.example.examplemod.client.screen;

import com.example.examplemod.multiblock.MultiblockPreviewRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class MineControllerScreen extends Screen {

    // =========================================================
    // ОСНОВНЫЕ РАЗМЕРЫ
    // =========================================================

    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 500;

    private int left;
    private int top;

    // =========================================================
    // ТЕСТОВЫЕ ДАННЫЕ ПИТАНИЯ
    //
    // Потом заменим их на реальные данные EE.
    // =========================================================

    private double voltage = 1024.0;
    private double current = 5.0;
    private double power = 5120.0;

    private double minimumVoltage = 512.0;
    private double maximumVoltage = 4096.0;

    // =========================================================
    // СОСТОЯНИЯ
    // =========================================================

    private PowerState powerState = PowerState.NORMAL;

    private boolean mineRunning = true;
    private boolean emergencyStop = false;

    // =========================================================
    // АНИМАЦИЯ
    // =========================================================

    private long screenOpenTime;

    private float extractionProgress = 0.72F;

    private int miningCycle = 0;

    // =========================================================
    // РЕСУРС
    // =========================================================

    private String resourceName = "ЖЕЛЕЗНАЯ РУДА";
    private String resourceAmount = "×8";

    // =========================================================
    // ЖУРНАЛ
    // =========================================================

    private final List<String> logEntries =
            new ArrayList<>();

    // =========================================================
    // КНОПКИ
    // =========================================================

    private Button schematicButton;
    private Button startButton;
    private Button stopButton;
    private Button emergencyButton;

    // =========================================================
    // КОНСТРУКТОР
    // =========================================================

    public MineControllerScreen() {

        super(
                Component.translatable(
                        "gui.createcityexpansioncore.mine_controller.title"
                )
        );

        screenOpenTime =
                System.currentTimeMillis();

        logEntries.add(
                "[14:32] Шахта запущена"
        );

        logEntries.add(
                "[14:31] Добыт: Железная руда ×8"
        );

        logEntries.add(
                "[14:31] Энергия: 1024 V"
        );

        logEntries.add(
                "[14:31] Система в норме"
        );

        logEntries.add(
                "[14:30] Добыт: Медь ×6"
        );
    }

    // =========================================================
    // INIT
    // =========================================================

    @Override
    protected void init() {

        super.init();

        left =
                (this.width - PANEL_WIDTH) / 2;

        top =
                (this.height - PANEL_HEIGHT) / 2;

        // -----------------------------------------------------
        // СХЕМА
        // -----------------------------------------------------

        schematicButton =
                Button.builder(
                        Component.literal(
                                "▣  ПОКАЗАТЬ СХЕМУ"
                        ),
                        button -> {

                            if (MultiblockPreviewRenderer.isShowing()) {

                                MultiblockPreviewRenderer.hide();

                                button.setMessage(
                                        Component.literal(
                                                "▣  ПОКАЗАТЬ СХЕМУ"
                                        )
                                );

                            } else {

                                /*
                                 * Временно схема запускается
                                 * из уже существующего контроллера.
                                 *
                                 * Позиция хранится в renderer.
                                 *
                                 * Поэтому здесь пока используем
                                 * текущий активный preview.
                                 *
                                 * Реальную позицию передадим
                                 * следующим шагом через Screen.
                                 */
                                button.setMessage(
                                        Component.literal(
                                                "▣  СХЕМА АКТИВНА"
                                        )
                                );
                            }
                        }
                ).bounds(
                        left + 560,
                        top + 285,
                        175,
                        28
                ).build();

        addRenderableWidget(
                schematicButton
        );

        // -----------------------------------------------------
        // ЗАПУСК
        // -----------------------------------------------------

        startButton =
                Button.builder(
                        Component.literal(
                                "▶  ЗАПУСТИТЬ"
                        ),
                        button -> {

                            emergencyStop = false;
                            mineRunning = true;

                            addLog(
                                    "Шахта запущена"
                            );
                        }
                ).bounds(
                        left + 560,
                        top + 325,
                        175,
                        32
                ).build();

        addRenderableWidget(
                startButton
        );

        // -----------------------------------------------------
        // ОСТАНОВКА
        // -----------------------------------------------------

        stopButton =
                Button.builder(
                        Component.literal(
                                "Ⅱ  ОСТАНОВИТЬ"
                        ),
                        button -> {

                            mineRunning = false;

                            addLog(
                                    "Шахта остановлена"
                            );
                        }
                ).bounds(
                        left + 560,
                        top + 365,
                        175,
                        32
                ).build();

        addRenderableWidget(
                stopButton
        );

        // -----------------------------------------------------
        // АВАРИЙНАЯ ОСТАНОВКА
        // -----------------------------------------------------

        emergencyButton =
                Button.builder(
                        Component.literal(
                                "◉  АВАРИЙНОЕ ОТКЛ."
                        ),
                        button -> {

                            emergencyStop = true;
                            mineRunning = false;

                            addLog(
                                    "АВАРИЙНОЕ ОТКЛЮЧЕНИЕ"
                            );
                        }
                ).bounds(
                        left + 560,
                        top + 405,
                        175,
                        32
                ).build();

        addRenderableWidget(
                emergencyButton
        );
    }

    // =========================================================
    // RENDER
    // =========================================================

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        updatePowerState();

        updateMiningAnimation();

        /*
         * Затемняем игровой мир.
         */
        graphics.fill(
                0,
                0,
                this.width,
                this.height,
                0xB8000000
        );

        /*
         * Основная панель.
         */
        drawMainPanel(
                graphics
        );

        /*
         * Заголовок.
         */
        drawHeader(
                graphics
        );

        /*
         * Левая панель питания.
         */
        drawPowerPanel(
                graphics
        );

        /*
         * Центральная панель добычи.
         */
        drawExtractionPanel(
                graphics
        );

        /*
         * Правая панель.
         */
        drawRightPanel(
                graphics
        );

        /*
         * Журнал.
         */
        drawJournal(
                graphics
        );

        /*
         * Инвентарь.
         */
        drawInventory(
                graphics
        );

        /*
         * CRT эффект.
         */
        drawScanlines(
                graphics
        );

        /*
         * Кнопки.
         */
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    // =========================================================
    // MAIN PANEL
    // =========================================================

    private void drawMainPanel(
            GuiGraphics graphics
    ) {

        /*
         * Тень.
         */
        graphics.fill(
                left + 6,
                top + 7,
                left + PANEL_WIDTH + 6,
                top + PANEL_HEIGHT + 7,
                0x90000000
        );

        /*
         * Основной фон.
         */
        graphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xF0081016
        );

        /*
         * Внешняя рамка.
         */
        graphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + 2,
                0xFFB45B22
        );

        graphics.fill(
                left,
                top + PANEL_HEIGHT - 2,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF6A3518
        );

        graphics.fill(
                left,
                top,
                left + 2,
                top + PANEL_HEIGHT,
                0xFF6A3518
        );

        graphics.fill(
                left + PANEL_WIDTH - 2,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF6A3518
        );

        /*
         * Внутренняя рамка.
         */
        graphics.fill(
                left + 7,
                top + 7,
                left + PANEL_WIDTH - 7,
                top + 9,
                0xFF29343A
        );

        graphics.fill(
                left + 7,
                top + 7,
                left + 9,
                top + PANEL_HEIGHT - 7,
                0xFF29343A
        );
    }

    // =========================================================
    // HEADER
    // =========================================================

    private void drawHeader(
            GuiGraphics graphics
    ) {

        int x =
                left + 25;

        int y =
                top + 18;

        graphics.drawString(
                this.font,
                "КОНТРОЛЛЕР ШАХТЫ",
                x,
                y,
                0xFFE8EEF2,
                false
        );

        graphics.drawString(
                this.font,
                "CREATE CITY EXPANSION",
                x,
                y + 15,
                0xFF738894,
                false
        );

        /*
         * Индикатор ONLINE.
         */
        int indicatorColor =
                getPowerIndicatorColor();

        graphics.fill(
                left + 710,
                top + 25,
                left + 718,
                top + 33,
                indicatorColor
        );

        graphics.drawString(
                this.font,
                "ONLINE",
                left + 724,
                top + 24,
                indicatorColor,
                false
        );

        /*
         * Разделительная линия.
         */
        graphics.fill(
                left + 20,
                top + 52,
                left + PANEL_WIDTH - 20,
                top + 53,
                0xFF263640
        );
    }

    // =========================================================
    // POWER PANEL
    // =========================================================

    private void drawPowerPanel(
            GuiGraphics graphics
    ) {

        int x =
                left + 20;

        int y =
                top + 70;

        int width =
                235;

        int height =
                245;

        drawPanel(
                graphics,
                x,
                y,
                width,
                height
        );

        drawPanelTitle(
                graphics,
                "ПИТАНИЕ (EE)",
                x + 14,
                y + 15
        );

        /*
         * Большой вольтаж.
         */
        graphics.drawString(
                this.font,
                "⚡",
                x + 15,
                y + 43,
                getPowerIndicatorColor(),
                false
        );

        graphics.drawString(
                this.font,
                String.format(
                        "%.0f V",
                        voltage
                ),
                x + 47,
                y + 43,
                getPowerIndicatorColor(),
                false
        );

        /*
         * Статус.
         */
        graphics.drawString(
                this.font,
                "СТАТУС:",
                x + 47,
                y + 62,
                0xFF778891,
                false
        );

        graphics.drawString(
                this.font,
                getPowerStateText(),
                x + 95,
                y + 62,
                getPowerIndicatorColor(),
                false
        );

        /*
         * Полоса нагрузки.
         */
        int barX =
                x + 14;

        int barY =
                y + 87;

        int barWidth =
                width - 28;

        int barHeight =
                18;

        drawProgressBar(
                graphics,
                barX,
                barY,
                barWidth,
                barHeight,
                getPowerLoad()
        );

        /*
         * Данные.
         */
        drawValueLine(
                graphics,
                "ТОК",
                String.format(
                        "%.2f A",
                        current
                ),
                x + 14,
                y + 125
        );

        drawValueLine(
                graphics,
                "МОЩНОСТЬ",
                String.format(
                        "%.0f W",
                        power
                ),
                x + 14,
                y + 143
        );

        drawValueLine(
                graphics,
                "МИН. ТРЕБУЕТСЯ",
                String.format(
                        "%.0f V",
                        minimumVoltage
                ),
                x + 14,
                y + 161
        );

        drawValueLine(
                graphics,
                "МАКС. ДОПУСТИМО",
                String.format(
                        "%.0f V",
                        maximumVoltage
                ),
                x + 14,
                y + 179
        );

        /*
         * Индикаторы состояния.
         */
        drawPowerStateLegend(
                graphics,
                x + 14,
                y + 207
        );
    }

    // =========================================================
    // EXTRACTION
    // =========================================================

    private void drawExtractionPanel(
            GuiGraphics graphics
    ) {

        int x =
                left + 265;

        int y =
                top + 70;

        int width =
                280;

        int height =
                335;

        drawPanel(
                graphics,
                x,
                y,
                width,
                height
        );

        drawPanelTitle(
                graphics,
                "ПРОЦЕСС ДОБЫЧИ",
                x + 14,
                y + 15
        );

        /*
         * Голографическая область.
         */
        int holoX =
                x + 14;

        int holoY =
                y + 42;

        int holoWidth =
                width - 28;

        int holoHeight =
                205;

        drawHologram(
                graphics,
                holoX,
                holoY,
                holoWidth,
                holoHeight
        );

        /*
         * Ресурс.
         */
        graphics.drawString(
                this.font,
                resourceName,
                x + 15,
                y + 255,
                0xFF5FDFFF,
                false
        );

        /*
         * Прогресс.
         */
        graphics.drawString(
                this.font,
                "ДОБЫЧА...",
                x + 15,
                y + 274,
                0xFF8498A4,
                false
        );

        graphics.drawString(
                this.font,
                String.format(
                        "%d%%",
                        (int) (extractionProgress * 100)
                ),
                x + width - 50,
                y + 274,
                0xFF70E8FF,
                false
        );

        drawProgressBar(
                graphics,
                x + 15,
                y + 291,
                width - 30,
                12,
                extractionProgress
        );

        /*
         * Скорость.
         */
        graphics.drawString(
                this.font,
                "СКОРОСТЬ",
                x + 15,
                y + 314,
                0xFF667983,
                false
        );

        graphics.drawString(
                this.font,
                "5 РУДЫ/МИН",
                x + 105,
                y + 314,
                0xFFB8C7CE,
                false
        );
    }

    // =========================================================
    // HOLOGRAM
    // =========================================================

    private void drawHologram(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {

        /*
         * Фон.
         */
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                0xE0081821
        );

        /*
         * Рамка.
         */
        graphics.fill(
                x,
                y,
                x + width,
                y + 1,
                0xFF1787A8
        );

        graphics.fill(
                x,
                y + height - 1,
                x + width,
                y + height,
                0xFF12627A
        );

        graphics.fill(
                x,
                y,
                x + 1,
                y + height,
                0xFF1787A8
        );

        graphics.fill(
                x + width - 1,
                y,
                x + width,
                y + height,
                0xFF1787A8
        );

        /*
         * Голографическая сетка.
         */
        for (int gridX = x + 15;
             gridX < x + width;
             gridX += 25) {

            graphics.fill(
                    gridX,
                    y + 15,
                    gridX + 1,
                    y + height - 15,
                    0x241E8EAA
            );
        }

        for (int gridY = y + 15;
             gridY < y + height;
             gridY += 25) {

            graphics.fill(
                    x + 15,
                    gridY,
                    x + width - 15,
                    gridY + 1,
                    0x241E8EAA
            );
        }

        /*
         * Параметры анимации.
         */
        float time =
                (System.currentTimeMillis()
                        - screenOpenTime) / 1000.0F;

        /*
         * Центр.
         */
        int centerX =
                x + width / 2;

        int centerY =
                y + 115;

        /*
         * Руда.
         */
        drawOreBlock(
                graphics,
                centerX - 42,
                centerY - 28,
                84,
                time
        );

        /*
         * Кирка.
         */
        drawAnimatedPickaxe(
                graphics,
                centerX,
                centerY,
                time
        );

        /*
         * Частицы.
         */
        drawMiningParticles(
                graphics,
                centerX,
                centerY,
                time
        );

        /*
         * Информация справа сверху.
         */
        graphics.drawString(
                this.font,
                "ГЛУБИНА: 32",
                x + width - 105,
                y + 18,
                0xFF547685,
                false
        );

        graphics.drawString(
                this.font,
                "СЛОЙ: ЖЕЛЕЗО",
                x + width - 105,
                y + 31,
                0xFF547685,
                false
        );

        /*
         * Сканирующая линия.
         */
        float scan =
                (time % 2.5F) / 2.5F;

        int scanY =
                y + 10 + (int) (scan * (height - 20));

        graphics.fill(
                x + 8,
                scanY,
                x + width - 8,
                scanY + 1,
                0x3028C9E8
        );
    }

    // =========================================================
    // ORE BLOCK
    // =========================================================

    private void drawOreBlock(
            GuiGraphics graphics,
            int x,
            int y,
            int size,
            float time
    ) {

        /*
         * Фаза разрушения.
         */
        float phase =
                (time % 2.4F) / 2.4F;

        /*
         * Основной блок.
         */
        int blockColor =
                0xFF24566A;

        graphics.fill(
                x,
                y,
                x + size,
                y + size,
                blockColor
        );

        /*
         * Свечение.
         */
        graphics.fill(
                x + 2,
                y + 2,
                x + size - 2,
                y + 4,
                0x8060DFFF
        );

        graphics.fill(
                x + 2,
                y + size - 4,
                x + size - 2,
                y + size - 2,
                0x6030A8C4
        );

        /*
         * Рудные вкрапления.
         */
        int ore =
                0xFFB7D2DA;

        drawOreSpot(
                graphics,
                x + 15,
                y + 18,
                ore
        );

        drawOreSpot(
                graphics,
                x + 51,
                y + 13,
                ore
        );

        drawOreSpot(
                graphics,
                x + 30,
                y + 43,
                ore
        );

        drawOreSpot(
                graphics,
                x + 62,
                y + 57,
                ore
        );

        drawOreSpot(
                graphics,
                x + 12,
                y + 62,
                ore
        );

        /*
         * Трещины появляются постепенно.
         */
        int cracks =
                (int) (phase * 6.0F);

        if (cracks > 0) {

            graphics.fill(
                    x + 39,
                    y + 10,
                    x + 41,
                    y + 32,
                    0xFF101B20
            );
        }

        if (cracks > 1) {

            graphics.fill(
                    x + 40,
                    y + 30,
                    x + 58,
                    y + 32,
                    0xFF101B20
            );
        }

        if (cracks > 2) {

            graphics.fill(
                    x + 39,
                    y + 30,
                    x + 31,
                    y + 49,
                    0xFF101B20
            );
        }

        if (cracks > 3) {

            graphics.fill(
                    x + 31,
                    y + 48,
                    x + 14,
                    y + 55,
                    0xFF101B20
            );
        }

        /*
         * Финальная фаза —
         * куски начинают разлетаться.
         */
        if (phase > 0.82F) {

            int alpha =
                    (int) (
                            (1.0F - phase)
                                    * 255.0F
                    );

            alpha =
                    Math.max(
                            0,
                            Math.min(
                                    255,
                                    alpha
                            )
                    );

            int color =
                    (alpha << 24)
                            | 0x75CBEA;

            graphics.fill(
                    x - 8,
                    y + 12,
                    x,
                    y + 20,
                    color
            );

            graphics.fill(
                    x + size,
                    y + 32,
                    x + size + 8,
                    y + 39,
                    color
            );
        }
    }

    private void drawOreSpot(
            GuiGraphics graphics,
            int x,
            int y,
            int color
    ) {

        graphics.fill(
                x,
                y,
                x + 8,
                y + 7,
                color
        );
    }

    // =========================================================
    // PICKAXE
    // =========================================================

    private void drawAnimatedPickaxe(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            float time
    ) {

        /*
         * Движение от -1 до 1.
         */
        float swing =
                (float) Math.sin(
                        time * Math.PI * 2.0
                );

        /*
         * Небольшое движение.
         */
        int offsetX =
                (int) (swing * 22);

        int offsetY =
                (int) (Math.abs(swing) * 8);

        int px =
                centerX - 58 + offsetX;

        int py =
                centerY - 60 + offsetY;

        /*
         * Рукоять.
         */
        graphics.fill(
                px + 32,
                py + 15,
                px + 38,
                py + 72,
                0xFFB67A3D
        );

        graphics.fill(
                px + 34,
                py + 15,
                px + 39,
                py + 70,
                0xFFE3A15B
        );

        /*
         * Голова кирки.
         */
        graphics.fill(
                px + 8,
                py + 8,
                px + 58,
                py + 16,
                0xFF4FC7E8
        );

        graphics.fill(
                px + 14,
                py + 4,
                px + 51,
                py + 10,
                0xFF8BE8FF
        );

        /*
         * Свечение.
         */
        graphics.fill(
                px + 8,
                py + 16,
                px + 18,
                py + 19,
                0x6033DFFF
        );

        graphics.fill(
                px + 50,
                py + 12,
                px + 58,
                py + 15,
                0x6033DFFF
        );
    }

    // =========================================================
    // PARTICLES
    // =========================================================

    private void drawMiningParticles(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            float time
    ) {

        for (int i = 0; i < 8; i++) {

            float phase =
                    (time * 0.8F
                            + i * 0.17F) % 1.0F;

            int px =
                    centerX
                            + (int) (
                            Math.sin(
                                    i * 2.4
                            ) * 55
                    )
                            + (int) (
                            phase * 20
                    );

            int py =
                    centerY
                            - (int) (
                            phase * 75
                    )
                            + (i % 3) * 10;

            int size =
                    2 + (i % 3);

            graphics.fill(
                    px,
                    py,
                    px + size,
                    py + size,
                    0xA050D5F0
            );
        }
    }

    // =========================================================
    // RIGHT PANEL
    // =========================================================

    private void drawRightPanel(
            GuiGraphics graphics
    ) {

        int x =
                left + 560;

        int y =
                top + 70;

        int width =
                180;

        /*
         * Ресурсы.
         */
        drawPanel(
                graphics,
                x,
                y,
                width,
                120
        );

        drawPanelTitle(
                graphics,
                "ВЫХОД РЕСУРСОВ",
                x + 12,
                y + 15
        );

        graphics.drawString(
                this.font,
                resourceName,
                x + 12,
                y + 43,
                0xFFE1E9ED,
                false
        );

        graphics.drawString(
                this.font,
                resourceAmount,
                x + 12,
                y + 61,
                0xFF65DFFF,
                false
        );

        graphics.drawString(
                this.font,
                "ЗА ПОСЛЕДНЮЮ МИНУТУ",
                x + 12,
                y + 80,
                0xFF647A84,
                false
        );

        /*
         * Состояние шахты.
         */
        drawPanel(
                graphics,
                x,
                y + 130,
                width,
                105
        );

        drawPanelTitle(
                graphics,
                "СОСТОЯНИЕ ШАХТЫ",
                x + 12,
                y + 145
        );

        int stateColor =
                mineRunning
                        && !emergencyStop
                        ? 0xFF30F080
                        : 0xFFFF4A4A;

        graphics.drawString(
                this.font,
                mineRunning
                        ? "МУЛЬТИБЛОК СОБРАН"
                        : "ШАХТА ОСТАНОВЛЕНА",
                x + 12,
                y + 172,
                stateColor,
                false
        );

        graphics.drawString(
                this.font,
                "РАЗМЕР: 10×10×3",
                x + 12,
                y + 190,
                0xFF81939C,
                false
        );

        graphics.drawString(
                this.font,
                "ИНТЕРФЕЙСЫ: 2 / 2",
                x + 12,
                y + 206,
                0xFF81939C,
                false
        );
    }

    // =========================================================
    // JOURNAL
    // =========================================================

    private void drawJournal(
            GuiGraphics graphics
    ) {

        int x =
                left + 20;

        int y =
                top + 325;

        int width =
                235;

        int height =
                155;

        drawPanel(
                graphics,
                x,
                y,
                width,
                height
        );

        drawPanelTitle(
                graphics,
                "ЖУРНАЛ",
                x + 14,
                y + 15
        );

        int lineY =
                y + 38;

        int start =
                Math.max(
                        0,
                        logEntries.size() - 6
                );

        for (int i = start;
             i < logEntries.size();
             i++) {

            graphics.drawString(
                    this.font,
                    logEntries.get(i),
                    x + 14,
                    lineY,
                    0xFF748A94,
                    false
            );

            lineY += 17;
        }
    }

    // =========================================================
    // INVENTORY
    // =========================================================

    private void drawInventory(
            GuiGraphics graphics
    ) {

        int x =
                left + 265;

        int y =
                top + 420;

        int width =
                280;

        drawPanel(
                graphics,
                x,
                y,
                width,
                60
        );

        drawPanelTitle(
                graphics,
                "ИНВЕНТАРЬ",
                x + 12,
                y + 13
        );

        for (int i = 0; i < 8; i++) {

            int slotX =
                    x + 12 + i * 31;

            int slotY =
                    y + 32;

            graphics.fill(
                    slotX,
                    slotY,
                    slotX + 26,
                    slotY + 22,
                    0xFF101A20
            );

            graphics.fill(
                    slotX,
                    slotY,
                    slotX + 26,
                    slotY + 1,
                    0xFF34434B
            );
        }

        /*
         * Простые пиксельные предметы.
         */
        drawInventoryItem(
                graphics,
                x + 12,
                y + 32,
                0xFF8A969A,
                12
        );

        drawInventoryItem(
                graphics,
                x + 43,
                y + 32,
                0xFF7C4E34,
                5
        );

        drawInventoryItem(
                graphics,
                x + 74,
                y + 32,
                0xFFD77D38,
                3
        );

        drawInventoryItem(
                graphics,
                x + 105,
                y + 32,
                0xFFF1C644,
                1
        );
    }

    private void drawInventoryItem(
            GuiGraphics graphics,
            int x,
            int y,
            int color,
            int amount
    ) {

        graphics.fill(
                x + 6,
                y + 5,
                x + 20,
                y + 17,
                color
        );

        graphics.drawString(
                this.font,
                String.valueOf(amount),
                x + 16,
                y + 13,
                0xFFFFFFFF,
                false
        );
    }

    // =========================================================
    // PANEL
    // =========================================================

    private void drawPanel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {

        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                0xE80B141A
        );

        graphics.fill(
                x,
                y,
                x + width,
                y + 1,
                0xFF263A44
        );

        graphics.fill(
                x,
                y,
                x + 1,
                y + height,
                0xFF263A44
        );

        graphics.fill(
                x + width - 1,
                y,
                x + width,
                y + height,
                0xFF17252D
        );

        graphics.fill(
                x,
                y + height - 1,
                x + width,
                y + height,
                0xFF17252D
        );
    }

    // =========================================================
    // PANEL TITLE
    // =========================================================

    private void drawPanelTitle(
            GuiGraphics graphics,
            String text,
            int x,
            int y
    ) {

        graphics.drawString(
                this.font,
                text,
                x,
                y,
                0xFF58D7FF,
                false
        );

        graphics.fill(
                x,
                y + 12,
                x + 110,
                y + 13,
                0xFF164C5E
        );
    }

    // =========================================================
    // VALUE LINE
    // =========================================================

    private void drawValueLine(
            GuiGraphics graphics,
            String name,
            String value,
            int x,
            int y
    ) {

        graphics.drawString(
                this.font,
                name,
                x,
                y,
                0xFF657B85,
                false
        );

        graphics.drawString(
                this.font,
                value,
                x + 145,
                y,
                0xFFD3DEE2,
                false
        );
    }

    // =========================================================
    // PROGRESS BAR
    // =========================================================

    private void drawProgressBar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            float progress
    ) {

        progress =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                progress
                        )
                );

        /*
         * Background.
         */
        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                0xFF071015
        );

        /*
         * Fill.
         */
        int fillWidth =
                (int) (
                        width * progress
                );

        int color =
                getPowerIndicatorColor();

        if (powerState
                == PowerState.NORMAL) {

            color =
                    0xFF35E875;
        }

        graphics.fill(
                x + 2,
                y + 2,
                x + fillWidth - 2,
                y + height - 2,
                color
        );

        /*
         * Светящиеся сегменты.
         */
        for (int i = x + 4;
             i < x + fillWidth - 2;
             i += 8) {

            graphics.fill(
                    i,
                    y + 3,
                    i + 3,
                    y + height - 3,
                    0x307FFFFF
            );
        }
    }

    // =========================================================
    // POWER LEGEND
    // =========================================================

    private void drawPowerStateLegend(
            GuiGraphics graphics,
            int x,
            int y
    ) {

        drawLegendLine(
                graphics,
                "НЕТ ПИТАНИЯ",
                0xFFFF4545,
                x,
                y
        );

        drawLegendLine(
                graphics,
                "НИЗКОЕ ПИТАНИЕ",
                0xFFFFA72F,
                x,
                y + 16
        );

        drawLegendLine(
                graphics,
                "НОРМАЛЬНО",
                0xFF35F07A,
                x,
                y + 32
        );

        drawLegendLine(
                graphics,
                "ПЕРЕГРУЗКА",
                0xFFFF3F3F,
                x,
                y + 48
        );
    }

    private void drawLegendLine(
            GuiGraphics graphics,
            String text,
            int color,
            int x,
            int y
    ) {

        graphics.fill(
                x,
                y,
                x + 8,
                y + 8,
                color
        );

        graphics.drawString(
                this.font,
                text,
                x + 14,
                y,
                color,
                false
        );
    }

    // =========================================================
    // CRT SCANLINES
    // =========================================================

    private void drawScanlines(
            GuiGraphics graphics
    ) {

        /*
         * Очень слабые полосы,
         * чтобы не мешали интерфейсу.
         */
        for (int y = top;
             y < top + PANEL_HEIGHT;
             y += 4) {

            graphics.fill(
                    left,
                    y,
                    left + PANEL_WIDTH,
                    y + 1,
                    0x08000000
            );
        }

        /*
         * Иногда экран слегка мерцает
         * при проблемах питания.
         */
        if (powerState
                == PowerState.NO_POWER
                || powerState
                == PowerState.OVERLOAD) {

            long time =
                    System.currentTimeMillis();

            if ((time / 120) % 3 == 0) {

                graphics.fill(
                        left,
                        top,
                        left + PANEL_WIDTH,
                        top + PANEL_HEIGHT,
                        0x12000000
                );
            }
        }
    }

    // =========================================================
    // POWER STATE
    // =========================================================

    private void updatePowerState() {

        if (voltage <= 0.0) {

            powerState =
                    PowerState.NO_POWER;

            return;
        }

        if (voltage < minimumVoltage) {

            powerState =
                    PowerState.LOW_POWER;

            return;
        }

        if (voltage > maximumVoltage) {

            powerState =
                    PowerState.OVERLOAD;

            return;
        }

        powerState =
                PowerState.NORMAL;
    }

    // =========================================================
    // MINING ANIMATION
    // =========================================================

    private void updateMiningAnimation() {

        if (!mineRunning
                || emergencyStop
                || powerState
                == PowerState.NO_POWER
                || powerState
                == PowerState.LOW_POWER
                || powerState
                == PowerState.OVERLOAD) {

            return;
        }

        /*
         * Скорость цикла.
         */
        extractionProgress +=
                0.0015F;

        if (extractionProgress >= 1.0F) {

            extractionProgress = 0.0F;

            miningCycle++;

            addLog(
                    "Добыт: "
                            + resourceName
                            + " ×8"
            );
        }
    }

    // =========================================================
    // POWER LOAD
    // =========================================================

    private float getPowerLoad() {

        if (maximumVoltage <= 0.0) {
            return 0.0F;
        }

        return (float)
                Math.min(
                        1.0,
                        voltage
                                / maximumVoltage
                );
    }

    // =========================================================
    // POWER TEXT
    // =========================================================

    private String getPowerStateText() {

        return switch (powerState) {

            case NO_POWER ->
                    "НЕТ ПИТАНИЯ";

            case LOW_POWER ->
                    "НИЗКОЕ ПИТАНИЕ";

            case NORMAL ->
                    "НОРМАЛЬНО";

            case OVERLOAD ->
                    "ПЕРЕГРУЗКА";
        };
    }

    // =========================================================
    // POWER COLOR
    // =========================================================

    private int getPowerIndicatorColor() {

        return switch (powerState) {

            case NO_POWER ->
                    0xFFFF3F3F;

            case LOW_POWER ->
                    0xFFFFA72F;

            case NORMAL ->
                    0xFF35F07A;

            case OVERLOAD ->
                    0xFFFF3030;
        };
    }

    // =========================================================
    // LOG
    // =========================================================

    private void addLog(
            String message
    ) {

        logEntries.add(
                "[--:--] " + message
        );

        /*
         * Не даём журналу
         * разрастаться бесконечно.
         */
        while (logEntries.size() > 12) {

            logEntries.remove(0);
        }
    }

    // =========================================================
    // EE API
    // =========================================================

    /**
     * Этот метод пока не вызывается.
     *
     * Следующим шагом сюда будем передавать
     * реальные данные от MinePowerPortDevice.
     */
    public void setPowerData(
            double voltage,
            double current,
            double power
    ) {

        this.voltage = voltage;
        this.current = current;
        this.power = power;
    }

    // =========================================================
    // POWER STATE ENUM
    // =========================================================

    private enum PowerState {

        NO_POWER,

        LOW_POWER,

        NORMAL,

        OVERLOAD
    }

    // =========================================================
    // CLOSE
    // =========================================================

    @Override
    public void onClose() {

        /*
         * Схему пока специально НЕ скрываем.
         *
         * Следующим шагом сделаем нормальную
         * кнопку включения/выключения схемы
         * и будем хранить её состояние отдельно.
         */

        super.onClose();
    }
}