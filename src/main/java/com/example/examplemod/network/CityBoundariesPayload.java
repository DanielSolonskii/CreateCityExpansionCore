package com.example.examplemod.network;
import com.example.examplemod.city.CityPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.*;
public record CityBoundariesPayload(ResourceLocation dimension, BlockPos anchor, List<CityView> cities) implements CustomPacketPayload {
 public static final int MAX_CITIES=32,MAX_PLOTS=4096;
 public record CityView(UUID id,String name,int colour,boolean canBuild,Set<CityPlot> plots){public CityView{plots=Set.copyOf(plots);}}
 public CityBoundariesPayload{cities=List.copyOf(cities);}
 public static final Type<CityBoundariesPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","city_boundaries"));
 public static final StreamCodec<RegistryFriendlyByteBuf,CityBoundariesPayload> STREAM_CODEC=StreamCodec.of((b,p)->{
  b.writeResourceLocation(p.dimension);b.writeBlockPos(p.anchor);b.writeVarInt(p.cities.size());
  for(var c:p.cities){b.writeUUID(c.id);b.writeUtf(c.name,128);b.writeInt(c.colour);b.writeBoolean(c.canBuild);b.writeVarInt(c.plots.size());for(var plot:c.plots){b.writeVarInt(plot.plotX());b.writeVarInt(plot.plotZ());}}
 },b->{
  var dim=b.readResourceLocation();var anchor=b.readBlockPos();int count=b.readVarInt();if(count<0||count>MAX_CITIES)throw new IllegalArgumentException("Boundary city budget");
  List<CityView> cities=new ArrayList<>();int total=0;
  for(int i=0;i<count;i++){UUID id=b.readUUID();String name=b.readUtf(128);int colour=b.readInt();boolean access=b.readBoolean();int n=b.readVarInt();if(n<0||n>MAX_PLOTS-total)throw new IllegalArgumentException("Boundary plot budget");total+=n;Set<CityPlot> plots=new HashSet<>();for(int j=0;j<n;j++){var plot=new CityPlot(dim,b.readVarInt(),b.readVarInt());if(!plot.isValid())throw new IllegalArgumentException("Invalid boundary plot");plots.add(plot);}cities.add(new CityView(id,name,colour,access,plots));}
  return new CityBoundariesPayload(dim,anchor,cities);
 });
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
