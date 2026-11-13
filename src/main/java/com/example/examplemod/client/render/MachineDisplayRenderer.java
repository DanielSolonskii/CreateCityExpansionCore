package com.example.examplemod.client.render;

import com.example.examplemod.machine.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Draws only the inset face of an actual placed block; never substitutes the structure's geometry. */
public final class MachineDisplayRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final ResourceLocation SYMBOLS = ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "textures/block/machine_symbols.png");
    public MachineDisplayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(T entity, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(entity instanceof MachineDisplaySource display)) return;
        var t = display.telemetry();
        var role = display.displayRole();
        if (role != MachineDisplaySource.Role.CONTROLLER && !t.assembled()) return;
        Direction facing = entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        pose.pushPose();
        pose.translate(.5,.5,.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (facing) { case EAST -> 90; case SOUTH -> 0; case WEST -> 270; default -> 180; }));
        pose.translate(0,0,.501);
        pose.scale(1/128F, -1/128F, 1/128F);
        if (role == MachineDisplaySource.Role.CONTROLLER) {
            if (t.product() != null && BuiltInRegistries.ITEM.containsKey(t.product())) {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(t.product()));
                var model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.getLevel(), null, 0);
                var sprite = model.getParticleIcon();
                quad(pose, buffers, InventoryMenu.BLOCK_ATLAS, -37, -24, 30, 30, 0xffffffff,
                        sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
            } else text(pose, buffers, "?", -24,-12,0xffb8c6ad);
            int icon = switch (t.status()) {
                case INCOMPLETE -> 0; case NO_POWER -> 2; case READY -> 3;
                case WARNING -> 1; case UNSAFE, NO_DEPOSIT -> 4;
            };
            icon(pose,buffers,icon,8,-24,32);
            text(pose,buffers,"T"+t.tier(),-40,23,0xff9fb397);
            String state = Component.translatable("gui.createcityexpansioncore.machine.state."+t.state().toLowerCase(java.util.Locale.ROOT)).getString();
            text(pose,buffers,Minecraft.getInstance().font.plainSubstrByWidth(state,68),-21,23,0xff9fb397);
        } else if (role == MachineDisplaySource.Role.ENERGY) {
            icon(pose,buffers,t.status()==MachineTelemetry.Status.UNSAFE ? 4 : 1,-40,-39,24);
            text(pose,buffers,"T"+t.tier(),22,-32,0xffb8c6ad);
            int color = t.status()==MachineTelemetry.Status.READY ? 0xff79b95a
                    : t.status()==MachineTelemetry.Status.UNSAFE ? 0xffd14f3b : 0xffd1a24f;
            double voltageFraction = t.power().minVoltage() <= 0 ? 0
                    : t.power().voltage() / t.power().minVoltage();
            bar(pose,buffers,-40,0,voltageFraction);
            text(pose,buffers,Math.round(voltageFraction*100)+"%",-12,21,color);
        } else {
            text(pose,buffers,Component.translatable("gui.createcityexpansioncore.machine.cycle_short").getString(),-40,-39,0xffb8c6ad);
            bar(pose,buffers,-40,-25,t.production().progress());
            text(pose,buffers,Component.translatable("gui.createcityexpansioncore.machine.output_short").getString(),-40,0,0xffb8c6ad);
            bar(pose,buffers,-40,14,t.production().fullness());
        }
        pose.popPose();
    }
    private static void text(PoseStack pose, MultiBufferSource buffers, String text, float x, float y, int color) {
        pose.pushPose(); pose.translate(0,0,.02);
        Minecraft.getInstance().font.drawInBatch(text,x,y,color,false,pose.last().pose(),buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
    private static void bar(PoseStack pose, MultiBufferSource buffers, float x, float y, double fraction) {
        rect(pose,buffers,x,y,80,10,0xff171b18,80,0,1,1);
        pose.pushPose(); pose.translate(0,0,.01);
        int filled = (int)Math.round(20 * Math.clamp(fraction,0,1));
        for (int segment = 0; segment < 20; segment++) {
            int color = segment < filled ? 0xff14d524 : 0xffef2820;
            rect(pose,buffers,x+1+segment*4,y+1,2,8,color,80,0,1,1);
        }
        pose.popPose();
    }
    private static void icon(PoseStack pose, MultiBufferSource buffers, int index, float x, float y, float size) {
        rect(pose,buffers,x,y,size,size,0xffffffff,index*16,0,16,16);
    }
    private static void rect(PoseStack pose, MultiBufferSource buffers, float x, float y, float w, float h,
                             int color, float u, float v, float uw, float vh) {
        quad(pose, buffers, SYMBOLS, x, y, w, h, color, u / 128, v / 16, (u + uw) / 128, (v + vh) / 16);
    }

    private static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                             float x, float y, float w, float h, int color, float u0, float v0, float u1, float v1) {
        var consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        var p = pose.last();
        consumer.addVertex(p,x,y,0).setColor(color).setUv(u0,v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p,0,0,1);
        consumer.addVertex(p,x,y+h,0).setColor(color).setUv(u0,v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p,0,0,1);
        consumer.addVertex(p,x+w,y+h,0).setColor(color).setUv(u1,v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p,0,0,1);
        consumer.addVertex(p,x+w,y,0).setColor(color).setUv(u1,v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p,0,0,1);
    }
}
