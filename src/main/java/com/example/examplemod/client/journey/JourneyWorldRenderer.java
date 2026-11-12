package com.example.examplemod.client.journey;

import com.example.examplemod.journey.JourneyModel.Marker;
import com.example.examplemod.journey.JourneyModel.Point;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Depth-tested enamel signs with a brass edge and the atlas silhouette. */
@EventBusSubscriber(modid = "createcityexpansioncore", value = Dist.CLIENT)
public final class JourneyWorldRenderer {
    private JourneyWorldRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (mc.player.hurtTime > 0 || mc.player.getHealth() <= 6 || mc.player.isOnFire()) return;

        Marker target = JourneyClient.displayTarget();
        Marker done = JourneyClient.displayCompletion();
        long now = Util.getMillis();
        boolean completing = done != null && now - JourneyClient.displayCompletionAt() < 950 && visible(mc, done);
        List<Marker> shown = new ArrayList<>(markersForView(mc, target));
        Turn turn = nextTurn(mc, target);
        var focused = JourneyWaypointHint.capture(mc, event.getCamera(), shown, target, turn);
        if (completing && shown.stream().noneMatch(marker -> marker.id.equals(done.id))) shown.add(done);

        var poses = event.getPoseStack();
        var source = mc.renderBuffers().bufferSource();
        boolean reduced = JourneyClient.reduced();
        for (Marker marker : shown) {
            boolean active = marker == target;
            boolean finished = !active && completing && done.id.equals(marker.id);
            double distance = JourneyClient.distance(marker.point);
            float alpha = (float) Math.clamp((marker.range - distance) / 10, 0, 1);
            if (finished) alpha *= 1 - (now - JourneyClient.displayCompletionAt()) / 950f;
            double reveal = active ? JourneyAtlasStyle.reveal(now - JourneyClient.displayTargetAt(), reduced) : 1;
            double radius = marker.size * (active ? .027 : .023) * (.84 + .16 * reveal);

            Vec3 position = signPosition(marker.point);
            Vec3 fromCamera = position.subtract(event.getCamera().getPosition());
            poses.pushPose();
            poses.translate(fromCamera.x, fromCamera.y - (1 - reveal) * .12, fromCamera.z);
            poses.mulPose(event.getCamera().rotation());
            var face = source.getBuffer(RenderType.debugQuads());
            int[][] shape = JourneyAtlasStyle.silhouette(marker.kind);
            badge(poses, face, shape, radius + .022, -.015, 0xff131719, alpha);
            badge(poses, face, shape, radius, 0, active ? JourneyAtlasStyle.BRASS : JourneyAtlasStyle.color(marker), alpha);
            badge(poses, face, shape, radius - .030, .008, active ? 0xff393229 : 0xff252d31, alpha);
            source.endBatch(RenderType.debugQuads());

            var strokes = source.getBuffer(RenderType.lines());
            double scale = radius / 9;
            for (var segment : JourneyAtlasStyle.glyph(finished ? 7 : marker.icon)) {
                line(poses, strokes, segment[0] * scale, -segment[1] * scale, .018,
                        segment[2] * scale, -segment[3] * scale, .018,
                        finished ? 0xffa8c4a9 : JourneyAtlasStyle.TEXT, alpha);
            }
            source.endBatch(RenderType.lines());
            boolean aimed = mc.player.getLookAngle().dot(fromCamera.normalize()) > .985;
            boolean namedInCard = focused != null && !focused.turn() && focused.marker() == marker;
            if (!namedInCard && (active && distance < 22 || aimed && distance < 36)) {
                poses.scale(-.022f, -.022f, .022f);
                var matrix = poses.last().pose();
                String label = mc.font.plainSubstrByWidth(marker.title, 172);
                int color = (int) (alpha * 255) << 24 | JourneyAtlasStyle.TEXT & 0xffffff;
                mc.font.drawInBatch(label, -mc.font.width(label) / 2f, (float) (radius / .022 + 4), color,
                        false, matrix, source, Font.DisplayMode.NORMAL, 0xb0192125, LightTexture.FULL_BRIGHT);
            }
            poses.popPose();
        }
        if (turn != null) drawTurn(event, mc, turn);
    }

    static List<Marker> markersForView(Minecraft mc, Marker target) {
        if (JourneyPlaytest.running() || !JourneyClient.chain.isEmpty() && !JourneyClient.paused) {
            return target != null && visible(mc, target) ? List.of(target) : List.of();
        }
        return JourneyClient.markers.values().stream().filter(marker -> visible(mc, marker))
                .sorted(Comparator.comparingDouble(marker -> marker == target ? -1 : JourneyClient.distance(marker.point)))
                .limit(8).toList();
    }

    static Vec3 signPosition(Point point) {
        return new Vec3(point.x() + .5, point.y() + 1.65, point.z() + .5);
    }

    private static boolean visible(Minecraft mc, Marker marker) {
        Point p = marker.point;
        return marker.inWorld && JourneyClient.distance(p) < marker.range
                && mc.level.hasChunkAt(new BlockPos(p.x(), p.y(), p.z()));
    }

    static Turn nextTurn(Minecraft mc, Marker marker) {
        if (marker == null || !marker.inWorld) return null;
        List<Point> path = JourneyClient.displayPath(marker);
        if (path.size() < 2) return null;
        Point point = path.get(0);
        Point next = path.get(1);
        double dx = next.x() - point.x();
        double dz = next.z() - point.z();
        double length = Math.hypot(dx, dz);
        if (length < 1 || JourneyClient.distance(point) > 28
                || !mc.level.hasChunkAt(new BlockPos(point.x(), point.y(), point.z()))) return null;
        return new Turn(point, dx / length, dz / length);
    }

    record Turn(Point point, double dx, double dz) {
        Vec3 position() { return new Vec3(point.x() + .5, point.y() + .18, point.z() + .5); }
    }

    private static void drawTurn(RenderLevelStageEvent event, Minecraft mc, Turn turn) {
        var camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        poses.pushPose();
        Vec3 position = turn.position().subtract(camera);
        poses.translate(position.x, position.y, position.z);
        var source = mc.renderBuffers().bufferSource();
        var strokes = source.getBuffer(RenderType.lines());
        double dx = turn.dx(), dz = turn.dz();
        line(poses, strokes, -dx * .36 - dz * .2, 0, -dz * .36 + dx * .2,
                0, 0, 0, JourneyAtlasStyle.BRASS, .8f);
        line(poses, strokes, 0, 0, 0, -dx * .36 + dz * .2, 0, -dz * .36 - dx * .2,
                JourneyAtlasStyle.BRASS, .8f);
        source.endBatch(RenderType.lines());
        poses.popPose();
    }

    private static void badge(PoseStack poses, VertexConsumer out, int[][] shape, double radius, double z,
                              int color, float alpha) {
        float scale = (float) (radius / 10);
        for (int i = 0; i < shape.length; i++) {
            int[] a = shape[i];
            int[] b = shape[(i + 1) % shape.length];
            vertex(poses, out, 0, 0, z, color, alpha);
            vertex(poses, out, a[0] * scale, -a[1] * scale, z, color, alpha);
            vertex(poses, out, b[0] * scale, -b[1] * scale, z, color, alpha);
            vertex(poses, out, 0, 0, z, color, alpha);
        }
    }

    private static void vertex(PoseStack poses, VertexConsumer out, double x, double y, double z, int color, float alpha) {
        out.addVertex(poses.last(), (float) x, (float) y, (float) z).setColor(
                (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, alpha);
    }

    private static void line(PoseStack poses, VertexConsumer out, double ax, double ay, double az,
                             double bx, double by, double bz, int color, float alpha) {
        Vec3 normal = new Vec3(bx - ax, by - ay, bz - az).normalize();
        if (normal.lengthSqr() == 0) return;
        var pose = poses.last();
        float r = (color >> 16 & 255) / 255f;
        float g = (color >> 8 & 255) / 255f;
        float b = (color & 255) / 255f;
        out.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        out.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
