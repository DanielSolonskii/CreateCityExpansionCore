package com.example.examplemod.client.uiedit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Хук для layout-классов ручных экранов (фаза 2 редактора UI).
 *
 * <p>Экраны вроде Пилона, Профиля и Соцсетей не создают виджетов: они считают
 * геометрию в layout-классе и рисуют всё вручную через {@code GuiGraphics}.
 * Хук ставится на выход аксессора layout-класса:
 *
 * <pre>
 * public Rect close() { return hook("close", new Rect(WIDTH - 36, 8, 24, 22)); }
 *
 * private Rect hook(String name, Rect r) {
 *     return UiEditLayout.apply(name, r.x(), r.y(), r.width(), r.height(),
 *             scale(), x(), y(), Rect::new);
 * }
 * </pre>
 *
 * <p><b>Ключ экрана — текущий экран, а не строка в точке вызова.</b>
 * {@link UiEditEvents} объявляет его в {@code Init.Pre} ({@link #setActiveScreen}),
 * до {@code init()}, когда layout-класс только считается. Хук не передаёт имя
 * экрана вовсе — это структурно исключает рассинхрон ключа именем класса
 * (однажды уже пойманный баг: хук писал {@code "PlayerProfile"}, а сессия
 * читала {@code "PlayerProfileScreen"} — обводок не было и правка не работала).
 *
 * <p><b>Три обязанности.</b>
 * <ol>
 *   <li><b>Реестр.</b> Каждый посчитанный прямоугольник регистрируется под
 *       именем элемента — оверлей рисует обводку, ловит клики и перетаскивание
 *       там, где нет ни одного {@code AbstractWidget}. Реестр хранит
 *       <b>экранные</b> координаты: в них живут обводка и попадания.</li>
 *   <li><b>Применение дельт.</b> Правки хранятся и применяются в
 *       <b>логических</b> координатах layout (design space) — они переживают
 *       GUI scale, как и дельты виджетов.</li>
 *   <li><b>Единый источник.</b> {@link #overridesFor} отдаёт тот же объект,
 *       что видит сессия редактора: правка элемента видна после пересборки
 *       экрана без перечитывания файла, автосохранение пишет именно его.</li>
 * </ol>
 *
 * <p><b>Координаты.</b> layout-классы рисуют через
 * {@code pose().translate(originX, originY); pose().scale(scale)}, поэтому
 * экран = origin + local * scale; обратное — local = (screen - origin) / scale.
 * Класс не знает конкретных Rect-типов: аксессор отдаёт координаты и фабрику
 * {@link BoxFactory}, создающую свой record.
 *
 * <p>Правки применяются только игрокам из allowlist (как и путь виджетов):
 * для остальных хук — чистая проходная функция. Класс не зависит от Minecraft —
 * проверяется в {@code UiEditCheck}.
 */
public final class UiEditLayout {

    /** Фабрика_Rect layout-класса: координаты логического прямоугольника. */
    @FunctionalInterface
    public interface BoxFactory<R> {

        R create(int x, int y, int w, int h);
    }

    /** Преобразование логических координат layout в экранные. */
    private record Transform(float scale, float originX, float originY) {

        boolean identity() {
            return scale == 1f && originX == 0f && originY == 0f;
        }
    }

    /** Экран → (элемент → логические границы), в порядке чтения layout-класса. */
    private static final Map<String, Map<String, UiEditModel.Bounds>> REGISTERED = new HashMap<>();
    /** Экран → правки; тот же объект, что у сессии редактора. */
    private static final Map<String, UiEditModel.ScreenOverrides> FILES = new HashMap<>();
    /** Экран → параметры pose-преобразования (обновляется на каждом apply). */
    private static final Map<String, Transform> TRANSFORMS = new HashMap<>();

    private static final Map<String, Map<String, Transform>> ELEMENT_TRANSFORMS = new HashMap<>();

    private static final Map<String, Map<String, UiEditModel.Bounds>> ELEMENT_VIEWPORTS = new HashMap<>();

    private static UiEditStore store;
    private static BooleanSupplier gate = UiEditLayout::allowedByDefault;
    private static long gateCheckedAt;
    private static boolean gateCached;
    /** Ключ текущего экрана (className) — объявляется в Init.Pre до init(). */
    private static volatile String activeScreen;

    private UiEditLayout() {
    }

    // ------------------------------------------------------------------
    // Текущий экран
    // ------------------------------------------------------------------

    /**
     * Объявить текущий экран: вызывается из {@code ScreenEvent.Init.Pre},
     * <b>до</b> {@code init()}, когда layout-класс считает геометрию.
     *
     * <p>Вне игрового экрана (headless-проверки, статический код) ключа нет —
     * {@link #apply} работает чистой проходной функцией и ничего не регистрирует.
     */
    public static void setActiveScreen(Object screen) {
        activeScreen = screen == null ? null : UiEditSession.className(screen);
    }

    /** Тесты: ключ активного экрана напрямую (или {@code null} — вне игры). */
    static void activateForTests(String screenKey) {
        activeScreen = screenKey;
    }

    // ------------------------------------------------------------------
    // Хук layout-класса
    // ------------------------------------------------------------------

    /**
     * Применить правку элемента и зарегистрировать его для оверлея.
     *
     * <p>Имя экрана берётся из {@link #setActiveScreen}: вызывающему коду его
     * передавать не нужно (см. класс javadoc — так исключён баг рассинхрон ключа).
     *
     * @param element   имя элемента в layout, например {@code "close"} или {@code "tab[0]"}
     * @param x, y, w, h логические координаты из layout-класса
     * @param scale, originX, originY параметры pose-преобразования экрана
     * @param factory   фабрика_Rect конкретного layout-класса
     */
    public static <R> R apply(
            String element,
            int x,
            int y,
            int w,
            int h,
            float scale,
            float originX,
            float originY,
            BoxFactory<R> factory) {
        String screen = activeScreen;
        if (screen == null) {
            // Нет активного экрана (headless) — проходная функция без регистрации.
            return factory.create(x, y, w, h);
        }
        ELEMENT_TRANSFORMS.computeIfAbsent(screen, key -> new HashMap<>())
                .put(element, new Transform(scale, originX, originY));
        UiEditModel.Bounds local = new UiEditModel.Bounds(x, y, w, h);
        REGISTERED.computeIfAbsent(screen, key -> new LinkedHashMap<>()).put(element, local);
        UiEditModel.WidgetOverride override = allowedNow() ? overridesFor(screen).getElement(element) : null;
        if (override == null) {
            return factory.create(x, y, w, h);
        }
        UiEditModel.Bounds applied = override.applyTo(local);
        return factory.create(applied.x(), applied.y(), applied.w(), applied.h());
    }

    /** Хук без преобразования координат — экраны, рисующие без pose-масштаба. */
    public static <R> R apply(
            String element, int x, int y, int w, int h, BoxFactory<R> factory) {
        return apply(element, x, y, w, h, 1f, 0f, 0f, factory);
    }

    /**
     * Зарегистрировать pose-масштаб экрана, у которого layout-класса нет.
     *
     * <p>{@code CityPylonScreen} и {@code MineControllerScreen} сами считают
     * {@code interfaceScale} и рисуют дочерние виджеты внутри
     * {@code pose().scale(interfaceScale)} — их координаты логические, а мышь
     * экранные. Без этой регистрации обводка и попадания по виджетам не
     * сходились. Вызывается из {@code init()} экрана сразу после вычисления
     * масштаба; origin (0,0), потому что pose делит только на масштаб.
     */
    public static void registerTransform(Object screenKey, float scale, float originX, float originY) {
        TRANSFORMS.put(UiEditSession.className(screenKey), new Transform(scale, originX, originY));
    }

    // ------------------------------------------------------------------
    // Правки: общий с сессией источник
    // ------------------------------------------------------------------

    /** Правки экрана (ленивая загрузка из файла; объект общий с сессией). */
    public static UiEditModel.ScreenOverrides overridesFor(String screen) {
        UiEditModel.ScreenOverrides cached = FILES.get(screen);
        if (cached == null) {
            cached = load(screen);
            FILES.put(screen, cached);
        }
        return cached;
    }

    /** Перечитать файл с диска («Откатить»): новый объект становится общим. */
    public static UiEditModel.ScreenOverrides reload(String screen) {
        UiEditModel.ScreenOverrides fresh = load(screen);
        FILES.put(screen, fresh);
        return fresh;
    }

    private static UiEditModel.ScreenOverrides load(String screen) {
        try {
            return store().load(screen);
        } catch (Throwable t) {
            // Нет диска/FML — работаем с пустым набором, чтобы не ронять рендер.
            return new UiEditModel.ScreenOverrides(screen);
        }
    }

    private static UiEditStore store() {
        if (store == null) {
            store = new UiEditStore(UiEditStore.defaultRoot());
        }
        return store;
    }

    // ------------------------------------------------------------------
    // Реестр элементов для оверлея
    // ------------------------------------------------------------------

    /** Зарегистрированные элементы экрана: имя → логические границы. */
    public static Map<String, UiEditModel.Bounds> registeredFor(String screen) {
        Map<String, UiEditModel.Bounds> map = REGISTERED.get(screen);
        return map == null ? Collections.emptyMap() : map;
    }

    /** Имена элементов сверху вниз — порядок отрисовки layout-класса. */
    public static List<String> registeredNames(String screen) {
        return new ArrayList<>(registeredFor(screen).keySet());
    }

    /** Очистить реестр перед инициализацией нового экрана (Init.Pre). */
    public static void clearRegistry() {
        REGISTERED.clear();
        ELEMENT_TRANSFORMS.clear();
        ELEMENT_VIEWPORTS.clear();
    }

    /** Remove entries for controls whose visibility is recomputed on each frame. */
    public static void clearElements(String prefix) {
        Map<String, UiEditModel.Bounds> bounds = REGISTERED.get(activeScreen);
        Map<String, Transform> transforms = ELEMENT_TRANSFORMS.get(activeScreen);
        if (bounds != null) {
            bounds.keySet().removeIf(name -> name.startsWith(prefix));
            transforms.keySet().removeIf(name -> name.startsWith(prefix));
            var viewports = ELEMENT_VIEWPORTS.get(activeScreen);
            if (viewports != null) {
                viewports.keySet().removeIf(name -> name.startsWith(prefix));
            }
        }
    }

    /** Keep page controls selectable only within the surface that paints them. */
    public static void setElementViewport(String element, UiEditModel.Bounds viewport) {
        String screen = activeScreen;
        if (screen != null) {
            ELEMENT_VIEWPORTS.computeIfAbsent(screen, key -> new HashMap<>()).put(element, viewport);
        }
    }

    static UiEditModel.Bounds elementViewport(String screen, String element) {
        return ELEMENT_VIEWPORTS.getOrDefault(screen, Collections.emptyMap()).get(element);
    }

    private static Transform elementTransform(String screen, String element) {
        return ELEMENT_TRANSFORMS.getOrDefault(screen, Collections.emptyMap()).get(element);
    }

    public static UiEditModel.Bounds elementToScreen(String screen, String element, UiEditModel.Bounds local) {
        return toScreen(elementTransform(screen, element), local);
    }

    public static UiEditModel.Bounds elementToLayout(String screen, String element, UiEditModel.Bounds bounds) {
        return toLayout(elementTransform(screen, element), bounds);
    }

    static int elementToScreenDelta(String screen, String element, int delta) {
        return toScreenDelta(elementTransform(screen, element), delta);
    }

    // ------------------------------------------------------------------
    // Координаты: layout ↔ экран
    // ------------------------------------------------------------------

    /** Логические границы → экранные (как их рисует pose-стек экрана). */
    public static UiEditModel.Bounds toScreen(String screen, UiEditModel.Bounds local) {
        return toScreen(TRANSFORMS.get(screen), local);
    }

    private static UiEditModel.Bounds toScreen(Transform t, UiEditModel.Bounds local) {
        if (t == null || t.identity()) {
            return local;
        }
        return new UiEditModel.Bounds(
                Math.round(t.originX() + local.x() * t.scale()),
                Math.round(t.originY() + local.y() * t.scale()),
                Math.max(1, Math.round(local.w() * t.scale())),
                Math.max(1, Math.round(local.h() * t.scale())));
    }

    /** Экранные границы → логические (обратное преобразование). */
    public static UiEditModel.Bounds toLayout(String screen, UiEditModel.Bounds screenBounds) {
        return toLayout(TRANSFORMS.get(screen), screenBounds);
    }

    private static UiEditModel.Bounds toLayout(Transform t, UiEditModel.Bounds screenBounds) {
        if (t == null || t.identity()) {
            return screenBounds;
        }
        float inv = 1f / t.scale();
        return new UiEditModel.Bounds(
                Math.round((screenBounds.x() - t.originX()) * inv),
                Math.round((screenBounds.y() - t.originY()) * inv),
                Math.max(UiEditModel.MIN_SIZE, Math.round(screenBounds.w() * inv)),
                Math.max(UiEditModel.MIN_SIZE, Math.round(screenBounds.h() * inv)));
    }

    /** Экранный сдвиг пикселей → логические единицы layout (для дельт). */
    public static int toLayoutDelta(String screen, int screenDelta) {
        Transform t = TRANSFORMS.get(screen);
        if (t == null || t.identity()) {
            return screenDelta;
        }
        return Math.round(screenDelta / t.scale());
    }

    /** Логическая дельта → экранные пиксели (для строки состояния). */
    public static int toScreenDelta(String screen, int layoutDelta) {
        return toScreenDelta(TRANSFORMS.get(screen), layoutDelta);
    }

    private static int toScreenDelta(Transform t, int layoutDelta) {
        if (t == null || t.identity()) {
            return layoutDelta;
        }
        return Math.round(layoutDelta * t.scale());
    }

    // ------------------------------------------------------------------
    // Доступ и тесты
    // ------------------------------------------------------------------

    /** Есть ли у экрана преобразование координат (pose-масштаб). */
    public static boolean hasTransform(String screen) {
        Transform t = TRANSFORMS.get(screen);
        return t != null && !t.identity();
    }

    /** В игре — allowlist-файл; вне игры (headless) — false, хук проходной. */
    private static boolean allowedByDefault() {
        try {
            return UiEditAccess.isAllowed();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Проверка доступа с кешем на полсекунды: apply() зовётся каждый кадр на
     * каждый прямоугольник, а allowlist-файл — stat-вызов на каждое чтение.
     */
    private static boolean allowedNow() {
        long now = System.currentTimeMillis();
        if (now - gateCheckedAt > 500L) {
            gateCheckedAt = now;
            gateCached = gate.getAsBoolean();
        }
        return gateCached;
    }

    /** Тесты: свой стенд и своя проверка доступа. */
    static void installForTests(UiEditStore testStore, BooleanSupplier testGate) {
        store = testStore;
        gate = testGate;
        gateCheckedAt = 0L;
        gateCached = testGate.getAsBoolean();
        reset();
    }

    /** Тесты: полный сброс статического состояния. */
    static void reset() {
        REGISTERED.clear();
        ELEMENT_TRANSFORMS.clear();
        ELEMENT_VIEWPORTS.clear();
        FILES.clear();
        TRANSFORMS.clear();
        activeScreen = null;
        gateCheckedAt = 0L;
        gateCached = false;
    }
}
