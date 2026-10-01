package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record EconomyCommandPayload(BlockPos pylonPos,Action action,UUID targetId,UUID secondaryId,
                                    ResourceLocation itemId,ResourceLocation requestedItemId,
                                    long amount,long value,long deadlineTicks,
                                    int typeOrdinal,String title,String description,String note)
        implements CustomPacketPayload {
    public enum Action {REQUEST_STATE,ACCEPT_MARKET_OFFER,CREATE_CITY_CONTRACT,CREATE_MASTER_SPECIAL,ACCEPT_CONTRACT,CREATE_SHIPMENT,CANCEL_SHIPMENT,CONFIRM_CONTRACT,CANCEL_CONTRACT,DISPUTE_CONTRACT,CREATE_LISTING,ACCEPT_LISTING,MARK_HANDED,CONFIRM_LISTING,CANCEL_LISTING,DISPUTE_LISTING,BIND_INFRASTRUCTURE,BIND_INTAKE_STATION,REGISTER_FREIGHT,MANAGE_FREIGHT,CONTROL_DISPATCH,SETUP_FREIGHT,CREATE_FREIGHT_SELL,ACCEPT_FREIGHT_SELL,REQUEST_FREIGHT_ROUTES,SELECT_FREIGHT_ROUTE,
        /** Cancel/revoke an open freight dispatch; appended so existing ordinals stay stable. */
        REVOKE_FREIGHT}
    public static final Type<EconomyCommandPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"economy_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EconomyCommandPayload> STREAM_CODEC=StreamCodec.of(EconomyCommandPayload::encode,EconomyCommandPayload::decode);
    public EconomyCommandPayload{title=trim(title,96);description=trim(description,512);note=trim(note,160);}
    public static EconomyCommandPayload simple(BlockPos pos,Action action,UUID target){return new EconomyCommandPayload(pos,action,target,null,null,null,0,0,0,0,"","","");}
    private static String trim(String v,int max){if(v==null)return"";return v.length()<=max?v:v.substring(0,max);}
    private static void encode(RegistryFriendlyByteBuf b,EconomyCommandPayload p){BlockPos.STREAM_CODEC.encode(b,p.pylonPos);b.writeEnum(p.action);b.writeBoolean(p.targetId!=null);if(p.targetId!=null)b.writeUUID(p.targetId);b.writeBoolean(p.secondaryId!=null);if(p.secondaryId!=null)b.writeUUID(p.secondaryId);b.writeBoolean(p.itemId!=null);if(p.itemId!=null)b.writeResourceLocation(p.itemId);b.writeBoolean(p.requestedItemId!=null);if(p.requestedItemId!=null)b.writeResourceLocation(p.requestedItemId);b.writeLong(p.amount);b.writeLong(p.value);b.writeLong(p.deadlineTicks);b.writeVarInt(p.typeOrdinal);b.writeUtf(p.title,96);b.writeUtf(p.description,512);b.writeUtf(p.note,160);}
    private static EconomyCommandPayload decode(RegistryFriendlyByteBuf b){BlockPos pos=BlockPos.STREAM_CODEC.decode(b);Action action=b.readEnum(Action.class);UUID target=b.readBoolean()?b.readUUID():null,secondary=b.readBoolean()?b.readUUID():null;ResourceLocation item=b.readBoolean()?b.readResourceLocation():null;ResourceLocation requested=b.readBoolean()?b.readResourceLocation():null;return new EconomyCommandPayload(pos,action,target,secondary,item,requested,b.readLong(),b.readLong(),b.readLong(),b.readVarInt(),b.readUtf(96),b.readUtf(512),b.readUtf(160));}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
