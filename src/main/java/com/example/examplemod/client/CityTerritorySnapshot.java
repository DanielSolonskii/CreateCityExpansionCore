package com.example.examplemod.client;

import com.example.examplemod.client.screen.CityPylonScreen;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.network.CityMapPeersPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Единственная точка приёма снимка территорий на клиенте.
 *
 * <p><b>Снимок может прийти раньше экрана.</b> Сервер отправляет его сразу после
 * {@code openMenu}, а меню и пакет идут разными каналами: экран на клиенте ещё
 * не создан, когда пакет уже в очереди. Раньше обработчик проверял
 * {@code screen instanceof CityPylonScreen} и просто отбрасывал всё, что не
 * совпало, — то есть почти всегда. Панель оставалась без территорий: не было ни
 * заливки, ни фронтира доступных участков, а значит и ПКМ по участку нечего было
 * принимать. Теперь снимок запоминается и применяется, как только экран появился.
 *
 * <p>Одноэлементный буфер хранит только последний снимок: он заменяет предыдущий
 * целиком, поэтому держать историю незачем, а cityId вместе с позицией пилона
 * защищает от применения чужого снимка к другому экрану.
 */
public final class CityTerritorySnapshot {

    private static BlockPos pendingPylon;
    private static List<CityMapPeersPayload.Peer> pendingPeers;

    private CityTerritorySnapshot() {
    }

    /** Применяет снимок к открытому экрану, иначе сохраняет его до открытия. */
    public static void accept(CityMapPeersPayload payload) {
        /*
         * Сессия карты по клавише тоже забирает снимок: имя и число жителей
         * есть только у собственного города в этом списке, а отдельного пакета
         * ради двух строк заводить не за что. Снимок не служебный — он уже
         * приходит участнику города, поэтому это не расширение доступа.
         */
        CityWorldMapSession.INSTANCE.acceptPeers(payload.peers());

        CityPylonScreen screen = openPylonScreen(payload.pylonPos());
        if (screen != null) {
            apply(screen, payload.peers());
            clearPending();
            return;
        }
        /*
         * Атлас рисует ту же панель, что и вкладка «Карта» — он берёт её
         * у экрана пилона, — но сам экраном пилона не является.
         *
         * <p>Здесь раньше такого случая не было, и снимок после claim из
         * атласа уходил в буфер до следующего открытия пилона: подтверждение
         * закрывалось, сервер участок принимал, а на карте ничего не
         * появлялось — атлас продолжал рисовать прежние участки. Плюс
         * собственный периодический запрос атласа (раз в 20 тиков) писал
         * в тот же буфер, так что буфер перезаписывался сам собой и
         * расхождение не накапливалось, а просто не исчезало.
         */
        var atlas = openAtlas(payload.pylonPos());
        if (atlas != null) {
            if (atlas.isReadOnly()) {
                atlas.acceptTerritories(payload);
                clearPending();
                return;
            }
            /*
             * apply, а не receivePeers напрямую: у атласа под собой есть экран
             * пилона, и его кеш участков тоже обязан обновиться — из него
             * берётся граница в мире и счётчик на вкладке «Территория».
             */
            apply(atlas.getParentScreen(), payload.peers());
            clearPending();
            return;
        }
        pendingPylon = payload.pylonPos();
        pendingPeers = payload.peers();
    }

    /**
     * Вызывается экраном сразу после создания.
     *
     * <p>Нужен для случая, когда пакет успел прийти раньше экрана: без этого
     * карта ждала бы следующего снимка, то есть до переоткрытия пилона.
     */
    public static void applyPending(CityPylonScreen screen) {
        if (screen == null || pendingPeers == null
                || !screen.getMenu().getPylonPos().equals(pendingPylon)) {
            return;
        }
        apply(screen, pendingPeers);
        clearPending();
    }

    /** Экран этого пилона, если он открыт прямо сейчас. */
    private static CityPylonScreen openPylonScreen(BlockPos pylonPos) {
        CityPylonScreen screen = com.example.examplemod.client.screen.CityPylonWorkspace.controller();
        return screen != null && screen.getMenu().getPylonPos().equals(pylonPos) ? screen : null;
    }

    /** Атлас этого пилона, если он открыт прямо сейчас. */
    private static PolishedWorldMapScreen openAtlas(BlockPos pylonPos) {
        return Minecraft.getInstance().screen instanceof PolishedWorldMapScreen atlas
                && (atlas.getMenu() != null ? atlas.getMenu().getPylonPos().equals(pylonPos)
                    : CityWorldMapSession.INSTANCE.isReady() && pylonPos.equals(CityWorldMapSession.INSTANCE.getPylonPos()))
                ? atlas
                : null;
    }

    /**
     * Снимок обновляет и карту, и границу в мире, и вкладку «Территория» из одного
     * источника. Поэтому успешный claim и смена цвета видны сразу, без закрытия и
     * повторного открытия пилона.
     *
     * <p>Обновляется и экран пилона под атласом, а не только видимая панель:
     * экран хранит свой кеш участков, из него берётся вкладка «Территория»
     * и из него же рисуется граница в мире. Обновить только панель значило бы
     * оставить карту новой, а границу и счётчик — старыми.
     */
    private static void apply(CityPylonScreen screen, List<CityMapPeersPayload.Peer> peers) {
        screen.getMapPanel().receivePeers(peers);
        screen.receiveTerritories(peers);
    }

    private static void clearPending() {
        pendingPylon = null;
        pendingPeers = null;
    }
}
