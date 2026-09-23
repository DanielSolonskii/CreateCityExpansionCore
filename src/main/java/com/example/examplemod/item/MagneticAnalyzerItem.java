package com.example.examplemod.item;

import com.example.examplemod.geology.GeologicalRegion;
import com.example.examplemod.geology.GeologyManager;
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
         * ПОЛУЧАЕМ БОГАТСТВО РЕСУРСОВ
         * =========================================
         */

        int copper = getResource(
                region,
                "copper"
        );

        int iron = getResource(
                region,
                "iron"
        );

        int coal = getResource(
                region,
                "coal"
        );

        int zinc = getResource(
                region,
                "zinc"
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
                        copper,
                        iron,
                        coal,
                        zinc,
                        remainingUses
                );

        player.connection.send(
                payload
        );


        return InteractionResult.SUCCESS;
    }


    /*
     * =========================================
     * ПОЛУЧЕНИЕ РЕСУРСА
     * =========================================
     */

    private int getResource(
            GeologicalRegion region,
            String name
    ) {

        return region.getResources()
                .entrySet()
                .stream()
                .filter(entry ->
                        entry.getKey()
                                .getPath()
                                .equals(name)
                )
                .mapToInt(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }
}