package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;

public final class MineOutputMapping {

    private static final Map<ResourceLocation, Item> OUTPUTS = Map.of(
            resource("iron"), Items.RAW_IRON,
            resource("copper"), Items.RAW_COPPER,
            resource("gold"), Items.RAW_GOLD,
            resource("coal"), Items.COAL,
            resource("zinc"), BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("create", "raw_zinc")
            )
    );

    private MineOutputMapping() {
    }

    public static Optional<Item> outputItem(ResourceLocation geologicalResource) {
        return Optional.ofNullable(OUTPUTS.get(geologicalResource));
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID,
                path
        );
    }
}
