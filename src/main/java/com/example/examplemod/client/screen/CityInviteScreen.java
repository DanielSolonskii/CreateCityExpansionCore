package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.network.CityInviteNotificationPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CityInviteScreen extends Screen {

    private static final int PANEL_WIDTH =
            420;

    private static final int PANEL_HEIGHT =
            235;

    private final UiMotion motion = new UiMotion();

    public CityInviteScreen() {

        super(
                Component.literal(
                        "Приглашение в город"
                )
        );
    }

    /*
     * =========================================
     * INIT
     * =========================================
     */

    @Override
    protected void init() {

        super.init();

        int left =
                (this.width - PANEL_WIDTH) / 2;

        int top =
                (this.height - PANEL_HEIGHT) / 2;

        /*
         * ПРИНЯТЬ
         */

        this.addRenderableWidget(
                new ChromeButton(
                        left + 25,
                        top + 175,
                        175,
                        30,
                        Component.literal(
                                "ПРИНЯТЬ"
                        ),
                        button ->
                                CityInviteClientHandler
                                        .respond(true),
                        true
                )
        );

        /*
         * ОТКЛОНИТЬ
         */

        this.addRenderableWidget(
                new ChromeButton(
                        left + 220,
                        top + 175,
                        175,
                        30,
                        Component.literal(
                                "ОТКЛОНИТЬ"
                        ),
                        button ->
                                CityInviteClientHandler
                                        .respond(false)
                )
        );
    }

    /*
     * =========================================
     * ESC
     * =========================================
     */

    @Override
    public boolean shouldCloseOnEsc() {

        return false;
    }

    /*
     * =========================================
     * RENDER
     * =========================================
     */

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        /*
         * =========================================
         * ФОН БЕЗ BLUR
         * =========================================
         *
         * Намеренно НЕ вызываем:
         *
         * renderBackground(...)
         *
         * Поэтому Minecraft не размывает
         * игровой мир за интерфейсом.
         */

        long now = net.minecraft.Util.getMillis();
        motion.opened(now);
        float offset = motion.entranceOffset(now);

        int left =
                (this.width - PANEL_WIDTH) / 2;

        int top =
                (this.height - PANEL_HEIGHT) / 2;

        /*
         * Виджеты рисуются внутри того же pose, поэтому сдвигаются вместе с панелью.
         */

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, offset, 0.0F);
        try {

        /*
         * Панель
         */

        WorldMapChrome.panel(
                guiGraphics,
                left,
                top,
                PANEL_WIDTH,
                PANEL_HEIGHT
        );

        /*
         * Заголовок
         */

        WorldMapChrome.rounded(
                guiGraphics,
                left + 1,
                top + 1,
                PANEL_WIDTH - 2,
                51,
                WorldMapChrome.RADIUS - 1,
                WorldMapChrome.RAISED
        );

        /*
         * Линия
         */

        guiGraphics.hLine(
                left + WorldMapChrome.RADIUS,
                left + PANEL_WIDTH - WorldMapChrome.RADIUS - 1,
                top + 50,
                WorldMapChrome.ACCENT
        );

        /*
         * Заголовок
         */

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "ПРИГЛАШЕНИЕ В ГОРОД"
                ),
                this.width / 2,
                top + 14,
                WorldMapChrome.ACCENT
        );

        /*
         * =========================================
         * ПРИГЛАШЕНИЕ
         * =========================================
         */

        CityInviteNotificationPayload invite =
                CityInviteClientHandler
                        .getCurrentInvite();

        if (
                invite == null
        ) {

            this.onClose();
            return;
        }

        int count =
                CityInviteClientHandler
                        .getInviteCount();

        if (
                count > 1
        ) {

            guiGraphics.drawCenteredString(
                    this.font,
                    Component.literal(
                            "ПРИГЛАШЕНИЕ 1 / "
                                    + count
                    ),
                    this.width / 2,
                    top + 65,
                    0x888888
            );
        }

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "Вас приглашают в город"
                ),
                this.width / 2,
                top + 85,
                WorldMapChrome.MUTED
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "«"
                                + invite.cityName()
                                + "»"
                ),
                this.width / 2,
                top + 108,
                WorldMapChrome.ACCENT
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "Владелец: "
                                + invite.ownerName()
                ),
                this.width / 2,
                top + 133,
                WorldMapChrome.TEXT
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "После принятия вы станете жителем города."
                ),
                this.width / 2,
                top + 153,
                WorldMapChrome.MUTED
        );

        /*
         * =========================================
         * КНОПКИ
         * =========================================
         */

        super.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        } finally {
            // The early return above (no pending invite) must not leave the pose unbalanced,
            // or every later screen would render shifted.
            guiGraphics.pose().popPose();
        }
    }

    /*
     * =========================================
     * ВВОД
     * =========================================
     *
     * Виджеты нарисованы внутри сдвинутого pose, поэтому их границы живут в
     * координатах панели. Клики и прокрутка приводятся к ним же, иначе в первые
     * 180 мс после открытия кнопки принимали бы попадания выше нарисованных.
     */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY - motion.entranceOffset(net.minecraft.Util.getMillis()), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY - motion.entranceOffset(net.minecraft.Util.getMillis()), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY - motion.entranceOffset(net.minecraft.Util.getMillis()), button, dragX, dragY);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Ничего не делаем.
        // Приглашение не должно размывать игровой мир.
    }
}