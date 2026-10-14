package com.example.examplemod.auth;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Separate reporting feature; no changes to admission, gameplay or player profiles. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class CcecReportCommands {
    private static final Map<MinecraftServer,Worker> workers=new ConcurrentHashMap<>();
    private CcecReportCommands(){}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("report").then(Commands.argument("text",StringArgumentType.greedyString()).executes(ctx->{
            var player=ctx.getSource().getPlayerOrException();String text=StringArgumentType.getString(ctx,"text").strip();
            if(player instanceof FakePlayer || !ReportLimits.valid(text)){player.sendSystemMessage(Component.literal("Репорт: от 5 до 1000 символов, без управляющих символов."));return 0;}
            GameBackendClient backend;
            try {backend=new GameBackendClient(AuthenticationServerConfig.BACKEND.get(),System.getenv("CCEC_BACKEND_SECRET"));}
            catch(RuntimeException missing){player.sendSystemMessage(Component.literal("Репорты сейчас недоступны. Попробуйте позже."));return 0;}
            var server=ctx.getSource().getServer();Worker worker=workers.computeIfAbsent(server,s->new Worker());
            if(!worker.limits.allow(player.getUUID())){player.sendSystemMessage(Component.literal("Подождите минуту перед следующим репортом."));return 0;}
            UUID id=UUID.randomUUID();
            var body=Map.of("commandId",id,"minecraftUuid",player.getUUID(),"serverId",AuthenticationServerConfig.SERVER_ID.get(),
                "text",text,"dimension",player.level().dimension().location().toString(),"x",player.getX(),"y",player.getY(),"z",player.getZ(),"occurredAt",Instant.now().toString());
            try {worker.pool.execute(()->{
                boolean ok=false;
                for(int attempt=0;attempt<2 && !Thread.currentThread().isInterrupted();attempt++){
                    try {var response=backend.post("api/internal/game/reports",body);ok=id.toString().equals(response.get("id").getAsString());break;}
                    catch(Exception ignored){ /* No body, player text or credentials in logs. Same id retries only. */ }
                }
                final boolean accepted=ok;
                server.execute(()->{if(player.connection!=null && player.connection.getConnection().isConnected())player.sendSystemMessage(Component.literal(accepted ? "Репорт принят. ID: "+id : "Не удалось подтвердить приём репорта. Попробуйте позже; сообщение могло быть сохранено."));});
            });}catch(RejectedExecutionException full){player.sendSystemMessage(Component.literal("Сервис репортов занят. Попробуйте позже."));return 0;}
            return 1;
        })));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){var worker=workers.remove(event.getServer());if(worker!=null)worker.pool.shutdownNow();}
    private static final class Worker {
        final ReportLimits limits=new ReportLimits(System::nanoTime);
        final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"CCEC report delivery");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
}
