package com.example.examplemod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
public class CityButton extends Button {
    private boolean active = false;

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return this.active;
    }
    public CityButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            OnPress onPress
    ) {
        super(
                x,
                y,
                width,
                height,
                message,
                onPress,
                DEFAULT_NARRATION
        );
    }

    @Override
    protected void renderWidget(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        boolean hovered = this.isHoveredOrFocused();

        int backgroundColor;
        int borderColor;
        int textColor = 0xFFE8E8E8;

        if (active) {
            backgroundColor = 0xFF303030;
            borderColor = 0xFFD09A3A;
        } else if (hovered) {
            backgroundColor = 0xFF3A3A3A;
            borderColor = 0xFFD09A3A;
        } else {
            backgroundColor = 0xFF292929;
            borderColor = 0xFF555555;
        }

        /*
         * Внешняя рамка
         */
        guiGraphics.fill(
                this.getX(),
                this.getY(),
                this.getX() + this.width,
                this.getY() + this.height,
                borderColor
        );

        /*
         * Основная поверхность
         */
        guiGraphics.fill(
                this.getX() + 1,
                this.getY() + 1,
                this.getX() + this.width - 1,
                this.getY() + this.height - 1,
                backgroundColor
        );

        /*
         * Верхний блик
         */
        guiGraphics.fill(
                this.getX() + 2,
                this.getY() + 2,
                this.getX() + this.width - 2,
                this.getY() + 3,
                hovered
                        ? 0xFF6A6A6A
                        : 0xFF454545
        );

        /*
         * Нижняя тень
         */
        guiGraphics.fill(
                this.getX() + 2,
                this.getY() + this.height - 3,
                this.getX() + this.width - 2,
                this.getY() + this.height - 2,
                0xFF1A1A1A
        );

        /*
         * Текст
         */
        int textWidth =
                Minecraft.getInstance().font.width(
                        this.getMessage()
                );

        int textX =
                this.getX()
                        + (this.width - textWidth) / 2;

        int textY =
                this.getY()
                        + (this.height - 8) / 2;

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                this.getMessage(),
                textX,
                textY,
                textColor
        );
    }
}