package com.example.examplemod.client.uiedit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;

/**
 * Обработчики редактора UI: переключение по F7, применение сохранённых правок,
 * рисование оверлея и перехват мыши/клавиатуры.
 *
 * <p><b>Почему в режиме редактора всё перехватывается.</b> Пока редактор включен,
 * клики и клавиши не доходят до экрана: нельзя случайно нажать чужую кнопку,
 * отправить товар или закрыть экран. Esc сначала выключает редактор и только
 * со второго нажатия закрывает экран. Каждый вход перехвата помечен проверкой
 * {@code active && matches(screen)} — вне режима инструмент прозрачен.
 *
 * <p>Все обработчики статические и регистрируются на игровой шине классом
 * в {@code CreateCityExpansionCoreClient}.
 */
public final class UiEditEvents {

    private UiEditEvents() {
    }

    // ------------------------------------------------------------------
    // F7
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!UiEditKey.consumeToggle()) {
            return;
        }
        toggleEditor(Minecraft.getInstance().screen);
    }

    /**
     * Общий путь переключения F7.
     *
     * <p><b>Почему он вызывается из двух мест.</b> Ваниль записывает нажатия
     * биндов ({@code KeyMapping.click}) только когда экрана нет — так устроен
     * {@code KeyboardHandler}: {@code if (screen == null) KeyMapping.click(...)}.
     * То есть с открытым экраном F7 через KeyMapping молчит в принципе.
     * Поэтому, пока экран открыт, F7 перехватывается из {@code ScreenEvent.KeyPressed},
     * а из мира — из тика (там KeyMapping работает, и подсказка «откройте экран»
     * остаётся на месте). Одновременно два пути сработать не могут: ваниль не
     * создаёт клик KeyMapping при открытом экране.
     */
    private static void toggleEditor(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (screen == null) {
            UiEditSession session = UiEditSession.get();
            if (session.active()) {
                // Редактор остался включённым после закрытия последнего экрана —
                // F7 в мире выключает его, а не ругается на «откройте экран».
                session.setActive(false);
                toast(minecraft, "ui_edit.off");
            } else {
                toast(minecraft, "ui_edit.no_screen");
            }
            return;
        }
        if (!UiEditAccess.isAllowed()) {
            chat(minecraft, "ui_edit.denied");
            return;
        }
        UiEditSession session = UiEditSession.get();
        session.toggle();
        toast(minecraft, session.active() ? "ui_edit.on" : "ui_edit.off");
    }

    // ------------------------------------------------------------------
    // Применение сохранённых правок и автосохранение
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onInitPre(ScreenEvent.Init.Pre event) {
        // Сначала объявляем ключ экрана: layout-классы посчитают геометрию в
        // init() между Pre и Post, и хук зарегистрирует элементы под ним же.
        UiEditLayout.setActiveScreen(event.getScreen());
        // Реестр layout-элементов живёт в статике: без очистки на новом экране
        // остались бы имена и координаты предыдущего. Сам экран наполнит реестр
        // заново, пока идёт init() между Pre и Post.
        UiEditLayout.clearRegistry();
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!UiEditAccess.isAllowed()) {
            return;
        }
        UiEditSession.get().attach(event.getScreen(), event.getListenersList(), true);
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        UiEditSession session = UiEditSession.get();
        if (session.matches(event.getScreen())) {
            session.autoSaveOnClose();
        }
    }

    // ------------------------------------------------------------------
    // Рендер
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        UiEditSession session = UiEditSession.get();
        if (!session.active() || !session.matches(event.getScreen())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UiEditOverlay.render(
                session,
                event.getScreen(),
                event.getGuiGraphics(),
                minecraft.font,
                event.getMouseX(),
                event.getMouseY(),
                event.getPartialTick());
    }

    // ------------------------------------------------------------------
    // Мышь
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        UiEditSession session = UiEditSession.get();
        if (!session.active() || !session.matches(event.getScreen())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int button = event.getButton();

        // 1. Строка текста: клик по полю обрабатывается, клик вне — применяет текст.
        if (session.textMode()) {
            var box = session.textBox();
            if (box != null && box.mouseClicked(mouseX, mouseY, button)) {
                event.setCanceled(true);
                return;
            }
            session.applyText();
        }

        // 2. Кнопки нижней панели.
        UiEditOverlay.BarButton barButton =
                UiEditOverlay.buttonAt(event.getScreen(), minecraft.font, mouseX, mouseY);
        if (barButton != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                runAction(session, event.getScreen(), barButton.id());
            }
            event.setCanceled(true);
            return;
        }

        // 3. Остальное: выбор и перетаскивание — сначала виджеты, затем элементы
        //    layout ручных экранов. Чужие клики блокируются полностью.
        event.setCanceled(true);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        GuiEventListener target = session.widgetAt(event.getScreen(), mouseX, mouseY);
        if (target instanceof AbstractWidget widget) {
            session.select(widget);
            boolean textOpened = session.registerClick(event.getScreen(), widget);
            if (!textOpened) {
                session.beginDrag(event.getScreen(), widget, mouseX, mouseY, Screen.hasShiftDown());
            }
            return;
        }
        String element = session.elementAt(event.getScreen(), mouseX, mouseY);
        if (element != null) {
            session.selectElement(element);
            session.beginElementDrag(event.getScreen(), element, mouseX, mouseY, Screen.hasShiftDown());
        } else {
            session.select(null);
            session.selectElement(null);
        }
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        UiEditSession session = UiEditSession.get();
        if (!session.active() || !session.matches(event.getScreen())) {
            return;
        }
        if (event.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (session.draggingElement()) {
                session.dragElement(event.getScreen(), event.getMouseX(), event.getMouseY());
            } else if (session.dragging()) {
                session.drag(event.getScreen(), event.getMouseX(), event.getMouseY());
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
        UiEditSession session = UiEditSession.get();
        if (!session.active() || !session.matches(event.getScreen())) {
            return;
        }
        if (session.draggingElement()) {
            session.endElementDrag(event.getScreen());
        } else if (session.dragging()) {
            session.endDrag();
        }
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------
    // Клавиатура
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        // F7 внутри экранов: KeyMapping-клики ваниль не создаёт при открытом
        // экране (см. toggleEditor), поэтому переключение ловим здесь.
        if (event.getKeyCode() == GLFW.GLFW_KEY_F7) {
            toggleEditor(event.getScreen());
            event.setCanceled(true);
            return;
        }
        UiEditSession session = UiEditSession.get();
        if (!session.active() || !session.matches(event.getScreen())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int key = event.getKeyCode();

        if (session.textMode()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                session.applyText();
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                session.cancelText();
            } else if (session.textBox() != null) {
                session.textBox().keyPressed(key, event.getScanCode(), event.getModifiers());
            }
            event.setCanceled(true);
            return;
        }

        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                session.setActive(false);
                toast(minecraft, "ui_edit.off");
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER ->
                    session.startTextMode(event.getScreen());
            case GLFW.GLFW_KEY_R -> session.resetSelected(event.getScreen());
            case GLFW.GLFW_KEY_H -> session.toggleHelp();
            case GLFW.GLFW_KEY_LEFT -> session.nudgeSelected(event.getScreen(), -step(), 0);
            case GLFW.GLFW_KEY_RIGHT -> session.nudgeSelected(event.getScreen(), step(), 0);
            case GLFW.GLFW_KEY_UP -> session.nudgeSelected(event.getScreen(), 0, -step());
            case GLFW.GLFW_KEY_DOWN -> session.nudgeSelected(event.getScreen(), 0, step());
            default -> {
                // Любой другой клавише экран не отдаём — режим песочницы.
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChar(ScreenEvent.CharacterTyped.Pre event) {
        UiEditSession session = UiEditSession.get();
        if (!session.active() || !session.matches(event.getScreen())) {
            return;
        }
        if (session.textMode() && session.textBox() != null) {
            session.textBox().charTyped(event.getCodePoint(), event.getModifiers());
        }
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------
    // Действия панели
    // ------------------------------------------------------------------

    private static void runAction(UiEditSession session, Screen screen, String id) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (id) {
            case "save" -> toast(minecraft, session.save() ? "ui_edit.saved" : "ui_edit.save.failed");
            case "revert" -> {
                session.revert();
                toast(minecraft, "ui_edit.reverted");
            }
            case "clear" -> {
                session.clearAll();
                toast(minecraft, "ui_edit.cleared");
            }
            case "export" -> {
                try {
                    String path = session.exportTo(screen, screen.width, screen.height);
                    toast(minecraft, "ui_edit.exported", path);
                } catch (IOException e) {
                    toast(minecraft, "ui_edit.export.failed");
                }
            }
            case "help" -> session.toggleHelp();
            default -> {
            }
        }
    }

    private static int step() {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    private static void toast(Minecraft minecraft, String key, Object... args) {
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void chat(Minecraft minecraft, String key, Object... args) {
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.displayClientMessage(Component.translatable(key, args), false);
    }
}
