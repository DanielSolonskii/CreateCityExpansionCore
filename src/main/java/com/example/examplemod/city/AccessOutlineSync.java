package com.example.examplemod.city;

import com.example.examplemod.item.AccessControlRemoteItem;
import com.example.examplemod.network.AccessOutlinePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Sends only changed snapshots while holding the remote. No terrain loading. */
@EventBusSubscriber(modid = "createcityexpansioncore")
public final class AccessOutlineSync {
    private static final Map<ServerPlayer, AccessOutlinePayload> LAST = new WeakHashMap<>();
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean holding = player.getMainHandItem().getItem() instanceof AccessControlRemoteItem
                || player.getOffhandItem().getItem() instanceof AccessControlRemoteItem;
        var level = player.serverLevel();
        var dimension = level.dimension().location();
        if (!holding) {
            if (LAST.remove(player) != null) PacketDistributor.sendToPlayer(player, new AccessOutlinePayload(dimension, List.of()));
            return;
        }
        var manager = CityManager.get(player.getServer().overworld());
        var entries = new ArrayList<AccessOutlinePayload.Entry>();
        for (var city : manager.getCities()) {
            if (!city.hasMember(player.getUUID())) continue;
            for (var grant : manager.interactionGrants(city)) {
                var pos = grant.pos();
                if (!grant.dimension().equals(dimension) || pos.distToCenterSqr(player.position()) > 64 * 64
                        || !level.hasChunkAt(pos) || manager.getCityAt(dimension, pos) != city) continue;
                if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(grant.blockId())) continue;
                entries.add(new AccessOutlinePayload.Entry(pos, grant.blockId()));
            }
        }
        entries.sort(Comparator.comparingLong(e -> e.pos().asLong()));
        // Bound every individual packet; nearby grants beyond this exceptionally large cap are omitted.
        if (entries.size() > AccessOutlinePayload.MAX_ENTRIES) entries.subList(AccessOutlinePayload.MAX_ENTRIES, entries.size()).clear();
        var snapshot = new AccessOutlinePayload(dimension, entries);
        if (!snapshot.equals(LAST.get(player))) {
            LAST.put(player, snapshot);
            PacketDistributor.sendToPlayer(player, snapshot);
        }
    }
}
