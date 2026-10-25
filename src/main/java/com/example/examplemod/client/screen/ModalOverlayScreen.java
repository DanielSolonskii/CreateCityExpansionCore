package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Общее поведение модального экрана поверх другого экрана.
 *
 * <p><b>Причина старого артефакта.</b> Модальные экраны вызывали
 * {@code renderBackground()}, но НЕ рисовали родительский экран.
 * Ванильный {@code renderBlurredBackground()} блюрит только 3D-мир,
 * а не GUI, поэтому интерфейс под модалкой оставался резким.
 * Раньше это маскировалось тем, что blur вообще отключали.
 *
 * <p><b>Правильный порядок.</b>
 * <ol>
 *   <li>рисуюется родительский экран целиком, включая его виджеты;</li>
 *   <li>применяется blur ко всему, что уже нарисовано;</li>
 *   <li>поверх рисуется затемнение и содержимое модалки.</li>
 * </ol>
 * Родительские виджеты не перерисовываются после blur, поэтому
 * резких кнопок поверх размытого интерфейса больше нет.
 *
 * <p>Ввод родительским элементам перекрыт автоматически: экран не
 * вызывает у {@code underlying} ни mouseClicked, ни mouseScrolled,
 * ни keyPressed. Ввод обрабатывают только renderables модалки,
 * поэтому подложка не подсвечивается, не получает фокус, не
 * принимает прокрутку и не показывает тултипы.
 */
public abstract class ModalOverlayScreen extends Screen {

    /** Экран, который нужно нарисовать размытым под модалкой. */
    private final Screen underlying;

    private final UiMotion motion = new UiMotion();
    /**
     * Сдвиг, применённый к содержимому в этом кадре.
     *
     * <p>Виджеты модалки рисуются внутри pose, сдвинутого на это значение, поэтому
     * хит-тест обязан вычесть его же — иначе в первые 180 мс после открытия кнопки
     * оказываются там, где их рисовали в прошлом кадре. Значение хранится, а не
     * пересчитывается: обработчики мыши приходят вне кадра и должны видеть ровно то,
     * что было показано последним кадром.
     */
    private float entranceOffset;

    protected ModalOverlayScreen(Screen underlying, Component title) {
        super(title);
        this.underlying = underlying;
    }

    protected Screen underlying() {
        return this.underlying;
    }

    /** Экранные координаты мыши -> координаты содержимого модалки. */
    protected final double localY(double mouseY) {
        return mouseY - this.entranceOffset;
    }

    /**
     * Рисует ли родительский экран под модалкой.
     *
     * <p>По умолчанию да: размытый силуэт интерфейса читается как «это окно поверх
     * того же экрана», а не как отдельное приложение.
     *
     * <p>Однако {@code processBlurEffect} размывает уже нарисованный кадр, и
     * мелкий текст под вкладками после blur остаётся читаемым — особенно при
     * высоком GUI Scale, где шрифт крупный. Для компактных окон вроде палитры
     * цвета это выглядит как дефект: подписи вкладок просвечивают сквозь панель.
     * Такие окна переопределяют метод и возвращают {@code false}: подложкой
     * становится затемнённый мир, и никакой чужой текст под панелью не возможен
     * в принципе.
     */
    protected boolean drawsUnderlyingScreen() {
        return true;
    }

    /**
     * Рисует модальный слой: размытый родитель, затемнение, содержимое.
     *
     * <p>Подклассам не нужно вызывать {@code super.render()} и не нужно
     * вручную рисовать фон — достаточно отрисовать своё содержимое
     * в {@link #renderModalContent(GuiGraphics, int, int, float)}.
     */
    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (CityPylonWorkspace.isEmbedded(this)) {
            guiGraphics.fill(0, 0, width, height, 0xFF192125);
            renderModalContent(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        long now = net.minecraft.Util.getMillis();
        motion.opened(now);
        this.entranceOffset = motion.entranceOffset(now);
        boolean underlying = drawsUnderlyingScreen() && this.underlying != null;

        /*
         * 1. Родительский экран целиком, вместе со своими виджетами.
         *    Он рисуется один раз и больше не перерисовывается.
         */
        if (underlying) {
            this.underlying.render(guiGraphics, -10000, -10000, partialTick);
        }
        // Submit the parent's queued text/items before post-processing its framebuffer.
        guiGraphics.flush();

        /*
         * 2. Blur применяется ко всему, что уже есть в кадре.
         *
         * <p>Вызывается в обоих случаях. Раньше blur был только в ветке с
         * родителем, и окно, которое родителя не рисует, оставляло под собой
         * резкий несurдлённый кадр мира — панель «плавала» поверх него.
         */
        if (this.minecraft != null) {
            this.minecraft.gameRenderer.processBlurEffect(partialTick);
            this.minecraft.getMainRenderTarget().bindWrite(false);
        }

        /*
         * 3. Затемнение и содержимое модалки поверх.
         *
         * <p>Без родителя затемнение плотное: под панелью не должно читаться
         * ничего, иначе панель выглядит дырявой.
         */
        guiGraphics.pose().pushPose();
        // Container labels/items/tooltips have their own positive Z offsets.
        // Both the modal's opaque panels and its widgets must cover those layers.
        guiGraphics.pose().translate(0.0F, 0.0F, 1000.0F);
        // Same slide the profile window uses: the panel eases in from just below its resting
        // place instead of appearing mid-frame.
        guiGraphics.pose().translate(0.0F, this.entranceOffset, 0.0F);
        try {
            guiGraphics.fill(
                    0,
                    0,
                    this.width,
                    this.height,
                    underlying ? 0xB0000000 : 0xF2101719
            );

            this.renderModalContent(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.flush();
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    /**
     * Рисует собственные виджеты модалки.
     *
     * <p>В 1.21.1 {@code Screen} не имеет отдельного
     * {@code renderWidgets()}, а рисует renderables прямо внутри
     * {@code render()}. Здесь повторяем этот цикл: вызывать
     * {@code super.render()} нельзя, потому что он заново нарисовал бы
     * фон и renderables — а нам нужен только слой виджетов поверх
     * уже размытой подложки.
     */
    protected final void renderOwnWidgets(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Ввод передаётся виджетам в координатах содержимого.
     *
     * <p>Виджеты рисуются внутри сдвинутого pose, поэтому их собственные границы —
     * это координаты содержимого, а не экрана. Без этого сдвига кнопка на открытии
     * модалки принимала бы клики на 8 пикселей выше, чем нарисована. Сдвиг к этому
     * моменту уже равен нулю, так что на установившемся окне это честное тождество.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, localY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, localY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, localY(mouseY), button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, localY(mouseY), scrollX, scrollY);
    }

    /**
     * Содержимое модалки: панели, текст, её собственные виджеты.
     */
    protected abstract void renderModalContent(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    );
}

