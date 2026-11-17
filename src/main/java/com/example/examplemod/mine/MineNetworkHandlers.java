package com.example.examplemod.mine;

import com.example.examplemod.electrical.MineControllerBlock;
import com.example.examplemod.network.MineControllerSnapshotPayload;
import com.example.examplemod.network.MineCommissionContextPayload;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityProject;
import com.example.examplemod.sound.ModSounds;
import com.example.examplemod.sound.WorldSoundHelper;
import com.example.examplemod.city.CityProjectType;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.multiblock.MineMultiblock;
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

    private static void sendCommissionContext(ServerPlayer player, BlockPos controllerPos) {
        City city = CityManager.get(player.getServer().overworld())
                .getCityByMember(player.getUUID());
        boolean allowed = city != null && city.canBuild(player.getUUID());
        String message = city == null
                ? "gui.createcityexpansioncore.mine.commission.no_city"
                : allowed ? "gui.createcityexpansioncore.mine.commission.available"
                : "gui.createcityexpansioncore.mine.commission.permission_denied";
        PacketDistributor.sendToPlayer(player, new MineCommissionContextPayload(
                controllerPos,
                city == null ? Optional.empty() : Optional.of(city.getId()),
                city == null ? "" : city.getName(),
                allowed,
                message
        ));
    }

    private record ControllerContext(
            ServerLevel level,
            MineControllerBlockEntity controller
    ) {
    }
}
