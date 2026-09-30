package com.example.examplemod.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class ShippingManifestItem extends Item {
    public ShippingManifestItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ShippingManifestItemData.read(stack).ifPresentOrElse(
                id -> tooltip.add(Component.translatable("item.createcityexpansioncore.shipping_manifest.id", id.toString()).withStyle(ChatFormatting.GRAY)),
                () -> tooltip.add(Component.translatable("item.createcityexpansioncore.shipping_manifest.invalid").withStyle(ChatFormatting.RED)));
        tooltip.add(Component.translatable("item.createcityexpansioncore.shipping_manifest.server_authoritative").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
