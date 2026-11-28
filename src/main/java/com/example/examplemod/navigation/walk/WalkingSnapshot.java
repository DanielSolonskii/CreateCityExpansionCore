package com.example.examplemod.navigation.walk;

import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.city.TerrainPyramid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/** Request-scoped, lazily sampled collision flags. Only the server thread reads already-loaded FULL chunks. */
final class WalkingSnapshot {
    enum Passage { OPEN, BLOCKED, UNKNOWN }
    static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int CENTER = 1, ALL = 31;
    private static final int MAX_CELLS = 20_000;
    private static final VoxelShape FOOTPRINT = Shapes.box(.19,.99,.19,.81,1,.81);
    private static final VoxelShape[] SWEPT = {
            Shapes.box(.19,0,.19,.81,1,.81), Shapes.box(.19,0,0,.81,1,.81),
            Shapes.box(.19,0,.19,1,1,.81), Shapes.box(.19,0,.19,.81,1,1), Shapes.box(0,0,.19,.81,1,.81)
    };
    private record Cell(int clear, boolean floor) {}
    private static final Cell AIR = new Cell(ALL, false), SOLID = new Cell(0,true),
            OBSTACLE = new Cell(0,false), UNKNOWN = new Cell(0,false);
    private final ServerPlayer actor;
    private final ServerLevel level;
    private final Map<Long,Cell> cells = new HashMap<>();
    private final Map<Long,Boolean> nearbyChunks = new HashMap<>();
    private final Map<BlockState,Cell> shapes = new HashMap<>();
    boolean full;

    WalkingSnapshot(ServerPlayer actor) { this.actor = actor; level = actor.serverLevel(); }

    Passage stand(BlockPos feet) {
        var ground = cell(feet.below());
        if (ground == UNKNOWN) return Passage.UNKNOWN;
        if (!ground.floor()) return Passage.BLOCKED;
        return clear(feet, CENTER);
    }

    Passage cross(BlockPos from, BlockPos to, Direction direction) {
        var landing = stand(to);
        if (landing != Passage.OPEN) return landing;
        int dy = to.getY() - from.getY();
        int outgoing = mask(direction), incoming = mask(direction.getOpposite());
        // For a full-block jump the player rises on the low side before crossing the lip.
        BlockPos at = dy > 0 ? from.above() : from;
        BlockPos across = dy < 0 ? to.above() : to;
        var start = clear(at, outgoing);
        if (start != Passage.OPEN) return start;
        // A jump peaks above the landing. Keep additional headroom over the high side too.
        if (dy > 0) {
            var overhead = cell(to.above(2));
            if (overhead == UNKNOWN) return Passage.UNKNOWN;
            if ((overhead.clear() & incoming) == 0) return Passage.BLOCKED;
        }
        return clear(across, incoming);
    }

    private Passage clear(BlockPos feet, int mask) {
        var body = cell(feet);
        var head = cell(feet.above());
        if (body == UNKNOWN || head == UNKNOWN) return Passage.UNKNOWN;
        return (body.clear() & head.clear() & mask) != 0 ? Passage.OPEN : Passage.BLOCKED;
    }

    private static int mask(Direction d) {
        return switch (d) { case NORTH -> 2; case EAST -> 4; case SOUTH -> 8; case WEST -> 16;
            default -> throw new IllegalArgumentException("Horizontal movement required"); };
    }

    private Cell cell(BlockPos p) {
        long key = p.asLong();
        var cached = cells.get(key);
        if (cached != null) return cached;
        if (cells.size() >= MAX_CELLS) { full = true; return UNKNOWN; }
        Cell result = sample(p);
        cells.put(key, result);
        return result;
    }

    private Cell sample(BlockPos p) {
        if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p.getX()+.19,p.getZ()+.19)
                || !level.getWorldBorder().isWithinBounds(p.getX()+.81,p.getZ()+.81)) return OBSTACLE;
        int cx = p.getX() >> 4, cz = p.getZ() >> 4;
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx,cz);
        if (chunk == null) return UNKNOWN;
        var chunkPos = chunk.getPos();
        boolean near = nearbyChunks.computeIfAbsent(chunkPos.toLong(), k ->
                chunkPos.equals(actor.chunkPosition()) || level.getChunkSource().chunkMap.getPlayers(chunkPos,false).contains(actor));
        if (!near && !publicSurface(chunk,p)) return UNKNOWN;
        var state = chunk.getBlockState(p);
        if (state.isAir()) return AIR;
        // Collision which consults a block entity or live neighbors cannot be frozen as a state-only sample.
        if (state.getBlock().hasDynamicShape()) return UNKNOWN;
        return shapes.computeIfAbsent(state, s -> describe(s,p));
    }

    private boolean publicSurface(LevelChunk chunk, BlockPos p) {
        // Outside the player's visible chunks, only the already surveyed, currently exposed surface can be used.
        var data = CityMapSavedData.get(level).terrain(new TerrainPyramid.Key(0,p.getX() >> 5,p.getZ() >> 5));
        if (data == null) return false;
        int i = (p.getZ() & 31) * 32 + (p.getX() & 31);
        int y = data.heights()[i];
        return data.colors()[i] != 0 && y != TerrainPyramid.NO_HEIGHT && p.getY() >= y
                && p.getY() >= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX() & 15,p.getZ() & 15);
    }

    private static Cell describe(BlockState s, BlockPos p) {
        if ((s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock)
                && !s.getValue(BlockStateProperties.OPEN)) return OBSTACLE;
        if (!s.getFluidState().isEmpty() || s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES)
                || s.is(Blocks.CACTUS) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.POWDER_SNOW)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.COBWEB)
                || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_GATEWAY)
                || s.is(Blocks.POINTED_DRIPSTONE) || s.is(Blocks.SCAFFOLDING)) return OBSTACLE;
        var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE,p);
        if (shape.isEmpty()) return AIR;
        if (shape == Shapes.block()) return SOLID;
        if (shape.min(Direction.Axis.X) < 0 || shape.max(Direction.Axis.X) > 1
                || shape.min(Direction.Axis.Z) < 0 || shape.max(Direction.Axis.Z) > 1) return UNKNOWN;
        int clear = 0;
        for (int i=0; i<SWEPT.length; i++)
            if (!Shapes.joinIsNotEmpty(SWEPT[i],shape,BooleanOp.AND)) clear |= 1 << i;
        boolean floor = shape.max(Direction.Axis.Y) == 1 && !Shapes.joinIsNotEmpty(FOOTPRINT,shape,BooleanOp.ONLY_FIRST);
        return new Cell(clear,floor);
    }
}
