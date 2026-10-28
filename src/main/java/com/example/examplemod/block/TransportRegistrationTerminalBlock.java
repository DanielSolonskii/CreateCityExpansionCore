package com.example.examplemod.block;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Терминал регистрации транспорта.
 *
 * <p>Заглушка: блок с моделью старого моноблочного компьютера
 * (чёрный корпус, латунная окантовка), который ставится экраном
 * к игроку. Функционал регистрации вводится отдельной задачей,
 * поэтому пока у блока нет BlockEntity и интеракции.
 *
 * <p>Модель смотрит фронтом на -Z (north), как у CityPylonBlock:
 * свойство HORIZONTAL_FACING поворачивает её к игроку при установке.
 *
 * <p>При установке блок запоминает город, участником которого является
 * ставящий игрок (см. TransportRegistrationTerminalBlockEntity).
 * Игрок вне города получает сообщение, что функционала у терминала
 * пока нет, — блок при этом всё равно ставится.
 */
public final class TransportRegistrationTerminalBlock extends Block implements EntityBlock {

    public static final MapCodec<TransportRegistrationTerminalBlock> CODEC =
            simpleCodec(TransportRegistrationTerminalBlock::new);

    /**
     * Габариты selection/коллизии собраны из боксов элементов модели.
     * Базовая форма — для facing = NORTH, остальные стороны получаются
     * поворотом вокруг центра блока тем же правилом, что и y-rotation
     * в blockstate, чтобы визуал и хитбокс не расходились.
     */
    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public TransportRegistrationTerminalBlock(
            BlockBehaviour.Properties properties
    ) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        );
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        return defaultBlockState()
                .setValue(
                        BlockStateProperties.HORIZONTAL_FACING,
                        context.getHorizontalDirection().getOpposite()
                );
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPES.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new TransportRegistrationTerminalBlockEntity(pos, state);
    }

    /**
     * Привязывает терминал к текущему городу ставящего игрока.
     *
     * <p>Город берётся из CityManager через getMemberCity — то же
     * каноническое хранилище, что используют остальные городские
     * системы. Игрок вне города: блок ставится, в чат уходит
     * предупреждение о отсутствии функционала. Блок ставится
     * на стороне сервера, поэтому сообщение отправляется только
     * после того, как состояние BlockEntity зафиксировано.
     */
    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide
                || !(placer instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof TransportRegistrationTerminalBlockEntity terminal)) {
            return;
        }
        City city = CityManager.get(
                serverPlayer.serverLevel().getServer().overworld()
        ).getMemberCity(serverPlayer.getUUID());
        if (city == null) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.createcityexpansioncore.transport_terminal.no_city")
            );
            return;
        }
        terminal.setCityId(city.getId());
        serverPlayer.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.transport_terminal.linked",
                city.getName()
        ));
    }

    /**
     * Строит форму коллизии для каждой из четырёх сторон.
     *
     * <p>Боксы модели (facing = NORTH):
     * <ul>
     *   <li>полка клавиатуры: 1..15 x 0..2 x 0..3 (выступ спереди)</li>
     *   <li>корпус: 1..15 x 1..15 x 3..15</li>
     *   <li>латунная подставка: 4..12 x 0..1 x 5..13</li>
     * </ul>
     *
     * <p>Повороты повторяют правила blockstate y-rotation:
     * north->east при 90°, north->south при 180°, north->west при 270°.
     */
    private static Map<Direction, VoxelShape> buildShapes() {
        record Box(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ
        ) {
        }

        Box[] modelBoxes = new Box[]{
                new Box(1.0D, 0.0D, 0.0D, 15.0D, 2.0D, 3.0D),
                new Box(1.0D, 1.0D, 3.0D, 15.0D, 15.0D, 15.0D),
                new Box(4.0D, 0.0D, 5.0D, 12.0D, 1.0D, 13.0D)
        };

        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            List<VoxelShape> parts = new ArrayList<>(modelBoxes.length);
            for (Box box : modelBoxes) {
                double minX;
                double minZ;
                double maxX;
                double maxZ;
                switch (direction) {
                    case EAST -> {
                        minX = 16.0D - box.maxZ();
                        minZ = box.minX();
                        maxX = 16.0D - box.minZ();
                        maxZ = box.maxX();
                    }
                    case SOUTH -> {
                        minX = 16.0D - box.maxX();
                        minZ = 16.0D - box.maxZ();
                        maxX = 16.0D - box.minX();
                        maxZ = 16.0D - box.minZ();
                    }
                    case WEST -> {
                        minX = box.minZ();
                        minZ = 16.0D - box.maxX();
                        maxX = box.maxZ();
                        maxZ = 16.0D - box.minX();
                    }
                    default -> {
                        minX = box.minX();
                        minZ = box.minZ();
                        maxX = box.maxX();
                        maxZ = box.maxZ();
                    }
                }
                parts.add(Block.box(
                        Math.min(minX, maxX), box.minY(), Math.min(minZ, maxZ),
                        Math.max(minX, maxX), box.maxY(), Math.max(minZ, maxZ)
                ));
            }
            /*
             * Объединяем боксы последовательно через двухместный Shapes.or,
             * не полагаясь на varargs-перегрузку.
             */
            VoxelShape result = parts.get(0);
            for (int i = 1; i < parts.size(); i++) {
                result = Shapes.or(result, parts.get(i));
            }
            shapes.put(direction, result);
        }
        return shapes;
    }
}
