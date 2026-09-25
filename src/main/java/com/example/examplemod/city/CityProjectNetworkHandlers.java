package com.example.examplemod.city;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.network.CityDevelopmentPayload;
import com.example.examplemod.network.CityPylonStatePayload;
import com.example.examplemod.railway.RailwayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CityProjectNetworkHandlers {

    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private CityProjectNetworkHandlers() {
    }

    public static void decommission(ServerPlayer player, BlockPos pylonPos, UUID projectId) {
        CityContext context = findCity(player, pylonPos);
        if (context == null || !context.city().isAdmin(player.getUUID())) {
            return;
        }
        CityProject project = context.manager().removeProject(context.city(), projectId);
        if (project == null) {
            return;
        }
        RailwayManager.get(player.serverLevel()).linkProject(project.projectId(), null);

        ServerLevel projectLevel = player.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, project.dimensionId())
        );
        if (projectLevel != null && projectLevel.hasChunkAt(project.controllerPos())
                && projectLevel.getBlockEntity(project.controllerPos())
                instanceof MineControllerBlockEntity controller) {
            controller.decommission(project.projectId());
        }
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.project.decommissioned"
        ));
        sync(player, context.city());
    }

    public static void development(
            ServerPlayer player,
            BlockPos pylonPos,
            CityDevelopmentPayload.Action action
    ) {
        CityContext context = findCity(player, pylonPos);
        if (context == null || !context.city().hasMember(player.getUUID())) {
            return;
        }
        City city = context.city();
        if (city.getLevel() >= 2) {
            player.sendSystemMessage(Component.translatable(
                    "message.createcityexpansioncore.development.already_level_two"
            ));
            sync(player, city);
            return;
        }
        var primary = city.getDevelopmentPrimaryResource();
        var requirements = primary == null
                ? List.<CityDevelopmentRequirements.Requirement>of()
                : CityDevelopmentRequirements.forLevelTwo(primary);
        if (requirements.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.createcityexpansioncore.development.no_primary_industry"
            ));
            return;
        }

        if (action == CityDevelopmentPayload.Action.CONTRIBUTE) {
            long totalAccepted = 0L;
            for (CityDevelopmentRequirements.Requirement requirement : requirements) {
                Item item = BuiltInRegistries.ITEM.getOptional(requirement.itemId()).orElse(null);
                if (item == null) {
                    continue;
                }
                long remaining = Math.max(0L, requirement.amount()
                        - city.getDevelopmentContribution(requirement.itemId()));
                int available = player.getInventory().countItem(item);
                int requested = (int) Math.min(Math.min(remaining, Integer.MAX_VALUE), available);
                if (requested <= 0) {
                    continue;
                }
                int removed = removeItems(player, item, requested);
                totalAccepted += city.contribute(
                        requirement.itemId(), removed, requirement.amount()
                );
            }
            if (totalAccepted > 0L) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                context.manager().markChanged();
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.development.contributed_all",
                        totalAccepted
                ));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.development.nothing_to_contribute"
                ));
            }
        } else if (action == CityDevelopmentPayload.Action.COMPLETE_LEVEL_TWO) {
            if (!city.isAdmin(player.getUUID())) {
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.development.permission_denied"
                ));
                return;
            }
            if (!city.canCompleteLevelTwo()) {
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.development.requirements_incomplete"
                ));
                return;
            }
            city.setLevel(2);
            context.manager().markChanged();
            player.sendSystemMessage(Component.translatable(
                    "message.createcityexpansioncore.development.level_two_complete"
            ));
        }
        sync(player, city);
    }

    private static int removeItems(ServerPlayer player, Item item, int requested) {
        int remaining = requested;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return requested - remaining;
    }

    private static CityContext findCity(ServerPlayer player, BlockPos pylonPos) {
        if (!(player.containerMenu instanceof CityPylonMenu menu)
                || !menu.getPylonPos().equals(pylonPos)
                || player.distanceToSqr(
                pylonPos.getX() + 0.5, pylonPos.getY() + 0.5, pylonPos.getZ() + 0.5
        ) > MAX_INTERACTION_DISTANCE_SQUARED
                || !(player.serverLevel().getBlockState(pylonPos).getBlock() instanceof CityPylonBlock)) {
            return null;
        }
        CityManager manager = CityManager.get(player.getServer().overworld());
        City city = manager.getCityByPylon(
                player.serverLevel().dimension().location(), pylonPos
        );
        return city == null ? null : new CityContext(manager, city);
    }

    private static void sync(ServerPlayer player, City city) {
        PacketDistributor.sendToPlayer(player, createState(player.getServer(), city));
    }

    public static CityPylonStatePayload createState(MinecraftServer server, City city) {
        List<CityProjectSnapshot> projects = new ArrayList<>();
        for (CityProject project : city.getProjects()) {
            String status = "OFFLINE / UNLOADED";
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, project.dimensionId())
            );
            if (level != null && level.hasChunkAt(project.controllerPos())) {
                if (level.getBlockEntity(project.controllerPos())
                        instanceof MineControllerBlockEntity controller
                        && controller.getProjectId().filter(project.projectId()::equals).isPresent()) {
                    status = controller.getRuntimeState().name();
                } else {
                    status = "OFFLINE / CONTROLLER MISSING";
                }
            }
            projects.add(new CityProjectSnapshot(
                    project.projectId(), project.projectType(), project.dimensionId(),
                    project.controllerPos(), project.creatorId(), project.resourceId(),
                    project.richness(), status
            ));
        }
        var primary = city.getDevelopmentPrimaryResource();
        var requirements = primary == null
                ? List.<CityDevelopmentRequirements.Requirement>of()
                : CityDevelopmentRequirements.forLevelTwo(primary);
        List<CityDevelopmentRequirementSnapshot> development = requirements.stream()
                .map(requirement -> new CityDevelopmentRequirementSnapshot(
                        requirement.group(), requirement.itemId(),
                        city.getDevelopmentContribution(requirement.itemId()),
                        requirement.amount()
                ))
                .toList();
        return new CityPylonStatePayload(
                city.getPylonPos(), city.getLevel(), city.getProjectCapacity(), projects,
                primary, development
        );
    }

    private record CityContext(CityManager manager, City city) {
    }
}
