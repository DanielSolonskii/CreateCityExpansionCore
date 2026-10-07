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

    protected ModalOverlayScreen(Screen underlying, Component title) {
        super(title);
        this.underlying = underlying;
    }

    protected Screen underlying() {
        return this.underlying;
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
        boolean underlying = drawsUnderlyingScreen() && this.underlying != null;

        /*
         * 1. Родительский экран целиком, вместе со своими виджетами.
         *    Он рисуется один раз и больше не перерисовывается.
         */
        if (underlying) {
            this.underlying.render(guiGraphics, mouseX, mouseY, partialTick);
        }

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
        guiGraphics.fill(
                0,
                0,
                this.width,
                this.height,
                underlying ? 0xB0000000 : 0xF2101719
        );

        this.renderModalContent(guiGraphics, mouseX, mouseY, partialTick);
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
     * Содержимое модалки: панели, текст, её собственные виджеты.
     */
    protected abstract void renderModalContent(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    );
}

