package com.example.examplemod.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Receipts for incremental cancellation settlement; the money remains in existing escrow. */
final class FreightCancellation {
    final Map<UUID, Long> paid = new LinkedHashMap<>();
    long refunded;
    long releasedQuantity;
    boolean bondReturned;
    boolean closed;

    long paidTotal() { return paid.values().stream().reduce(0L, Math::addExact); }

    CompoundTag save(UUID subject) {
        var tag = new CompoundTag();
        tag.putUUID("Subject", subject);
        tag.putLong("Refunded", refunded);
        tag.putLong("ReleasedQuantity", releasedQuantity);
        tag.putBoolean("BondReturned", bondReturned);
        tag.putBoolean("Closed", closed);
        var recipients = new ListTag();
        paid.forEach((city, amount) -> {
            var receipt = new CompoundTag();
            receipt.putUUID("City", city);
            receipt.putLong("Amount", amount);
            recipients.add(receipt);
        });
        tag.put("Paid", recipients);
        return tag;
    }

    static FreightCancellation load(CompoundTag tag) {
        var result = new FreightCancellation();
        result.refunded = Math.max(0L, tag.getLong("Refunded"));
        result.releasedQuantity = Math.max(0L, tag.getLong("ReleasedQuantity"));
        result.bondReturned = tag.getBoolean("BondReturned");
        result.closed = tag.getBoolean("Closed");
        for (Tag raw : tag.getList("Paid", Tag.TAG_COMPOUND)) {
            var receipt = (CompoundTag) raw;
            if (receipt.hasUUID("City") && receipt.getLong("Amount") > 0L)
                result.paid.put(receipt.getUUID("City"), receipt.getLong("Amount"));
        }
        result.paidTotal();
        return result;
    }
}
