package com.example.examplemod.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityDevelopmentRequirements;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.menu.CityPylonMenu;
import com.mojang.serialization.MapCodec;
import com.example.examplemod.city.CityMember;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CityPylonBlock extends Block {

    public static final MapCodec<CityPylonBlock> CODEC =
            simpleCodec(CityPylonBlock::new);

    public CityPylonBlock(
            BlockBehaviour.Properties properties
    ) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public MenuProvider getMenuProvider(
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) ->
                        new CityPylonMenu(
                                containerId,
                                inventory,
                                pos
                        ),
                Component.translatable(
                        "menu.createcityexpansioncore.city_pylon"
                )
        );
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {

        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer) {

            ServerLevel overworld =
                    serverPlayer
                            .serverLevel()
                            .getServer()
                            .overworld();

            CityManager cityManager =
                    CityManager.get(overworld);

            City city = cityManager.getCityByPylon(
                    serverPlayer.serverLevel().dimension().location(), pos
            );

            if (city != null) {
                cityManager.markPylonActive(city);
            }

            serverPlayer.openMenu(
                    state.getMenuProvider(
                            level,
                            pos
                    ),
                    data -> {

                        /*
                         * =================================
                         * ПОЗИЦИЯ ПИЛОНА
                         * =================================
                         */

                        data.writeBlockPos(pos);
                        data.writeResourceLocation(
                                serverPlayer.serverLevel().dimension().location()
                        );

                        /*
                         * =================================
                         * ЕСТЬ ЛИ ГОРОД
                         * =================================
                         */

                        data.writeBoolean(
                                city != null
                        );

                        /*
                         * =================================
                         * НАЗВАНИЕ
                         * =================================
                         */

                        data.writeUtf(
                                city != null
                                        ? city.getName()
                                        : ""
                        );

                        /*
                         * =================================
                         * УРОВЕНЬ
                         * =================================
                         */

                        data.writeVarInt(
                                city != null
                                        ? city.getLevel()
                                        : 1
                        );

                        /*
                         * =================================
                         * ТЕРРИТОРИЯ
                         * =================================
                         */

                        data.writeVarInt(
                                city != null
                                        ? city.getTerritorySize()
                                        : 100
                        );

                        /*
                         * =================================
                         * ЕСТЬ ЛИ ВЛАДЕЛЕЦ
                         * =================================
                         */

                        data.writeBoolean(
                                city != null
                        );

                        /*
                         * =================================
                         * UUID ВЛАДЕЛЬЦА
                         * =================================
                         */

                        if (city != null) {

                            data.writeUUID(
                                    city.getOwner()
                            );
                        }

                        /*
                         * =================================
                         * УЧАСТНИКИ
                         * =================================
                         */

                        if (city != null) {

                            data.writeVarInt(
                                    city.getMembers().size()
                            );

                            for (var member : city.getMembers()) {
                                data.writeUUID(member.getPlayerId());
                                data.writeVarInt(member.getRole().ordinal());
                            }

                        } else {

                            data.writeVarInt(0);
                        }

                        data.writeVarInt(city != null ? city.getProjectCapacity() : 1);
                        data.writeBoolean(city == null || city.isPylonActive());
                        if (city == null) {
                            data.writeVarInt(0);
                        } else {
                            data.writeVarInt(city.getProjects().size());
                            for (var project : city.getProjects()) {
                                data.writeUUID(project.projectId());
                                data.writeEnum(project.projectType());
                                data.writeResourceLocation(project.dimensionId());
                                data.writeBlockPos(project.controllerPos());
                                data.writeUUID(project.creatorId());
                                data.writeResourceLocation(project.resourceId());
                                data.writeDouble(project.richness());

                                ServerLevel projectLevel = serverPlayer.getServer().getLevel(
                                        ResourceKey.create(Registries.DIMENSION, project.dimensionId())
                                );
                                String status = "OFFLINE / UNLOADED";
                                if (projectLevel != null && projectLevel.hasChunkAt(project.controllerPos())) {
                                    if (projectLevel.getBlockEntity(project.controllerPos())
                                            instanceof MineControllerBlockEntity controller
                                            && controller.getProjectId()
                                            .filter(project.projectId()::equals).isPresent()) {
                                        status = controller.getRuntimeState().name();
                                    } else {
                                        status = "OFFLINE / CONTROLLER MISSING";
                                    }
                                }
                                data.writeUtf(status, 128);
                            }
                        }

                        var primary = city == null ? null : city.getDevelopmentPrimaryResource();
                        data.writeBoolean(primary != null);
                        if (primary != null) {
                            data.writeResourceLocation(primary);
                        }
                        var requirements = primary == null
                                ? java.util.List.<CityDevelopmentRequirements.Requirement>of()
                                : CityDevelopmentRequirements.forLevelTwo(primary);
                        data.writeVarInt(requirements.size());
                        for (var requirement : requirements) {
                            data.writeEnum(requirement.group());
                            data.writeResourceLocation(requirement.itemId());
                            data.writeVarLong(city == null ? 0L
                                    : city.getDevelopmentContribution(requirement.itemId()));
                            data.writeVarLong(requirement.amount());
                        }
                    }
            );
        }

        return InteractionResult.sidedSuccess(
                level.isClientSide
        );
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)
                || !(placer instanceof ServerPlayer player)) {
            return;
        }
        CityManager manager = CityManager.get(serverLevel.getServer().overworld());
        var dimension = serverLevel.dimension().location();
        UUID linkedCityId = com.example.examplemod.city.CityPylonItemData.read(stack).orElse(null);
        City target = linkedCityId == null
                ? manager.getCityByOwner(player.getUUID())
                : manager.getCity(linkedCityId);

        if (linkedCityId != null && (target == null
                || !target.getOwner().equals(player.getUUID()))) {
            rejectPlacement(serverLevel, pos, player, stack,
                    "message.createcityexpansioncore.pylon.restore.not_owner");
            return;
        }
        City existingAtPosition = manager.getCityByPylon(dimension, pos);
        if (linkedCityId == null && existingAtPosition != null
                && !existingAtPosition.getOwner().equals(player.getUUID())) {
            rejectPlacement(serverLevel, pos, player, stack,
                    "message.createcityexpansioncore.pylon.restore.foreign_city");
            return;
        }
        if (target == null) {
            return;
        }
        if (target.isPylonActive()) {
            rejectPlacement(serverLevel, pos, player, stack,
                    "message.createcityexpansioncore.pylon.restore.already_active");
            return;
        }
        if (!manager.rebindPylon(target, dimension, pos)) {
            rejectPlacement(serverLevel, pos, player, stack,
                    "message.createcityexpansioncore.pylon.restore.failed");
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.pylon.restore.complete", target.getName()
        ));
    }

    private static void rejectPlacement(
            ServerLevel level,
            BlockPos pos,
            Player player,
            ItemStack source,
            String message
    ) {
        level.removeBlock(pos, false);
        if (!player.getAbilities().instabuild) {
            ItemStack refund = source.copy();
            refund.setCount(1);
            Block.popResource(level, pos, refund);
        }
        player.sendSystemMessage(Component.translatable(message));
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player
    ) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            CityManager manager = CityManager.get(serverLevel.getServer().overworld());
            City city = manager.getCityByPylon(serverLevel.dimension().location(), pos);
            if (player.getAbilities().instabuild) {
                Block.popResource(serverLevel, pos, transferableStack(city));
            }
            if (city != null) {
                manager.markPylonRelocating(city);
                player.sendSystemMessage(Component.translatable(
                        "message.createcityexpansioncore.pylon.relocating", city.getName()
                ));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            net.minecraft.world.item.ItemStack tool
    ) {

        super.playerDestroy(
                level,
                player,
                pos,
                state,
                blockEntity,
                tool
        );

        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && !player.getAbilities().instabuild) {
            City city = CityManager.get(serverLevel.getServer().overworld())
                    .getCityByPylon(serverLevel.dimension().location(), pos);
            Block.popResource(serverLevel, pos, transferableStack(city));
        }
    }

    private static ItemStack transferableStack(City city) {
        ItemStack result = new ItemStack(CreateCityExpansionCore.CITY_PYLON_ITEM.get());
        if (city != null) {
            com.example.examplemod.city.CityPylonItemData.write(result, city.getId());
        }
        return result;
    }
}
