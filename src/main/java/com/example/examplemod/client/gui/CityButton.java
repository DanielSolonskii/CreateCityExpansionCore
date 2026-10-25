package com.example.examplemod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import com.example.examplemod.client.screen.UiMotion;
public class CityButton extends Button {
    public enum Style { CITY, INDUSTRIAL, DANGER }

    private boolean selected;
    private final Style style;
    /** Optional leading icon. Null for every existing button, so those render unchanged. */
    private net.minecraft.world.item.ItemStack icon;
    /** Smoothed hover, keyed by position: a fixed control never moves, so position is stable. */
    private final UiMotion motion = new UiMotion();

    public void setIcon(net.minecraft.world.item.ItemStack icon) {
        this.icon = icon;
    }

    public void setActive(boolean active) {
        this.selected = active;
    }

    public boolean isActive() {
        return this.selected;
    }
    public CityButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            OnPress onPress
    ) {
        this(x, y, width, height, message, onPress, Style.CITY);
    }

    public CityButton(int x, int y, int width, int height, Component message,
                      OnPress onPress, Style style) {
        super(
                x,
                y,
                width,
                height,
                message,
                onPress,
                DEFAULT_NARRATION
        );
        this.style = style;
    }

    @Override
    protected void renderWidget(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        boolean hovered = this.isHoveredOrFocused();
        // Keyed by position: this control never moves, so its coordinates are a stable identity.
        // Lerping rather than switching keeps a sweep across a row of tabs from flickering.
        float lit = motion.hover(getX() * 7919 + getY(), hovered,
                motion.delta(net.minecraft.Util.getMillis()));

        int backgroundColor;
        int borderColor;
        int textColor = this.active ? 0xFFE8EEF2 : 0xFF68767C;
        int accent = switch (style) {
            case CITY -> 0xFFD09A3A;
            case INDUSTRIAL -> 0xFF58D7FF;
            case DANGER -> 0xFFFF5A4F;
        };

        int resting = style == Style.INDUSTRIAL ? 0xFF101E25 : 0xFF292929;
        int restingEdge = style == Style.INDUSTRIAL ? 0xFF34515E : 0xFF555555;
        int lifted = style == Style.INDUSTRIAL ? 0xFF16323D : 0xFF3A3A3A;
        if (!this.active) {
            backgroundColor = 0xFF171D20;
            borderColor = 0xFF344148;
        } else if (selected) {
            backgroundColor = style == Style.INDUSTRIAL ? 0xFF17303A : 0xFF303030;
            borderColor = accent;
        } else {
            backgroundColor = UiMotion.mix(resting, lifted, lit);
            borderColor = UiMotion.mix(restingEdge, accent, lit);
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
         * Верхний блик. The resting state is opaque and the hovered state is a translucent accent
         * tint, so the blend has to carry alpha as well -- an opaque mix would turn the reflection
         * into a solid bar the moment the cursor arrives.
         */
        guiGraphics.fill(
                this.getX() + 2,
                this.getY() + 2,
                this.getX() + this.width - 2,
                this.getY() + 3,
                this.active ? tint(0xFF27343A, accent & 0x66FFFFFF, lit) : 0xFF27343A
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
        Component label = this.getMessage();
        int iconSlot = this.icon == null ? 0 : 20;
        if (this.icon != null) {
            guiGraphics.renderItem(
                    this.icon,
                    this.getX() + 5,
                    this.getY() + (this.height - 16) / 2);
        }
        int availableWidth = Math.max(1, this.width - 8 - iconSlot);
        String fitted = Minecraft.getInstance().font.plainSubstrByWidth(label.getString(), availableWidth);
        if (!fitted.equals(label.getString()) && fitted.length() > 2) {
            fitted = Minecraft.getInstance().font.plainSubstrByWidth(
                    label.getString(), Math.max(1, availableWidth - 6)) + "…";
        }
        int textWidth = Minecraft.getInstance().font.width(fitted);

        int textX =
                this.getX()
                        + iconSlot
                        + (this.width - iconSlot - textWidth) / 2;

        int textY =
                this.getY()
                        + (this.height - 8) / 2;

        guiGraphics.drawString(
                Minecraft.getInstance().font,
                fitted,
                textX,
                textY,
                textColor
        );
    }

    /**
     * Blends two colours including their alpha channels.
     *
     * <p>{@link UiMotion#mix} always returns an opaque colour, which is right for surfaces but
     * wrong for a glint that is meant to stay translucent.
     */
    private static int tint(int from, int to, float lit) {
        float clamped = Math.max(0f, Math.min(1f, lit));
        int alpha = (int) (((from >> 24) & 255) * (1 - clamped) + ((to >> 24) & 255) * clamped);
        int red = (int) (((from >> 16) & 255) * (1 - clamped) + ((to >> 16) & 255) * clamped);
        int green = (int) (((from >> 8) & 255) * (1 - clamped) + ((to >> 8) & 255) * clamped);
        int blue = (int) ((from & 255) * (1 - clamped) + (to & 255) * clamped);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
