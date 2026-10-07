package com.example.examplemod.client;

import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.network.*;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Complete replacement snapshots: removed cities disappear and stale pages cannot mix. */
public final class CityPublicDirectory {
    private static UUID token;
    private static int expected, total;
    private static long receivedAt;
    public static boolean loading() { return token != null && expected < total && System.currentTimeMillis() - receivedAt < 15000; }
    private static ResourceLocation dimension;
    private static final Map<UUID, CityPublicProfile> staging = new LinkedHashMap<>();
    private static Map<UUID, CityPublicProfile> profiles = Map.of();
    public static List<ResourceLocation> dimensions = List.of();
    private CityPublicDirectory() { }
    public static CityPublicProfile profile(UUID id) { return id == null ? null : profiles.get(id); }
    public static List<CityPublicProfile> profiles(ResourceLocation dim) { return profiles.values().stream().filter(c -> c.dimension().equals(dim)).toList(); }
    public static void reset() { token = null; expected = total = 0; dimension = null; staging.clear(); profiles = Map.of(); dimensions = List.of(); }
    public static void cancelLoading() { token = null; expected = total = 0; staging.clear(); }
    public static void accept(CityDirectoryPayload payload) {
        if (!(Minecraft.getInstance().screen instanceof PolishedWorldMapScreen screen)) return;
        if (!screen.acceptsDirectory(payload)) return;
        CityWorldMapSession.INSTANCE.acceptDirectory(payload);
        if (payload.offset() == 0) {
            token = payload.token(); expected = 0; total = payload.total(); dimension = payload.dimension(); staging.clear();
            dimensions = payload.dimensions();
        }
        if (!payload.token().equals(token) || payload.offset() != expected || payload.total() != total || !payload.dimension().equals(dimension)) return;
        receivedAt = System.currentTimeMillis();
        for (var city : payload.cities()) staging.put(city.id(), city);
        expected += payload.cities().size();
        if (expected < total) {
            if (payload.cities().isEmpty()) return;
            PacketDistributor.sendToServer(new RequestCityDirectoryPayload(dimension, token, expected));
        } else {
            profiles = Collections.unmodifiableMap(new LinkedHashMap<>(staging));
            screen.directoryReady(payload);
        }
    }
}
