package com.example.examplemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиша открытия карты города — только просмотр.
 *
 * <p><b>Почему карта по клавише ничего не меняет.</b> Карта открывается участнику
 * города в любом месте, без пилона под ногами. Всё, что на ней можно изменить,
 * — это город: занять участок, зарегистрировать поезд, сменить тариф. Всё это
 * требует открытого меню пилона и проверки расстояния, то есть сознательной
 * работы рядом со зданием. Клавиша, работающая откуда угодно, не должна обходить
 * это: поэтому карта по клавише только читает, а право менять остаётся у экрана
 * пилона.
 *
 * <p><b>Почему это не защита.</b> Скрытие действий — удобство, а не безопасность.
 * Настоящая проверка на сервере: изменяющие команды требуют открытого контейнера
 * пилона, поэтому по этому пути изменить город нельзя даже с подделанным пакетом.
 */
public final class CityWorldMapKey {

    private static final KeyMapping OPEN = new KeyMapping(
            "key.createcityexpansioncore.open_world_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.createcityexpansioncore"
    );

    private CityWorldMapKey() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    /**
     * Есть ли нажатие.
     *
     * <p>Клавиша не назначена — {@code consumeClick} на несвязанном бинде берёт
     * нажатие, оставшееся от прошлого назначения, и карта открылась бы сама.
     * Поэтому назначение проверяется явно.
     */
    public static boolean consumeOpen() {
        /*
         * Несвязанный бинд (UNBOUND) отдаёт накопленное нажатие, и карта
         * открылась бы сама — ровно то, что игрок отключил в настройках.
         * Поэтому назначение проверяется до consumeClick.
         */
        if (OPEN.getKey() == InputConstants.UNKNOWN) {
            return false;
        }
        return OPEN.consumeClick();
    }
}