package com.example.examplemod.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.mixin.CutsceneChunkTrackingAccess;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-owned camera paths. Clients supply playback time, never arbitrary chunk coordinates. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class CutsceneChunks {
    private static final TicketType<UUID> TICKET = TicketType.create("ccec_cutscene", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, String> GRANTS = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private CutsceneChunks() {
    }

    static void authorize(ServerPlayer player, String json) {
        GRANTS.put(player.getUUID(), json);
    }

    public static ChunkPos center(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.player == player && session.level == player.level() ? session.center : null;
    }

    public static int radius(ServerPlayer player) {
        return Math.max(2, Math.min(8, Math.min(player.requestedViewDistance(),
                player.serverLevel().getServer().getPlayerList().getViewDistance())));
    }

    static void handle(ServerPlayer player, CutsceneChunkNetwork.Request request) {
        Session session = SESSIONS.get(player.getUUID());
        if (request.action() == 0) {
            if (!player.isAlive() || (!player.hasPermissions(2) && !request.json().equals(GRANTS.get(player.getUUID())))) {
                return;
            }
            Cutscene scene;
            try {
                scene = CutsceneJson.decode(request.json());
            } catch (IllegalArgumentException e) {
                return;
            }
            if (scene.camera().isEmpty() || !scene.dimension().equals(player.level().dimension().location())
                    || request.tick() < 0 || request.tick() >= scene.durationTicks()) {
                return;
            }
            stop(player);
            session = new Session(player, request.session(), scene, request.tick());
            SESSIONS.put(player.getUUID(), session);
            session.update(request.tick());
            CutsceneChunkNetwork.accept(player, request.session());
        } else if (session != null && session.id.equals(request.session())) {
            if (request.action() == 2) {
                stop(player);
            } else if (request.action() == 1 && request.tick() >= session.tick
                    && request.tick() < CutscenePresentation.endTick(session.scene)
                    && request.tick() - session.startTick <= (System.nanoTime() - session.startedAt) / 50_000_000L + 40) {
                session.update(request.tick());
            }
        }
    }

    static void stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            session.release();
            if (player.level() == session.level && !player.isRemoved()) {
                ((CutsceneChunkTrackingAccess) session.level.getChunkSource().chunkMap).ccec$updateChunkTracking(player);
            }
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (Session session : java.util.List.copyOf(SESSIONS.values())) {
            if (session.player.serverLevel().getServer() != event.getServer()) {
                continue;
            }
            if (!session.player.isAlive() || session.player.hasDisconnected()
                    || session.player.level() != session.level || System.nanoTime() - session.updatedAt > 10_000_000_000L) {
                stop(session.player);
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
            GRANTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        for (Session session : java.util.List.copyOf(SESSIONS.values())) {
            session.release();
        }
        SESSIONS.clear();
        GRANTS.clear();
    }

    /** Same outgoing-segment easing and shortest-yaw-independent position as the client camera. */
    public static ChunkPos position(Cutscene scene, double tick) {
        var frames = scene.camera();
        var from = frames.getFirst();
        for (int index = 1; index < frames.size(); index++) {
            var to = frames.get(index);
            if (tick < to.tick()) {
                double progress = Math.clamp((tick - from.tick()) / (to.tick() - from.tick()), 0, 1);
                double eased = switch (from.easing()) {
                    case LINEAR -> progress;
                    case SMOOTH -> progress * progress * (3 - 2 * progress);
                    case EASE_IN -> progress * progress;
                    case EASE_OUT -> progress * (2 - progress);
                    case HOLD -> 0;
                };
                return new ChunkPos((int) Math.floor((from.x() + eased * (to.x() - from.x())) / 16),
                        (int) Math.floor((from.z() + eased * (to.z() - from.z())) / 16));
            }
            from = to;
        }
        return new ChunkPos((int) Math.floor(from.x() / 16), (int) Math.floor(from.z() / 16));
    }

    private static final class Session {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final UUID id;
        private final Cutscene scene;
        private final int startTick;
        private final long startedAt = System.nanoTime();
        private long updatedAt = startedAt;
        private int tick;
        private int radius;
        private ChunkPos center;
        private ChunkPos ahead;

        private Session(ServerPlayer player, UUID id, Cutscene scene, int startTick) {
            this.player = player;
            this.level = player.serverLevel();
            this.id = id;
            this.scene = scene;
            this.startTick = startTick;
        }

        private void update(int tick) {
            this.tick = tick;
            updatedAt = System.nanoTime();
            ChunkPos next = cameraPosition(tick);
            ChunkPos nextAhead = cameraPosition(Math.min(CutscenePresentation.endTick(scene), tick + 40));
            int nextRadius = CutsceneChunks.radius(player);
            if (next.equals(center) && nextAhead.equals(ahead) && nextRadius == radius) {
                return;
            }
            release();
            center = next;
            ahead = nextAhead;
            radius = nextRadius;
            level.getChunkSource().addRegionTicket(TICKET, center, radius + 1, id);
            if (!ahead.equals(center)) {
                level.getChunkSource().addRegionTicket(TICKET, ahead, radius + 1, id);
            }
            ((CutsceneChunkTrackingAccess) level.getChunkSource().chunkMap).ccec$updateChunkTracking(player);
        }

        private ChunkPos cameraPosition(double time) {
            var last = scene.camera().getLast();
            if (time < last.tick()) {
                return position(scene, time);
            }
            double progress = CutscenePresentation.returnProgress(scene, time);
            double x = last.x() + progress * (player.getX() - last.x());
            double z = last.z() + progress * (player.getZ() - last.z());
            return new ChunkPos((int) Math.floor(x / 16), (int) Math.floor(z / 16));
        }

        private void release() {
            if (center != null) {
                level.getChunkSource().removeRegionTicket(TICKET, center, radius + 1, id);
                if (!ahead.equals(center)) {
                    level.getChunkSource().removeRegionTicket(TICKET, ahead, radius + 1, id);
                }
            }
        }
    }
}
