package com.example.examplemod.client.screen;
import com.example.examplemod.network.*;
import com.example.examplemod.railway.RailwayRouteOption;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Consumer;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
public final class RailwayRouteSelectionScreen extends CityPylonPageScreen{
 private final Screen parent;private final RailwayRouteOptionsPayload payload;private final Consumer<RailwayRouteOption> selector;
 private FreightRouteLayout layout;private int selected=-1,scroll;private LogisticsStyle.Action confirm;
 public RailwayRouteSelectionScreen(Screen parent,RailwayRouteOptionsPayload payload){this(parent,payload,null);}
 public RailwayRouteSelectionScreen(Screen parent,RailwayRouteOptionsPayload payload,Consumer<RailwayRouteOption> selector){super(Component.translatable("gui.createcityexpansioncore.railway.routes.title"));this.parent=parent;this.payload=payload;this.selector=selector;}
 @Override protected void init(){layout=FreightRouteLayout.of(width,height);var a=layout.back();addRenderableWidget(new LogisticsStyle.Action(a.x(),a.y(),a.width(),LogisticsScreen.tr("back"),false,this::onClose));a=layout.confirm();confirm=addRenderableWidget(new LogisticsStyle.Action(a.x(),a.y(),a.width(),LogisticsScreen.tr("confirm_train_route"),true,this::choose));confirm.active=selected>=0;}
 private void choose(){if(selected<0||selected>=payload.options().size())return;var o=payload.options().get(selected);if(selector==null)PacketDistributor.sendToServer(RailwayCommandPayload.simple(payload.pylonPos(),RailwayCommandPayload.Action.SELECT_ROUTE,payload.fleetTrainId(),o.id()));else selector.accept(o);onClose();}
 @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
 @Override public void render(GuiGraphics g,int mx,int my,float pt){
  rounded(g,layout.x(),layout.y(),layout.width(),layout.height(),4,LogisticsStyle.PANEL);g.drawString(font,fit(font,title,layout.width()-20),layout.x()+10,layout.y()+6,ACCENT,false);
  g.drawString(font,fit(font,Component.literal(payload.source()+" → "+payload.destination()),layout.width()-20),layout.x()+10,layout.y()+21,MUTED,false);
  var v=layout.viewport();CityPylonWorkspace.clip(g,v.x(),v.y(),v.x()+v.width(),v.y()+v.height());
  String error=!payload.errorKey().isEmpty()?payload.errorKey():payload.options().isEmpty()?"no_route_options":"";
  if(!error.isEmpty())g.drawWordWrap(font,Component.translatable("gui.createcityexpansioncore.railway.routes."+error),v.x()+5,v.y()+4,v.width()-10,0xffee9a86);
  else for(int i=0;i<payload.options().size();i++){
   var r=layout.row(i,scroll);var o=payload.options().get(i);boolean hover=r.contains(mx,my)&&v.contains(mx,my);rounded(g,r.x(),r.y(),r.width(),r.height(),3,selected==i?0xff383123:hover?0xff2a363b:INSET);
   g.fill(r.x()+1,r.y()+5,r.x()+3,r.y()+r.height()-5,selected==i?ACCENT:EDGE);
   var name=Component.translatable("gui.createcityexpansioncore.railway.routes."+o.strategy().name().toLowerCase(Locale.ROOT));g.drawString(font,fit(font,name,r.width()-14),r.x()+8,r.y()+6,ACCENT,false);
   var price=Component.translatable("gui.createcityexpansioncore.railway.routes.price",o.tariffCost());var eta=Component.translatable("gui.createcityexpansioncore.railway.routes.eta",formatTicks(o.estimatedTicks()));
   g.drawString(font,fit(font,price,r.width()-14),r.x()+8,r.y()+21,TEXT,false);g.drawString(font,fit(font,eta,r.width()-14),r.x()+8,r.y()+36,MUTED,false);
   if(r.width()>350)drawMap(g,o,r.x()+r.width()-135,r.y()+8,124,36);
  }g.disableScissor();super.render(g,mx,my,pt);
 }
 @Override public boolean mouseClicked(double x,double y,int b){if(super.mouseClicked(x,y,b))return true;if(b==0&&layout.viewport().contains(x,y))for(int i=0;i<payload.options().size();i++)if(layout.row(i,scroll).contains(x,y)){selected=i;confirm.active=true;return true;}return false;}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!layout.viewport().contains(x,y))return false;scroll=layout.clamp(scroll+(dy<0?24:-24),payload.options().size());return true;}
 private void drawMap(GuiGraphics g,RailwayRouteOption o,int x,int y,int w,int h){if(o.edges().isEmpty())return;double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,minZ=Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;for(var e:o.edges())for(var n:List.of(e.first(),e.second())){minX=Math.min(minX,n.x());maxX=Math.max(maxX,n.x());minZ=Math.min(minZ,n.z());maxZ=Math.max(maxZ,n.z());}double dx=Math.max(1,maxX-minX),dz=Math.max(1,maxZ-minZ);for(var e:o.edges()){int x1=x+(int)((e.first().x()-minX)/dx*w),z1=y+(int)((e.first().z()-minZ)/dz*h),x2=x+(int)((e.second().x()-minX)/dx*w),z2=y+(int)((e.second().z()-minZ)/dz*h);line(g,x1,z1,x2,z2,0xFF65B9C8);}}
 private static void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color){int dx=Math.abs(x2-x1),sx=x1<x2?1:-1,dy=-Math.abs(y2-y1),sy=y1<y2?1:-1,err=dx+dy;while(true){g.fill(x1,y1,x1+1,y1+1,color);if(x1==x2&&y1==y2)break;int e=2*err;if(e>=dy){err+=dy;x1+=sx;}if(e<=dx){err+=dx;y1+=sy;}}}
 private static String formatTicks(long ticks){long seconds=ticks/20;return seconds<60?seconds+"s":(seconds/60)+"m "+(seconds%60)+"s";}
}
