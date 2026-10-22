package com.example.examplemod.client.map;
import java.nio.file.*;
/** Coverage, clipping, scale and persistence checks; no Minecraft render context. */
public final class MapHudCheck {
 private static int checks;
 private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  var b=new MapBorderGeometry.Bounds(-50,-20,75,90);
  require(b.contains(-50,-20),"inclusive minimum");require(!b.contains(75,90),"exclusive maximum");
  require(b.intersects(-100,0,-49,1),"crossing border tile");require(!b.intersects(-100,0,-50,1),"outside tile touching edge");
  require(b.covers(74,89,75,90),"last inside block remains available");
  require(!b.covers(75,89,76,90),"outside block is unavailable");
  // Every viewport pixel outside a border belongs to precisely one mask; inside belongs to none.
  for(int l:new int[]{-30,5,20,80})for(int t:new int[]{-20,7,25,70}){
   int r=l+30,bottom=t+25;var rects=MapBorderGeometry.outside(5,7,60,48,l,t,r,bottom);
   for(int y=7;y<55;y++)for(int x=5;x<65;x++){
    int count=0;for(var rect:rects)if(x>=rect.x()&&x<rect.x()+rect.width()&&y>=rect.y()&&y<rect.y()+rect.height())count++;
    require(count==((x>=l&&x<r&&y>=t&&y<bottom)?0:1),"mask gap/overlap");
   }
  }
  var crossing=MapLineClip.clip(Integer.MIN_VALUE,15,Integer.MAX_VALUE,15,10,10,30,30);
  require(crossing!=null&&crossing.x1()==10&&crossing.x2()==30,"long cross-map track clips safely");
  require(MapLineClip.clip(-100,-100,-10,-10,0,0,40,40)==null,"offscreen track rejected");
  var vertical=MapLineClip.clip(20,-100,20,100,10,10,30,30);
  require(vertical!=null&&vertical.y1()==10&&vertical.y2()==30,"vertical rail retains thickness bounds");
  for(int width:new int[]{320,427,640,960})for(int height:new int[]{180,240,360,540}){
   for(int corner=0;corner<2;corner++)for(int size:new int[]{96,124,160}){
    var view=MinimapLayout.view(width,height,size,corner);
    require(view.x()>=0&&view.y()>=0&&view.x()+view.size()<width&&view.y()+view.size()+18<height,"HUD bounds");
   }
   var settings=MinimapLayout.settings(width,height);
   require(settings.x()>=0&&settings.y()>=0&&settings.x()+settings.width()<=width&&settings.y()+settings.height()<=height,"settings adaptive bounds");
   for(int i=0;i<7;i++)require(settings.buttonX(i)>=settings.x()&&settings.buttonX(i)+settings.buttonWidth()<settings.x()+settings.width()&&settings.buttonY(i)+20<settings.y()+settings.height()-28,"settings button bounds");
  }
  Path dir=Files.createTempDirectory("ccec-map-hud-");Path file=dir.resolve("preferences.properties");
  try{
   var preferences=new MinimapPreferences(file);preferences.enabled=false;preferences.zoom=2;preferences.size=156;preferences.corner=1;preferences.save();
   var loaded=new MinimapPreferences(file);require(!loaded.enabled&&loaded.zoom==2&&loaded.size==156&&loaded.corner==1,"settings survive restart");
   Files.writeString(file,"zoom=NaN\nsize=999999\nopacity=-100\ncorner=9\n");
   loaded=new MinimapPreferences(file);require(loaded.zoom==.5&&loaded.size==160&&loaded.opacity==40&&loaded.corner==1,"corrupt settings clamped");
  }finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
  var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
  try{
   var dimension=net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
   var border=new com.example.examplemod.network.MapBorderPayload(dimension,-123.5,77.25,4096,2048,30000);
   com.example.examplemod.network.MapBorderPayload.STREAM_CODEC.encode(buffer,border);
   require(com.example.examplemod.network.MapBorderPayload.STREAM_CODEC.decode(buffer).equals(border),"border codec retains centre and lerp");
   buffer.clear();
   var request=new com.example.examplemod.network.RequestMinimapPayload(java.util.UUID.randomUUID(),32);
   com.example.examplemod.network.RequestMinimapPayload.STREAM_CODEC.encode(buffer,request);
   require(com.example.examplemod.network.RequestMinimapPayload.STREAM_CODEC.decode(buffer).equals(request),"minimap pagination token survives wire");
   buffer.clear();
   var page=new com.example.examplemod.network.CityDirectoryPayload(java.util.UUID.randomUUID(),net.minecraft.core.BlockPos.ZERO,dimension,0,0,java.util.List.of(dimension),java.util.List.of());
   var reply=new com.example.examplemod.network.MinimapDirectoryPayload(page);
   com.example.examplemod.network.MinimapDirectoryPayload.STREAM_CODEC.encode(buffer,reply);
   require(com.example.examplemod.network.MinimapDirectoryPayload.STREAM_CODEC.decode(buffer).equals(reply),"isolated HUD response reuses public directory codec");
   require(!com.example.examplemod.network.MinimapDirectoryPayload.TYPE.equals(com.example.examplemod.network.CityDirectoryPayload.TYPE),"late HUD response cannot enter full atlas handler");
  }finally{buffer.release();}
  System.out.println("Map HUD: "+checks+" checks passed");
 }
}
