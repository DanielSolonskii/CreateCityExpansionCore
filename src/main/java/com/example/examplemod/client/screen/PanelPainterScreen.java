package com.example.examplemod.client.screen;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.machine.PanelPainterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PanelPainterScreen extends Screen {
    private static final int CELL = 24;
    private final InteractionHand hand;
    private final DyeColor selected;

    private PanelPainterScreen(InteractionHand hand, DyeColor selected) {
        super(Component.translatable("item.createcityexpansioncore.panel_painter"));
        this.hand = hand;
        this.selected = selected;
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PanelPainterItem.openPalette = hand -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setScreen(new PanelPainterScreen(hand, PanelPainterItem.color(minecraft.player.getItemInHand(hand))));
            }
        });
    }

    @Override
    protected void init() {
        for (var color : DyeColor.values()) {
            var name = PanelPainterItem.colorName(color);
            addRenderableWidget(Button.builder(Component.empty(), button -> {
                PacketDistributor.sendToServer(new PanelPainterItem.SelectColor(hand, color.getId()));
                onClose();
            }).bounds(x(color), y(color), 22, 22).tooltip(Tooltip.create(name))
                    .createNarration(ignored -> name.copy()).build());
        }
    }

    private int x(DyeColor color) { return width / 2 - 47 + color.getId() % 4 * CELL; }
    private int y(DyeColor color) { return height / 2 - 42 + color.getId() / 4 * CELL; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - 70;
        int top = height / 2 - 68;
        graphics.fill(left, top, left + 140, top + 129, 0xFF222C30);
        graphics.renderOutline(left, top, 140, 129, 0xFF596064);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFD59A);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (var color : DyeColor.values()) {
            graphics.renderItem(new ItemStack(DyeItem.byColor(color)), x(color) + 3, y(color) + 3);
        }
        graphics.renderOutline(x(selected), y(selected), 22, 22, 0xFFFFD59A);
    }

    @Override public boolean isPauseScreen() { return false; }
}
