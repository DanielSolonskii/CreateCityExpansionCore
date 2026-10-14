package com.example.examplemod.chunkloader;

import java.util.UUID;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores who placed the loader, so a placed block can be reasoned about without the registry.
 *
 * <p>The UUID here is a convenience copy, not the authority. {@link ChunkLoaderSavedData} is what enforces
 * the limit, because it survives the chunk unloading; this block entity exists so that a player standing
 * next to a loader can be told who owns it, and so ownership is readable in a world dump.
 *
 * <p>The name is display-only. Ownership is never compared by name, so a rename cannot hand a loader to a
 * different player or take one away.
 */
public final class ChunkLoaderBlockEntity extends BlockEntity {
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_OWNER_NAME = "OwnerName";

    private UUID owner;
    private String ownerName = "";

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.CHUNK_LOADER_BLOCK_ENTITY.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    /** Display-only name of the placing player, empty when unknown. */
    public String ownerName() {
        return ownerName;
    }

    public boolean hasOwner() {
        return owner != null;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID(KEY_OWNER, owner);
        }
        if (!ownerName.isEmpty()) {
            tag.putString(KEY_OWNER_NAME, ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        ownerName = tag.getString(KEY_OWNER_NAME);
    }
}