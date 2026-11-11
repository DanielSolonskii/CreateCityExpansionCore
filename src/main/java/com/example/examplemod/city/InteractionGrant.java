package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Точечное разрешение на взаимодействие (ПКМ) с одним блоком.
 *
 * <p>Пульт управления доступом открывает конкретный блок города для
 * right-click любого игрока — даже не члена города. Разрешение
 * привязано к позиции, измерению и к тому, какой блок стоял в момент
 * выдачи: если блок заменили, разрешение больше не работает
 * ({@link CityManager#allowsInteraction} снимает его при первой
 * проверке).
 *
 * <p>Разрешение не является правом безопасности уровня членства: игрок
 * всё ещё не может ни ломать, ни ставить блоки. Открытый сундук и
 * открытая дверь — это осознанное решение города, а не обход защиты.
 */
public record InteractionGrant(
        ResourceLocation dimension,
        BlockPos pos,
        ResourceLocation blockId
) {
}
