package com.example.examplemod.mine;

import com.example.examplemod.electrical.MineControllerBlock;
import com.example.examplemod.network.MineControllerSnapshotPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.UUID;

public final class MineNetworkHandlers {

    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private MineNetworkHandlers() {
    }

    public static void open(
            ServerPlayer player,
            BlockPos controllerPos,
            MineControllerBlockEntity controller
    ) {
        IndustrialMineService.inspectSnapshot(player.serverLevel(), controllerPos, controller);
    }

    public static void commission(
            ServerPlayer player,
            BlockPos controllerPos,
            UUID cityId,
            net.minecraft.resources.ResourceLocation depositId
    ) {
        // Legacy packets cannot choose a city or resource. Apply the physical control rules.
        MineInteraction.press(player, controllerPos);
    }

    public static void selectDeposit(
            ServerPlayer player,
            BlockPos controllerPos,
            net.minecraft.resources.ResourceLocation depositId
    ) {
        // Deposits are spatial and cannot be selected by a client.
        findController(player, controllerPos).ifPresent(c -> IndustrialMineService.inspectSnapshot(c.level(), controllerPos, c.controller()));
    }

    public static void control(
            ServerPlayer player,
            BlockPos controllerPos,
            MineControlCommand command
    ) {
        MineInteraction.command(player, controllerPos, command);
    }

    static void syncNearby(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerSnapshot snapshot,
            boolean openScreen
    ) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(
                    controllerPos.getX() + 0.5,
                    controllerPos.getY() + 0.5,
                    controllerPos.getZ() + 0.5
            ) <= MAX_INTERACTION_DISTANCE_SQUARED) {
                send(player, snapshot, openScreen);
            }
        }
    }

    private static Optional<ControllerContext> findController(
            ServerPlayer player,
            BlockPos controllerPos
    ) {
        if (player.distanceToSqr(
                controllerPos.getX() + 0.5,
                controllerPos.getY() + 0.5,
                controllerPos.getZ() + 0.5
        ) > MAX_INTERACTION_DISTANCE_SQUARED) {
            return Optional.empty();
        }

        ServerLevel level = player.serverLevel();
        if (!(level.getBlockState(controllerPos).getBlock()
                instanceof MineControllerBlock)) {
            return Optional.empty();
        }
        if (!(level.getBlockEntity(controllerPos)
                instanceof MineControllerBlockEntity controller)) {
            return Optional.empty();
        }
        return Optional.of(new ControllerContext(level, controller));
    }

    private static void send(
            ServerPlayer player,
            MineControllerSnapshot snapshot,
            boolean openScreen
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new MineControllerSnapshotPayload(
                        snapshot,
                        openScreen
                )
        );
    }

    private record ControllerContext(
            ServerLevel level,
            MineControllerBlockEntity controller
    ) {
    }
}
