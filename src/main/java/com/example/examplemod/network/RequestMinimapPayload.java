package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;
/** Dimension and anchor are selected by the server, including subsequent pages. */
public record RequestMinimapPayload(UUID token,int offset) implements CustomPacketPayload {
 public RequestMinimapPayload(){this(RequestCityDirectoryPayload.NEW,0);}
 public static final Type<RequestMinimapPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","request_minimap"));
 public static final StreamCodec<RegistryFriendlyByteBuf,RequestMinimapPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeVarInt(p.offset);},b->new RequestMinimapPayload(b.readUUID(),b.readVarInt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
