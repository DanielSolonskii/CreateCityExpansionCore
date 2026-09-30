package com.example.examplemod.economy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

public final class ShippingManifestItemData {
    private static final String ROOT = "CcecManifest";
    private ShippingManifestItemData() {}

    public static ItemStack create(net.minecraft.world.item.Item item, UUID manifestId) {
        ItemStack stack = new ItemStack(item);
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag value = new CompoundTag();
        value.putUUID("Id", manifestId);
        custom.put(ROOT, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return stack;
    }

    public static Optional<UUID> read(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains(ROOT, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag value = custom.getCompound(ROOT);
        return value.hasUUID("Id") ? Optional.of(value.getUUID("Id")) : Optional.empty();
    }
}
