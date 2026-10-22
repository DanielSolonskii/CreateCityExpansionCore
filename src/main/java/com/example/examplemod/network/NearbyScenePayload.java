package com.example.examplemod.network;
import com.example.examplemod.projects.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Read-only recipient-scoped projection. No wallets, applicants, message bodies or Y coordinates. */
public record NearbyScenePayload(ResourceLocation dimension,boolean sharing,List<Friend> friends,boolean replaceProjects,List<Site> projects) implements CustomPacketPayload {
    public static final int MAX_FRIENDS=32,MAX_PROJECTS=24;
    public record Friend(UUID id,double x,double z){public Friend{if(id==null||!Double.isFinite(x)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000)throw new IllegalArgumentException("Nearby friend");}}
    public record Site(UUID id,String title,Project.Scope scope,Project.Status status,ProjectZone zone){public Site{Objects.requireNonNull(id);Objects.requireNonNull(scope);Objects.requireNonNull(status);Objects.requireNonNull(zone);if(title==null||title.isBlank()||title.length()>96)throw new IllegalArgumentException("Nearby project");}}
    public NearbyScenePayload{Objects.requireNonNull(dimension);friends=List.copyOf(friends);projects=List.copyOf(projects);if(friends.size()>MAX_FRIENDS||projects.size()>MAX_PROJECTS||!replaceProjects&&!projects.isEmpty()||projects.stream().anyMatch(p->!p.zone.dimension().equals(dimension)))throw new IllegalArgumentException("Nearby scene budget");}
    public static final Type<NearbyScenePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","nearby_scene_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NearbyScenePayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension);b.writeBoolean(p.sharing);b.writeVarInt(p.friends.size());for(var f:p.friends){b.writeUUID(f.id);b.writeDouble(f.x);b.writeDouble(f.z);}
        b.writeBoolean(p.replaceProjects);b.writeVarInt(p.projects.size());for(var s:p.projects){b.writeUUID(s.id);b.writeUtf(s.title,96);b.writeEnum(s.scope);b.writeEnum(s.status);b.writeEnum(s.zone.shape());b.writeInt(s.zone.x1());b.writeInt(s.zone.z1());b.writeInt(s.zone.x2());b.writeInt(s.zone.z2());}
    },b->{
        var dim=b.readResourceLocation();boolean sharing=b.readBoolean();int n=bounded(b.readVarInt(),MAX_FRIENDS);var friends=new ArrayList<Friend>();for(int i=0;i<n;i++)friends.add(new Friend(b.readUUID(),b.readDouble(),b.readDouble()));
        boolean replace=b.readBoolean();n=bounded(b.readVarInt(),MAX_PROJECTS);var sites=new ArrayList<Site>();for(int i=0;i<n;i++)sites.add(new Site(b.readUUID(),b.readUtf(96),b.readEnum(Project.Scope.class),b.readEnum(Project.Status.class),new ProjectZone(dim,b.readEnum(ProjectZone.Shape.class),b.readInt(),b.readInt(),b.readInt(),b.readInt())));
        return new NearbyScenePayload(dim,sharing,friends,replace,sites);
    });
    private static int bounded(int value,int maximum){if(value<0||value>maximum)throw new IllegalArgumentException("Nearby count");return value;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
