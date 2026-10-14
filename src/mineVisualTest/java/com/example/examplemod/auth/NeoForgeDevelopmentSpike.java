package com.example.examplemod.auth;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Explicit local-only fixture: DISABLED needs no Backend; a marked world rejects downgrade. */
public final class NeoForgeDevelopmentSpike {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),java=Path.of(args[1]).toAbsolutePath();
        Path properties=root.resolve("server.properties");byte[] original=Files.readAllBytes(properties);
        String name="auth-dev-spike-"+System.currentTimeMillis();Path world=root.resolve(name);
        Files.createDirectories(world.resolve("serverconfig"));
        Files.writeString(world.resolve("serverconfig/createcityexpansioncore-authentication.toml"),"[authentication]\nmode = \"DISABLED\"\nbackendBaseUrl = \"\"\nserverId = \"\"\n");
        String configured=new String(original,StandardCharsets.UTF_8).replace("level-name=auth-spike-world","level-name="+name);
        if(!configured.contains("level-name="+name))throw new AssertionError("unexpected fixture server.properties");
        try {
            Files.writeString(properties,configured);
            run(root,java,false);
            Files.writeString(world.resolve("recreated-auth-domain.txt"),"local-auth-spike",StandardOpenOption.CREATE_NEW);
            run(root,java,true);
            System.out.println("LIVE DEVELOPMENT SPIKE PASSED: DISABLED starts without Backend/credential, production-marked world rejects downgrade");
        }finally{Files.write(properties,original);}
    }
    private static void run(Path root,Path java,boolean rejected)throws Exception {
        var builder=new ProcessBuilder(java.toString(),"-Xms256m","-Xmx2G","-XX:ActiveProcessorCount=4","@libraries/net/neoforged/neoforge/21.1.251/win_args.txt","--nogui").directory(root.toFile()).redirectErrorStream(true);
        builder.environment().remove("CCEC_BACKEND_SECRET");
        builder.environment().remove("CCEC_AUTH_TRANSPORT");
        for(String variable:new String[]{"JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS"})builder.environment().remove(variable);
        var process=builder.start();var ready=new CompletableFuture<Void>();var disabled=new CompletableFuture<Void>();
        Path log=root.resolve(rejected?"dev-downgrade-console.log":"dev-disabled-console.log");
        var reader=new Thread(()->{try(var in=process.inputReader();var out=Files.newBufferedWriter(log)){String line;while((line=in.readLine())!=null){out.write(line);out.newLine();out.flush();if(line.contains("authentication DISABLED"))disabled.complete(null);if(line.contains("Done ("))ready.complete(null);}}catch(Exception e){ready.completeExceptionally(e);}});reader.start();
        try {
            if(rejected){
                if(!process.waitFor(120,TimeUnit.SECONDS))throw new AssertionError("downgrade startup did not terminate");reader.join(3000);
                // Vanilla prints Done before NeoForge's ServerStartingEvent; require actual failure/exit, not that log alone.
                if(disabled.isDone() || !Files.readString(log).contains("authentication startup configuration rejected"))throw new AssertionError("marked-world downgrade was not rejected");
            }else{ready.get(120,TimeUnit.SECONDS);disabled.get(10,TimeUnit.SECONDS);}
        }finally{
            if(process.isAlive()){process.outputWriter().write("stop\n");process.outputWriter().flush();if(!process.waitFor(45,TimeUnit.SECONDS))process.destroy();}reader.join(3000);
        }
    }
}
