package com.example.examplemod.electrical;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityProject;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.mine.MineControllerItemData;
import com.example.examplemod.mine.MineNetworkHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public class MineControllerBlock extends Block implements EntityBlock {

    public MineControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any().setValue(
                        BlockStateProperties.HORIZONTAL_FACING,
                        Direction.NORTH
                )
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(
            net.minecraft.world.item.context.BlockPlaceContext context
    ) {
        return defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineControllerBlockEntity(pos, state);
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
        if (!level.isClientSide()
                && level instanceof ServerLevel serverLevel
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MineControllerBlockEntity controller) {
            MineControllerItemData.Data data = MineControllerItemData.read(stack).orElse(null);
            if (data == null) {
                controller.setCreator(player.getUUID(), com.example.examplemod.auth.PlayerNames.name(serverLevel.getServer(), player.getUUID()));
                return;
            }
            if (data.commissioned()
                    && CityManager.get(serverLevel.getServer().overworld())
                    .getProject(data.projectId()) == null) {
                data = data.withoutProject();
            }
            if (!canRestore(serverLevel, pos, player, data)) {
                rejectPlacement(serverLevel, pos, player, stack,
                        "message.createcityexpansioncore.controller.restore.denied");
                return;
            }
            if (data.commissioned()) {
                CityManager manager = CityManager.get(serverLevel.getServer().overworld());
                CityProject project = manager.getProject(data.projectId());
                if (!manager.relocateProject(
                        project, serverLevel.dimension().location(), pos
                )) {
                    rejectPlacement(serverLevel, pos, player, stack,
                            "message.createcityexpansioncore.controller.restore.project_failed");
                    return;
                }
            }
            controller.restoreFromItem(data);
        }
    }

    private static boolean canRestore(
            ServerLevel level,
            BlockPos pos,
            Player player,
            MineControllerItemData.Data data
    ) {
        if (data.creatorId() != null && !data.creatorId().equals(player.getUUID())) {
            return false;
        }
        if (!data.commissioned()) {
            return true;
        }
        CityManager manager = CityManager.get(level.getServer().overworld());
        City city = manager.getCity(data.cityId());
        CityProject project = manager.getProject(data.projectId());
        if (city == null || project == null || !city.canBuild(player.getUUID())
                || !project.cityId().equals(data.cityId())
                || !project.creatorId().equals(data.creatorId())
                || !project.depositId().equals(data.depositId())
                || !project.resourceId().equals(data.resourceId())
                || com.example.examplemod.geology.GeologyManager.getRegion(level, pos)
                .getDeposit(data.depositId())
                .filter(deposit -> deposit.getResourceId().equals(data.resourceId()))
                .isEmpty()) {
            return false;
        }
        ServerLevel oldLevel = level.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        project.dimensionId()
                )
        );
        if (oldLevel != null
                && (!project.dimensionId().equals(level.dimension().location())
                || !project.controllerPos().equals(pos))
                && oldLevel.hasChunkAt(project.controllerPos())
                && oldLevel.getBlockEntity(project.controllerPos())
                instanceof MineControllerBlockEntity existing
                && existing.getProjectId().filter(project.projectId()::equals).isPresent()) {
            return false;
        }
        return true;
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
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel
                && player.getAbilities().instabuild) {
            Block.popResource(serverLevel, pos, transferableStack(serverLevel, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            ItemStack tool
    ) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel
                && !player.getAbilities().instabuild) {
            Block.popResource(serverLevel, pos,
                    transferableStack(serverLevel, blockEntity));
        }
    }

    private static ItemStack transferableStack(ServerLevel level, BlockPos pos) {
        return transferableStack(level, level.getBlockEntity(pos));
    }

    private static ItemStack transferableStack(ServerLevel level, BlockEntity blockEntity) {
        ItemStack result = new ItemStack(CreateCityExpansionCore.MINE_CONTROLLER_ITEM.get());
        if (blockEntity instanceof MineControllerBlockEntity controller) {
            MineControllerItemData.write(result, MineControllerItemData.capture(level, controller));
        }
        return result;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, blockState, blockEntity) -> {
            if (tickerLevel instanceof ServerLevel serverLevel
                    && blockEntity instanceof MineControllerBlockEntity controller) {
                MineControllerBlockEntity.serverTick(serverLevel, pos, blockState, controller);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos)
                instanceof MineControllerBlockEntity controller)) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.createcityexpansioncore.controller.invalid_state"
            ));
            return InteractionResult.SUCCESS;
        }

        MineNetworkHandlers.open(serverPlayer, pos, controller);

        return InteractionResult.SUCCESS;
    }
}
