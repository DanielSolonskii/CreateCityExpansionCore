package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Права игрока на чужой территории.
 *
 * <p>Принадлежность координаты городу определяется только участками
 * 50×50 ({@link CityManager#getCityAt}): старый квадрат вокруг пилона
 * по уровню города больше не участвует в защите, поэтому карта,
 * граница в мире и права на блоки не могут разойтись между собой.
 */
public class CityProtectionManager {

    /**
     * Проверяет, может ли игрок изменять блок в указанной позиции.
     */
    public static boolean canInteract(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos
    ) {

        CityManager cityManager = CityManager.get(level.getServer().overworld());

        City city = cityManager.getCityAt(level.dimension().location(), pos);

        if (city == null) {
            return true;
        }

        return cityManager.allowsTerritoryAction(city, player.getUUID(), false);
    }

    public static boolean canModify(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos
    ) {

        CityManager cityManager = CityManager.get(level.getServer().overworld());

        City city = cityManager.getCityAt(level.dimension().location(), pos);

        // Если территории города здесь нет —
        // обычный Minecraft работает как обычно.
        if (city == null) {
            return true;
        }

        // Если участок принадлежит городу —
        // проверяем права игрока.
        return cityManager.allowsTerritoryAction(city, player.getUUID(), true);
    }
}
