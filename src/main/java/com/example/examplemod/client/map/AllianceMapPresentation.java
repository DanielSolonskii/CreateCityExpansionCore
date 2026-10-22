package com.example.examplemod.client.map;
import com.example.examplemod.client.AllianceClient;
import net.minecraft.client.gui.GuiGraphics;
import java.util.UUID;
/** Badges and focus frames only: an alliance never implies ownership of connecting land. */
public final class AllianceMapPresentation {
    private static final int BADGE=6,FOCUS_RADIUS=11;
    private AllianceMapPresentation(){}
    public static void marker(GuiGraphics g,int x,int y,UUID city){var a=AllianceClient.forCity(city);if(a==null)return;int color=0xFF000000|a.getInt("Color");
        g.fill(x+6,y-11,x+6+BADGE,y-11+BADGE,0xFF152024);g.renderOutline(x+7,y-10,BADGE-2,BADGE-2,color);
        if(AllianceClient.focusedCities.contains(city)){g.renderOutline(x-FOCUS_RADIUS,y-FOCUS_RADIUS,FOCUS_RADIUS*2+1,FOCUS_RADIUS*2+1,0xFF152024);g.renderOutline(x-FOCUS_RADIUS+1,y-FOCUS_RADIUS+1,FOCUS_RADIUS*2-1,FOCUS_RADIUS*2-1,color);}
    }
}
