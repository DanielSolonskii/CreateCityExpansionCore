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

import java.util.List;

/**
 * Данные для карты города — источник, не привязанный к экрану пилона.
 *
 * <p><b>Зачем это отдельный интерфейс.</b> Раньше World Map брала всё из
 * {@code CityPylonMenu}, а меню — это контейнер, живущий ровно пока игрок
 * стоит у пилона. Из-за этого карта открывалась только от пилона и только
 * рядом с ним. Сама карта при этом от пилона не зависит: {@code CityMapPanel}
 * получает всё через {@code set*}/{@code receive*}, а меню — лишь
 * промежуточная очередь между сетевыми пакетами и экраном.
 *
 * <p>Значит, чтобы открыть ту же карту по клавише, нужен второй источник
 * тех же данных, а не переписывание карты. Меню и сессия — две реализации
 * этого интерфейса, а экран не знает, откуда пришли данные.
 *
 * <p><b>Почему это не гарантия безопасности.</b> Интерфейс ничего не
 * разрешает: право прислать данные решает сервер, а редактирование карта
 * отправляет пакетами, которые сервер проверяет отдельно. Здесь только форма
 * данных.
 *
 * @see CityWorldMapSession источник для карты, открытой по клавише
 * @see com.example.examplemod.menu.CityPylonMenu источник для карты из пилона
 */
public interface WorldMapDataSource {

    /** Пилон города: служит якорем для запросов, а не точкой доступа. */
    BlockPos getPylonPos();

    /** Измерение, в которой существует город. */
    ResourceLocation getDimensionId();

    String getCityName();

    int getColorRgb();

    int getMemberCount();

    List<CityProjectSnapshot> getProjects();

    /** Тайлы местности, накопленные с прошлого запроса. */
    List<CityMapTilePayload> drainMapTiles();

    List<RailwayStationSnapshot> getRailwayStations();

    List<RailwayTrainSnapshot> getRailwayTrains();

    List<RailwayTrainCandidate> getRailwayTrainCandidates();

    List<RailwayOwnershipSnapshot> getRailwayOwnership();

    /** Снимок рельсовой сети; {@code null}, если ещё не приходил. */
    RailwayMapPayload drainRailwayMap();

    default com.example.examplemod.network.RailwayRouteOptionsPayload drainRouteOptions(){return null;}
    EconomyStatePayload getEconomyState();

    CityRelationsStatePayload getRelationsState();

    // ---- Приём пакетов. Меню зовёт их всегда, сессия — когда меню нет. ----

    void applyState(CityPylonStatePayload payload);

    void acceptMapTile(CityMapTilePayload payload);

    void applyRailwayState(RailwayStatePayload payload);

    void acceptRailwayMap(RailwayMapPayload payload);

    void applyEconomyState(EconomyStatePayload payload);

    void applyRelationsState(CityRelationsStatePayload payload);
}