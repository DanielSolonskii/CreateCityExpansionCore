package com.example.examplemod.client.map;

import com.example.examplemod.client.journey.JourneyClient;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.journey.JourneyModel.Marker;
import com.example.examplemod.navigation.JourneyTripGameTest;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.nio.file.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import static com.example.examplemod.client.map.NavigationClientProbe.*;

/** A single local client searches the real atlas and operates movement, seats and dismount through client input. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class NavigationRideProbe {
    private static int stage, ticks;
    private static long deadline;
    private static Path folder;
    private static JourneyTripGameTest.Interactive scene;
    private static final List<String> departures=new ArrayList<>();
    private static final Set<UUID> boarded=new LinkedHashSet<>(), travelled=new LinkedHashSet<>();
    private static String lastPhase="";
    private static boolean reachedExit;
    private static final Minecraft mc=Minecraft.getInstance();
    private static final boolean linksOnly=Boolean.getBoolean("ccec.qa.linksOnly");
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void log(String s) throws Exception {
        Files.writeString(folder.resolve("navigation-client.txt"),s+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private static void next(int step){stage=step;ticks=0;}
    private static void stopKeys(){mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(false);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(stage<0 || !Boolean.getBoolean("ccec.qa.interactiveRide"))return;
        try {
            if(folder==null){
                folder=mc.gameDirectory.getCanonicalFile().toPath();
                check(folder.equals(Path.of(System.getProperty("ccec.qa.interactiveRoot"))) && !Files.exists(folder.resolve("saves/RideQA")),"new isolated world");
                deadline=Util.getMillis()+600000;
            }
            ticks++;
            check(Util.getMillis()<deadline,"timeout, stage "+stage+" phase "+lastPhase+" feet "+(mc.player==null?"":mc.player.blockPosition()));
            if(mc.screen instanceof DailyRewardsScreen rewards){rewards.onClose();return;}
            var atlas=mc.screen instanceof PolishedWorldMapScreen a?a:null;
            switch(stage){
                case 0 -> {if(mc.screen instanceof TitleScreen title && mc.getOverlay()==null && ticks>20){
                    mc.createWorldOpenFlows().createFreshLevel("RideQA",MinecraftServer.DEMO_SETTINGS,WorldOptions.DEMO_OPTIONS,WorldPresets::createNormalWorldDimensions,title);
                    next(1);
                }}
                case 1 -> {if(mc.player!=null && mc.level!=null && ticks>20 && mc.getOverlay()==null){
                    if(mc.screen!=null){mc.setScreen(null);return;}
                    var user=mc.player.getUUID();var server=mc.getSingleplayerServer();
                    server.execute(()->{
                        var actor=server.getPlayerList().getPlayer(user);
                        server.getCommands().performPrefixedCommand(actor.createCommandSourceStack().withPermission(4),
                                linksOnly?"test run atlasinfrastructurecards":"test run walkrideandarriveatentrance");
                    });
                    log(linksOnly?"One graphical client connected. Starting the infrastructure cards fixture."
                            :"One graphical client connected. Starting the railway, shipment and joint project fixture.");next(2);
                }}
                case 2 -> {check(JourneyTripGameTest.interactiveFailure().isEmpty(),JourneyTripGameTest.interactiveFailure());
                    scene=JourneyTripGameTest.interactive();if(scene!=null && mc.player.blockPosition().equals(scene.start())){
                    int before=(int)mc.getWindow().getGuiScale();
                    mc.options.guiScale().set(before==1?2:1);mc.resizeDisplay();
                    int after=(int)mc.getWindow().getGuiScale();
                    check(before!=after,"GUI scale changed");
                    log("GUI scale "+before+" -> "+after);
                    if(linksOnly)next(10);
                    else {atlasKey();next(3);}
                }}
                case 3 -> {if(atlas!=null && field(atlas,"map")!=null && ticks>=15){
                    atlas.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);searchFocus(atlas,-1);
                    for(char c:"QA depot".toCharArray())atlas.charTyped(c,0);
                    next(4);
                }}
                case 4 -> {if(atlas!=null){
                    var rows=(List<?>)field(atlas,"searchResults");
                    int target=-1;
                    for(int i=0;i<rows.size();i++)if(scene.goal().toString().equals(get(rows.get(i),"id")))target=i;
                    if(target<0)return;
                    searchFocus(atlas,7);int selected=(int)field(atlas,"searchCursor");
                    for(int i=0;i<Math.abs(target-selected);i++)press(atlas,target<selected?GLFW.GLFW_KEY_UP:GLFW.GLFW_KEY_DOWN);
                    press(atlas,GLFW.GLFW_KEY_ENTER);next(5);
                }}
                case 5 -> {if(atlas!=null && ticks>3){
                    var map=(CityMapPanel)field(atlas,"map");
                    check(map.getSelection()!=null && scene.goal().equals(map.getSelection().id()),"selected the correct duplicate name");
                    var rows=(List<?>)field(atlas,"cardActions");
                    for(int i=0;i<rows.size();i++)if("ROUTE".equals(get(rows.get(i),"action").toString())){
                        activate(atlas,"card."+i);next(6);return;
                    }
                }}
                case 6 -> {if(atlas!=null){
                    var tool=(AtlasTripScreen)field(atlas,"atlasTool");
                    if(tool==null || !((Button)field(tool,"begin")).active)return;
                    var reply=(CompoundTag)field(tool,"response");
                    var plans=reply.getList("Options",Tag.TAG_COMPOUND);int chosen=-1;boolean walk=false;
                    for(int i=0;i<plans.size();i++){
                        if(plans.getCompound(i).getInt("Rides")==0)walk=true;
                        if(plans.getCompound(i).getInt("Rides")==2)chosen=i;
                    }
                    check(walk && chosen>=0,"walking and transfer shown together: "+plans);
                    while((int)field(tool,"index")!=chosen)((Button)field(tool,"nextOption")).onPress();
                    for(var leg:plans.getCompound(chosen).getList("Steps",Tag.TAG_COMPOUND)){
                        var row=(CompoundTag)leg;if(row.contains("Train"))departures.add(row.getString("Line"));
                    }
                    log("Compared walking and transfer, chose "+departures);next(60);
                }}
                case 60 -> {if(atlas!=null && ticks>=4){
                    var tool=(AtlasTripScreen)field(atlas,"atlasTool");
                    if(!"options".equals(field(tool,"wrappedStatus")) || !((Button)field(tool,"begin")).active)return;
                    screenshot(mc,"transfer-options");((Button)field(tool,"begin")).onPress();next(7);
                }}
                case 7 -> {if(atlas!=null && JourneyClient.itinerary()!=null){atlas.onClose();next(8);}}
                case 8 -> travel();
                case 9 -> {if(ticks>=15){check(mc.player.blockPosition().equals(scene.entrance()),"exact final XYZ");
                    log("Arrived at "+scene.entrance()+"; two actually ridden trains "+travelled);
                    JourneyTripGameTest.completed(mc.player.getUUID(),scene.goal());screenshot(mc,"arrival");next(10);
                }}
                case 10 -> {if(ticks>=10 && NavigationRideLinks.tick(mc,scene)){log("PASS");stage=-1;mc.stop();}}
            }
        } catch(Throwable failure){
            try{log("FAIL "+stage+" "+failure);}catch(Exception ignored){}
            failure.printStackTrace();stopKeys();stage=-1;mc.stop();
        }
    }
    private static void travel() throws Exception {
        if(mc.screen!=null || mc.player==null)return;
        var track=JourneyClient.class.getDeclaredField("atlasGoal");track.setAccessible(true);var objective=track.get(null);
        if(objective==null){
            check(reachedExit && boarded.size()==2 && travelled.size()==2,"lost active trip before both physical exits");
            stopKeys();next(9);return;
        }
        var marker=(Marker)field(objective,"marker");
        var instructions=(Component)field(objective,"instruction");
        String key=((TranslatableContents)instructions.getContents()).getKey();
        String phase=key.substring(key.lastIndexOf('.')+1);
        var target=new BlockPos(marker.point.x(),marker.point.y(),marker.point.z());
        var vehicle=mc.player.getRootVehicle() instanceof CarriageContraptionEntity c ? c : null;
        if(!lastPhase.equals(phase)){
            log("HUD "+phase+" feet="+mc.player.blockPosition()+" marker="+target+" vehicle="+(vehicle==null?"none":vehicle.trainId));
            lastPhase=phase;
        }
        switch(phase){
            case "walk", "exit_walk" -> walk(target);
            case "wait_run" -> walk(target);
            case "board" -> {
                stopKeys();
                if(vehicle!=null){boarded.add(vehicle.trainId);return;}
                int index=travelled.size();check(index<departures.size(),"binding to planned service");
                String name=departures.get(index);
                UUID train=scene.rails().stream().filter(r->r.name().equals(name)).findFirst().orElseThrow().train();
                if(!board(train))walk(target);
            }
            case "ride" -> {stopKeys();if(vehicle!=null){boarded.add(vehicle.trainId);travelled.add(vehicle.trainId);}}
            case "exit" -> {stopKeys();if(vehicle!=null){reachedExit=true;mc.options.keyShift.setDown(true);}}
            default -> throw new AssertionError("unexpected guidance "+phase+" at "+mc.player.blockPosition()+" for "+target);
        }
    }
    private static void walk(BlockPos block){
        stopKeys();
        var player=mc.player;var delta=Vec3.atBottomCenterOf(block).subtract(player.position());
        if(delta.horizontalDistance()<.17)return;
        face(delta);mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(delta.y>.6 && player.onGround());
    }
    private static void face(Vec3 delta){
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
    }
    private static boolean board(UUID train) throws Exception {
        var nearby=mc.level.getEntitiesOfClass(CarriageContraptionEntity.class,mc.player.getBoundingBox().inflate(8));
        if(ticks%35==0)log("Looking for "+train+" near "+mc.player.position()+"; carriages "+nearby.stream().map(c->c.trainId).toList());
        for(var carriage:nearby){
            if(!train.equals(carriage.trainId) || !(carriage.getContraption() instanceof CarriageContraption c))continue;
            for(var seat:c.getSeats()){
                if(c.conductorSeats.containsKey(seat))continue;
                var target=carriage.toGlobalVector(Vec3.atLowerCornerOf(seat).add(.5,.25,.5),1);
                var eyes=mc.player.getEyePosition();if(eyes.distanceToSqr(target)>4.5*4.5)continue;
                face(target.subtract(eyes));
                var hit=ContraptionHandlerClient.rayTraceContraption(eyes,eyes.add(mc.player.getLookAngle().scale(4.5)),carriage);
                if(ticks%30==0)log("Passenger seat "+seat+" hit="+(hit==null?"none":hit.getBlockPos())+" distance="+eyes.distanceTo(target));
                if(hit==null || !seat.equals(hit.getBlockPos()))continue;
                if(ticks%3==0)KeyMapping.click(mc.options.keyUse.getKey());
                return true;
            }
        }
        return false;
    }
}
