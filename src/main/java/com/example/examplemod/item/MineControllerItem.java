package com.example.examplemod.item;

import com.example.examplemod.mine.MineControllerItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class MineControllerItem extends BlockItem {

    public MineControllerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        MineControllerItemData.Data data = MineControllerItemData.read(stack).orElse(null);
        if (data == null || !data.configured()) {
            tooltip.add(Component.translatable(
                    "tooltip.createcityexpansioncore.mine_controller.unconfigured"
            ).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!data.creatorName().isBlank()) {
            tooltip.add(Component.translatable(
                    "tooltip.createcityexpansioncore.mine_controller.owner", data.creatorName())
                    .withStyle(ChatFormatting.GRAY));
        }
        Component resource = data.resourceId() == null
                ? Component.translatable("gui.createcityexpansioncore.common.unavailable")
                : Component.translatable(
                "resource.createcityexpansioncore." + data.resourceId().getPath()
        );
        tooltip.add(Component.translatable(
                "tooltip.createcityexpansioncore.mine_controller.resource", resource
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.createcityexpansioncore.mine_controller.locked")
                .withStyle(ChatFormatting.GOLD));
        if (data.commissioned()) {
            if (!data.cityName().isBlank()) {
                tooltip.add(Component.translatable(
                        "tooltip.createcityexpansioncore.mine_controller.city", data.cityName())
                        .withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable(
                    "tooltip.createcityexpansioncore.mine_controller.registered")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

}
