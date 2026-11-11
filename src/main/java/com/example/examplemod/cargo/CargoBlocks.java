package com.example.examplemod.cargo;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Uses CCEC's existing deferred registers, not a second set of mod registries. */
public final class CargoBlocks {
    public static final DeferredBlock<CargoRouterBlock> ROUTER = CreateCityExpansionCore.BLOCKS.register(
            "cargo_router", () -> new CargoRouterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.5F).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ROUTER_ITEM =
            CreateCityExpansionCore.ITEMS.registerSimpleBlockItem("cargo_router", ROUTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoRouterBlockEntity>> ROUTER_ENTITY =
            CreateCityExpansionCore.BLOCK_ENTITY_TYPES.register("cargo_router",
                    () -> BlockEntityType.Builder.of(CargoRouterBlockEntity::new, ROUTER.get()).build(null));

    private CargoBlocks() {}
    public static final DeferredBlock<CargoTickerBlock> TICKER = CreateCityExpansionCore.BLOCKS.register(
            "cargo_ticker", () -> new CargoTickerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.5F).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem> TICKER_ITEM =
            CreateCityExpansionCore.ITEMS.register("cargo_ticker", () ->
                    new com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem(TICKER.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoTickerBlockEntity>> TICKER_ENTITY =
            CreateCityExpansionCore.BLOCK_ENTITY_TYPES.register("cargo_ticker",
                    () -> BlockEntityType.Builder.of(CargoTickerBlockEntity::new, TICKER.get()).build(null));
    public static void init() { /* force deferred entries to initialize before registers attach */ }
}
