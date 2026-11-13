package com.example.examplemod.client.render;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.electrical.DrillUnitBlock;
import com.example.examplemod.electrical.TechnicalBlock;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class MineWorkEffects {
    private static final Map<BlockPos, MineControllerBlockEntity> CONTROLLERS = new HashMap<>();
    private static final Map<BlockPos, MineWorkMotion> MOTIONS = new HashMap<>();
    private static Map<BlockPos, Auger> augers = Map.of();
    private static volatile Map<BlockPos, BlockState> hidden = Map.of();
    private static ClientLevel tracked;
    private record Auger(BlockPos owner, BlockState state, boolean single) {}

    public static void changed(MineControllerBlockEntity controller) {
        if (!(controller.getLevel() instanceof ClientLevel level) || level != Minecraft.getInstance().level) return;
        ensureLevel(level);
        if (controller.isRemoved() || !controller.isVisualClientLoaded()) {
            CONTROLLERS.remove(controller.getBlockPos()); MOTIONS.remove(controller.getBlockPos());
        } else CONTROLLERS.put(controller.getBlockPos(), controller);
    }

    public static boolean hidden(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        // Chunk render regions are snapshots, but item and schematic renderers have no model-data lookup here.
        return state.equals(hidden.get(pos));
    }

    private static void ensureLevel(ClientLevel level) {
        if (tracked == level) return;
        CONTROLLERS.clear(); MOTIONS.clear(); augers = Map.of(); hidden = Map.of(); tracked = level;
    }

    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level) || level != Minecraft.getInstance().level) return;
        ensureLevel(level);
        if (event.getChunk() instanceof LevelChunk chunk) chunk.getBlockEntities().values().forEach(be -> {
            if (be instanceof MineControllerBlockEntity controller) changed(controller);
        });
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == tracked) ensureLevel(null);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || mc.isPaused()) return;
        ensureLevel(level);
        Map<BlockPos, BlockState> nextHidden = new HashMap<>();
        Map<BlockPos, Auger> nextAugers = new HashMap<>();
        for (var controller : List.copyOf(CONTROLLERS.values())) {
            BlockPos owner = controller.getBlockPos();
            if (controller.isRemoved() || !controller.isVisualClientLoaded() || !level.hasChunkAt(owner)
                    || level.getBlockEntity(owner) != controller) {
                CONTROLLERS.remove(owner); MOTIONS.remove(owner); continue;
            }
            boolean running = MineWorkMotion.running(controller.isVisualAssembled(),
                    controller.getRuntimeState(), controller.getVisualElectricalState());
            MOTIONS.computeIfAbsent(owner, ignored -> new MineWorkMotion()).tick(running);
            if (!controller.isVisualAssembled()) continue;
            for (BlockPos pos : controller.effectDrills()) {
                if (!level.hasChunkAt(pos) || nextAugers.containsKey(pos)) continue;
                var state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof DrillUnitBlock drill)) continue;
                boolean single = state.getValue(DrillUnitBlock.PART) == DrillUnitBlock.Part.SINGLE;
                Map<BlockPos, BlockState> cells = new HashMap<>();
                if (single) cells.put(pos, state);
                else {
                    for (BlockPos offset : BlockPos.betweenClosed(0,0,0,1,1,1)) {
                        BlockPos cell = pos.offset(offset);
                        var expected = drill.partState(offset.getX(), offset.getY(), offset.getZ(), state.getValue(DrillUnitBlock.FACING));
                        if (!level.hasChunkAt(cell) || !level.getBlockState(cell).equals(expected)) { cells.clear(); break; }
                        cells.put(cell, expected);
                    }
                }
                if (cells.isEmpty()) continue;
                nextAugers.put(pos, new Auger(owner,state,single)); nextHidden.putAll(cells);
            }
            if (running) dust(level, controller);
        }
        augers = Map.copyOf(nextAugers);
        if (!nextHidden.equals(hidden)) {
            Set<BlockPos> changed = new HashSet<>(hidden.keySet()); changed.addAll(nextHidden.keySet());
            hidden = Map.copyOf(nextHidden);
            changed.stream().map(SectionPos::of).distinct().forEach(s ->
                    mc.levelRenderer.setSectionDirtyWithNeighbors(s.x(),s.y(),s.z()));
        }
    }

    private static void dust(ClientLevel level, MineControllerBlockEntity controller) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int emitted = 0;
        for (BlockPos pos : controller.effectVents()) {
            if (emitted >= 8) break; // Bound clouds per mine and tick, even for experimental huge blueprints.
            if (Math.floorMod(level.getGameTime() + pos.asLong(), 12) != 0
                    || camera.distanceToSqr(Vec3.atCenterOf(pos)) > 40*40 || !level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof TechnicalBlock)) continue;
            Direction out = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (!level.hasChunkAt(pos.relative(out)) || level.getBlockState(pos.relative(out)).isSolidRender(level, pos.relative(out))) continue;
            Direction across = out.getClockWise();
            double jitter = (level.random.nextDouble() - .5) * .45;
            Vec3 start = Vec3.atCenterOf(pos).add(out.getStepX()*.57 + across.getStepX()*jitter,
                    (level.random.nextDouble()-.5)*.3, out.getStepZ()*.57 + across.getStepZ()*jitter);
            level.addParticle(ParticleTypes.POOF, start.x,start.y,start.z,
                    out.getStepX()*.055, .018, out.getStepZ()*.055);
            emitted++;
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance(); var level = mc.level;
        if (level == null || level != tracked) return;
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.solid());
        var renderer = mc.getBlockRenderer().getModelRenderer();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var entry : augers.entrySet()) {
            BlockPos pos = entry.getKey(); Auger auger = entry.getValue();
            var motion = MOTIONS.get(auger.owner);
            if (motion == null) continue;
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).equals(auger.state)) continue;
            // Hidden models must remain visible throughout normal chunk render distances.
            if (!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(pos).inflate(2))) continue;
            double size = auger.single ? .5 : 1;
            int light = LevelRenderer.getLightColor(level, pos);
            pose.pushPose(); pose.translate(pos.getX()+size-camera.x,pos.getY()+size-camera.y,pos.getZ()+size-camera.z);
            switch (auger.state.getValue(DrillUnitBlock.FACING)) {
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(180));
                case NORTH -> pose.mulPose(Axis.XP.rotationDegrees(90));
                case SOUTH -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                case EAST -> pose.mulPose(Axis.ZP.rotationDegrees(90));
                case WEST -> pose.mulPose(Axis.ZP.rotationDegrees(-90));
                default -> {}
            }
            pose.scale((float)size,(float)size,(float)size);
            for (boolean rotating : new boolean[]{false,true}) {
                pose.pushPose();
                if (rotating) pose.mulPose(Axis.YP.rotationDegrees((float)motion.angle(partial)));
                pose.translate(-1,-1,-1);
                var model = mc.getModelManager().getModel(rotating ? AugerAnimationModels.ROTOR : AugerAnimationModels.HOUSING);
                renderer.renderModel(pose.last(), consumer, auger.state, model, 1,1,1, light,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());
                pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch(RenderType.solid());
    }
}
