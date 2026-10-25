package com.example.examplemod.network;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CommunityRequestPayload(UUID request,Action action,UUID city,UUID target,UUID token,int value,int page,String query) implements CustomPacketPayload {
    public enum Action { VIEW, INVITE, ACCEPT, DECLINE, REVOKE, ROLE, PREFERENCE, MUTE, LEAVE, KICK, TRANSFER, READ_INBOX, INBOX, READ_ALLIANCE, RELATIONS, SET_TRUST, SET_TERRITORY, SUMMARY }
    public CommunityRequestPayload{if(page<0||page>100000||query==null||query.length()>16)throw new IllegalArgumentException("Community request");}
    public static final Type<CommunityRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","community_request"));
    private static void uuid(RegistryFriendlyByteBuf b,UUID id){b.writeBoolean(id!=null);if(id!=null)b.writeUUID(id);}
    private static UUID uuid(RegistryFriendlyByteBuf b){return b.readBoolean()?b.readUUID():null;}
    public static final StreamCodec<RegistryFriendlyByteBuf,CommunityRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request);b.writeEnum(p.action);uuid(b,p.city);uuid(b,p.target);uuid(b,p.token);b.writeVarInt(p.value);b.writeVarInt(p.page);b.writeUtf(p.query,16);},b->new CommunityRequestPayload(b.readUUID(),b.readEnum(Action.class),uuid(b),uuid(b),uuid(b),b.readVarInt(),b.readVarInt(),b.readUtf(16)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
