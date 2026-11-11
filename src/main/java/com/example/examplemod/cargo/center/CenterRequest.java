package com.example.examplemod.cargo.center;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CenterRequest(CompoundTag data) implements CustomPacketPayload {
    public CenterRequest { if(data==null||data.toString().length()>16384)throw new IllegalArgumentException("Logistics request bounds");data=data.copy(); }
    public static final Type<CenterRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","logistics_center_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CenterRequest> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new CenterRequest(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
