package com.example.examplemod.city;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.city.CityProjectNetworkHandlers;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityMapPeersPayload;
import com.example.examplemod.network.ClaimCityPlotPayload;
import com.example.examplemod.network.CityNotificationPayload;
import com.example.examplemod.network.RequestCityTerritoriesPayload;
import com.example.examplemod.network.SetCityColorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Серверная часть территориальных claims и цвета города.
 *
 * <p>Здесь живут обе точки доверия: клиент отправляет намерение, а
 * здесь оно проверяется по каноническому состоянию {@link CityManager}
 * и только потом применяется. Ни город, ни его участки, ни его цвет
 * клиент не меняет напрямую — в пакете нет ни одного поля, которое
 * можно было бы принять как факт.
 */
public final class CityTerritoryHandlers {

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    /**
     * Максимальное расстояние от игрока до пилона для территориальных
     * команд. Совпадает с проверкой в {@code RequestCityMapTilePayload}
     * и с командой создания города: пилон должен быть под рукой.
     */
    private static final double MAX_PYLON_DISTANCE_SQR = 64.0;

    private CityTerritoryHandlers() {
    }

    /**
     * Обрабатывает запрос занять участок.
     *
     * <p>Ответ всегда отправляется обратно игроку, включая отказ: карта
     * должна сказать, почему клик ничего не сделал, иначе игрок будет
     * кликать снова и снова. После успеха дополнительно пересылается
     * снимок территорий, поэтому карта обновляется на месте.
     */
    public static void claim(ServerPlayer player, ClaimCityPlotPayload request) {
        CityManager manager = CityManager.get(player.getServer().overworld());

        City city = null;
        CityClaimResult result;

        if (!(player.containerMenu instanceof CityPylonMenu menu)
                || !menu.getPylonPos().equals(request.pylonPos())) {
            result = CityClaimResult.NO_CITY;
        } else if (request.dimension() == null
                || !player.serverLevel().dimension().location().equals(request.dimension())) {
            result = CityClaimResult.DIMENSION_MISMATCH;
        } else if (player.distanceToSqr(
                request.pylonPos().getX() + 0.5,
                request.pylonPos().getY() + 0.5,
                request.pylonPos().getZ() + 0.5
        ) > MAX_PYLON_DISTANCE_SQR) {
            result = CityClaimResult.TOO_FAR;
        } else if ((city = manager.getCityByPylon(request.dimension(), request.pylonPos())) == null) {
            result = CityClaimResult.NO_CITY;
        } else {
            var plot=request.plot();var border=player.serverLevel().getWorldBorder();
            result = !plot.isValid() || plot.minX()<border.getMinX() || plot.maxX()+1d>border.getMaxX()
                    || plot.minZ()<border.getMinZ() || plot.maxZ()+1d>border.getMaxZ()
                    ? CityClaimResult.INVALID_PLOT : manager.claimPlot(city, plot, player.getUUID());
        }
        LOGGER.info("Claim request from {}: pylon={} plot=({},{}) result={}",
                com.example.examplemod.auth.PlayerNames.name(player), request.pylonPos(),
                request.plotX(), request.plotZ(), result);

        if (result.success()) {
            /*
             * Всё обновляется без закрытия пилона: игрок тут же видит
             * новый участок на карте и новое число участков во вкладке
             * «Территория».
             */
            syncTerritoryViews(player, city);
        }
        notify(player, result, request.plot());
    }

    /**
     * Обрабатывает запрос сменить цвет города.
     *
     * <p>При успехе пересылаются и снимок территорий (цвет виден на
     * карте), и состояние пилона (цвет виден во вкладке «Территория»).
     */
    public static void setColor(ServerPlayer player, SetCityColorPayload request) {
        CityManager manager = CityManager.get(player.getServer().overworld());

        City city = null;
        boolean changed = false;

        if (player.containerMenu instanceof CityPylonMenu menu
                && menu.getPylonPos().equals(request.pylonPos())
                && player.distanceToSqr(
                request.pylonPos().getX() + 0.5,
                request.pylonPos().getY() + 0.5,
                request.pylonPos().getZ() + 0.5
        ) <= MAX_PYLON_DISTANCE_SQR
                && (city = manager.getCityByPylon(
                player.serverLevel().dimension().location(), request.pylonPos())) != null) {
            changed = manager.setCityColor(city, request.colorRgb(), player.getUUID());
        }

        if (changed) {
            syncTerritoryViews(player, city);
        }
    }

    /**
     * Обновляет всё, что показывает территорию и цвет города, без закрытия пилона.
     *
     * <p><b>Порядок пакетов важен.</b> Сначала состояние города, потом снимок территорий:
     * состояние несёт цвет и число участков, а обработчик снимка перерисовывает границу в мире
     * именно текущим цветом. Обратный порядок оставил бы границу со старым цветом до следующего
     * обновления — то есть визуально «до перезахода».
     *
     * <p>Отправляется и участникам города: у другого администратора может быть открыт этот же
     * пилон, и его экран тоже обязан увидеть новое состояние. Снимок применяется только к меню
     * с тем же пилоном, поэтому лишние отправки безопасны.
     */
    private static void syncTerritoryViews(ServerPlayer player, City city) {
        if (city == null) {
            return;
        }
        var state = CityProjectNetworkHandlers.createState(player.getServer(), city, player);
        PacketDistributor.sendToPlayer(player, state);
        sendTerritories(player, city);
        for (var online : player.getServer().getPlayerList().getPlayers()) {
            if (online != player && city.hasMember(online.getUUID())) {
                PacketDistributor.sendToPlayer(online,
                        CityProjectNetworkHandlers.createState(
                                player.getServer(), city, online));
            }
        }
    }

    /**
     * Отвечает на запрос снимка территорий.
     *
     * <p>Проверки те же, что и при любой другой территориальной операции: открытое
     * меню именно этого пилона, город за ним существует, игрок рядом. Запрос без
     * этого контекста игнорируется, поэтому чужим пилоном нельзя вытянуть список
     * городов.
     *
     * <p>Карта по клавише сюда не ходит: у неё нет открытого меню, поэтому запрос
     * от неё отказал бы, и территорий на карте не было бы. Она получает тот же
     * снимок напрямую через {@link #sendTerritories}, где фильтр по расстоянию до
     * пилона не применяется, а фильтр по членству — да.
     */
    public static void requestTerritories(ServerPlayer player, RequestCityTerritoriesPayload request) {
        CityManager cities=CityManager.get(player.getServer().overworld());
        City city=cities.getMemberCity(player.getUUID());
        boolean menuMatches=player.containerMenu instanceof CityPylonMenu menu
                &&menu.getPylonPos().equals(request.pylonPos())
                &&menu.getDimensionId().equals(player.serverLevel().dimension().location());
        boolean memberMatches=city!=null&&city.getPylonPos().equals(request.pylonPos())
                &&city.getPylonDimension().equals(player.serverLevel().dimension().location());
        if(!menuMatches&&!memberMatches)return;
        if(menuMatches)city=cities.getCityByPylon(player.serverLevel().dimension().location(),request.pylonPos());
        var budget=player.getPersistentData();long now=net.minecraft.Util.getMillis();
        long previous=budget.getLong("CCECTerritoryRefresh");
        if(now>=previous&&now-previous<500)return;
        budget.putLong("CCECTerritoryRefresh",now);
        sendTerritories(player,city,request.center(),request.range());
    }
    /**
     * Отправляет игроку снимок территорий для его карты.
     *
     * <p>Фильтры и лимиты — на сервере: клиент не должен ни получать
     * города, которых всё равно не видно, ни решать, сколько их
     * рисовать.
     *
     * <p><b>Пилон берётся из меню, если оно открыто, иначе — из города.</b>
     * Второй случай нужен карте по клавише: там меню нет, а якорь требуется.
     * {@code ownCity} приходит только от вызывающего, который уже проверил
     * членство, либо равен {@code null} — тогда снимок опишет города вокруг
     * пилона из меню, как и раньше.
     *
     * <p>Опасность в том, что метод публичный и принимает город на веру. Поэтому
     * членство проверяется здесь же: иначе новый вызов с чужим городом отдал бы
     * его территории тому, кто просто знает, куда позвать.
     */
    public static void sendTerritories(ServerPlayer player, City ownCity) {
        sendTerritories(player,ownCity,null,CityPylonBlock.MAP_PEER_RANGE);
    }
    private static void sendTerritories(ServerPlayer player,City ownCity,BlockPos center,int range) {
        if (ownCity != null && !ownCity.hasMember(player.getUUID())) {
            LOGGER.info("Territory snapshot refused: {} is not a member of {}",
                    com.example.examplemod.auth.PlayerNames.name(player), ownCity.getName());
            return;
        }
        BlockPos pylonPos = player.containerMenu instanceof CityPylonMenu menu
                ? menu.getPylonPos()
                : (ownCity != null ? ownCity.getPylonPos() : null);
        if (pylonPos == null) {
            return;
        }
        ResourceLocation dimension = player.containerMenu instanceof CityPylonMenu menu
                ? menu.getDimensionId() : ownCity != null ? ownCity.getPylonDimension() : player.serverLevel().dimension().location();
        UUID masterCityId = EconomyManager.get(player.serverLevel()).masterCityId();
        UUID ownCityId = ownCity != null ? ownCity.getId() : null;

        /*
         * Предикат master-города строится так, чтобы пережить null.
         *
         * <p>Раньше здесь стояло `masterCityId::equals`, и при незаданной
         * master city (это обычное состояние нового мира: она выбирается
         * отдельно) первый же город ронял весь снимок NPE. Снимок
         * падал на каждом запросе, поэтому карта оставалась без
         * территорий, без фронтира и без рабочего ПКМ — при этом
         * исключение уходило в лог сервера, а на экране не появлялось
         * ничего.
         */
        java.util.function.Predicate<UUID> isMaster =
                masterCityId == null ? id -> false : masterCityId::equals;

        var peers = new ArrayList<CityMapPeersPayload.Peer>(CityMapPeersPayload.MAX_PEERS);
        for (CityManager.CityTerritoryView view : CityManager.get(player.getServer().overworld())
                .territoryViews(dimension, center==null?pylonPos:center, range,
                        ownCityId, isMaster)) {
            peers.add(new CityMapPeersPayload.Peer(
                    view.cityId(), view.name(), view.pylonX(), view.pylonZ(),
                    view.colorRgb(), view.plots(), view.residents(), view.own(), view.master()
            ));
        }

        PacketDistributor.sendToPlayer(player, new CityMapPeersPayload(pylonPos, dimension, peers));
        int totalPlots = peers.stream().mapToInt(peer -> peer.plots().size()).sum();
        LOGGER.info("Territory snapshot sent: pylon={} dimension={} cities={} plots={} own={}",
                pylonPos, dimension, peers.size(), totalPlots,
                peers.stream().anyMatch(CityMapPeersPayload.Peer::own));
    }

    /**
     * Показывает игроку результат операции — и успех, и конкретную причину отказа.
     *
     * <p>Тост виден всегда, а в чат причина попадает только при отказе:
     * успех и так подтверждён новым участком на карте, а вот молчаливое
     * «ничего не произошло» — главный источник путаницы при отказе.
     *
     * <p>Координаты участка подставляются в сообщение об успехе: без них
     * игрок не понимает, какой именно участок вошёл в территорию.
     */
    private static void notify(ServerPlayer player, CityClaimResult result, CityPlot plot) {
        Component message = Component.translatable(
                "gui.createcityexpansioncore.city.plot." + result.name().toLowerCase(java.util.Locale.ROOT),
                plot.plotX(), plot.plotZ());
        Component title = Component.translatable(result.success()
                ? "gui.createcityexpansioncore.city.plot.title"
                : "gui.createcityexpansioncore.city.plot.title_refused");

        PacketDistributor.sendToPlayer(player, new CityNotificationPayload(
                title.getString(), message.getString()));
        if (!result.success()) {
            player.sendSystemMessage(message);
        }
    }
}
