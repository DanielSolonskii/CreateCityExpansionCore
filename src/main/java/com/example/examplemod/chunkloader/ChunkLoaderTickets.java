package com.example.examplemod.chunkloader;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * Chunk forcing, delegated entirely to NeoForge's own forced-chunk system.
 *
 * <p>No fake player, no manual {@code chunk.tick()}, no home-grown ticket loop. This only calls
 * {@link TicketController#forceChunk}, which makes NeoForge write the ticket into its
 * {@code ForcedChunksSavedData} and put a real region ticket on the level's chunk source. Two things then
 * come for free that would otherwise be extra code to get wrong:
 *
 * <ul>
 *   <li><b>Persistence.</b> Tickets are saved with the level and reinstated on load by
 *       {@code ForcedChunkManager.reinstatePersistentChunks}, so a restart restores them without this mod
 *       doing anything.</li>
 *   <li><b>Several loaders in one chunk.</b> The ticket owner is the loader's own {@link BlockPos}, not the
 *       chunk. Two loaders in the same chunk are therefore two independent ticket owners pointing at the
 *       same chunk, and the chunk stays forced while either owner exists. Removing one unforces only that
 *       owner's ticket and leaves the other holding the chunk.</li>
 * </ul>
 *
 * <p>The ticket is ticking, because the point of a chunk loader is that the factory inside it keeps running
 * with no player nearby. The owner is the block position rather than the player UUID, deliberately: a
 * loader must survive its owner logging out, and the per-player limit is
 * {@link ChunkLoaderSavedData}'s job, not the ticket's.
 */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class ChunkLoaderTickets {
    private ChunkLoaderTickets() {
    }

    /**
     * The controller id. It is written into the saved forced-chunk data, so changing it would orphan every
     * ticket a running server has already issued; treat it as frozen once shipped.
     */
    public static final ResourceLocation CONTROLLER_ID =
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "chunk_loader");

    private static TicketController controller;

    /**
     * Registers the controller. NeoForge refuses forceChunk calls for an unregistered id and silently
     * discards that controller's saved tickets on load, so this is mandatory before any placement.
     */
    @SubscribeEvent
    public static void onRegisterControllers(RegisterTicketControllersEvent event) {
        controller = new TicketController(CONTROLLER_ID, ChunkLoaderTickets::validate);
        event.register(controller);
    }

    /**
     * Drops tickets whose block is no longer a chunk loader.
     *
     * <p>This is the ghost-ticket guard. Tickets are persisted independently of the block, so anything that
     * removes a block without going through the ordinary removal hook — a world edit, a corrupted save, an
     * earlier bug — would otherwise leave a chunk forced forever. NeoForge calls this once per level load
     * and removes whatever the callback rejects before the tickets are reinstated.
     */
    private static void validate(ServerLevel level, TicketHelper helper) {
        for (BlockPos owner : new java.util.ArrayList<>(helper.getBlockTickets().keySet())) {
            if (!(level.getBlockState(owner).getBlock() instanceof ChunkLoaderBlock)) {
                helper.removeAllTickets(owner);
            }
        }
    }

    /**
     * Starts forcing the chunk this loader stands in. Exactly one chunk: the one containing {@code pos},
     * never a neighbourhood.
     *
     * @return true if the ticket state changed
     */
    public static boolean activate(ServerLevel level, BlockPos pos) {
        if (controller == null) {
            // ForceChunkManager throws for an unknown controller; a missing registration is a mod-load
            // order bug, and refusing loudly beats a silent loader that never loads anything.
            throw new IllegalStateException("Chunk loader ticket controller is not registered");
        }
        ChunkPos chunk = new ChunkPos(pos);
        return controller.forceChunk(level, pos, chunk.x, chunk.z, true, true);
    }

    /**
     * Stops forcing, releasing this loader's own ticket only. Another loader in the same chunk keeps its own
     * ticket and the chunk stays forced.
     *
     * @return true if the ticket state changed
     */
    public static boolean deactivate(ServerLevel level, BlockPos pos) {
        if (controller == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(pos);
        return controller.forceChunk(level, pos, chunk.x, chunk.z, false, true);
    }
}