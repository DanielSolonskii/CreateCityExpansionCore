package com.example.examplemod.client.journey;

import com.example.examplemod.journey.JourneyModel.Marker;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import static com.example.examplemod.client.journey.JourneyAtlasStyle.*;

/** The same current objective presentation is used in normal play and in a draft walkthrough. */
public final class JourneyHud {
    private JourneyHud() {}

    public static void render(RenderGuiEvent.Post event, String completed, long completedAt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || mc.player.hurtTime > 0
                || mc.player.getHealth() <= 6 || mc.player.isOnFire()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        long now = Util.getMillis();
        boolean testing = JourneyPlaytest.running();
        if (!testing && !completed.isEmpty() && now - completedAt < 1500) {
            toast(graphics, mc, Component.literal(completed), now - completedAt);
        }
        if (testing && (JourneyPlaytest.waiting() || JourneyPlaytest.completed())) {
            Component title = JourneyClient.tr(JourneyPlaytest.waiting() ? "trial_waiting" : "trial_complete");
            drawCard(graphics, mc, null, title, JourneyClient.tr("trial_exit"), JourneyClient.tr("trial_label"), now);
            return;
        }

        Marker marker = JourneyClient.displayTarget();
        var focused = JourneyWaypointHint.current(mc, now);
        boolean goalInFocus = focused != null && focused.isGoal(marker);
        if (focused != null && !goalInFocus) {
            Component title = focused.turn() ? JourneyClient.tr("waypoint_turn_title") : Component.literal(focused.marker().title);
            Component hint = focused.turn() ? JourneyClient.tr("waypoint_follow") : Component.empty();
            JourneyWaypointHint.draw(graphics, mc, focused, false, title, hint, Component.empty(), now);
        }
        if (testing) {
            var step = JourneyPlaytest.step();
            if (step == null || marker == null) return;
            var last = JourneyPlaytest.completionMarker();
            if (last != null && now - JourneyPlaytest.completionAt() < 900) {
                toast(graphics, mc, Component.literal(last.title), now - JourneyPlaytest.completionAt());
            }
            var footer = JourneyClient.tr("trial_step", JourneyPlaytest.stepNumber(), JourneyPlaytest.totalSteps(),
                    JourneyClient.JOURNAL.getTranslatedKeyMessage());
            Component hint = action(mc, marker, step.type().name(), step.item(), step.count(), true, goalInFocus);
            if (goalInFocus) {
                JourneyWaypointHint.draw(graphics, mc, focused, true, Component.literal(step.title()), hint, footer, now);
            } else {
                drawCard(graphics, mc, marker, Component.literal(step.title()), hint, footer, now);
            }
            return;
        }

        if (JourneyClient.paused) return;
        var quest = JourneyClient.quests.stream().filter(q -> q.getString("Id").equals(JourneyClient.activeQuest))
                .findFirst().orElse(null);
        if (marker == null && quest == null) return;
        CompoundTag objective = quest == null ? null : quest.getList("Objectives", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast).filter(value -> !value.getBoolean("Met")).findFirst().orElse(null);
        String name = quest == null ? marker.title : quest.getString("Title");
        Component hint = objective == null ? (goalInFocus ? JourneyClient.tr("waypoint_arrive") : direction(mc, marker))
                : action(mc, marker, objective.getString("Kind"), objective.getString("Target"),
                        objective.getInt("Count"), false, goalInFocus);
        Component footer = JourneyClient.tr("hud_journal", JourneyClient.JOURNAL.getTranslatedKeyMessage());
        var chain = JourneyClient.chains.stream().filter(c -> c.getString("Id").equals(JourneyClient.chain)).findFirst();
        if (quest != null && chain.isPresent()) {
            int total = chain.get().getInt("Total");
            if (total > 0) footer = JourneyClient.tr("hud_step", Math.min(total, chain.get().getInt("Done") + 1), total,
                    JourneyClient.JOURNAL.getTranslatedKeyMessage());
        }
        if (goalInFocus) {
            JourneyWaypointHint.draw(graphics, mc, focused, true, Component.literal(name), hint, footer, now);
        } else {
            drawCard(graphics, mc, marker, Component.literal(name), hint, footer, now);
        }
    }

    private static Component action(Minecraft mc, Marker marker, String kind, String target, int count,
                                    boolean testing, boolean focused) {
        boolean nearby = marker != null && JourneyClient.distance(marker.point) <= marker.radius;
        if (kind.equals("ITEM")) {
            if (testing && nearby) return JourneyClient.tr("trial_item", itemName(target), count);
            Component items = JourneyClient.tr("hud_item", itemName(target), count);
            return marker != null && !nearby && !focused ? direction(mc, marker).copy().append(" · ").append(items) : items;
        }
        if (kind.equals("INTERACT") || kind.equals("OPEN_PYLON")) {
            if (nearby && aimingAt(mc, marker)) {
                return JourneyClient.tr(kind.equals("OPEN_PYLON") ? "hud_open" : "hud_use",
                        mc.options.keyUse.getTranslatedKeyMessage());
            }
            if (nearby) return JourneyClient.tr("hud_aim");
            if (focused) return JourneyClient.tr(kind.equals("OPEN_PYLON") ? "waypoint_approach_pylon" : "waypoint_approach_block");
        }
        if (marker != null && !focused) return direction(mc, marker);
        return switch (kind) {
            case "VISIT" -> JourneyClient.tr("waypoint_arrive");
            case "CITY_MEMBER", "SHIPMENT_DELIVERED", "PROJECT_COMPLETED", "ADVANCEMENT" ->
                    JourneyClient.tr("objective." + kind.toLowerCase(java.util.Locale.ROOT), target, count);
            default -> JourneyClient.tr("hud_journal", JourneyClient.JOURNAL.getTranslatedKeyMessage());
        };
    }

    private static Component itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key)
                ? BuiltInRegistries.ITEM.get(key).getDescription() : Component.literal(id);
    }

    private static boolean aimingAt(Minecraft mc, Marker marker) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        var pos = hit.getBlockPos();
        return marker.point.x() == pos.getX() && marker.point.y() == pos.getY() && marker.point.z() == pos.getZ();
    }

    private static Component direction(Minecraft mc, Marker marker) {
        if (marker == null) return Component.empty();
        double distance = JourneyClient.distance(marker.point);
        if (!Double.isFinite(distance)) return JourneyClient.tr("dimension", marker.point.dimension());
        var target = JourneyClient.displayPath(marker).getFirst();
        double yaw = Math.toDegrees(Math.atan2(-(target.x() + .5 - mc.player.getX()), target.z() + .5 - mc.player.getZ()));
        double angle = Mth.wrapDegrees(yaw - mc.player.getYRot());
        String arrow = Math.abs(angle) < 25 ? "↑" : angle > 0 ? "→" : "←";
        int elevation = target.y() - mc.player.getBlockY();
        var label = JourneyClient.tr("hud_distance", arrow, (int) Math.ceil(distance)).copy();
        if (Math.abs(elevation) >= 4) label.append(" · ").append(JourneyClient.tr(elevation > 0 ? "hud_up" : "hud_down"));
        return label;
    }

    private static void drawCard(GuiGraphics g, Minecraft mc, Marker marker, Component title, Component hint,
                                 Component footer, long now) {
        int width = Math.min(282, g.guiWidth() - 24);
        int x = 12;
        int y = Math.max(18, g.guiHeight() - 108);
        panel(g, x, y, width, 50);
        if (marker != null) mark(g, x + 19, y + 24, marker, true, false, now, JourneyClient.reduced(), false);
        int inset = marker == null ? 11 : 39;
        drawLine(g, mc, footer, x + inset, y + 6, width - inset - 8, BRASS);
        drawLine(g, mc, title, x + inset, y + 20, width - inset - 8, TEXT);
        drawLine(g, mc, hint, x + inset, y + 34, width - inset - 8, MUTED);
    }

    private static void toast(GuiGraphics g, Minecraft mc, Component title, long age) {
        int width = Math.min(244, g.guiWidth() - 24);
        int x = (g.guiWidth() - width) / 2;
        int y = 26 - (int) ((1 - reveal(age, JourneyClient.reduced())) * 5);
        panel(g, x, y, width, 30);
        for (var segment : glyph(7)) {
            line(g, x + 15 + segment[0], y + 15 + segment[1], x + 15 + segment[2], y + 15 + segment[3], BRASS);
        }
        drawLine(g, mc, title, x + 27, y + 11, width - 35, TEXT);
    }

    private static void drawLine(GuiGraphics g, Minecraft mc, Component text, int x, int y, int width, int color) {
        g.drawString(mc.font, mc.font.plainSubstrByWidth(text.getString(), Math.max(1, width)), x, y, color, false);
    }
}
