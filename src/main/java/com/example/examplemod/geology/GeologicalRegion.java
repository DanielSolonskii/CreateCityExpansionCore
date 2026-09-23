package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;

public class GeologicalRegion {

    private final ResourceLocation id;
    private final Map<ResourceLocation, Integer> resources;

    public GeologicalRegion(
            ResourceLocation id,
            Map<ResourceLocation, Integer> resources
    ) {
        this.id = id;
        this.resources = Map.copyOf(resources);
    }

    public ResourceLocation getId() {
        return id;
    }

    public Map<ResourceLocation, Integer> getResources() {
        return Collections.unmodifiableMap(resources);
    }

    /**
     * Возвращает богатство конкретного ресурса.
     *
     * 0  = ресурса практически нет
     * 100 = очень богатое месторождение
     */
    public int getRichness(ResourceLocation resource) {
        return resources.getOrDefault(resource, 0);
    }

    /**
     * Возвращает ресурс с наибольшим содержанием.
     */
    public ResourceLocation getPrimaryResource() {
        ResourceLocation primary = null;
        int highest = 0;

        for (Map.Entry<ResourceLocation, Integer> entry : resources.entrySet()) {
            if (entry.getValue() > highest) {
                highest = entry.getValue();
                primary = entry.getKey();
            }
        }

        return primary;
    }
}