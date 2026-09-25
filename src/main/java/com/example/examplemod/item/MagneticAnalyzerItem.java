package com.example.examplemod.item;

import com.example.examplemod.geology.GeologicalRegion;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import com.example.examplemod.network.GeologyAnalysisPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class MagneticAnalyzerItem extends Item {

    public MagneticAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {

        /*
         * =========================================
         * ПРОВЕРЯЕМ ИГРОКА
         * =========================================
         */

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }


        /*
         * =========================================
         * ПОЛУЧАЕМ АНАЛИЗИРУЕМУЮ ПОЗИЦИЮ
         * =========================================
         */

        ItemStack stack = context.getItemInHand();

        BlockPos analyzedPos = context.getClickedPos();


        /*
         * =========================================
         * ПОЛУЧАЕМ ГЕОЛОГИЮ
         * =========================================
         */

        GeologicalRegion region =
                GeologyManager.getRegion(
                        player.serverLevel(),
                        analyzedPos
                );


        /*
         * =========================================
         * ИСПОЛЬЗУЕМ ОДИН ЗАРЯД АНАЛИЗАТОРА
         * =========================================
         */

        stack.hurtAndBreak(
                1,
                player,
                player.getEquipmentSlotForItem(stack)
        );


        /*
         * =========================================
         * ОСТАВШИЕСЯ ИСПОЛЬЗОВАНИЯ
         * =========================================
         */

        int remainingUses =
                stack.getMaxDamage() - stack.getDamageValue();


        /*
         * =========================================
         * ОТПРАВЛЯЕМ РЕЗУЛЬТАТ НА КЛИЕНТ
         * =========================================
         */

        GeologyAnalysisPayload payload =
                new GeologyAnalysisPayload(
                        analyzedPos,
                        GeologyRegionSnapshot.from(region),
                        remainingUses
                );

        player.connection.send(
                payload
        );


        return InteractionResult.SUCCESS;
    }
}
