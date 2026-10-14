package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.blast.BlastFurnaceMultiblock;
import com.example.examplemod.blast.BlastFurnaceMultiblock.ExpectedBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.EnumMap;
import java.util.Map;

public final class BlastFurnaceGuideRenderer {
    private static final int ALL_LAYERS = -1;
    private static BlockPos controllerPos;
    private static int selectedLayer;
    private static int lastSingleLayer;
    private static int actionBarCooldown;

    private BlastFurnaceGuideRenderer() {}

    public static void toggle(BlockPos pos) {
        CokeBatteryGuideRenderer.hide(false);
        SteelConverterGuideRenderer.hide(false);
        if (pos.equals(controllerPos)) { hide(true); return; }
        controllerPos = pos.immutable(); selectedLayer = 0; lastSingleLayer = 0; actionBarCooldown = 0;
        announceState(false);
    }
    public static void hide(boolean announce) {
        boolean showing = controllerPos != null; controllerPos = null;
        if (announce && showing && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                    "message.createcityexpansioncore.blast_guide.disabled"), true);
    }
    public static boolean isShowing() { return controllerPos != null; }

    @SubscribeEvent public static void onClientTick(ClientTickEvent.Post event) {
        if (!isShowing()) return;
        while (BuildGuideKeyMappings.consumeNext()) changeLayer(1);
        while (BuildGuideKeyMappings.consumePrevious()) changeLayer(-1);
        while (BuildGuideKeyMappings.consumeAll()) toggleAll();
        if (++actionBarCooldown >= 20) { actionBarCooldown = 0; announceNearest(); }
    }
    private static void changeLayer(int delta) {
        int base = selectedLayer == ALL_LAYERS ? lastSingleLayer : selectedLayer;
        selectedLayer = Math.floorMod(base + delta, BlastFurnaceMultiblock.HEIGHT);
        lastSingleLayer = selectedLayer; announceState(false);
    }
    private static void toggleAll() {
        if (selectedLayer == ALL_LAYERS) selectedLayer = lastSingleLayer;
        else { lastSingleLayer = selectedLayer; selectedLayer = ALL_LAYERS; }
        announceState(false);
    }

    @SubscribeEvent public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || controllerPos == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        BlockState controller = minecraft.level.getBlockState(controllerPos);
        if (!controller.is(CreateCityExpansionCore.BLAST_FURNACE_CONTROLLER.get())
                || !controller.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) { hide(false); return; }
        Direction facing = controller.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos origin = BlastFurnaceMultiblock.getOrigin(controllerPos, facing);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = source.getBuffer(RenderType.lines());
        var camera = event.getCamera().getPosition();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        int firstY = selectedLayer == ALL_LAYERS ? 0 : selectedLayer;
        int lastY = selectedLayer == ALL_LAYERS ? BlastFurnaceMultiblock.HEIGHT - 1 : selectedLayer;
        for (int y = firstY; y <= lastY; y++) for (int z = 0; z < BlastFurnaceMultiblock.DEPTH; z++)
            for (int x = 0; x < BlastFurnaceMultiblock.WIDTH; x++) {
                ExpectedBlock expected = BlastFurnaceMultiblock.expectedAt(x, y, z);
                BlockPos world = BlastFurnaceMultiblock.getWorldPosition(origin, facing, x, y, z);
                BlockState actual = minecraft.level.getBlockState(world);
                if (!BlastFurnaceMultiblock.matches(actual, expected))
                    BuildGuideRenderHelper.renderOutline(pose, buffer, world, visualStatus(expected, actual));
            }
        pose.popPose(); source.endBatch(RenderType.lines());
    }

    private static BuildGuideRenderHelper.Status visualStatus(ExpectedBlock expected, BlockState actual) {
        if (expected == ExpectedBlock.AIR) return BuildGuideRenderHelper.Status.OCCUPIED_AIR;
        if (!actual.isAir()) return BuildGuideRenderHelper.Status.WRONG;
        return isFunctional(expected) ? BuildGuideRenderHelper.Status.FUNCTIONAL
                : BuildGuideRenderHelper.Status.MISSING;
    }
    private static boolean isFunctional(ExpectedBlock expected) {
        return expected == ExpectedBlock.CONTROLLER || expected == ExpectedBlock.ITEM_INPUT
                || expected == ExpectedBlock.FLUID_OUTPUT || expected == ExpectedBlock.POWER_PORT;
    }

    private static void announceState(boolean actionBar) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || controllerPos == null) return;
        Counts counts = counts(minecraft);
        Component layer = selectedLayer == ALL_LAYERS
                ? Component.translatable("message.createcityexpansioncore.build_guide.all_layers")
                : Component.literal("Y=" + selectedLayer);
        minecraft.player.displayClientMessage(Component.translatable(
                "message.createcityexpansioncore.blast_guide.enabled", layer,
                counts.correct(), counts.required(), counts.errors()), actionBar);
    }

    private static void announceNearest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || controllerPos == null) return;
        BlockState controller = minecraft.level.getBlockState(controllerPos);
        if (!controller.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return;
        Direction facing = controller.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos origin = BlastFurnaceMultiblock.getOrigin(controllerPos, facing);
        Target nearest = null; Map<ExpectedBlock, Integer> remaining = new EnumMap<>(ExpectedBlock.class);
        int firstY = selectedLayer == ALL_LAYERS ? 0 : selectedLayer;
        int lastY = selectedLayer == ALL_LAYERS ? BlastFurnaceMultiblock.HEIGHT - 1 : selectedLayer;
        for (int y = firstY; y <= lastY; y++) for (int z = 0; z < BlastFurnaceMultiblock.DEPTH; z++)
            for (int x = 0; x < BlastFurnaceMultiblock.WIDTH; x++) {
                ExpectedBlock expected = BlastFurnaceMultiblock.expectedAt(x, y, z);
                BlockPos world = BlastFurnaceMultiblock.getWorldPosition(origin, facing, x, y, z);
                if (BlastFurnaceMultiblock.matches(minecraft.level.getBlockState(world), expected)) continue;
                remaining.merge(expected, 1, Integer::sum);
                double distance = minecraft.player.distanceToSqr(world.getX() + .5, world.getY() + .5, world.getZ() + .5);
                if (nearest == null || distance < nearest.distance())
                    nearest = new Target(new BlastFurnaceMultiblock.LocalPos(x, y, z), world, expected, distance);
            }
        if (nearest == null) { announceState(true); return; }
        Component name = nearest.expected() == ExpectedBlock.AIR
                ? Component.translatable("message.createcityexpansioncore.build_guide.required_air")
                : BlastFurnaceMultiblock.requiredBlock(nearest.expected()).getName();
        minecraft.player.displayClientMessage(Component.translatable(
                "message.createcityexpansioncore.build_guide.target", name,
                nearest.local().x(), nearest.local().y(), nearest.local().z(),
                nearest.world().getX(), nearest.world().getY(), nearest.world().getZ(),
                remaining.getOrDefault(nearest.expected(), 0)), true);
    }

    private static Counts counts(Minecraft minecraft) {
        BlockState controller = minecraft.level.getBlockState(controllerPos);
        if (!controller.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return new Counts(0, 915, 1);
        Direction facing = controller.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos origin = BlastFurnaceMultiblock.getOrigin(controllerPos, facing);
        int correct = 0, required = 0, errors = 0;
        for (int y = 0; y < BlastFurnaceMultiblock.HEIGHT; y++) for (int z = 0; z < BlastFurnaceMultiblock.DEPTH; z++)
            for (int x = 0; x < BlastFurnaceMultiblock.WIDTH; x++) {
                ExpectedBlock expected = BlastFurnaceMultiblock.expectedAt(x, y, z);
                BlockState actual = minecraft.level.getBlockState(
                        BlastFurnaceMultiblock.getWorldPosition(origin, facing, x, y, z));
                if (expected != ExpectedBlock.AIR) required++;
                if (BlastFurnaceMultiblock.matches(actual, expected)) {
                    if (expected != ExpectedBlock.AIR) correct++;
                } else errors++;
            }
        return new Counts(correct, required, errors);
    }
    private record Counts(int correct, int required, int errors) {}
    private record Target(BlastFurnaceMultiblock.LocalPos local, BlockPos world,
                          ExpectedBlock expected, double distance) {}
}
