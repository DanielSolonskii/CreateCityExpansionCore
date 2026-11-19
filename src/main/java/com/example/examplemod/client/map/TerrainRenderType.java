package com.example.examplemod.client.map;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** Managed GUI state for one terrain page. Only this pass interprets vertex colour as sampling flags. */
@EventBusSubscriber(modid="createcityexpansioncore", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
final class TerrainRenderType extends RenderType {
    private static ShaderInstance shader;

    private TerrainRenderType() {
        super("ccec_terrain", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    @SubscribeEvent
    static void register(RegisterShadersEvent event) throws IOException {
        var id = ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "map_terrain");
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION_TEX_COLOR),
                instance -> shader = instance);
    }

    static RenderType page(ResourceLocation texture) {
        return create("ccec_terrain", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                65536, false, false, CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(texture,false,false))
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .createCompositeState(false));
    }
}
