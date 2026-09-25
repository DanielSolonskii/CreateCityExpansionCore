package com.example.examplemod.mine;

import com.example.examplemod.geology.DepositExtractionResult;
import com.example.examplemod.geology.GeologicalRegion;
import net.minecraft.resources.ResourceLocation;

interface MineGeologyAccess {

    boolean storageAvailable();

    GeologicalRegion getRegion();

    DepositExtractionResult extract(
            ResourceLocation depositId,
            long requestedAmount
    );
}
