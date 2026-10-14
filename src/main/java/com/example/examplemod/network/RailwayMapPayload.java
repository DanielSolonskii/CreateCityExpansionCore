package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.railway.RailwayOwnershipTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** One bounded railway-map snapshot; requested on map open, never from hover. */
public record RailwayMapPayload(BlockPos pylonPos,ResourceLocation dimension,List<TrackSegment> segments,
                                List<NetworkView> networks,List<SectionView> sections,List<RouteView> routes)
        implements CustomPacketPayload {
    public static final int MAX_TRACK_SEGMENTS=20_000,MAX_SEMANTIC_SEGMENTS=4_096,MAX_NETWORKS=512,MAX_SECTIONS=1_024,MAX_ROUTES=128;
    public record TrackSegment(float x1,float z1,float x2,float z2) {}
    public record NetworkView(UUID id,String name,RailwayOwnershipTypes.OwnerType ownerType,UUID ownerId,
                              String ownerName,RailwayOwnershipTypes.Access access,long tariff,double length,
                              int sections,int onlineSections) {}
    public record SectionView(UUID id,UUID networkId,double length,RailwayOwnershipTypes.SectionStatus status,
                              List<TrackSegment> segments){public SectionView{segments=List.copyOf(segments);}}
    public record RouteView(UUID id,UUID fleetTrainId,boolean selected,String status,double distance,long price,
                            long etaTicks,List<TrackSegment> segments){public RouteView{segments=List.copyOf(segments);}}
    public static final Type<RailwayMapPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"railway_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RailwayMapPayload> STREAM_CODEC=StreamCodec.of(RailwayMapPayload::encode,RailwayMapPayload::decode);
    public RailwayMapPayload{segments=List.copyOf(segments.subList(0,Math.min(MAX_TRACK_SEGMENTS,segments.size())));networks=List.copyOf(networks.subList(0,Math.min(MAX_NETWORKS,networks.size())));sections=List.copyOf(sections.subList(0,Math.min(MAX_SECTIONS,sections.size())));routes=List.copyOf(routes.subList(0,Math.min(MAX_ROUTES,routes.size())));}
    public RailwayMapPayload(BlockPos pylonPos,ResourceLocation dimension,List<TrackSegment> segments){this(pylonPos,dimension,segments,List.of(),List.of(),List.of());}
    private static void encode(RegistryFriendlyByteBuf b,RailwayMapPayload p){BlockPos.STREAM_CODEC.encode(b,p.pylonPos);b.writeResourceLocation(p.dimension);writeSegments(b,p.segments,MAX_TRACK_SEGMENTS);b.writeVarInt(p.networks.size());for(var n:p.networks){b.writeUUID(n.id);b.writeUtf(n.name,64);b.writeEnum(n.ownerType);b.writeUUID(n.ownerId);b.writeUtf(n.ownerName,96);b.writeEnum(n.access);b.writeVarLong(n.tariff);b.writeDouble(n.length);b.writeVarInt(n.sections);b.writeVarInt(n.onlineSections);}b.writeVarInt(p.sections.size());for(var s:p.sections){b.writeUUID(s.id);b.writeUUID(s.networkId);b.writeDouble(s.length);b.writeEnum(s.status);writeSegments(b,s.segments,MAX_SEMANTIC_SEGMENTS);}b.writeVarInt(p.routes.size());for(var r:p.routes){b.writeUUID(r.id);b.writeUUID(r.fleetTrainId);b.writeBoolean(r.selected);b.writeUtf(r.status,48);b.writeDouble(r.distance);b.writeVarLong(r.price);b.writeVarLong(r.etaTicks);writeSegments(b,r.segments,MAX_SEMANTIC_SEGMENTS);}}
    private static RailwayMapPayload decode(RegistryFriendlyByteBuf b){BlockPos p=BlockPos.STREAM_CODEC.decode(b);ResourceLocation d=b.readResourceLocation();List<TrackSegment> tracks=readSegments(b,MAX_TRACK_SEGMENTS);int nc=Math.min(MAX_NETWORKS,Math.max(0,b.readVarInt()));List<NetworkView> networks=new ArrayList<>(nc);for(int i=0;i<nc;i++)networks.add(new NetworkView(b.readUUID(),b.readUtf(64),b.readEnum(RailwayOwnershipTypes.OwnerType.class),b.readUUID(),b.readUtf(96),b.readEnum(RailwayOwnershipTypes.Access.class),b.readVarLong(),b.readDouble(),b.readVarInt(),b.readVarInt()));int sc=Math.min(MAX_SECTIONS,Math.max(0,b.readVarInt()));List<SectionView> sections=new ArrayList<>(sc);for(int i=0;i<sc;i++)sections.add(new SectionView(b.readUUID(),b.readUUID(),b.readDouble(),b.readEnum(RailwayOwnershipTypes.SectionStatus.class),readSegments(b,MAX_SEMANTIC_SEGMENTS)));int rc=Math.min(MAX_ROUTES,Math.max(0,b.readVarInt()));List<RouteView> routes=new ArrayList<>(rc);for(int i=0;i<rc;i++)routes.add(new RouteView(b.readUUID(),b.readUUID(),b.readBoolean(),b.readUtf(48),b.readDouble(),b.readVarLong(),b.readVarLong(),readSegments(b,MAX_SEMANTIC_SEGMENTS)));return new RailwayMapPayload(p,d,tracks,networks,sections,routes);}
    private static void writeSegments(RegistryFriendlyByteBuf b,List<TrackSegment> values,int limit){int count=Math.min(limit,values.size());b.writeVarInt(count);for(int i=0;i<count;i++){var s=values.get(i);b.writeFloat(s.x1);b.writeFloat(s.z1);b.writeFloat(s.x2);b.writeFloat(s.z2);}}
    private static List<TrackSegment> readSegments(RegistryFriendlyByteBuf b,int limit){int count=Math.min(limit,Math.max(0,b.readVarInt()));List<TrackSegment> values=new ArrayList<>(count);for(int i=0;i<count;i++)values.add(new TrackSegment(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));return values;}
    public int estimatedBytes(){int value=96+segments.size()*16+networks.size()*176;for(var s:sections)value+=64+s.segments.size()*16;for(var r:routes)value+=96+r.segments.size()*16;return value;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
