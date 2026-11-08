package com.example.examplemod.block;

import com.example.examplemod.city.CityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Collection;

/** Registration policy shared by assembly validation and placement. */
public final class TransportTerminalPolicy {
    private TransportTerminalPolicy() {}
    public static int registeredCount(ServerLevel level, Collection<BlockPos> blocks) {
        var manager = CityManager.get(level.getServer().overworld());
        int count = 0;
        for (var pos : blocks) {
            if (level.getBlockEntity(pos) instanceof TransportRegistrationTerminalBlockEntity terminal
                    && terminal.cityId() != null && manager.getCity(terminal.cityId()) != null) {
                if (++count > 1) return count;
            }
        }
        return count;
    }
    public static String assemblyFailure(int count) {
        return count <= 1 ? null : "message.createcityexpansioncore.transport_terminal.assembly_multiple";
    }
}
