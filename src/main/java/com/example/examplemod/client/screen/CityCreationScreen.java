package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.example.examplemod.network.CreateCityPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
public class CityCreationScreen extends Screen {

    private EditBox cityName;
    private final BlockPos pylonPos;
    public CityCreationScreen(BlockPos pylonPos) {
        super(Component.translatable("gui.createcityexpansioncore.city.creation.title"));
        this.pylonPos = pylonPos;
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = 320;
        int panelLeft = (this.width - panelWidth) / 2;

        // Поле названия города
        this.cityName = new EditBox(
                this.font,
                panelLeft + 20,
                this.height / 2 - 45,
                panelWidth - 40,
                24,
                Component.translatable("gui.createcityexpansioncore.city.creation.name")
        );

        this.cityName.setMaxLength(32);
        this.cityName.setHint(
                Component.translatable("gui.createcityexpansioncore.city.creation.name_hint")
        );

        this.addRenderableWidget(this.cityName);

        // Кнопка "Назад"
        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.createcityexpansioncore.common.back"),
                        button -> {
                            this.onClose();
                        }

                ).bounds(
                        panelLeft + 20,
                        this.height / 2 + 25,
                        135,
                        24
                ).build()
        );

        // Кнопка "Создать"
        this.addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.createcityexpansioncore.city.creation.create"),
                        button -> {
                            String name = this.cityName.getValue().trim();

                            if (!name.isBlank()) {
                                PacketDistributor.sendToServer(
                                        new CreateCityPayload(
                                                name,
                                                this.pylonPos
                                        )
                                );

                                this.onClose();
                            }
                        }
                ).bounds(
                        panelLeft + 165,
                        this.height / 2 + 25,
                        135,
                        24
                ).build()
        );

        this.setInitialFocus(this.cityName);
    }

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        int panelWidth = 320;
        int panelHeight = 220;

        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        // Основная панель
        guiGraphics.fill(
                left,
                top,
                left + panelWidth,
                top + panelHeight,
                0xFF252525
        );

        // Верхняя часть
        guiGraphics.fill(
                left,
                top,
                left + panelWidth,
                top + 42,
                0xFF3A3A3A
        );

        // Золотая линия
        guiGraphics.fill(
                left,
                top + 40,
                left + panelWidth,
                top + 42,
                0xFFD09A3A
        );

        // Заголовок
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.creation.heading"),
                left + 14,
                top + 13,
                0xFFFFFF
        );

        // Подпись
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.creation.name"),
                left + 20,
                top + 58,
                0xD0D0D0
        );

        // Информация
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.creation.territory", 100, 100),
                left + 20,
                top + 105,
                0x909090
        );

        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.createcityexpansioncore.city.creation.cost_pending"),
                left + 20,
                top + 122,
                0x909090
        );

        super.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );
    }
}
