package com.example.examplemod.electrical.bridge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.drmangotea.tfmg.base.TFMGUtils;

final class TfmgTerminalDisplay {
    static void render(DcBridgeEntity entity, PoseStack pose, MultiBufferSource buffers) {
        if (!(entity instanceof TfmgBridgePort port) || !port.isTfmgSection()) return;
        var endpoint=port.terminal();var origin=endpoint.getCablePosition();var pos=port.getBlockPos();
        pose.pushPose();pose.translate(origin.x()-pos.getX(),origin.y()-pos.getY(),origin.z()-pos.getZ());
        for(var cable:endpoint.connections) if(cable.visible) {
            int color=cable.type.getColor();
            TFMGUtils.renderWire(port.getLevel(),pose,buffers,cable.pos2,cable.pos1,cable.getLength()/4500f,
                    (color>>16)&255,(color>>8)&255,color&255);
        }
        pose.popPose();
    }
}
