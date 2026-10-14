package com.example.examplemod.auth;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Real modded client/TLS/CONFIGURATION spike against an isolated local server and fake authority. */
public final class NeoForgeTlsClientSpike {
    private static final String SERVICE="local-spike-only-not-a-production-service-secret";
    private static final String NAME="cs12345678901234";
    private static final String SECRET="s".repeat(43);
    private static final UUID TICKET=UUID.randomUUID(),USER=UUID.randomUUID();
    public static void main(String[] args)throws Exception {
        Path fixture=Path.of(args[0]).toAbsolutePath(),installation=Path.of(args[1]).toAbsolutePath();
        Path java=installation.resolve("runtime/java-21/bin/java.exe"),client=fixture.resolveSibling("client");
        Path key=fixture.resolveSibling("spike-tls-public-port.p12");
        Path properties=fixture.resolve("server.properties");byte[] originalProperties=Files.readAllBytes(properties);
        Files.createDirectories(client.resolve("mods"));
        try(var files=Files.list(installation.resolve("instances/alpha/mods"))){for(Path file:files.filter(Files::isRegularFile).toList()){
            if(!file.getFileName().toString().startsWith("createcityexpansioncore"))Files.copy(file,client.resolve("mods").resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);
        }}
        Files.copy(fixture.resolve("mods/createcityexpansioncore-1.0.1.jar"),client.resolve("mods/createcityexpansioncore-1.0.1.jar"),StandardCopyOption.REPLACE_EXISTING);
        if(!Files.exists(key)){
            Process cert=new ProcessBuilder(installation.resolve("runtime/java-21/bin/keytool.exe").toString(),"-genkeypair","-alias","fixture","-keystore",key.toString(),"-storetype","PKCS12","-storepass","test-only-password","-keypass","test-only-password","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.2").redirectErrorStream(true).redirectOutput(fixture.resolve("spike-keytool.log").toFile()).start();
            if(!cert.waitFor(20,TimeUnit.SECONDS) || cert.exitValue()!=0)throw new AssertionError("fixture certificate creation failed");
        }
        var store=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(key)){store.load(input,"test-only-password".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,"test-only-password".toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(store);
        var tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        var gateway=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(25585,4,InetAddress.getByName("127.0.0.2"));
        var active=new AtomicBoolean(true);var seen=new AtomicReference<JsonObject>();var consumed=new AtomicBoolean();
        var accepted=new CompletableFuture<Void>();var connected=new CompletableFuture<Void>();var disconnected=new AtomicBoolean();var udp=new CompletableFuture<Void>();
        var gatewayThread=Thread.ofVirtual().start(()->{
            while(active.get())try{
                SSLSocket socket=(SSLSocket)gateway.accept();
                Thread.ofVirtual().start(()->{
                    try(socket;var raw=new Socket("127.0.0.1",25585)){
                        socket.setSoTimeout(10000);socket.startHandshake();socket.setSoTimeout(0);
                        var up=Thread.ofVirtual().start(()->copy(socket,raw));copy(raw,socket);up.join(1000);
                    }catch(Exception ignored){}
                });
            }catch(IOException ignored){break;}
        });
        var backend=HttpServer.create(new InetSocketAddress("127.0.0.1",18087),4);
        backend.createContext("/api/internal/game/",exchange->{
            var input=JsonParser.parseString(new String(exchange.getRequestBody().readNBytes(4097),StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject response=new JsonObject();int status=401;
            if(SERVICE.equals(exchange.getRequestHeaders().getFirst("X-Game-Service-Secret"))){
                if(exchange.getRequestURI().getPath().endsWith("/boot")){response.add("bootId",input.get("bootId"));status=200;}
                else{
                    JsonObject c=seen.get();boolean tuple=c!=null;
                    for(String field:List.of("serverId","bootId","connectionId","nonce"))tuple=tuple && Objects.equals(c.get(field),input.get(field));
                    UUID mc=UUID.nameUUIDFromBytes(("OfflinePlayer:"+NAME).getBytes(StandardCharsets.UTF_8));
                    if(tuple && TICKET.toString().equals(input.get("gameSessionId").getAsString()) && SECRET.equals(input.get("secret").getAsString())
                            && mc.toString().equals(input.get("minecraftUuid").getAsString()) && NAME.equals(input.get("joinName").getAsString()) && consumed.compareAndSet(false,true)){
                        status=200;response.addProperty("platformUserId",USER.toString());response.addProperty("minecraftUuid",mc.toString());response.addProperty("joinName",NAME);response.addProperty("currentName","Fixture");accepted.complete(null);
                    }
                }
            }
            byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });backend.start();
        Process server=null,game=null;Thread serverReader=null,gameReader=null;
        try{
            Files.writeString(properties,new String(originalProperties,StandardCharsets.UTF_8).replace("server-ip=127.0.0.1","server-ip=0.0.0.0"));
            var serverBuilder=new ProcessBuilder(java.toString(),"-Xms256m","-Xmx2G","-XX:ActiveProcessorCount=4","@libraries/net/neoforged/neoforge/21.1.251/win_args.txt","--nogui").directory(fixture.toFile()).redirectErrorStream(true);
            serverBuilder.environment().put("CCEC_BACKEND_SECRET",SERVICE);serverBuilder.environment().put("CCEC_AUTH_TRANSPORT","TLS_GATEWAY");server=serverBuilder.start();
            var ready=new CompletableFuture<Void>();var process=server;
            serverReader=new Thread(()->{try(var in=process.inputReader();var out=Files.newBufferedWriter(fixture.resolve("tls-server-console.log"))){String line;while((line=in.readLine())!=null){out.write(line);out.newLine();out.flush();if(line.contains("Done ("))ready.complete(null);if(line.contains(NAME+"[") && line.contains(" logged in with entity id"))connected.complete(null);if(line.contains(NAME) && line.contains("lost connection"))disconnected.set(true);if(line.contains("UDP authentication complete with"))udp.complete(null);}}catch(Exception e){ready.completeExceptionally(e);}},"TLS server log");serverReader.start();
            ready.get(120,TimeUnit.SECONDS);Thread.sleep(1000);
            // Same standard Java endpoint-identification policy as CCEC; wrong hostname fails closed.
            try(var socket=new Socket("127.0.0.2",25585);var bad=(SSLSocket)tls.getSocketFactory().createSocket(socket,"incorrect.invalid",25585,true)){
                var parameters=bad.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");bad.setSSLParameters(parameters);
                try{bad.startHandshake();throw new AssertionError("wrong TLS hostname accepted");}catch(SSLHandshakeException expected){}
            }
            var manifest=JsonParser.parseString(Files.readString(installation.resolve("launch.json"))).getAsJsonObject();
            var libraries=new ArrayList<String>();for(var artifact:manifest.getAsJsonArray("libraries"))libraries.add(installation.resolve(artifact.getAsJsonObject().get("path").getAsString()).toString());
            Map<String,String> vars=new HashMap<>();vars.put("classpath",String.join(";",libraries));vars.put("classpath_separator",";");vars.put("library_directory",installation.resolve("libraries").toString());
            vars.put("natives_directory",client.resolve("natives").toString());vars.put("game_directory",client.toString());vars.put("assets_root",installation.resolve("assets").toString());vars.put("assets_index_name","17");
            vars.put("launcher_name","RecreatedAuthSpike");vars.put("launcher_version","test");vars.put("version_name","neoforge-21.1.251");vars.put("version_type","release");vars.put("auth_player_name",NAME);vars.put("auth_uuid",UUID.nameUUIDFromBytes(("OfflinePlayer:"+NAME).getBytes(StandardCharsets.UTF_8)).toString());
            for(String v:List.of("auth_access_token","clientid","auth_xuid"))vars.put(v,"0");vars.put("user_type","legacy");
            var arguments=new ArrayList<String>(List.of("-Xms256m","-Xmx3G","-XX:ActiveProcessorCount=4","-Drecreated.game.tlsHost=127.0.0.2","-Drecreated.game.tlsPort=25585","-Drecreated.auth.spike=true","-Djavax.net.ssl.trustStore="+key,"-Djavax.net.ssl.trustStorePassword=test-only-password"));
            for(JsonElement v:manifest.getAsJsonArray("jvm"))arguments.add(replace(v.getAsString(),vars));arguments.add(replace(manifest.get("logging").getAsString(),vars));arguments.add(manifest.get("mainClass").getAsString());
            for(JsonElement v:manifest.getAsJsonArray("game"))arguments.add(replace(v.getAsString(),vars));arguments.addAll(List.of("--quickPlayMultiplayer","127.0.0.2:25585"));
            Path argfile=client.resolve("spike.args");Files.write(argfile,arguments.stream().map(s->"\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"").toList());
            game=new ProcessBuilder(java.toString(),"@"+argfile).directory(client.toFile()).redirectErrorStream(true).start();
            var child=game;var remote=new AtomicBoolean();
            gameReader=new Thread(()->{try(var in=child.inputReader();var out=Files.newBufferedWriter(client.resolve("spike-console.log"))){String line;while((line=in.readLine())!=null){
                if(line.contains("RECREATED_TLS_REMOTE 127.0.0.2:25585"))remote.set(true);
                int offset=line.indexOf("RECREATED_AUTH_V1 ");
                if(offset>=0){var c=JsonParser.parseString(line.substring(offset+"RECREATED_AUTH_V1 ".length())).getAsJsonObject();seen.set(c);var response=new JsonObject();response.add("connectionId",c.get("connectionId"));response.addProperty("gameSessionId",TICKET.toString());response.addProperty("secret",SECRET);child.outputWriter().write(response+"\n");child.outputWriter().flush();}
                else{out.write(line);out.newLine();out.flush();}
            }}catch(Exception e){accepted.completeExceptionally(e);}},"TLS client IPC");gameReader.start();
            child.onExit().thenRun(()->{if(!accepted.isDone())accepted.completeExceptionally(new AssertionError("fixture client exited before authentication; inspect spike-console.log"));});
            accepted.get(600,TimeUnit.SECONDS);connected.get(30,TimeUnit.SECONDS);udp.get(20,TimeUnit.SECONDS);
            if(!remote.get())throw new AssertionError("remote address changed or TLS handshake evidence missing");
            if(Files.readString(client.resolve("spike-console.log")).contains(SECRET))throw new AssertionError("proof in game log");
            child.getOutputStream().close();Thread.sleep(5000);
            if(!child.isAlive() || disconnected.get())throw new AssertionError("Launcher IPC closure broke established game connection");
            System.out.println("LIVE TLS CLIENT SPIKE PASSED: hostname validation, native Minecraft channel TLS, unchanged remote address, stdin proof, CONFIGURATION consume, player created, Sable UDP authentication on unchanged port, connection survives IPC closure");
        }finally{
            if(game!=null && game.isAlive()){game.destroy();game.waitFor(10,TimeUnit.SECONDS);}
            if(server!=null && server.isAlive()){server.outputWriter().write("stop\n");server.outputWriter().flush();if(!server.waitFor(45,TimeUnit.SECONDS))server.destroy();}
            active.set(false);gateway.close();gatewayThread.join(3000);backend.stop(0);if(serverReader!=null)serverReader.join(3000);if(gameReader!=null)gameReader.join(3000);
            Files.write(properties,originalProperties);
        }
    }
    private static void copy(Socket from,Socket to){try{from.getInputStream().transferTo(to.getOutputStream());}catch(IOException ignored){}finally{try{to.close();}catch(IOException ignored){}}}
    private static String replace(String text,Map<String,String> values){for(var value:values.entrySet())text=text.replace("${"+value.getKey()+"}",value.getValue());if(text.contains("${"))throw new AssertionError("unknown launch argument");return text;}
}
