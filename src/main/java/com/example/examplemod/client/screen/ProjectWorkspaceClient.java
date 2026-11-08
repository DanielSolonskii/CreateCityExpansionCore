package com.example.examplemod.client.screen;
import com.example.examplemod.client.*;

import com.example.examplemod.client.map.*;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.network.*;
import com.example.examplemod.projects.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Voluntary planning cursors, never live player positions; one request per second while visible. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class ProjectWorkspaceClient {
    private static Project project; private static CompoundTag room=new CompoundTag(); private static UUID pending;
    private static String label="",result="ok"; private static long sent,received; private static boolean focus,armed,mutation;
    private static int[] first;private static WorldMapUiLayout.Rect bounds,toolbar;private static CityMapPanel panel;
    private ProjectWorkspaceClient(){}
    public static void accept(WorkspaceState payload){CommunityDashboard.acceptWorkspace(payload);ProjectWorkspaceScreen.accept(payload);var data=payload.data();if(pending==null||!data.hasUUID("Request")||!pending.equals(data.getUUID("Request")))return;pending=null;result=data.getString("Result");if(!data.hasUUID("ProjectId")&&!result.equals("permission")){mutation=false;return;}received=net.minecraft.Util.getMillis();if(!data.hasUUID("ProjectId")||project==null||!project.id().equals(data.getUUID("ProjectId"))||!data.getBoolean("CanDiscuss")){clear();return;}if(data.getBoolean("RoomUnchanged")){data.put("Chat",room.getList("Chat",10).copy());data.put("Marks",room.getList("Marks",10).copy());}room=data;project=ProjectCodec.read(data.getCompound("Project"));if(mutation){CommunityFx.sound(result.equals("ok")?"success":"error");mutation=false;if(result.equals("ok")){first=null;armed=false;}}}
    public static void openMap(Screen parent,Project value,CompoundTag snapshot,String text){clear();project=value;room=snapshot.copy();label=text;focus=true;received=0;CityWorldMapSession.INSTANCE.awaitAnchor();var map=new PolishedWorldMapScreen(CityWorldMapSession.INSTANCE);map.planningDimension(value.zone().dimension());Minecraft.getInstance().setScreen(SocialHubScreen.retainMap(map));PacketDistributor.sendToServer(new RequestCityDirectoryPayload(value.zone().dimension(),RequestCityDirectoryPayload.NEW,0));}
    public static void clear(){project=null;room=new CompoundTag();pending=null;panel=null;bounds=toolbar=null;first=null;armed=focus=mutation=false;sent=received=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    private static void request(WorkspaceRequest.Action action,int x1,int z1,int x2,int z2,boolean cursor){if(project==null||pending!=null)return;pending=UUID.randomUUID();sent=net.minecraft.Util.getMillis();mutation=action!=WorkspaceRequest.Action.VIEW;PacketDistributor.sendToServer(new WorkspaceRequest(pending,action,project.id(),null,project.revision(),room.getLong("RoomRevision"),0,6,0,cursor,action==WorkspaceRequest.Action.MARK?label:"",x1,z1,x2,z2));}
    public static void render(GuiGraphics g,CityMapPanel map,ResourceLocation dimension,WorldMapUiLayout.Rect viewport,int mouseX,int mouseY,boolean visible){bounds=toolbar=null;panel=null;if(project==null)return;if(!dimension.equals(project.zone().dimension())){clear();return;}if(!visible)return;bounds=viewport;panel=map;var mc=Minecraft.getInstance();long now=net.minecraft.Util.getMillis();if(focus){map.centerOn(BlockPos.containing(((double)project.zone().minX()+project.zone().maxX())/2,0,((double)project.zone().minZ()+project.zone().maxZ())/2));focus=false;}
        if(pending!=null&&now-sent>8000){pending=null;mutation=false;result="timeout";received=0;}int wx=(int)Math.floor(map.screenToWorldX(mouseX)),wz=(int)Math.floor(map.screenToWorldZ(mouseY));if(pending==null&&now-sent>=1000)request(WorkspaceRequest.Action.VIEW,wx,wz,0,0,viewport.contains(mouseX,mouseY));
        g.enableScissor(viewport.x(),viewport.y(),viewport.right(),viewport.bottom());
        if(now-received<8000){for(Tag raw:room.getList("Marks",10)){var t=(CompoundTag)raw;int x1=map.worldToScreenX(t.getInt("X1")),y1=map.worldToScreenY(t.getInt("Z1")),x2=map.worldToScreenX(t.getInt("X2")),y2=map.worldToScreenY(t.getInt("Z2"));int color=t.getBoolean("Approved")?0xff82d5b0:0xffe6ba6e;line(g,x1,y1,x2,y2,color,!t.getBoolean("Approved"));if(viewport.contains(x1,y1)){g.fill(x1-2,y1-2,x1+3,y1+3,color);g.fill(x1+5,y1-5,x1+10+Math.min(160,mc.font.width(t.getString("Text"))),y1+8,0xe010191d);g.drawString(mc.font,fit(mc.font,net.minecraft.network.chat.Component.literal(t.getString("Text")),160),x1+8,y1-2,color,false);}}
            for(Tag raw:room.getList("Presence",10)){var t=(CompoundTag)raw;if(!t.getBoolean("Cursor")||t.getUUID("Id").equals(mc.player.getUUID()))continue;int x=map.worldToScreenX(t.getInt("X")),y=map.worldToScreenY(t.getInt("Z"));if(!viewport.contains(x,y))continue;g.hLine(x-4,x+4,y,0xff8dccdd);g.vLine(x,y-4,y+4,0xff8dccdd);g.drawString(mc.font,t.getString("Name"),x+7,y+4,0xff8dccdd,false);}
        }
        if(first!=null){int x=map.worldToScreenX(first[0]),y=map.worldToScreenY(first[1]);line(g,x,y,mouseX,mouseY,0xffe6ba6e,true);g.renderOutline(x-3,y-3,7,7,0xffe6ba6e);}
        int width=Math.min(viewport.width()-12,360);toolbar=new WorldMapUiLayout.Rect(viewport.x()+6,viewport.y()+6,width,34);panel(g,toolbar.x(),toolbar.y(),toolbar.width(),toolbar.height());g.drawString(mc.font,fit(mc.font,CommunityDashboard.tr(armed?(first==null?"pick_first":"pick_second"):"planning_title",label),width-80),toolbar.x()+8,toolbar.y()+7,TEXT,false);g.drawString(mc.font,result.equals("ok")?CommunityDashboard.tr("plan_proposals"):result.equals("invalid")?CommunityDashboard.tr("plan_bounds"):net.minecraft.network.chat.Component.translatable("gui.createcityexpansioncore.projects.result."+result),toolbar.x()+8,toolbar.y()+21,MUTED,false);g.drawString(mc.font,CommunityDashboard.tr(armed?"cancel":"draw"),toolbar.right()-63,toolbar.y()+12,ACCENT,false);g.disableScissor();
    }
    public static boolean click(double x,double y,int button){if(project==null||bounds==null||panel==null)return false;if(toolbar!=null&&toolbar.contains(x,y)){if(button==0){armed=!armed;first=null;CommunityFx.sound("click");}return true;}if(!armed||button!=0||!bounds.contains(x,y))return false;if(pending!=null)return true;int wx=(int)Math.floor(panel.screenToWorldX(x)),wz=(int)Math.floor(panel.screenToWorldZ(y));if(first==null)first=new int[]{wx,wz};else request(WorkspaceRequest.Action.MARK,first[0],first[1],wx,wz,false);return true;}
    private static void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color,boolean dashed){var c=MapLineClip.clip(x1,y1,x2,y2,bounds.x(),bounds.y(),bounds.right()-1,bounds.bottom()-1);if(c==null)return;int dx=Math.abs(c.x2()-c.x1()),dy=Math.abs(c.y2()-c.y1()),sx=c.x1()<c.x2()?1:-1,sy=c.y1()<c.y2()?1:-1,error=dx-dy,x=c.x1(),y=c.y1(),i=0;while(true){if(!dashed||i%9<5)g.fill(x,y,x+1,y+1,color);if(x==c.x2()&&y==c.y2())break;int e=2*error;if(e>-dy){error-=dy;x+=sx;}if(e<dx){error+=dx;y+=sy;}i++;}}
}
