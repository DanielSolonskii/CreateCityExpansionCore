package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityInviteClientHandler;
import com.example.examplemod.network.CityInviteNotificationPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CityInviteScreen extends Screen {

    private static final int PANEL_WIDTH =
            420;

    private static final int PANEL_HEIGHT =
            235;

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
                Button.builder(
                        Component.literal(
                                "ПРИНЯТЬ"
                        ),
                        button ->
                                CityInviteClientHandler
                                        .respond(true)
                ).bounds(
                        left + 25,
                        top + 175,
                        175,
                        30
                ).build()
        );

        /*
         * ОТКЛОНИТЬ
         */

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "ОТКЛОНИТЬ"
                        ),
                        button ->
                                CityInviteClientHandler
                                        .respond(false)
                ).bounds(
                        left + 220,
                        top + 175,
                        175,
                        30
                ).build()
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

        int left =
                (this.width - PANEL_WIDTH) / 2;

        int top =
                (this.height - PANEL_HEIGHT) / 2;

        /*
         * Панель
         */

        guiGraphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF202020
        );

        /*
         * Заголовок
         */

        guiGraphics.fill(
                left,
                top,
                left + PANEL_WIDTH,
                top + 52,
                0xFF353535
        );

        /*
         * Линия
         */

        guiGraphics.fill(
                left,
                top + 49,
                left + PANEL_WIDTH,
                top + 52,
                0xFFD09A3A
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
                0xFFFFFF
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
                0xAAAAAA
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
                0xFFD09A3A
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "Владелец: "
                                + invite.ownerName()
                ),
                this.width / 2,
                top + 133,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                this.font,
                Component.literal(
                        "После принятия вы станете жителем города."
                ),
                this.width / 2,
                top + 153,
                0x777777
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
    }
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Ничего не делаем.
        // Приглашение не должно размывать игровой мир.
    }
}