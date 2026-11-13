package com.example.examplemod.mine;

import com.example.examplemod.city.*;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.multiblock.BlueprintCatalog;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** Physical controls: city comes from the placer, resource from the geology at the controller. */
public final class MineInteraction {
    private MineInteraction() {}

    public static void placed(ServerLevel level, MineControllerBlockEntity controller, Player placer) {
        controller.setCreator(placer.getUUID(), com.example.examplemod.auth.PlayerNames.name(level.getServer(), placer.getUUID()));
        if (!controller.isCommissioned()) {
            City city = CityManager.get(level.getServer().overworld()).getMemberCity(placer.getUUID());
            controller.assignPlacementCity(city == null ? null : city.getId());
        }
        controller.initializeRedstone(level.hasNeighborSignal(controller.getBlockPos()));
        IndustrialMineService.inspectSnapshot(level, controller.getBlockPos(), controller);
    }

    public static void press(ServerPlayer player, BlockPos pos) {
        command(player, pos, null);
    }

    public static void command(ServerPlayer player, BlockPos pos, MineControlCommand requested) {
        if (player.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 64
                || !player.serverLevel().hasChunkAt(pos)
                || !(player.serverLevel().getBlockEntity(pos) instanceof MineControllerBlockEntity controller)) return;
        String failure = authorize(player.serverLevel(), controller, player.getUUID());
        if (failure == null) failure = operate(player.serverLevel(), controller, requested);
        if (failure != null) player.displayClientMessage(Component.literal(failure), true);
    }

    static String authorize(ServerLevel level, MineControllerBlockEntity controller, UUID actor) {
        migratePlacementCity(level, controller);
        UUID id = controller.isCommissioned() ? controller.getCityId().orElse(null) : controller.placementCity();
        City city = id == null ? null : CityManager.get(level.getServer().overworld()).getCity(id);
        if (city == null) return "Установивший контроллер игрок не состоял в городе. Переставьте контроллер от имени участника города.";
        if (!city.canBuild(actor)) return "Нет права управлять промышленными структурами этого города.";
        return null;
    }

    private static void migratePlacementCity(ServerLevel level, MineControllerBlockEntity controller) {
        if (controller.hasPlacementCity()) return;
        City city = controller.getCreatorId().map(id -> CityManager.get(level.getServer().overworld()).getMemberCity(id)).orElse(null);
        controller.assignPlacementCity(controller.getCityId().orElse(city == null ? null : city.getId()));
    }

    static void redstone(ServerLevel level, MineControllerBlockEntity controller) {
        boolean signal = level.hasNeighborSignal(controller.getBlockPos());
        if (!controller.consumeRedstoneEdge(signal)) return;
        if (!signal) { IndustrialMineService.controlSnapshot(level, controller.getBlockPos(), controller, MineControlCommand.STOP); return; }
        UUID actor = controller.getCreatorId().orElse(null);
        String failure = actor == null ? "У контроллера нет владельца." : authorize(level, controller, actor);
        if (failure == null) failure = operate(level, controller, MineControlCommand.START);
        if (failure != null) controller.setRuntimeState(controller.getRuntimeState(), failure);
    }

    private static String operate(ServerLevel level, MineControllerBlockEntity controller, MineControlCommand requested) {
        BlockPos pos = controller.getBlockPos();
        boolean stop = requested == MineControlCommand.STOP || requested == MineControlCommand.EMERGENCY
                || (requested == null && controller.isUserRequestedStart());
        if (stop) {
            IndustrialMineService.controlSnapshot(level, pos, controller,
                    requested == MineControlCommand.EMERGENCY ? requested : MineControlCommand.STOP);
            return null;
        }
        var validation = MineMultiblock.validate(level, pos);
        if (!validation.assembled()) return "Шахта не собрана: " + validation.message();
        if (!controller.isCommissioned()) {
            CityManager cities = CityManager.get(level.getServer().overworld());
            City city = cities.getCity(controller.placementCity());
            if (city == null) return "Город контроллера недоступен.";
            if (city.getUnlockedBlueprintTier(BlueprintCatalog.Structure.MINE) < 1) return "Чертёж шахты закрыт для города.";
            if (city.getProjects().size() >= city.getProjectCapacity()) return "Достигнут лимит проектов города.";
            if (!GeologyManager.get(level).isStorageAvailable()) return "Геология недоступна.";
            var deposit = GeologyManager.getRegion(level, pos).getDeposits().stream()
                    .filter(d -> d.localPotentialPerHour(pos.getX(), pos.getZ()) > 0).findFirst().orElse(null);
            if (deposit == null) return "Под контроллером нет залежи.";
            UUID projectId = UUID.randomUUID();
            CityProject project = new CityProject(projectId, city.getId(), CityProjectType.INDUSTRIAL_MINE,
                    level.dimension().location(), pos, controller.getCreatorId().orElseThrow(),
                    deposit.getId(), deposit.getResourceId(), deposit.getGrade());
            if (!cities.addProject(city, project)) return "Не удалось зарегистрировать проект.";
            if (!controller.commission(city.getId(), projectId, deposit.getId(), deposit.getResourceId())) {
                cities.removeProject(city, projectId); return "Не удалось привязать контроллер к проекту.";
            }
        }
        if (!controller.isUserRequestedStart()) {
            IndustrialMineService.controlSnapshot(level, pos, controller, MineControlCommand.EMERGENCY);
            IndustrialMineService.controlSnapshot(level, pos, controller, MineControlCommand.START);
        }
        return controller.getStatusMessage();
    }
}
