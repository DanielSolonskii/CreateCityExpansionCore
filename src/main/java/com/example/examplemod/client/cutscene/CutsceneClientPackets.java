package com.example.examplemod.client.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.cutscene.Cutscene;
import com.example.examplemod.cutscene.CutsceneJson;
import com.example.examplemod.cutscene.CutsceneNetwork;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class CutsceneClientPackets {
    private static WeakReference<CutsceneEditorScreen> editor = new WeakReference<>(null);
    private static PendingIntro intro;

    private CutsceneClientPackets() {
    }

    public static boolean canSave() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(CutsceneNetwork.SaveRequest.TYPE);
    }

    public static void open(CutsceneNetwork.Editor packet) {
        Minecraft client = Minecraft.getInstance();
        try {
            Cutscene scene = CutsceneJson.decode(packet.json());
            stop();
            CutsceneEditorScreen screen = new CutsceneEditorScreen(packet.id(), scene, packet.exists(), packet.revision());
            editor = new WeakReference<>(screen);
            client.setScreen(screen);
        } catch (IllegalArgumentException e) {
            showError(e);
        }
    }

    public static void saved(CutsceneNetwork.SaveResult packet) {
        CutsceneEditorScreen screen = editor.get();
        if (screen != null && screen.sceneId().equals(packet.id())) {
            screen.onSaveResult(packet.success(), packet.message(), packet.revision());
        }
    }

    public static void play(CutsceneNetwork.Play packet, Connection connection) {
        try {
            Cutscene scene = CutsceneJson.decode(packet.json());
            if (packet.intro()) {
                intro = new PendingIntro(scene, connection);
            } else {
                intro = null;
                start(scene);
            }
        } catch (IllegalArgumentException e) {
            showError(e);
        }
    }

    public static void stop() {
        intro = null;
        CutscenePlayback.stop();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        PendingIntro pending = intro;
        if (pending == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener listener = client.getConnection();
        if (!pending.connection().isConnected()
                || (listener != null && listener.getConnection() != pending.connection())
                || (client.level != null && !client.level.dimension().location().equals(pending.scene().dimension()))) {
            intro = null;
        } else if (listener != null && client.player != null && client.player.isAlive() && client.level != null
                && client.screen == null && client.getOverlay() == null
                && client.level.getChunkSource().hasChunk(client.player.chunkPosition().x, client.player.chunkPosition().z)) {
            intro = null;
            start(pending.scene());
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        intro = null;
        editor.clear();
    }

    private static void start(Cutscene scene) {
        try {
            CutscenePlayback.start(scene, null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            showError(e);
        }
    }

    private static void showError(RuntimeException error) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Ошибка кат-сцены. " + error.getMessage()), false);
        }
    }

    private record PendingIntro(Cutscene scene, Connection connection) {
    }
}
