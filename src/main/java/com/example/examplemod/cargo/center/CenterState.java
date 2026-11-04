package com.example.examplemod.cargo.center;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CenterState(CompoundTag data) implements CustomPacketPayload {
    public CenterState { if(data==null||data.toString().length()>900000)throw new IllegalArgumentException("Logistics state bounds");data=data.copy(); }
    public static final Type<CenterState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","logistics_center_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CenterState> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new CenterState(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
