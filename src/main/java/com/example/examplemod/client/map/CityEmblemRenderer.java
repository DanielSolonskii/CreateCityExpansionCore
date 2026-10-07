package com.example.examplemod.client.map;

import com.example.examplemod.city.CityIdentity;
import net.minecraft.client.gui.GuiGraphics;

/** Native pixel art, drawn as horizontal runs without texture allocations. */
public final class CityEmblemRenderer {
    private CityEmblemRenderer() { }
    public static void draw(GuiGraphics graphics, CityIdentity identity, int colour, int x, int y, int scale) {
        String pixels = identity.pixels();
        for (int row = 0; row < CityIdentity.SIZE; row++) {
            for (int column = 0; column < CityIdentity.SIZE;) {
                char ink = pixels.charAt(row * CityIdentity.SIZE + column);
                int end = column + 1;
                while (end < CityIdentity.SIZE && pixels.charAt(row * CityIdentity.SIZE + end) == ink) end++;
                if (ink != '0') graphics.fill(x + column * scale, y + row * scale, x + end * scale, y + (row + 1) * scale, CityIdentity.colour(Character.digit(ink, 16), colour));
                column = end;
            }
        }
    }
}
