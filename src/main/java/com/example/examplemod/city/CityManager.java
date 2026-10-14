package com.example.examplemod.city;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CityManager extends SavedData {

    private static final String DATA_NAME =
            "createcityexpansioncore_cities";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Города, получившие участки при этой загрузке.
     *
     * <p>Нужно только для одного информационного сообщения в лог:
     * операция одноразовая и самодостаточная, но по логу видно, что мир
     * уже обновлён. Поле хранится в экземпляре, а не статически, потому
     * что миграция — свойство одной загрузки, а не процесса: несколько
     * загрузок в одном JVM не должны смешивать свои отчёты.
     */
    private final List<UUID> migratedCities = new ArrayList<>();

    /** Города, мигрированные с квадратной территории на участки при этой загрузке. */
    public List<UUID> migratedCities() {
        return List.copyOf(this.migratedCities);
    }

    private final Map<UUID, City> cities =
            new HashMap<>();
    private final Map<UUID, Set<UUID>> trustedCities = new HashMap<>();

    /**
     * Spatial lookup for the block protection path: {@code dimension + plotX + plotZ → cityId}.
     *
     * <p><b>Derived, never persisted.</b> The canonical truth is {@link City#getClaimedPlots()};
     * this map is rebuilt from it on load and updated on every claim, so a save file can never
     * disagree with itself. Without it, {@code getCityAt} would have to walk every plot of every
     * city on every block break and every block place — the protection events fire on ordinary
     * mining, not on city administration.
     */
    private final Map<CityPlot, UUID> claimOwners = new HashMap<>();

    private java.util.function.Predicate<UUID> activeBuildProjects = id -> false;

    public CityManager() {
    }

    // =========================================================
    // LOAD
    // =========================================================

    public static CityManager load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {

        CityManager manager =
                new CityManager();

        ListTag cityList =
                tag.getList(
                        "Cities",
                        Tag.TAG_COMPOUND
                );

        for (Tag cityTagRaw : cityList) {

            CompoundTag cityTag =
                    (CompoundTag) cityTagRaw;

            UUID id =
                    cityTag.getUUID("Id");

            String name =
                    cityTag.getString("Name");

            UUID owner =
                    cityTag.getUUID("Owner");

            BlockPos pylonPos =
                    NbtUtils.readBlockPos(
                            cityTag,
                            "PylonPos"
                    ).orElse(BlockPos.ZERO);

            int level =
                    cityTag.getInt("Level");

            City city =
                    new City(
                            id,
                            name,
                            owner,
                            pylonPos,
                            level,
                            java.util.List.of(),
                            true,
                            java.util.List.of(),
                            null,
                            java.util.Map.of(),
                            cityTag.contains("PylonDimension", Tag.TAG_STRING)
                                    ? java.util.Objects.requireNonNullElse(
                                    ResourceLocation.tryParse(cityTag.getString("PylonDimension")),
                                    net.minecraft.world.level.Level.OVERWORLD.location())
                                    : net.minecraft.world.level.Level.OVERWORLD.location()
                    );

            city.setPylonActive(
                    !cityTag.contains("PylonActive") || cityTag.getBoolean("PylonActive")
            );
            city.restoreTradeReputation(cityTag.getInt("TradeReputation"));

            // =================================================
            // ЦВЕТ ГОРОДА
            // =================================================

            /*
             * Старые города цвета не имели. restoreColor подставляет
             * детерминированный цвет из UUID, поэтому он не меняется
             * от запуска к запуску, и город после первого сохранения
             * уже лежит в новом формате.
             */
            city.restoreColor(cityTag.contains("Color", Tag.TAG_INT)
                    ? cityTag.getInt("Color")
                    : CityColors.defaultColor(id));
            city.restoreIdentity(cityTag.getString("Description"), cityTag.getString("Emblem"), cityTag.getLong("IdentityRevision"));

            // =================================================
            // УЧАСТКИ ТЕРРИТОРИИ 50×50 (+ МИГРАЦИЯ)
            // =================================================

            /*
             * Отсутствие самой секции Claims — признак города,
             * сохранённого до появления участков. Такие города
             * получают набор участков, покрывающий их прежний
             * квадрат вокруг пилона; старые данные не удаляются,
             * город просто переписывается в новом формате при
             * следующем сохранении.
             */
            if (cityTag.contains("Claims", Tag.TAG_LIST)) {
                city.replaceClaims(readPlots(cityTag.getList("Claims", Tag.TAG_COMPOUND)));
            } else {
                city.replaceClaims(CityClaims.legacyTerritory(
                        city.getPylonDimension(),
                        city.getPylonPos(),
                        city.getLegacyTerritorySize()
                ));
                manager.migratedCities.add(id);
            }

            // =================================================
            // MEMBERS
            // =================================================

            ListTag members =
                    cityTag.getList(
                            "Members",
                            Tag.TAG_COMPOUND
                    );

            for (Tag memberRaw : members) {

                CompoundTag memberTag =
                        (CompoundTag) memberRaw;

                UUID playerId =
                        memberTag.getUUID("Player");

                int roleId =
                        memberTag.getInt("Role");

                CityMember.Role[] roles =
                        CityMember.Role.values();

                if (
                        roleId < 0
                                || roleId >= roles.length
                ) {
                    roleId =
                            CityMember.Role.RESIDENT.ordinal();
                }

                CityMember.Role role =
                        roles[roleId];

                /*
                 * Владелец всегда OWNER.
                 *
                 * Никто другой не может загрузиться
                 * как OWNER из сохранения.
                 */

                if (
                        playerId.equals(owner)
                ) {

                    role =
                            CityMember.Role.OWNER;

                } else if (
                        role == CityMember.Role.OWNER
                ) {

                    role =
                            CityMember.Role.RESIDENT;
                }

                city.addMember(
                        playerId,
                        role
                );
            }

            // =================================================
            // INVITES
            // =================================================

            ListTag invites =
                    cityTag.getList(
                            "Invites",
                            Tag.TAG_COMPOUND
                    );

            for (Tag inviteRaw : invites) {

                CompoundTag inviteTag =
                        (CompoundTag) inviteRaw;

                UUID playerId =
                        inviteTag.getUUID("Player");

                city.addInvite(
                        playerId,
                        inviteTag.hasUUID("Inviter") ? inviteTag.getUUID("Inviter") : null
                );
            }

            ListTag projects = cityTag.getList("Projects", Tag.TAG_COMPOUND);
            for (Tag projectRaw : projects) {
                CompoundTag projectTag = (CompoundTag) projectRaw;
                try {
                    ResourceLocation dimensionId = ResourceLocation.tryParse(
                            projectTag.getString("Dimension")
                    );
                    ResourceLocation depositId = ResourceLocation.tryParse(
                            projectTag.getString("Deposit")
                    );
                    ResourceLocation resourceId = ResourceLocation.tryParse(
                            projectTag.getString("Resource")
                    );
                    int typeId = projectTag.getInt("Type");
                    if (dimensionId == null || depositId == null || resourceId == null
                            || typeId < 0 || typeId >= CityProjectType.values().length) {
                        continue;
                    }
                    city.addProject(new CityProject(
                            projectTag.getUUID("ProjectId"),
                            id,
                            CityProjectType.values()[typeId],
                            dimensionId,
                            NbtUtils.readBlockPos(projectTag, "ControllerPos")
                                    .orElse(BlockPos.ZERO),
                            projectTag.getUUID("Creator"),
                            depositId,
                            resourceId,
                            projectTag.getDouble("Richness")
                    ));
                } catch (RuntimeException ignored) {
                    // Preserve the city even if one project entry is damaged.
                }
            }

            if (cityTag.contains("DevelopmentPrimaryResource", Tag.TAG_STRING)) {
                city.restoreDevelopmentPrimaryResource(ResourceLocation.tryParse(
                        cityTag.getString("DevelopmentPrimaryResource")
                ));
            }

            ListTag development = cityTag.getList("Development", Tag.TAG_COMPOUND);
            for (Tag progressRaw : development) {
                CompoundTag progressTag = (CompoundTag) progressRaw;
                ResourceLocation itemId = ResourceLocation.tryParse(progressTag.getString("Item"));
                long amount = progressTag.getLong("Amount");
                if (itemId != null && amount > 0L) {
                    city.contribute(itemId, amount, Long.MAX_VALUE);
                }
            }

            manager.cities.put(
                    id,
                    city
            );
        }

        for (Tag raw : tag.getList("Relations", Tag.TAG_COMPOUND)) {
            CompoundTag relation = (CompoundTag) raw;
            if (!relation.hasUUID("From") || !relation.hasUUID("To")) continue;
            UUID from = relation.getUUID("From");
            UUID to = relation.getUUID("To");
            if (!from.equals(to) && manager.cities.containsKey(from) && manager.cities.containsKey(to)) {
                manager.trustedCities.computeIfAbsent(from, ignored -> new HashSet<>()).add(to);
            }
        }

        /*
         * Индекс владения участками строится один раз после того, как
         * все города прочитаны и мигрированы: до этого момента
         * пересечения участков ещё не разрешены, и строить индекс
         * по ходу чтения означало бы перетирать записи.
         */
        manager.rebuildClaimIndex();

        if (!manager.migratedCities.isEmpty()) {
            LOGGER.info(
                    "Migrated {} city/cities from the legacy square territory to 50x50 plot claims: {}",
                    manager.migratedCities.size(),
                    manager.cities.values().stream()
                            .filter(city -> manager.migratedCities.contains(city.getId()))
                            .map(City::getName)
                            .toList()
            );
        }

        return manager;
    }

    /**
     * Читает список участков города из NBT.
     *
     * <p>Измерение берётся у города, а не из записи: запись хранит
     * только координаты сетки. Так в одном файле не может оказаться
     * город, «захвативший» участок чужого измерения, и одна повреждённая
     * запись не ломает загрузку — она просто пропускается.
     */
    private static Set<CityPlot> readPlots(ListTag claims) {
        Set<CityPlot> plots = new LinkedHashSet<>();
        for (Tag raw : claims) {
            if (!(raw instanceof CompoundTag claimTag)) {
                continue;
            }
            ResourceLocation dimension =
                    ResourceLocation.tryParse(claimTag.getString("Dimension"));
            if (dimension == null) {
                continue;
            }
            plots.add(new CityPlot(
                    dimension,
                    claimTag.getInt("PlotX"),
                    claimTag.getInt("PlotZ")
            ));
        }
        return plots;
    }

    /**
     * Пересобирает индекс {@code участок → город} из канонических данных.
     *
     * <p>Вызывается после загрузки. Каноническим остаётся набор участков
     * внутри города: индекс — производная величина, и в NBT он не
     * пишется, поэтому рассинхронизация «индекс vs город» невозможна
     * по построению.
     *
     * <p>Пересечения, оставшиеся от старого мира (два города с
     * перекрывающимися квадратами), разрешаются в пользу первого
     * загруженного города, а проигравшему участок удаляется. Иначе
     * один участок принадлежал бы двум городам, и защита зависела бы
     * от порядка обхода.
     */
    private void rebuildClaimIndex() {
        claimOwners.clear();
        for (City city : cities.values()) {
            for (CityPlot plot : city.getClaimedPlots()) {
                UUID previous = claimOwners.putIfAbsent(plot, city.getId());
                if (previous != null && !previous.equals(city.getId())) {
                    City loser = cities.get(previous);
                    if (loser != null) {
                        loser.removeClaim(plot);
                        LOGGER.warn(
                                "City {} lost overlapping plot ({}, {}) to city {} during load",
                                loser.getName(), plot.plotX(), plot.plotZ(),
                                city.getName()
                        );
                    }
                }
            }
            /*
             * Пилон обязан остаться внутри своей территории: без этого
             * у города не было бы ни одного участка, к которому можно
             * было бы присоединить новый, и город стал бы неуправляемым.
             */
            CityPlot pylonPlot = city.getPylonPlot();
            if (!city.hasClaim(pylonPlot)) {
                city.addClaim(pylonPlot);
                claimOwners.put(pylonPlot, city.getId());
                LOGGER.warn(
                        "City {} had no claim covering its pylon; the pylon plot was added back",
                        city.getName()
                );
            }
        }
    }

    // =========================================================
    // SAVE
    // =========================================================

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {

        ListTag cityList =
                new ListTag();

        for (City city : cities.values()) {

            CompoundTag cityTag =
                    new CompoundTag();

            // -------------------------------------------------
            // CITY BASIC DATA
            // -------------------------------------------------

            cityTag.putUUID(
                    "Id",
                    city.getId()
            );

            cityTag.putString(
                    "Name",
                    city.getName()
            );

            cityTag.putUUID(
                    "Owner",
                    city.getOwner()
            );

            cityTag.put(
                    "PylonPos",
                    NbtUtils.writeBlockPos(
                            city.getPylonPos()
                    )
            );

            cityTag.putInt(
                    "Level",
                    city.getLevel()
            );
            cityTag.putString("PylonDimension", city.getPylonDimension().toString());

            cityTag.putBoolean("PylonActive", city.isPylonActive());
            cityTag.putInt("TradeReputation", city.getTradeReputation());

            /*
             * Цвет города. 24 бита без альфы — ровно столько, сколько
             * нужно карте; альфа задаётся при отрисовке и от города
             * не зависит.
             */
            cityTag.putInt("Color", city.getColorRgb());
            cityTag.putString("Description", city.getIdentity().description());
            cityTag.putString("Emblem", city.getIdentity().pixels());
            cityTag.putLong("IdentityRevision", city.getIdentityRevision());

            /*
             * Участки территории. Наличие этой секции — признак города
             * нового формата: при загрузке её отсутствие запускает
             * миграцию со старого квадрата.
             */
            ListTag claims = new ListTag();
            for (CityPlot plot : city.getClaimedPlots()) {
                CompoundTag claimTag = new CompoundTag();
                claimTag.putString("Dimension", plot.dimension().toString());
                claimTag.putInt("PlotX", plot.plotX());
                claimTag.putInt("PlotZ", plot.plotZ());
                claims.add(claimTag);
            }
            cityTag.put("Claims", claims);

            ListTag projects = new ListTag();
            for (CityProject project : city.getProjects()) {
                CompoundTag projectTag = new CompoundTag();
                projectTag.putUUID("ProjectId", project.projectId());
                projectTag.putInt("Type", project.projectType().ordinal());
                projectTag.putString("Dimension", project.dimensionId().toString());
                projectTag.put("ControllerPos", NbtUtils.writeBlockPos(project.controllerPos()));
                projectTag.putUUID("Creator", project.creatorId());
                projectTag.putString("Deposit", project.depositId().toString());
                projectTag.putString("Resource", project.resourceId().toString());
                projectTag.putDouble("Richness", project.richness());
                projects.add(projectTag);
            }
            cityTag.put("Projects", projects);

            ListTag development = new ListTag();
            city.getDevelopmentContributions().forEach((itemId, amount) -> {
                CompoundTag progressTag = new CompoundTag();
                progressTag.putString("Item", itemId.toString());
                progressTag.putLong("Amount", amount);
                development.add(progressTag);
            });
            cityTag.put("Development", development);
            if (city.getDevelopmentPrimaryResource() != null) {
                cityTag.putString(
                        "DevelopmentPrimaryResource",
                        city.getDevelopmentPrimaryResource().toString()
                );
            }

            // -------------------------------------------------
            // MEMBERS
            // -------------------------------------------------

            ListTag members =
                    new ListTag();

            for (
                    CityMember member
                    : city.getMembers()
            ) {

                CompoundTag memberTag =
                        new CompoundTag();

                memberTag.putUUID(
                        "Player",
                        member.getPlayerId()
                );

                memberTag.putInt(
                        "Role",
                        member.getRole().ordinal()
                );

                members.add(
                        memberTag
                );
            }

            cityTag.put(
                    "Members",
                    members
            );

            // -------------------------------------------------
            // INVITES
            // -------------------------------------------------

            ListTag invites =
                    new ListTag();

            for (
                    CityInvite invite
                    : city.getInvites()
            ) {

                CompoundTag inviteTag =
                        new CompoundTag();

                inviteTag.putUUID("Player", invite.getPlayerId()); inviteTag.putUUID("Inviter", invite.getInvokerId());

                invites.add(
                        inviteTag
                );
            }

            cityTag.put(
                    "Invites",
                    invites
            );

            cityList.add(
                    cityTag
            );
        }

        tag.put(
                "Cities",
                cityList
        );

        ListTag relations = new ListTag();
        trustedCities.forEach((from, targets) -> targets.forEach(to -> {
            if (!from.equals(to) && cities.containsKey(from) && cities.containsKey(to)) {
                CompoundTag relation = new CompoundTag();
                relation.putUUID("From", from);
                relation.putUUID("To", to);
                relations.add(relation);
            }
        }));
        tag.put("Relations", relations);

        return tag;
    }

    // =========================================================
    // GET MANAGER
    // =========================================================

    public static CityManager get(
            ServerLevel level
    ) {

        CityManager manager = level
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                CityManager::new,
                                CityManager::load,
                                null
                        ),
                        DATA_NAME
                );
        manager.activeBuildProjects = id -> com.example.examplemod.economy.EconomyManager.get(level).projects().hasActive(id);
        return manager;
    }

    // =========================================================
    // CREATE CITY
    // =========================================================

    /**
     * Основывает город. Только когда участок пилона свободен.
     *
     * @return null, если участок под новым пилоном уже принадлежит другому городу
     */
    public City createCity(
            String name,
            UUID owner,
            BlockPos pylonPos
    ) {

        return createCity(name, owner, net.minecraft.world.level.Level.OVERWORLD.location(), pylonPos);
    }

    /**
     * Основывает город на участке пилона.
     *
     * <p>Город сразу получает один участок 50×50 вокруг пилона, и
     * участок под пилона должен быть свободен: нельзя основать
     * столицу на чужой земле. Старая модель квадратов разрешала
     * перекрытие, но с участками перекрытие означало бы, что один
     * блок принадлежит двум городам, а защита зависела бы от
     * порядка обхода — поэтому здесь явный отказ.
     *
     * @return новый город либо {@code null}, если участок занят
     */
    public City createCity(
            String name,
            UUID owner,
            ResourceLocation pylonDimension,
            BlockPos pylonPos
    ) {

        if (name == null || name.isBlank() || owner == null || pylonPos == null
                || pylonDimension == null) {
            return null;
        }

        CityPlot pylonPlot = CityClaims.pylonPlot(pylonDimension, pylonPos);
        if (claimOwners.containsKey(pylonPlot)) {
            return null;
        }

        UUID id =
                UUID.randomUUID();

        City city =
                new City(id, name, owner, pylonPos, 1, java.util.List.of(), true,
                        java.util.List.of(), null, java.util.Map.of(), pylonDimension);

        cities.put(
                id,
                city
        );
        claimOwners.put(pylonPlot, id);

        setDirty();
        com.example.examplemod.auth.ServerTelemetryBridge.emit("CITY_CREATED", owner, id, "");

        return city;
    }

    // =========================================================
    // GET CITY
    // =========================================================

    public City getCity(
            UUID id
    ) {

        return cities.get(id);
    }

    public List<City> getCities() {
        return List.copyOf(cities.values());
    }

    public boolean trusts(UUID fromCityId, UUID toCityId) {
        return fromCityId != null && toCityId != null && !fromCityId.equals(toCityId)
                && trustedCities.getOrDefault(fromCityId, Set.of()).contains(toCityId);
    }

    public boolean partners(UUID firstCityId, UUID secondCityId) {
        return trusts(firstCityId, secondCityId) && trusts(secondCityId, firstCityId);
    }

    public Set<UUID> trustedCities(UUID cityId) {
        return Set.copyOf(trustedCities.getOrDefault(cityId, Set.of()));
    }

    public boolean setTrusted(UUID fromCityId, UUID toCityId, boolean trusted) {
        if (fromCityId == null || toCityId == null || fromCityId.equals(toCityId)
                || !cities.containsKey(fromCityId) || !cities.containsKey(toCityId)) return false;
        Set<UUID> targets = trustedCities.computeIfAbsent(fromCityId, ignored -> new HashSet<>());
        boolean changed = trusted ? targets.add(toCityId) : targets.remove(toCityId);
        if (targets.isEmpty()) trustedCities.remove(fromCityId);
        if (changed) setDirty();
        return changed;
    }

    private void removeRelations(UUID cityId) {
        trustedCities.remove(cityId);
        trustedCities.values().forEach(targets -> targets.remove(cityId));
        trustedCities.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public City getCityByName(String name) {
        if (name == null) {
            return null;
        }
        for (City city : cities.values()) {
            if (city.getName().equalsIgnoreCase(name.trim())) {
                return city;
            }
        }
        return null;
    }

    public CityProject getProject(UUID projectId) {
        for (City city : cities.values()) {
            CityProject project = city.getProject(projectId);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    public boolean addProject(City city, CityProject project) {
        if (city == null || project == null || cities.get(city.getId()) != city) {
            return false;
        }
        boolean added = city.addProject(project);
        if (added) com.example.examplemod.auth.ServerTelemetryBridge.emit("PROJECT_ADDED", project.creatorId(), project.projectId(), project.projectType().name());
        if (added) {
            setDirty();
        }
        return added;
    }

    public CityProject removeProject(City city, UUID projectId) {
        if (city == null || cities.get(city.getId()) != city) {
            return null;
        }
        CityProject removed = city.removeProject(projectId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public boolean relocateProject(
            CityProject project,
            ResourceLocation dimensionId,
            BlockPos controllerPos
    ) {
        City city = project == null ? null : cities.get(project.cityId());
        if (city == null || city.getProject(project.projectId()) != project) {
            return false;
        }
        CityProject replacement = new CityProject(
                project.projectId(), project.cityId(), project.projectType(), dimensionId,
                controllerPos, project.creatorId(), project.depositId(),
                project.resourceId(), project.richness()
        );
        if (!city.replaceProject(replacement)) {
            return false;
        }
        setDirty();
        return true;
    }

    public void markPylonRelocating(City city) {
        if (city != null && city.isPylonActive()) {
            city.setPylonActive(false);
            setDirty();
        }
    }

    public void markPylonActive(City city) {
        if (city != null && !city.isPylonActive()) {
            city.setPylonActive(true);
            setDirty();
        }
    }

    /**
     * Перепривязывает City Pylon после его переноса.
     *
     * <p><b>Территория при переносе не пересоздаётся.</b> Участки
     * принадлежат городу, а не пилону, поэтому перенос пилона внутри
     * своей территории ничего в ней не меняет. Перенос за пределы
     * своей территории отклоняется: иначе пилон оказался бы вне
     * защищённой земли, а правило смежности перестало бы работать —
     * не от чего было бы расширяться.
     *
     * <p>Перенос в другое измерение переносит форму территории
     * целиком: участки сдвигаются на разницу участков пилона,
     * поэтому новый пилон снова внутри своей земли, а размер и форма
     * территории сохраняются. Ни один участок не исчезает и не
     * создаётся заново.
     *
     * @return true, если перенос выполнен
     */
    public boolean rebindPylon(City city, ResourceLocation dimension, BlockPos pos) {
        if (city == null || dimension == null || pos == null
                || cities.get(city.getId()) != city || city.isPylonActive()) {
            return false;
        }

        CityPlot previousPylonPlot = city.getPylonPlot();
        CityPlot targetPylonPlot = CityClaims.pylonPlot(dimension, pos);

        if (dimension.equals(previousPylonPlot.dimension())) {
            if (!city.ownsBlock(dimension, pos.getX(), pos.getZ())) {
                return false;
            }
            city.rebindPylon(dimension, pos);
        } else {
            /*
             * Смена измерения идёт через rebaseClaims: он перепривязывает
             * пилон первым, потому что участки нового измерения должны
             * совпасть с измерением города, и только потом сдвигает их.
             * При отказе всё возвращается назад целиком.
             */
            if (!rebaseClaims(city, dimension, pos,
                    targetPylonPlot.plotX() - previousPylonPlot.plotX(),
                    targetPylonPlot.plotZ() - previousPylonPlot.plotZ())) {
                return false;
            }
            return true;
        }

        /*
         * Участок пилона обязан принадлежать городу. При переносе в
         * пределах измерения он принадлежал уже; строка остаётся как
         * страховка от будущих путей переноса.
         */
        CityPlot newPylonPlot = city.getPylonPlot();
        if (!city.hasClaim(newPylonPlot)) {
            claimOwners.remove(previousPylonPlot);
            if (city.addClaim(newPylonPlot)) {
                claimOwners.put(newPylonPlot, city.getId());
            }
        }
        setDirty();
        return true;
    }

    /**
     * Переносит территорию города в другое измерение целиком.
     *
     * <p>Участки сдвигаются на разницу участков пилона, поэтому новый
     * пилон снова внутри своей земли, а размер и форма территории
     * сохраняются: ни один участок не исчезает и не создаётся заново.
     *
     * <p>Перенос отменяется целиком, если хотя бы один целевой участок
     * уже занят: частично применённый сдвиг оставил бы индекс и данные
     * города в разных состояниях и отнял бы у города часть земли.
     *
     * @return true, если перенос выполнен
     */
    private boolean rebaseClaims(City city, ResourceLocation dimension, BlockPos pos,
                                 int deltaX, int deltaZ) {
        if (activeBuildProjects.test(city.getId())) return false;
        ResourceLocation previousDimension = city.getPylonDimension();
        BlockPos previousPos = city.getPylonPos();
        List<CityPlot> previousPlots = new ArrayList<>(city.getClaimedPlots());

        List<CityPlot> rebased = new ArrayList<>(previousPlots.size());
        for (CityPlot plot : previousPlots) {
            CityPlot moved = new CityPlot(dimension, plot.plotX() + deltaX, plot.plotZ() + deltaZ);
            if (!moved.isValid()) {
                LOGGER.warn(
                        "Rebase of city {} would leave the world bounds; relocation refused",
                        city.getName());
                return false;
            }
            rebased.add(moved);
        }

        for (CityPlot plot : previousPlots) {
            claimOwners.remove(plot, city.getId());
        }
        city.rebindPylon(dimension, pos);
        city.replaceClaimsRaw(rebased);

        for (CityPlot plot : city.getClaimedPlots()) {
            UUID previous = claimOwners.putIfAbsent(plot, city.getId());
            if (previous != null && !previous.equals(city.getId())) {
                for (CityPlot moved : city.getClaimedPlots()) {
                    claimOwners.remove(moved, city.getId());
                }
                city.rebindPylon(previousDimension, previousPos);
                city.replaceClaimsRaw(previousPlots);
                for (CityPlot restored : previousPlots) {
                    claimOwners.putIfAbsent(restored, city.getId());
                }
                LOGGER.warn(
                        "Rebase of city {} was rolled back: target plots in {} belong to another city",
                        city.getName(), dimension
                );
                return false;
            }
        }
        setDirty();
        return true;
    }

    public void markChanged() {
        setDirty();
    }

    // =========================================================
    // GET CITY BY PYLON
    // =========================================================

    public City getCityByPylon(
            BlockPos pylonPos
    ) {

        for (
                City city
                : cities.values()
        ) {

            if (
                    city.getPylonPos()
                            .equals(pylonPos)
            ) {

                return city;
            }
        }

        return null;
    }

    public City getCityByPylon(ResourceLocation dimension, BlockPos pylonPos) {
        for (City city : cities.values()) {
            if (city.getPylonDimension().equals(dimension)
                    && city.getPylonPos().equals(pylonPos)) {
                return city;
            }
        }
        return null;
    }
    public City removeCityByOwner(UUID player) {

        City city = getCityByOwner(player);

        if (city == null) {
            return null;
        }

        if (activeBuildProjects.test(city.getId())) return null;
        dropCity(city.getId());

        setDirty();

        return city;
    }

    /**
     * Забывает город во всех индексах: города, доверия и владения участками.
     *
     * <p>Единая точка удаления: раньше удаление города забывало про
     * участки, и после удаления они оставались бы занятыми в
     * пространственном индексе, хотя город больше не существует.
     */
    private void dropCity(UUID cityId) {
        cities.remove(cityId);
        removeRelations(cityId);
        claimOwners.values().removeIf(cityId::equals);
    }
    // =========================================================
    // GET CITY BY OWNER
    // =========================================================

    public City getCityByOwner(
            UUID player
    ) {

        for (
                City city
                : cities.values()
        ) {

            if (
                    city.getOwner()
                            .equals(player)
            ) {

                return city;
            }
        }

        return null;
    }
    // =========================================================
    // ВЛАДЕНИЕ УЧАСТКАМИ
    // =========================================================

    /**
     * Город, которому принадлежит координата, или {@code null}.
     *
     * <p>Единственный источник правды о принадлежности координаты
     * городу. Раньше здесь был квадрат вокруг пилона по уровню
     * города, и карта, граница в мире и защита считали территорию
     * каждый раз по-своему. Теперь все три смотрят на один и тот же
     * набор участков.
     *
     * <p>Стоимость — одна запись в хеш-таблице, а не обход всех
     * участков всех городов: этот метод вызывается на каждом
     * разрушении и установке блока.
     *
     * <p>Y не учитывается: участок — это столбец мира.
     */
    public City getCityAt(ResourceLocation dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return null;
        }
        return getCityAt(dimension, pos.getX(), pos.getZ());
    }

    /** То же, что {@link #getCityAt(ResourceLocation, BlockPos)}, но для сырых координат. */
    public City getCityAt(ResourceLocation dimension, int blockX, int blockZ) {
        if (dimension == null) {
            return null;
        }
        UUID cityId = claimOwners.get(CityPlot.at(dimension, blockX, blockZ));
        return cityId == null ? null : cities.get(cityId);
    }

    /**
     * Город, которому принадлежит участок, или {@code null} — участок свободен.
     *
     * <p>Отдельное имя для участка, а не для блока: карта спрашивает
     * именно про участок, и пересчитывать координаты блока в участок
     * ещё раз на стороне вызова незачем.
     */
    public City getClaimOwner(CityPlot plot) {
        if (plot == null || !plot.isValid()) {
            return null;
        }
        UUID cityId = claimOwners.get(plot);
        return cityId == null ? null : cities.get(cityId);
    }

    /** Свободен ли участок. */
    public boolean isPlotFree(CityPlot plot) {
        return plot != null && plot.isValid() && !claimOwners.containsKey(plot);
    }

    /**
     * Занимает город участок.
     *
     * <p>Единственная точка, где территория города растёт, и
     * единственное место, где проверяются правила. Проверки идут в
     * порядке «дешёвые и важные раньше», а ответом является
     * {@link CityClaimResult}: клиент получает конкретную причину
     * отказа, а не тишину.
     *
     * <p><b>Правила.</b>
     * <ol>
     *   <li>город существует и принадлежит этому хранилищу;</li>
     *   <li>участок в пределах мира;</li>
     *   <li>участок в том же измерении, что и пилон города — город
     *       не может владеть землёй в другом мире;</li>
     *   <li>участок не занят другим городом;</li>
     *   <li>участок ещё не принадлежит этому городу;</li>
     *   <li>участок примыкает стороной хотя бы к одному уже
     *       принадлежащему участку — диагональное касание не считается.</li>
     * </ol>
     *
     * <p>Никакой экономики и лимитов здесь сознательно нет: правила
     * расширения сосредоточены в одном месте, поэтому добавление цены
     * или лимита позже — это ещё одно условие в этом методе, а не
     * переписывание модели.
     *
     * @param actor игрок, от имени которого выполняется claim; OWNER или ADMIN
     */
    public CityClaimResult claimPlot(City city, CityPlot plot, UUID actor) {
        if (city == null || cities.get(city.getId()) != city) {
            return CityClaimResult.NO_CITY;
        }
        if (actor == null || !city.isAdmin(actor)) {
            return CityClaimResult.NO_PERMISSION;
        }
        if (plot == null || !plot.isValid()) {
            return CityClaimResult.INVALID_PLOT;
        }
        if (!city.getPylonDimension().equals(plot.dimension())) {
            return CityClaimResult.DIMENSION_MISMATCH;
        }
        if (city.hasClaim(plot)) {
            return CityClaimResult.ALREADY_OWNED;
        }
        UUID ownerId = claimOwners.get(plot);
        if (ownerId != null && !ownerId.equals(city.getId())) {
            return CityClaimResult.OWNED_BY_OTHER_CITY;
        }
        /*
         * Смежность проверяется последней: это самая дорогая проверка
         * из всех, что могут отказать, и она бессмысленна, если
         * участок уже отвергнут по другой причине.
         */
        if (!CityClaims.touchesOwned(city.getClaimedPlots(), plot)) {
            return CityClaimResult.NOT_ADJACENT;
        }

        if (!city.addClaim(plot)) {
            return CityClaimResult.ALREADY_OWNED;
        }
        claimOwners.put(plot, city.getId());
        setDirty();
        com.example.examplemod.auth.ServerTelemetryBridge.emit(
                "CITY_PLOT_CLAIMED", actor, city.getId(), plot.plotX() + ":" + plot.plotZ()
        );
        return CityClaimResult.CLAIMED;
    }

    /**
     * Меняет цвет города.
     *
     * <p>Только OWNER/ADMIN и только цвет из палитры: клиент присылает
     * число, и без проверки в сохранение попал бы цвет, который
     * нельзя ни показать в палитре, ни отрисовать на карте.
     */
    public boolean setCityColor(City city, int rgb, UUID actor) {
        if (city == null || cities.get(city.getId()) != city || actor == null) {
            return false;
        }
        if (!city.isAdmin(actor) || !CityColors.isPreset(rgb)) {
            return false;
        }
        if (!city.applyColor(rgb)) {
            return false;
        }
        setDirty();
        com.example.examplemod.auth.ServerTelemetryBridge.emit(
                "CITY_COLOR_CHANGED", actor, city.getId(), String.format("#%06X", rgb)
        );
        return true;
    }

    /** Public identity is editable only by the canonical city's owner/admin. */
    public String updateIdentity(City city, UUID actor, long revision, int colour, String description, String pixels) {
        if (city == null || cities.get(city.getId()) != city || actor == null || !city.isAdmin(actor)) return "forbidden";
        if (revision != city.getIdentityRevision()) return "conflict";
        if (!CityColors.isPreset(colour) || !CityIdentity.valid(description, pixels)) return "invalid";
        city.applyIdentity(new CityIdentity(description, pixels), colour);
        setDirty();
        return "saved";
    }

    /**
     * Город по координате блока без указания измерения.
     *
     * @deprecated измерение обязательно: город в Нижнем мире и город
     * в Верхнем мире с одинаковыми координатами — разные города.
     * Используйте {@link #getCityAt(ResourceLocation, BlockPos)}.
     */
    @Deprecated
    public City getCityAt(BlockPos pos) {

        for (City city : cities.values()) {

            if (city.getPylonDimension().equals(net.minecraft.world.level.Level.OVERWORLD.location())
                    && city.hasClaim(CityPlot.at(city.getPylonDimension(), pos))) {

                return city;
            }
        }

        return null;
    }
    // =========================================================
    // GET CITY BY MEMBER
    //
    // Ищет игрока среди ВСЕХ участников городов.
    // Нужно для запрета нахождения в нескольких городах.
    // =========================================================

    public City getCityByMember(
            UUID player
    ) {

        for (
                City city
                : cities.values()
        ) {

            if (
                    city.hasMember(player)
            ) {

                return city;
            }
        }

        return null;
    }

    /**
     * City of the player, or {@code null} when they belong to none.
     *
     * <p>Single authorization point for "my city". Requests that are not tied to
     * a pylon cannot ask the server to identify the city: a pylon in the request
     * would be unchecked input, and trusting it would leak another city's data
     * while refusing it would make the request pointless. So the city is
     * resolved from membership alone.
     *
     * <p>{@link #getCityByMember} already matches on membership, so the second
     * check looks redundant. It is kept deliberately: it states the requirement
     * at the point of use, and {@code CityMapReadAccess} is decided from the
     * same two facts, so a later change to how a city is found cannot silently
     * widen access.
     */
    public City getMemberCity(UUID player) {
        City city = getCityByMember(player);
        return city != null && city.hasMember(player) ? city : null;
    }

    // =========================================================
    // HAS CITY
    //
    // Проверяет именно наличие собственного города.
    // =========================================================

    public boolean hasCity(
            UUID player
    ) {

        return getCityByOwner(player) != null;
    }

    // =========================================================
    // CITY COUNT
    // =========================================================

    public int getCityCount() {

        return cities.size();
    }

    // =========================================================
    // GET CITIES WITH INVITE
    // =========================================================

    public List<City> getCitiesWithInvite(
            UUID player
    ) {

        List<City> result =
                new ArrayList<>();

        for (
                City city
                : cities.values()
        ) {

            if (
                    city.hasInvite(player)
            ) {

                result.add(city);
            }
        }

        return result;
    }
    // =========================================================
// REMOVE CITY BY PYLON
// =========================================================

    public City removeCityByPylon(BlockPos pylonPos) {

        City city = getCityByPylon(pylonPos);

        if (city == null) {
            return null;
        }

        if (activeBuildProjects.test(city.getId())) return null;
        dropCity(city.getId());

        setDirty();

        return city;
    }

    /**
     * Снимок владения участками для карты: только то, что игрок имеет
     * право видеть, и только в пределах его измерения.
     *
     * <p>Список один раз собирается из канонического хранилища городов
     * и целиком кешируется клиентом — карта не обходит города и не
     * читает сохранение в цикле кадра.
     *
     * @param ownCityId город самого игрока, может быть {@code null}
     */
    public List<CityTerritoryView> territoryViews(
            ResourceLocation dimension,
            BlockPos origin,
            int range,
            UUID ownCityId,
            java.util.function.Predicate<UUID> masterCity
    ) {
        List<CityTerritoryView> views = new ArrayList<>();
        if (dimension == null) {
            return views;
        }
        List<City> candidates=cities.values().stream().filter(java.util.Objects::nonNull)
                .filter(c->dimension.equals(c.getPylonDimension()))
                .sorted(java.util.Comparator.<City>comparingInt(c->c.getId().equals(ownCityId)?0:1)
                        .thenComparingDouble(c->origin==null?0:c.getPylonPos().distSqr(origin)))
                .toList();
        for (City city : candidates) {
            if (city == null || !dimension.equals(city.getPylonDimension())) {
                continue;
            }
            BlockPos pylon = city.getPylonPos();
            // Include visible claimed land even when its pylon is outside the requested region.
            if (!city.getId().equals(ownCityId) && origin != null && Math.max(
                    Math.abs(pylon.getX() - origin.getX()),
                    Math.abs(pylon.getZ() - origin.getZ())
            ) > range && city.getClaimedPlots().stream().noneMatch(p->
                    p.maxX()>=origin.getX()-(long)range&&p.minX()<=origin.getX()+(long)range
                    &&p.maxZ()>=origin.getZ()-(long)range&&p.minZ()<=origin.getZ()+(long)range)) {
                continue;
            }
            views.add(new CityTerritoryView(
                    city.getId(),
                    city.getName(),
                    city.getColorRgb(),
                    pylon.getX(),
                    pylon.getZ(),
                    city.getMemberCount(),
                    city.getId().equals(ownCityId),
                    masterCity != null && masterCity.test(city.getId()),
                    city.getClaimedPlots()
            ));
            if (views.size() >= com.example.examplemod.network.CityMapPeersPayload.MAX_PEERS) {
                break;
            }
        }
        return views;
    }

    /**
     * Видимая с карты территория одного города.
     *
     * <p>Только то, что нужно для рисования: идентичность, цвет,
     * позиция пилона и участки. Ни прав, ни экономики, ни участников
     * чужих городов в снимок не попадает — карта показывает факт
     * существования территории, а не доступ к управлению.
     */
    public record CityTerritoryView(
            UUID cityId,
            String name,
            int colorRgb,
            int pylonX,
            int pylonZ,
            int residents,
            boolean own,
            boolean master,
            Set<CityPlot> plots
    ) {
    }
}
