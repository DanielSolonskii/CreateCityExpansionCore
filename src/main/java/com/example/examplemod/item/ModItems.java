package com.example.examplemod.item;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(
                    Registries.ITEM,
                    CreateCityExpansionCore.MODID
            );

    public static final DeferredHolder<Item, Item> MAGNETIC_ANALYZER =
            ITEMS.register(
                    "magnetic_analyzer",
                    () -> new Item(
                            new Item.Properties()
                                    .durability(10)
                    )
            );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}