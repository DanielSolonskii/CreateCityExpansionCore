package com.example.examplemod.client.map;

import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.network.CityMapPeersPayload;
import java.util.*;

/** Public markers and bounded nearby claims share one projection in every map view. */
public final class CityMapCities {
    private CityMapCities() { }
    public static List<CityMapPeersPayload.Peer> merge(List<CityMapPeersPayload.Peer> territories,
                                                     List<CityPublicProfile> directory, boolean complete) {
        Map<UUID, CityMapPeersPayload.Peer> merged = new LinkedHashMap<>();
        for (var peer : territories) merged.put(peer.cityId(), peer);
        Set<UUID> ids = new HashSet<>();
        for (var city : directory) {
            ids.add(city.id()); var known = merged.get(city.id());
            merged.put(city.id(), new CityMapPeersPayload.Peer(city.id(), city.name(), city.pos().getX(), city.pos().getZ(), city.colour(),
                    known == null ? Set.of() : known.plots(), city.residents(), city.own(), known != null && known.master()));
        }
        if (complete) merged.keySet().retainAll(ids);
        return List.copyOf(merged.values());
    }
    public static List<CityMapPeersPayload.Peer> matching(List<CityMapPeersPayload.Peer> peers, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return peers.stream().filter(peer -> peer.name().toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }
}
