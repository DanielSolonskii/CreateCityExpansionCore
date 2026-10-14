package com.example.examplemod.chunkloader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Everything the chunk loader listens to.
 *
 * <p>Three jobs, kept apart on purpose:
 *
 * <ol>
 *   <li><b>The limit.</b> Checked in {@link BlockEvent.EntityPlaceEvent}, which fires after the block state
 *       has been written but before the placement is accepted. Cancelling it makes the game restore the block
 *       snapshots itself and skip the stack decrement, so a refused fourth loader costs the player neither
 *       the block nor the item, and no ticket is ever issued.</li>
 *   <li><b>Ownership of destruction.</b> {@link PlayerInteractEvent.LeftClickBlock} stops a stranger before
 *       damage starts, {@link BlockEvent.BreakEvent} is the backstop for any break that skipped the click, and
 *       {@link ExplosionEvent.Detonate} takes the loader out of blast damage so TNT cannot delete someone
 *       else's factory.</li>
 *   <li><b>The reconcile sweep.</b> A cheap periodic pass that notices a loader whose block is gone and tears
 *       it down. This is the catch-all for removal routes that have no event of their own, and it is what
 *       makes a desynchronised registry heal itself instead of holding a chunk open forever.</li>
 * </ol>
 *
 * <p>Pistons and contraptions are handled without an event, in the block itself: {@code pushReaction}
 * BLOCK stops a piston from moving a loader, and {@code onRemove} releases the ticket for any removal
 * route at all, including contraption assembly. See {@link ChunkLoaderBlock}.
 *
 * <p>Refusal messages are throttled per player per reason, because a player can hold down left click on a
 * stranger's loader and every attempt arrives as a separate event.
 */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class ChunkLoaderEvents {
    /** How often the reconcile sweep runs, in ticks. Every five seconds is plenty for a safety net. */
    private static final int RECONCILE_INTERVAL_TICKS = 100;

    /** Minimum gap between two identical refusal messages for one player. */
    private static final long REFUSAL_COOLDOWN_MILLIS = 4_000L;

    private static int ticksSinceReconcile;

    private ChunkLoaderEvents() {
    }

    // ------------------------------------------------------------------ the limit

    /**
     * Refuses a placement that would take its owner past the personal limit.
     *
     * <p>Cancelling is the safe direction: the game rolls the placement back and leaves the item in the
     * player's hand, so a refusal can neither destroy the loader nor leave the player short an item.
     */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getPlacedBlock().getBlock() instanceof ChunkLoaderBlock)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // Not placed by a player, so nobody can be charged for it. The reconcile sweep tidies up if this
            // was a stray placement by some other mechanism.
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        if (registry.canPlaceMore(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        notifyRefusal(player, "message.createcityexpansioncore.chunk_loader.limit_reached");
    }

    // ------------------------------------------------------- ownership of destruction

    /**
     * A left click that starts breaking. Cancelling here means no damage is applied, so nothing drops and the
     * loader keeps running.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isLoader(player.level(), event.getPos())) {
            return;
        }
        if (ChunkLoaderLifecycle.mayBreak(player, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        notifyRefusal(player, "message.createcityexpansioncore.chunk_loader.not_owner");
    }

    /**
     * A break already in progress, including one from a path that skipped the click. This is the backstop
     * behind the click check, and it is also where an authorised break is recorded so the removal message
     * can name the owner once the block is actually gone.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!isLoader(player.level(), event.getPos())) {
            return;
        }
        if (!ChunkLoaderLifecycle.mayBreak(player, event.getPos())) {
            event.setCanceled(true);
            notifyRefusal(player, "message.createcityexpansioncore.chunk_loader.not_owner");
            return;
        }
        // The owner is about to break it. onRemove fires synchronously inside this break, so a note left here
        // is picked up there, where the registry entry still exists and the new count is known.
        ChunkLoaderLifecycle.expectRemoval(player, event.getPos());
    }

    /**
     * Keeps a loader out of blast damage.
     *
     * <p>Without this, a creeper would delete a stranger's factory and there is no ownership to appeal to in
     * the explosion path. Only the loader is filtered out; the rest of the blast is untouched.
     */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> isLoader(level, pos));
    }

    // -------------------------------------------------------------- reconcile sweep

    /**
     * Notices loaders whose block no longer exists.
     *
     * <p>There is no event for "this block vanished in a way I did not see", so the registry is checked
     * periodically instead. It only looks at loaders whose chunk is currently loaded and skips the rest,
     * which is both cheap and the correct choice: an unloaded chunk cannot be inspected, and the ticket there
     * was reinstated from saved data and is expected to match its block.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticksSinceReconcile < RECONCILE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceReconcile = 0;

        MinecraftServer server = event.getServer();
        ChunkLoaderSavedData registry = ChunkLoaderSavedData.get(server);
        for (var entry : registry.allEntries()) {
            ResourceLocation dimension = entry.getValue().dimension();
            BlockPos pos = entry.getValue().pos();
            // The registry stores a plain ResourceLocation, so it is turned back into a dimension key here.
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            if (!isLoader(level, pos)) {
                ChunkLoaderLifecycle.release(level, pos);
            }
        }
    }

    // ---------------------------------------------------------------------- helpers

    private static boolean isLoader(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ChunkLoaderBlock;
    }

    /** Throttles a refusal so holding left click does not turn the chat into spam. */
    private static void notifyRefusal(ServerPlayer player, String key) {
        if (!refusals(player.getUUID(), key)) {
            return;
        }
        player.sendSystemMessage(Component.translatable(key));
    }

    private static final Map<String, Long> LAST_REFUSAL = new HashMap<>();

    private static boolean refusals(UUID player, String key) {
        long now = System.currentTimeMillis();
        String id = player + "|" + key;
        Long last = LAST_REFUSAL.get(id);
        if (last != null && now - last < REFUSAL_COOLDOWN_MILLIS) {
            return false;
        }
        LAST_REFUSAL.put(id, now);
        // Keep the map from growing without bound on a long-lived server.
        if (LAST_REFUSAL.size() > 256) {
            LAST_REFUSAL.entrySet().removeIf(entry -> now - entry.getValue() >= REFUSAL_COOLDOWN_MILLIS * 20L);
        }
        return true;
    }
}
