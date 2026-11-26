package com.example.examplemod.machine;

import com.example.examplemod.CreateCityExpansionCore;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlock;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class ValvePainting {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof PanelPainterItem painter)
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ValveHandleBlock)) return;

        // Create also turns the valve from this event, before the normal item hooks run.
        var result = painter.useOn(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec()));
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    private ValvePainting() {}
}
