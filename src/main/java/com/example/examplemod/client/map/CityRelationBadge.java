package com.example.examplemod.client.map;

import com.example.examplemod.network.CityRelationSnapshot.Status;
import net.minecraft.client.gui.GuiGraphics;

/** Compact arrows read from the viewer's city: outgoing, incoming or reciprocal. */
public final class CityRelationBadge {
    private CityRelationBadge() {}

    public static void draw(GuiGraphics g, int x, int y, Status status, boolean enabled) {
        if (status == Status.NONE) return;
        int color = !enabled ? MapPalette.TEXT_FAINT : switch (status) {
            case PARTNER -> 0xFF65D8AF;
            case TRUSTED -> 0xFF79BBD8;
            case TRUSTS_US -> 0xFFBA9FDB;
            default -> throw new AssertionError(status);
        };
        g.fill(x-5,y-3,x+6,y+4,0xFF172228);
        g.hLine(x-3,x+3,y,color);
        if (status != Status.TRUSTS_US) {
            g.vLine(x+2,y-1,y+1,color);
            g.vLine(x+1,y-2,y+2,color);
        }
        if (status != Status.TRUSTED) {
            g.vLine(x-2,y-1,y+1,color);
            g.vLine(x-1,y-2,y+2,color);
        }
    }
}
