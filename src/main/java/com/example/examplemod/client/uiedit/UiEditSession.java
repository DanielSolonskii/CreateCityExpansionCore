package com.example.examplemod.client.uiedit;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Состояние редактора UI: текущий экран, исходные позиции, выбор, перетаскивание,
 * текстовое поле и рабочий набор правок.
 *
 * <p><b>Жизненный цикл.</b> {@link #attach} вызывается на каждом открытии экрана
 * (событие Init.Post): сначала запоминаются исходные значения виджетов, потом
 * применяются сохранённые правки из JSON. Поэтому дельты всегда считаются от
 * ванильной раскладки, а не от уже сдвинутой — перекладка экрана под другое
 * разрешение не «умножает» сдвиг.
 *
 * <p><b>Изменения живут в памяти</b> до кнопки «Сохранить» (или автосохранения
 * при закрытии экрана). «Откатить» возвращает экран к записанному на диске
 * состоянию, «Сбросить» — к ванильному, но тоже только после сохранения.
 */
public final class UiEditSession {

    /** Исходное состояние одного виджета: геометрия, компонента и её текст. */
    private record Orig(UiEditModel.Bounds bounds, Component message, String text) {
    }

    private static UiEditSession instance;

    public static UiEditSession get() {
        if (instance == null) {
            instance = new UiEditSession();
        }
        return instance;
    }

    private UiEditStore store;
    private Screen screen;
    private UiEditModel.ScreenOverrides working;

    /** Исходные значения по идентичности экземпляра: пересозданный виджет — новый оригинал. */
    private final IdentityHashMap<GuiEventListener, Orig> originals = new IdentityHashMap<>();

    private GuiEventListener selected;
    private GuiEventListener hovered;

    private GuiEventListener dragging;
    private boolean resizeDrag;
    private double grabX;
    private double grabY;

    private long lastClickMillis;
    private GuiEventListener lastClickTarget;

    private boolean active;
    private boolean dirty;
    private boolean helpVisible;
    private boolean textMode;
    private GuiEventListener textTarget;
    private EditBox textBox;

    private UiEditSession() {
    }

    // ------------------------------------------------------------------
    // Доступ
    // ------------------------------------------------------------------

    public boolean active() {
        return active;
    }

    public Screen screen() {
        return screen;
    }

    public boolean dirty() {
        return dirty;
    }

    public boolean helpVisible() {
        return helpVisible;
    }

    public boolean textMode() {
        return textMode;
    }

    public boolean dragging() {
        return dragging != null;
    }

    public GuiEventListener selected() {
        return selected;
    }

    public GuiEventListener hovered() {
        return hovered;
    }

    public EditBox textBox() {
        return textBox;
    }

    public void setActive(boolean value) {
        active = value;
        if (!value) {
            dragging = null;
            stopTextMode();
        }
    }

    public void toggleHelp() {
        helpVisible = !helpVisible;
    }

    public void setHovered(GuiEventListener listener) {
        hovered = listener;
    }

    public boolean matches(Screen target) {
        return target != null && target == screen;
    }

    // ------------------------------------------------------------------
    // Привязка к экрану
    // ------------------------------------------------------------------

    /**
     * Привязка сессии к экрану: запомнить исходные значения и применить сохранённые
     * правки. {@code freshInit == true} — вызов из Init.Post, списки виджетов новые.
     */
    void attach(Screen target, List<? extends GuiEventListener> listeners, boolean freshInit) {
        ensureStore();
        boolean changed = target != screen;
        if (changed || working == null) {
            screen = target;
            working = store.load(className(target));
            dirty = false;
        }
        if (freshInit || changed) {
            originals.clear();
            selected = null;
            hovered = null;
            dragging = null;
            stopTextMode();
        }
        int index = 0;
        for (GuiEventListener listener : listeners) {
            if (listener instanceof AbstractWidget widget) {
                rememberOriginal(listener, widget);
                String key = UiEditModel.widgetKey(index, className(listener));
                UiEditModel.WidgetOverride override = working.get(key);
                if (override != null) {
                    apply(widget, override, originals.get(listener));
                }
            }
            index++;
        }
    }

    /** Переключение режима. Включение подстраховывается привязкой на случай, если её не было. */
    public void toggle() {
        if (active) {
            setActive(false);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            return;
        }
        attach(minecraft.screen, minecraft.screen.children(), false);
        active = true;
    }

    // ------------------------------------------------------------------
    // Поиск и выбор
    // ------------------------------------------------------------------

    /** Виджет под курсором; последний в списке children — верхний. */
    GuiEventListener widgetAt(Screen target, double mouseX, double mouseY) {
        List<? extends GuiEventListener> children = target.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiEventListener listener = children.get(i);
            if (listener instanceof AbstractWidget widget
                    && widget.visible
                    && boundsOf(widget).contains(mouseX, mouseY)) {
                return listener;
            }
        }
        return null;
    }

    public void select(GuiEventListener listener) {
        selected = listener;
    }

    /**
     * Отметка двойного клика. Возвращает true, если открылось редактирование
     * текста — начинать в этот же момент перетаскивание не нужно.
     */
    public boolean registerClick(Screen target, GuiEventListener listener) {
        long now = Util.getMillis();
        boolean doubleClick = lastClickTarget == listener && now - lastClickMillis <= 400L;
        lastClickMillis = now;
        lastClickTarget = listener;
        if (doubleClick) {
            lastClickTarget = null;
            startTextMode(target);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Перетаскивание и размер
    // ------------------------------------------------------------------

    public void beginDrag(Screen target, GuiEventListener listener, double mouseX, double mouseY, boolean resize) {
        if (!(listener instanceof AbstractWidget widget)) {
            return;
        }
        rememberOriginal(listener, widget);
        dragging = listener;
        resizeDrag = resize;
        grabX = mouseX - widget.getX();
        grabY = mouseY - widget.getY();
    }

    public void drag(Screen target, double mouseX, double mouseY) {
        if (!(dragging instanceof AbstractWidget widget)) {
            return;
        }
        if (resizeDrag) {
            widget.setWidth(clamp((int) Math.round(mouseX - widget.getX()), UiEditModel.MIN_SIZE, 4000));
            widget.setHeight(clamp((int) Math.round(mouseY - widget.getY()), UiEditModel.MIN_SIZE, 4000));
        } else {
            widget.setX(clamp((int) Math.round(mouseX - grabX), 16 - widget.getWidth(), target.width - 16));
            widget.setY(clamp((int) Math.round(mouseY - grabY), 16 - widget.getHeight(), target.height - 16));
        }
        updateOverride(target, widget);
    }

    public void endDrag() {
        dragging = null;
    }

    /** Сдвиг выбранного виджета стрелками: 1 px, с Shift — 10 px. */
    public void nudgeSelected(Screen target, int dx, int dy) {
        if (!(selected instanceof AbstractWidget widget)) {
            return;
        }
        rememberOriginal(selected, widget);
        widget.setX(clamp(widget.getX() + dx, 16 - widget.getWidth(), target.width - 16));
        widget.setY(clamp(widget.getY() + dy, 16 - widget.getHeight(), target.height - 16));
        updateOverride(target, widget);
    }

    // ------------------------------------------------------------------
    // Текст
    // ------------------------------------------------------------------

    public void startTextMode(Screen target) {
        if (!(selected instanceof AbstractWidget widget)) {
            return;
        }
        rememberOriginal(selected, widget);
        textTarget = widget;
        textMode = true;
        if (textBox == null) {
            textBox = new EditBox(Minecraft.getInstance().font, 0, 0, 10, 16, Component.literal(""));
            textBox.setMaxLength(512);
        }
        textBox.setValue(textOf(widget));
        textBox.setFocused(true);
    }

    /** Применить текст из поля к виджету. */
    public void applyText() {
        if (!textMode) {
            return;
        }
        if (textTarget instanceof AbstractWidget widget && textBox != null) {
            widget.setMessage(Component.literal(textBox.getValue()));
            updateOverride(screen, widget);
        }
        stopTextMode();
    }

    /** Отменить правку текста: виджет остаётся с прежним текстом. */
    public void cancelText() {
        stopTextMode();
    }

    private void stopTextMode() {
        textMode = false;
        textTarget = null;
        if (textBox != null) {
            textBox.setFocused(false);
        }
    }

    // ------------------------------------------------------------------
    // Правки: сброс, сохранение, экспорт
    // ------------------------------------------------------------------

    /** Сброс одного выбранного виджета к ванильному состоянию. */
    public void resetSelected(Screen target) {
        if (!(selected instanceof AbstractWidget widget)) {
            return;
        }
        int index = indexOf(target, widget);
        if (index < 0) {
            return;
        }
        working.remove(UiEditModel.widgetKey(index, className(widget)));
        dirty = true;
        Orig original = originals.get(widget);
        if (original != null) {
            restore(widget, original);
        }
    }

    /** Сохранить рабочий набор в JSON. Ложь — файла не записано. */
    public boolean save() {
        if (working == null || working.broken()) {
            return false;
        }
        try {
            store.save(working);
            dirty = false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Откат к записанному на диске состоянию. */
    public void revert() {
        if (screen == null) {
            return;
        }
        working = store.load(className(screen));
        dirty = false;
        selected = null;
        stopTextMode();
        restoreAll();
    }

    /** Очистить все правки экрана (в памяти; запись — кнопкой «Сохранить»). */
    public void clearAll() {
        if (working == null) {
            return;
        }
        working.clear();
        dirty = true;
        stopTextMode();
        restoreAll();
    }

    /** Автосохранение при закрытии экрана: не теряем наработанное. */
    public void autoSaveOnClose() {
        if (dirty) {
            save();
        }
    }

    /**
     * Экспорт правок текстом: копия в буфер обмена плюс файл в ui-edit/export.
     *
     * @return путь записанного файла
     */
    public String exportTo(Screen target, int guiWidth, int guiHeight) throws IOException {
        List<UiEditModel.Original> originalBounds = new ArrayList<>();
        List<UiEditModel.Bounds> currentBounds = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        for (GuiEventListener listener : target.children()) {
            classNames.add(className(listener));
            Orig original = originals.get(listener);
            if (listener instanceof AbstractWidget widget) {
                originalBounds.add(original == null
                        ? new UiEditModel.Original(boundsOf(widget), textOf(widget))
                        : new UiEditModel.Original(original.bounds(), original.text()));
                currentBounds.add(boundsOf(widget));
            } else {
                originalBounds.add(null);
                currentBounds.add(null);
            }
        }
        List<UiEditStore.ExportRow> rows = UiEditStore.rowsFor(working, originalBounds, currentBounds, classNames);
        String content = UiEditStore.exportText(
                className(target),
                guiWidth + "\u00d7" + guiHeight,
                rows);
        Path written = store.writeExport(className(target), content);
        Minecraft.getInstance().keyboardHandler.setClipboard(content);
        return written.toString();
    }

    // ------------------------------------------------------------------
    // Внутреннее
    // ------------------------------------------------------------------

    private void ensureStore() {
        if (store == null) {
            store = new UiEditStore(FMLPaths.CONFIGDIR.get()
                    .resolve("createcityexpansioncore")
                    .resolve("ui-edit"));
        }
    }

    private void rememberOriginal(GuiEventListener listener, AbstractWidget widget) {
        if (!originals.containsKey(listener)) {
            Component message = widget.getMessage();
            originals.put(listener, new Orig(boundsOf(widget), message, message.getString()));
        }
    }

    private void updateOverride(Screen target, AbstractWidget widget) {
        int index = indexOf(target, widget);
        if (index < 0) {
            return;
        }
        rememberOriginal(widget, widget);
        Orig original = originals.get(widget);
        String key = UiEditModel.widgetKey(index, className(widget));
        working.put(key, UiEditModel.delta(
                original.bounds(),
                boundsOf(widget),
                original.text(),
                textOf(widget)));
        dirty = true;
    }

    /** Применить правку к виджету относительно его исходного состояния. */
    private void apply(AbstractWidget widget, UiEditModel.WidgetOverride override, Orig original) {
        if (original == null) {
            return;
        }
        UiEditModel.Bounds bounds = override.applyTo(original.bounds());
        widget.setX(bounds.x());
        widget.setY(bounds.y());
        widget.setWidth(bounds.w());
        widget.setHeight(bounds.h());
        if (override.hasText()) {
            widget.setMessage(Component.literal(override.text()));
        } else {
            widget.setMessage(original.message());
        }
    }

    private void restore(AbstractWidget widget, Orig original) {
        widget.setX(original.bounds().x());
        widget.setY(original.bounds().y());
        widget.setWidth(original.bounds().w());
        widget.setHeight(original.bounds().h());
        widget.setMessage(original.message());
    }

    /** Вернуть все виджеты к состоянию, соответствующему текущему набору правок. */
    private void restoreAll() {
        if (screen == null) {
            return;
        }
        int index = 0;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget) {
                Orig original = originals.get(listener);
                if (original != null) {
                    UiEditModel.WidgetOverride override =
                            working.get(UiEditModel.widgetKey(index, className(listener)));
                    if (override != null) {
                        apply(widget, override, original);
                    } else {
                        restore(widget, original);
                    }
                }
            }
            index++;
        }
    }

    private int indexOf(Screen target, GuiEventListener listener) {
        List<? extends GuiEventListener> children = target.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == listener) {
                return i;
            }
        }
        return -1;
    }

    /** Правка этого виджета в рабочем наборе или null, если её нет. */
    UiEditModel.WidgetOverride overrideOf(Screen target, GuiEventListener listener) {
        if (working == null) {
            return null;
        }
        int index = indexOf(target, listener);
        if (index < 0) {
            return null;
        }
        return working.get(UiEditModel.widgetKey(index, className(listener)));
    }

    private static UiEditModel.Bounds boundsOf(AbstractWidget widget) {
        return new UiEditModel.Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    private static String textOf(AbstractWidget widget) {
        return widget.getMessage().getString();
    }

    /** Имя ключа: simple name, а для анонимных классов — фрагмент полного имени. */
    static String className(Object object) {
        String simple = object.getClass().getSimpleName();
        if (!simple.isEmpty()) {
            return simple;
        }
        String name = object.getClass().getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
