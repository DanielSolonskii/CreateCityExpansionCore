package com.example.examplemod.client;

import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.CityPylonStatePayload;
import com.example.examplemod.network.CityRelationsStatePayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.RailwayStatePayload;
import com.example.examplemod.railway.RailwayOwnershipSnapshot;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTrainCandidate;
import com.example.examplemod.railway.RailwayTrainSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Данные карты для города, открытой по клавише, — без открытого меню пилона.
 *
 * <p><b>Зачем нужен.</b> Сетевые пакеты складываются в {@code CityPylonMenu},
 * а меню существует только пока открыт контейнер. Карта, открытая по клавише,
 * работает без пилона, поэтому пакеты ей некуда принимать: они молча терялись,
 * и на экране была пустая карта. Этот класс — приёмник рядом с меню.
 *
 * <p><b>Правило записи.</b> Сессия перезаписывает данные целиком, а не
 * дополняет: каждый ответ сервера описывает текущее состояние, и смешивание
 * старого снимка с новым дало бы карту, которой не существует.
 *
 * <p><b>Привязка к городу.</b> Данные принимаются только для пилона, который
 * уже записан в сессии, либо записывают его сами. Чужой или устаревший пилон
 * не может переписать карту: без этой проверки пакет от прошлого города
 * нарисовал бы чужие рельсы поверх текущих.
 *
 * <p><b>Это не право доступа.</b> Сессия хранит лишь то, что сервер уже
 * прислал. Сервер присылает данные только участнику города, поэтому кеш не
 * расширяет доступ ни на кого.
 */
public final class CityWorldMapSession implements WorldMapDataSource {

    /** Единственный источник: экран и обработчики пакетов должны видеть одни данные. */
    public static final CityWorldMapSession INSTANCE = new CityWorldMapSession();

    private BlockPos pylonPos;
    private ResourceLocation dimensionId;
    /** Сессия ждёт первый пакет, чтобы узнать пилон города. */
    private boolean awaitingAnchor;
    /**
     * Сервер ответил, что городов у игрока нет.
     *
     * <p>Нужен, чтобы «города нет» отличалось от «ещё грузится»: молчание сервера
     * выглядит как медленная загрузка, и без этого флага экран висел вечно.
     */
    private boolean denied;
    private String cityName = "";
    private int colorRgb;
    private int memberCount;

    private final List<CityProjectSnapshot> projects = new ArrayList<>();
    private final List<CityMapTilePayload> mapTiles = new ArrayList<>();
    private final List<RailwayStationSnapshot> stations = new ArrayList<>();
    private final List<RailwayTrainSnapshot> trains = new ArrayList<>();
    private final List<RailwayTrainCandidate> trainCandidates = new ArrayList<>();
    private final List<RailwayOwnershipSnapshot> ownership = new ArrayList<>();
    private RailwayMapPayload railwayMap;
    private EconomyStatePayload economyState;
    private CityRelationsStatePayload relationsState;

    private CityWorldMapSession() {
    }

    /** Привязывает сессию к городу и забывает прежние данные. */
    public void begin(BlockPos pos, ResourceLocation dimension) {
        this.cityName = ""; this.memberCount = 0; this.colorRgb = 0;
        this.pylonPos = pos;
        this.dimensionId = dimension;
        this.projects.clear();
        this.mapTiles.clear();
        this.stations.clear();
        this.trains.clear();
        this.trainCandidates.clear();
        this.ownership.clear();
        this.railwayMap = null;
        this.economyState = null;
        this.relationsState = null;
    }

    /** Забывает всё: карта закрыта и город больше не показывается. */
    public void clear() {
        this.cityName = "";
        this.memberCount = 0;
        this.colorRgb = 0;
        this.awaitingAnchor = false;
        this.denied = false;
        this.pylonPos = null;
        this.dimensionId = null;
        this.projects.clear();
        this.mapTiles.clear();
        this.stations.clear();
        this.trains.clear();
        this.trainCandidates.clear();
        this.ownership.clear();
        this.railwayMap = null;
        this.economyState = null;
        this.relationsState = null;
    }

    /**
     * Пометить сессию как ожидающую якорь.
     *
     * <p>Пилон города клиент узнать не может: он определяется на сервере по
     * членству, и приходит он первым же пакетом в ответ на запрос. Привязываться
     * приходится по первому пакету, а не до запроса.
     *
     * <p>Привязка по первому пакету безопасна тем, что сервер отвечает только на
     * явно отправленный запрос и только участнику города — то есть привязаться
     * больше не к чему.
     */
    public void awaitAnchor() {
        clear();
        this.awaitingAnchor = true;
        /*
         * Новый запрос — новый ответ: прежний отказ больше не действителен.
         *
         * <p>Без этого игрок, вступивший в город после отказа, нажал бы M и снова
         * увидел «города нет»: карта открылась бы в состоянии, которое сервер уже
         * не подтверждает.
         */
        this.denied = false;
        this.pylonPos = null;
        this.dimensionId = null;
    }

    /**
     * Сервер ответил, что городов у игрока нет.
     *
     * <p>Нужен, чтобы «города нет» отличалось от «ещё грузится»: молчание сервера
     * выглядит как медленная загрузка, и без этого флага экран висел бы вечно.
     */
    public void deny() {
        this.denied = true;
    }

    /** Отказал ли сервер: у игрока нет города. */
    public boolean isDenied() {
        return this.denied;
    }

    /** Готов ли источник к отрисовке. */
    public boolean isReady() {
        return this.pylonPos != null;
    }

    public void acceptDirectory(com.example.examplemod.network.CityDirectoryPayload payload) {
        if (awaitingAnchor || dimensionId == null && payload.anchor().equals(pylonPos)) {
            begin(payload.anchor(), payload.dimension());
            awaitingAnchor = false;
            denied = false;
        }
        if (!payload.dimension().equals(dimensionId) || !payload.anchor().equals(pylonPos)) return;
        for (var profile : payload.cities()) if (profile.own()) {
            cityName = profile.name(); colorRgb = profile.colour(); memberCount = profile.residents();
        }
    }

    /**
     * Принимает ли пакет.
     *
     * <p>Первый пакет ожидающей сессии задаёт якорь. Дальше сверка строгая: пакет
     * чужого города переписал бы карту.
     */
    private boolean accepts(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (this.pylonPos == null) {
            if (!this.awaitingAnchor) {
                return false;
            }
            this.pylonPos = pos;
            this.awaitingAnchor = false;
            return true;
        }
        return pos.equals(this.pylonPos);
    }

    @Override
    public BlockPos getPylonPos() {
        return this.pylonPos;
    }

    @Override
    public ResourceLocation getDimensionId() {
        return this.dimensionId == null ? ResourceLocation.withDefaultNamespace("minecraft") : this.dimensionId;
    }

    @Override
    public String getCityName() {
        return this.cityName;
    }

    @Override
    public int getColorRgb() {
        return this.colorRgb;
    }

    @Override
    public int getMemberCount() {
        return this.memberCount;
    }

    @Override
    public List<CityProjectSnapshot> getProjects() {
        return List.copyOf(this.projects);
    }

    @Override
    public List<CityMapTilePayload> drainMapTiles() {
        List<CityMapTilePayload> drained = List.copyOf(this.mapTiles);
        this.mapTiles.clear();
        return drained;
    }

    @Override
    public List<RailwayStationSnapshot> getRailwayStations() {
        return List.copyOf(this.stations);
    }

    @Override
    public List<RailwayTrainSnapshot> getRailwayTrains() {
        return List.copyOf(this.trains);
    }

    @Override
    public List<RailwayTrainCandidate> getRailwayTrainCandidates() {
        return List.copyOf(this.trainCandidates);
    }

    @Override
    public List<RailwayOwnershipSnapshot> getRailwayOwnership() {
        return List.copyOf(this.ownership);
    }

    @Override
    public RailwayMapPayload drainRailwayMap() {
        RailwayMapPayload drained = this.railwayMap;
        this.railwayMap = null;
        return drained;
    }

    @Override
    public EconomyStatePayload getEconomyState() {
        return this.economyState;
    }

    @Override
    public CityRelationsStatePayload getRelationsState() {
        return this.relationsState;
    }

    @Override
    public void applyState(CityPylonStatePayload payload) {
        if (!accepts(payload.pylonPos())) {
            return;
        }
        this.colorRgb = payload.colorRgb();
        this.projects.clear();
        this.projects.addAll(payload.projects());
    }

    /**
     * Имя города и число жителей берутся из снимка соседей.
     *
     * <p>В {@code CityPylonStatePayload} их нет: там только уровень, проекты,
     * развитие, цвет и право управлять территорией. Зато у собственного
     * города в снимке территорий уже есть и имя, и число жителей — брать
     * их оттуда значит не заводить отдельный пакет ради двух строк.
     */
    public void acceptPeers(List<com.example.examplemod.network.CityMapPeersPayload.Peer> peers) {
        for (com.example.examplemod.network.CityMapPeersPayload.Peer peer : peers) {
            if (peer.own()) {
                this.cityName = peer.name();
                this.memberCount = peer.residents();
                return;
            }
        }
    }

    @Override
    public void acceptMapTile(CityMapTilePayload payload) {
        if (dimensionId != null && !dimensionId.equals(payload.dimensionId())) return;
        if (!accepts(payload.pylonPos())) {
            return;
        }
        this.mapTiles.add(payload);
    }

    @Override
    public void applyRailwayState(RailwayStatePayload payload) {
        if (!accepts(payload.pylonPos())) {
            return;
        }
        this.stations.clear();
        this.stations.addAll(payload.stations());
        this.trains.clear();
        this.trains.addAll(payload.trains());
        this.trainCandidates.clear();
        this.trainCandidates.addAll(payload.candidates());
        this.ownership.clear();
        this.ownership.addAll(payload.ownership());
    }

    @Override
    public void acceptRailwayMap(RailwayMapPayload payload) {
        if (!accepts(payload.pylonPos())) {
            return;
        }
        this.railwayMap = payload;
    }

    @Override
    public void applyEconomyState(EconomyStatePayload payload) {
        if (!accepts(payload.pylonPos())) {
            return;
        }
        this.economyState = payload;
    }

    @Override
    public void applyRelationsState(CityRelationsStatePayload payload) {
        if (!accepts(payload.pylonPos())) {
            return;
        }
        this.relationsState = payload;
    }
}
