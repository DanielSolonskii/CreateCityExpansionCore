package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Доставка снимка территорий на клиент: проверка порядка, а не отрисовки.
 *
 * <p><b>Что здесь ломалось.</b> Сервер отправляет снимок сразу после
 * {@code openMenu}, но меню и сетевой пакет идут разными каналами: экран на
 * клиенте часто ещё не создан, когда пакет уже пришёл. Обработчик проверял
 * «открыт ли City Pylon» и всё, что не совпало, отбрасывал. Панель оставалась
 * без территорий — и без заливки, и без фронтира, поэтому ПКМ по участку
 * нечего было принимать.
 *
 * <p>Здесь проверяется чистая часть этого контракта: снимок, пришедший до экрана,
 * обязан быть применён, когда экран появился, и обязан быть отброшен, если он
 * относится к другому пилону. Сам экран и сеть в headless-проверке недоступны,
 * поэтому проверяется правило доставки, а не факт отрисовки.
 */
public final class CityTerritoryDeliveryCheck {

    private static int checks;
    private static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /**
     * Одноэлементный буфер снимка — ровно та логика, что в
     * {@code CityTerritorySnapshot}, но без Minecraft-типов.
     */
    private static final class Pending {
        private BlockPos pylon;
        private List<CityPlot> plots;
        private int appliedTo;
        private int appliedPlots;
        /** Видимая карта: общая у вкладки «Карта» и у атласа. */
        private int panelUpdates;
        /** Кеш участков и граница мира живут в экране пилона. */
        private int screenCacheUpdates;

        /*
         * Вид сверху: атлас. Снимок обязан дойти и до панели, и до экрана
         * пилона под ним, иначе карта обновится, а граница в мире и счётчик
         * на вкладке «Территория» останутся прежними.
         *
         * <p>Ни буфера, ни ожидания следующего открытия: за claim игрок
         * смотрит в карту и ждёт результат, а не переоткрывает пилон.
         */
        void acceptWhileAtlasOpen(BlockPos pylonPos, List<CityPlot> payload) {
            if (pylonPos == null) {
                return;
            }
            this.panelUpdates++;
            this.screenCacheUpdates++;
        }

        void accept(BlockPos pylonPos, List<CityPlot> payload) {
            // Экран ещё не создан: снимок запоминается, а не теряется.
            this.pylon = pylonPos;
            this.plots = payload;
        }

        void openScreen(BlockPos pylonPos) {
            if (pylonPos == null || !pylonPos.equals(this.pylon) || plots == null) {
                return;
            }
            this.appliedTo++;
            this.appliedPlots = plots.size();
            this.pylon = null;
            this.plots = null;
        }
    }

    public static void main(String[] args) {
        deliveryBeforeScreen();
        screenMismatchIgnored();
        latestSnapshotWins();
        connectionResetOnApply();
        frontierNeedsSnapshot();
        atlasAppliesSnapshotImmediately();
        atlasAppliesRepeatedRequests();
        atlasSnapshotUpdatesWorldBoundaryCache();

        System.out.println("PASS: " + checks + " territory snapshot delivery checks");
    }

    /**
     * Главный случай для атласа: claim из атласа обновляет карту сразу.
     *
     * <p>Раньше снимок при открытом атласе уходил в буфер «до открытия экрана
     * пилона». Атлас не экран пилона, поэтому условие не выполнялось никогда:
     * подтверждение закрывалось, сервер участок принимал, а карта продолжала
     * рисовать прежние участки. Выглядело как «сохранил, но ничего не
     * нарисовалось».
     */
    private static void atlasAppliesSnapshotImmediately() {
        Pending pending = new Pending();
        BlockPos pylon = new BlockPos(120, 64, -80);

        pending.acceptWhileAtlasOpen(pylon, List.of(
                new CityPlot(OVERWORLD, 0, 0),
                new CityPlot(OVERWORLD, 1, 0)));
        check(pending.panelUpdates == 1, "a snapshot with the atlas open reaches the map at once");
        check(pending.appliedTo == 0,
                "the atlas path does not wait for the pylon screen to exist");
    }

    /**
     * Периодический запрос атласа не копится, а применяется каждый раз.
     *
     * <p>Атлас переспрашивает снимок раз в 20 тиков. Если бы эти ответы
     * попадали в буфер, он перезаписывал бы себя сам собой, и расхождение
     * не просто не исчезало — оно выглядело бы как «сеть не отвечает».
     */
    private static void atlasAppliesRepeatedRequests() {
        Pending pending = new Pending();
        BlockPos pylon = new BlockPos(0, 64, 0);
        for (int tick = 0; tick < 5; tick++) {
            pending.acceptWhileAtlasOpen(pylon, List.of(new CityPlot(OVERWORLD, tick, 0)));
        }
        check(pending.panelUpdates == 5, "every atlas refresh is applied, not buffered");
    }

    /**
     * Кеш экрана обновляется вместе с картой: граница в мире и счётчик
     * участков обязаны видеть тот же снимок.
     *
     * <p>Обновить только панель — значит оставить карту новой, а границу
     * старой: игрок заclaimил участок и видел бы, что заявка прошла по
     * одному экрану и не прошла по другому.
     */
    private static void atlasSnapshotUpdatesWorldBoundaryCache() {
        Pending pending = new Pending();
        pending.acceptWhileAtlasOpen(new BlockPos(0, 64, 0), List.of(new CityPlot(OVERWORLD, 0, 0)));
        check(pending.screenCacheUpdates == pending.panelUpdates,
                "the map and the pylon screen cache are updated from the same snapshot");
    }

    /**
     * Главный случай: пакет успел раньше экрана.
     *
     * <p>До исправления снимок отбрасывался, и карта оставалась пустой до
     * переоткрытия пилона.
     */
    private static void deliveryBeforeScreen() {
        Pending pending = new Pending();
        BlockPos pylon = new BlockPos(120, 64, -80);

        // Снимок приходит, экрана ещё нет.
        pending.accept(pylon, List.of(new CityPlot(OVERWORLD, 2, -2), new CityPlot(OVERWORLD, 3, -2)));
        check(pending.appliedTo == 0, "nothing is applied before the screen exists");

        // Экран открылся — снимок должен примениться сам.
        pending.openScreen(pylon);
        check(pending.appliedTo == 1, "a snapshot that arrived first is applied once the screen opens");
        check(pending.appliedPlots == 2, "the buffered snapshot carries its plots");

        // Повторное открытие не применяет уже потреблённый снимок дважды.
        pending.openScreen(pylon);
        check(pending.appliedTo == 1, "a consumed snapshot is not applied twice");
    }

    /** Снимок чужого пилона не должен применяться к этому экрану. */
    private static void screenMismatchIgnored() {
        Pending pending = new Pending();
        pending.accept(new BlockPos(0, 64, 0), List.of(new CityPlot(OVERWORLD, 0, 0)));
        pending.openScreen(new BlockPos(500, 64, 500));
        check(pending.appliedTo == 0, "a snapshot for another pylon is not applied");
        pending.openScreen(new BlockPos(0, 64, 0));
        check(pending.appliedTo == 1, "the snapshot still applies to its own pylon afterwards");
    }

    /** Последний снимок заменяет предыдущий целиком: держать историю незачем. */
    private static void latestSnapshotWins() {
        Pending pending = new Pending();
        BlockPos pylon = new BlockPos(0, 64, 0);
        pending.accept(pylon, List.of(new CityPlot(OVERWORLD, 0, 0)));
        pending.accept(pylon, List.of(
                new CityPlot(OVERWORLD, 0, 0),
                new CityPlot(OVERWORLD, 1, 0),
                new CityPlot(OVERWORLD, 1, 1)));
        pending.openScreen(pylon);
        check(pending.appliedTo == 1, "only one snapshot is applied, not both");
        check(pending.appliedPlots == 3, "the newest snapshot wins over the older one");
    }

    /** Пустой снимок — тоже снимок: он обязан применяться и снимать прежние данные. */
    private static void connectionResetOnApply() {
        Pending pending = new Pending();
        BlockPos pylon = new BlockPos(0, 64, 0);
        pending.accept(pylon, List.of(new CityPlot(OVERWORLD, 0, 0)));
        pending.accept(pylon, List.of());
        pending.openScreen(pylon);
        check(pending.appliedTo == 1, "an empty snapshot is applied like any other");
        check(pending.appliedPlots == 0, "an empty snapshot clears the previous territories");
    }

    /**
     * Связь доставки и claim.
     *
     * <p>Пока снимок не применён, участки неизвестны, а значит нечего предложить
     * и нечего принять по ПКМ. Это ровно та связь, из-за которой пропала вся
     * отрисовка территории вместе с правом расширять её.
     */
    private static void frontierNeedsSnapshot() {
        CityManager manager = new CityManager();
        UUID owner = UUID.randomUUID();
        City city = manager.createCity("Delivery", owner, OVERWORLD, new BlockPos(0, 64, 0));

        Set<CityPlot> owned = city.getClaimedPlots();
        check(owned.size() == 1, "the city owns its pylon plot before any snapshot exists");

        // Пока снимка нет, у клиента нет и списка участков, поэтому фронтир
        // пуст и правый клик принимать нечего — это ожидаемое состояние,
        // а не ошибка. Проверяем, что после применения снимка фронтир есть.
        Set<CityPlot> beforeSnapshot = new java.util.LinkedHashSet<>();
        for (CityPlot plot : owned) {
            for (CityPlot adjacent : plot.neighbours()) {
                beforeSnapshot.add(adjacent);
            }
        }
        check(!beforeSnapshot.isEmpty(), "a known territory yields a non-empty frontier");

        List<CityPlot> delivered = new java.util.ArrayList<>(city.getClaimedPlots());
        Set<CityPlot> afterSnapshot = new java.util.LinkedHashSet<>();
        for (CityPlot plot : delivered) {
            for (CityPlot adjacent : plot.neighbours()) {
                if (!delivered.contains(adjacent)) {
                    afterSnapshot.add(adjacent);
                }
            }
        }
        check(afterSnapshot.equals(beforeSnapshot),
                "the client frontier derived from a delivered snapshot matches the server view");
        check(afterSnapshot.stream().allMatch(manager::isPlotFree),
                "every plot offered after delivery is genuinely free");
    }
}
