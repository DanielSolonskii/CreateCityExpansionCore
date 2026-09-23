package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CityManager extends SavedData {

    private static final String DATA_NAME =
            "createcityexpansioncore_cities";

    private final Map<UUID, City> cities =
            new HashMap<>();

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
                            level
                    );

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
                        playerId
                );
            }

            manager.cities.put(
                    id,
                    city
            );
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

                inviteTag.putUUID(
                        "Player",
                        invite.getPlayerId()
                );

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

        UUID id =
                UUID.randomUUID();

        City city =
                new City(
                        id,
                        name,
                        owner,
                        pylonPos
                );

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
    public City removeCityByOwner(UUID player) {

        City city = getCityByOwner(player);

        if (city == null) {
            return null;
        }

        cities.remove(city.getId());

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

        cities.remove(city.getId());

        setDirty();

        return city;
    }
}