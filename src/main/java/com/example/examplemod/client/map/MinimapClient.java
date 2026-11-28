package com.example.examplemod.client.map;
import com.example.examplemod.network.*;
import com.example.examplemod.citymap.CityMapNetwork;
import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.ProjectClientState;
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
 private static final HudPanelLifecycle<CityMapPanel> lifecycle =
         new HudPanelLifecycle<>(CityMapPanel::releaseTerrainTextures, MinimapClient::resetScope);
 private static ResourceLocation dimension;
 private static net.minecraft.core.BlockPos anchor;
 private static long nextRequest,pendingUntil;
 private static com.example.examplemod.client.DirectoryPages pages;
 private static UUID token, viewId, requestId, accepted;
 private static int expected,total;
 private static final List<CityPublicProfile> staging=new ArrayList<>();
 private static List<CityPublicProfile> cities=List.of();
 private static MapViewBindings.Binding featureScope;
 private static UUID featureRequest;
 private static long nextFeatures;
 private static ProjectClientState.Marker focusedProject;
 private static UUID focusWorld,focusMember;
 private static long focusReceipt,focusRevocation;
 public static void focus(com.example.examplemod.projects.Project p){
  var world=CityPublicDirectory.worldId(p.zone().dimension());
  if(world==null||!ProjectClientState.hasMapSnapshot())return;
  if(focusedProject!=null&&focusedProject.id().equals(p.id())&&focusReceipt==ProjectClientState.mapReceipt())return;
  focusedProject=new ProjectClientState.Marker(p.id(),p.title(),p.scope(),p.zone(),p.status(),p.category(),p.city());
  focusWorld=world;focusMember=CityPublicDirectory.hasPrivateAccess()?ProjectClientState.city:null;
  focusReceipt=ProjectClientState.mapReceipt();focusRevocation=ProjectClientState.revocation();
 }
 public static void refreshFocusedProject(){
  if(focusedProject==null)return;
  UUID id=focusedProject.id();
  focusedProject=ProjectClientState.mapMarkers().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);
  focusReceipt=ProjectClientState.mapReceipt();
 }
 private static MinimapPreferences settings;
 static void applyDisplay(CityMapPanel panel) {
  if(settings!=null) panel.applyDisplaySettings(settings);
 }
 public static MinimapPreferences settings(){if(settings==null)settings=new MinimapPreferences(Minecraft.getInstance().gameDirectory.toPath().resolve("config/ccec-minimap.properties"));return settings;}
 private static boolean visible(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&mc.screen==null&&!mc.options.hideGui&&!mc.getDebugOverlay().showDebugScreen()&&settings().enabled;}
 private static void cancelDirectory(){
  pendingUntil=0;token=null;pages=null;expected=total=0;staging.clear();
 }
 private static void resetScope(){
  if(viewId!=null && Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new CloseMapViewPayload(viewId));
  viewId=requestId=accepted=null;
  MapViewBindings.invalidate(true);
  featureScope=null;featureRequest=null;nextFeatures=0;CityCartographyLayer.minimapFeatures(List.of());
  cancelDirectory();anchor=null;dimension=null;cities=List.of();focusedProject=null;nextRequest=0;
 }
 private static void createPanel(){
  var panel=new CityMapPanel(anchor,dimension,List.of());
  panel.setDrawInternalControls(false);
  lifecycle.install(panel);
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();
  if(mc.player!=null&&mc.screen==null){
   while(MinimapKeys.TOGGLE.consumeClick()){settings().enabled=!settings().enabled;settings().save();}
   if(MinimapKeys.SETTINGS.consumeClick())mc.setScreen(new com.example.examplemod.client.screen.MinimapSettingsScreen());
  }
  long now=net.minecraft.Util.getMillis();
  boolean show=visible();
  lifecycle.update(mc.player==null?null:mc.level, mc.player!=null && settings().enabled, show, now);
  if(!show){
   if(pendingUntil!=0){cancelDirectory();nextRequest=0;}
   return;
  }
  if(lifecycle.panel()==null && anchor!=null)createPanel();
  if(now>=nextRequest && (pendingUntil==0 || now>=pendingUntil)){
   MapDiagnostics.request(pendingUntil!=0);cancelDirectory();nextRequest=now+5000;pendingUntil=now+15000;
   if(viewId==null)viewId=UUID.randomUUID();
   requestId=UUID.randomUUID();
   pages=new com.example.examplemod.client.DirectoryPages(viewId,requestId,mc.level.dimension().location());
   PacketDistributor.sendToServer(new RequestMinimapPayload(RequestCityDirectoryPayload.NEW,0,viewId,requestId));
  }
 }
 public static void directory(CityDirectoryPayload p){
  var mc=Minecraft.getInstance();
  long now=net.minecraft.Util.getMillis();
  if(!visible()||!lifecycle.visibleFor(mc.level)||pendingUntil==0||now>pendingUntil||pages==null||!pages.accept(p)){MapDiagnostics.stale();return;}
  if(p.offset()==0){
   com.example.examplemod.client.journey.JourneyClient.confirmedMapAccess(p.dimension(), p.worldId(), p.memberCityId());
   accepted=p.requestId();
   if(focusedProject!=null && (!Objects.equals(focusWorld,p.worldId())
           ||focusMember!=null&&!focusMember.equals(p.memberCityId())))focusedProject=null;
   MapViewBindings.confirm(true,p.anchor(),p.dimension(),p.viewId(),p.worldId());
   var binding=MapViewBindings.binding(true,p.anchor(),p.dimension());
   if(!binding.equals(featureScope)){
    featureScope=binding;featureRequest=null;nextFeatures=0;CityCartographyLayer.minimapFeatures(List.of());
   }
   if(settings().cities && now>=nextFeatures && mc.getConnection().hasChannel(CityMapNetwork.Request.TYPE)){
    featureRequest=UUID.randomUUID();nextFeatures=now+15000;
    // The geometry-only protocol omits train positions, platforms and passenger tracks.
    PacketDistributor.sendToServer(new CityMapNetwork.Request(featureRequest,binding.viewId(),binding.worldId(),
            binding.dimension(),CityMapNetwork.ALL,CityMapNetwork.Action.LIST,CityMapNetwork.ALL,0,null));
   }
   token=p.token();expected=0;total=p.total();staging.clear();
   if(lifecycle.panel()==null||!p.anchor().equals(anchor)||!p.dimension().equals(dimension)){
    cities=List.of();anchor=p.anchor();dimension=p.dimension();createPanel();
   }
  }
  if(!p.token().equals(token)||p.offset()!=expected||p.total()!=total){MapDiagnostics.stale();return;}
  staging.addAll(p.cities());expected+=p.cities().size();
  if(expected<total){
   if(p.cities().isEmpty())return;
   pendingUntil=now+15000;MapDiagnostics.request(false);
   PacketDistributor.sendToServer(new RequestMinimapPayload(p.token(),expected,viewId,requestId));
  }else {cities=List.copyOf(staging);cancelDirectory();}
 }
 public static boolean acceptsBorder(MapBorderPayload p){
  return p.viewId().equals(viewId) && p.requestId().equals(accepted) && p.dimension().equals(dimension);
 }
 static boolean receiveFeatures(CityMapNetwork.Reply reply){
  if(!reply.nonce().equals(featureRequest))return false;
  featureRequest=null;
  if(featureScope!=null && featureScope.equals(MapViewBindings.binding(true,anchor,dimension))){
   CityCartographyLayer.minimapFeatures("ok".equals(reply.status())?reply.features():List.of());
  }
  return true;
 }
 public static void tile(CityMapTilePayload p){
  var panel=lifecycle.panel();
  if(panel!=null && visible() && lifecycle.visibleFor(Minecraft.getInstance().level))panel.receive(p);
 }
 @SubscribeEvent public static void render(RenderGuiEvent.Post event){
  var panel=lifecycle.panel();
  if(!visible()||!lifecycle.visibleFor(Minecraft.getInstance().level)||panel==null)return;
  var mc=Minecraft.getInstance();var g=event.getGuiGraphics();var s=settings();
  var layout=MinimapLayout.view(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),s.size,s.corner);
  int size=layout.size(),x=layout.x(),y=layout.y();if(size<64)return;
  // Header/footer stay inside one frame. Vanilla boss bars occupy the centre, not this corner.
  g.fill(layout.frameX(),layout.frameY(),layout.frameX()+layout.frameWidth(),layout.frameY()+layout.frameHeight(),0xED1C252A);
  g.renderOutline(layout.frameX(),layout.frameY(),layout.frameWidth(),layout.frameHeight(),0xFF536064);
  g.fill(x+8,y-3,x+size-8,y-2,0xFFD1A75F);
  panel.renderMinimap(g,x,y,size,s.zoom);
  g.enableScissor(x,y,x+size,y+size);
  if(s.cities && featureScope!=null && featureScope.equals(MapViewBindings.binding(true,anchor,dimension)))
   CityCartographyLayer.drawMinimap(g,panel,x,y,size);
  if(s.cities)for(var city:cities){int cx=panel.worldToScreenX(city.pos().getX()),cy=panel.worldToScreenY(city.pos().getZ());
   if(cx<x-4||cy<y-4||cx>x+size+4||cy>y+size+4)continue;
   g.fill(cx-3,cy-3,cx+4,cy+4,0xF0192328);g.renderOutline(cx-2,cy-2,5,5,0xFF000000|city.colour());
  }
  if(focusedProject!=null && (!ProjectClientState.recentReceipt(focusReceipt,System.nanoTime())
          || focusRevocation!=ProjectClientState.revocation()))focusedProject=null;
  var binding=MapViewBindings.binding(true,anchor,dimension);
  if(s.projects && focusedProject!=null && binding!=null && binding.worldId().equals(focusWorld)
          && focusedProject.zone().dimension().equals(dimension)){var marker=focusedProject;var zone=marker.zone();
   int px=panel.worldToScreenX((zone.x1()+zone.x2())/2d),py=panel.worldToScreenY((zone.z1()+zone.z2())/2d);
   if(px>=x&&py>=y&&px<x+size&&py<y+size)
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
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){
  lifecycle.close();resetScope();MapBorderLayer.clear();MapDiagnostics.report();
 }
}
