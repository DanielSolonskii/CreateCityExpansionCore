package com.example.examplemod.knowledge;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.examplemod.knowledge.KnowledgeSyncNetwork.*;

/** All session mutation happens on the server thread; disk work has a bounded executor. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class KnowledgeSyncServer {
    private static MinecraftServer owner;
    private static ExecutorService io;
    private static KnowledgeBundle.Snapshot snapshot;
    private static CompletableFuture<KnowledgeBundle.Snapshot> building;
    private static CommandSourceStack feedback;
    private static UUID library;
    private static int globalRate=256*1024, playerRate=64*1024;
    private static KnowledgeBundle.Limiter global;
    private static final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private static final Set<UUID> waiting=new LinkedHashSet<>(), welcomed=new HashSet<>();
    private static int cursor;
    private static String failure;
    private static Path store(){return FMLPaths.GAMEDIR.get().resolve(".ccec-knowledge-server-cache");}
    private static final class Session {
        final UUID id=UUID.randomUUID();final KnowledgeBundle.Snapshot version;
        final Map<String,KnowledgeBundle.Entry> blobs;
        final KnowledgeBundle.Limiter limiter=new KnowledgeBundle.Limiter(playerRate,System.nanoTime());
        final Set<String> requested=new HashSet<>();
        String file="";int offset;boolean manifest=true;KnowledgeBundle.Entry entry;
        CompletableFuture<byte[]> pending;
        Session(KnowledgeBundle.Snapshot version){this.version=version;this.blobs=version.catalog().blobs();}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        owner=event.getServer();
        if(!owner.isSingleplayer())reload(owner,null);
    }
    public static boolean reload(MinecraftServer server,CommandSourceStack source) {
        if(building!=null)return false;
        if(io==null)io=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"CCEC knowledge files");t.setDaemon(true);return t;});
        owner=server;feedback=source;
        building=CompletableFuture.supplyAsync(()->{
            try {
                Properties settings=new Properties();Path path=FMLPaths.CONFIGDIR.get().resolve("ccec-knowledge.properties");
                if(Files.isRegularFile(path))try(var in=Files.newInputStream(path)){settings.load(in);}
                String saved=settings.getProperty("libraryId");
                UUID id=saved==null?UUID.randomUUID():UUID.fromString(saved);
                int all=parseRate(settings,"globalKiBPerSecond",256),per=parseRate(settings,"playerKiBPerSecond",64);
                settings.setProperty("libraryId",id.toString());settings.setProperty("globalKiBPerSecond",String.valueOf(all));settings.setProperty("playerKiBPerSecond",String.valueOf(per));
                Files.createDirectories(path.getParent());try(var out=Files.newOutputStream(path)){settings.store(out,"Knowledge background transfer. Restart/reload applies rates. Keep libraryId stable.");}
                KnowledgeBundle.pruneSnapshot(store(),snapshot==null?Set.of():snapshot.sources().keySet());
                var built=KnowledgeBundle.freeze(KnowledgeBundle.build(KnowledgeStorage.folder(),id),store());
                library=id;globalRate=all*1024;playerRate=per*1024;
                return built;
            } catch(Exception e){throw new CompletionException(e);}
        },io);
        return true;
    }
    private static int parseRate(Properties p,String key,int fallback) {
        int value=Integer.parseInt(p.getProperty(key,String.valueOf(fallback)));
        if(value<16 || value>4096)throw new IllegalArgumentException(key+" must be 16..4096 KiB/s");return value;
    }
    public static void request(ServerPlayer player,Request request) {
        MinecraftServer server=player.getServer();
        if(server==null || server.isSingleplayerOwner(player.getGameProfile()))return;
        UUID id=player.getUUID();
        if(request.kind()==HELLO) {
            if(!welcomed.add(id))return; // A connection may initiate only once, after entering the world.
            waiting.add(id);
            if(snapshot==null && building==null && failure==null)reload(server,null);
            return;
        }
        Session session=sessions.get(id);
        if(session==null || !session.id.equals(request.session()))return;
        if(request.kind()==FINISH || request.kind()==CANCEL){sessions.remove(id);return;}
        if(request.kind()!=GET || session.manifest || session.entry!=null || !KnowledgeBundle.validHash(request.hash()))return;
        var entry=session.blobs.get(request.hash());
        if(entry==null || !session.requested.add(request.hash()))return;
        session.entry=entry;session.file=entry.hash();session.offset=0;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server=event.getServer();if(server!=owner)return;
        if(building!=null && building.isDone()) {
            try {
                snapshot=building.join();failure=null;
                global=new KnowledgeBundle.Limiter(globalRate,System.nanoTime());
                sessions.clear();waiting.addAll(welcomed);
                if(feedback!=null)feedback.sendSuccess(()->Component.literal("База знаний обновлена: "+snapshot.articles().size()+" статей, "+snapshot.catalog().files().size()+" файлов."),false);
            } catch(Exception e) {
                failure="Не удалось обновить базу знаний. Администратору следует проверить журнал сервера.";
                LogUtils.getLogger().warn("Knowledge reload failed; keeping last published revision",e);
                if(feedback!=null)feedback.sendFailure(Component.literal(failure));
            } finally {building=null;feedback=null;}
        }
        for(UUID id:new ArrayList<>(waiting)) {
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if(player==null){waiting.remove(id);continue;}
            if(snapshot==null) {
                if(failure!=null){send(player,new Data(NONE,ERROR,failure,0,0,new byte[0]));waiting.remove(id);}continue;
            }
            Session s=new Session(snapshot);sessions.put(id,s);waiting.remove(id);
            KnowledgeBaseServer.grantDefaults(player,snapshot.articles());KnowledgeBaseServer.sync(player);
            send(player,new Data(s.id,BEGIN,library.toString(),0,snapshot.manifest().length,new byte[0]));
        }
        if(global==null || sessions.isEmpty())return;
        long now=System.nanoTime();global.refill(now);
        List<UUID> order=new ArrayList<>(sessions.keySet());
        for(int i=0;i<order.size();i++) {
            UUID id=order.get(Math.floorMod(cursor+i,order.size()));Session s=sessions.get(id);
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if(player==null){sessions.remove(id);continue;}
            s.limiter.refill(now);
            if(!s.manifest && s.entry==null)continue;
            int total=s.manifest?s.version.manifest().length:s.entry.size();
            int length=Math.min(KnowledgeBundle.CHUNK,total-s.offset),cost=length+256;
            if(!global.canSend(cost) || !s.limiter.canSend(cost))continue;
            try {
                byte[] bytes;
                if(s.manifest)bytes=Arrays.copyOfRange(s.version.manifest(),s.offset,s.offset+length);
                else {
                    if(s.pending==null) {
                        int offset=s.offset;Path path=s.version.sources().get(s.file);
                        s.pending=CompletableFuture.supplyAsync(()->{
                            try(var channel=FileChannel.open(path,StandardOpenOption.READ)) {
                                if(channel.size()!=total)throw new IOException("Library changed; run /ccec knowledge reload");
                                ByteBuffer buffer=ByteBuffer.allocate(length);
                                while(buffer.hasRemaining())if(channel.read(buffer,offset+buffer.position())<0)throw new EOFException();
                                return buffer.array();
                            } catch(IOException ex){throw new CompletionException(ex);}
                        },io);
                        continue;
                    }
                    if(!s.pending.isDone())continue;bytes=s.pending.join();s.pending=null;
                }
                send(player,new Data(s.id,s.manifest?MANIFEST:FILE,s.file,s.offset,total,bytes));
                global.take(cost);s.limiter.take(cost);s.offset+=length;
                if(s.offset==total){s.manifest=false;s.entry=null;s.offset=0;}
            } catch(Exception e) {
                sessions.remove(id);send(player,new Data(s.id,ERROR,"Ошибка чтения базы знаний. Требуется /ccec knowledge reload.",0,0,new byte[0]));
                LogUtils.getLogger().warn("Knowledge transfer failed",e);
            }
        }
        cursor++;
    }
    private static void send(ServerPlayer p,Data d){PacketDistributor.sendToPlayer(p,d);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id=e.getEntity().getUUID();sessions.remove(id);waiting.remove(id);welcomed.remove(id);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if(event.getServer()!=owner)return;
        if(io!=null)io.shutdownNow();io=null;building=null;feedback=null;owner=null;snapshot=null;failure=null;
        sessions.clear();waiting.clear();welcomed.clear();global=null;
    }
}
