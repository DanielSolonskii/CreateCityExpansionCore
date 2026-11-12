package com.example.examplemod.client.journey;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.journey.JourneyModel.Kind;
import com.example.examplemod.journey.JourneyModel.Marker;
import com.example.examplemod.journey.JourneyModel.ObjectiveKind;
import com.example.examplemod.journey.JourneyModel.Point;
import com.example.examplemod.journey.JourneyPlaytestNetwork;
import com.example.examplemod.journey.JourneyPlaytestNetwork.Action;
import com.example.examplemod.journey.JourneyRouteDraft;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class JourneyPlaytest {
    private static Session session;
    private static Component lastError;

    private JourneyPlaytest() {}

    public static Component lastError() { return lastError; }
    public static boolean running() { return session != null; }
    static boolean waiting() { return session != null && !session.ready; }
    static boolean completed() { return session != null && session.trial.current() == null; }
    static JourneyRouteDraft.Step step() { return session == null ? null : session.trial.current(); }
    static int stepNumber() { return session.trial.index() + 1; }
    static int totalSteps() { return session.trial.total(); }
    static long targetAt() { return session == null ? 0 : session.targetAt; }
    static long completionAt() { return session == null ? 0 : session.completionAt; }
    static Marker completionMarker() { return session == null ? null : session.completed; }

    static Marker marker() {
        if (session == null || !session.ready || completed()) return null;
        return session.markers.get(session.trial.index());
    }

    static List<Point> remaining(Marker marker) {
        List<Point> path = JourneyClient.path(marker);
        return path.subList(Math.min(session.waypoint, path.size() - 1), path.size());
    }

    static void openGuide() {
        var step = step();
        if (step == null || waiting()) return;
        Minecraft.getInstance().setScreen(new Screen(Component.literal(step.title())) {
            @Override protected void init() {
                addRenderableWidget(JourneyAtlasStyle.button(width / 2 - 62, height / 2 + 66,
                        124, "resume", this::onClose));
            }

            @Override public boolean isPauseScreen() { return false; }

            @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                int panelWidth = Math.min(360, width - 24);
                int x = (width - panelWidth) / 2;
                int y = height / 2 - 84;
                JourneyAtlasStyle.panel(graphics, x, y, panelWidth, 180);
                graphics.drawString(font, JourneyClient.tr("trial_label"), x + 12, y + 12, JourneyAtlasStyle.BRASS);
                graphics.drawWordWrap(font, title, x + 12, y + 31, panelWidth - 24, JourneyAtlasStyle.TEXT);
                graphics.enableScissor(x + 12, y + 49, x + panelWidth - 12, y + 141);
                Component hint = step.description().isBlank() ? JourneyClient.tr("trial_no_guide") : Component.literal(step.description());
                graphics.drawWordWrap(font, hint, x + 12, y + 50, panelWidth - 24, JourneyAtlasStyle.MUTED);
                graphics.disableScissor();
                super.render(graphics, mouseX, mouseY, partialTick);
            }
        });
    }

    public static boolean start(Screen editor, CompoundTag draft, int stepIndex, Runnable onAbandon) {
        lastError = null;
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientPacketListener connection = client.getConnection();
        if (session != null || client.screen != editor || player == null || !player.isAlive() || connection == null
                || !connection.hasChannel(JourneyPlaytestNetwork.Request.TYPE)) {
            return reject("trial_unavailable");
        }

        try {
            JourneyRouteDraft definition = JourneyRouteDraft.parse(draft);
            JourneyTrial trial = new JourneyTrial(definition, stepIndex);
            Point start = stepIndex == 0 ? definition.start() : null;
            if (start != null && JourneyClient.distance(start) > 4) return reject("trial_at_start");
            if (!trial.current().point().dimension().equals(player.level().dimension().location().toString())) {
                return reject("trial_dimension");
            }
            session = new Session(editor, onAbandon, connection, player, trial, definition.steps());
        } catch (JourneyRouteDraft.Failure failure) {
            Component reason = JourneyClient.tr("error." + failure.code());
            lastError = failure.step() > 0 ? JourneyClient.tr("route_validation", failure.step(), reason) : reason;
            return false;
        } catch (IllegalArgumentException failure) {
            return reject("trial_invalid");
        }

        client.setScreen(null);
        if (client.screen != null) {
            session = null;
            return reject("trial_unavailable");
        }
        send(session, Action.BEGIN);
        return true;
    }

    private static boolean reject(String key) {
        lastError = JourneyClient.tr(key);
        return false;
    }

    private static void accept(JourneyPlaytestNetwork.Reply reply) {
        Session current = session;
        if (current == null || !current.id.equals(reply.id())) return;
        if (!current.isCurrent(Minecraft.getInstance())) {
            finish(false);
            return;
        }
        if (!reply.accepted()) {
            reject("trial_unavailable");
            finish(true);
            return;
        }
        long now = Util.getMillis();
        if (!current.ready) {
            current.ready = true;
            current.targetAt = now;
            CommunityFx.sound("open");
        }
        current.replyDeadline = now + 20_000;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Session current = session;
        if (current == null) return;
        Minecraft client = Minecraft.getInstance();
        if (!current.isCurrent(client)) {
            finish(false);
            return;
        }
        long now = Util.getMillis();
        if (now >= current.replyDeadline) {
            reject("trial_timeout");
            finish(true);
            return;
        }
        if (!current.ready) return;
        if (now >= current.nextHeartbeat) {
            current.nextHeartbeat = now + 8_000;
            send(current, Action.KEEP_ALIVE);
        }
        Marker marker = marker();
        if (marker == null || client.screen != null || now - current.targetAt < 450) return;
        List<Point> path = JourneyClient.path(marker);
        while (current.waypoint < path.size() - 1 && JourneyClient.distance(path.get(current.waypoint)) < 3) {
            current.waypoint++;
        }
        var player = current.player;
        if (current.trial.visit(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
            current.advance(marker);
        }
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        Session current = session;
        if (current == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft client = Minecraft.getInstance();
        Marker marker = marker();
        if (!event.isUseItem() || marker == null || client.screen != null || !current.isCurrent(client)
                || JourneyClient.distance(marker.point) > marker.radius || Util.getMillis() - current.targetAt < 450
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        var pos = hit.getBlockPos();
        if (step().type() == ObjectiveKind.OPEN_PYLON && !(current.player.level().getBlockState(pos).getBlock()
                instanceof com.example.examplemod.block.CityPylonBlock)) return;
        var point = new Point(current.player.level().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        if (current.trial.interact(point)) current.advance(marker);
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        Session current = session;
        if (current == null || event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_ENTER) return;
        Minecraft client = Minecraft.getInstance();
        Marker marker = marker();
        if (marker == null || client.screen != null || !current.isCurrent(client)
                || Util.getMillis() - current.targetAt < 450) return;
        var player = current.player;
        if (current.trial.simulateItem(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
            current.advance(marker);
        }
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        Session current = session;
        if (current == null || !(event.getNewScreen() instanceof PauseScreen)
                || !current.isCurrent(Minecraft.getInstance())) return;
        finishLease(current);
        event.setNewScreen(current.editor);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        finish(false);
    }

    private static void finish(boolean returnToEditor) {
        Session current = session;
        if (current == null) return;
        finishLease(current);
        Minecraft client = Minecraft.getInstance();
        if (returnToEditor && current.isCurrent(client)) client.setScreen(current.editor);
        else current.onAbandon.run();
    }

    private static void finishLease(Session current) {
        session = null;
        if (Minecraft.getInstance().getConnection() == current.connection) send(current, Action.END);
    }

    private static void send(Session current, Action action) {
        PacketDistributor.sendToServer(new JourneyPlaytestNetwork.Request(current.id, action));
    }

    private static final class Session {
        private final UUID id = UUID.randomUUID();
        private final Screen editor;
        private final Runnable onAbandon;
        private final ClientPacketListener connection;
        private final LocalPlayer player;
        private final JourneyTrial trial;
        private final List<Marker> markers;
        private boolean ready;
        private int waypoint;
        private long targetAt;
        private long completionAt;
        private long replyDeadline = Util.getMillis() + 8_000;
        private long nextHeartbeat = Util.getMillis() + 8_000;
        private Marker completed;

        private Session(Screen editor, Runnable onAbandon, ClientPacketListener connection, LocalPlayer player,
                        JourneyTrial trial, List<JourneyRouteDraft.Step> steps) {
            this.editor = editor;
            this.onAbandon = onAbandon;
            this.connection = connection;
            this.player = player;
            this.trial = trial;
            markers = java.util.stream.IntStream.range(0, steps.size()).mapToObj(i -> marker(steps.get(i), i)).toList();
        }

        private boolean isCurrent(Minecraft client) {
            return client.getConnection() == connection && client.player == player && player.isAlive();
        }

        private void advance(Marker marker) {
            completed = marker;
            completionAt = targetAt = Util.getMillis();
            waypoint = 0;
            CommunityFx.sound("success");
        }

        private static Marker marker(JourneyRouteDraft.Step step, int index) {
            Marker marker = new Marker("draft/" + index, step.point());
            marker.title = step.title();
            marker.description = step.description();
            marker.radius = step.radius();
            marker.kind = step.type() == ObjectiveKind.VISIT ? Kind.GUIDE : Kind.QUEST;
            marker.icon = step.icon();
            marker.route.addAll(step.route());
            return marker;
        }
    }

    @EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> JourneyPlaytestNetwork.client = JourneyPlaytest::accept);
        }
    }
}
