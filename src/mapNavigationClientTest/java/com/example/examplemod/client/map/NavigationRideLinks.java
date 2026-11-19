package com.example.examplemod.client.map;

import com.example.examplemod.client.logistics.*;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.navigation.JourneyTripGameTest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import org.lwjgl.glfw.GLFW;
import static com.example.examplemod.client.map.NavigationClientProbe.*;

/** Checks the actual server-backed pages of each selected atlas card and their return locations. */
final class NavigationRideLinks {
    private static int page,step,ticks;
    private static double x,z,zoom;
    private static UUID markerId;
    private static String evidence="";
    @net.neoforged.neoforge.gametest.GameTestHolder("createcityexpansioncore")
    @net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
    public static final class Scene {
        @net.minecraft.gametest.framework.GameTest(template="atlas_interchange",timeoutTicks=600)
        public static void atlasInfrastructureCards(net.minecraft.gametest.framework.GameTestHelper test){
            JourneyTripGameTest.prepareInfrastructureCards(test);
        }
    }
    private static void advance(){step++;ticks=0;}
    static boolean tick(Minecraft mc,JourneyTripGameTest.Interactive scene)throws Exception{
        if(page==3)return true;
        var atlas=mc.screen instanceof PolishedWorldMapScreen a?a:null;
        var map=atlas==null?null:(CityMapPanel)field(atlas,"map");
        if(step==6 && atlas!=null && map!=null){
            String state="Return page "+page+" selected="+(map.getSelection()==null?"none":map.getSelection().id())
                    +" pending="+field(atlas,"pendingSelection")+" directory="+LogisticsMapOverlay.hasDirectory(map.dimensionId())
                    +" kind="+LogisticsMapOverlay.markerKind(markerId,map.dimensionId())+" tradeDone="+AtlasTradeDirectory.finishedRestore(markerId)
                    +" projectReady="+com.example.examplemod.client.ProjectClientState.hasMapSnapshot()+" notice="+field(atlas,"cardNotice");
            if(!state.equals(evidence)){evidence=state;NavigationRideProbe.log(state);}
        }
        if(++ticks>520)throw new AssertionError("Infrastructure page "+page+", step "+step+" on "+mc.screen
                +", wanted "+markerId+", selection "+(map==null?null:map.getSelection())
                +", awaiting "+(atlas==null?null:field(atlas,"pendingSelection")));
        if(step==0){if(atlas==null){if(ticks==1)atlasKey();return false;}if(map!=null)advance();}
        else if(step==1){
            if(!(boolean)field(atlas,"searchOpen"))atlas.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);
            ((EditBox)field(atlas,"searchBox")).setValue("");advance();
        }else if(step==2){
            var rows=(List<?>)field(atlas,"searchResults");int index=-1;
            for(int i=0;i<rows.size();i++){
                var row=(AtlasSearch.Entry)rows.get(i);
                if(page==0 && row.type()==AtlasSearch.Type.LOGISTICS && row.id().equals(scene.parcel().toString()) ||
                        page==2 && row.type()==AtlasSearch.Type.PROJECT && row.id().equals(scene.project().toString())){index=i;break;}
                if(page==1 && row.type()==AtlasSearch.Type.TRADE){
                    var live=AtlasTradeDirectory.row(UUID.fromString(row.id()),map.dimensionId());
                    if(live!=null && live.object().equals(JourneyTripGameTest.interactiveOffer())){index=i;break;}
                }
            }
            if(index<0)return false;
            markerId=UUID.fromString(((AtlasSearch.Entry)rows.get(index)).id());
            searchFocus(atlas,7);int current=(int)field(atlas,"searchCursor");
            for(int i=0;i<Math.abs(index-current);i++)press(atlas,index<current?GLFW.GLFW_KEY_UP:GLFW.GLFW_KEY_DOWN);
            press(atlas,GLFW.GLFW_KEY_ENTER);advance();
        }else if(step==3 && ticks>16){
            if(map==null || map.getSelection()==null || !markerId.equals(map.getSelection().id()))return false;
            x=map.centerX();z=map.centerZ();zoom=map.zoom();
            if(page==0){if(action(atlas,"INSPECT_SHIPMENT"))advance();}
            else if(action(atlas,page==1?"CARGO":"PROJECT")){advance();advance();}
        }else if(step==4){
            if(LogisticsMapOverlay.inspectionLines(markerId,map.dimensionId()).size()<2)return false;
            if(action(atlas,"CARGO"))advance();
        }else if(step==5){
            if(page==2){
                var project=SocialHubScreen.find(ProjectWorkspaceScreen.class,p->true);
                if(project==null)return false;
                var data=(CompoundTag)field(project,"state");
                if(!data.hasUUID("ProjectId")||!scene.project().equals(data.getUUID("ProjectId")))return false;
            }else{
                if(!(mc.screen instanceof LogisticsCenterScreen center))return false;
                var row=((CompoundTag)field(center,"state")).getCompound("SelectedRow");
                if(!row.hasUUID("Id") || !row.getUUID("Id").equals(page==0?scene.shipment():JourneyTripGameTest.interactiveOffer()))return false;
            }
            NavigationRideProbe.log("Opened page "+page+" for authorized object "+(page==0?scene.shipment():page==1?JourneyTripGameTest.interactiveOffer():scene.project())+", marker "+markerId);
            if(page==0){
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();var parcel=markerId;
                server.execute(()->{
                    var query=new CompoundTag();query.putString("Section","map");
                    var data=com.example.examplemod.cargo.center.CenterView.snapshot(server.getPlayerList().getPlayer(id),query).getCompound("Map");
                    boolean visible=data.getList("Markers",10).stream().map(CompoundTag.class::cast).anyMatch(t->t.hasUUID("Id") && parcel.equals(t.getUUID("Id")));
                    System.out.println("QA card source parcel="+parcel+" visible to connected client="+visible);
                });
            }
            mc.screen.onClose();advance();
            if(mc.screen instanceof PolishedWorldMapScreen returned){
                var context=field(returned,"VIEWS");
                var lookup=context.getClass().getDeclaredMethod("get",Object.class,String.class);
                lookup.setAccessible(true);
                var saved=lookup.invoke(context,mc.getConnection(),get(returned,"viewKey"));
                NavigationRideProbe.log("Saved return target "+(saved==null?null:get(saved,"selected")));
            }
        }else if(step==6 && map!=null && map.getSelection()!=null){
            if(!markerId.equals(map.getSelection().id()))return false;
            if(Math.abs(map.centerX()-x)>1 || Math.abs(map.centerZ()-z)>1 || Math.abs(map.zoom()-zoom)>.01)
                throw new AssertionError("Camera changed after opening infrastructure card "+page);
            NavigationRideProbe.log(switch(page){case 0->"Actual shipment chain and management";case 1->"Published Cargo offer";default->"Shared project";}+
                    ": opened correct object and returned to original selection, camera and zoom.");
            page++;step=0;ticks=0;
        }
        return false;
    }
    private static boolean action(PolishedWorldMapScreen atlas,String action)throws Exception{
        var rows=(List<?>)field(atlas,"cardActions");
        for(int i=0;i<rows.size();i++)if(action.equals(get(rows.get(i),"action").toString())){
            activate(atlas,"card."+i);return true;
        }
        return false;
    }
}
