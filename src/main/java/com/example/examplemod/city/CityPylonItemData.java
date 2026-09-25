package com.example.examplemod.city;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

public final class CityPylonItemData {

    private static final String ROOT = "createcityexpansioncore:city_pylon";
    private static final String CITY_ID = "CityId";

    private CityPylonItemData() {
    }

    public static void write(ItemStack stack, UUID cityId) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag data = new CompoundTag();
        data.putUUID(CITY_ID, cityId);
        custom.put(ROOT, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public static Optional<UUID> read(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains(ROOT, CompoundTag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag data = custom.getCompound(ROOT);
        return data.hasUUID(CITY_ID) ? Optional.of(data.getUUID(CITY_ID)) : Optional.empty();
    }
}
