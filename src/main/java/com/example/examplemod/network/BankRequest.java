package com.example.examplemod.network;
import com.example.examplemod.economy.BankBook;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record BankRequest(UUID request,long revision,BankBook.Action action,UUID source,UUID destination,UUID peer,long amount,long goal,String text,int kind,boolean compact) implements CustomPacketPayload {
    public BankRequest{if(request==null||action==null||text==null||text.length()>96||kind<0||kind>1||amount<0||goal<0)throw new IllegalArgumentException("Bank packet bounds");}
    public static final Type<BankRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","bank_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BankRequest> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request);b.writeLong(p.revision);b.writeEnum(p.action);uuid(b,p.source);uuid(b,p.destination);uuid(b,p.peer);b.writeLong(p.amount);b.writeLong(p.goal);b.writeUtf(p.text,96);b.writeVarInt(p.kind);b.writeBoolean(p.compact);},b->new BankRequest(b.readUUID(),b.readLong(),b.readEnum(BankBook.Action.class),uuid(b),uuid(b),uuid(b),b.readLong(),b.readLong(),b.readUtf(96),b.readVarInt(),b.readBoolean()));
    private static void uuid(RegistryFriendlyByteBuf b,UUID id){b.writeBoolean(id!=null);if(id!=null)b.writeUUID(id);}private static UUID uuid(RegistryFriendlyByteBuf b){return b.readBoolean()?b.readUUID():null;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
