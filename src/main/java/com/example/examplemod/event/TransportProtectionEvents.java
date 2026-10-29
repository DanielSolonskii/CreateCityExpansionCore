package com.example.examplemod.event;

import com.example.examplemod.block.SimulatedCompat;
import com.example.examplemod.block.TransportProtection;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Защита транспорта на мировых событиях.
 *
 * <p>Две поверхности:
 * <ul>
 *   <li>блоки sublevel'ов (физические конструкции Create Simulated /
 *       Aeronautics): Sable подмешивает plot-чанки в конвейер
 *       серверного уровня, поэтому vanilla-события разрушения,
 *       использования и установки срабатывают на блоках летящей
 *       конструкции — проверяем принадлежность sublevel'у через
 *       {@link SimulatedCompat};</li>
 *   <li>якоря contraption'ов (подшипник, пулли): разборка
 *       гайковёртом идёт по мировому блоку-якорю, территориальная
 *       защита участков на него не смотрит, если якорь вне
 *       приватов — проверяем содержимое конструкции.</li>
 * </ul>
 */
public class TransportProtectionEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (SimulatedCompat.canModifySubLevelBlock(player, level, event.getPos())) {
            return;
        }
        TransportProtection.deny(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (SimulatedCompat.canModifySubLevelBlock(player, level, event.getPos())) {
            return;
        }
        TransportProtection.deny(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Взаимодействие с блоками sublevel'а (сундуки, клапаны,
        // панели). Грант пульта управления доступом открывает только
        // взаимодействие; гайковёрт — это разборка конструкции, он
        // идёт по строгой проверке прав изменения.
        if (event.getLevel() instanceof ServerLevel level) {
            boolean wrench = !event.getItemStack().isEmpty()
                    && event.getItemStack().getItem() instanceof WrenchItem;
            boolean allowed = wrench
                    ? SimulatedCompat.canModifySubLevelBlock(player, level, event.getPos())
                    : SimulatedCompat.canInteractWithSubLevelBlock(player, level, event.getPos());
            if (!allowed) {
                TransportProtection.deny(player);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
        }

        // Разборка contraption'а гайковёртом по якорю.
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!isAssembleAnchor(state)) {
            return;
        }
        if (TransportProtection.canModifyContraptionAtAnchor(
                player,
                player.serverLevel(),
                event.getPos()
        )) {
            return;
        }
        TransportProtection.deny(player);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    /**
     * Якорь contraption'а, через который Create выполняет разборку.
     * Подшипники и пулли покрывают вращающиеся и подъёмные машины;
     * поезда и вагонетки защищены своими mixin-точками.
     */
    private static boolean isAssembleAnchor(BlockState state) {
        return state.getBlock() instanceof com.simibubi.create.content.contraptions.bearing.BearingBlock
                || state.getBlock() instanceof com.simibubi.create.content.contraptions.pulley.PulleyBlock;
    }
}
