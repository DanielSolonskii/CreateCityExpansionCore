package com.example.examplemod.auth;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Explicit local runtime spike. Never connects to production, never uses account/service secrets. */
public final class NeoForgeAdmissionSpike {
    private static final String FIXTURE_SECRET="local-spike-only-not-a-production-service-secret";
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path fixture=Path.of(args[0]).toAbsolutePath();
        Path java=Path.of(args[1]).toAbsolutePath();
        var backend=HttpServer.create(new InetSocketAddress("127.0.0.1",18087),4);
        backend.createContext("/api/internal/game/boot",exchange->{
            byte[] body=exchange.getRequestBody().readNBytes(4097);
            var input=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
            boolean auth=FIXTURE_SECRET.equals(exchange.getRequestHeaders().getFirst("X-Game-Service-Secret"));
            byte[] result=("{\"bootId\":\""+input.get("bootId").getAsString()+"\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(auth?200:401,result.length);exchange.getResponseBody().write(result);exchange.close();
        });
        backend.start();
        var builder=new ProcessBuilder(java.toString(),"-Xms512m","-Xmx3G","@libraries/net/neoforged/neoforge/21.1.251/win_args.txt","--nogui");
        builder.directory(fixture.toFile()).redirectErrorStream(true);
        builder.environment().put("CCEC_BACKEND_SECRET",FIXTURE_SECRET);
        builder.environment().put("CCEC_AUTH_TRANSPORT","TLS_GATEWAY");
        for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS"))builder.environment().remove(key);
        Process server=builder.start();
        var ready=new CompletableFuture<Void>();
        var all=new StringBuffer();
        var log=fixture.resolve("spike-console.log");
        var reader=new Thread(()->{
            try(var input=server.inputReader();var output=Files.newBufferedWriter(log)){
                String line;while((line=input.readLine())!=null){output.write(line);output.newLine();output.flush();all.append(line).append('\n');if(line.contains("Done ("))ready.complete(null);}
                if(!ready.isDone())ready.completeExceptionally(new IllegalStateException("Server exited before Done; inspect fixture log"));
            }catch(Exception e){ready.completeExceptionally(e);}
        },"Local spike console");reader.start();
        try {
            ready.get(150,TimeUnit.SECONDS);Thread.sleep(1000);
            String name="cs12345678901234";
            try(Socket first=login(name)){
                var firstInput=new DataInputStream(first.getInputStream());
                require(packet(firstInput)[0]==2,"first login did not reach GameProfile");
                try(Socket second=login(name)){
                    require(packet(new DataInputStream(second.getInputStream()))[0]==0,"duplicate login was not denied before eviction");
                }
                require(!first.isClosed(),"NEW closed OLD socket");
                send(first.getOutputStream(),new byte[]{3}); // LOGIN acknowledged -> CONFIGURATION
                var ping=waitFor(firstInput,5);
                require(ping.length==5,"configuration ping missing");
                send(first.getOutputStream(),new byte[]{5,0,0,0,0}); // no advertised auth channel
                require(waitFor(firstInput,2).length>1,"missing-channel connection was not denied");
            }
            try(Socket forged=login("cs98765432109876")){
                var input=new DataInputStream(forged.getInputStream());require(packet(input)[0]==2,"forged login profile missing");
                send(forged.getOutputStream(),new byte[]{3});
                send(forged.getOutputStream(),new byte[]{3}); // forged FINISH CONFIGURATION before verified task
                require(waitFor(input,2).length>1,"early FINISH was not denied");
            }
            require(!all.toString().contains("logged in with entity id"),"unverified player created");
            System.out.println("LIVE NEOFORGE SPIKE PASSED: Done, early duplicate NEW denied, missing channel denied, forged FINISH denied, no player created");
        } finally {
            if(server.isAlive()){server.outputWriter().write("stop\n");server.outputWriter().flush();}
            if(!server.waitFor(45,TimeUnit.SECONDS)){server.destroy();server.waitFor(10,TimeUnit.SECONDS);}
            backend.stop(0);reader.join(5000);
        }
    }
    private static Socket login(String name)throws Exception{
        var socket=new Socket();socket.connect(new InetSocketAddress("127.0.0.1",25585),2000);socket.setSoTimeout(10000);
        var packet=new ByteArrayOutputStream();var data=new DataOutputStream(packet);
        varint(data,0);varint(data,767);text(data,"127.0.0.1");data.writeShort(25585);varint(data,2);send(socket.getOutputStream(),packet.toByteArray());
        packet.reset();varint(data,0);text(data,name);data.writeLong(0);data.writeLong(0);send(socket.getOutputStream(),packet.toByteArray());return socket;
    }
    private static byte[] waitFor(DataInputStream input,int id)throws Exception{
        for(int n=0;n<128;n++){byte[] p=packet(input);if(p[0]==id)return p;}throw new AssertionError("packet limit");
    }
    private static byte[] packet(DataInputStream input)throws Exception{
        int size=varint(input);if(size<1 || size>2097152)throw new AssertionError("bad packet size");byte[] data=input.readNBytes(size);require(data.length==size,"EOF");return data;
    }
    private static int varint(DataInputStream input)throws Exception{
        int value=0;for(int shift=0;shift<35;shift+=7){int b=input.readUnsignedByte();value|=(b&127)<<shift;if((b&128)==0)return value;}throw new AssertionError("bad varint");
    }
    private static void varint(DataOutputStream out,int value)throws Exception{while((value&~127)!=0){out.writeByte(value&127|128);value>>>=7;}out.writeByte(value);}
    private static void text(DataOutputStream out,String value)throws Exception{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);varint(out,bytes.length);out.write(bytes);}
    private static void send(OutputStream out,byte[] data)throws Exception{var packet=new DataOutputStream(out);varint(packet,data.length);out.write(data);out.flush();}
}
