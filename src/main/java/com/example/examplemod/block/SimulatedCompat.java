package com.example.examplemod.block;

import com.example.examplemod.city.TransportGrantStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Чистая обёртка над защитой sublevel'ов для всегда-загружаемого
 * кода.
 *
 * <p>{@link TransportSubLevelProtection} трогает классы Sable и
 * Simulated, поэтому напрямую ссылаться на него из общего кода
 * нельзя — без Aeronautics класс не загрузится. Здесь единственный
 * вход: проверка наличия Simulated (безопасная, по имени класса с
 * {@code initialize = false}) и только затем переход к защищённому
 * классу.
 */
public final class SimulatedCompat {

    private static boolean checked;
    private static boolean present;

    private SimulatedCompat() {
    }

    /**
     * Может ли игрок изменить блок, если он принадлежит sublevel'у с
     * зарегистрированным терминалом. Без Simulated всегда true.
     */
    public static boolean canModifySubLevelBlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        if (!isPresent()) {
            return true;
        }
        return TransportSubLevelProtection.canModifyBlockAt(player, level, pos);
    }

    /**
     * Может ли игрок взаимодействовать (ПКМ) с блоком sublevel'а.
     * Грант пульта управления доступом открывает взаимодействие,
     * но не изменение: разборка гайковёртом остаётся на строгой
     * проверке {@link #canModifySubLevelBlock}. Без Simulated всегда true.
     */
    public static boolean canInteractWithSubLevelBlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        if (!isPresent()) {
            return true;
        }
        return TransportSubLevelProtection.canInteractWithBlockAt(player, level, pos);
    }

    /**
     * Переключает грант пульта у блока физической конструкции.
     * Без Simulated — {@link TransportGrantStatus#NOT_PHYSICAL}:
     * вызывающий уходит на статический путь участков.
     */
    public static TransportGrantStatus toggleTransportBlockGrant(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        if (!isPresent()) {
            return TransportGrantStatus.NOT_PHYSICAL;
        }
        return TransportSubLevelProtection.toggleBlockGrantAt(player, level, pos);
    }

    public static boolean isPresent() {
        if (!checked) {
            checked = true;
            try {
                Class.forName(
                        "dev.simulated_team.simulated.Simulated",
                        false,
                        SimulatedCompat.class.getClassLoader()
                );
                present = true;
            } catch (Throwable ignored) {
                present = false;
            }
        }
        return present;
    }
}
