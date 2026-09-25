package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record RailwayMapPayload(BlockPos pylonPos, ResourceLocation dimension, List<TrackSegment> segments)
        implements CustomPacketPayload {
    public record TrackSegment(float x1,float z1,float x2,float z2) {}
    public static final Type<RailwayMapPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"railway_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RailwayMapPayload> STREAM_CODEC=StreamCodec.of(RailwayMapPayload::encode,RailwayMapPayload::decode);
    public RailwayMapPayload{segments=List.copyOf(segments);}
    private static void encode(RegistryFriendlyByteBuf b,RailwayMapPayload p){BlockPos.STREAM_CODEC.encode(b,p.pylonPos);b.writeResourceLocation(p.dimension);b.writeVarInt(p.segments.size());for(var s:p.segments){b.writeFloat(s.x1);b.writeFloat(s.z1);b.writeFloat(s.x2);b.writeFloat(s.z2);}}
    private static RailwayMapPayload decode(RegistryFriendlyByteBuf b){BlockPos p=BlockPos.STREAM_CODEC.decode(b);ResourceLocation d=b.readResourceLocation();int c=Math.min(20000,b.readVarInt());List<TrackSegment>s=new ArrayList<>(c);for(int i=0;i<c;i++)s.add(new TrackSegment(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));return new RailwayMapPayload(p,d,s);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
