package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityProject;
import com.example.examplemod.city.CityProjectType;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.electrical.DrillUnitBlock;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import com.example.examplemod.network.DrillPlacementFeedback;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class IndustrialAugerGameTest {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "AugerTest"), ClientInformation.createDefault());
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        player.setYRot(Direction.SOUTH.toYRot());
        player.setXRot(90);
        return player;
    }

    private static void place(GameTestHelper helper, ServerPlayer player, BlockPos target, boolean success) {
        place(helper, player, target, Direction.UP, success);
    }

    private static void place(GameTestHelper helper, ServerPlayer player, BlockPos target, Direction face, boolean success) {
        var support = target.relative(face.getOpposite());
        var oldSupport = helper.getLevel().getBlockState(support);
        helper.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*.5, face.getStepY()*.5, face.getStepZ()*.5), face, support, false);
        var stack = new ItemStack(CreateCityExpansionCore.DRILL_UNIT_ITEM.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.getLevel().setBlockAndUpdate(support, oldSupport);
        helper.assertTrue(result.consumesAction() == success, "Placement outcome: " + result);
        helper.assertTrue(stack.getCount() == (success ? 1 : 2), "Exactly one item per complete auger");
    }

    private static void assertVolume(GameTestHelper helper, BlockPos base, boolean present) {
        assertVolume(helper, base, present, Direction.DOWN);
    }

    private static void assertVolume(GameTestHelper helper, BlockPos base, boolean present, Direction facing) {
        for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
            var actual = helper.getLevel().getBlockState(base.offset(offset));
            helper.assertTrue(present ? actual == CreateCityExpansionCore.DRILL_UNIT.get()
                            .partState(offset.getX(), offset.getY(), offset.getZ(), facing) : actual.isAir(),
                    "Eight linked cells: " + offset + " -> " + actual);
        }
    }

    private static int drops(GameTestHelper helper, BlockPos base) {
        int count = 0;
        for (var item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(5))) {
            if (item.getItem().is(CreateCityExpansionCore.DRILL_UNIT_ITEM.get())) count += item.getItem().getCount();
            item.discard();
        }
        return count;
    }

    @GameTest(template = "auger_empty", timeoutTicks = 200)
    public static void placementAndEveryPartDropsOneItem(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        var clicked = helper.absolutePos(new BlockPos(7, 3, 7));
        for (Direction direction : Direction.values()) {
            player.setYRot(direction.getAxis().isHorizontal() ? direction.toYRot() : 0);
            player.setXRot(direction == Direction.DOWN ? 90 : direction == Direction.UP ? -90 : 0);
            Direction face = direction.getOpposite();
            var base = clicked.offset(Math.min(face.getStepX(), 0), Math.min(face.getStepY(), 0), Math.min(face.getStepZ(), 0));
            for (boolean shift : new boolean[]{false, true}) {
                player.setShiftKeyDown(shift);
                Direction tip = shift ? direction.getOpposite() : direction;
                for (BlockPos broken : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
                    place(helper, player, clicked, face, true);
                    assertVolume(helper, base, true, tip);
                    level.destroyBlock(base.offset(broken), true);
                    assertVolume(helper, base, false);
                    helper.assertTrue(drops(helper, base) == 1, "One drop from " + broken + " facing " + tip);
                }
            }
        }
        player.setYRot(Direction.SOUTH.toYRot());
        player.setXRot(90);
        player.setShiftKeyDown(false);
        for (BlockPos broken : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
            place(helper, player, clicked, true);
            var creative = helper.makeMockPlayer(GameType.CREATIVE);
            var pos = clicked.offset(broken);
            var state = level.getBlockState(pos);
            state.getBlock().playerWillDestroy(level, pos, state, creative);
            level.removeBlock(pos, false);
            assertVolume(helper, clicked, false);
            helper.assertTrue(drops(helper, clicked) == 0, "Creative destruction has no drops");
        }
        // Two touching augers must not share ownership of any cell.
        place(helper, player, clicked, true);
        place(helper, player, clicked.east(2), true);
        level.destroyBlock(clicked.above().south().east(), true);
        assertVolume(helper, clicked, false);
        assertVolume(helper, clicked.east(2), true);
        level.destroyBlock(clicked.east(2), true);
        helper.assertTrue(drops(helper, clicked) == 2, "Adjacent augers yield two items");
        helper.succeed();
    }

    @GameTest(template = "auger_empty")
    public static void blockedVolumeAndCanceledPlacementAreAtomic(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        var base = helper.absolutePos(new BlockPos(6, 3, 6));
        // Exclude the clicked cell: vanilla correctly tries the next free face of a solid clicked block.
        var feedback = new java.util.ArrayList<BlockPos>();
        Consumer<DrillPlacementFeedback.Failed> failed = event -> {
            if (event.level == level) feedback.add(event.origin);
        };
        NeoForge.EVENT_BUS.addListener(failed);
        try {
            for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
                if (offset.equals(BlockPos.ZERO)) continue;
                var obstruction = base.offset(offset);
                level.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
                int messages = feedback.size();
                place(helper, player, base, false);
                helper.assertTrue(feedback.size() == messages + 1 && feedback.getLast().equals(base), "Failure frame uses exact attempted volume");
                helper.assertTrue(level.getBlockState(obstruction).is(Blocks.STONE), "Obstacle is preserved");
                level.removeBlock(obstruction, false);
                assertVolume(helper, base, false);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(failed);
        }
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == player) event.setCanceled(true);
        };
        feedback.clear();
        NeoForge.EVENT_BUS.addListener(failed);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.EntityPlaceEvent.class, cancel);
        try { place(helper, player, base, false); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        assertVolume(helper, base, false);
        helper.assertTrue(drops(helper, base) == 0, "Rollback must not duplicate augers");
        helper.runAfterDelay(2, () -> {
            NeoForge.EVENT_BUS.unregister(failed);
            helper.assertTrue(feedback.size() == 1 && feedback.getFirst().equals(base),
                    "Final server cancellation reports the attempted volume, including later LOWEST listeners");
            helper.succeed();
        });
    }

    @GameTest(template = "auger_empty")
    public static void orientationSurvivesNbtRotationAndMirror(GameTestHelper helper) {
        var block = CreateCityExpansionCore.DRILL_UNIT.get();
        var lookup = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        var packet = new DrillPlacementFeedback.Failure(helper.getLevel().dimension().location(), new BlockPos(-33, 91, 48));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            DrillPlacementFeedback.Failure.CODEC.encode(buffer, packet);
            helper.assertTrue(DrillPlacementFeedback.Failure.CODEC.decode(buffer).equals(packet), "Remote failure preserves dimension and volume origin");
        } finally { buffer.release(); }
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("Name", "createcityexpansioncore:drill_unit");
        helper.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(lookup, legacy).getValue(DrillUnitBlock.FACING) == Direction.DOWN,
                "Missing facing in old saves defaults down");
        for (Direction facing : Direction.values()) {
            for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
                int x = offset.getX(), y = offset.getY(), z = offset.getZ();
                var state = block.partState(x, y, z, facing);
                helper.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(lookup, net.minecraft.nbt.NbtUtils.writeBlockState(state)) == state,
                        "Every oriented part survives NBT round-trip");
                helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90) == block.partState(1-z, y, x, Rotation.CLOCKWISE_90.rotate(facing)),
                        "Whole structure rotation updates part and facing");
                helper.assertTrue(state.mirror(Mirror.FRONT_BACK) == block.partState(1-x, y, z, Mirror.FRONT_BACK.mirror(facing)),
                        "Whole structure mirror updates part and facing");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "auger_empty", timeoutTicks = 200)
    public static void allTiersRotateAndCountFourAugers(GameTestHelper helper) {
        var level = helper.getLevel();
        var target = helper.absolutePos(new BlockPos(10, 2, 10));
        for (Tier tier : Tier.values()) {
            var plan = MineBlueprints.current().plan(tier);
            helper.assertTrue(plan.materials(false).get(CreateCityExpansionCore.DRILL_UNIT_ITEM.get()) == 4,
                    "Every mine requires four auger items");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlueprintEditorWorld.place(level, plan, target, facing);
                var controller = plan.worldPosition(new BlockPos(4, 1, 0), target, facing);
                helper.assertTrue(MineMultiblock.validate(level, controller).assembled(), "Mine rotates as one structure");
                var positions = plan.parts().stream().map(part -> plan.worldPosition(part.position(), target, facing)).toList();
                var min = new BlockPos(positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                        positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                        positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
                var max = new BlockPos(positions.stream().mapToInt(BlockPos::getX).max().orElseThrow(),
                        positions.stream().mapToInt(BlockPos::getY).max().orElseThrow(),
                        positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
                var captured = BlueprintEditorWorld.capture(level, min, max);
                helper.assertTrue(BlueprintEditorWorld.merge(MineBlueprints.current(), tier, captured)
                        .blocks(tier).equals(MineBlueprints.current().blocks(tier)), "Editor capture round-trip " + tier + "/" + facing);
                for (var part : plan.parts()) level.removeBlock(plan.worldPosition(part.position(), target, facing), false);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "auger_empty", timeoutTicks = 120)
    public static void breakingUpperAugerStopsMineAndKeepsBinding(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var controllerPos = origin.offset(4, 1, 0);
        var plan = MineBlueprints.current().plan(Tier.I);
        BlueprintEditorWorld.place(level, plan, origin.offset(MineBlueprints.ANCHOR), Direction.NORTH);
        var controller = (MineControllerBlockEntity) level.getBlockEntity(controllerPos);
        var owner = UUID.randomUUID();
        var project = UUID.randomUUID();
        var deposit = ResourceLocation.fromNamespaceAndPath("test", "auger_vein");
        var resource = ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "iron");
        var cities = CityManager.get(level);
        var city = cities.createCity("Auger Test Foundry", owner, level.dimension().location(), origin.offset(4000,0,0));
        helper.assertTrue(cities.addProject(city, new CityProject(project, city.getId(), CityProjectType.INDUSTRIAL_MINE,
                level.dimension().location(), controllerPos, owner, deposit, resource, .5)), "Project registered");
        helper.assertTrue(controller.commission(city.getId(), project, deposit, resource), "Mine commissioned");
        controller.setRuntimeState(IndustrialMineState.RUNNING, "GameTest");
        controller.setUserRequestedStart(true);
        controller.setMiningTicks(37);
        var base = origin.offset(3, 0, 3);
        level.destroyBlock(base.offset(1, 1, 1), true);
        assertVolume(helper, base, false);
        helper.runAfterDelay(7, () -> {
            helper.assertTrue(controller.getRuntimeState() == IndustrialMineState.ERROR
                    && !controller.isUserRequestedStart() && controller.getMiningTicks() == 37, "Broken auger stops mining");
            var snapshot = IndustrialMineService.inspectSnapshot(level, controllerPos, controller);
            helper.assertTrue(!snapshot.structureValid() && snapshot.commissioned()
                    && snapshot.cityId().orElseThrow().equals(city.getId())
                    && snapshot.projectId().orElseThrow().equals(project)
                    && snapshot.selectedDepositId().orElseThrow().equals(deposit), "All bindings survive destruction");
            for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
                level.setBlockAndUpdate(base.offset(offset), CreateCityExpansionCore.DRILL_UNIT.get()
                        .partState(offset.getX(), offset.getY(), offset.getZ()));
            }
            helper.assertTrue(MineMultiblock.validate(level, controllerPos).assembled(), "Replacing auger repairs mine");
            helper.succeed();
        });
    }
}
