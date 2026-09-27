package com.example.examplemod.client.render;

import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Mine adapter: authoritative semantic sync plus a fail-open local validation guard. */
public final class IndustrialMineVisuals {
    private static final Map<BlockPos, MineControllerBlockEntity> CONTROLLERS = new HashMap<>();
    private static final Map<BlockPos, Motion> MOTION = new HashMap<>();
    private static ClientLevel trackedLevel;
    private IndustrialMineVisuals() {}

    public static void changed(MineControllerBlockEntity controller) {
        if (!(controller.getLevel() instanceof ClientLevel level)
                || level != Minecraft.getInstance().level) return;
        ensureLevel(level);
        BlockPos pos = controller.getBlockPos();
        if (controller.isRemoved() || !controller.isVisualClientLoaded()) {
            if (CONTROLLERS.get(pos) == controller) {
                CONTROLLERS.remove(pos);
                MOTION.remove(pos);
                AssembledBlockVisibility.remove(pos);
            }
        } else {
            CONTROLLERS.put(pos, controller);
            refresh(controller);
        }
    }

    private static void refresh(MineControllerBlockEntity controller) {
        BlockPos pos = controller.getBlockPos();
        var level = Minecraft.getInstance().level;
        if (level == null || controller.getLevel() != level || controller.isRemoved()
                || !controller.isVisualAssembled() || !level.hasChunkAt(pos)
                || level.getBlockEntity(pos) != controller
                || controller.getVisualPowerPort() == null || controller.getVisualGateway() == null) {
            AssembledBlockVisibility.remove(pos);
            return;
        }
        // Exactly the canonical rules, including the two empty EE wire cells and loaded chunks.
        var validation = MineMultiblock.validate(level, pos);
        if (!validation.assembled()
                || !Objects.equals(validation.powerPort(), controller.getVisualPowerPort())
                || !Objects.equals(validation.gateway(), controller.getVisualGateway())) {
            AssembledBlockVisibility.remove(pos);
            return;
        }
        Direction facing = controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        Map<BlockPos, BlockState> members = new HashMap<>();
        for (BlockPos member : MineMultiblock.visualMembers(pos, facing, validation.powerPort())) {
            members.put(member, level.getBlockState(member));
        }
        AssembledBlockVisibility.replace(pos, members);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ensureLevel(level);
        for (var controller : java.util.List.copyOf(CONTROLLERS.values())) {
            refresh(controller);
            Motion motion = MOTION.computeIfAbsent(controller.getBlockPos(), ignored -> new Motion());
            var state = controller.getRuntimeState();
            boolean moving = state == IndustrialMineState.RUNNING || state == IndustrialMineState.STARTING;
            boolean active = AssembledBlockVisibility.isActive(controller.getBlockPos());
            motion.previous = motion.angle;
            // Semantic states only: sixty-tick startup, forty-tick mechanical rundown.
            double target = moving && active ? 1.5D : 0.0D;
            double step = target > motion.speed ? 1.5D / 60.0D : 1.5D / 40.0D;
            motion.speed += Math.max(-step, Math.min(step, target - motion.speed));
            if (!active) motion.speed = 0;
            // Frozen phase at rest; no snap to angle zero on STOPPED/NO_POWER.
            motion.angle += motion.speed;
        }
    }

    @SubscribeEvent
    public static void loadChunk(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)
                || level != Minecraft.getInstance().level) return;
        ensureLevel(level);
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        chunk.getBlockEntities().values().forEach(blockEntity -> {
            if (blockEntity instanceof MineControllerBlockEntity controller) changed(controller);
        });
    }

    public static double angle(BlockPos pos, float partialTicks) {
        Motion motion = MOTION.get(pos);
        return motion == null ? 0.0D : motion.previous + (motion.angle - motion.previous) * partialTicks;
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() != Minecraft.getInstance().level) return;
        var chunk = event.getChunk().getPos();
        for (var controller : java.util.List.copyOf(CONTROLLERS.values())) {
            BlockPos pos = controller.getBlockPos();
            Direction facing = controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos power = controller.getVisualPowerPort();
            boolean touches = power != null && MineMultiblock.visualMembers(pos, facing, power).stream()
                    .anyMatch(member -> (member.getX() >> 4) == chunk.x && (member.getZ() >> 4) == chunk.z);
            if (touches) AssembledBlockVisibility.remove(pos);
            if ((pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z) {
                CONTROLLERS.remove(pos);
                MOTION.remove(pos);
                AssembledBlockVisibility.remove(pos);
            }
        }
    }

    @SubscribeEvent
    public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() != trackedLevel) return;
        CONTROLLERS.clear();
        MOTION.clear();
        AssembledBlockVisibility.resetLevel();
        trackedLevel = null;
    }

    private static void ensureLevel(ClientLevel level) {
        if (trackedLevel == level) return;
        CONTROLLERS.clear();
        MOTION.clear();
        AssembledBlockVisibility.resetLevel();
        trackedLevel = level;
    }

    private static final class Motion { double previous; double angle; double speed; }
}
