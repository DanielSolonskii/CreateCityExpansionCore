package com.example.examplemod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Компактный выбор предмета внутри текущего экрана.
 * <p>
 * Список берётся из реального реестра предметов Minecraft.
 * Поиск идёт сначала по локализованному названию, потом по registry id.
 * Рисуется сеткой иконок с прокруткой и корректным clipping.
 */
public final class ItemSelectorOverlay {

    /** Ячейка сетки: 16px иконка + 2px отступ с каждой стороны. */
    private static final int CELL = 20;
    private static final int PAD = 8;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 15;

    private static final int COLOR_PANEL = 0xF5151B1E;
    private static final int COLOR_CELL = 0xFF20272B;
    private static final int COLOR_CELL_HOVER = 0xFF31404A;
    private static final int COLOR_BORDER = 0xFFD09A3A;
    private static final int COLOR_TEXT = 0xFFE2E6E7;
    private static final int COLOR_MUTED = 0xFF8F9A9E;

    private record Entry(ResourceLocation id, Item item, String name, String modName) {
        String nameKey() {
            return name.toLowerCase(Locale.ROOT);
        }

        String idKey() {
            return id.toString().toLowerCase(Locale.ROOT);
        }
    }

    private static List<Entry> cache;

    private boolean open;
    /** Smoothed hover for the grid cells and the close control. */
    private final UiMotion motion = new UiMotion();
    /** One lerp step per frame, measured once in render -- not per cell. */
    private float frameDelta;
    private int x;
    private int y;
    private int w;
    private int h;
    private int scroll;
    private EditBox search;
    private String lastQuery = "";
    private List<Entry> filtered = List.of();
    private Consumer<ResourceLocation> onSelect;

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        search = null;
        onSelect = null;
        filtered = List.of();
        scroll = 0;
        lastQuery = "";
    }

    public void open(int x, int y, int w, int h, Consumer<ResourceLocation> onSelect) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.onSelect = onSelect;
        this.scroll = 0;
        this.lastQuery = "";
        this.filtered = entries();
        Minecraft minecraft = Minecraft.getInstance();
        this.search = new EditBox(minecraft.font, x + PAD, y + 26, w - PAD * 2 - 18, 16,
                Component.translatable("gui.createcityexpansioncore.item_selector.search"));
        this.search.setHint(Component.translatable("gui.createcityexpansioncore.item_selector.search_hint"));
        this.search.setMaxLength(64);
        this.search.setFocused(true);
        this.open = true;
    }

    private static List<Entry> entries() {
        if (cache != null) return cache;
        List<Entry> built = new ArrayList<>(1024);
        for (var reference : BuiltInRegistries.ITEM.entrySet()) {
            Item item = reference.getValue();
            if (item == null || item == Items.AIR) continue;
            ResourceLocation id = reference.getKey().location();
            String name = new ItemStack(item).getHoverName().getString();
            if (name == null || name.isBlank()) name = id.toString();
            built.add(new Entry(id, item, name, modName(id.getNamespace())));
        }
        built.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        cache = List.copyOf(built);
        return cache;
    }

    /** Название мода для второй строки подсказки; null — если мод не найден. */
    private static String modName(String namespace) {
        if ("minecraft".equals(namespace)) return "Minecraft";
        if ("createcityexpansioncore".equals(namespace)) return null;
        try {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById(namespace)
                    .map(container -> container.getModInfo().getDisplayName())
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void reindex() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery)) return;
        lastQuery = query;
        scroll = 0;
        if (query.isEmpty()) {
            filtered = entries();
            return;
        }
        List<Entry> byName = new ArrayList<>();
        List<Entry> byId = new ArrayList<>();
        for (Entry entry : entries()) {
            if (entry.nameKey().contains(query)) byName.add(entry);
            else if (entry.idKey().contains(query)) byId.add(entry);
        }
        byName.addAll(byId);
        filtered = List.copyOf(byName);
    }

    private int gridX() {
        return x + PAD + 1;
    }

    private int gridY() {
        return y + HEADER_H;
    }

    private int gridW() {
        return w - PAD * 2 - 2;
    }

    private int gridH() {
        return h - HEADER_H - FOOTER_H - 2;
    }

    private int columns() {
        return Math.max(1, gridW() / CELL);
    }

    private int rows() {
        return Math.max(1, gridH() / CELL);
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - columns() * rows());
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!open) return;
        reindex();
        this.frameDelta = motion.delta(net.minecraft.Util.getMillis());

        Minecraft minecraft = Minecraft.getInstance();
        int screenW = minecraft.getWindow().getGuiScaledWidth();
        int screenH = minecraft.getWindow().getGuiScaledHeight();

        // Затемнение подложки: клики по ней блокируются в mouseClicked.
        graphics.fill(0, 0, screenW, screenH, 0x96000000);
        // Drop shadow first, then the gold rim, then the surface: the rim reads as the panel's
        // edge instead of as a line drawn on top of it.
        WorldMapChrome.rounded(graphics, x, y + 3, w, h, WorldMapChrome.RADIUS, 0x50000000);
        WorldMapChrome.rounded(graphics, x, y, w, h, WorldMapChrome.RADIUS, COLOR_BORDER);
        WorldMapChrome.rounded(graphics, x + 1, y + 1, w - 2, h - 2, WorldMapChrome.RADIUS - 1, COLOR_PANEL);
        graphics.fill(x + WorldMapChrome.RADIUS, y + 1, x + w - WorldMapChrome.RADIUS, y + 4, 0xFFD09A3A);

        graphics.drawString(minecraft.font,
                Component.translatable("gui.createcityexpansioncore.item_selector.title"),
                x + PAD, y + 11, COLOR_TEXT, false);

        // Кнопка закрытия. A fixed control, so its position is already a stable id.
        int closeX = x + w - 20;
        int closeY = y + 9;
        float closeLit = motion.hover(closeX * 7919 + closeY,
                inside(mouseX, mouseY, closeX, closeY, 13, 13), frameDelta);
        graphics.fill(closeX, closeY, closeX + 13, closeY + 13,
                UiMotion.mix(0xFF3A2A27, 0xFF8A4B3F, closeLit));
        graphics.drawCenteredString(minecraft.font, "x", closeX + 6, closeY + 3, 0xFFF0D9D4);

        if (search != null) search.render(graphics, mouseX, mouseY, partialTick);

        int gx = gridX();
        int gy = gridY();
        int cols = columns();
        int rowCount = rows();

        graphics.fill(gx, gy, gx + gridW(), gy + gridH(), 0xE0101518);

        CityPylonWorkspace.clip(graphics,gx, gy, gx + gridW(), gy + gridH());
        int hoverIndex = -1;
        for (int index = 0; index < filtered.size(); index++) {
            int cell = index - scroll;
            if (cell < 0 || cell >= cols * rowCount) continue;
            int cellX = gx + (cell % cols) * CELL;
            int cellY = gy + (cell / cols) * CELL;
            boolean hover = inside(mouseX, mouseY, cellX, cellY, CELL - 1, CELL - 1);
            if (hover) hoverIndex = index;
            Entry entry = filtered.get(index);
            // Keyed by the item, not the slot: scrolling under the cursor must not carry the
            // highlight onto whichever item has moved into view.
            float lit = motion.hover(entry.id().hashCode(), hover, frameDelta);
            graphics.fill(cellX, cellY, cellX + CELL - 2, cellY + CELL - 2,
                    UiMotion.mix(COLOR_CELL, COLOR_CELL_HOVER, lit));
            graphics.renderItem(new ItemStack(entry.item()), cellX + 1, cellY + 1);
        }
        graphics.disableScissor();

        graphics.renderOutline(gx, gy, gridW(), gridH(), 0xFF30383C);

        Component footer = Component.translatable(
                "gui.createcityexpansioncore.item_selector.count", filtered.size());
        graphics.drawString(minecraft.font, footer, x + PAD, y + h - 12, COLOR_MUTED, false);

        if (maxScroll() > 0) {
            Component scrollHint = Component.translatable("gui.createcityexpansioncore.item_selector.scroll");
            graphics.drawString(minecraft.font, scrollHint,
                    x + w - PAD - minecraft.font.width(scrollHint), y + h - 12, COLOR_MUTED, false);
        }

        if (hoverIndex >= 0 && hoverIndex < filtered.size()) {
            Entry entry = filtered.get(hoverIndex);
            List<Component> lines = new ArrayList<>(2);
            lines.add(Component.literal(entry.name()));
            if (entry.modName() != null) lines.add(Component.literal(entry.modName()));
            graphics.renderTooltip(minecraft.font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        // Перехватываем всё, чтобы не было click-through по экрану под оверлеем.
        if (search != null && search.mouseClicked(mouseX, mouseY, button)) return true;

        int closeX = x + w - 20;
        int closeY = y + 9;
        if (inside(mouseX, mouseY, closeX, closeY, 13, 13)) {
            close();
            return true;
        }

        int gx = gridX();
        int gy = gridY();
        if (inside(mouseX, mouseY, gx, gy, gridW(), gridH())) {
            int cols = columns();
            int cellX = (int) Math.floor(mouseX - gx);
            int cellY = (int) Math.floor(mouseY - gy);
            int column = cellX / CELL;
            int row = cellY / CELL;
            if (column >= 0 && column < cols && row >= 0 && row < rows()) {
                int index = scroll + row * cols + column;
                if (index >= 0 && index < filtered.size()) {
                    Entry entry = filtered.get(index);
                    Consumer<ResourceLocation> target = onSelect;
                    close();
                    if (target != null) target.accept(entry.id());
                    return true;
                }
            }
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!open) return true;
        int gx = gridX();
        int gy = gridY();
        if (inside(mouseX, mouseY, gx, gy, gridW(), gridH())) {
            int step = Math.max(1, rows() - 1);
            scroll += scrollY > 0 ? -step : step;
            scroll = Math.max(0, Math.min(maxScroll(), scroll));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        return search != null && search.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!open) return false;
        return search != null && search.charTyped(codePoint, modifiers);
    }

    private static boolean inside(double mouseX, double mouseY, int px, int py, int width, int height) {
        return mouseX >= px && mouseX < px + width && mouseY >= py && mouseY < py + height;
    }
}
