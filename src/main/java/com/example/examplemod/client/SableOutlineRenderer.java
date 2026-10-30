package com.example.examplemod.client;

import com.example.examplemod.network.TransportOutlinePayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/** Uses Sable's interpolated pose, including rotation point and scale. */
public final class SableOutlineRenderer {
    public static void render(RenderLevelStageEvent event,TransportOutlinePayload snapshot){
        var mc=Minecraft.getInstance();var container=SubLevelContainer.getContainer(mc.level);
        if(container==null)return;
        var poses=event.getPoseStack();var camera=event.getCamera().getPosition();
        var source=mc.renderBuffers().bufferSource();var buffer=source.getBuffer(RenderType.debugQuads());
        for(var entry:snapshot.entries()){
            if(!(container.getSubLevel(entry.sublevel()) instanceof ClientSubLevel sub)||sub.isRemoved())continue;
            var pose=sub.renderPose(mc.getTimer().getGameTimeDeltaPartialTick(false));
            var pos=entry.pos();
            if(pose.transformPosition(Vec3.atCenterOf(pos)).distanceToSqr(mc.player.position())>64*64)continue;
            var plot=sub.getPlot();
            var holder=plot.getChunkHolder(plot.toLocal(new net.minecraft.world.level.ChunkPos(pos)));
            if(holder==null||holder.getChunk()==null)continue;
            var accessor=plot.getEmbeddedLevelAccessor();
            var localPos=pos.subtract(plot.getCenterBlock());
            var state=holder.getChunk().getBlockState(pos);
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(entry.blockId()))continue;
            var origin=pose.transformPosition(Vec3.atLowerCornerOf(pos));
            poses.pushPose();
            poses.translate(origin.x-camera.x,origin.y-camera.y,origin.z-camera.z);
            poses.mulPose(new Quaternionf(pose.orientation()));
            poses.scale((float)pose.scale().x(),(float)pose.scale().y(),(float)pose.scale().z());
            state.getShape(accessor,localPos).forAllEdges((x,y,z,X,Y,Z)->
                AccessOutlineRenderer.box(poses,buffer,new AABB(Math.min(x,X),Math.min(y,Y),Math.min(z,Z),
                        Math.max(x,X),Math.max(y,Y),Math.max(z,Z)).inflate(1.0/32.0)));
            poses.popPose();
        }
        source.endBatch(RenderType.debugQuads());
    }
}
