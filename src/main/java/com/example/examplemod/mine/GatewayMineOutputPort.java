package com.example.examplemod.mine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class GatewayMineOutputPort implements MineOutputPort {

    private final MineGatewayBlockEntity gateway;

    GatewayMineOutputPort(MineGatewayBlockEntity gateway) {
        this.gateway = gateway;
    }

    @Override
    public boolean available() {
        return gateway != null;
    }

    @Override
    public boolean supports(ResourceLocation resourceId) {
        return MineOutputMapping.outputItem(resourceId).isPresent();
    }

    @Override
    public boolean canAccept(ResourceLocation resourceId, long amount) {
        return stack(resourceId, amount)
                .map(gateway.output()::canInsert)
                .orElse(false);
    }

    @Override
    public void insertExact(ResourceLocation resourceId, long amount) {
        ItemStack output = stack(resourceId, amount).orElseThrow(() ->
                new IllegalArgumentException("Unsupported mine output: " + resourceId)
        );
        gateway.output().insertExact(output);
    }

    @Override
    public int itemCount() {
        return available() ? gateway.output().itemCount() : 0;
    }

    @Override
    public int totalCapacity() {
        return available() ? gateway.output().totalCapacity() : 0;
    }

    private static java.util.Optional<ItemStack> stack(
            ResourceLocation resourceId,
            long amount
    ) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return java.util.Optional.empty();
        }
        return MineOutputMapping.outputItem(resourceId)
                .map(item -> new ItemStack(item, (int) amount));
    }
}
