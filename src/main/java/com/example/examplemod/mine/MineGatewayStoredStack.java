package com.example.examplemod.mine;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

record MineGatewayStoredStack(
        int slot,
        ResourceLocation itemId,
        int count
) {

    MineGatewayStoredStack {
        Objects.requireNonNull(itemId, "itemId");
        if (slot < 0 || slot >= MineGatewayBuffer.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid gateway slot: " + slot);
        }
        if (count <= 0 || count > 64) {
            throw new IllegalArgumentException("Invalid gateway stack count: " + count);
        }
    }
}
