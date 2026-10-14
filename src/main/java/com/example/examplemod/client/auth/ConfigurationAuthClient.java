package com.example.examplemod.client.auth;

import com.example.examplemod.auth.GameAuthPayloads;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Public stdout challenge -> private child stdin proof. No reusable Launcher credential in Minecraft. */
public final class ConfigurationAuthClient {
    public static final String PREFIX="RECREATED_AUTH_V1 ";
    private static final Map<UUID,Waiter> WAITERS=new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTED=new AtomicBoolean();
    private ConfigurationAuthClient() {}
    public static void challenge(GameAuthPayloads.Challenge challenge,IPayloadContext context) {
        if(!GameTlsTransport.protectedConnection(context.connection()) || WAITERS.size()>=4) {
            context.disconnect(Component.literal("Нужен защищённый запуск через RE:CREATED Launcher"));return;
        }
        startReader();
        var future=new CompletableFuture<GameAuthPayloads.Proof>();
        var waiter=new Waiter(challenge,future);
        if(WAITERS.putIfAbsent(challenge.connectionId(),waiter)!=null){context.disconnect(Component.literal("Повторный запрос входа"));return;}
        future.orTimeout(25,TimeUnit.SECONDS).whenComplete((proof,error)-> {
            WAITERS.remove(challenge.connectionId(),waiter);
            context.enqueueWork(()-> {
                if(error!=null || proof==null || !context.connection().isConnected() || !GameTlsTransport.protectedConnection(context.connection())) {
                    context.disconnect(Component.literal("Разрешение входа недоступно. Откройте Launcher и повторите подключение."));return;
                }
                context.reply(proof);
            });
        });
        System.out.println(PREFIX+new Gson().toJson(challenge));System.out.flush();
    }
    private static void startReader() {
        if(!STARTED.compareAndSet(false,true)) return;
        var worker=new Thread(()-> {
            try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
                while(true) {
                    var line=new StringBuilder();int next;
                    while((next=reader.read())!=-1 && next!='\n') {
                        if(line.length()>=4096) throw new IllegalStateException("IPC input limit");
                        if(next!='\r') line.append((char)next);
                    }
                    if(next==-1) break;
                    try {
                        var body=JsonParser.parseString(line.toString()).getAsJsonObject();
                        var id=UUID.fromString(body.get("connectionId").getAsString());
                        var waiter=WAITERS.get(id);if(waiter==null) continue;
                        var proof=new GameAuthPayloads.Proof(waiter.challenge,UUID.fromString(body.get("gameSessionId").getAsString()),body.get("secret").getAsString());
                        waiter.future.complete(proof);
                    } catch(Exception ignored){ /* Input errors must not echo secrets. */ }
                }
            } catch(Exception ignored) { /* No credentials in exceptions/logs. */ }
            finally{WAITERS.values().forEach(w->w.future.completeExceptionally(new IllegalStateException("Launcher IPC closed")));WAITERS.clear();}
        },"Recreated private proof input");worker.setDaemon(true);worker.start();
    }
    private record Waiter(GameAuthPayloads.Challenge challenge,CompletableFuture<GameAuthPayloads.Proof> future) {}
}
