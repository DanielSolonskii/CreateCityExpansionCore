package com.example.examplemod.mine;

import com.example.examplemod.electrical.MineControllerBlock;
import com.example.examplemod.network.MineControllerSnapshotPayload;
import com.example.examplemod.railway.CreateRailwayAdapter;
import com.example.examplemod.railway.RailwayManager;
import com.example.examplemod.network.MineCommissionContextPayload;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityProject;
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
        send(
                player,
                IndustrialMineService.inspectSnapshot(
                        player.serverLevel(),
                        controllerPos,
                        controller
                ),
                true
        );
        sendCommissionContext(player, controllerPos);
    }

    public static void commission(
            ServerPlayer player,
            BlockPos controllerPos,
            UUID cityId,
            net.minecraft.resources.ResourceLocation depositId
    ) {
        Optional<ControllerContext> contextResult = findController(player, controllerPos);
        if (contextResult.isEmpty()) {
            return;
        }
        ControllerContext context = contextResult.get();
        MineControllerBlockEntity controller = context.controller();
        CityManager manager = CityManager.get(context.level().getServer().overworld());
        City city = manager.getCity(cityId);
        String failureKey = null;
        GeologicalDeposit deposit = null;

        if (city == null) {
            failureKey = "message.createcityexpansioncore.mine.commission.city_missing";
        } else if (!city.canBuild(player.getUUID())) {
            failureKey = "message.createcityexpansioncore.mine.commission.permission_denied";
        } else if (city.getProjects().size() >= city.getProjectCapacity()) {
            failureKey = "message.createcityexpansioncore.mine.commission.capacity_full";
        } else if (controller.isCommissioned()) {
            failureKey = "message.createcityexpansioncore.mine.commission.already_registered";
        } else if (!MineMultiblock.validate(context.level(), controllerPos).assembled()) {
            failureKey = "message.createcityexpansioncore.mine.commission.structure_invalid";
        } else if (!GeologyManager.get(context.level()).isStorageAvailable()) {
            failureKey = "message.createcityexpansioncore.mine.commission.geology_unavailable";
        } else {
            GeologicalRegion region = GeologyManager.getRegion(context.level(), controllerPos);
            deposit = region.getDeposit(depositId)
                    .filter(value -> !value.isDepleted())
                    .orElse(null);
            if (deposit == null) {
                failureKey = "message.createcityexpansioncore.mine.commission.deposit_unavailable";
            }
        }

        if (failureKey == null && controller.getActiveDepositId().isPresent()
                && !controller.getActiveDepositId().get().equals(depositId)) {
            failureKey = "message.createcityexpansioncore.mine.commission.specialization_locked";
        }

        if (failureKey != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.createcityexpansioncore.mine.commission.failed",
                    net.minecraft.network.chat.Component.translatable(failureKey)
            ));
            send(player, IndustrialMineService.inspectSnapshot(
                    context.level(), controllerPos, controller
            ), false);
            sendCommissionContext(player, controllerPos);
            return;
        }

        UUID projectId = UUID.randomUUID();
        UUID creatorId = controller.getCreatorId().orElse(player.getUUID());
        controller.setCreator(creatorId, player.getName().getString());
        CityProject project = new CityProject(
                projectId,
                city.getId(),
                CityProjectType.INDUSTRIAL_MINE,
                context.level().dimension().location(),
                controllerPos,
                creatorId,
                depositId,
                deposit.getResourceId(),
                deposit.getGrade()
        );
        if (!manager.addProject(city, project)
                || !controller.commission(
                city.getId(), projectId, depositId, deposit.getResourceId()
        )) {
            manager.removeProject(city, projectId);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.createcityexpansioncore.mine.commission.not_completed"
            ));
        } else {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.createcityexpansioncore.mine.commission.complete", city.getName()
            ));
        }
        send(player, IndustrialMineService.inspectSnapshot(
                context.level(), controllerPos, controller
        ), false);
        sendCommissionContext(player, controllerPos);
    }

    public static void selectDeposit(
            ServerPlayer player,
            BlockPos controllerPos,
            net.minecraft.resources.ResourceLocation depositId
    ) {
        findController(player, controllerPos).ifPresent(context -> send(
                player,
                IndustrialMineService.selectDepositSnapshot(
                        context.level(),
                        controllerPos,
                        context.controller(),
                        depositId
                ),
                false
        ));
    }

    public static void control(
            ServerPlayer player,
            BlockPos controllerPos,
            MineControlCommand command
    ) {
        findController(player, controllerPos).ifPresent(context -> send(
                player,
                IndustrialMineService.controlSnapshot(
                        context.level(),
                        controllerPos,
                        context.controller(),
                        command
                ),
                false
        ));
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
        RailwayManager railway = RailwayManager.get(player.serverLevel());
        var station = snapshot.projectId()
                .map(railway::projectStation)
                .map(railway::station)
                .orElse(null);
        PacketDistributor.sendToPlayer(
                player,
                new MineControllerSnapshotPayload(
                        snapshot,
                        station == null ? Optional.empty() : Optional.of(station.lastKnownName()),
                        station != null && CreateRailwayAdapter.station(station.createStationId()) != null,
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
