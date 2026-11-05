package com.example.examplemod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Registry-free persistence regressions; live interaction requires an in-game check. */
public final class TransportTerminalBindingCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        UUID vessel = UUID.randomUUID(), city = UUID.randomUUID(), token = UUID.randomUUID();
        BlockPos center = new BlockPos(40961904, 0, 40986432);
        BlockPos terminal = center.offset(5, 72, -9);
        var binding = new TransportTerminalBinding(vessel, city, token, terminal.subtract(center));
        CompoundTag foreign = new CompoundTag();
        foreign.putString("other_mod:data", "untouched");
        CompoundTag stored = binding.store(foreign);
        require(!foreign.contains(TransportTerminalBinding.KEY), "store does not mutate shared data");
        require("untouched".equals(stored.getString("other_mod:data")), "foreign data preserved");
        require(binding.equals(TransportTerminalBinding.read(stored.copy(), vessel)), "NBT round trip");
        require(TransportTerminalBinding.read(stored, UUID.randomUUID()) == null, "split vessel cannot inherit binding");
        require(binding.store(stored) == stored, "unchanged lookup does not rewrite metadata");
        BlockPos moved = new BlockPos(40968192, 0, 41000960);
        require(TransportTerminalBinding.read(stored, vessel).localPos().offset(moved).equals(moved.offset(5, 72, -9)),
                "registration follows plot relocation");
        var legacy = new TransportTerminalBinding(vessel, city, null, new BlockPos(2, 64, 3));
        require(legacy.equals(TransportTerminalBinding.read(legacy.store(stored), vessel)), "legacy null token");
        CompoundTag corrupt = stored.copy();
        corrupt.getCompound(TransportTerminalBinding.KEY).remove("Position");
        require(TransportTerminalBinding.read(corrupt, vessel) == null, "missing position rejected");
        corrupt = stored.copy();
        corrupt.getCompound(TransportTerminalBinding.KEY).putInt("Version", 999);
        require(TransportTerminalBinding.read(corrupt, vessel) == null, "unknown schema rejected");
        CompoundTag cleared = TransportTerminalBinding.clear(stored);
        require(!cleared.contains(TransportTerminalBinding.KEY), "removal clears binding");
        require("untouched".equals(cleared.getString("other_mod:data")), "removal preserves foreign data");
        require(stored.contains(TransportTerminalBinding.KEY), "clear preserves earlier snapshot");
        require(TransportTerminalBinding.read(null, vessel) == null, "old vessel without metadata");
        try (var stream = TransportTerminalBindingCheck.class.getResourceAsStream("TransportSubLevelProtection.class")) {
            require(stream != null, "production class exists");
            String pool = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            require(!pool.contains("EmbeddedPlotLevelAccessor"), "exact embedded-to-world crash path absent");
            require(!pool.contains("getChunkSource"), "no vanilla chunk source access");
        }
        System.out.println("Transport terminal binding: " + checks + " checks passed");
    }
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
