package com.example.examplemod.economy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

public final class ShipmentTokenItemData {
    private static final String ROOT = "CcecShipment";
    private static final String TOKEN = "Token";
    private ShipmentTokenItemData() {}

    public static void write(ItemStack stack, UUID tokenId) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag value = new CompoundTag();
        value.putUUID(TOKEN, tokenId);
        custom.put(ROOT, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public static Optional<UUID> read(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains(ROOT, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag value = custom.getCompound(ROOT);
        return value.hasUUID(TOKEN) ? Optional.of(value.getUUID(TOKEN)) : Optional.empty();
    }

    public static ItemStack withoutToken(ItemStack stack) {
        if (read(stack).isEmpty()) return stack.copy();
        ItemStack copy = stack.copy();
        CompoundTag custom = copy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.remove(ROOT);
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return copy;
    }
}
