package com.example.examplemod.city;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.GazetteEditionPayload;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class GazetteDelivery {
    private record Delivery(int container, UUID city, long revision) {}
    private static final Map<ServerPlayer, Delivery> delivered = new WeakHashMap<>();

    @SubscribeEvent public static void restoreArchive(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        CityGazette.get(event.getServer());
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 20 != 0) return;
        if (!(player.containerMenu instanceof CityPylonMenu menu) || !player.connection.hasChannel(GazetteEditionPayload.TYPE)) {
            delivered.remove(player);
            return;
        }
        var server = player.serverLevel().getServer();
        var cities = CityManager.get(server.overworld());
        var publisher = cities.getCityByPylon(menu.getDimensionId(), menu.getPylonPos());
        if (publisher == null) return;
        var paper = CityGazette.get(server);
        var next = new Delivery(menu.containerId, publisher.getId(), paper.revision());
        if (next.equals(delivered.get(player))) return;
        var stories = paper.edition(publisher.getId());
        if (player.connection.hasChannel(GazetteEditionPayload.V3.TYPE)) {
            PacketDistributor.sendToPlayer(player, new GazetteEditionPayload.V3(new GazetteEditionPayload(menu.containerId,stories)));
            delivered.put(player,next);
            return;
        }
        stories = stories.stream().filter(s -> s.kind().ordinal() <= CityGazette.Kind.NEWCOMER.ordinal()).toList();
        if (player.connection.hasChannel(GazetteEditionPayload.V2.TYPE)) {
            PacketDistributor.sendToPlayer(player, new GazetteEditionPayload.V2(new GazetteEditionPayload(menu.containerId, stories)));
        } else {
            var compatible = stories.stream().filter(s -> s.kind() != CityGazette.Kind.NEWCOMER).toList();
            PacketDistributor.sendToPlayer(player, new GazetteEditionPayload(menu.containerId, compatible));
        }
        delivered.put(player, next);
    }
}
