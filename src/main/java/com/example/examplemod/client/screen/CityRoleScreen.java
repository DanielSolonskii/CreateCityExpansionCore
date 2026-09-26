package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityMember;
import com.example.examplemod.network.ChangeCityRolePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import com.example.examplemod.client.screen.CityRemoveMemberConfirmScreen;
import java.util.UUID;
import com.example.examplemod.client.gui.CityButton;
public class CityRoleScreen extends ModalOverlayScreen {

    private final Screen parent;
    private final BlockPos pylonPos;
    private final UUID targetPlayer;
    private final String playerName;

    public CityRoleScreen(
            Screen parent,
            BlockPos pylonPos,
            UUID targetPlayer,
            String playerName
    ) {
        super(parent, Component.translatable("gui.createcityexpansioncore.city.role.title"));

        this.parent = parent;
        this.pylonPos = pylonPos;
        this.targetPlayer = targetPlayer;
        this.playerName = playerName;
    }

    @Override
    protected void init() {

        super.init();

        int panelWidth = 300;
        int panelHeight = 230;

        int left =
                (this.width - panelWidth) / 2;

        int top =
                (this.height - panelHeight) / 2;

        /*
         * =========================================
         * АДМИНИСТРАТОР
         * =========================================
         */

        this.addRenderableWidget(
                new CityButton(
                        left + 25,
                        top + 60,
                        250,
                        25,
                        Component.translatable("role.createcityexpansioncore.admin"),
                        button -> selectRole(
                                CityMember.Role.ADMIN
                        )
                )
        );

        /*
         * =========================================
         * СТРОИТЕЛЬ
         * =========================================
         */

        this.addRenderableWidget(
                new CityButton(
                        left + 25,
                        top + 90,
                        250,
                        25,
                        Component.translatable("role.createcityexpansioncore.builder"),
                        button -> selectRole(
                                CityMember.Role.BUILDER
                        )
                )
        );

        /*
         * =========================================
         * ЖИТЕЛЬ
         * =========================================
         */

        this.addRenderableWidget(
                new CityButton(
                        left + 25,
                        top + 120,
                        250,
                        25,
                        Component.translatable("role.createcityexpansioncore.resident"),
                        button -> selectRole(
                                CityMember.Role.RESIDENT
                        )
                )
        );
        /*
         * =========================================
         * ИСКЛЮЧИТЬ ИЗ ГОРОДА
         * =========================================
         */

        this.addRenderableWidget(
                new CityButton(
                        left + 25,
                        top + 160,
                        250,
                        25,
                        Component.translatable("gui.createcityexpansioncore.city.role.remove"),
                        button -> {

                            if (this.minecraft != null) {

                                this.minecraft.setScreen(
                                        new CityRemoveMemberConfirmScreen(
                                                this.parent,
                                                this.pylonPos,
                                                this.targetPlayer,
                                                this.playerName
                                        )
                                );
                            }
                        }
                )
        );
        /*
         * =========================================
         * ОТМЕНА
         * =========================================
         */

        this.addRenderableWidget(
                new CityButton(
                        left + 25,
                        top + 195,
                        250,
                        25,
                        Component.translatable("gui.createcityexpansioncore.common.cancel"),
                        button -> this.onClose()
                )
        );
    }

    /*
     * =========================================
     * ВЫБОР РОЛИ
     * =========================================
     */

    private void selectRole(
            CityMember.Role role
    ) {

        /*
         * OWNER никогда не должен назначаться
         * через это меню.
         *
         * Дополнительная защита на клиенте.
         */

        if (role == CityMember.Role.OWNER) {
            return;
        }

        PacketDistributor.sendToServer(
                new ChangeCityRolePayload(
                        this.pylonPos,
                        this.targetPlayer,
                        role.ordinal()
                )
        );

        this.onClose();
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
