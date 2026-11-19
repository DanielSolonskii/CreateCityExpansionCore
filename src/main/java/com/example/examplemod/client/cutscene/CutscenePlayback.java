package com.example.examplemod.client.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.cutscene.Cutscene;
import com.example.examplemod.cutscene.CutsceneChunks;
import com.example.examplemod.cutscene.CutsceneChunkNetwork;
import com.example.examplemod.cutscene.CutsceneJson;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class CutscenePlayback {
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private static final Component SKIP = Component.translatable("screen.createcityexpansioncore.cutscene.skip");
    private static @Nullable Playback active;

    private CutscenePlayback() {
    }

    public static void start(Cutscene scene, @Nullable Runnable onFinish) {
        start(scene, 0, onFinish);
    }

    public static void start(Cutscene scene, int startTick, @Nullable Runnable onFinish) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null || !player.isAlive() || !level.dimension().location().equals(scene.dimension())) {
            throw new IllegalStateException("Cutscene playback requires a living player in the scene's dimension");
        }
        if (startTick < 0 || startTick >= scene.durationTicks()) {
            throw new IllegalArgumentException("Playback must start before the end of the cutscene");
        }

        Runnable completion = onFinish;
        Screen editor = client.screen;
        if (completion == null && (editor instanceof CutsceneEditorScreen || editor instanceof CutsceneCueScreen)) {
            completion = () -> client.setScreen(editor);
        } else if (completion == null && active != null && active.isCurrent(client)) {
            completion = active.onFinish;
        }
        if (client.screen instanceof AbstractContainerScreen<?>) {
            player.closeContainer();
        }

        PlaybackScreen screen = new PlaybackScreen();
        client.setScreen(screen);
        if (client.screen != screen) {
            throw new IllegalStateException("Another screen prevented cutscene playback");
        }
        active = new Playback(scene, level, player, screen, startTick, completion);
        active.beginChunks(client);
        if (active.started) {
            active.playSounds(client, startTick);
        }
    }

    public static void stop() {
        finish(true);
    }

    public static boolean isPlaying() {
        return current() != null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Playback playback = active;
        if (playback == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!playback.isCurrent(client)) {
            finish(false);
            return;
        }
        double time = playback.time();
        if (!playback.started) {
            if (playback.chunksAccepted && playback.initialChunksLoaded()) {
                playback.started = true;
                playback.startTime = Util.getNanos();
                playback.playSounds(client, playback.startTick);
            } else if (Util.getNanos() - playback.createdAt > 60_000_000_000L) {
                finish(true);
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("Не удалось загрузить чанки кат-сцены."), false);
                }
            } else {
                playback.sendProgress(playback.startTick);
            }
            return;
        }
        playback.sendProgress((int) time);
        if (time >= playback.scene.durationTicks()) {
            finish(true);
        } else {
            playback.playSounds(client, time);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Minecraft is already tearing down the level and will replace the playback screen.
        discard();
    }

    @SubscribeEvent
    public static void hideHud(RenderGuiEvent.Pre event) {
        if (isPlaying()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void hideBlockOutline(RenderHighlightEvent.Block event) {
        if (isPlaying()) {
            event.setCanceled(true);
        }
    }

    public static @Nullable CameraPose cameraPose(Camera camera) {
        Playback playback = current();
        if (playback == null || playback.scene.camera().isEmpty()
            || camera != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            return null;
        }
        return playback.cameraPose(playback.time());
    }

    public static void acceptChunks(UUID session) {
        if (active != null && active.session.equals(session)) {
            active.chunksAccepted = true;
        }
    }

    private static @Nullable Playback current() {
        Playback playback = active;
        return playback != null && playback.isCurrent(Minecraft.getInstance()) ? playback : null;
    }

    private static void finish(boolean returnToEditor) {
        Playback playback = discard();
        Minecraft client = Minecraft.getInstance();
        if (playback == null || client.screen != playback.screen || client.level == null || client.player == null) {
            return;
        }
        try {
            if (returnToEditor && playback.isCurrent(client) && playback.onFinish != null) {
                playback.onFinish.run();
            }
        } finally {
            if (client.screen == playback.screen) {
                client.setScreen(null);
            }
        }
    }

    private static @Nullable Playback discard() {
        Playback playback = active;
        if (playback != null) {
            active = null;
            if (playback.streaming && Minecraft.getInstance().getConnection() != null
                    && Minecraft.getInstance().level == playback.level) {
                PacketDistributor.sendToServer(new CutsceneChunkNetwork.Request(playback.session, 2, 0, ""));
            }
            playback.screen.restoreCursor();
            var soundManager = Minecraft.getInstance().getSoundManager();
            playback.sounds.forEach(soundManager::stop);
        }
        return playback;
    }

    public record CameraPose(double x, double y, double z, float yaw, float pitch) {
        private CameraPose(Cutscene.CameraKeyframe frame) {
            this(frame.x(), frame.y(), frame.z(), frame.yaw(), frame.pitch());
        }
    }

    private static final class Playback {
        private final Cutscene scene;
        private final ClientLevel level;
        private final LocalPlayer player;
        private final PlaybackScreen screen;
        private final int startTick;
        private final @Nullable Runnable onFinish;
        private final UUID session = UUID.randomUUID();
        private final long createdAt = Util.getNanos();
        private long startTime = createdAt;
        private long lastProgress;
        private long chunksLoadedAt;
        private boolean started = true;
        private boolean streaming;
        private boolean chunksAccepted;
        private final List<SoundInstance> sounds = new ArrayList<>();
        private int soundIndex;
        private int cameraIndex;

        private Playback(Cutscene scene, ClientLevel level, LocalPlayer player, PlaybackScreen screen, int startTick,
                         @Nullable Runnable onFinish) {
            this.scene = scene;
            this.level = level;
            this.player = player;
            this.screen = screen;
            this.startTick = startTick;
            this.onFinish = onFinish;
            while (soundIndex < scene.sounds().size() && scene.sounds().get(soundIndex).tick() < startTick) {
                soundIndex++;
            }
        }

        private boolean isCurrent(Minecraft client) {
            return client.level == level && client.player == player && player.isAlive() && client.screen == screen;
        }

        private double time() {
            return started ? startTick + (Util.getNanos() - startTime) / NANOS_PER_TICK : startTick;
        }

        private void beginChunks(Minecraft client) {
            streaming = !scene.camera().isEmpty() && client.getConnection() != null
                    && client.getConnection().hasChannel(CutsceneChunkNetwork.Request.TYPE)
                    && client.getConnection().hasChannel(CutsceneChunkNetwork.Accept.TYPE);
            if (streaming) {
                started = false;
                lastProgress = Util.getNanos();
                PacketDistributor.sendToServer(new CutsceneChunkNetwork.Request(session, 0, startTick, CutsceneJson.encode(scene)));
            }
        }

        private void sendProgress(int tick) {
            long now = Util.getNanos();
            if (streaming && now - lastProgress >= 100_000_000L) {
                lastProgress = now;
                PacketDistributor.sendToServer(new CutsceneChunkNetwork.Request(session, 1,
                        Math.min(tick, scene.durationTicks() - 1), ""));
            }
        }

        private boolean initialChunksLoaded() {
            var center = CutsceneChunks.position(scene, startTick);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (!level.getChunkSource().hasChunk(center.x + x, center.z + z)) {
                        chunksLoadedAt = 0;
                        return false;
                    }
                }
            }
            if (chunksLoadedAt == 0) {
                chunksLoadedAt = Util.getNanos();
            }
            // Let the moved section grid compile before revealing the first frame.
            // Empty/void scenes still start even when there are no renderable sections.
            return Minecraft.getInstance().levelRenderer.countRenderedSections() > 0
                    || Util.getNanos() - chunksLoadedAt >= 3_000_000_000L;
        }

        private void playSounds(Minecraft client, double time) {
            List<Cutscene.SoundCue> cues = scene.sounds();
            while (soundIndex < cues.size() && cues.get(soundIndex).tick() <= time) {
                Cutscene.SoundCue cue = cues.get(soundIndex++);
                if (cue.volume() > 0) {
                    SoundInstance sound = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(cue.sound()),
                        cue.pitch(), cue.volume());
                    client.getSoundManager().play(sound);
                    sounds.add(sound);
                }
            }
        }

        private CameraPose cameraPose(double time) {
            List<Cutscene.CameraKeyframe> frames = scene.camera();
            while (cameraIndex + 1 < frames.size() && frames.get(cameraIndex + 1).tick() <= time) {
                cameraIndex++;
            }
            Cutscene.CameraKeyframe from = frames.get(cameraIndex);
            if (time <= from.tick() || cameraIndex == frames.size() - 1) {
                return new CameraPose(from);
            }
            Cutscene.CameraKeyframe to = frames.get(cameraIndex + 1);
            double progress = (time - from.tick()) / (to.tick() - from.tick());
            double eased = switch (from.easing()) {
                case LINEAR -> progress;
                case SMOOTH -> progress * progress * (3 - 2 * progress);
                case EASE_IN -> progress * progress;
                case EASE_OUT -> progress * (2 - progress);
                case HOLD -> 0;
            };
            float yaw = from.yaw() + (float) eased * Mth.wrapDegrees(to.yaw() - from.yaw());
            return new CameraPose(Mth.lerp(eased, from.x(), to.x()), Mth.lerp(eased, from.y(), to.y()),
                Mth.lerp(eased, from.z(), to.z()), yaw, Mth.lerp((float) eased, from.pitch(), to.pitch()));
        }
    }

    private static final class PlaybackScreen extends Screen {
        private boolean cursorHidden;

        private PlaybackScreen() {
            super(Component.translatable("screen.createcityexpansioncore.cutscene.playback"));
        }

        @Override
        protected void init() {
            long window = minecraft.getWindow().getWindow();
            if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
                cursorHidden = true;
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void tick() {
            if (active == null || active.screen != this) {
                super.onClose();
            }
        }

        @Override
        public void onClose() {
            if (active != null && active.screen == this) {
                stop();
            } else {
                super.onClose();
            }
        }

        @Override
        public void removed() {
            if (active != null && active.screen == this) {
                discard();
            } else {
                restoreCursor();
            }
        }

        private void restoreCursor() {
            if (cursorHidden) {
                long window = minecraft.getWindow().getWindow();
                if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_HIDDEN) {
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }
                cursorHidden = false;
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Playback playback = current();
            if (playback == null) {
                return;
            }
            double time = playback.time();
            if (!playback.started) {
                graphics.fill(0, 0, width, height, 0xFF121822);
                graphics.drawCenteredString(font, Component.literal("Загрузка чанков кат-сцены…"),
                        width / 2, height / 2, 0xFFF2F5F8);
                graphics.drawCenteredString(font, SKIP, width / 2, height / 2 + 20, 0xFFACBACB);
                return;
            }
            int bottom = height - 28;
            int lineHeight = font.lineHeight + 3;
            int maxWidth = Math.max(1, Math.min(420, width - 40));
            List<Cutscene.TextCue> cues = playback.scene.texts();
            for (int index = cues.size() - 1; index >= 0 && bottom > 28; index--) {
                Cutscene.TextCue cue = cues.get(index);
                if (time < cue.tick() || time >= cue.tick() + cue.durationTicks()) {
                    continue;
                }
                List<FormattedCharSequence> lines = font.split(Component.literal(cue.text()), maxWidth);
                int lineCount = Math.min(lines.size(), (bottom - 12 - 12) / lineHeight);
                if (lineCount <= 0) {
                    break;
                }
                int panelWidth = lines.subList(0, lineCount).stream().mapToInt(font::width).max().orElse(0) + 24;
                int panelHeight = lineCount * lineHeight + 10;
                int left = (width - panelWidth) / 2;
                int top = bottom - panelHeight;
                graphics.fill(left, top, left + panelWidth, bottom, cue.backgroundColor());
                if ((cue.textColor() >>> 24) >= 4) {
                    for (int line = 0; line < lineCount; line++) {
                        graphics.drawCenteredString(font, lines.get(line), width / 2, top + 6 + line * lineHeight,
                            cue.textColor());
                    }
                }
                bottom = top - 6;
            }
            int hintLeft = width - font.width(SKIP) - 12;
            graphics.fill(hintLeft - 5, height - 21, width - 7, height - 6, 0x88000000);
            graphics.drawString(font, SKIP, hintLeft, height - 18, 0xFFE3E9EF, false);
        }
    }
}
