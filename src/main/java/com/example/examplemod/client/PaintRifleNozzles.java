package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.machine.PanelPainterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;
import java.util.WeakHashMap;

@EventBusSubscriber(modid=CreateCityExpansionCore.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class PaintRifleNozzles {
    private record Sample(Vec3 position,long tick) {}
    private static final WeakHashMap<AbstractClientPlayer,Sample[]> SAMPLES=new WeakHashMap<>();
    public static Vec3 get(AbstractClientPlayer player,HumanoidArm arm) {
        var samples=SAMPLES.get(player); var s=samples==null ? null : samples[arm==HumanoidArm.RIGHT ? 0 : 1];
        return s!=null && player.level().getGameTime()-s.tick<=2 ? s.position : null;
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        for(var skin:event.getSkins()) {
            PlayerRenderer renderer=event.getSkin(skin);
            if(renderer!=null) renderer.addLayer(new NozzleLayer(renderer));
        }
    }
    private static final class NozzleLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
        NozzleLayer(PlayerRenderer renderer) { super(renderer); }
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,
                float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch) {
            var mc=Minecraft.getInstance();
            for(var hand:InteractionHand.values()) {
                var stack=player.getItemInHand(hand);
                if(!(stack.getItem() instanceof PanelPainterItem)) continue;
                var arm=hand==InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
                boolean left=arm==HumanoidArm.LEFT;
                var aim=PaintRiflePose.get(player,arm);
                pose.pushPose();
                if (aim!=null) {
                    pose.mulPose(aim.item());
                } else {
                getParentModel().translateToHand(arm,pose);
                pose.mulPose(Axis.XP.rotationDegrees(-90)); pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.translate((left ? -1 : 1)/16f,.125,-.625);
                var model=mc.getItemRenderer().getModel(stack,player.level(),player,0);
                model.getTransforms().getTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(left,pose);
                pose.translate(-.5,-.5,-.5);
                }
                var point=pose.last().pose().transformPosition(new Vector3f(8f/16,9.25f/16,-7.43f/16));
                var world=mc.gameRenderer.getMainCamera().getPosition().add(point.x,point.y,point.z);
                SAMPLES.computeIfAbsent(player,p->new Sample[2])[left ? 1 : 0]=new Sample(world,player.level().getGameTime());
                pose.popPose();
            }
        }
    }
}
