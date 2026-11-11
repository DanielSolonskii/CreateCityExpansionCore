package com.example.examplemod.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.cutscene.CutsceneClientPackets;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class CutsceneNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StreamCodec<ByteBuf, String> ID = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> REVISION = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> JSON = ByteBufCodecs
            .byteArray(CutsceneJson.MAX_JSON_BYTES)
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8), value -> value.getBytes(StandardCharsets.UTF_8));

    private CutsceneNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("cutscene-1").optional();
        registrar.playToServer(SaveRequest.TYPE, SaveRequest.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handleSave(player, packet);
            }
        });
        registrar.playToClient(Editor.TYPE, Editor.CODEC, (packet, context) -> CutsceneClientPackets.open(packet));
        registrar.playToClient(SaveResult.TYPE, SaveResult.CODEC, (packet, context) -> CutsceneClientPackets.saved(packet));
        registrar.playToClient(Play.TYPE, Play.CODEC, (packet, context) -> CutsceneClientPackets.play(packet, context.connection()));
        registrar.playToClient(Stop.TYPE, Stop.CODEC, (packet, context) -> CutsceneClientPackets.stop());
    }

    public static void save(String id, Cutscene scene, String revision) {
        if (!CutsceneClientPackets.canSave()) {
            throw new IllegalArgumentException("Сервер не поддерживает сохранение кат-сцен.");
        }
        PacketDistributor.sendToServer(new SaveRequest(id, revision, CutsceneJson.encode(scene)));
    }

    static boolean canEdit(ServerPlayer player) {
        return player.connection.hasChannel(Editor.TYPE) && player.connection.hasChannel(SaveResult.TYPE)
                && player.connection.hasChannel(SaveRequest.TYPE);
    }

    static void open(ServerPlayer player, String id, Cutscene scene) {
        boolean exists = scene != null;
        Cutscene draft = exists ? scene : new Cutscene(Cutscene.CURRENT_VERSION, player.level().dimension().location(),
                200, List.of(), List.of(), List.of());
        String json = CutsceneJson.encode(draft);
        PacketDistributor.sendToPlayer(player, new Editor(id, exists, exists ? revision(json) : "", json));
    }

    static boolean canPlay(ServerPlayer player) {
        return player.connection.hasChannel(Play.TYPE) && player.connection.hasChannel(Stop.TYPE);
    }

    static void play(ServerPlayer player, String json, boolean intro) {
        PacketDistributor.sendToPlayer(player, new Play(json, intro));
    }

    static void stop(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Stop());
    }

    static void requireContent(Cutscene scene) {
        if (scene.camera().isEmpty() && scene.sounds().isEmpty() && scene.texts().isEmpty()) {
            throw new IllegalArgumentException("Добавьте хотя бы одну точку камеры, звук или текст.");
        }
    }

    private static void handleSave(ServerPlayer player, SaveRequest request) {
        if (!player.connection.hasChannel(SaveResult.TYPE)) {
            return;
        }
        if (!player.hasPermissions(2)) {
            sendSaveResult(player, request.id(), false, "Для сохранения нужны права оператора уровня 2.", "");
            return;
        }

        try {
            var server = player.serverLevel().getServer();
            Cutscene scene = CutsceneJson.decode(request.json());
            requireContent(scene);
            if (server.levelKeys().stream().noneMatch(level -> level.location().equals(scene.dimension()))) {
                throw new IllegalArgumentException("Измерение сцены отсутствует на сервере.");
            }
            Cutscene stored = CutsceneStore.load(server, request.id());
            String currentRevision = stored == null ? "" : revision(CutsceneJson.encode(stored));
            if (!currentRevision.equals(request.revision())) {
                sendSaveResult(player, request.id(), false,
                        "Сцена изменилась на сервере. Скопируйте черновик в JSON и откройте сцену заново.", "");
                return;
            }
            String savedRevision = revision(CutsceneJson.encode(scene));
            CutsceneStore.save(server, request.id(), scene);
            sendSaveResult(player, request.id(), true, "Сцена сохранена на сервере.", savedRevision);
        } catch (IllegalArgumentException e) {
            sendSaveResult(player, request.id(), false, e.getMessage(), "");
        } catch (IOException e) {
            LOGGER.warn("Could not save cutscene {}", request.id(), e);
            sendSaveResult(player, request.id(), false, "Сервер не смог записать JSON сцены. Подробности в журнале.", "");
        }
    }

    private static void sendSaveResult(ServerPlayer player, String id, boolean success, String message, String revision) {
        String feedback = message == null ? "Ошибка сохранения сцены." : message;
        if (feedback.length() > 512) {
            feedback = feedback.substring(0, 512);
        }
        PacketDistributor.sendToPlayer(player, new SaveResult(id, success, feedback, revision));
    }

    private static String revision(String json) {
        return Hashing.sha256().hashString(json, StandardCharsets.UTF_8).toString();
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, path));
    }

    public record SaveRequest(String id, String revision, String json) implements CustomPacketPayload {
        public static final Type<SaveRequest> TYPE = CutsceneNetwork.type("cutscene_save");
        private static final StreamCodec<RegistryFriendlyByteBuf, SaveRequest> CODEC = StreamCodec.composite(
                ID, SaveRequest::id, REVISION, SaveRequest::revision, JSON, SaveRequest::json, SaveRequest::new);

        @Override
        public Type<SaveRequest> type() {
            return TYPE;
        }
    }

    public record Editor(String id, boolean exists, String revision, String json) implements CustomPacketPayload {
        private static final Type<Editor> TYPE = CutsceneNetwork.type("cutscene_editor");
        private static final StreamCodec<RegistryFriendlyByteBuf, Editor> CODEC = StreamCodec.composite(
                ID, Editor::id, ByteBufCodecs.BOOL, Editor::exists,
                REVISION, Editor::revision, JSON, Editor::json, Editor::new);

        @Override
        public Type<Editor> type() {
            return TYPE;
        }
    }

    public record SaveResult(String id, boolean success, String message, String revision) implements CustomPacketPayload {
        private static final Type<SaveResult> TYPE = CutsceneNetwork.type("cutscene_save_result");
        private static final StreamCodec<RegistryFriendlyByteBuf, SaveResult> CODEC = StreamCodec.composite(
                ID, SaveResult::id, ByteBufCodecs.BOOL, SaveResult::success,
                ByteBufCodecs.stringUtf8(512), SaveResult::message, REVISION, SaveResult::revision, SaveResult::new);

        @Override
        public Type<SaveResult> type() {
            return TYPE;
        }
    }

    public record Play(String json, boolean intro) implements CustomPacketPayload {
        private static final Type<Play> TYPE = CutsceneNetwork.type("cutscene_play");
        private static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(
                JSON, Play::json, ByteBufCodecs.BOOL, Play::intro, Play::new);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    public record Stop() implements CustomPacketPayload {
        private static final Type<Stop> TYPE = CutsceneNetwork.type("cutscene_stop");
        private static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = StreamCodec.unit(new Stop());

        @Override
        public Type<Stop> type() {
            return TYPE;
        }
    }
}
