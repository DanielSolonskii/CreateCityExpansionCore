package com.example.examplemod.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Reversible, position-specific world-model suppression. No BlockState writes or BE changes.
 * Ownership is main-thread only; chunk meshing sees an immutable volatile snapshot.
 * Other assembled machines can register their block model names and publish member maps.
 */
public final class AssembledBlockVisibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModelProperty<Boolean> HIDDEN = new ModelProperty<>();
    private static final Set<String> MODEL_PATHS = new HashSet<>();
    private static final Map<BlockPos, Map<BlockPos, BlockState>> OWNERS = new HashMap<>();
    private static volatile Map<BlockPos, BlockState> hidden = Map.of();

    private AssembledBlockVisibility() {}

    public static void registerModels(String... paths) {
        for (String path : paths) {
            String base = path.startsWith("block/") ? path.substring("block/".length()) : path;
            // Blockstate keys use namespace:model#variant. Some additional-model loaders
            // use namespace:block/model#standalone; accepting both keeps this reusable.
            MODEL_PATHS.add(base);
            MODEL_PATHS.add("block/" + base);
        }
    }

    static boolean shouldWrap(String namespace, String path, String variant) {
        return namespace.equals("createcityexpansioncore")
                && MODEL_PATHS.contains(path)
                && !variant.equals("inventory") && !variant.equals("standalone");
    }

    public static boolean isActive(BlockPos owner) { return OWNERS.containsKey(owner); }

    public static void replace(BlockPos owner, Map<BlockPos, BlockState> members) {
        if (members.equals(OWNERS.get(owner))) return;
        Set<BlockPos> changed = new HashSet<>(members.keySet());
        Map<BlockPos, BlockState> previous = OWNERS.put(owner.immutable(), Map.copyOf(members));
        if (previous != null) changed.addAll(previous.keySet());
        publish(changed);
    }

    public static void remove(BlockPos owner) {
        Map<BlockPos, BlockState> previous = OWNERS.remove(owner);
        if (previous != null) publish(previous.keySet());
    }

    public static void clear() {
        Set<BlockPos> previous = hidden.keySet();
        OWNERS.clear();
        publish(previous);
    }

    /** Outgoing level sections are already being discarded; don't touch a disposed ViewArea. */
    public static void resetLevel() {
        OWNERS.clear();
        hidden = Map.of();
    }

    private static void publish(Set<BlockPos> changed) {
        Map<BlockPos, BlockState> snapshot = new HashMap<>();
        OWNERS.values().forEach(snapshot::putAll);
        hidden = Map.copyOf(snapshot);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Set<SectionPos> sections = new HashSet<>();
        changed.forEach(pos -> sections.add(SectionPos.of(pos)));
        // Also rebuild neighboring sections, so exposed boundary faces recover.
        sections.forEach(section -> minecraft.levelRenderer.setSectionDirtyWithNeighbors(
                section.x(), section.y(), section.z()));
    }

    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;
        for (var entry : event.getModels().entrySet()) {
            var id = entry.getKey();
            if (shouldWrap(id.id().getNamespace(), id.id().getPath(), id.variant())) {
                entry.setValue(new HiddenWorldModel(entry.getValue()));
                wrapped++;
            }
        }
        LOGGER.info("Installed assembled-visibility wrapper on {} baked block models", wrapped);
    }

    private static final class HiddenWorldModel extends BakedModelWrapper<BakedModel> {
        private HiddenWorldModel(BakedModel original) { super(original); }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return super.getModelData(level, pos, state, data).derive()
                    // Position + exact BlockState make this a world-only decision. Item models
                    // have no world position; preview worlds do not share the live hidden map.
                    .with(HIDDEN, state.equals(hidden.get(pos))).build();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return Boolean.TRUE.equals(data.get(HIDDEN)) ? ChunkRenderTypeSet.none()
                    : super.getRenderTypes(state, random, data);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                        ModelData data, RenderType renderType) {
            return Boolean.TRUE.equals(data.get(HIDDEN)) ? List.of()
                    : super.getQuads(state, side, random, data, renderType);
        }
    }
}
