package com.example.examplemod.client.uiedit;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

    /** Выбранный/подсвечиваемый элемент layout (ручные экраны, взаимоисключимо с selected). */
    private String selectedElement;
    private String hoveredElement;
    private String draggingElement;
    /** Экранные границы, куда элемент встанет после коммита (пока тянется). */
    private UiEditModel.Bounds elementGhost;
    private double elementGrabX;
    private double elementGrabY;
    private int elementAnchorX;
    private int elementAnchorY;
    private boolean elementResize;
    private boolean elementMoved;

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

    public String selectedElement() {
        return selectedElement;
    }

    public String hoveredElement() {
        return hoveredElement;
    }

    public void setHoveredElement(String name) {
        hoveredElement = name;
    }

    public boolean draggingElement() {
        return draggingElement != null;
    }

    /** Экранные границы перетаскиваемого элемента (призрак до коммита). */
    public UiEditModel.Bounds elementGhost() {
        return elementGhost;
    }

    public EditBox textBox() {
        return textBox;
    }

    public void setActive(boolean value) {
        active = value;
        if (!value) {
            dragging = null;
            draggingElement = null;
            elementGhost = null;
            selectedElement = null;
            hoveredElement = null;
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
            // Тот же объект, что у хука UiEditLayout: элементы и виджети одного
            // файла правок, автосохранение и «Откатить» видят обе половины.
            working = UiEditLayout.overridesFor(className(target));
            dirty = false;
        }
        if (freshInit || changed) {
            originals.clear();
            selected = null;
            hovered = null;
            dragging = null;
            hoveredElement = null;
            draggingElement = null;
            elementGhost = null;
            stopTextMode();
            if (changed) {
                // Выбор элемента — имя, а не экземпляр: он переживает пересборку
                // экрана (resize → Init), поэтому стрелками можно жать серию.
                selectedElement = null;
            }
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
        String screenName = className(target);
        List<? extends GuiEventListener> children = target.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            GuiEventListener listener = children.get(i);
            if (listener instanceof AbstractWidget widget
                    && widget.visible
                    && UiEditLayout.toScreen(screenName, boundsOf(widget)).contains(mouseX, mouseY)) {
                return listener;
            }
        }
        return null;
    }

    /**
     * Элемент layout под курсором или null. Виджеты приоритетнее: если курсор на
     * виджете, лежащем внутри layout-прямоугольника, выбирается виджет.
     */
    String elementAt(Screen target, double mouseX, double mouseY) {
        List<String> names = UiEditLayout.registeredNames(className(target));
        for (int i = names.size() - 1; i >= 0; i--) {
            String name = names.get(i);
            UiEditModel.Bounds b = appliedElement(target, name);
            if (b != null && b.contains(mouseX, mouseY)) {
                return name;
            }
        }
        return null;
    }

    public void select(GuiEventListener listener) {
        selected = listener;
        if (listener != null) {
            selectedElement = null;
        }
    }

    /** Выбор элемента layout; взаимоисключимо с выбором виджета. */
    public void selectElement(String name) {
        if (name == null) {
            selectedElement = null;
            return;
        }
        selectedElement = name;
        selected = null;
    }

    /** Экранные границы элемента (с учётом сохранённой правки) или null. */
    UiEditModel.Bounds appliedElement(Screen target, String name) {
        UiEditModel.Bounds raw = UiEditLayout.registeredFor(className(target)).get(name);
        if (raw == null) {
            return null;
        }
        UiEditModel.WidgetOverride override = working == null ? null : working.getElement(name);
        UiEditModel.Bounds local = override == null ? raw : override.applyTo(raw);
        return UiEditLayout.toScreen(className(target), local);
    }

    /** Границы выбранного элемента для строки состояния: призрак, если тянется. */
    UiEditModel.Bounds selectedElementBounds() {
        if (selectedElement == null) {
            return null;
        }
        if (elementGhost != null) {
            return elementGhost;
        }
        return screen == null ? null : appliedElement(screen, selectedElement);
    }

    /** Правка элемента в рабочем наборе или null. */
    UiEditModel.WidgetOverride overrideOfElement(String name) {
        return working == null ? null : working.getElement(name);
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
        UiEditModel.Bounds ws = UiEditLayout.toScreen(className(target), boundsOf(widget));
        grabX = mouseX - ws.x();
        grabY = mouseY - ws.y();
    }

    public void drag(Screen target, double mouseX, double mouseY) {
        if (!(dragging instanceof AbstractWidget widget)) {
            return;
        }
        String screenName = className(target);
        UiEditModel.Bounds ws = UiEditLayout.toScreen(screenName, boundsOf(widget));
        if (resizeDrag) {
            int w = clamp((int) Math.round(mouseX - ws.x()), UiEditModel.MIN_SIZE, 4000);
            int h = clamp((int) Math.round(mouseY - ws.y()), UiEditModel.MIN_SIZE, 4000);
            widget.setWidth(clamp(widget.getWidth()
                    + UiEditLayout.toLayoutDelta(screenName, w - ws.w()), UiEditModel.MIN_SIZE, 4000));
            widget.setHeight(clamp(widget.getHeight()
                    + UiEditLayout.toLayoutDelta(screenName, h - ws.h()), UiEditModel.MIN_SIZE, 4000));
        } else {
            int x = clamp((int) Math.round(mouseX - grabX), 16 - ws.w(), target.width - 16);
            int y = clamp((int) Math.round(mouseY - grabY), 16 - ws.h(), target.height - 16);
            widget.setX(widget.getX() + UiEditLayout.toLayoutDelta(screenName, x - ws.x()));
            widget.setY(widget.getY() + UiEditLayout.toLayoutDelta(screenName, y - ws.y()));
        }
        updateOverride(target, widget);
    }

    public void endDrag() {
        dragging = null;
    }

    /** Сдвиг выбранного виджета или элемента стрелками: 1 px, с Shift — 10 px. */
    public void nudgeSelected(Screen target, int dx, int dy) {
        if (selectedElement != null) {
            nudgeElement(target, dx, dy);
            return;
        }
        if (!(selected instanceof AbstractWidget widget)) {
            return;
        }
        rememberOriginal(selected, widget);
        String screenName = className(target);
        UiEditModel.Bounds ws = UiEditLayout.toScreen(screenName, boundsOf(widget));
        int x = clamp(ws.x() + dx, 16 - ws.w(), target.width - 16);
        int y = clamp(ws.y() + dy, 16 - ws.h(), target.height - 16);
        widget.setX(widget.getX() + UiEditLayout.toLayoutDelta(screenName, x - ws.x()));
        widget.setY(widget.getY() + UiEditLayout.toLayoutDelta(screenName, y - ws.y()));
        updateOverride(target, widget);
    }

    // ------------------------------------------------------------------
    // Перетаскивание элементов layout (ручные экраны)
    // ------------------------------------------------------------------

    public void beginElementDrag(Screen target, String name, double mouseX, double mouseY, boolean resize) {
        UiEditModel.Bounds current = appliedElement(target, name);
        if (current == null) {
            return;
        }
        draggingElement = name;
        elementResize = resize;
        elementMoved = false;
        elementAnchorX = current.x();
        elementAnchorY = current.y();
        elementGrabX = mouseX - current.x();
        elementGrabY = mouseY - current.y();
        elementGhost = current;
    }

    public void dragElement(Screen target, double mouseX, double mouseY) {
        if (draggingElement == null || elementGhost == null) {
            return;
        }
        UiEditModel.Bounds cur = elementGhost;
        UiEditModel.Bounds next;
        if (elementResize) {
            int w = clamp((int) Math.round(mouseX - elementAnchorX), UiEditModel.MIN_SIZE, 4000);
            int h = clamp((int) Math.round(mouseY - elementAnchorY), UiEditModel.MIN_SIZE, 4000);
            next = new UiEditModel.Bounds(elementAnchorX, elementAnchorY, w, h);
        } else {
            int x = clamp((int) Math.round(mouseX - elementGrabX), 16 - cur.w(), target.width - 16);
            int y = clamp((int) Math.round(mouseY - elementGrabY), 16 - cur.h(), target.height - 16);
            next = new UiEditModel.Bounds(x, y, cur.w(), cur.h());
        }
        if (!next.equals(cur)) {
            elementGhost = next;
            elementMoved = true;
        }
    }

    /**
     * Отпустили мышь: правка фиксируется, экран пересобирается — layout-класс
     * пересчитает геометрию уже с новой дельтой (Init.Pre/Post от resize идут
     * ровно один раз, выбор элемента сохраняется).
     */
    public void endElementDrag(Screen target) {
        if (draggingElement == null) {
            return;
        }
        String name = draggingElement;
        UiEditModel.Bounds ghost = elementGhost;
        boolean moved = elementMoved;
        draggingElement = null;
        elementGhost = null;
        elementMoved = false;
        if (moved && ghost != null) {
            commitElement(target, name, ghost);
            rebuild(target);
        }
    }

    private void nudgeElement(Screen target, int dx, int dy) {
        UiEditModel.Bounds current = appliedElement(target, selectedElement);
        if (current == null) {
            return;
        }
        int x = clamp(current.x() + dx, 16 - current.w(), target.width - 16);
        int y = clamp(current.y() + dy, 16 - current.h(), target.height - 16);
        if (x == current.x() && y == current.y()) {
            return;
        }
        commitElement(target, selectedElement, new UiEditModel.Bounds(x, y, current.w(), current.h()));
        rebuild(target);
    }

    /** Записать правку элемента: экранный призрак → логические дельты. */
    private void commitElement(Screen target, String name, UiEditModel.Bounds screenBounds) {
        if (working == null) {
            return;
        }
        String screenName = className(target);
        UiEditModel.Bounds raw = UiEditLayout.registeredFor(screenName).get(name);
        if (raw == null) {
            return;
        }
        UiEditModel.Bounds local = UiEditLayout.toLayout(screenName, screenBounds);
        working.putElement(name, UiEditModel.delta(raw, local, null, null));
        dirty = true;
    }

    /** Пересборка экрана: resize → rebuildWidgets → Init.Pre/Post → attach. */
    private void rebuild(Screen target) {
        if (target == null) {
            return;
        }
        target.resize(Minecraft.getInstance(), target.width, target.height);
    }

    /** Есть ли на экране элементы layout — пересборка нужна только им. */
    private boolean hasLayoutElements() {
        if (screen == null) {
            return false;
        }
        return !UiEditLayout.registeredFor(className(screen)).isEmpty()
                || (working != null && !working.elements().isEmpty());
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

    /** Сброс одного выбранного виджета или элемента layout к ванильному состоянию. */
    public void resetSelected(Screen target) {
        if (selectedElement != null) {
            if (working != null && working.getElement(selectedElement) != null) {
                working.removeElement(selectedElement);
                dirty = true;
                stopTextMode();
                rebuild(target);
            }
            return;
        }
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
        boolean rebuildNeeded = hasLayoutElements();
        // reload меняет общий объект: хук UiEditLayout видит откат сразу же.
        working = UiEditLayout.reload(className(screen));
        dirty = false;
        selected = null;
        selectedElement = null;
        stopTextMode();
        restoreAll();
        if (rebuildNeeded) {
            rebuild(screen);
        }
    }

    /** Очистить все правки экрана (в памяти; запись — кнопкой «Сохранить»). */
    public void clearAll() {
        if (working == null) {
            return;
        }
        boolean rebuildNeeded = hasLayoutElements();
        working.clear();
        dirty = true;
        stopTextMode();
        restoreAll();
        if (rebuildNeeded) {
            rebuild(screen);
        }
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
        List<UiEditStore.ExportRow> rows =
                new ArrayList<>(UiEditStore.rowsFor(working, originalBounds, currentBounds, classNames));
        // Строки для элементов layout: имя, исходный логический прямоугольник, дельта.
        String screenName = className(target);
        for (var entry : working.elements().entrySet()) {
            UiEditModel.Bounds raw = UiEditLayout.registeredFor(screenName).get(entry.getKey());
            if (raw == null) {
                continue;
            }
            rows.add(UiEditStore.ExportRow.of(
                    entry.getKey(),
                    new UiEditModel.Original(raw, ""),
                    entry.getValue().applyTo(raw),
                    entry.getValue()));
        }
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
            // Тот же путь, что у UiEditLayout: одна правка — один файл.
            store = new UiEditStore(UiEditStore.defaultRoot());
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
    public static String className(Object object) {
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
