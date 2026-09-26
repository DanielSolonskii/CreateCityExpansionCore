package com.example.examplemod.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityDevelopmentRequirements;
import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityMapPeersPayload;
import com.mojang.serialization.MapCodec;
import com.example.examplemod.city.CityMember;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CityPylonBlock extends BaseEntityBlock {

    public static final MapCodec<CityPylonBlock> CODEC =
            simpleCodec(CityPylonBlock::new);

    /**
     * ÐœÐ¾Ð´ÐµÐ»ÑŒ Astra Ð¸Ð¼ÐµÐµÑ‚ Ñ„Ñ€Ð¾Ð½Ñ‚Ð°Ð»ÑŒÐ½ÑƒÑŽ ÑÑ‚Ð¾Ñ€Ð¾Ð½Ñƒ: ÑÐºÑ€Ð°Ð½, Ð»Ð°Ð¼Ð¿Ñ‹ Ð¸ ÑÐµÐ»ÐµÐºÑ‚Ð¾Ñ€
     * Ð¾Ð±Ñ€Ð°Ñ‰ÐµÐ½Ñ‹ Ð² -Z (north). Ð‘ÐµÐ· ÑÑ‚Ð¾Ð³Ð¾ ÑÐ²Ð¾Ð¹ÑÑ‚Ð²Ð° Ð¼Ð¾Ð´ÐµÐ»ÑŒ Ð²ÑÐµÐ³Ð´Ð° ÑÐ¼Ð¾Ñ‚Ñ€ÐµÐ»Ð° Ð±Ñ‹
     * Ð½Ð° north.
     *
     * <p>Ð—Ð½Ð°Ñ‡ÐµÐ½Ð¸Ðµ Ð¿Ð¾ ÑƒÐ¼Ð¾Ð»Ñ‡Ð°Ð½Ð¸ÑŽ â€” NORTH, Ñ‚Ð¾ ÐµÑÑ‚ÑŒ Ñ€Ð¾Ð²Ð½Ð¾ Ñ‚Ð¾, Ñ‡Ñ‚Ð¾ Ð±Ñ‹Ð»Ð¾ Ð´Ð¾
     * Ð´Ð¾Ð±Ð°Ð²Ð»ÐµÐ½Ð¸Ñ ÑÐ²Ð¾Ð¹ÑÑ‚Ð²Ð°. Ð£Ð¶Ðµ ÑƒÑÑ‚Ð°Ð½Ð¾Ð²Ð»ÐµÐ½Ð½Ñ‹Ðµ Ð¿Ð¸Ð»Ð¾Ð½Ñ‹ Ð² ÑÐ¾Ñ…Ñ€Ð°Ð½Ñ‘Ð½Ð½Ñ‹Ñ… Ð¼Ð¸Ñ€Ð°Ñ…
     * Ð¿Ð¾Ð»ÑƒÑ‡Ð°Ñ‚ NORTH Ð¸ Ð¿Ð¾Ñ‚Ð¾Ð¼Ñƒ Ð±ÑƒÐ´ÑƒÑ‚ Ð²Ñ‹Ð³Ð»ÑÐ´ÐµÑ‚ÑŒ Ñ‚Ð°Ðº Ð¶Ðµ, ÐºÐ°Ðº Ñ€Ð°Ð½ÑŒÑˆÐµ.
     */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /**
     * ÐœÐ°ÐºÑÐ¸Ð¼Ð°Ð»ÑŒÐ½Ð°Ñ ÑƒÐ´Ð°Ð»Ñ‘Ð½Ð½Ð¾ÑÑ‚ÑŒ ÑÐ¾ÑÐµÐ´Ð½ÐµÐ³Ð¾ Ð³Ð¾Ñ€Ð¾Ð´Ð° Ð¾Ñ‚ Ð¿Ð¸Ð»Ð¾Ð½Ð°,
     * Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð¾Ð½ Ð¼Ð¾Ð³ Ð¿Ð¾Ð¿Ð°ÑÑ‚ÑŒ Ð² ÐºÐ°Ð´Ñ€ ÐºÐ°Ñ€Ñ‚Ñ‹. Ð¡Ð¾Ð²Ð¿Ð°Ð´Ð°ÐµÑ‚ Ñ
     * MAX_PAN_DISTANCE Ð¿Ð°Ð½ÐµÐ»Ð¸ ÐºÐ°Ñ€Ñ‚Ñ‹.
     */
    private static final int MAP_PEER_RANGE = 512;

    /**
     * Ð“Ð°Ð±Ð°Ñ€Ð¸Ñ‚Ñ‹ collision/selection Ð¸Ð· integration_manifest.json.
     * Ð¢ÐµÐ»Ð¾ Ð¿Ð¸Ð»Ð¾Ð½Ð°: Ð¾ÑÐ½Ð¾Ð²Ð°Ð½Ð¸Ðµ 1..15, ÐºÐ¾Ñ€Ð¿ÑƒÑ 2..13, Ð²ÐµÑ€Ñ…Ð½Ð¸Ð¹ Ð¼Ð¾Ð´ÑƒÐ»ÑŒ 13..15.9.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.85D, 15.0D),
            Block.box(2.0D, 2.85D, 3.0D, 14.0D, 13.0D, 14.0D),
            Block.box(4.5D, 13.0D, 5.5D, 11.5D, 15.9D, 12.5D)
    );

    public CityPylonBlock(
            BlockBehaviour.Properties properties
    ) {
        super(properties);
        /*
         * NORTH â€” Ð·Ð½Ð°Ñ‡ÐµÐ½Ð¸Ðµ Ð¿Ð¾ ÑƒÐ¼Ð¾Ð»Ñ‡Ð°Ð½Ð¸ÑŽ. ÐžÐ½Ð¾ Ð¶Ðµ ÑÐ¾Ñ…Ñ€Ð°Ð½ÑÐµÑ‚ Ñ‚ÐµÐºÑƒÑ‰Ð¸Ð¹
         * Ð²Ð½ÐµÑˆÐ½Ð¸Ð¹ Ð²Ð¸Ð´ Ð´Ð»Ñ Ð²ÑÐµÑ… Ð¿Ð¸Ð»Ð¾Ð½Ð¾Ð², ÑƒÑÑ‚Ð°Ð½Ð¾Ð²Ð»ÐµÐ½Ð½Ñ‹Ñ… Ð´Ð¾ ÑÑ‚Ð¾Ð³Ð¾ Ð¸Ð·Ð¼ÐµÐ½ÐµÐ½Ð¸Ñ.
         */
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * BaseEntityBlock Ð¿Ð¾ ÑƒÐ¼Ð¾Ð»Ñ‡Ð°Ð½Ð¸ÑŽ Ð²Ð¾Ð·Ð²Ñ€Ð°Ñ‰Ð°ÐµÑ‚ INVISIBLE, Ð¿Ð¾Ñ‚Ð¾Ð¼Ñƒ Ñ‡Ñ‚Ð¾ Ñ€Ð°ÑÑÑ‡Ð¸Ñ‚Ñ‹Ð²Ð°ÐµÑ‚
     * Ð½Ð° Ð¿Ð¾Ð»Ð½Ð¾ÑÑ‚ÑŒÑŽ ÐºÐ°ÑÑ‚Ð¾Ð¼Ð½Ñ‹Ð¹ Ñ€ÐµÐ½Ð´ÐµÑ€ÐµÑ€. Ð—Ð´ÐµÑÑŒ ÐºÐ¾Ñ€Ð¿ÑƒÑ Ñ€Ð¸ÑÑƒÐµÑ‚ Ð¾Ð±Ñ‹Ñ‡Ð½Ñ‹Ð¹ blockstate,
     * Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ Ð²Ð¾Ð·Ð²Ñ€Ð°Ñ‰Ð°ÐµÐ¼ MODEL: Ð°Ð½Ð¸Ð¼Ð¸Ñ€Ð¾Ð²Ð°Ð½Ð½Ñ‹Ðµ Ñ‡Ð°ÑÑ‚Ð¸ Ð´Ð¾Ð±Ð°Ð²Ð»ÑÐµÑ‚ BlockEntityRenderer.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<
                    net.minecraft.world.level.block.Block,
                    BlockState
                    > builder
    ) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new CityPylonBlockEntity(pos, state);
    }

    /**
     * Ð’Ñ‹Ñ‡Ð¸ÑÐ»ÑÐµÑ‚, Ð°ÐºÑ‚Ð¸Ð²ÐµÐ½ Ð»Ð¸ Ð¿Ð¸Ð»Ð¾Ð½, Ð¿Ð¾ Ð´Ð°Ð½Ð½Ñ‹Ð¼ CityManager.
     *
     * <p>Ð§Ð¸ÑÑ‚Ð°Ñ Ñ„ÑƒÐ½ÐºÑ†Ð¸Ñ Ñ‡Ñ‚ÐµÐ½Ð¸Ñ: Ð½Ðµ Ð¾Ð±Ñ€Ð°Ñ‰Ð°ÐµÑ‚ÑÑ Ðº BlockEntity Ð¸ Ð½Ð¸Ñ‡ÐµÐ³Ð¾
     * Ð½Ðµ Ð¼ÐµÐ½ÑÐµÑ‚. Ð­Ñ‚Ð¾ Ð¿Ñ€Ð¸Ð½Ñ†Ð¸Ð¿Ð¸Ð°Ð»ÑŒÐ½Ð¾, Ð¿Ð¾Ñ‚Ð¾Ð¼Ñƒ Ñ‡Ñ‚Ð¾ Ð²Ñ‹Ð·Ñ‹Ð²Ð°ÐµÑ‚ÑÑ Ð¸Ð·
     * {@code BlockEntity#setLevel}, Ñ‚Ð¾ ÐµÑÑ‚ÑŒ Ð² Ð¼Ð¾Ð¼ÐµÐ½Ñ‚ Ñ€ÐµÐ³Ð¸ÑÑ‚Ñ€Ð°Ñ†Ð¸Ð¸
     * ÑÐ°Ð¼Ð¾Ð³Ð¾ BlockEntity. Ð›ÑŽÐ±Ð¾Ð¹ {@code level.getBlockEntity(pos)}
     * Ð·Ð´ÐµÑÑŒ Ð²Ñ‹Ð·Ð²Ð°Ð» Ð±Ñ‹ Ð¿Ð¾Ð²Ñ‚Ð¾Ñ€Ð½ÑƒÑŽ Ñ€ÐµÐ³Ð¸ÑÑ‚Ñ€Ð°Ñ†Ð¸ÑŽ Ð¸ Ð¿ÐµÑ€ÐµÐ¿Ð¾Ð»Ð½ÐµÐ½Ð¸Ðµ ÑÑ‚ÐµÐºÐ°.
     *
     * @return true, ÐµÑÐ»Ð¸ Ð·Ð° Ð¿Ð¾Ð·Ð¸Ñ†Ð¸ÐµÐ¹ ÑÑ‚Ð¾Ð¸Ñ‚ Ð³Ð¾Ñ€Ð¾Ð´ Ð¸ Ð¿Ð¸Ð»Ð¾Ð½ Ð°ÐºÑ‚Ð¸Ð²ÐµÐ½
     */
    public static boolean computeActive(
            Level level,
            BlockPos pos
    ) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        City city = CityManager.get(serverLevel.getServer().overworld())
                .getCityByPylon(serverLevel.dimension().location(), pos);
        return city != null && city.isPylonActive();
    }

    /**
     * ÐžÐ±Ð½Ð¾Ð²Ð»ÑÐµÑ‚ Ñ„Ð»Ð°Ð³ Ð°ÐºÑ‚Ð¸Ð²Ð½Ð¾ÑÑ‚Ð¸ ÑƒÐ¶Ðµ ÑÑƒÑ‰ÐµÑÑ‚Ð²ÑƒÑŽÑ‰ÐµÐ³Ð¾ BlockEntity.
     *
     * <p>Ð‘ÐµÐ·Ð¾Ð¿Ð°ÑÐ½Ð¾ Ð²Ñ‹Ð·Ñ‹Ð²Ð°Ñ‚ÑŒ Ñ‚Ð¾Ð»ÑŒÐºÐ¾ ÐºÐ¾Ð³Ð´Ð° BlockEntity ÑƒÐ¶Ðµ Ð·Ð°Ñ€ÐµÐ³Ð¸ÑÑ‚Ñ€Ð¸Ñ€Ð¾Ð²Ð°Ð½
     * Ð² Ñ‡Ð°Ð½ÐºÐµ, Ñ‚Ð¾ ÐµÑÑ‚ÑŒ ÑÐ½Ð°Ñ€ÑƒÐ¶Ð¸ setLevel/addAndRegisterBlockEntity.
     * Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ÑÑ Ð¿Ñ€Ð¸ Ñ€Ð°Ð·Ð¼ÐµÑ‰ÐµÐ½Ð¸Ð¸ Ð±Ð»Ð¾ÐºÐ°.
     */
    public static void refreshActive(
            Level level,
            BlockPos pos
    ) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof CityPylonBlockEntity pylon) {
            pylon.setActive(computeActive(level, pos));
        }
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
                // Ð¡Ð¸Ð½Ñ…Ñ€Ð¾Ð½Ð¸Ð·Ð¸Ñ€ÑƒÐµÐ¼ Ð²Ð¸Ð·ÑƒÐ°Ð»ÑŒÐ½Ñ‹Ð¹ Ñ„Ð»Ð°Ð³ Ñ Ð¼Ð¾Ð´ÐµÐ»ÑŒÑŽ Ð³Ð¾Ñ€Ð¾Ð´Ð°.
                refreshActive(serverPlayer.serverLevel(), pos);
            }

            serverPlayer.openMenu(
                    state.getMenuProvider(
                            level,
                            pos
                    ),
                    data -> {

                        /*
                         * =================================
                         * ÐŸÐžÐ—Ð˜Ð¦Ð˜Ð¯ ÐŸÐ˜Ð›ÐžÐÐ
                         * =================================
                         */

                        data.writeBlockPos(pos);
                        data.writeResourceLocation(
                                serverPlayer.serverLevel().dimension().location()
                        );

                        /*
                         * =================================
                         * Ð•Ð¡Ð¢Ð¬ Ð›Ð˜ Ð“ÐžÐ ÐžÐ”
                         * =================================
                         */

                        data.writeBoolean(
                                city != null
                        );

                        /*
                         * =================================
                         * ÐÐÐ—Ð’ÐÐÐ˜Ð•
                         * =================================
                         */

                        data.writeUtf(
                                city != null
                                        ? city.getName()
                                        : ""
                        );

                        /*
                         * =================================
                         * Ð£Ð ÐžÐ’Ð•ÐÐ¬
                         * =================================
                         */

                        data.writeVarInt(
                                city != null
                                        ? city.getLevel()
                                        : 1
                        );

                        /*
                         * =================================
                         * Ð¢Ð•Ð Ð Ð˜Ð¢ÐžÐ Ð˜Ð¯
                         * =================================
                         */

                        data.writeVarInt(
                                city != null
                                        ? city.getTerritorySize()
                                        : 100
                        );

                        /*
                         * =================================
                         * Ð•Ð¡Ð¢Ð¬ Ð›Ð˜ Ð’Ð›ÐÐ”Ð•Ð›Ð•Ð¦
                         * =================================
                         */

                        data.writeBoolean(
                                city != null
                        );

                        /*
                         * =================================
                         * UUID Ð’Ð›ÐÐ”Ð•Ð›Ð¬Ð¦Ð
                         * =================================
                         */

                        if (city != null) {

                            data.writeUUID(
                                    city.getOwner()
                            );
                        }

                        /*
                         * =================================
                         * Ð£Ð§ÐÐ¡Ð¢ÐÐ˜ÐšÐ˜
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

            /*
             * =========================================
             * Ð¡ÐžÐ¡Ð•Ð”ÐÐ˜Ð• Ð“ÐžÐ ÐžÐ”Ð Ð”Ð›Ð¯ ÐšÐÐ Ð¢Ð«
             * =========================================
             *
             * ÐžÑ‚Ð´ÐµÐ»ÑŒÐ½Ñ‹Ð¹ Ñ€Ð°Ð·Ð¾Ð²Ñ‹Ð¹ Ð¿Ð°ÐºÐµÑ‚: ÑÐ¿Ð¸ÑÐ¾Ðº Ð³Ð¾Ñ€Ð¾Ð´Ð¾Ð² Ð½Ðµ Ð²Ð»ÐµÐ·Ð°ÐµÑ‚ Ð²
             * Ð´Ð°Ð½Ð½Ñ‹Ðµ Ð¼ÐµÐ½ÑŽ, Ð¿Ð¾Ñ‚Ð¾Ð¼Ñƒ Ñ‡Ñ‚Ð¾ Ð¼ÐµÐ½ÑŽ Ð¾Ñ‚ÐºÑ€Ñ‹Ð²Ð°ÐµÑ‚ÑÑ Ð½Ð° ÐºÐ¾Ð½ÐºÑ€ÐµÑ‚Ð½Ð¾Ð³Ð¾
             * Ð¸Ð³Ñ€Ð¾ÐºÐ°, Ð° ÐºÐ°Ñ€Ñ‚Ð° Ð¸Ð½Ñ‚ÐµÑ€ÐµÑÑƒÐµÑ‚ Ð²ÑÐµÐ¼, ÐºÑ‚Ð¾ Ð½Ð° Ð½ÐµÑ‘ ÑÐ¼Ð¾Ñ‚Ñ€Ð¸Ñ‚.
             *
             * ÐŸÐµÑ€ÐµÐ´Ð°Ñ‘Ñ‚ÑÑ Ð¢ÐžÐ›Ð¬ÐšÐž Ð²Ð¸Ð·ÑƒÐ°Ð»ÑŒÐ½Ð°Ñ Ð¸Ð½Ñ„Ð¾Ñ€Ð¼Ð°Ñ†Ð¸Ñ: Ð¸Ð¼Ñ, Ð¿Ð¾Ð·Ð¸Ñ†Ð¸Ñ
             * Ð¿Ð¸Ð»Ð¾Ð½Ð° Ð¸ Ñ€Ð°Ð·Ð¼ÐµÑ€ Ñ‚ÐµÑ€Ñ€Ð¸Ñ‚Ð¾Ñ€Ð¸Ð¸. ÐÐ¸ Ð¿Ñ€Ð°Ð², Ð½Ð¸ ÑÐºÐ¾Ð½Ð¾Ð¼Ð¸ÐºÐ¸, Ð½Ð¸
             * Ð¿Ñ€Ð¾ÐµÐºÑ‚Ð¾Ð² Ñ‡ÑƒÐ¶Ð¸Ñ… Ð³Ð¾Ñ€Ð¾Ð´Ð¾Ð² ÐºÐ»Ð¸ÐµÐ½Ñ‚ Ð½Ðµ Ð¿Ð¾Ð»ÑƒÑ‡Ð°ÐµÑ‚.
             *
             * Ð¡Ð¿Ð¸ÑÐ¾Ðº ÑÑ‚Ñ€Ð¾Ð¸Ñ‚ÑÑ Ð¾Ð´Ð¸Ð½ Ñ€Ð°Ð· Ð¸Ð· CityManager.getCities(),
             * Ñ‚Ð¾ ÐµÑÑ‚ÑŒ Ð¸Ð· ÐºÐ°Ð½Ð¾Ð½Ð¸Ñ‡ÐµÑÐºÐ¾Ð³Ð¾ Ñ…Ñ€Ð°Ð½Ð¸Ð»Ð¸Ñ‰Ð° Ð³Ð¾Ñ€Ð¾Ð´Ð¾Ð². Ð¡Ð¾Ð±ÑÑ‚Ð²ÐµÐ½Ð½Ð¾Ð³Ð¾
             * Ñ…Ñ€Ð°Ð½Ð¸Ð»Ð¸Ñ‰Ð° Â«ÑÐ¾ÑÐµÐ´ÐµÐ¹Â» Ð½Ðµ Ð·Ð°Ð²Ð¾Ð´Ð¸Ñ‚ÑÑ.
             */
            sendMapPeers(serverPlayer, city, pos);
        }

        return InteractionResult.sidedSuccess(
                level.isClientSide
        );
    }

    /**
     * Ð¨Ð»Ñ‘Ñ‚ ÐºÐ»Ð¸ÐµÐ½Ñ‚Ñƒ ÑÐ¿Ð¸ÑÐ¾Ðº ÑÐ¾ÑÐµÐ´Ð½Ð¸Ñ… Ð³Ð¾Ñ€Ð¾Ð´Ð¾Ð² Ð² Ð¿Ñ€ÐµÐ´ÐµÐ»Ð°Ñ… ÐºÐ°Ñ€Ñ‚Ñ‹.
     *
     * <p>Ð¤Ð¸Ð»ÑŒÑ‚Ñ€ÑƒÐµÑ‚ Ð¿Ð¾ Ð¸Ð·Ð¼ÐµÑ€ÐµÐ½Ð¸ÑŽ Ð¸ Ð¿Ð¾ ÑƒÐ´Ð°Ð»Ñ‘Ð½Ð½Ð¾ÑÑ‚Ð¸ Ð¾Ñ‚ Ñ‚ÐµÐºÑƒÑ‰ÐµÐ³Ð¾ Ð¿Ð¸Ð»Ð¾Ð½Ð°,
     * Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð½Ðµ Ð¾Ñ‚Ð¿Ñ€Ð°Ð²Ð»ÑÑ‚ÑŒ Ð³Ð¾Ñ€Ð¾Ð´Ð°, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ðµ Ð¸Ð³Ñ€Ð¾Ðº Ð²ÑÑ‘ Ñ€Ð°Ð²Ð½Ð¾ Ð½Ðµ ÑƒÐ²Ð¸Ð´Ð¸Ñ‚.
     * Ð­Ñ‚Ð¾ Ð¶Ðµ Ð¾Ð³Ñ€Ð°Ð½Ð¸Ñ‡Ð¸Ð²Ð°ÐµÑ‚ Ð¾Ð±ÑŠÑ‘Ð¼ Ð¿Ð°ÐºÐµÑ‚Ð° Ð±ÐµÐ· Ð¾Ð±Ñ…Ð¾Ð´Ð° Ñ‡Ð°Ð½ÐºÐ¾Ð².
     */
    private static void sendMapPeers(
            ServerPlayer player,
            City ownCity,
            BlockPos ownPos
    ) {
        var dimension = player.serverLevel().dimension().location();

        var peers = new ArrayList<CityMapPeersPayload.Peer>();

        for (City other : CityManager.get(player.serverLevel().getServer().overworld())
                .getCities()) {

            if (other.getPylonDimension() == null
                    || !other.getPylonDimension().equals(dimension)) {
                continue;
            }

            BlockPos otherPos = other.getPylonPos();
            int distance = (int) Math.max(
                    Math.abs(otherPos.getX() - ownPos.getX()),
                    Math.abs(otherPos.getZ() - ownPos.getZ())
            );

            /*
             * ÐšÐ°Ñ€Ñ‚Ð° Ñ„Ð¸Ð·Ð¸Ñ‡ÐµÑÐºÐ¸ Ð¿Ð¾ÐºÐ°Ð·Ñ‹Ð²Ð°ÐµÑ‚ Ð¾Ð±Ð»Ð°ÑÑ‚ÑŒ Ð²Ð¾ÐºÑ€ÑƒÐ³ Ð¿Ð¸Ð»Ð¾Ð½Ð°.
             * Ð“Ð¾Ñ€Ð¾Ð´Ð° Ð´Ð°Ð»ÑŒÑˆÐµ Ð¼Ð°ÐºÑÐ¸Ð¼Ð°Ð»ÑŒÐ½Ð¾Ð¹ Ð´Ð¸ÑÑ‚Ð°Ð½Ñ†Ð¸Ð¸ Ð½Ðµ Ð¿Ð¾Ð¿Ð°Ð´ÑƒÑ‚
             * Ð² ÐºÐ°Ð´Ñ€, Ð¿Ð¾ÑÑ‚Ð¾Ð¼Ñƒ Ð¸ Ð½Ðµ Ð¾Ñ‚Ð¿Ñ€Ð°Ð²Ð»ÑÑŽÑ‚ÑÑ.
             */
            if (distance > MAP_PEER_RANGE) {
                continue;
            }

            boolean own = ownCity != null
                    && other.getId().equals(ownCity.getId());

            peers.add(new CityMapPeersPayload.Peer(
                    other.getName(),
                    otherPos.getX(),
                    otherPos.getZ(),
                    other.getTerritorySize(),
                    own
            ));

            if (peers.size() >= CityMapPeersPayload.MAX_PEERS) {
                break;
            }
        }

        PacketDistributor.sendToPlayer(player, new CityMapPeersPayload(
                ownPos,
                dimension,
                peers
        ));
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
        /*
         * ÐžÐ±Ð½Ð¾Ð²Ð»ÑÐµÐ¼ Ð²Ð¸Ð·ÑƒÐ°Ð»ÑŒÐ½Ñ‹Ð¹ Ñ„Ð»Ð°Ð³ Ð°ÐºÑ‚Ð¸Ð²Ð½Ð¾ÑÑ‚Ð¸ ÑÑ€Ð°Ð·Ñƒ Ð¿Ð¾ÑÐ»Ðµ ÑƒÑÑ‚Ð°Ð½Ð¾Ð²ÐºÐ¸,
         * Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð»Ð°Ð¼Ð¿Ñ‹ Ð±Ñ‹Ð»Ð¸ ÐºÐ¾Ñ€Ñ€ÐµÐºÑ‚Ð½Ñ‹ Ð¸ Ð´Ð»Ñ Ð½Ð¾Ð²Ð¾Ð³Ð¾ Ð¿Ð¸Ð»Ð¾Ð½Ð°.
         */
        refreshActive(level, pos);
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
        // rebindPylon Ð¿ÐµÑ€ÐµÐ²Ð¾Ð´Ð¸Ñ‚ Ð³Ð¾Ñ€Ð¾Ð´ Ð² Ð°ÐºÑ‚Ð¸Ð²Ð½Ð¾Ðµ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ðµ â€” Ð¾Ð±Ð½Ð¾Ð²Ð»ÑÐµÐ¼ Ð»Ð°Ð¼Ð¿Ñ‹.
        refreshActive(serverLevel, pos);
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
                // ÐŸÐ¸Ð»Ð¾Ð½ ÑÐ½ÑÑ‚ -> Ð»Ð°Ð¼Ð¿Ñ‹ Ð´Ð¾Ð»Ð¶Ð½Ñ‹ Ð¿Ð¾Ð³Ð°ÑÐ½ÑƒÑ‚ÑŒ ÑÑ€Ð°Ð·Ñƒ.
                refreshActive(serverLevel, pos);
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
