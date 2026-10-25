package com.example.examplemod.network;
import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.social.SocialBook;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record SocialRequestPayload(UUID request,UUID target,SocialBook.Action action,long revision,String text,String query,int filter,int page,long before) implements CustomPacketPayload {
    public SocialRequestPayload {if(query.length()>16||text.length()>500||filter<0||filter>3||page<0||page>100000||before<0||revision<0)throw new IllegalArgumentException("Invalid social request");}
    public static final Type<SocialRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"social_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SocialRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.request);b.writeBoolean(p.target!=null);if(p.target!=null)b.writeUUID(p.target);b.writeEnum(p.action);b.writeVarLong(p.revision);b.writeUtf(p.text,500);b.writeUtf(p.query,16);b.writeVarInt(p.filter);b.writeVarInt(p.page);b.writeVarLong(p.before);
    },b->new SocialRequestPayload(b.readUUID(),b.readBoolean()?b.readUUID():null,b.readEnum(SocialBook.Action.class),b.readVarLong(),b.readUtf(500),b.readUtf(16),b.readVarInt(),b.readVarInt(),b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
