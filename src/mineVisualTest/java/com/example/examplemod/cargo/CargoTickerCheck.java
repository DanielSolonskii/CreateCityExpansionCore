package com.example.examplemod.cargo;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Real production address/settings codec and external Create linkage contract; no simulated packing. */
public final class CargoTickerCheck {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static ClassNode node(String name) throws Exception {
        try (var stream = CargoTickerCheck.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing dependency: " + name);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    public static void main(String[] args) throws Exception {
        UUID owner = UUID.randomUUID(), network = UUID.randomUUID(), first = UUID.randomUUID(), last = UUID.randomUUID();
        var known = Set.of(first, last);
        var unconfigured = new CargoTickerSettings(owner, null, null);
        require(unconfigured.address(last, network, known::contains).isEmpty(), "final point never becomes implicit first hop");
        var settings = new CargoTickerSettings(owner, first, network);
        var address = settings.address(last, network, known::contains).orElseThrow();
        require(address.nextPoint().equals(first) && address.finalPoint().equals(last), "explicit first and final UUID kept separately");
        require(settings.address(first, network, known::contains).orElseThrow().equals(new CargoAddress(first, first)), "direct delivery only after explicit first-hop configuration");
        require(settings.address(last, UUID.randomUUID(), known::contains).isEmpty(), "retuned network invalidates setup");
        require(settings.address(last, network, id -> id.equals(last)).isEmpty(), "deleted first point blocks dispatch");
        require(settings.address(last, network, id -> id.equals(first)).isEmpty(), "deleted final point blocks dispatch");
        require(settings.address(null, network, known::contains).isEmpty(), "missing final UUID rejected");
        var path = Files.createTempFile("ccec-ticker-", ".dat");
        try {
            NbtIo.writeCompressed(settings.save(), path);
            var loaded = CargoTickerSettings.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
            require(loaded.equals(settings), "owner/network/first point survive disk reload");
            require(loaded.address(last, network, known::contains).orElseThrow().equals(address), "restored setup produces same stable address");
        } finally { Files.deleteIfExists(path); }
        require(CargoTickerSettings.load(unconfigured.save()).firstPoint() == null, "fresh ticker remains unconfigured after reload");
        for (String removed : new String[]{"FirstPoint", "Network"}) {
            CompoundTag broken = settings.save(); broken.remove(removed);
            boolean rejected = false;
            try { CargoTickerSettings.load(broken); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "partial setup rejected: " + removed);
        }
        CompoundTag future = settings.save(); future.putInt("Version", 2);
        boolean rejected = false;
        try { CargoTickerSettings.load(future); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "future settings version rejected");

        // Contract of the dependency we rely on, checked without initializing Minecraft's registries.
        var checking = node("com/simibubi/create/content/logistics/stockTicker/StockCheckingBlockEntity");
        var summary = checking.methods.stream().filter(m -> m.name.equals("getAccurateSummary")).findFirst().orElseThrow();
        boolean summaryDelegates = false;
        for (var instruction : summary.instructions) if (instruction instanceof MethodInsnNode call)
            summaryDelegates |= call.owner.endsWith("/LogisticsManager") && call.name.equals("getSummaryOfNetwork");
        require(summaryDelegates, "native accurate summary delegates to LogisticsManager");
        var linkedItem = node("com/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBlockItem");
        require(linkedItem.methods.stream().anyMatch(m -> m.name.equals("assignFrequency")), "native linked item supports frequency assignment");
        var manager = node("com/simibubi/create/content/logistics/packagerLink/LogisticsManager");
        require(manager.methods.stream().anyMatch(m -> m.name.equals("broadcastPackageRequest") && m.desc.endsWith("Ljava/lang/String;)Z")), "pinned native request signature");

        try (var zip = new ZipFile(Path.of(args[0]).toFile())) {
            for (String entry : new String[]{"com/example/examplemod/cargo/CargoTickerBlock.class",
                    "com/example/examplemod/cargo/CargoTickerBlockEntity.class",
                    "assets/createcityexpansioncore/blockstates/cargo_ticker.json",
                    "assets/createcityexpansioncore/models/block/cargo_ticker.json",
                    "assets/createcityexpansioncore/models/item/cargo_ticker.json",
                    "data/createcityexpansioncore/loot_table/blocks/cargo_ticker.json"}) {
                require(zip.getEntry(entry) != null, "packaged " + entry);
                if (entry.endsWith(".json")) try (var stream = zip.getInputStream(zip.getEntry(entry))) {
                    require(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isJsonObject(), "valid JSON " + entry);
                }
            }
            for (String lang : new String[]{"ru_ru", "en_us"}) try (var stream = zip.getInputStream(zip.getEntry(
                    "assets/createcityexpansioncore/lang/" + lang + ".json"))) {
                require(!JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                        .get("block.createcityexpansioncore.cargo_ticker").getAsString().isBlank(), "translated " + lang);
            }
        }
        System.out.println("PASS: " + checks + " Cargo Ticker setup, address, persistence, dependency and asset checks");
    }
}
