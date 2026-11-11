package com.example.examplemod.client.uiedit;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиша включения редактора UI — F7, категория «Create City Expansion Core».
 *
 * <p>Паттерн повторяет {@code CityWorldMapKey}: несвязанный бинд сам по себе
 * отдаёт накопленное нажатие, поэтому назначение проверяется явно, иначе
 * редактор открыл бы себя на самом старте.
 */
public final class UiEditKey {

    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.createcityexpansioncore.uiedit.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.createcityexpansioncore"
    );

    private UiEditKey() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    /** Есть ли нажатие F7 (с учётом того, что бинд мог быть снят). */
    public static boolean consumeToggle() {
        if (TOGGLE.getKey() == InputConstants.UNKNOWN) {
            return false;
        }
        return TOGGLE.consumeClick();
    }
}
