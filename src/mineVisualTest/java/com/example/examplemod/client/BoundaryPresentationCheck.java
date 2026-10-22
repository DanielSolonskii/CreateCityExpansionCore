package com.example.examplemod.client;
import com.example.examplemod.city.CityPlot;
import net.minecraft.world.level.Level;
import java.nio.file.*;
import java.util.*;
public final class BoundaryPresentationCheck {
 private static int checks;
 private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  var dim=Level.OVERWORLD.location();var single=new BoundaryGeometry(Set.of(new CityPlot(dim,0,0)));
  require(single.edgeCount()==4&&single.cornerCount()==4,"one plot has four real corners");
  var joined=new BoundaryGeometry(Set.of(new CityPlot(dim,0,0),new CityPlot(dim,1,0)));
  require(joined.edgeCount()==6&&joined.cornerCount()==4,"no internal edge or straight seam beacon");
  for(var m:joined.nearby(50,25,96))require(!(m.x()==50&&m.z()>0&&m.z()<50),"no internal boundary");
  require(joined.nearby(10000,10000,96).isEmpty(),"far geometry culled");
  require(joined.nearby(50,25,96).size()<=BoundaryGeometry.MAX_MARKS,"bounded sample budget");
  require(BoundaryGeometry.fade(96,96)==0&&BoundaryGeometry.fade(0,96)==1,"distance fades bounded");
  var negative=new BoundaryGeometry(Set.of(new CityPlot(dim,-1,-1)));
  require(negative.nearby(-25,-25,96).stream().allMatch(m->m.x()<=0&&m.z()<=0),"negative coordinates remain correct");
  var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{
   var view=new com.example.examplemod.network.CityBoundariesPayload.CityView(UUID.randomUUID(),"City",0x97A95A,true,Set.of(new CityPlot(dim,-1,0)));
   var payload=new com.example.examplemod.network.CityBoundariesPayload(dim,new net.minecraft.core.BlockPos(-12,64,5),List.of(view));
   com.example.examplemod.network.CityBoundariesPayload.STREAM_CODEC.encode(buffer,payload);
   require(com.example.examplemod.network.CityBoundariesPayload.STREAM_CODEC.decode(buffer).equals(payload),"wire preserves dimension, colour, permission and plots");
   buffer.clear();buffer.writeResourceLocation(dim);buffer.writeBlockPos(net.minecraft.core.BlockPos.ZERO);buffer.writeVarInt(33);
   boolean refused=false;try{com.example.examplemod.network.CityBoundariesPayload.STREAM_CODEC.decode(buffer);}catch(IllegalArgumentException expected){refused=true;}
   require(refused,"oversized city count rejected");
  }finally{buffer.release();}
  System.out.println("PASS: "+checks+" boundary geometry and packet checks");
 }
}
