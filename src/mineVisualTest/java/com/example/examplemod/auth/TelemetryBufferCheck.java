package com.example.examplemod.auth;
import java.util.*;
public final class TelemetryBufferCheck {
    public static void main(String[] args)throws Exception{
        var queue=new TelemetryBuffer<Integer>(3);
        if(!queue.offer(1)||!queue.offer(2)||!queue.batch(1).equals(List.of(1))||!queue.offer(3)||queue.offer(4)||queue.dropped()!=1)throw new AssertionError();
        if(queue.batch(50)!=queue.batch(1))throw new AssertionError("retry must retain exact batch");
        queue.acknowledge();if(!queue.batch(50).equals(List.of(2,3)))throw new AssertionError();
        queue.acknowledge();if(!queue.batch(50).isEmpty())throw new AssertionError();
        var now=java.time.Instant.now();var batch=new ServerTelemetryBridge.Batch(UUID.randomUUID(),"test",UUID.randomUUID(),now,1,now,true,1,List.of(UUID.randomUUID()),15,"1.21.1","1.0.1","NeoForge",0,List.of());
        String wire=new com.google.gson.Gson().toJson(ServerTelemetryBridge.wire(batch));
        if(!wire.contains(now.toString())||!wire.contains("\"maxPlayers\":15"))throw new AssertionError("wire serialization");
        var received=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var fake=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        fake.createContext("/api/internal/game/telemetry",exchange->{
            if(!"test-only-independent-game-service-secret-32".equals(exchange.getRequestHeaders().getFirst("X-Game-Service-Secret")))throw new AssertionError();
            received.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            var response=("{\"id\":\""+batch.id()+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });fake.start();
        try(var worker=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var client=new GameBackendClient("http://127.0.0.1:"+fake.getAddress().getPort(),"test-only-independent-game-service-secret-32");
            var sending=worker.submit(()->client.post("api/internal/game/telemetry",ServerTelemetryBridge.wire(batch)));
            if(!received.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("no transport");
            // Producer remains independent while Backend response is blocked.
            queue.offer(9);if(!queue.batch(1).equals(List.of(9)))throw new AssertionError();
            release.countDown();if(!sending.get(3,java.util.concurrent.TimeUnit.SECONDS).get("id").getAsString().equals(batch.id().toString()))throw new AssertionError();
        }finally{release.countDown();fake.stop(0);}
        var poison=new TelemetryBuffer<Integer>(5);poison.offer(1);poison.offer(2);poison.batch(50);poison.offer(3);
        if(ServerTelemetryBridge.rejectPermanent(poison,new java.io.IOException("offline")))throw new AssertionError("network failure must retry");
        if(!poison.batch(50).equals(List.of(1,2)))throw new AssertionError("retry lost pending events");
        if(!ServerTelemetryBridge.rejectPermanent(poison,new GameBackendClient.InvalidTelemetry())||poison.dropped()!=2||!poison.batch(50).equals(List.of(3)))throw new AssertionError("poison batch must not block successors");
        var status=new java.util.concurrent.atomic.AtomicInteger(400);var code=new java.util.concurrent.atomic.AtomicReference<>("INVALID_TELEMETRY");
        var errors=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        errors.createContext("/",exchange->{var body=("{\"code\":\""+code.get()+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),body.length);exchange.getResponseBody().write(body);exchange.close();});errors.start();
        try{
            var client=new GameBackendClient("http://127.0.0.1:"+errors.getAddress().getPort(),"test-only-independent-game-service-secret-32");
            for(int http:new int[]{400,401,403,409,429,500}){
                status.set(http);
                try{client.post("api/internal/game/telemetry",Map.of());throw new AssertionError("error accepted");}
                catch(GameBackendClient.InvalidTelemetry invalid){if(http!=400)throw new AssertionError("retryable failure discarded");}
                catch(IllegalStateException retryable){if(http==400)throw new AssertionError("validation rejection not classified");}
            }
            status.set(400);code.set("OTHER_ERROR");
            try{client.post("api/internal/game/telemetry",Map.of());throw new AssertionError();}
            catch(GameBackendClient.InvalidTelemetry invalid){throw new AssertionError("unknown error discarded");}catch(IllegalStateException expected){}
            code.set("INVALID_TELEMETRY");
            try{client.post("api/internal/game/consume",Map.of());throw new AssertionError();}
            catch(GameBackendClient.InvalidTelemetry invalid){throw new AssertionError("4B semantics changed");}catch(IllegalStateException expected){}
        }finally{errors.stop(0);}
        System.out.println("PASS bounded telemetry queue, immutable retry, overflow accounting and ACK");
    }
}
