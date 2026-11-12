package com.example.examplemod.city;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Files;
import java.util.*;

/** Local operator identities only; never performs a remote profile lookup. */
final class OperatorCityRoster {
    private record Snapshot(long tick,Map<UUID,String> profiles) {}
    private static final Map<MinecraftServer,Snapshot> CACHE=new WeakHashMap<>();
    private OperatorCityRoster() {}
    static Map<UUID,String> current(MinecraftServer server) {
        long tick=server.overworld().getGameTime();
        Snapshot old=CACHE.get(server);
        if(old==null||old.tick()!=tick) {
            var identities=new LinkedHashMap<UUID,String>();
            var file=server.getPlayerList().getOps().getFile().toPath();
            // UUIDs of offline operators are held in the vanilla ops file, not playerdata.
            try {
                if(Files.size(file)<=2*1024*1024)try(var reader=Files.newBufferedReader(file)) {
                    for(var raw:JsonParser.parseReader(reader).getAsJsonArray()) {
                        var value=raw.getAsJsonObject();
                        identities.put(UUID.fromString(value.get("uuid").getAsString()),value.get("name").getAsString());
                    }
                }
            } catch(java.io.IOException|RuntimeException invalid) { /* Fail closed; online profiles below remain verifiable. */ }
            for(var player:server.getPlayerList().getPlayers())identities.put(player.getUUID(),player.getGameProfile().getName());
            old=new Snapshot(tick,Map.copyOf(identities));CACHE.put(server,old);
        }
        var result=new LinkedHashMap<UUID,String>();
        // Validate the live in-memory list on every lookup, including deop within the same tick.
        old.profiles().forEach((id,name)->{if(server.getPlayerList().getOps().get(new GameProfile(id,name))!=null)result.put(id,name);});
        return result;
    }
}
