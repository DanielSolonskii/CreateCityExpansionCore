package com.example.examplemod.client.screen;

import com.example.examplemod.network.RemoveCityMemberPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class CityRemoveMemberConfirmScreen extends ModalOverlayScreen {

    private final Screen parent;
    private final BlockPos pylonPos;
    private final UUID targetPlayer;
    private final String playerName;

    public CityRemoveMemberConfirmScreen(
            Screen parent,
            BlockPos pylonPos,
            UUID targetPlayer,
            String playerName
    ) {
        super(parent, Component.translatable("gui.createcityexpansioncore.city.remove_member.title"));

        this.parent = parent;
        this.pylonPos = pylonPos;
        this.targetPlayer = targetPlayer;
        this.playerName = playerName;
    }

    @Override
    protected void init() {

        super.init();

        int panelWidth = 420;

        int left =
                (this.width - panelWidth) / 2;

        /*
         * =====================================
         * ОТМЕНА
         * =====================================
         */

        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.createcityexpansioncore.common.cancel"),
                        button -> {

                            this.minecraft.setScreen(
                                    this.parent
                            );
                        }
                ).bounds(
                        left + 35,
                        this.height / 2 + 55,
                        165,
                        26
                ).build()
        );

        /*
         * =====================================
         * ИСКЛЮЧИТЬ
         * =====================================
         */

        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.createcityexpansioncore.city.remove_member.confirm"),
                        button -> {

                            if (this.minecraft
                                    != null
                                    && this.minecraft
                                    .getConnection()
                                    != null) {

                                this.minecraft
                                        .getConnection()
                                        .send(
                                                new RemoveCityMemberPayload(
                                                        this.pylonPos,
                                                        this.targetPlayer
                                                )
                                        );
                            }

                            this.minecraft.setScreen(
                                    this.parent
                            );
                        }
                ).bounds(
                        left + 220,
                        this.height / 2 + 55,
                        165,
                        26
                ).build()
        );
    }

    /*
     * =========================================
     * ESC
     * =========================================
     */

    @Override
    public void onClose() {

        this.minecraft.setScreen(
                this.parent
        );
    }

    /*
     * =========================================
     * ФОН
     * =========================================
     */

        @Override
    protected void renderModalContent(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        int panelWidth = 420;
        int panelHeight = 180;

        int left =
                (this.width - panelWidth) / 2;

        int top =
                (this.height - panelHeight) / 2;

        /*
         * =====================================
         * ОСНОВНАЯ ПАНЕЛЬ
         * =====================================
         */

        guiGraphics.fill(
                left,
                top,
                left + panelWidth,
                top + panelHeight,
                0xFF202020
        );

        /*
         * =====================================
         * ВЕРХНЯЯ ПАНЕЛЬ
         * =====================================
         */

        guiGraphics.fill(
                left,
                top,
                left + panelWidth,
                top + 50,
                0xFF353535
        );

        /*
         * Золотая линия Create
         */

        guiGraphics.fill(
                left,
                top + 48,
                left + panelWidth,
                top + 51,
                0xFFD09A3A
        );

        /*
         * =====================================
         * ЗАГОЛОВОК
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.remove_member.heading"),
                left + 20,
                top + 18,
                0xFFFFFF
        );

        /*
         * =====================================
         * ТЕКСТ
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.remove_member.description"),
                left + 20,
                top + 75,
                0xAAAAAA
        );

        /*
         * =====================================
         * ИМЯ ИГРОКА
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.literal(
                        playerName
                ),
                left + 20,
                top + 98,
                0xFFFFFF
        );

        /*
         * =====================================
         * ПРЕДУПРЕЖДЕНИЕ
         * =====================================
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.remove_member.warning"),
                left + 20,
                top + 122,
                0xAAAAAA
        );

        renderOwnWidgets(guiGraphics, mouseX, mouseY, partialTick);
    }
}
