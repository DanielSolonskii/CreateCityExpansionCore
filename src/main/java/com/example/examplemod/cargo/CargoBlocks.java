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
                    .strength(3.5F).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ROUTER_ITEM =
            CreateCityExpansionCore.ITEMS.registerSimpleBlockItem("cargo_router", ROUTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoRouterBlockEntity>> ROUTER_ENTITY =
            CreateCityExpansionCore.BLOCK_ENTITY_TYPES.register("cargo_router",
                    () -> BlockEntityType.Builder.of(CargoRouterBlockEntity::new, ROUTER.get()).build(null));

    private CargoBlocks() {}
    public static final DeferredBlock<CargoTickerBlock> TICKER = CreateCityExpansionCore.BLOCKS.register(
            "cargo_ticker", () -> new CargoTickerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.5F).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem> TICKER_ITEM =
            CreateCityExpansionCore.ITEMS.register("cargo_ticker", () ->
                    new com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem(TICKER.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoTickerBlockEntity>> TICKER_ENTITY =
            CreateCityExpansionCore.BLOCK_ENTITY_TYPES.register("cargo_ticker",
                    () -> BlockEntityType.Builder.of(CargoTickerBlockEntity::new, TICKER.get()).build(null));
    public static final DeferredBlock<com.example.examplemod.cargo.center.MasterCityCustomsBufferBlock> CUSTOMS_BUFFER = CreateCityExpansionCore.BLOCKS.register(
            "customs_buffer", () -> new com.example.examplemod.cargo.center.MasterCityCustomsBufferBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4F).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> CUSTOMS_BUFFER_ITEM = CreateCityExpansionCore.ITEMS.registerSimpleBlockItem("customs_buffer",CUSTOMS_BUFFER);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<com.example.examplemod.cargo.center.MasterCityCustomsBufferBlockEntity>> CUSTOMS_BUFFER_ENTITY =
            CreateCityExpansionCore.BLOCK_ENTITY_TYPES.register("customs_buffer",()->BlockEntityType.Builder.of(com.example.examplemod.cargo.center.MasterCityCustomsBufferBlockEntity::new,CUSTOMS_BUFFER.get()).build(null));
    public static void init() { /* force deferred entries to initialize before registers attach */ }
}
