package com.example.examplemod.client.uiedit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Отрисовка редактора поверх открытого экрана: обводка виджетов, всплывающая
 * карточка элемента, нижняя панель с кнопками, строка текста и справка.
 *
 * <p>Стиль — тёмная индустриальная палитра с латунью, как у Logistics/Пилона:
 * инструмент выглядит частью мода, а не случайным отладочным слоем.
 *
 * <p>Геометрия панели ({@link #bar}) считается один раз и используется и здесь,
 * и в {@link UiEditEvents} для попадания кликами — рисование и обработка кликов
 * не могут разойтись.
 */
public final class UiEditOverlay {

    /** Высота нижней панели в координатах GUI. */
    static final int BAR_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int BAR_MARGIN = 6;

    private static final int COLOR_LINE = 0x99FFFFFF;
    private static final int COLOR_LINE_HOVER = 0xFFFFFFFF;
    private static final int COLOR_LINE_SELECTED = 0xFFE0B04A;
    private static final int COLOR_FILL_SELECTED = 0x30E0B04A;
    private static final int COLOR_BAR_BG = 0xD8141A1F;
    private static final int COLOR_BUTTON_BG = 0xFF20282E;
    private static final int COLOR_BUTTON_BG_HOVER = 0xFF2C373F;
    private static final int COLOR_BUTTON_BORDER = 0xFF3A464E;
    private static final int COLOR_PANEL_BG = 0xF012171B;
    private static final int COLOR_TEXT = 0xFFE8ECEF;
    private static final int COLOR_TEXT_DIM = 0xFF9AA6AD;
    private static final int COLOR_BRASS = 0xFFE0B04A;
    private static final int COLOR_DIRTY = 0xFFFF9A73;
    private static final int COLOR_TOOLTIP_BG = 0xF010151A;

    private UiEditOverlay() {
    }

    /** Кнопка нижней панели. */
    public record BarButton(String id, int x, int y, int w, int h) {

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** Кнопки панели справа от края; одна и та же геометрия для кликов и рендера. */
    public static List<BarButton> bar(Screen screen, Font font) {
        List<BarButton> out = new ArrayList<>();
        String[] ids = {"save", "revert", "clear", "export", "help"};
        String[] keys = {"ui_edit.save", "ui_edit.revert", "ui_edit.clear", "ui_edit.export", "ui_edit.help.button"};
        int y = screen.height - BAR_HEIGHT + 3;
        int x = screen.width - BAR_MARGIN;
        for (int i = ids.length - 1; i >= 0; i--) {
            int w = font.width(lang(keys[i])) + 14;
            x -= w;
            out.add(new BarButton(ids[i], x, y, w, BUTTON_HEIGHT));
            x -= BUTTON_GAP;
        }
        return out;
    }

    /** Кнопка под курсором или null. */
    public static BarButton buttonAt(Screen screen, Font font, double mouseX, double mouseY) {
        for (BarButton button : bar(screen, font)) {
            if (button.contains(mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }

    /** Клик внутри полосы панели (фон или кнопка). */
    public static boolean inBar(Screen screen, double mouseX, double mouseY) {
        return mouseY >= screen.height - BAR_HEIGHT;
    }

    public static void render(
            UiEditSession session,
            Screen screen,
            GuiGraphics graphics,
            Font font,
            int mouseX,
            int mouseY,
            float partialTick) {
        boolean interactive = !session.textMode() && !inBar(screen, mouseX, mouseY);
        GuiEventListener hovered = interactive ? session.widgetAt(screen, mouseX, mouseY) : null;
        // Призрак перетаскиваемого элемента и так под курсором — карточка не мешает.
        String hoveredElement = interactive && hovered == null && !session.draggingElement()
                ? session.elementAt(screen, mouseX, mouseY)
                : null;
        session.setHovered(hovered);
        session.setHoveredElement(hoveredElement);

        drawOutlines(session, screen, graphics);
        if (hovered != null) {
            drawTooltip(session, screen, graphics, font, hovered, mouseX, mouseY);
        } else if (hoveredElement != null) {
            drawElementTooltip(session, screen, graphics, font, hoveredElement, mouseX, mouseY);
        }

        drawBar(session, screen, graphics, font, mouseX, mouseY);
        if (session.textMode()) {
            drawTextBox(session, screen, graphics, font);
        }
        drawBadge(session, screen, graphics, font, countWidgets(screen));
        if (session.helpVisible()) {
            drawHelp(screen, graphics, font);
        }
    }

    /** Сколько на экране реально редактируемых виджетов (AbstractWidget в children). */
    private static int countWidgets(Screen screen) {
        int count = 0;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget && widget.visible) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Обводка виджетов
    // ------------------------------------------------------------------

    private static void drawOutlines(UiEditSession session, Screen screen, GuiGraphics graphics) {
        String screenName = UiEditSession.className(screen);
        List<UiEditModel.Bounds> widgetRects = new ArrayList<>();
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget && widget.visible) {
                UiEditModel.Bounds b = UiEditLayout.toScreen(screenName, new UiEditModel.Bounds(
                        widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
                widgetRects.add(b);
                paint(graphics, b,
                        listener == session.selected(),
                        listener == session.hovered());
            }
        }
        // Элементы layout ручных экранов (Пилон, Профиль, Соцсети...): живут
        // отдельно от children(), поэтому обводятся по своему реестру.
        for (String name : UiEditLayout.registeredNames(screenName)) {
            UiEditModel.Bounds b;
            if (name.equals(session.selectedElement()) && session.elementGhost() != null) {
                b = session.elementGhost();
            } else {
                b = session.appliedElement(screen, name);
            }
            // Layout-прямоугольник под своим же виджетом не дублируем.
            if (b == null || duplicatesWidget(b, widgetRects)) {
                continue;
            }
            paint(graphics, b,
                    name.equals(session.selectedElement()),
                    name.equals(session.hoveredElement()));
        }
    }

    private static void paint(
            GuiGraphics graphics, UiEditModel.Bounds b,
            boolean selected, boolean hovered) {
        int color;
        int fill = 0;
        if (selected) {
            color = COLOR_LINE_SELECTED;
            fill = COLOR_FILL_SELECTED;
        } else if (hovered) {
            color = COLOR_LINE_HOVER;
        } else {
            color = COLOR_LINE;
        }
        if (fill != 0) {
            graphics.fill(b.x(), b.y(), b.right(), b.bottom(), fill);
        }
        outline(graphics, b, color);
    }

    private static boolean duplicatesWidget(UiEditModel.Bounds b, List<UiEditModel.Bounds> widgetRects) {
        for (UiEditModel.Bounds w : widgetRects) {
            if (Math.abs(w.x() - b.x()) <= 2 && Math.abs(w.y() - b.y()) <= 2
                    && Math.abs(w.w() - b.w()) <= 2 && Math.abs(w.h() - b.h()) <= 2) {
                return true;
            }
        }
        return false;
    }

    private static void outline(GuiGraphics graphics, UiEditModel.Bounds b, int color) {
        graphics.fill(b.x(), b.y(), b.right(), b.y() + 1, color);
        graphics.fill(b.x(), b.bottom() - 1, b.right(), b.bottom(), color);
        graphics.fill(b.x(), b.y(), b.x() + 1, b.bottom(), color);
        graphics.fill(b.right() - 1, b.y(), b.right(), b.bottom(), color);
    }

    // ------------------------------------------------------------------
    // Карточка элемента под курсором
    // ------------------------------------------------------------------

    private static void drawTooltip(
            UiEditSession session,
            Screen screen,
            GuiGraphics graphics,
            Font font,
            GuiEventListener hovered,
            int mouseX,
            int mouseY) {
        if (!(hovered instanceof AbstractWidget widget)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(UiEditSession.className(hovered));
        String screenName = UiEditSession.className(screen);
        UiEditModel.Bounds bounds = UiEditLayout.toScreen(screenName, new UiEditModel.Bounds(
                widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        lines.add(bounds.x() + "," + bounds.y() + "  "
                + bounds.w() + "\u00d7" + bounds.h());
        UiEditModel.WidgetOverride override = session.overrideOf(screen, hovered);
        if (override != null) {
            lines.add("сдвиг " + fmt(UiEditLayout.toScreenDelta(screenName, override.dx()))
                    + "," + fmt(UiEditLayout.toScreenDelta(screenName, override.dy()))
                    + "  размер " + fmt(UiEditLayout.toScreenDelta(screenName, override.dw()))
                    + "," + fmt(UiEditLayout.toScreenDelta(screenName, override.dh())));
        }
        String text = widget.getMessage().getString();
        if (!text.isEmpty()) {
            lines.add(truncate(text, 48));
        }
        drawCard(graphics, font, lines, screen, mouseX, mouseY);
    }

    /** Карточка элемента layout: имя, экранные границы, дельта. */
    private static void drawElementTooltip(
            UiEditSession session,
            Screen screen,
            GuiGraphics graphics,
            Font font,
            String name,
            int mouseX,
            int mouseY) {
        UiEditModel.Bounds b = session.appliedElement(screen, name);
        if (b == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(name);
        lines.add(b.x() + "," + b.y() + "  " + b.w() + "\u00d7" + b.h());
        UiEditModel.WidgetOverride override = session.overrideOfElement(name);
        if (override != null) {
            String screenName = UiEditSession.className(screen);
            lines.add("сдвиг " + fmt(UiEditLayout.elementToScreenDelta(screenName, name, override.dx()))
                    + "," + fmt(UiEditLayout.elementToScreenDelta(screenName, name, override.dy()))
                    + "  размер " + fmt(UiEditLayout.elementToScreenDelta(screenName, name, override.dw()))
                    + "," + fmt(UiEditLayout.elementToScreenDelta(screenName, name, override.dh())));
        }
        drawCard(graphics, font, lines, screen, mouseX, mouseY);
    }

    private static void drawCard(
            GuiGraphics graphics, Font font, List<String> lines, Screen screen, int mouseX, int mouseY) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        width += 10;
        int height = lines.size() * 10 + 6;
        int x = Math.min(mouseX + 10, screen.width - width - 2);
        int y = Math.min(mouseY + 10, screen.height - height - BAR_HEIGHT - 2);
        graphics.fill(x, y, x + width, y + height, COLOR_TOOLTIP_BG);
        int textY = y + 4;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 5, textY,
                    i == 0 ? COLOR_BRASS : COLOR_TEXT, true);
            textY += 10;
        }
    }

    // ------------------------------------------------------------------
    // Нижняя панель
    // ------------------------------------------------------------------

    private static void drawBar(
            UiEditSession session,
            Screen screen,
            GuiGraphics graphics,
            Font font,
            double mouseX,
            double mouseY) {
        int top = screen.height - BAR_HEIGHT;
        graphics.fill(0, top, screen.width, screen.height, COLOR_BAR_BG);
        graphics.fill(0, top, screen.width, top + 1, COLOR_BUTTON_BORDER);

        String info = infoLine(session, screen, font);
        graphics.drawString(font, info, BAR_MARGIN, screen.height - 16,
                session.dirty() ? COLOR_DIRTY : COLOR_TEXT_DIM, false);

        for (BarButton button : bar(screen, font)) {
            boolean hot = button.contains(mouseX, mouseY);
            graphics.fill(button.x() - 1, button.y() - 1,
                    button.x() + button.w() + 1, button.y() + button.h() + 1,
                    hot ? COLOR_BRASS : COLOR_BUTTON_BORDER);
            graphics.fill(button.x(), button.y(), button.x() + button.w(), button.y() + button.h(),
                    hot ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG);
            String label = lang(switch (button.id()) {
                case "save" -> "ui_edit.save";
                case "revert" -> "ui_edit.revert";
                case "clear" -> "ui_edit.clear";
                case "export" -> "ui_edit.export";
                default -> "ui_edit.help.button";
            });
            graphics.drawCenteredString(font, label,
                    button.x() + button.w() / 2, button.y() + 4, COLOR_TEXT);
        }
    }

    private static String infoLine(UiEditSession session, Screen screen, Font font) {
        GuiEventListener selected = session.selected();
        String element = session.selectedElement();
        if (selected == null && element == null) {
            return lang("ui_edit.info.none");
        }
        String screenName = UiEditSession.className(screen);
        if (element != null) {
            UiEditModel.Bounds bounds = session.selectedElementBounds();
            if (bounds == null) {
                return element;
            }
            UiEditModel.WidgetOverride override = session.overrideOfElement(element);
            return infoFor(element, bounds,
                    override == null ? 0 : UiEditLayout.elementToScreenDelta(screenName, element, override.dx()),
                    override == null ? 0 : UiEditLayout.elementToScreenDelta(screenName, element, override.dy()),
                    screen, font);
        }
        if (selected instanceof AbstractWidget widget) {
            UiEditModel.Bounds bounds = UiEditLayout.toScreen(screenName, new UiEditModel.Bounds(
                    widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
            UiEditModel.WidgetOverride override = session.overrideOf(screen, selected);
            return infoFor(UiEditSession.className(selected), bounds,
                    override == null ? 0 : UiEditLayout.toScreenDelta(screenName, override.dx()),
                    override == null ? 0 : UiEditLayout.toScreenDelta(screenName, override.dy()),
                    screen, font);
        }
        return UiEditSession.className(selected);
    }

    /** Одна строка состояния для виджета и элемента layout: имя, границы, сдвиг. */
    private static String infoFor(
            String name, UiEditModel.Bounds bounds, int dx, int dy, Screen screen, Font font) {
        String full = Component.translatable(
                "ui_edit.info.selected",
                name,
                bounds.x(), bounds.y(),
                bounds.w(), bounds.h(),
                dx, dy).getString();
        return font.width(full) > screen.width - barWidth(screen, font) - BAR_MARGIN * 2
                ? Component.translatable("ui_edit.info.selected.short", name, dx, dy).getString()
                : full;
    }

    private static int barWidth(Screen screen, Font font) {
        int width = 0;
        for (BarButton ignored : bar(screen, font)) {
            width += ignored.w() + BUTTON_GAP;
        }
        return width;
    }

    // ------------------------------------------------------------------
    // Строка текста, бейдж, справка
    // ------------------------------------------------------------------

    private static void drawTextBox(UiEditSession session, Screen screen, GuiGraphics graphics, Font font) {
        var box = session.textBox();
        if (box == null) {
            return;
        }
        int boxWidth = Math.min(320, Math.max(80, screen.width - 220));
        int x = BAR_MARGIN;
        int y = screen.height - BAR_HEIGHT - 22;
        box.setX(x);
        box.setY(y);
        box.setWidth(boxWidth);
        box.setHeight(16);
        box.render(graphics, -1, -1, 1.0f);
        graphics.drawString(font, lang("ui_edit.text.hint"),
                x + boxWidth + 8, y + 4, COLOR_TEXT_DIM, false);
    }

    private static void drawBadge(
            UiEditSession session, Screen screen, GuiGraphics graphics, Font font, int widgetCount) {
        String badge = lang("ui_edit.badge")
                + (session.dirty() ? "*" : "")
                + " \u00b7 F7 \u00b7 H";
        int width = font.width(badge) + 10;
        int height = 12;
        // Ручная отрисовка: говорим прямо, сколько элементов доступно для правки.
        String manual = widgetCount == 0 ? manualHint(screen) : null;
        if (manual != null) {
            width = Math.max(width, font.width(manual) + 10);
            height = 24;
        }
        graphics.fill(4, 4, 4 + width, 4 + height, COLOR_PANEL_BG);
        graphics.drawString(font, badge, 9, 6, session.dirty() ? COLOR_DIRTY : COLOR_BRASS, false);
        if (manual != null) {
            graphics.drawString(font, manual, 9, 16, COLOR_TEXT_DIM, false);
        }
    }

    /** Нет виджетов: сколько элементов layout подключено или что правки пока нет. */
    private static String manualHint(Screen screen) {
        int elements = UiEditLayout.registeredNames(UiEditSession.className(screen)).size();
        return elements == 0
                ? lang("ui_edit.badge.manual")
                : lang("ui_edit.badge.layout", elements);
    }

    private static void drawHelp(Screen screen, GuiGraphics graphics, Font font) {
        String title = lang("ui_edit.help.title");
        String[] keys = {
                "ui_edit.help.1", "ui_edit.help.2", "ui_edit.help.3", "ui_edit.help.4",
                "ui_edit.help.5", "ui_edit.help.6", "ui_edit.help.7", "ui_edit.help.8"
        };
        int width = Math.min(screen.width - 20, 360);
        int height = 18 + keys.length * 11 + 8;
        int x = (screen.width - width) / 2;
        int y = (screen.height - BAR_HEIGHT - height) / 2;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, COLOR_BRASS);
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL_BG);
        graphics.drawCenteredString(font, title, x + width / 2, y + 5, COLOR_BRASS);
        int lineY = y + 20;
        for (String key : keys) {
            graphics.drawString(font, lang(key), x + 8, lineY, COLOR_TEXT, false);
            lineY += 11;
        }
    }

    // ------------------------------------------------------------------

    private static String lang(String key) {
        return Component.translatable(key).getString();
    }

    private static String lang(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fmt(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "\u2026";
    }
}
