package com.example.examplemod.chunkloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The authoritative registry of placed chunk loaders, and the only place the personal limit lives.
 *
 * <p>Why this exists instead of counting block entities near a player: a loader in the Nether and a loader
 * in the Overworld both count against the same three, and both must still count after the chunk they sit in
 * unloads and after a restart. Anything derived from loaded chunks therefore under-reports the moment a
 * player walks away, which is exactly when the limit has to hold. So ownership is persisted here, keyed by
 * player UUID, and the loaders a player owns are the loaders they are charged for.
 *
 * <p>The data is stored on the overworld's {@link net.minecraft.world.level.storage.DimensionDataStorage} on
 * purpose: the limit is global across dimensions, so the registry has to live in one place rather than
 * once per level.
 *
 * <p>This class holds no live level or chunk references. Nothing here may keep a {@link ServerLevel} around,
 * because these records outlive level unloads and a stale reference would leak the whole dimension.
 *
 * <p>Chunk tickets themselves are <em>not</em> stored here. They belong to NeoForge's own forced-chunk data
 * and are managed by {@link ChunkLoaderTickets}; this registry only answers "who owns what, and may they
 * place another".
 */
public final class ChunkLoaderSavedData extends SavedData {
    /** One source of truth for the limit. Nothing else may hardcode the number. */
    public static final int MAX_LOADERS_PER_PLAYER = 3;

    private static final String DATA_NAME = "createcityexpansioncore_chunk_loaders";
    private static final String KEY_OWNERS = "Owners";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_POS = "Pos";

    /** Owner UUID to the loaders they own. Insertion-ordered so messages and reconciles are stable. */
    private final Map<UUID, Set<LoaderRef>> byOwner = new HashMap<>();

    /**
     * A loader's location: the dimension it stands in and the exact block. The dimension is part of the
     * identity because the same coordinates in two dimensions are two loaders, charged separately.
     */
    public record LoaderRef(ResourceLocation dimension, BlockPos pos) {
        public String describe() {
            return dimension + " " + pos.toShortString();
        }
    }

    public static ChunkLoaderSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChunkLoaderSavedData::new, ChunkLoaderSavedData::load, null),
                DATA_NAME);
    }

    /** How many loaders this player currently owns, across every dimension. */
    public int count(UUID owner) {
        Set<LoaderRef> loaders = byOwner.get(owner);
        return loaders == null ? 0 : loaders.size();
    }

    /** Whether this player may place another one. */
    public boolean canPlaceMore(UUID owner) {
        return count(owner) < MAX_LOADERS_PER_PLAYER;
    }

    /** Slots left, for the "used / 3" message. */
    public int used(UUID owner) {
        return count(owner);
    }

    /**
     * Records a placed loader.
     *
     * <p>Idempotent on purpose: registering the same position twice must not consume two of the owner's
     * slots, because the chunk it forces is already held. A set keyed on dimension and position gives that
     * for free, and it is the same reason a double placement event cannot produce a 4/3.
     *
     * @return true if this added a new entry, false if the loader was already registered
     */
    public boolean register(UUID owner, ResourceLocation dimension, BlockPos pos) {
        boolean added = byOwner.computeIfAbsent(owner, key -> new LinkedHashSet<>())
                .add(new LoaderRef(dimension, pos.immutable()));
        if (added) {
            setDirty();
        }
        return added;
    }

    /**
     * Forgets a loader.
     *
     * @return true if an entry was actually removed
     */
    public boolean unregister(UUID owner, ResourceLocation dimension, BlockPos pos) {
        Set<LoaderRef> loaders = byOwner.get(owner);
        if (loaders == null || !loaders.remove(new LoaderRef(dimension, pos))) {
            return false;
        }
        if (loaders.isEmpty()) {
            byOwner.remove(owner);
        }
        setDirty();
        return true;
    }

    /** Removes a loader whoever owns it, by position. Used when a block disappears without a player. */
    public boolean unregisterAt(ResourceLocation dimension, BlockPos pos) {
        LoaderRef ref = new LoaderRef(dimension, pos);
        for (Map.Entry<UUID, Set<LoaderRef>> entry : byOwner.entrySet()) {
            if (entry.getValue().remove(ref)) {
                if (entry.getValue().isEmpty()) {
                    byOwner.remove(entry.getKey());
                }
                setDirty();
                return true;
            }
        }
        return false;
    }

    /** The UUID that owns the loader standing at this exact position, or null when there is none. */
    public UUID ownerAt(ResourceLocation dimension, BlockPos pos) {
        LoaderRef ref = new LoaderRef(dimension, pos);
        for (Map.Entry<UUID, Set<LoaderRef>> entry : byOwner.entrySet()) {
            if (entry.getValue().contains(ref)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Every registered loader, for the reconcile pass. */
    public List<Map.Entry<UUID, LoaderRef>> allEntries() {
        List<Map.Entry<UUID, LoaderRef>> out = new ArrayList<>();
        for (Map.Entry<UUID, Set<LoaderRef>> entry : byOwner.entrySet()) {
            for (LoaderRef ref : entry.getValue()) {
                out.add(Map.entry(entry.getKey(), ref));
            }
        }
        return out;
    }

    public int totalLoaders() {
        int total = 0;
        for (Set<LoaderRef> loaders : byOwner.values()) {
            total += loaders.size();
        }
        return total;
    }

    /** Test seam: drops all state. */
    public void clear() {
        byOwner.clear();
        setDirty();
    }

    /** Test seam: direct view for assertions. */
    public Map<UUID, Set<LoaderRef>> view() {
        return Collections.unmodifiableMap(byOwner);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, Set<LoaderRef>> entry : byOwner.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(KEY_OWNER, entry.getKey());
            ListTag loaders = new ListTag();
            for (LoaderRef ref : entry.getValue()) {
                CompoundTag loaderTag = new CompoundTag();
                loaderTag.putString(KEY_DIMENSION, ref.dimension().toString());
                loaderTag.put(KEY_POS, NbtUtils.writeBlockPos(ref.pos()));
                loaders.add(loaderTag);
            }
            ownerTag.put(KEY_OWNERS, loaders);
            owners.add(ownerTag);
        }
        tag.put(KEY_OWNERS, owners);
        return tag;
    }

    public static ChunkLoaderSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkLoaderSavedData data = new ChunkLoaderSavedData();
        ListTag owners = tag.getList(KEY_OWNERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag ownerTag = owners.getCompound(i);
            if (!ownerTag.hasUUID(KEY_OWNER)) {
                continue;
            }
            UUID owner = ownerTag.getUUID(KEY_OWNER);
            ListTag loaders = ownerTag.getList(KEY_OWNERS, Tag.TAG_COMPOUND);
            for (int j = 0; j < loaders.size(); j++) {
                CompoundTag loaderTag = loaders.getCompound(j);
                if (!loaderTag.contains(KEY_DIMENSION, Tag.TAG_STRING)) {
                    continue;
                }
                BlockPos pos = NbtUtils.readBlockPos(loaderTag, KEY_POS).orElse(BlockPos.ZERO);
                ResourceLocation dimension = ResourceLocation.parse(loaderTag.getString(KEY_DIMENSION));
                data.byOwner.computeIfAbsent(owner, key -> new LinkedHashSet<>())
                        .add(new LoaderRef(dimension, pos));
            }
        }
        return data;
    }

    /** The dimension key a level reports, used to keep registry keys and level lookups in one format. */
    public static ResourceLocation dimensionKey(Level level) {
        return level.dimension().location();
    }
}