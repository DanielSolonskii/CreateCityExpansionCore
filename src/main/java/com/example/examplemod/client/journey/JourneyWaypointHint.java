package com.example.examplemod.client.journey;

import com.example.examplemod.client.map.MinimapClient;
import com.example.examplemod.client.map.MinimapLayout;
import com.example.examplemod.journey.JourneyModel.Marker;
import com.example.examplemod.journey.JourneyModel.Point;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.example.examplemod.client.journey.JourneyAtlasStyle.*;

/** Context card for a visible world sign, using the same candidates as the world renderer. */
final class JourneyWaypointHint {
    private static final double LOOK_RANGE = 24;
    private static final double NEAR_RANGE = 6;
    private static final double LOOK_ALIGNMENT = .994;
    private static WeakReference<ClientLevel> level = new WeakReference<>(null);
    private static Focus focus;
    private static long lastFrame;
    private static long appearedAt;

    private JourneyWaypointHint() {}

    record Focus(Marker marker, Point point, boolean turn, double distance) {
        boolean isGoal(Marker active) { return !turn && marker == active; }
    }

    private record Candidate(Focus focus, Vec3 anchor, double score, boolean hit) {}

    static Focus capture(Minecraft client, Camera camera, List<Marker> markers,
                         Marker target, JourneyWorldRenderer.Turn turn) {
        Vec3 eye = camera.getPosition();
        var cameraForward = camera.getLookVector();
        Vec3 forward = new Vec3(cameraForward.x(), cameraForward.y(), cameraForward.z());
        var candidates = new ArrayList<Candidate>();
        for (Marker marker : markers) {
            addCandidate(client, candidates, eye, forward, marker, marker.point,
                    JourneyWorldRenderer.signPosition(marker.point), false, marker == target);
        }
        if (turn != null) {
            addCandidate(client, candidates, eye, forward, target, turn.point(), turn.position(), true, true);
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        Focus next = null;
        for (Candidate candidate : candidates) {
            if (candidate.hit() || unobstructed(client, eye, candidate)) {
                next = candidate.focus();
                break;
            }
        }
        long now = Util.getMillis();
        if (next != null && (level.get() != client.level || focus == null || !samePoint(focus, next) || now - lastFrame > 250)) {
            appearedAt = now;
        }
        if (level.get() != client.level) level = new WeakReference<>(client.level);
        focus = next;
        lastFrame = now;
        return next;
    }

    static Focus current(Minecraft client, long now) {
        return level.get() == client.level && now - lastFrame <= 250 ? focus : null;
    }

    private static boolean samePoint(Focus previous, Focus next) {
        return previous.turn() == next.turn() && previous.marker().id.equals(next.marker().id)
                && previous.point().equals(next.point());
    }

    private static void addCandidate(Minecraft client, List<Candidate> candidates, Vec3 eye, Vec3 forward,
                                     Marker marker, Point point, Vec3 anchor, boolean turn, boolean current) {
        double distance = JourneyClient.distance(point);
        if (distance > LOOK_RANGE) return;
        Vec3 offset = anchor.subtract(eye);
        double aim = forward.dot(offset.normalize());
        boolean hit = !turn && client.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && atPoint(block.getBlockPos(), point);
        if (!hit && aim < LOOK_ALIGNMENT && distance > NEAR_RANGE) return;

        // Direct aim wins over proximity. A turn at the same spot as its goal is the next useful cue.
        double score = hit ? 1000 : aim >= LOOK_ALIGNMENT ? 100 + aim * 8 : 0;
        score += (current ? .25 : 0) + (turn ? .10 : 0) - distance * .10;
        candidates.add(new Candidate(new Focus(marker, point, turn, distance), anchor, score, hit));
    }

    private static boolean unobstructed(Minecraft client, Vec3 eye, Candidate candidate) {
        var ray = client.level.clip(new ClipContext(eye, candidate.anchor(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, client.player));
        return ray.getType() == HitResult.Type.MISS || atPoint(ray.getBlockPos(), candidate.focus().point());
    }

    private static boolean atPoint(BlockPos pos, Point point) {
        return pos.getX() == point.x() && pos.getY() == point.y() && pos.getZ() == point.z();
    }

    static void draw(GuiGraphics graphics, Minecraft client, Focus focused, boolean active, Component title,
                     Component action, Component footer, long now) {
        int center = graphics.guiWidth() / 2;
        int leftSpace = center - 24;
        int width = Math.min(202, leftSpace >= 126 ? leftSpace : graphics.guiWidth() - 16);
        int x = leftSpace >= 126 ? center - 16 - width : (graphics.guiWidth() - width) / 2;
        int textWidth = width - 20;
        var font = client.font;
        Component description = Component.literal(focused.turn() ? "" : focused.marker().description);
        List<FormattedCharSequence> descriptionLines = description.getString().isBlank() ? List.of() : font.split(description, textWidth);
        List<FormattedCharSequence> actionLines = action.getString().isBlank() ? List.of() : font.split(action, textWidth);
        int descriptionCount = Math.min(2, descriptionLines.size());
        int actionCount = Math.min(2, actionLines.size());
        int footerHeight = footer.getString().isBlank() ? 0 : 12;
        int height = 39 + descriptionCount * 11 + actionCount * 11 + footerHeight;
        int lift = (int) (3 * (1 - reveal(now - appearedAt, JourneyClient.reduced())));
        int y = Math.max(8, graphics.guiHeight() / 2 - 10 - height + lift);
        var minimap = MinimapClient.settings();
        if (minimap.enabled && minimap.corner == 1 && !client.getDebugOverlay().showDebugScreen()) {
            var view = MinimapLayout.view(graphics.guiWidth(), graphics.guiHeight(), minimap.size, minimap.corner);
            int mapRight = view.frameX() + view.frameWidth();
            if (view.size() >= 64 && x < mapRight && y < view.frameY() + view.frameHeight()
                    && mapRight + 6 + width <= graphics.guiWidth() - 8) x = mapRight + 6;
        }

        panel(graphics, x, y, width, height);
        graphics.fill(x, y + 7, x + 2, y + height - 7, BRASS);
        if (focused.turn()) {
            line(graphics, x + 11, y + 19, x + 16, y + 14, BRASS);
            line(graphics, x + 16, y + 14, x + 21, y + 19, BRASS);
        } else {
            for (var stroke : glyph(focused.marker().icon)) {
                line(graphics, x + 16 + stroke[0], y + 16 + stroke[1],
                        x + 16 + stroke[2], y + 16 + stroke[3], BRASS);
            }
        }
        Component caption = JourneyClient.tr(active ? "waypoint_goal" : focused.turn() ? "waypoint_turn" : "waypoint_place",
                (int) Math.ceil(focused.distance()));
        graphics.drawString(font, font.plainSubstrByWidth(caption.getString(), width - 35), x + 27, y + 8, BRASS, false);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width - 35), x + 27, y + 21, TEXT, false);
        int row = y + 34;
        for (int i = 0; i < descriptionCount; i++, row += 11) {
            graphics.drawString(font, descriptionLines.get(i), x + 10, row, MUTED, false);
        }
        for (int i = 0; i < actionCount; i++, row += 11) {
            graphics.drawString(font, actionLines.get(i), x + 10, row, TEXT, false);
        }
        if (footerHeight > 0) {
            graphics.drawString(font, font.plainSubstrByWidth(footer.getString(), textWidth), x + 10, row, MUTED, false);
        }
    }
}
