package com.example.examplemod.client.screen;

import com.example.examplemod.network.RemoveCityMemberPayload;
import net.minecraft.client.gui.GuiGraphics;
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
                new ChromeButton(
                        left + 35,
                        this.height / 2 + 55,
                        165,
                        26,
                        Component.translatable("gui.createcityexpansioncore.common.cancel"),
                        button -> {

                            this.minecraft.setScreen(
                                    this.parent
                            );
                        }
                )
        );

        /*
         * =====================================
         * ИСКЛЮЧИТЬ
         * =====================================
         */

        this.addRenderableWidget(
                new ChromeButton(
                        left + 220,
                        this.height / 2 + 55,
                        165,
                        26,
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
                        },
                        false
                )
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
         * Основная панель и шапка берутся из общего хрома профиля: скруглённые углы,
         * тень и верхняя подсветка вместо плоских прямоугольников.
         */

        WorldMapChrome.panel(
                guiGraphics,
                left,
                top,
                panelWidth,
                panelHeight
        );

        /*
         * Шапка и золотая линия Create.
         */

        WorldMapChrome.rounded(
                guiGraphics,
                left + 1,
                top + 1,
                panelWidth - 2,
                48,
                WorldMapChrome.RADIUS - 1,
                WorldMapChrome.RAISED
        );

        guiGraphics.hLine(
                left + WorldMapChrome.RADIUS,
                left + panelWidth - WorldMapChrome.RADIUS - 1,
                top + 49,
                WorldMapChrome.ACCENT
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
                WorldMapChrome.ACCENT
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
                WorldMapChrome.MUTED
        );

        /*
         * ИМЯ ИГРОКА
         */

        guiGraphics.drawString(
                this.font,
                WorldMapChrome.fit(
                        this.font,
                        Component.literal(playerName),
                        panelWidth - 40
                ),
                left + 20,
                top + 98,
                WorldMapChrome.TEXT
        );

        /*
         * ПРЕДУПРЕЖДЕНИЕ
         */

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.remove_member.warning"),
                left + 20,
                top + 122,
                WorldMapChrome.MUTED
        );

        renderOwnWidgets(guiGraphics, mouseX, mouseY, partialTick);
    }
}
