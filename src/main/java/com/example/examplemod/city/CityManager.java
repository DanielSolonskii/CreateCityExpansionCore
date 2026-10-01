package com.example.examplemod.city;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

public class CityManager extends SavedData {

    private static final String DATA_NAME =
            "createcityexpansioncore_cities";

    private final Map<UUID, City> cities =
            new HashMap<>();
    private final Map<UUID, Set<UUID>> trustedCities = new HashMap<>();

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
                 * Ð’Ð»Ð°Ð´ÐµÐ»ÐµÑ† Ð²ÑÐµÐ³Ð´Ð° OWNER.
                 *
                 * ÐÐ¸ÐºÑ‚Ð¾ Ð´Ñ€ÑƒÐ³Ð¾Ð¹ Ð½Ðµ Ð¼Ð¾Ð¶ÐµÑ‚ Ð·Ð°Ð³Ñ€ÑƒÐ·Ð¸Ñ‚ÑŒÑÑ
                 * ÐºÐ°Ðº OWNER Ð¸Ð· ÑÐ¾Ñ…Ñ€Ð°Ð½ÐµÐ½Ð¸Ñ.
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

        return manager;
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

        return level
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                CityManager::new,
                                CityManager::load,
                                null
                        ),
                        DATA_NAME
                );
    }

    // =========================================================
    // CREATE CITY
    // =========================================================

    public City createCity(
            String name,
            UUID owner,
            BlockPos pylonPos
    ) {

        return createCity(name, owner, net.minecraft.world.level.Level.OVERWORLD.location(), pylonPos);
    }

    public City createCity(
            String name,
            UUID owner,
            ResourceLocation pylonDimension,
            BlockPos pylonPos
    ) {

        UUID id =
                UUID.randomUUID();

        City city =
                new City(id, name, owner, pylonPos, 1, java.util.List.of(), true,
                        java.util.List.of(), null, java.util.Map.of(), pylonDimension);

        cities.put(
                id,
                city
        );

        setDirty();

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

    public boolean rebindPylon(City city, ResourceLocation dimension, BlockPos pos) {
        if (city == null || dimension == null || pos == null
                || cities.get(city.getId()) != city || city.isPylonActive()) {
            return false;
        }
        city.rebindPylon(dimension, pos);
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

        cities.remove(city.getId());
        removeRelations(city.getId());

        setDirty();

        return city;
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
    public City getCityAt(BlockPos pos) {

        for (City city : cities.values()) {

            BlockPos center = city.getPylonPos();

            int halfSize = city.getTerritorySize() / 2;

            int minX = center.getX() - halfSize;
            int maxX = center.getX() + halfSize;

            int minZ = center.getZ() - halfSize;
            int maxZ = center.getZ() + halfSize;

            if (pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ) {

                return city;
            }
        }

        return null;
    }
    // =========================================================
    // GET CITY BY MEMBER
    //
    // Ð˜Ñ‰ÐµÑ‚ Ð¸Ð³Ñ€Ð¾ÐºÐ° ÑÑ€ÐµÐ´Ð¸ Ð’Ð¡Ð•Ð¥ ÑƒÑ‡Ð°ÑÑ‚Ð½Ð¸ÐºÐ¾Ð² Ð³Ð¾Ñ€Ð¾Ð´Ð¾Ð².
    // ÐÑƒÐ¶Ð½Ð¾ Ð´Ð»Ñ Ð·Ð°Ð¿Ñ€ÐµÑ‚Ð° Ð½Ð°Ñ…Ð¾Ð¶Ð´ÐµÐ½Ð¸Ñ Ð² Ð½ÐµÑÐºÐ¾Ð»ÑŒÐºÐ¸Ñ… Ð³Ð¾Ñ€Ð¾Ð´Ð°Ñ….
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

    // =========================================================
    // HAS CITY
    //
    // ÐŸÑ€Ð¾Ð²ÐµÑ€ÑÐµÑ‚ Ð¸Ð¼ÐµÐ½Ð½Ð¾ Ð½Ð°Ð»Ð¸Ñ‡Ð¸Ðµ ÑÐ¾Ð±ÑÑ‚Ð²ÐµÐ½Ð½Ð¾Ð³Ð¾ Ð³Ð¾Ñ€Ð¾Ð´Ð°.
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

        cities.remove(city.getId());
        removeRelations(city.getId());

        setDirty();

        return city;
    }
}
