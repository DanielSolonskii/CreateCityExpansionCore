package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.UUID;
public class CityRoleScreen extends ModalOverlayScreen {

    private final Screen parent;
    private final UUID targetPlayer;
    private final String playerName;

    public CityRoleScreen(
            Screen parent,
            UUID targetPlayer,
            String playerName
    ) {
        super(parent, Component.translatable("gui.createcityexpansioncore.city.role.title"));

        this.parent = parent;
        this.targetPlayer = targetPlayer;
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        CommunityScreen.openRoles(this.parent,this.targetPlayer);
    }

    /*
     * =========================================
     * ЗАКРЫТИЕ
     * =========================================
     */

    @Override
    public void onClose() {

        if (this.minecraft != null) {

            this.minecraft.setScreen(
                    this.parent
            );
        }
    }
    public Screen getParentScreen() {
        return this.parent;
    }
    /*
     * =========================================
     * ОТРИСОВКА
     * =========================================
     */
    @Override
    protected void renderModalContent(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        int panelWidth = 300;
        int panelHeight = 230;

        int left =
                (this.width - panelWidth) / 2;

        int top =
                (this.height - panelHeight) / 2;

        /*
         * =========================================
         * ОСНОВНАЯ ПАНЕЛЬ
         * =========================================
         */

        guiGraphics.fill(
                left,
                top,
                left + panelWidth,
                top + panelHeight,
                0xFF202020
        );

        /*
         * =========================================
         * ВЕРХНЯЯ ПАНЕЛЬ
         * =========================================
         */

        guiGraphics.fill(
                left,
                top,
                left + panelWidth,
                top + 45,
                0xFF353535
        );

        /*
         * =========================================
         * ЗОЛОТАЯ ЛИНИЯ
         * =========================================
         */

        guiGraphics.fill(
                left,
                top + 43,
                left + panelWidth,
                top + 46,
                0xFFD09A3A
        );

        /*
         * =========================================
         * ЗАГОЛОВОК
         * =========================================
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.role.heading"),
                left + 15,
                top + 10,
                0xFFFFFF
        );

        /*
         * =========================================
         * ИМЯ ИГРОКА
         * =========================================
         */

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        playerName
                ),
                left + 15,
                top + 29,
                0xAAAAAA
        );

        /*
         * =========================================
         * ПОДСКАЗКА
         * =========================================
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.role.select"),
                left + 25,
                top + 48,
                0x777777
        );

        /*
         * =========================================
         * КНОПКИ
         * =========================================
         */

        renderOwnWidgets(guiGraphics, mouseX, mouseY, partialTick);
    }

}
