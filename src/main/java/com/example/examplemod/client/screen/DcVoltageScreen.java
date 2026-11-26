package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.electrical.bridge.DcVoltageNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DcVoltageScreen extends Screen {
    private static final int DIGIT_WIDTH = 30;
    private static final int DIGIT_HEIGHT = 34;
    private static final int CELL = DIGIT_WIDTH + 4;
    private static final int ARROW_HEIGHT = (int) Math.round(DIGIT_WIDTH * Math.sqrt(3) / 2);
    private static final int UP_Y = 25;
    private static final int DIGIT_Y = UP_Y + ARROW_HEIGHT + 3;
    private static final int DOWN_Y = DIGIT_Y + DIGIT_HEIGHT + 3;
    private static final int PANEL_HEIGHT = DOWN_Y + ARROW_HEIGHT + 8;
    private final DcVoltageNetwork.Open settings;
    private final int digits;
    private final Button[] up;
    private final Button[] down;
    private int voltage;
    private int left;
    private int top;
    private int panelWidth;

    private DcVoltageScreen(DcVoltageNetwork.Open settings) {
        super(Component.translatable("gui.createcityexpansioncore.powerbridge.voltage"));
        this.settings = settings;
        voltage = settings.voltage();
        digits = Integer.toString(settings.maximum()).length();
        up = new Button[digits];
        down = new Button[digits];
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DcVoltageNetwork.openScreen =
                settings -> {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.screen == null && GLFW.glfwGetMouseButton(
                            minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
                        minecraft.setScreen(new DcVoltageScreen(settings));
                    }
                });
    }

    @Override
    protected void init() {
        panelWidth = Math.max(digits * CELL + 20, font.width(title) + 26);
        left = (width - panelWidth) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        int step = 1;
        for (int column = digits - 1; column >= 0; column--, step *= 10) {
            int x = digitX(column);
            int place = step;
            up[column] = addRenderableWidget(new ArrowButton(x, top + UP_Y, true, () -> adjust(place)));
            down[column] = addRenderableWidget(new ArrowButton(x, top + DOWN_Y, false, () -> adjust(-place)));
        }
        updateButtons();
    }

    private int digitX(int column) {
        return (width - (digits * CELL - 4)) / 2 + column * CELL;
    }

    private void adjust(int delta) {
        voltage = Math.clamp(voltage + delta, settings.minimum(), settings.maximum());
        updateButtons();
    }

    private void updateButtons() {
        for (int i = 0; i < digits; i++) {
            up[i].active = voltage < settings.maximum();
            down[i].active = voltage > settings.minimum();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        brass(graphics, left, top, panelWidth, PANEL_HEIGHT, 0xff553427);
        for (int y = top + 4; y < top + PANEL_HEIGHT - 3; y += 3) {
            for (int x = left + 4 + (y % 2); x < left + panelWidth - 4; x += 4) {
                graphics.fill(x, y, x + 1, y + 1, ((x ^ y) & 4) == 0 ? 0xff634032 : 0xff4d3025);
            }
        }
        graphics.drawCenteredString(font, title, width / 2, top + 11, 0xfff4d99f);
        String number = String.format(java.util.Locale.ROOT, "%0" + digits + "d", voltage);
        for (int column = 0; column < digits; column++) {
            int x = digitX(column);
            brass(graphics, x, top + DIGIT_Y, DIGIT_WIDTH, DIGIT_HEIGHT, 0xff291b17);
            graphics.pose().pushPose();
            graphics.pose().translate(x + DIGIT_WIDTH / 2f, top + DIGIT_Y + DIGIT_HEIGHT / 2f, 0);
            graphics.pose().scale(2, 2, 1);
            graphics.drawCenteredString(font, Component.literal(number.substring(column, column + 1))
                    .withStyle(ChatFormatting.BOLD), 0, -font.lineHeight / 2, 0xffffdf96);
            graphics.pose().popPose();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void brass(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, 0xff422619);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xffa66532);
        graphics.fill(x + 1, y + 1, x + w - 2, y + 2, 0xfff6cf83);
        graphics.fill(x + 1, y + 2, x + 2, y + h - 2, 0xffdab16c);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, color);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return super.mouseReleased(mouseX, mouseY, button);
        if (voltage != settings.voltage()) {
            PacketDistributor.sendToServer(new DcVoltageNetwork.SetVoltage(settings.pos(), voltage));
        }
        onClose();
        return true;
    }

    private static final class ArrowButton extends Button {
        private final boolean up;

        private ArrowButton(int x, int y, boolean up, Runnable action) {
            super(x, y, DIGIT_WIDTH, ARROW_HEIGHT, Component.literal(up ? "▲" : "▼"),
                    button -> action.run(), DEFAULT_NARRATION);
            this.up = up;
        }

        private double halfWidthAt(double row) {
            double fromTip = up ? row : getHeight() - row;
            return fromTip * getWidth() / (2.0 * getHeight());
        }

        private boolean contains(double mouseX, double mouseY) {
            double row = mouseY - getY();
            double fromCenter = Math.abs(mouseX - getX() - getWidth() / 2.0);
            return row >= 0 && row < getHeight() && fromCenter <= halfWidthAt(row);
        }

        @Override
        protected boolean clicked(double mouseX, double mouseY) {
            return active && visible && contains(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlight = contains(mouseX, mouseY) || isFocused();
            int metal = active ? (highlight ? 0xffe9bb70 : 0xffc99a57) : 0xff745744;
            int edge = active ? 0xff59371f : 0xff46362d;
            int shine = active ? 0xfffbe0a2 : 0xffa08870;
            int bevel = active ? 0xffa26c38 : 0xff5d473a;
            int grainLight = active ? (highlight ? 0xfff1c87d : 0xffd3a462) : 0xff7d604c;
            int grainDark = active ? (highlight ? 0xffdbac64 : 0xffbe8e4d) : 0xff6c5141;
            int center = getX() + getWidth() / 2;
            for (int row = 0; row < getHeight(); row++) {
                int half = Math.max(1, (int) Math.round(halfWidthAt(row + (up ? 1 : 0))));
                int y = getY() + row;
                int first = center - half;
                int last = center + half;
                graphics.fill(first, y, last, y + 1, edge);
                if (half <= 2 || row == 0 || row == getHeight() - 1) continue;
                graphics.fill(first + 1, y, last - 1, y + 1, bevel);
                graphics.fill(first + 1, y, first + 2, y + 1, shine);
                if (row == 1 || row == getHeight() - 2) {
                    if (row == 1) graphics.fill(first + 1, y, last - 1, y + 1, shine);
                    continue;
                }
                graphics.fill(first + 2, y, last - 2, y + 1, metal);
                for (int x = first + 3; x < last - 3; x += 2) {
                    int grain = Math.floorMod((x - center) * 13 + row * 7, 11);
                    if (grain < 3) {
                        graphics.fill(x, y, x + 1, y + 1, grain == 0 ? grainLight : grainDark);
                    }
                }
            }
            int base = getY() + (up ? getHeight() - 6 : 5);
            rivet(graphics, center - 7, base, edge, shine);
            rivet(graphics, center + 7, base, edge, shine);
        }

        private static void rivet(GuiGraphics graphics, int x, int y, int shadow, int highlight) {
            graphics.fill(x - 1, y - 1, x + 2, y + 2, shadow);
            graphics.fill(x - 1, y - 1, x + 1, y, highlight);
            graphics.fill(x - 1, y, x, y + 1, highlight);
        }
    }
}
