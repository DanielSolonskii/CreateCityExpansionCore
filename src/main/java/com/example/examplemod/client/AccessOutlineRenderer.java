package com.example.examplemod.client;

import com.example.examplemod.item.AccessControlRemoteItem;
import com.example.examplemod.network.AccessOutlineNetwork;
import com.example.examplemod.network.AccessOutlinePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = "createcityexpansioncore", value = Dist.CLIENT)
public final class AccessOutlineRenderer {
    // Ribs are centered on the block edges, with one texture pixel of total thickness.
    private static final double PADDING = 0.0;
    private static final double HALF_WIDTH = 1.0 / 32.0;
    private static AccessOutlinePayload snapshot;
    private static Object world;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        AccessOutlineNetwork.client = AccessOutlineRenderer::accept;
        var level = Minecraft.getInstance().level;
        if (world != level) { world = level; snapshot = null; }
    }
    private static void accept(AccessOutlinePayload payload) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().location().equals(payload.dimension())) {
            world = level;
            snapshot = payload;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = null; world = null;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level != world || snapshot == null || mc.options.hideGui) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof AccessControlRemoteItem)
                && !(mc.player.getOffhandItem().getItem() instanceof AccessControlRemoteItem)) return;
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var source = mc.renderBuffers().bufferSource();
        var buffer = source.getBuffer(RenderType.debugQuads());
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (var entry : snapshot.entries()) {
            var pos = entry.pos();
            if (pos.distToCenterSqr(mc.player.position()) > 64 * 64 || !mc.level.hasChunkAt(pos)) continue;
            var state = mc.level.getBlockState(pos);
            if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(entry.blockId())) continue;
            var shape = state.getShape(mc.level, pos, CollisionContext.of(mc.player));
            // Inflate each component and union them before extracting edges, preserving partial blocks.
            var expanded = Shapes.empty();
            for (var box : shape.toAabbs()) expanded = Shapes.or(expanded, Shapes.create(box.inflate(PADDING)));
            expanded.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                var rib = new AABB(Math.min(x1,x2), Math.min(y1,y2), Math.min(z1,z2),
                        Math.max(x1,x2), Math.max(y1,y2), Math.max(z1,z2))
                        .inflate(HALF_WIDTH).move(pos);
                box(poses, buffer, rib);
            });
        }
        poses.popPose();
        source.endBatch(RenderType.debugQuads());
    }
    /** Solid depth-tested ribs; face shading gives the frame visible volume. */
    public static void box(PoseStack poses, VertexConsumer buffer, AABB b) {
        double x=b.minX,y=b.minY,z=b.minZ,X=b.maxX,Y=b.maxY,Z=b.maxZ;
        face(poses,buffer,1f, x,Y,z, x,Y,Z, X,Y,Z, X,Y,z);
        face(poses,buffer,.55f, x,y,z, X,y,z, X,y,Z, x,y,Z);
        face(poses,buffer,.8f, x,y,z, x,Y,z, X,Y,z, X,y,z);
        face(poses,buffer,.8f, x,y,Z, X,y,Z, X,Y,Z, x,Y,Z);
        face(poses,buffer,.65f, x,y,z, x,y,Z, x,Y,Z, x,Y,z);
        face(poses,buffer,.65f, X,y,z, X,Y,z, X,Y,Z, X,y,Z);
    }
    private static void face(PoseStack poses, VertexConsumer buffer, float shade, double... vertices) {
        for (int i=0;i<vertices.length;i+=3) {
            buffer.addVertex(poses.last().pose(), (float)vertices[i], (float)vertices[i+1], (float)vertices[i+2])
                    .setColor(.035f*shade, .36f*shade, .09f*shade, 1f);
        }
    }
}
