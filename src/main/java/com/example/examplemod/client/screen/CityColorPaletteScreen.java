package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityColors;
import com.example.examplemod.network.SetCityColorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Палитра собственного цвета города.
 *
 * <p><b>Пресеты, а не RGB-пикер.</b> Произвольный цвет почти всегда
 * оказывается нечитаемым на карте: чёрный сливается с рельефом,
 * белый выжигает участок, а чистый насыщенный зелёный или фиолетовый
 * выглядит как ошибка. Пресеты — это список цветов, которые заведомо
 * читаются, поэтому выбор всегда удачный, а сервер всё равно проверяет,
 * что прислан цвет из палитры.
 *
 * <p><b>Геометрия панели выводится из содержимого.</b> Раньше высота
 * панели и положение кнопки были заданы двумя независимыми числами, и
 * кнопка «Закрыть» оказывалась ниже нижнего края панели — прямо поверх
 * размытого родительского экрана. Родительский интерфейс просвечивал
 * сквозь окно выбора цвета. Теперь обе величины считаются из одного
 * списка раскладки, поэтому элемент не может выйти за панель.
 *
 * <p>Выбранный цвет обведён золотой рамкой, поэтому текущий видно без
 * сравнения «по памяти».
 */
public class CityColorPaletteScreen extends ModalOverlayScreen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_PADDING = 20;
    private static final int TITLE_BAR = 50;
    private static final int DESCRIPTION_Y = 64;
    private static final int SWATCH_TOP = 84;
    private static final int SWATCH = 26;
    private static final int SWATCH_GAP = 6;
    private static final int BUTTON_HEIGHT = 26;
    private static final int BUTTON_GAP = 14;
    private static final int BUTTON_BOTTOM_PADDING = 16;

    private final Screen parent;
    private final BlockPos pylonPos;
    private final int currentColor;

    /** Прямоугольник пресета. Один список на отрисовку и на клик — иначе они разъезжаются. */
    private record Swatch(int x, int y, int width, int height, int color) {
    }

    /** Панель целиком. Высоты считаются из содержимого, а не задаются константой. */
    private record Layout(int left, int top, int width, int height,
                          List<Swatch> swatches,
                          int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
    }

    public CityColorPaletteScreen(
            Screen parent,
            BlockPos pylonPos,
            int currentColor
    ) {
        super(parent, Component.translatable("gui.createcityexpansioncore.city.color.title"));
        this.parent = parent;
        this.pylonPos = pylonPos;
        this.currentColor = CityColors.normalize(currentColor);
    }

    /**
     * Единственный источник геометрии.
     *
     * <p>Высота панели — это последняя строка содержимого плюс отступ, а
     * кнопка стоит над этим отступом и внутри панели по определению.
     * Добавить в палитру ещё один ряд пресетов нельзя не заметить: он
     * увеличит панель, а не вылезет из неё.
     */
    private Layout layout() {
        int usable = PANEL_WIDTH - PANEL_PADDING * 2;
        int perRow = Math.max(1, (usable + SWATCH_GAP) / (SWATCH + SWATCH_GAP));
        int rows = Math.max(1, (CityColors.PALETTE.size() + perRow - 1) / perRow);

        List<Swatch> swatches = new java.util.ArrayList<>(CityColors.PALETTE.size());
        for (int index = 0; index < CityColors.PALETTE.size(); index++) {
            int column = index % perRow;
            int row = index / perRow;
            swatches.add(new Swatch(
                    PANEL_PADDING + column * (SWATCH + SWATCH_GAP),
                    SWATCH_TOP + row * (SWATCH + SWATCH_GAP),
                    SWATCH, SWATCH,
                    CityColors.PALETTE.get(index)
            ));
        }

        int swatchBottom = SWATCH_TOP + rows * (SWATCH + SWATCH_GAP) - SWATCH_GAP;
        int buttonY = swatchBottom + BUTTON_GAP;
        int panelHeight = buttonY + BUTTON_HEIGHT + BUTTON_BOTTOM_PADDING;
        int buttonWidth = 160;

        return new Layout(
                (this.width - PANEL_WIDTH) / 2,
                (this.height - panelHeight) / 2,
                PANEL_WIDTH,
                panelHeight,
                swatches,
                PANEL_PADDING + (PANEL_WIDTH - PANEL_PADDING * 2 - buttonWidth) / 2,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT
        );
    }

    @Override
    protected void init() {
        super.init();

        Layout layout = layout();

        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.createcityexpansioncore.common.close"),
                        button -> this.minecraft.setScreen(this.parent)
                ).bounds(
                        layout.left() + layout.buttonX(),
                        layout.top() + layout.buttonY(),
                        layout.buttonWidth(),
                        layout.buttonHeight()
                ).build()
        );
    }

    /**
     * Родительский экран под палитрой не рисуется.
     *
     * <p>Палитра — компактное окно поверх вкладки «Территория», а под ним
     * находится текст вкладок. Blur не убирает мелкий текст полностью, и он
     * просвечивал сквозь панель. Подложкой здесь служит затемнённый мир.
     */
    @Override
    protected boolean drawsUnderlyingScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Layout layout = layout();
            for (Swatch swatch : layout.swatches()) {
                int x = layout.left() + swatch.x();
                int y = layout.top() + swatch.y();
                if (mouseX >= x && mouseX < x + swatch.width()
                        && mouseY >= y && mouseY < y + swatch.height()) {
                    /*
                     * Повторный выбор текущего цвета не отправляется:
                     * сервер всё равно отклонил бы его как «без изменений»,
                     * а лишний пакет только вводит в заблуждение.
                     */
                    if (swatch.color() != this.currentColor) {
                        PacketDistributor.sendToServer(new SetCityColorPayload(
                                this.pylonPos, swatch.color()));
                    }
                    this.minecraft.setScreen(this.parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void renderModalContent(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        Layout layout = layout();

        guiGraphics.fill(layout.left(), layout.top(),
                layout.left() + layout.width(), layout.top() + layout.height(), 0xFF202020);
        guiGraphics.fill(layout.left(), layout.top(),
                layout.left() + layout.width(), layout.top() + TITLE_BAR, 0xFF353535);
        guiGraphics.fill(layout.left(), layout.top() + TITLE_BAR - 3,
                layout.left() + layout.width(), layout.top() + TITLE_BAR, 0xFFD09A3A);

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.color.title"),
                layout.left() + PANEL_PADDING,
                layout.top() + 18,
                0xFFFFFF
        );

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.color.description"),
                layout.left() + PANEL_PADDING,
                layout.top() + DESCRIPTION_Y,
                0xAAAAAA
        );

        for (Swatch swatch : layout.swatches()) {
            int x = layout.left() + swatch.x();
            int y = layout.top() + swatch.y();
            guiGraphics.fill(x, y, x + swatch.width(), y + swatch.height(),
                    CityColors.argb(swatch.color(), 0xFF));
            /*
             * Тёмная подложка под рамкой: иначе светлый пресет сливается
             * с тёмной панелью по краю и квадрат «теряется».
             */
            guiGraphics.renderOutline(x - 1, y - 1, swatch.width() + 2, swatch.height() + 2, 0xFF101010);
            if (swatch.color() == this.currentColor) {
                guiGraphics.renderOutline(x - 3, y - 3, swatch.width() + 6, swatch.height() + 6, 0xFFD09A3A);
            }
        }

        renderOwnWidgets(guiGraphics, mouseX, mouseY, partialTick);
    }
}
