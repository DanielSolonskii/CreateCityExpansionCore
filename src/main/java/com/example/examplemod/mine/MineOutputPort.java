package com.example.examplemod.mine;

import net.minecraft.resources.ResourceLocation;

interface MineOutputPort {

    boolean available();

    boolean supports(ResourceLocation resourceId);

    boolean canAccept(ResourceLocation resourceId, long amount);

    void insertExact(ResourceLocation resourceId, long amount);

    int itemCount();

    int totalCapacity();
}
