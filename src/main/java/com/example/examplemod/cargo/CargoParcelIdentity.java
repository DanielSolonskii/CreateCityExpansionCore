package com.example.examplemod.cargo;

import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

/** Physical parcel identity survives native ItemStack transport and CCEC address/assignment edits. */
public final class CargoParcelIdentity {
    static final String KEY = "ccec:parcel_id";
    private CargoParcelIdentity() {}

    public static Optional<UUID> id(ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }
    static Optional<UUID> read(CompoundTag custom) {
        if (!custom.contains(KEY)) return Optional.empty();
        if (!custom.hasUUID(KEY)) throw new IllegalArgumentException("Invalid CCEC parcel UUID");
        return Optional.of(custom.getUUID(KEY));
    }
    static void ensure(CompoundTag custom) {
        if (read(custom).isEmpty()) custom.putUUID(KEY, UUID.randomUUID());
    }
    static void stamp(ItemStack stack) {
        if (!PackageItem.isPackage(stack) || CargoAddress.parse(PackageItem.getAddress(stack)).isEmpty()) return;
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (read(custom).isPresent()) return;
        ensure(custom); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
}
