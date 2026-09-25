package com.example.examplemod.mine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

final class MineGatewayStorageCodec {

    private static final String ITEMS_TAG = "Items";

    private MineGatewayStorageCodec() {
    }

    static CompoundTag save(List<MineGatewayStoredStack> stacks) {
        CompoundTag root = new CompoundTag();
        ListTag items = new ListTag();
        for (MineGatewayStoredStack stack : stacks) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", stack.slot());
            entry.putString("Item", stack.itemId().toString());
            entry.putInt("Count", stack.count());
            items.add(entry);
        }
        root.put(ITEMS_TAG, items);
        return root;
    }

    static List<MineGatewayStoredStack> load(CompoundTag root) {
        List<MineGatewayStoredStack> result = new ArrayList<>();
        ListTag items = root.getList(ITEMS_TAG, Tag.TAG_COMPOUND);
        boolean[] occupied = new boolean[MineGatewayBuffer.SLOT_COUNT];

        for (int index = 0; index < items.size(); index++) {
            CompoundTag entry = items.getCompound(index);
            int slot = entry.getInt("Slot");
            int count = entry.getInt("Count");
            ResourceLocation itemId = ResourceLocation.tryParse(
                    entry.getString("Item")
            );
            if (slot < 0 || slot >= MineGatewayBuffer.SLOT_COUNT
                    || occupied[slot]
                    || count <= 0 || count > 64
                    || itemId == null) {
                continue;
            }
            occupied[slot] = true;
            result.add(new MineGatewayStoredStack(slot, itemId, count));
        }
        return List.copyOf(result);
    }
}
