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
     * Модель Astra имеет фронтальную сторону: экран, лампы и селектор
     * обращены в -Z (north). Без этого свойства модель всегда смотрела бы
     * на north.
     *
     * <p>Значение по умолчанию — NORTH, то есть ровно то, что было до
     * добавления свойства. Уже установленные пилоны в сохранённых мирах
     * получат NORTH и потому будут выглядеть так же, как раньше.
     */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Максимальная удалённость соседнего города от пилона,
     * чтобы он мог попасть в кадр карты. Совпадает с
     * MAX_PAN_DISTANCE панели карты.
     */
    private static final int MAP_PEER_RANGE = 512;

    /**
     * Габариты collision/selection из integration_manifest.json.
     * Тело пилона: основание 1..15, корпус 2..13, верхний модуль 13..15.9.
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
         * NORTH — значение по умолчанию. Оно же сохраняет текущий
         * внешний вид для всех пилонов, установленных до этого изменения.
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
     * BaseEntityBlock по умолчанию возвращает INVISIBLE, потому что рассчитывает
     * на полностью кастомный рендерер. Здесь корпус рисует обычный blockstate,
     * поэтому возвращаем MODEL: анимированные части добавляет BlockEntityRenderer.
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
     * Вычисляет, активен ли пилон, по данным CityManager.
     *
     * <p>Чистая функция чтения: не обращается к BlockEntity и ничего
     * не меняет. Это принципиально, потому что вызывается из
     * {@code BlockEntity#setLevel}, то есть в момент регистрации
     * самого BlockEntity. Любой {@code level.getBlockEntity(pos)}
     * здесь вызвал бы повторную регистрацию и переполнение стека.
     *
     * @return true, если за позицией стоит город и пилон активен
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
     * Обновляет флаг активности уже существующего BlockEntity.
     *
     * <p>Безопасно вызывать только когда BlockEntity уже зарегистрирован
     * в чанке, то есть снаружи setLevel/addAndRegisterBlockEntity.
     * Используется при размещении блока.
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
                // Синхронизируем визуальный флаг с моделью города.
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

            /*
             * =========================================
             * СОСЕДНИЕ ГОРОДА ДЛЯ КАРТЫ
             * =========================================
             *
             * Отдельный разовый пакет: список городов не влезает в
             * данные меню, потому что меню открывается на конкретного
             * игрока, а карта интересует всем, кто на неё смотрит.
             *
             * Передаётся ТОЛЬКО визуальная информация: имя, позиция
             * пилона и размер территории. Ни прав, ни экономики, ни
             * проектов чужих городов клиент не получает.
             *
             * Список строится один раз из CityManager.getCities(),
             * то есть из канонического хранилища городов. Собственного
             * хранилища «соседей» не заводится.
             */
            sendMapPeers(serverPlayer, city, pos);
        }

        return InteractionResult.sidedSuccess(
                level.isClientSide
        );
    }

    /**
     * Шлёт клиенту список соседних городов в пределах карты.
     *
     * <p>Фильтрует по измерению и по удалённости от текущего пилона,
     * чтобы не отправлять города, которые игрок всё равно не увидит.
     * Это же ограничивает объём пакета без обхода чанков.
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
             * Карта физически показывает область вокруг пилона.
             * Города дальше максимальной дистанции не попадут
             * в кадр, поэтому и не отправляются.
             */
            if (distance > MAP_PEER_RANGE) {
                continue;
            }

            boolean own = ownCity != null
                    && other.getId().equals(ownCity.getId());

            peers.add(new CityMapPeersPayload.Peer(
                    other.getId(),
                    other.getName(),
                    otherPos.getX(),
                    otherPos.getZ(),
                    other.getTerritorySize(),
                    own,
                    other.getId().equals(com.example.examplemod.economy.EconomyManager.get(player.serverLevel()).masterCityId())
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
         * Обновляем визуальный флаг активности сразу после установки,
         * чтобы лампы были корректны и для нового пилона.
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
        // rebindPylon переводит город в активное состояние — обновляем лампы.
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
                // Пилон снят -> лампы должны погаснуть сразу.
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
