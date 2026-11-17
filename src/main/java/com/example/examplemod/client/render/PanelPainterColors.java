package com.example.examplemod.client.render;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.machine.PanelPainterItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Only reservoir faces use tint index zero; metal, grips and fittings retain their materials. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PanelPainterColors {
    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0
                ? 0xff000000 | PanelPainterItem.color(stack).getTextureDiffuseColor() : -1,
                CreateCityExpansionCore.PANEL_PAINTER.get());
    }
}
