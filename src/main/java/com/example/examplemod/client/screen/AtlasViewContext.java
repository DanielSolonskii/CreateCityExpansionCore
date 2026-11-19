package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel;
import net.minecraft.core.BlockPos;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Data-only return locations scoped to a confirmed world and the current connection. */
final class AtlasViewContext {
    record Target(CityMapPanel.SelectionKind kind, UUID id, BlockPos pos) {
        static Target from(CityMapPanel.Selection value) {
            return value == null ? null : new Target(value.kind(), value.id(), value.pos().immutable());
        }
    }
    record State(double x, double z, double zoom, boolean searchOpen, String query,
                 int searchType, int searchOwner, boolean problems, int searchOffset,
                 String cardKey, int cardScroll, Target selected, AtlasSearch.Tab searchTab,
                 int searchCursor, UUID namedView, String objectQuery) {}

    private WeakReference<Object> connection = new WeakReference<>(null);
    private final LinkedHashMap<String,State> views = new LinkedHashMap<>(8,.75F,true);
    void clear() {
        connection.clear();
        views.clear();
    }
    static boolean canReturn(Object currentMenu, Object requiredMenu, boolean dimensionMatches) {
        return requiredMenu == null || currentMenu == requiredMenu && dimensionMatches;
    }
    private void bind(Object current) {
        if (current == null || current != connection.get()) {
            views.clear();
            connection = new WeakReference<>(current);
        }
    }
    State get(Object current, String key) {
        bind(current);
        return current == null || key == null ? null : views.get(key);
    }
    void put(Object current, String key, State state) {
        bind(current);
        if (current == null || key == null) return;
        views.put(key,state);
        if (views.size() > 16) views.pollFirstEntry();
    }
}
