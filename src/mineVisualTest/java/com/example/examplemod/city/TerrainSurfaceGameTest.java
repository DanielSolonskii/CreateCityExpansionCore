package com.example.examplemod.city;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A direct world edit bypasses player place/break events. The active overview must converge
 * through the real level tick, loaded-chunk sampler and pyramid publisher. */
@GameTestHolder("createcityexpansioncore")
@PrefixGameTestTemplate(false)
public final class TerrainSurfaceGameTest {
    @GameTest(template="auger_empty", timeoutTicks=1600)
    public static void overviewFindsDirectSurfaceChange(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = CityMapSavedData.get(level);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos block = new BlockPos(origin.getX(), level.getMaxBuildHeight() - 8, origin.getZ());
        var leaf = new TerrainPyramid.Key(0, Math.floorDiv(block.getX(),32), Math.floorDiv(block.getZ(),32));
        var parent = leaf.parent();
        UUID player = UUID.randomUUID(), view = UUID.randomUUID();
        int heightIndex = (Math.floorMod(block.getZ(),32)+1)*34 + Math.floorMod(block.getX(),32)+1;
        long[] before = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var baseline = data.snapshot(parent);
                    helper.assertTrue(baseline != null, "Initial overview still reducing");
                    before[0] = baseline.version();
                })
                .thenExecute(() -> {
                    data.watch(player,view,parent,level.getGameTime(),before[0]);
                    level.setBlockAndUpdate(block, Blocks.GOLD_BLOCK.defaultBlockState());
                })
                .thenWaitUntil(() -> {
                    var detailed = data.snapshot(leaf);
                    var overview = data.snapshot(parent);
                    helper.assertTrue(detailed != null && detailed.heights()[heightIndex] == block.getY(),
                            "Direct block edit is awaiting resampling");
                    helper.assertTrue(overview != null && overview.version() > before[0],
                            "Overview is awaiting the modified child");
                })
                .thenExecute(() -> {
                    data.unwatch(player,view);
                    level.removeBlock(block,false);
                })
                .thenSucceed();
    }
}
