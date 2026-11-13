package com.example.examplemod.knowledge;

import com.example.examplemod.client.screen.KnowledgeBaseScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.example.examplemod.knowledge.KnowledgeSyncNetwork.*;

@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class KnowledgeSyncClient {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"CCEC knowledge cache");t.setDaemon(true);return t;});
    private static final AtomicInteger queued=new AtomicInteger();
    private static volatile long generation,lastActivity;
    private static volatile boolean ready,failed;
    private static boolean hello;
    private static volatile String message="Ожидание загрузки базы знаний…";
    private static Path active;
    // The following transfer fields belong exclusively to IO.
    private static UUID session;
    private static String serverKey;
    private static int manifestSize;
    private static ByteArrayOutputStream buffer;
    private static KnowledgeBundle.Catalog catalog;
    private static KnowledgeBundle.Cache cache;
    private static List<KnowledgeBundle.Entry> missing;
    private static int next;

    public static boolean local(){return Minecraft.getInstance().isLocalServer();}
    public static Path cacheRoot(){return FMLPaths.GAMEDIR.get().resolve(".ccec-knowledge-cache");}
    public static Path activeRoot(){return active;}
    public static boolean ready(){return local() || ready;}
    public static boolean allowOpen() {
        if(ready())return true;
        var player=Minecraft.getInstance().player;
        if(player!=null)player.displayClientMessage(Component.literal(message),true);
        return false;
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){reset();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    private static void reset() {
        generation++;hello=false;ready=false;failed=false;active=null;lastActivity=System.nanoTime();
        message="Ожидание загрузки базы знаний…";KnowledgeBaseClient.invalidate(true);KnowledgeImages.clear();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(local() || mc.player==null || mc.level==null || mc.getConnection()==null)return;
        if(!hello && mc.player.tickCount>=60 && mc.getOverlay()==null) {
            hello=true;lastActivity=System.nanoTime();PacketDistributor.sendToServer(new Request(NONE,HELLO,""));
        }
        if(hello && !ready && !failed && System.nanoTime()-lastActivity>120_000_000_000L)
            fail(generation,new IOException("Нет ответа сервера. Перезайдите или попросите администратора обновить базу."));
    }
    public static void accept(Data packet) {
        if(local() || Minecraft.getInstance().getConnection()==null)return;
        if(packet.kind()==BEGIN) {
            generation++;ready=false;failed=false;active=null;lastActivity=System.nanoTime();
            message="Проверка локальной базы знаний…";
            if(Minecraft.getInstance().screen instanceof KnowledgeBaseScreen)Minecraft.getInstance().setScreen(null);
            KnowledgeBaseClient.invalidate(false);KnowledgeImages.clear();
        }
        if(failed)return;
        long epoch=generation;lastActivity=System.nanoTime();
        String address=Minecraft.getInstance().getCurrentServer()==null?"remote":Minecraft.getInstance().getCurrentServer().ip;
        if(queued.incrementAndGet()>128){queued.decrementAndGet();fail(epoch,new IOException("Очередь загрузки переполнена."));return;}
        IO.execute(()->{
            try {if(epoch==generation && !failed)receive(packet,epoch,address);}
            catch(Exception e){fail(epoch,e);}finally{queued.decrementAndGet();}
        });
    }
    private static void receive(Data p,long epoch,String address) throws IOException {
        if(p.kind()==ERROR)throw new IOException(p.name());
        if(p.kind()==BEGIN) {
            UUID library=UUID.fromString(p.name());
            if(p.total()<1 || p.total()>KnowledgeBundle.MAX_MANIFEST)throw new IOException("Некорректный размер каталога.");
            session=p.session();serverKey=KnowledgeBundle.hash((address+"\n"+library).getBytes(StandardCharsets.UTF_8));
            manifestSize=p.total();catalog=null;missing=null;next=0;buffer=new ByteArrayOutputStream();
            cache=new KnowledgeBundle.Cache(KnowledgeBundle.resolve(cacheRoot(),serverKey));return;
        }
        if(session==null || !session.equals(p.session()))return;
        if(p.offset()!=buffer.size() || p.bytes().length>KnowledgeBundle.CHUNK || p.offset()<0
                || (long)p.offset()+p.bytes().length>p.total())throw new IOException("Нарушен порядок частей файла.");
        if(p.kind()==MANIFEST) {
            if(catalog!=null || p.total()!=manifestSize)throw new IOException("Некорректный каталог.");
            buffer.write(p.bytes());if(buffer.size()!=manifestSize)return;
            catalog=KnowledgeBundle.decode(buffer.toByteArray());
            String expected=KnowledgeBundle.hash((address+"\n"+catalog.library()).getBytes(StandardCharsets.UTF_8));
            if(!serverKey.equals(expected))throw new IOException("Идентификатор базы изменился.");
            KnowledgeBundle.trimCaches(cacheRoot(),cache.root(),128L*1024*1024);
            missing=cache.missing(catalog,KnowledgeStorage.folder());next=0;advance(epoch);return;
        }
        if(p.kind()!=FILE || missing==null || next>=missing.size())throw new IOException("Неожиданный файл.");
        var expected=missing.get(next);
        if(!p.name().equals(expected.hash()) || p.total()!=expected.size())throw new IOException("Получен другой файл.");
        buffer.write(p.bytes());if(buffer.size()!=expected.size())return;
        cache.store(expected,buffer.toByteArray());next++;advance(epoch);
    }
    private static void advance(long epoch) throws IOException {
        buffer=new ByteArrayOutputStream();
        if(next<missing.size()) {
            message="База знаний загружается: "+next+" из "+missing.size()+" файлов. Попробуйте открыть её позже.";
            send(epoch,new Request(session,GET,missing.get(next).hash()));
        } else {
            message="Подготовка локальной базы знаний…";
            Path installed=cache.activate(catalog);UUID done=session;
            KnowledgeBundle.trimCaches(cacheRoot(),cache.root(),256L*1024*1024);
            Minecraft.getInstance().execute(()->{
                if(epoch!=generation || local() || failed)return;
                active=installed;ready=true;KnowledgeBaseClient.invalidate(false);
                PacketDistributor.sendToServer(new Request(done,FINISH,""));
            });
        }
    }
    private static void send(long epoch,Request request) {
        Minecraft.getInstance().execute(()->{
            if(epoch==generation && !local() && !failed && Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(request);
        });
    }
    private static void fail(long epoch,Exception e) {
        Minecraft.getInstance().execute(()->{
            if(epoch!=generation || local())return;
            failed=true;ready=false;message="Не удалось загрузить базу знаний. Перезайдите или обратитесь к администратору.";
            if(session!=null && Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new Request(session,CANCEL,""));
            LogUtils.getLogger().warn("Knowledge synchronization failed",e);
        });
    }
}
