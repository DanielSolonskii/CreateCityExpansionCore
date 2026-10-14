package com.example.examplemod.chunkloader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The one place a chunk loader is created or torn down, shared by every route.
 *
 * <p>A loader's lifetime is touched from many directions — a player breaking it, a piston, an explosion, a
 * contraption picking it up, a world edit, the reconcile sweep — and every one of them has to do exactly the
 * same two things: drop the ticket and drop the registry entry. Two copies of that cleanup is how a ghost
 * ticket happens, so they all funnel through here.
 *
 * <p>Nothing in this class keeps a live level reference; a level is only ever passed through.
 */
public final class ChunkLoaderLifecycle {
    private ChunkLoaderLifecycle() {
    }

    /**
     * Registers a freshly placed loader and starts forcing its chunk.
     *
     * <p>The registry entry is written before the ticket so a second placement in the same tick cannot observe
     * a loader that already forces a chunk but has not been charged for. That ordering is what keeps two nearly
     * simultaneous placements from producing a 4/3.
     *
     * @return true if this position was newly registered; false if it was already known
     */
    public static boolean activate(ServerLevel level, BlockPos pos, UUID owner, String ownerName) {
        ResourceLocation dimension = ChunkLoaderSavedData.dimensionKey(level);
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(level.getServer());
        boolean added = registry.register(owner, dimension, pos);

        if (level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity blockEntity) {
            blockEntity.setOwner(owner, ownerName);
        }
        ChunkLoaderTickets.activate(level, pos);
        return added;
    }

    /**
     * Removes the ticket and the registry entry for a loader standing at {@code pos}, whoever owns it.
     *
     * <p>Safe to call where no loader stands: a contraption dropping a block back into an old spot, or a double
     * removal, are no-ops rather than errors.
     *
     * @return true if a loader was actually torn down
     */
    public static boolean release(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        MinecraftServer server = serverLevel.getServer();
        ResourceLocation dimension = ChunkLoaderSavedData.dimensionKey(serverLevel);

        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        UUID owner = registry.ownerAt(dimension, pos);
        boolean removedFromRegistry = registry.unregisterAt(dimension, pos);

        // The ticket goes whether or not the registry still knew about it. A loader missing from the registry
        // must not keep forcing its chunk, and a ticket without a registry entry must not linger either.
        boolean releasedTicket = ChunkLoaderTickets.deactivate(serverLevel, pos);

        PendingRemoval pending = EXPECTED_REMOVALS.remove(key(dimension, pos));
        if (pending != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online.getUUID().equals(pending.owner())) {
                    online.sendSystemMessage(removedMessage(server, pending.owner()));
                }
            }
        }
        return removedFromRegistry || releasedTicket;
    }

    /**
     * Whether this player may break the loader at {@code pos}.
     *
     * <p>Only the owner and server staff may. A stranger's attempt is refused outright, so the block never
     * takes damage and nothing drops.
     */
    public static boolean mayBreak(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        // Staff override, so an operator can always service the server.
        if (player.hasPermissions(2)) {
            return true;
        }
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        UUID owner = registry.ownerAt(ChunkLoaderSavedData.dimensionKey(player.level()), pos);
        return owner != null && owner.equals(player.getUUID());
    }

    // ------------------------------------------------------------------ removal note

    /**
     * An authorised break, recorded just before the block goes so the removal message can be addressed.
     *
     * <p>{@code onRemove} fires synchronously inside the break and has no player left to hand, so the note is
     * left here and collected by the very next {@link #release}. Removals nobody asked for — a piston, a world
     * edit, the sweep — record nothing and therefore stay silent, which is correct: there is nobody to tell.
     */
    private static final Map<String, PendingRemoval> EXPECTED_REMOVALS = new HashMap<>();

    private record PendingRemoval(UUID owner) {
    }

    public static void expectRemoval(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        ResourceLocation dimension = ChunkLoaderSavedData.dimensionKey(player.level());
        UUID owner = registry.ownerAt(dimension, pos);
        if (owner != null) {
            EXPECTED_REMOVALS.put(key(dimension, pos), new PendingRemoval(owner));
        }
    }

    // ------------------------------------------------------------------- messaging

    /** "Chunk loader placed. Used: n of 3." — always read after the placement, never before. */
    public static Component placedMessage(MinecraftServer server, UUID owner) {
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        return Component.translatable(
                "message.createcityexpansioncore.chunk_loader.placed",
                registry.used(owner),
                ChunkLoaderSavedData.MAX_LOADERS_PER_PLAYER);
    }

    /** "Chunk loader removed. Used: n of 3." — read after the registry entry is already gone. */
    public static Component removedMessage(MinecraftServer server, UUID owner) {
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        return Component.translatable(
                "message.createcityexpansioncore.chunk_loader.removed",
                registry.used(owner),
                ChunkLoaderSavedData.MAX_LOADERS_PER_PLAYER);
    }

    private static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }
}
