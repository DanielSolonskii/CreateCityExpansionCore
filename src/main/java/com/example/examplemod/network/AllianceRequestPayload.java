package com.example.examplemod.network;
import com.example.examplemod.alliance.AllianceBook;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public record AllianceRequestPayload(AllianceBook.Command command,int page,int chatPage) implements CustomPacketPayload {
    public AllianceRequestPayload(AllianceBook.Command command,int page){this(command,page,0);}
    public AllianceRequestPayload{Objects.requireNonNull(command.request());Objects.requireNonNull(command.action());if(page<0||page>10000||chatPage<0||chatPage>4||command.revision()<0||!AllianceBook.validText(command.text(),256,true)||!AllianceBook.validText(command.description(),180,true))throw new IllegalArgumentException("Invalid alliance request");}
    public static final Type<AllianceRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","alliance_request_v1"));
    private static UUID uuid(RegistryFriendlyByteBuf b){return b.readBoolean()?b.readUUID():null;}
    private static void uuid(RegistryFriendlyByteBuf b,UUID id){b.writeBoolean(id!=null);if(id!=null)b.writeUUID(id);}
    public static final StreamCodec<RegistryFriendlyByteBuf,AllianceRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{var c=p.command;b.writeUUID(c.request());b.writeEnum(c.action());uuid(b,c.alliance());uuid(b,c.target());b.writeVarLong(c.revision());b.writeUtf(c.text(),256);b.writeUtf(c.description(),180);b.writeInt(c.color());b.writeVarInt(c.emblem());b.writeVarInt(p.page);b.writeVarInt(p.chatPage);},b->new AllianceRequestPayload(new AllianceBook.Command(b.readUUID(),b.readEnum(AllianceBook.Action.class),uuid(b),uuid(b),b.readVarLong(),b.readUtf(256),b.readUtf(180),b.readInt(),b.readVarInt()),b.readVarInt(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
