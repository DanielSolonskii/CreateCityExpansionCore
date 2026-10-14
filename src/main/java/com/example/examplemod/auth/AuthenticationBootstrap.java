package com.example.examplemod.auth;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Authentication boundary only: does not rewrite profiles, playerdata or gameplay managers. */
public final class AuthenticationBootstrap {
    public static final ConfigurationTask.Type AUTH_TASK = new ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","game_authentication"));
    private static volatile Runtime runtime;
    private static volatile MinecraftServer privateTcpServer;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<Player,Component> clientDisplayNames = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private AuthenticationBootstrap() {}
    public static void setup(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER,AuthenticationServerConfig.SPEC,"createcityexpansioncore-authentication.toml");
        bus.addListener(AuthenticationBootstrap::payloads);
        bus.addListener(AuthenticationBootstrap::tasks);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::starting);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::tick);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::stopped);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::displayName);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::tabName);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::displayTick);
        NeoForge.EVENT_BUS.addListener(AuthenticationBootstrap::displayLogin);
    }
    /** Presentation only: never writes GameProfile, UUID, permissions or admission state. */
    public static boolean accountDisplayName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }
    public static String verifiedDisplayName(Player player) {
        Runtime r=runtime;
        if (!(player instanceof ServerPlayer serverPlayer) || r==null || serverPlayer.getServer()!=r.server) return null;
        var identity=r.registry.identity(player.getUUID(),serverPlayer.connection.getConnection());
        return identity!=null && accountDisplayName(identity.displayName()) ? identity.displayName() : null;
    }
    private static Component authenticatedName(Player player) {
        String name = verifiedDisplayName(player);
        return name == null ? null : Component.literal(name);
    }
    private static void displayName(PlayerEvent.NameFormat event) {
        Player player=event.getEntity();
        Component name=authenticatedName(player);
        if (player.level().isClientSide() && player.getGameProfile().getName().matches("cs[a-z0-9]{14}")) {
            Component synced=player.getCustomName();
            if (synced!=null && accountDisplayName(synced.getString())) name=synced;
        }
        if (name!=null) event.setDisplayname(name);
    }
    private static void tabName(PlayerEvent.TabListNameFormat event) {
        Component name=authenticatedName(event.getEntity());
        if (name!=null) event.setDisplayName(name);
    }
    private static void displayLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerNames.name(player);
        syncDisplay(event.getEntity());
    }
    private static void displayTick(PlayerTickEvent.Post event) { syncDisplay(event.getEntity()); }
    private static void syncDisplay(Player player) {
        if (player.level().isClientSide()) {
            // Entity CustomName metadata is server-owned. NameFormat is cached independently.
            Component current=player.getCustomName();
            Component previous=clientDisplayNames.put(player,current);
            if (!java.util.Objects.equals(previous,current)) player.refreshDisplayName();
            return;
        }
        Component name=authenticatedName(player);
        if (name!=null && !name.equals(player.getCustomName())) {
            player.setCustomName(name);
            player.refreshDisplayName();
            ((ServerPlayer)player).refreshTabListName();
        }
    }
    private static void payloads(RegisterPayloadHandlersEvent e) {
        var registrar=e.registrar("game-auth-1").optional();
        registrar.configurationToClient(GameAuthPayloads.Challenge.TYPE,GameAuthPayloads.Challenge.CODEC,
                (p,c)->com.example.examplemod.client.auth.ConfigurationAuthClient.challenge(p,c));
        registrar.configurationToServer(GameAuthPayloads.Proof.TYPE,GameAuthPayloads.Proof.CODEC,AuthenticationBootstrap::proof);
    }
    /**
     * Fail-fast authentication startup.
     *
     * <p>Every rule lives in {@link AuthenticationStartupPolicy}, which is pure and therefore
     * verifiable headless; this method only reads the live server, applies the decision and
     * performs the marker side effect.
     *
     * <p>Dedicated servers are unchanged: REQUIRED with an unusable configuration still aborts the
     * boot, so a production deployment cannot come up half-authenticated. Integrated servers are the
     * only relaxed case, because they can never satisfy REQUIRED's transport preconditions at all —
     * vanilla {@code IntegratedServer} is permanently online-mode and never receives the private
     * loopback binding. Such a world starts with a local development identity, unless it is a marked
     * production world, which is still refused.
     */
    private static void starting(ServerStartingEvent e) {
        var server=e.getServer();
        var marker=server.getWorldPath(LevelResource.ROOT).resolve("recreated-auth-domain.txt");
        try {
            var decision=AuthenticationStartupPolicy.evaluate(readFacts(server,marker));
            switch (decision.decision()) {
                case AUTHENTICATED -> startProduction(server,marker);
                case LOCAL_IDENTITY -> { runtime=null; System.err.println(decision.reason()); }
                case REJECTED -> throw new IllegalStateException(decision.reason());
            }
        } catch (Exception rejected) {
            // Fail closed on anything at all, exactly as before — an unreadable marker or a failed
            // write must abort the boot, not degrade it. Same top-level message an operator's alert
            // matches on, now with the specific cause attached instead of discarded.
            throw new IllegalStateException("RE:CREATED authentication startup configuration rejected",rejected);
        }
    }

    /** Reads the facts the policy needs. Never decides anything itself. */
    private static AuthenticationStartupPolicy.Facts readFacts(MinecraftServer server,Path marker) throws java.io.IOException {
        // The marker is read only when it exists, so an unmarked world never touches the file.
        boolean marked=Files.exists(marker);
        return new AuthenticationStartupPolicy.Facts(
                server.isDedicatedServer(),
                AuthenticationServerConfig.MODE.get()==AuthenticationServerConfig.Mode.REQUIRED,
                AuthenticationServerConfig.SERVER_ID.get(),
                !server.usesAuthentication(),
                privateTcpServer==server,
                marked,
                marked ? Files.readString(marker) : null);
    }

    /**
     * Enforces production authentication and pins the world to a single domain.
     *
     * <p>The marker is written once and then never changes: it is what stops a production world from
     * being reopened under a different {@code serverId}, and what stops REQUIRED being switched off
     * on it. Writing uses {@code CREATE_NEW} so a concurrent start cannot silently re-point it.
     */
    private static void startProduction(MinecraftServer server,Path marker) throws java.io.IOException {
        String id=AuthenticationServerConfig.SERVER_ID.get();
        var backend=new GameBackendClient(AuthenticationServerConfig.BACKEND.get(),System.getenv("CCEC_BACKEND_SECRET"));
        if (!Files.exists(marker)) Files.writeString(marker,id,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        runtime=new Runtime(server,id,backend); runtime.registerBoot();
    }
    private static void tick(ServerTickEvent.Post event) {
        Runtime r=runtime; if(r==null || r.server!=event.getServer()) return;
        r.registry.sweep();
        r.pending.entrySet().removeIf(entry -> !entry.getKey().isConnected());
        if (!r.ready && System.nanoTime()-r.retryAt>=0) r.registerBoot();
    }
    private static void stopped(ServerStoppedEvent e) { Runtime r=runtime;runtime=null;privateTcpServer=null;if(r!=null){r.worker.shutdownNow();r.pending.clear();} }
    /** TCP binds before NeoForge loads world SERVER configs. Explicit environment is checked again at startup. */
    public static java.net.InetAddress privateTcpAddress(MinecraftServer server,java.net.InetAddress declaredAddress) {
        privateTcpServer=null;
        if (server.isDedicatedServer() && "TLS_GATEWAY".equals(System.getenv("CCEC_AUTH_TRANSPORT"))) {
            try { var address=java.net.InetAddress.getByAddress(new byte[]{127,0,0,1});privateTcpServer=server;return address; }
            catch(java.net.UnknownHostException impossible){throw new IllegalStateException("Private TCP binding failed");}
        }
        return declaredAddress;
    }
    public static boolean required() { return runtime!=null; }
    public static boolean reserve(GameProfile profile, Connection connection) {
        Runtime r=runtime;
        return r==null || (r.ready && r.registry.reserve(profile.getId(),connection)!=null);
    }
    public static boolean verified(GameProfile profile, Connection connection) {
        Runtime r=runtime; return r==null || r.registry.identity(profile.getId(),connection)!=null;
    }
    private static void tasks(RegisterConfigurationTasksEvent e) {
        Runtime r=runtime; if(r==null) return;
        if(!(e.getListener() instanceof ServerConfigurationPacketListenerImpl listener)) throw new IllegalStateException("Unexpected auth listener");
        e.register(new ICustomConfigurationTask() {
            public ConfigurationTask.Type type(){return AUTH_TASK;}
            public void run(Consumer<CustomPacketPayload> sender) {
                var connection=listener.getConnection(); var profile=listener.getOwner();
                var attempt=r.registry.attempt(profile.getId(),connection);
                if(!r.ready || !listener.hasChannel(GameAuthPayloads.Challenge.TYPE) || !listener.hasChannel(GameAuthPayloads.Proof.TYPE)
                        || !r.registry.challenge(attempt)) {listener.disconnect(Component.literal("Нужен авторизованный RE:CREATED Launcher"));return;}
                byte[] nonce=new byte[32];RANDOM.nextBytes(nonce);
                var challenge=new GameAuthPayloads.Challenge(r.serverId,r.bootId,UUID.randomUUID(),Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
                r.pending.put(connection,new Pending(attempt,challenge,profile)); sender.accept(challenge);
            }
        });
    }
    private static void proof(GameAuthPayloads.Proof proof, IPayloadContext context) {
        Runtime r=runtime;
        Pending p=r==null ? null : r.pending.get(context.connection());
        if(p==null || !p.challenge.equals(proof.challenge()) || !r.registry.beginConsume(p.attempt)) {
            context.disconnect(Component.literal("Вход не разрешён"));return;
        }
        try { r.worker.execute(()-> {
            ConnectionAdmissionRegistry.Verified identity=null;
            try {
                JsonObject result=r.backend.post("api/internal/game/sessions/consume",Map.of("gameSessionId",proof.gameSessionId(),"secret",proof.secret(),
                        "serverId",r.serverId,"bootId",r.bootId,"connectionId",p.challenge.connectionId(),"nonce",p.challenge.nonce(),
                        "minecraftUuid",p.profile.getId(),"joinName",p.profile.getName()));
                UUID mc=UUID.fromString(result.get("minecraftUuid").getAsString());
                String name=result.get("joinName").getAsString(),display=result.get("currentName").getAsString();
                if(!mc.equals(p.profile.getId()) || !name.equals(p.profile.getName()) || display.length()>16) throw new IllegalStateException("Invalid decision");
                identity=new ConnectionAdmissionRegistry.Verified(UUID.fromString(result.get("platformUserId").getAsString()),mc,name,display);
            } catch(Exception ignored) { /* Fail closed; never log request/proof or backend body. */ }
            var decision=identity;
            r.server.execute(()-> {
                r.pending.remove(context.connection(),p);
                if(runtime!=r || decision==null || !r.registry.complete(p.attempt,decision)) {r.registry.reject(p.attempt);return;}
                context.finishCurrentTask(AUTH_TASK);
            });
        }); } catch(RejectedExecutionException full){r.registry.reject(p.attempt);}
    }
    private record Pending(ConnectionAdmissionRegistry.Attempt<Connection> attempt,GameAuthPayloads.Challenge challenge,GameProfile profile) {}
    private static final class Runtime {
        final MinecraftServer server; final String serverId; final UUID bootId=UUID.randomUUID(); final GameBackendClient backend;
        final Map<Connection,Pending> pending=new ConcurrentHashMap<>();
        final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Recreated admission");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        final ConnectionAdmissionRegistry<Connection> registry=new ConnectionAdmissionRegistry<>(new ConnectionAdmissionRegistry.Transport<>() {
            public boolean alive(Connection c){return c.isConnected();}
            public void disconnect(Connection c){pending.remove(c);c.disconnect(Component.literal("Разрешение входа истекло или недоступно"));}
        },System::nanoTime,128,Duration.ofSeconds(120),Duration.ofSeconds(30));
        volatile boolean ready,registering; volatile long retryAt;
        Runtime(MinecraftServer server,String id,GameBackendClient backend){this.server=server;serverId=id;this.backend=backend;}
        synchronized void registerBoot(){
            if(registering) return;registering=true;
            worker.execute(()-> {try {var result=backend.post("api/internal/game/boot",Map.of("serverId",serverId,"bootId",bootId));ready=bootId.toString().equals(result.get("bootId").getAsString());}
                catch(Exception ignored){ready=false;}finally{retryAt=System.nanoTime()+Duration.ofSeconds(10).toNanos();registering=false;}});
        }
    }
}
