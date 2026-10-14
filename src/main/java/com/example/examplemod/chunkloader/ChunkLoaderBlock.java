package com.example.examplemod.chunkloader;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The chunk loader block.
 *
 * <p>It carries no gameplay logic of its own beyond the two things that must hold no matter how the block is
 * disturbed: it cannot be pushed, and it releases its ticket the moment it stops being a chunk loader.
 *
 * <p>{@link #onRemove} is the bypass guard. Vanilla calls it from {@code LevelChunk#setBlockState} for
 * <em>every</em> way a block can stop existing, not only a player breaking it: pistons, explosions, water and
 * fire, {@code /setblock}, world edits, and Create contraptions picking the block up or dropping it again. So a
 * loader cannot be moved or deleted by a non-player route while quietly keeping its chunk forced — the ticket
 * and the registry entry go away with the block. The registry is kept in step by
 * {@link ChunkLoaderLifecycle#release}.
 *
 * <p>Pistons are refused by {@code pushReaction(BLOCK)} in the properties rather than by an event: that is the
 * vanilla mechanism for "this block is not movable", and it refuses the push before any state changes.
 *
 * <p>Ownership is decided in {@link ChunkLoaderEvents#onEntityPlace}, which applies the per-player limit before
 * the block is allowed to exist. This class only handles the successful case.
 */
public final class ChunkLoaderBlock extends BaseEntityBlock {
    /** Serialisation codec required by every {@link BaseEntityBlock} subclass in this version. */
    public static final MapCodec<ChunkLoaderBlock> CODEC = simpleCodec(ChunkLoaderBlock::new);

    public ChunkLoaderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * The placement already passed the limit check, so ownership is claimed here and the owner is told the
     * result.
     *
     * <p>Placing by anything other than a player claims no owner: there is nobody to charge, and a loader with
     * no owner is inert until the reconcile sweep tidies it away.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof net.minecraft.server.level.ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            String name = com.example.examplemod.auth.PlayerNames.name(server, player.getUUID());
            ChunkLoaderLifecycle.activate(serverLevel, pos, player.getUUID(), name);
            player.sendSystemMessage(ChunkLoaderLifecycle.placedMessage(server, player.getUUID()));
        }
    }

    /**
     * Releases the ticket and the registry entry whenever this block stops being a chunk loader.
     *
     * <p>Delegated to {@link ChunkLoaderLifecycle#release} so the identical teardown runs whether the block was
     * broken, blown up, pushed or picked up by a contraption.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            ChunkLoaderLifecycle.release(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Not replaceable by a placement, and not washed away by a fluid.
     *
     * <p>Both overloads exist because they answer different questions: the context one decides whether a
     * block may be put there at all, the fluid one whether water and lava may flow into the spot. Either way
     * a loader is only ever removed by being broken, which is the path that runs ownership checks.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return false;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    public static BlockBehaviour.Properties chunkLoaderProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0F)
                .requiresCorrectToolForDrops()
                // Refuses a piston outright: the block stays where its owner put it and its ticket stays valid.
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }
}
