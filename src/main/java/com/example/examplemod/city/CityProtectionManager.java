package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CityProtectionManager {

    /**
     * Проверяет, может ли игрок изменять блок в указанной позиции.
     */
    public static boolean canInteract(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos
    ) {

        CityManager cityManager = CityManager.get(level);

        City city = cityManager.getCityAt(pos);

        if (city == null) {
            return true;
        }

        return city.hasMember(player.getUUID());
    }
    public static boolean canModify(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos
    ) {

        CityManager cityManager = CityManager.get(level);

        City city = cityManager.getCityAt(pos);

        // Если территории города здесь нет —
        // обычный Minecraft работает как обычно.
        if (city == null) {
            return true;
        }

        // Если территория принадлежит городу —
        // проверяем права игрока.
        return city.canBuild(player.getUUID());
    }
}