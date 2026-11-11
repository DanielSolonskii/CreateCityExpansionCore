package com.example.examplemod.client.map;
import com.example.examplemod.network.*;
import com.example.examplemod.city.CityPublicProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** HUD owns only its view; all terrain uses CityMapPanel and TerrainCache. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class MinimapClient {
 private MinimapClient(){}
 private static CityMapPanel panel;
 private static ResourceLocation dimension;
 private static net.minecraft.core.BlockPos anchor;
 private static long nextRequest,pendingUntil;
 private static UUID token;
 private static int expected,total;
 private static final List<CityPublicProfile> staging=new ArrayList<>();
 private static List<CityPublicProfile> cities=List.of();
 private static com.example.examplemod.client.ProjectClientState.Marker focusedProject;
 public static void focus(com.example.examplemod.projects.Project p){focusedProject=new com.example.examplemod.client.ProjectClientState.Marker(p.id(),p.title(),p.scope(),p.zone(),p.status(),p.category());}
 private static MinimapPreferences settings;
 public static MinimapPreferences settings(){if(settings==null)settings=new MinimapPreferences(Minecraft.getInstance().gameDirectory.toPath().resolve("config/ccec-minimap.properties"));return settings;}
 private static boolean visible(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&mc.screen==null&&!mc.options.hideGui&&!mc.getDebugOverlay().showDebugScreen()&&settings().enabled;}
 private static void release(){if(panel!=null)panel.releaseTerrainTextures();panel=null;anchor=null;dimension=null;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();
  if(mc.player!=null&&mc.screen==null){
   while(MinimapKeys.TOGGLE.consumeClick()){settings().enabled=!settings().enabled;settings().save();}
   if(MinimapKeys.SETTINGS.consumeClick())mc.setScreen(new com.example.examplemod.client.screen.MinimapSettingsScreen());
  }
  if(!visible()){release();pendingUntil=0;return;}
  var dim=mc.level.dimension().location();
  if(dimension!=null&&!dimension.equals(dim)){release();nextRequest=0;cities=List.of();}
  long now=net.minecraft.Util.getMillis();
  if(now>=nextRequest){nextRequest=now+5000;pendingUntil=now+4000;PacketDistributor.sendToServer(new RequestMinimapPayload());}
 }
 public static void directory(CityDirectoryPayload p){
  var mc=Minecraft.getInstance();if(!visible()||net.minecraft.Util.getMillis()>pendingUntil||!p.dimension().equals(mc.level.dimension().location()))return;
  if(p.offset()==0){token=p.token();expected=0;total=p.total();staging.clear();
   if(panel==null||!p.anchor().equals(anchor)||!p.dimension().equals(dimension)){
    release();anchor=p.anchor();dimension=p.dimension();panel=new CityMapPanel(anchor,dimension,List.of());panel.setDrawInternalControls(false);
   }
  }
  if(!p.token().equals(token)||p.offset()!=expected||p.total()!=total)return;
  staging.addAll(p.cities());expected+=p.cities().size();
  if(expected<total&&!p.cities().isEmpty())PacketDistributor.sendToServer(new RequestMinimapPayload(p.token(),expected));
  else {cities=List.copyOf(staging);pendingUntil=0;}
 }
 public static void tile(CityMapTilePayload p){if(panel!=null)panel.receive(p);}
 @SubscribeEvent public static void render(RenderGuiEvent.Post event){
  if(!visible()||panel==null)return;
  var mc=Minecraft.getInstance();var g=event.getGuiGraphics();var s=settings();
  var layout=MinimapLayout.view(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),s.size,s.corner);
  int size=layout.size(),x=layout.x(),y=layout.y();if(size<64)return;
  // Header/footer stay inside one frame. Vanilla boss bars occupy the centre, not this corner.
  g.fill(layout.frameX(),layout.frameY(),layout.frameX()+layout.frameWidth(),layout.frameY()+layout.frameHeight(),0xED1C252A);
  g.renderOutline(layout.frameX(),layout.frameY(),layout.frameWidth(),layout.frameHeight(),0xFF536064);
  g.fill(x+8,y-3,x+size-8,y-2,0xFFD1A75F);
  panel.renderMinimap(g,x,y,size,s.zoom);
  g.enableScissor(x,y,x+size,y+size);
  if(s.cities)for(var city:cities){int cx=panel.worldToScreenX(city.pos().getX()),cy=panel.worldToScreenY(city.pos().getZ());
   if(cx<x-4||cy<y-4||cx>x+size+4||cy>y+size+4)continue;
   g.fill(cx-3,cy-3,cx+4,cy+4,0xF0192328);g.renderOutline(cx-2,cy-2,5,5,0xFF000000|city.colour());
  }
  if(s.projects)for(var marker:focusedProject==null?List.<com.example.examplemod.client.ProjectClientState.Marker>of():List.of(focusedProject)){var zone=marker.zone();if(!zone.dimension().equals(dimension))continue;
   int px=panel.worldToScreenX((zone.x1()+zone.x2())/2d),py=panel.worldToScreenY((zone.z1()+zone.z2())/2d);
   if(px<x||py<y||px>=x+size||py>=y+size)continue;
   g.renderOutline(px-2,py-2,5,5,marker.scope()==com.example.examplemod.projects.Project.Scope.SERVER?0xFFE1B95D:0xFF77BDD2);
  }
  int alpha=(100-s.opacity)*255/100;if(alpha>0)g.fill(x,y,x+size,y+size,(alpha<<24)|0x00182024);
  FriendMinimapLayer.render(g,panel,layout);
  // Player pointer remains the strongest mark over cities and project markers.
  MapMarkerRenderer.renderPlayer(g,panel.worldToScreenX(mc.player.getX()),panel.worldToScreenY(mc.player.getZ()),net.minecraft.world.item.ItemStack.EMPTY,false,false,mc.player.getYRot()+180);
  g.disableScissor();
  g.drawCenteredString(mc.font,"N",x+size/2,y+3,0xFFF2D49B);
  g.drawString(mc.font,"X: "+mc.player.getBlockX(),x+3,layout.footerY(),0xFFCCD6D6,false);
  g.drawString(mc.font,"Z: "+mc.player.getBlockZ(),x+3,layout.footerY()+11,0xFFCCD6D6,false);
 }
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){release();cities=List.of();staging.clear();token=null;focusedProject=null;nextRequest=pendingUntil=0;MapBorderLayer.clear();}
}
