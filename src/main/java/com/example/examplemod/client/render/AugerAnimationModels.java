package com.example.examplemod.client.render;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import java.util.List;

/** The placed auger's geometry is split into stationary hardware and rotating steel only. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AugerAnimationModels {
    public static final ModelResourceLocation ROTOR = model("rotor");
    public static final ModelResourceLocation HOUSING = model("housing");
    private static final ModelProperty<Boolean> ANIMATED = new ModelProperty<>();
    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "block/industrial_auger/" + part));
    }
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
        event.register(ROTOR); event.register(HOUSING);
    }
    @SubscribeEvent public static void wrap(ModelEvent.ModifyBakingResult event) {
        for (var entry : event.getModels().entrySet()) {
            var id = entry.getKey();
            if (id.id().getNamespace().equals(CreateCityExpansionCore.MODID)
                    && id.id().getPath().equals("drill_unit") && id.variant().contains("part="))
                entry.setValue(new AnimatedWorldModel(entry.getValue()));
        }
    }
    private static final class AnimatedWorldModel extends BakedModelWrapper<BakedModel> {
        private AnimatedWorldModel(BakedModel original) { super(original); }
        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return super.getModelData(level, pos, state, data).derive()
                    .with(ANIMATED, MineWorkEffects.hidden(level, pos, state)).build();
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return Boolean.TRUE.equals(data.get(ANIMATED)) ? ChunkRenderTypeSet.none() : super.getRenderTypes(state, random, data);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
            return Boolean.TRUE.equals(data.get(ANIMATED)) ? List.of() : super.getQuads(state, side, random, data, type);
        }
    }
}
