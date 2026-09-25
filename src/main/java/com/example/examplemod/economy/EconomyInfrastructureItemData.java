package com.example.examplemod.economy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Optional;
import java.util.UUID;

public final class EconomyInfrastructureItemData {
    private static final String ROOT="createcityexpansioncore:economy_infrastructure";
    private EconomyInfrastructureItemData(){}
    public static void write(ItemStack stack,UUID physicalId,UUID registrationId){CompoundTag custom=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();CompoundTag data=new CompoundTag();data.putUUID("PhysicalId",physicalId);if(registrationId!=null)data.putUUID("RegistrationId",registrationId);custom.put(ROOT,data);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));}
    public static Optional<Data> read(ItemStack stack){CompoundTag custom=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(!custom.contains(ROOT,Tag.TAG_COMPOUND))return Optional.empty();CompoundTag data=custom.getCompound(ROOT);return data.hasUUID("PhysicalId")?Optional.of(new Data(data.getUUID("PhysicalId"),data.hasUUID("RegistrationId")?data.getUUID("RegistrationId"):null)):Optional.empty();}
    public record Data(UUID physicalId,UUID registrationId){}
}
