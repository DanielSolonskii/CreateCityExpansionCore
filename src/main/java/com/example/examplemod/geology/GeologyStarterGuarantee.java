package com.example.examplemod.geology;

import net.minecraft.core.BlockPos;

/**
 * Мягкая стартовая гарантия ресурсов рядом с опорной точкой
 * мира (обычно Master City).
 *
 * Намеренно вынесена в отдельный hook. Процедурная геология
 * полностью автономна и не знает про города: если владелец
 * сервера не задал опорную точку, гарантия просто не
 * работает, и базовая вероятность месторождений остаётся
 * прежней.
 *
 * Диапазон ориентировочный: 700-1400 блоков. Радиус не
 * создаёт запасов вне процедурного расчёта, а лишь
 * добавляет к запросу ещё одну macro-cell, чтобы рядом с
 * точкой старта не оказалось полностью пустой зоны.
 *
 * TODO(City integration): подключить реальные координаты
 * Master City в точке, где сервер уже знает master city id,
 * например через EconomyManager.masterCityId() и
 * CityManager.getCity(id).getPylonPos(). Зависимость
 * geology -> economy -> city намеренно не создаётся в этом
 * проходе: координаты города должны оставаться внешним
 * входом, а не фундаментом геологии.
 */
public final class GeologyStarterGuarantee {

    /** Нижняя граница рекомендуемого радиуса, в блоках. */
    public static final int MIN_RADIUS_BLOCKS = 700;

    /** Верхняя граница рекомендуемого радиуса, в блоках. */
    public static final int MAX_RADIUS_BLOCKS = 1_400;

    private GeologyStarterGuarantee() {
    }

    /**
     * Задать опорную точку для server-authoritative
     * экземпляра геологии.
     */
    public static void apply(GeologyManager manager, BlockPos anchor) {
        if (manager == null) {
            return;
        }
        manager.setStarterAnchor(
                anchor == null ? null : anchor.immutable()
        );
    }

    /**
     * Находится ли позиция в пределах мягкой гарантии.
     * Используется UI и будущими подсказками, но не генерацией.
     */
    public static boolean withinGuarantee(
            BlockPos anchor,
            BlockPos position,
            int radiusBlocks
    ) {
        if (anchor == null || position == null) {
            return false;
        }
        int clamped = Math.max(
                MIN_RADIUS_BLOCKS,
                Math.min(MAX_RADIUS_BLOCKS, radiusBlocks)
        );
        double dx = anchor.getX() - position.getX();
        double dz = anchor.getZ() - position.getZ();
        return dx * dx + dz * dz <= (double) clamped * clamped;
    }
}