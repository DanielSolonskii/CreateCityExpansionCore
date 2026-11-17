package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.network.PaintSprayFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3f;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class PaintSprayEffects {
    @SubscribeEvent
    public static void spray(PaintSprayFeedback.Received event) {
        var mc = Minecraft.getInstance(); var level = mc.level; var data = event.spray;
        if (level == null || event.level != level || mc.isPaused()
                || !(level.getEntity(data.playerId()) instanceof Player player)) return;
        Vec3 target = data.target();
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)
                || player.getEyePosition().distanceToSqr(target) > 32*32
                || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(target) > 48*48) return;
        var arm = data.hand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        double side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 forward = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = forward.cross(right).normalize();
        boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
        // Rest-pose nozzle offsets match the smaller first-person item transform.
        Vec3 origin = player.getEyePosition().add(forward.scale(firstPerson ? 1.63 : .85))
                .add(right.scale(side * (firstPerson ? .4775 : .30)))
                .subtract(up.scale(firstPerson ? .24 : .40));
        Vec3 renderedNozzle = firstPerson ? PanelPainterHands.firstPersonNozzle(arm) : null;
        if (renderedNozzle != null) {
            origin = mc.gameRenderer.getMainCamera().getPosition().add(renderedNozzle);
        }
        if (!firstPerson && player instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer) {
            var nozzle = PaintRifleNozzles.get(clientPlayer, arm);
            if (nozzle != null) origin = nozzle;
        }
        int rgb = DyeColor.byId(data.colorId()).getTextureDiffuseColor();
        var options = new DustParticleOptions(new Vector3f((rgb>>16&255)/255f, (rgb>>8&255)/255f, (rgb&255)/255f), .65f);
        for (int i = 0; i < 36; i++) {
            double radius=Math.sqrt(level.random.nextDouble())*.28;
            double angle=level.random.nextDouble()*Math.PI*2;
            Vec3 jitter = right.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));
            Vec3 start = origin.add(jitter.scale(.08));
            Vec3 velocity = target.add(jitter).subtract(start).scale(1.0/8);
            var particle = mc.particleEngine.createParticle(options, start.x,start.y,start.z,0,0,0);
            if (particle != null) {
                particle.setParticleSpeed(velocity.x,velocity.y,velocity.z);
                particle.setLifetime(9);
            }
        }
    }
}
