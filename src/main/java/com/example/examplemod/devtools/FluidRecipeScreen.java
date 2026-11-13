package com.example.examplemod.devtools;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidUtil;
import java.util.*;
import java.util.function.Consumer;

public final class FluidRecipeScreen extends Screen {
    private static final int ROWS = 8, ROW_H = 24;
    private final Screen parent;
    private final Consumer<JsonObject> accept;
    private final FluidRecipeDraft draft;
    private List<FluidRecipeDraft.Entry> entries = List.of();
    private int active = -1, top;
    private float scale = 1;
    private ItemStack sample = ItemStack.EMPTY;
    private String error = "Добавьте жидкость или выберите запись в списке слева.";
    private EditBox fluidBox, amountBox;

    public FluidRecipeScreen(Screen parent, JsonObject json, Consumer<JsonObject> accept) {
        super(Component.literal("Жидкости рецепта"));
        this.parent = parent; this.accept = accept; this.draft = new FluidRecipeDraft(json);
    }

    private void button(String text, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> {
            try { store(); action.run(); rebuild(); } catch (Exception e) { error = e.getMessage(); }
        }).bounds(x, y, w, 20).build());
    }

    private EditBox field(String value, int x, int y, int w) {
        var box = addRenderableWidget(new EditBox(font, x, y, w, 20, Component.empty()));
        box.setMaxLength(256); box.setValue(value); return box;
    }

    private String name(JsonObject obj) {
        String value = FluidRecipeDraft.nameOf(obj);
        return value.isEmpty() ? "minecraft:water" : value;
    }

    private int amount(JsonObject obj) { return FluidRecipeDraft.amountOf(obj); }

    private boolean tagMode() {
        return active >= 0 && active < entries.size()
                && FluidRecipeDraft.isTag(entries.get(active).json());
    }

    private void store() {
        if (fluidBox == null || active < 0 || active >= entries.size()) return;
        String value = fluidBox.getValue().trim();
        if (!tagMode()) ResourceLocation.parse(value);
        int volume = Integer.parseInt(amountBox.getValue());
        draft.set(active, value, tagMode(), volume);
    }

    private void write() {
        try {
            String value = fluidBox.getValue().trim();
            if (!tagMode() && !BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(value)))
                throw new IllegalArgumentException("Неизвестная жидкость: " + value);
            store();
            error = "Жидкость обновлена.";
            rebuild();
        } catch (Exception e) { error = e.getMessage(); }
    }

    private void pickBucket() {
        var contained = FluidUtil.getFluidContained(sample);
        if (contained.isEmpty() || contained.get().isEmpty()) {
            error = "Возьмите в образцы ведро или другой предмет с жидкостью.";
            return;
        }
        if (fluidBox != null) fluidBox.setValue(BuiltInRegistries.FLUID.getKey(contained.get().getFluid()).toString());
        error = "Записано: " + BuiltInRegistries.FLUID.getKey(contained.get().getFluid()).toString()
                + ". Нажмите «Применить».";
    }

    @Override protected void init() {
        scale = Math.min(1f, Math.min(width / 680f, height / 480f));
        width = Math.round(width / scale); height = Math.round(height / scale);
        entries = draft.entries(); fluidBox = amountBox = null;
        button("Применить в черновик", 12, 28, 180, () -> { accept.accept(draft.finish()); minecraft.setScreen(parent); });
        button("Отмена", 200, 28, 90, () -> minecraft.setScreen(parent));
        button("− Удалить выбранную", 478, 28, 190, () -> { draft.remove(active); active = Math.min(active, draft.entries().size() - 1); });
        if (draft.input()) button("+ Жидкость в ингредиенты", 12, 58, 230, () -> { draft.add(false); active = draft.entries().size() - 1; });
        if (draft.result()) button("+ Жидкость в результат", 254, 58, 230, () -> { draft.add(true); active = draft.entries().size() - 1; });
        if (active >= 0 && active < entries.size()) {
            var obj = entries.get(active).json();
            fluidBox = field(name(obj), 320, 100, 260);
            amountBox = field(String.valueOf(amount(obj)), 596, 100, 72);
            if (!entries.get(active).result()) button(tagMode() ? "Тег жидкости" : "Конкретная жидкость", 320, 128, 170, () -> draft.toggleTag(active));
            button("Применить", 496, 128, 80, this::write);
            addRenderableWidget(Button.builder(Component.literal("Из ведра"), b -> { try { pickBucket(); } catch (Exception e) { error = e.getMessage(); } }).bounds(584, 128, 84, 20).build());
        }
        top = Math.min(top, Math.max(0, entries.size() - ROWS));
    }

    private void rebuild() { if (minecraft.screen != this) return; width = Math.round(width * scale); height = Math.round(height * scale); clearWidgets(); init(); }

    private ItemStack bucket(JsonObject obj) {
        String id = name(obj);
        try {
            if (obj.has("fluid") && !obj.has("item")) {
                var state = BuiltInRegistries.FLUID.get(ResourceLocation.parse(obj.get("fluid").getAsString()));
                var bucket = new ItemStack(state.getBucket());
                if (!bucket.isEmpty()) return bucket;
            }
        } catch (Exception ignored) { }
        return new ItemStack(Items.BUCKET);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        x /= scale; y /= scale;
        int invX = (width - 180) / 2, invY = height - 80;
        if (minecraft.player != null) for (int i = 0; i < 36; i++) {
            int px = invX + i % 9 * 20, py = invY + i / 9 * 18;
            if (x >= px && x < px + 18 && y >= py && y < py + 18) {
                sample = minecraft.player.getInventory().getItem(i).copy();
                error = "Образец: " + (sample.isEmpty() ? "пусто" : sample.getHoverName().getString()) + ".";
                return true;
            }
        }
        if (button == 0 && x >= 12 && x < 300 && y >= 128 && y < 128 + ROWS * ROW_H) {
            int index = top + (int) (y - 128) / ROW_H;
            if (index >= 0 && index < entries.size()) { active = index; error = "Выбрана запись " + (index + 1) + "."; rebuild(); return true; }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x / scale >= 12 && x / scale < 300 && y / scale >= 128 && y / scale < 128 + ROWS * ROW_H) {
            top = Math.clamp(top - (int) Math.signum(dy), 0, Math.max(0, entries.size() - ROWS));
            return true;
        }
        return super.mouseScrolled(x / scale, y / scale, dx, dy);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.pose().pushPose(); g.pose().scale(scale, scale, 1);
        g.fill(0, 0, width, height, 0xff172026); super.render(g, (int) (mx / scale), (int) (my / scale), pt);
        g.drawString(font, title, 12, 10, 0xffdfbd77);
        g.drawString(font, "Тип рецепта: " + (draft.recipe().has("type") ? draft.recipe().get("type").getAsString() : "неизвестен"), 310, 10, 0xffa0afb8);
        g.drawString(font, "Записи жидкостей в рецепте", 12, 110, 0xffffffff);
        g.drawString(font, isLoadingNone() ? "Тип рецепта не поддерживает жидкости; используйте JSON." : "", 12, 96, 0xffe4cf94);
        g.drawString(font, "ID жидкости или тега", 320, 82, 0xffffffff);
        g.drawString(font, "Объём, mB", 596, 82, 0xffffffff);
        if (fluidBox == null && !entries.isEmpty()) g.drawString(font, "Выберите запись слева, чтобы редактировать.", 320, 104, 0xff9ab0bd);
        g.drawString(font, "Записей: " + entries.size() + (entries.size() > ROWS ? " · колёсико прокручивает список" : ""), 12, 128 + ROWS * ROW_H + 6, 0xffa0afb8);
        for (int row = 0; row < Math.min(entries.size() - top, ROWS); row++) {
            int index = top + row, x = 12, y = 128 + row * ROW_H;
            var obj = entries.get(index).json();
            g.fill(x - 1, y - 1, x + 286, y + ROW_H - 2, index == active ? 0xffad873d : 0xff53616c);
            var stack = bucket(obj);
            g.renderItem(stack, x, y + 3);
            String label = (index + 1) + ". " + name(obj) + (tagModeOf(obj) ? " (тег)" : "") + " — " + amount(obj) + " mB"
                    + (entries.get(index).result() ? " → результат" : " → ингредиент");
            g.drawString(font, font.plainSubstrByWidth(label, 240), x + 34, y + 8, index == active ? 0xfffff4d0 : 0xffc5d7df);
        }
        if (minecraft.player != null) {
            int invX = (width - 180) / 2, invY = height - 80;
            for (int i = 0; i < 36; i++) { int px = invX + i % 9 * 20, py = invY + i / 9 * 18;
                g.fill(px - 1, py - 1, px + 18, py + 17, 0xff34414b); g.renderItem(minecraft.player.getInventory().getItem(i), px, py); }
        }
        g.drawString(font, font.plainSubstrByWidth(error, width - 24), 12, height - 96, 0xffe4cf94);
        g.pose().popPose();
    }

    private boolean isLoadingNone() { return !draft.input() && !draft.result() && entries.isEmpty(); }
    private boolean tagModeOf(JsonObject obj) { return FluidRecipeDraft.isTag(obj); }

    @Override public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        try { store(); } catch (Exception ignored) { }
        super.resize(mc, w, h);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
