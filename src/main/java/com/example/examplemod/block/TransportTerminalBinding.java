package com.example.examplemod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/** Versioned vessel-local metadata. Never accesses a world or Sable. */
public record TransportTerminalBinding(UUID sublevelId, UUID cityId, @Nullable UUID token, BlockPos localPos) {
    public static final String KEY = "createcityexpansioncore:transport_terminal";
    private static final int VERSION = 1;

    public TransportTerminalBinding { localPos = localPos.immutable(); }

    @Nullable
    public static TransportTerminalBinding read(@Nullable CompoundTag userData, UUID expectedSublevel) {
        if (userData == null || !userData.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = userData.getCompound(KEY);
        if (tag.getInt("Version") != VERSION || !tag.hasUUID("SubLevel") || !tag.hasUUID("City")
                || !tag.contains("Position", Tag.TAG_LONG)
                || !tag.getUUID("SubLevel").equals(expectedSublevel)) return null;
        return new TransportTerminalBinding(expectedSublevel, tag.getUUID("City"),
                tag.hasUUID("Token") ? tag.getUUID("Token") : null, BlockPos.of(tag.getLong("Position")));
    }

    /** Preserve other mods' data and avoid rewriting identical metadata on every click. */
    public CompoundTag store(@Nullable CompoundTag userData) {
        if (equals(read(userData, sublevelId))) return userData;
        CompoundTag result = userData == null ? new CompoundTag() : userData.copy();
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putUUID("SubLevel", sublevelId);
        tag.putUUID("City", cityId);
        if (token != null) tag.putUUID("Token", token);
        tag.putLong("Position", localPos.asLong());
        result.put(KEY, tag);
        return result;
    }

    public static CompoundTag clear(@Nullable CompoundTag userData) {
        if (userData == null) return new CompoundTag();
        if (!userData.contains(KEY)) return userData;
        CompoundTag result = userData.copy();
        result.remove(KEY);
        return result;
    }
}
