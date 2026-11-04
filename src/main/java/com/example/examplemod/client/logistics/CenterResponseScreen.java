package com.example.examplemod.client.logistics;
import net.minecraft.nbt.CompoundTag;
/** Delivery of a correlated server response to the currently visible logistics screen. */
public interface CenterResponseScreen { void accept(CompoundTag data); }
