package com.example.examplemod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record FreightNetworkRequest(BlockPos pylon,Action action,UUID target,boolean enabled,List<UUID> hubs) implements CustomPacketPayload {
    public enum Action { KIND, PUBLIC, FORWARD, ROUTE }
    public FreightNetworkRequest {
        if(pylon==null||action==null||target==null||hubs==null||hubs.size()>4||new HashSet<>(hubs).size()!=hubs.size())throw new IllegalArgumentException("Freight network bounds");hubs=List.copyOf(hubs);
    }
    public static final Type<FreightNetworkRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","freight_network_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FreightNetworkRequest> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeBlockPos(p.pylon);b.writeEnum(p.action);b.writeUUID(p.target);b.writeBoolean(p.enabled);b.writeVarInt(p.hubs.size());p.hubs.forEach(b::writeUUID);
    },b->{var pos=b.readBlockPos();var action=b.readEnum(Action.class);var target=b.readUUID();boolean enabled=b.readBoolean();int n=b.readVarInt();if(n<0||n>4)throw new IllegalArgumentException("Too many hubs");var hubs=new ArrayList<UUID>();for(int i=0;i<n;i++)hubs.add(b.readUUID());return new FreightNetworkRequest(pos,action,target,enabled,hubs);});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
