package com.example.examplemod.client.render;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.machine.MachinePanels;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.List;
import java.util.Set;

/** Keeps the trim along the outline of a group of coplanar panels. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ConnectedPanelModel extends BakedModelWrapper<BakedModel> {
    private static final Set<String> MODELS = Set.of(
            "mine_frame", "technical_block", "mine_controller", "mine_power_port", "mine_gateway", "technical_screen");
    private static final ModelProperty<Long> NEIGHBORS = new ModelProperty<>();
    private static final ModelProperty<BlockPos> PANEL_ORIGIN = new ModelProperty<>();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int TRIM_INDEX = 1;
    private static final int PANEL_EDGE_INDEX = 2;

    private ConnectedPanelModel(BakedModel original) { super(original); }

    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        for (var entry : event.getModels().entrySet()) {
            var id = entry.getKey();
            if (id.id().getNamespace().equals(CreateCityExpansionCore.MODID)
                    && MODELS.contains(id.id().getPath()) && id.variant().contains("color=")) {
                entry.setValue(new ConnectedPanelModel(entry.getValue()));
            }
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        long mask = 0;
        var neighbor = new BlockPos.MutableBlockPos();
        for (var direction : DIRECTIONS) {
            neighbor.setWithOffset(pos, direction);
            if (MachinePanels.isPanel(level.getBlockState(neighbor))) mask |= bit(direction);
        }
        for (var first : DIRECTIONS) {
            if ((mask & bit(first)) == 0) continue;
            for (var second : DIRECTIONS) {
                if (first.ordinal() >= second.ordinal() || first.getAxis() == second.getAxis()
                        || (mask & bit(second)) == 0) continue;
                neighbor.setWithOffset(pos, first).move(second);
                if (MachinePanels.isPanel(level.getBlockState(neighbor))) {
                    mask |= diagonalBit(first.ordinal(), second.ordinal());
                }
            }
        }
        return super.getModelData(level, pos, state, data).derive().with(NEIGHBORS, mask)
                .with(PANEL_ORIGIN, pos.immutable()).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return super.getQuads(state, side, random).stream()
                .filter(quad -> quad.getTintIndex() != PANEL_EDGE_INDEX).toList();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        var quads = super.getQuads(state, side, random, data, renderType);
        Long mask = data.get(NEIGHBORS);
        long neighbors = mask == null ? 0 : mask;
        return quads.stream().filter(quad -> {
            int tint = quad.getTintIndex();
            if (tint != TRIM_INDEX && tint != PANEL_EDGE_INDEX) return true;
            return (tint == PANEL_EDGE_INDEX) == isConnected(trimEdges(quad), neighbors);
        }).map(quad -> mapPanel(quad, data.get(PANEL_ORIGIN))).toList();
    }

    private static BakedQuad mapPanel(BakedQuad quad, BlockPos origin) {
        var sprite = quad.getSprite();
        var id = sprite.contents().name();
        if (origin == null || !id.getNamespace().equals(CreateCityExpansionCore.MODID)
                || !id.getPath().startsWith("block/machine_panel/")) return quad;
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            int offset = i * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float u = PanelTextureCoordinates.u(quad.getDirection(), origin, x, y, z);
            float v = PanelTextureCoordinates.v(quad.getDirection(), origin, x, y, z);
            vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(u));
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(v));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite,
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static boolean isConnected(int edges, long neighbors) {
        if (edges == 0 || (edges & neighbors) != edges) return false;
        if (Integer.bitCount(edges) == 1) return true;
        // Keep the inner corner if both side neighbors exist but the diagonal is empty.
        int first = Integer.numberOfTrailingZeros(edges);
        int second = Integer.numberOfTrailingZeros(edges & (edges - 1));
        return (neighbors & diagonalBit(first, second)) != 0;
    }

    private static int bit(Direction direction) { return 1 << direction.ordinal(); }

    private static long diagonalBit(int first, int second) {
        return 1L << (DIRECTIONS.length + first * DIRECTIONS.length + second);
    }

    private static int trimEdges(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        int edges = 0;
        for (var direction : DIRECTIONS) {
            if (direction.getAxis() == quad.getDirection().getAxis()) continue;
            int axis = direction.getAxis().ordinal();
            float center = 0;
            for (int i = 0; i < 4; i++) center += Float.intBitsToFloat(vertices[i * stride + axis]);
            if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? center < .25f : center > 3.75f) {
                edges |= bit(direction);
            }
        }
        return edges;
    }
}
