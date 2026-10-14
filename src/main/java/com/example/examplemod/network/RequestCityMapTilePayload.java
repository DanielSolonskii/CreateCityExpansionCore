package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestCityMapTilePayload(
        BlockPos pylonPos,
        ResourceLocation dimensionId,
        int tileX,
        int tileZ, int level, long revision, java.util.UUID worldId
) implements CustomPacketPayload {

    public RequestCityMapTilePayload(BlockPos pos,ResourceLocation dimension,int x,int z){this(pos,dimension,x,z,0,0,new java.util.UUID(0,0));}
    public RequestCityMapTilePayload {if(level<0||level>7)throw new IllegalArgumentException("terrain level");}
    public static final Type<RequestCityMapTilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "request_city_map_tile"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCityMapTilePayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.pylonPos());
                        buffer.writeResourceLocation(payload.dimensionId());
                        buffer.writeInt(payload.tileX());
                        buffer.writeInt(payload.tileZ());buffer.writeByte(payload.level());buffer.writeLong(payload.revision());buffer.writeUUID(payload.worldId());
                    },
                    buffer -> new RequestCityMapTilePayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readResourceLocation(),
                            buffer.readInt(),
                            buffer.readInt(),buffer.readUnsignedByte(),buffer.readLong(),buffer.readUUID()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
