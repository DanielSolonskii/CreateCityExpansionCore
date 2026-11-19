package com.example.examplemod.client.map;

import com.example.examplemod.client.CommunityFeedbackSettings;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.journey.JourneyClient;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.places.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;

/** One opt-in graphical run. Only the explicitly isolated world may be created or changed. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class NavigationClientProbe {
    private static final String PLACE="Восточный вход QA";
    private static final UUID destination=UUID.randomUUID();
    private static final Set<Object> oldPages=Collections.newSetFromMap(new IdentityHashMap<>());
    private static int stage,ticks;
    private static boolean sampledWarm,overlaySeen;
    private static long deadline,closedAt,initialGuide;
    private static double centerX,centerZ,zoom;
    private static UUID overworldView;
    private static BlockPos door;
    private static Path directory;
    private static NavigationFrameSample sample;
    private static CompletableFuture<?> setup;
    private static CompletableFuture<Boolean> idleRead;
    private static int settled;

    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private static void mark(String line)throws Exception{
        Files.writeString(directory.resolve("navigation-client.txt"),line+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private static void next(int step){stage=step;ticks=0;}
    private static Object field(Object owner,Class<?> type,String name)throws Exception{
        var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    static Object field(Object owner,String name)throws Exception{return field(owner,owner.getClass(),name);}
    static Object get(Object owner,String method)throws Exception{
        var getter=owner.getClass().getDeclaredMethod(method);getter.setAccessible(true);return getter.invoke(owner);
    }
    static void atlasKey(){KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_M));}
    static void press(Screen screen,int key){screen.keyPressed(key,0,0);}
    static void screenshot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->{});}
    static void activate(PolishedWorldMapScreen atlas,String id)throws Exception{
        var keyboard=field(atlas,"keyboard");
        for(int i=0;i<120;i++){
            press(atlas,GLFW.GLFW_KEY_TAB);var current=get(keyboard,"current");
            if(current!=null && id.equals(get(current,"id"))){press(atlas,GLFW.GLFW_KEY_ENTER);return;}
        }
        throw new AssertionError("keyboard control "+id);
    }
    static void searchFocus(PolishedWorldMapScreen atlas,int target)throws Exception{
        for(int i=0;i<24;i++){
            if((int)field(atlas,"searchFocus")==target)return;
            press(atlas,GLFW.GLFW_KEY_TAB);
        }
        throw new AssertionError("search focus "+target);
    }
    private static void changeDimension(Minecraft mc,String dimension){
        var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
        server.execute(()->{
            var p=server.getPlayerList().getPlayer(id);
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(p).withSuppressedOutput(),
                    "execute in "+dimension+" run tp @s 5 85 6");
        });
    }
    private static void smallDisplay(Minecraft mc){
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),660,490);
        mc.options.guiScale().set(2);mc.resizeDisplay();
        MinimapClient.settings().setReducedMotion(true);
        CommunityFx.load();
        if(!CommunityFx.feedback.enabled(CommunityFeedbackSettings.Option.DO_NOT_DISTURB))
            CommunityFx.feedback.toggle(CommunityFeedbackSettings.Option.DO_NOT_DISTURB);
        CommunityFx.save();
    }
    // A fresh Nether can still have generation references while its first HUD directory is ready.
    // Let its normal server ticks release these before the next transfer or integrated-server shutdown.
    private static boolean generationSettled(Minecraft mc)throws Exception {
        if(idleRead!=null && idleRead.isDone()){
            settled=idleRead.get()?settled+1:0;idleRead=null;
            if(settled>=3){settled=0;return true;}
        }
        if(idleRead==null && ticks%10==0){
            var server=mc.getSingleplayerServer();
            var accessor=net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getChunks");
            accessor.setAccessible(true);
            idleRead=server.submit(()->{
                try{
                    for(var level:server.getAllLevels()){
                        var holders=(Iterable<?>)accessor.invoke(level.getChunkSource().chunkMap);
                        for(Object value:holders)if(((net.minecraft.server.level.ChunkHolder)value).getGenerationRefCount()!=0)return false;
                    }
                    return true;
                }catch(IllegalAccessException | java.lang.reflect.InvocationTargetException error){throw new IllegalStateException(error);}
            });
        }
        return false;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(sample!=null && Minecraft.getInstance().screen instanceof PolishedWorldMapScreen)sample.frame();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(stage<0 || Boolean.getBoolean("ccec.qa.interactiveRide"))return;
        var mc=Minecraft.getInstance();
        try{
            if(directory==null){
                directory=mc.gameDirectory.getCanonicalFile().toPath();
                check(directory.equals(Path.of(System.getProperty("ccec.qa.dir"))) && !Files.exists(directory.resolve("saves/NavQA")),"fresh isolated world");
                deadline=Util.getMillis()+420000;mark("OpenGL "+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            }
            ticks++;check(Util.getMillis()<deadline && ticks<1400,"timeout phase "+stage);
            if(mc.screen instanceof DailyRewardsScreen rewards){rewards.onClose();return;}
            var atlas=mc.screen instanceof PolishedWorldMapScreen a?a:null;
            var map=atlas==null?null:(CityMapPanel)field(atlas,"map");
            switch(stage){
            case 0 -> {
                if(mc.screen instanceof TitleScreen t && mc.getOverlay()==null && ticks>25){
                    mc.createWorldOpenFlows().createFreshLevel("NavQA",MinecraftServer.DEMO_SETTINGS,WorldOptions.DEMO_OPTIONS,WorldPresets::createNormalWorldDimensions,t);
                    next(1);
                }
            }
            case 1 -> {
                if(mc.player==null || mc.level==null || mc.getOverlay()!=null || ticks<25)return;
                if(mc.screen!=null){mc.setScreen(null);return;}
                if(Boolean.getBoolean("ccec.qa.markersOnly")){
                    if(!mc.player.onGround())return;
                    NavigationMarkerProbe.prepare(mc);next(23);return;
                }
                if(Boolean.getBoolean("ccec.qa.engineeringOnly")){
                    smallDisplay(mc);atlasKey();next(20);return;
                }
                if(Boolean.getBoolean("ccec.qa.dimensionOnly")){
                    MinimapClient.settings().enabled=true;
                    var binding=(MapViewBindings.Binding)field(null,MapViewBindings.class,"minimap");
                    if(binding==null || !generationSettled(mc))return;
                    overworldView=binding.viewId();changeDimension(mc,"minecraft:the_nether");next(15);return;
                }
                NavigationFrameSample.installTrafficMeter(mc);
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                setup=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(id);var level=p.serverLevel();door=p.blockPosition().above(4);
                    for(int x=-3;x<42;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=2;y++)
                        level.setBlock(door.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                    p.connection.teleport(door.getX()+.5,door.getY(),door.getZ()+.5,-90,0);
                });next(2);
            }
            case 2 -> {
                if(!setup.isDone() || ticks<12 || !mc.player.onGround())return;
                setup.join();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                setup=server.submit(()->{
                    var p=server.getPlayerList().getPlayer(id);
                    var place=new AtlasPlace(destination,id,0,PLACE,AtlasPlace.Category.PUBLIC,"",p.level().dimension().location(),door,List.of(door),false,null);
                    check(PlaceService.save(p,place).equals("saved"),"server surveyed standing entrance");
                    p.connection.teleport(door.getX()+33.5,door.getY(),door.getZ()+.5,90,0);
                });next(3);
            }
            case 3 -> {
                if(!setup.isDone() || ticks<20 || mc.screen!=null)return;
                setup.join();check(TerrainCache.cachedTiles()==0 && TerrainAtlas.occupied()==0,"cold start");
                if(Boolean.getBoolean("ccec.qa.formsOnly")){
                    mc.options.guiScale().set(1);mc.resizeDisplay();
                    mark("Explicit initial GUI scale "+mc.getWindow().getGuiScale());
                    smallDisplay(mc);atlasKey();next(17);return;
                }
                sample=new NavigationFrameSample("cold");atlasKey();next(4);
            }
            case 4 -> {
                if(map==null)return;
                sample.sampleQueues();if(!sample.ready())return;
                mark(sample.finish(directory));sample=null;
                check(TerrainAtlas.occupied()>0,"sample showed terrain");
                if(!sampledWarm){
                    centerX=map.centerX();centerZ=map.centerZ();zoom=map.zoom();
                    mark("scene "+map.dimensionId()+" center="+centerX+","+centerZ+" zoom="+zoom+" GUI="+atlas.width+"x"+atlas.height);
                    atlas.onClose();next(5);
                }else{
                    check(Math.abs(map.centerX()-centerX)<.01 && Math.abs(map.centerZ()-centerZ)<.01 && map.zoom()==zoom,"same camera");
                    if(Boolean.getBoolean("ccec.qa.metricsOnly")){
                        mark("Warm sample used the same camera and zoom");mark("PASS");stage=-1;mc.stop();return;
                    }
                    oldPages.addAll((List<?>)field(null,TerrainAtlas.class,"PAGES"));
                    var debug=KeyboardHandler.class.getDeclaredMethod("handleDebugKeys",int.class);debug.setAccessible(true);
                    check(Boolean.TRUE.equals(debug.invoke(mc.keyboardHandler,GLFW.GLFW_KEY_T)),"F3+T debug handler");
                    next(6);
                }
            }
            case 5 -> {
                if(mc.screen!=null || ticks<5)return;
                check(TerrainCache.cachedTiles()>0,"warm raw cache");
                sample=new NavigationFrameSample("warm");sampledWarm=true;atlasKey();next(4);
            }
            case 6 -> {
                overlaySeen|=mc.getOverlay()!=null;
                if(!overlaySeen || mc.getOverlay()!=null || map==null || TerrainAtlas.occupied()==0 || ticks<18)return;
                var pages=(List<?>)field(null,TerrainAtlas.class,"PAGES");
                if(pages.stream().anyMatch(oldPages::contains))return;
                mark("F3+T debug handler ran, reload overlay completed, old GPU pages replaced, "+TerrainAtlas.occupied()+" drawable slots");
                smallDisplay(mc);next(7);
            }
            case 7 -> {
                if(map==null || ticks<12 || atlas.width>370)return;
                check(map.reducedMotion(),"map reduced motion");
                check(JourneyClient.reduced(),"Journey reduced motion follows the map");
                check(!CommunityFx.feedback.sound("map_navigation",Util.getMillis(),System.currentTimeMillis()),"DND suppresses navigation audio");
                atlas.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);check((boolean)field(atlas,"searchOpen"),"Ctrl+F");
                press(atlas,GLFW.GLFW_KEY_ESCAPE);check(mc.screen==atlas && !(boolean)field(atlas,"searchOpen"),"Escape closes only search");
                atlas.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);searchFocus(atlas,-1);
                for(char c:PLACE.toCharArray())atlas.charTyped(c,0);
                next(8);
            }
            case 8 -> {
                if(map==null || ticks<10)return;
                List<?> results=(List<?>)field(atlas,"searchResults");int idx=-1;
                for(int i=0;i<results.size();i++)if(destination.toString().equals(get(results.get(i),"id")))idx=i;
                if(idx<0)return;screenshot(mc,"small-search");
                searchFocus(atlas,7);int cursor=(int)field(atlas,"searchCursor");
                for(int j=0;j<Math.abs(cursor-idx);j++)press(atlas,idx<cursor?GLFW.GLFW_KEY_UP:GLFW.GLFW_KEY_DOWN);
                press(atlas,GLFW.GLFW_KEY_ENTER);next(9);
            }
            case 9 -> {
                if(map==null || ticks<9)return;
                check(map.getSelection()!=null && destination.equals(map.getSelection().id()),"search resolved target ID");
                screenshot(mc,"small-card");var rows=(List<?>)field(atlas,"cardActions");
                int idx=-1;for(int i=0;i<rows.size();i++)if("ROUTE".equals(get(rows.get(i),"action").toString()))idx=i;
                check(idx>=0,"route card action");activate(atlas,"card."+idx);next(10);
            }
            case 10 -> {
                if(atlas==null || ticks<8)return;
                var tool=(AtlasTripScreen)field(atlas,"atlasTool");var begin=(Button)field(tool,"begin");
                if(!begin.active || !"options".equals(field(tool,"wrappedStatus")))return;
                screenshot(mc,"small-itinerary");
                for(var child:tool.children())if(child instanceof Button b){
                    check(b.getClass()!=Button.class,"atlas button skin");
                    check(b.getX()>=0 && b.getY()>=0 && b.getRight()<=atlas.width && b.getBottom()<=atlas.height,"visible button bounds");
                    for(var other:tool.children())if(other!=child && other instanceof Button n)
                        check(b.getX()>=n.getRight() || b.getRight()<=n.getX() || b.getY()>=n.getBottom() || b.getBottom()<=n.getY(),"distinct click regions");
                }
                if(Boolean.getBoolean("ccec.qa.formsOnly")){
                    mark("Styled planner and editor at effective scale "+mc.getWindow().getGuiScale()+", GUI "+atlas.width+"x"+atlas.height);
                    press(atlas,GLFW.GLFW_KEY_ESCAPE);check(mc.screen==atlas && field(atlas,"atlasTool")==null,"Escape closes planner");
                    next(20);return;
                }
                int x=begin.getX()+begin.getWidth()/2,y=begin.getY()+begin.getHeight()/2;
                atlas.mouseClicked(x,y,0);atlas.mouseReleased(x,y,0);next(11);
            }
            case 11 -> {
                if(atlas==null || JourneyClient.itinerary()==null || ticks<8)return;
                var goal=field(null,JourneyClient.class,"atlasGoal");initialGuide=(long)field(goal,"observed");
                closedAt=Util.getMillis();atlas.onClose();next(12);
            }
            case 12 -> {
                if(mc.screen!=null || Util.getMillis()-closedAt<6200)return;
                var goal=field(null,JourneyClient.class,"atlasGoal");
                check(goal!=null && (long)field(goal,"observed")>initialGuide,"fresh guide update with both map views closed");
                var prefs=MinimapClient.settings();prefs.enabled=true;prefs.corner=1;prefs.save();
                mark("Both maps closed >6s; server guide timestamp advanced; starting walk at "+mc.player.blockPosition());
                next(13);
            }
            case 13 -> {
                if(mc.screen!=null)return;
                mc.player.setYRot(90);mc.options.keyUp.setDown(true);
                if(ticks==20)screenshot(mc,"small-navigation-hud");
                if(JourneyClient.itinerary()==null || mc.player.getX()<door.getX()+.8){
                    mc.options.keyUp.setDown(false);next(14);
                }
            }
            case 14 -> {
                if(mc.screen!=null || JourneyClient.itinerary()!=null || ticks<18)return;
                check(mc.player.blockPosition().distManhattan(door)<=3,"actual arrival near entrance");
                var binding=(MapViewBindings.Binding)field(null,MapViewBindings.class,"minimap");if(binding==null)return;
                overworldView=binding.viewId();
                mark("Target reached on foot; reduced motion, DND, keyboard search, Escape, route button and Journey/left minimap at "+mc.getWindow().getGuiScaledWidth()+"x"+mc.getWindow().getGuiScaledHeight());
                changeDimension(mc,"minecraft:the_nether");next(15);
            }
            case 15 -> {
                var binding=(MapViewBindings.Binding)field(null,MapViewBindings.class,"minimap");
                if(binding==null || !binding.dimension().toString().equals("minecraft:the_nether") || ticks<20)return;
                check(!binding.viewId().equals(overworldView),"dimension uses fresh view");
                if(!generationSettled(mc))return;
                changeDimension(mc,"minecraft:overworld");next(16);
            }
            case 16 -> {
                var binding=(MapViewBindings.Binding)field(null,MapViewBindings.class,"minimap");
                if(binding==null || !binding.dimension().toString().equals("minecraft:overworld") || ticks<20)return;
                check(!binding.viewId().equals(overworldView),"return uses fresh view");
                if(!generationSettled(mc))return;
                mark("Overworld -> Nether -> Overworld: distinct validated HUD view IDs; resources raw="+TerrainCache.cachedTiles()
                        +" slots="+TerrainAtlas.occupied()+" pages="+TerrainAtlas.pages()+" pooled="+TerrainCache.pooledTextures()
                        +" queues[inflight="+TerrainCache.inflight()+",raster="+TerrainTileTexture.queueSize()+",io="+TerrainTileStore.ioQueueSize()+"]");
                mark("PASS");stage=-1;mc.stop();
            }
            case 17 -> {
                if(map==null || !AtlasPlaces.ready(map.dimensionId()) || ticks<15)return;
                activate(atlas,"view.14");next(18);
            }
            case 18 -> {
                if(atlas==null || ticks<9)return;
                var editor=(Screen)field(atlas,"atlasTool");
                var box=(net.minecraft.client.gui.components.EditBox)field(editor,"nameBox");
                check(!box.isBordered(),"custom atlas field");atlas.mouseClicked(box.getX()+5,box.getY()+5,0);
                atlas.mouseReleased(box.getX()+5,box.getY()+5,0);
                if(box.getValue().isEmpty()){
                    for(char c:"Депо у реки".toCharArray())atlas.charTyped(c,0);
                    check(box.getValue().equals("Депо у реки"),"focused map field preserves typing");return;
                }
                screenshot(mc,"small-place-editor");
                press(atlas,GLFW.GLFW_KEY_ESCAPE);check(mc.screen==atlas && field(atlas,"atlasTool")==null,"Escape closes editor");next(7);
            }
            case 19 -> {
                if(ticks>20 && generationSettled(mc)){mark("PASS");stage=-1;mc.stop();}
            }
            case 20 -> {
                if(map!=null && AtlasPlaces.ready(map.dimensionId()) && ticks>8){activate(atlas,"view.13");next(21);}
            }
            case 21 -> {
                if(atlas==null || ticks<5)return;
                var editor=(Screen)field(atlas,"planEditor");check(editor!=null,"engineering editor opened");
                for(var child:editor.children())if(child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().endsWith(".page.details")){
                    check(button.getClass()!=Button.class,"engineering tab style");
                    check(mc.font.width(button.getMessage())<=button.getWidth()-12,"engineering tab label fits");
                    int x=button.getX()+button.getWidth()/2,y=button.getY()+button.getHeight()/2;
                    atlas.mouseClicked(x,y,0);atlas.mouseReleased(x,y,0);next(22);return;
                }
                throw new AssertionError("engineering properties tab");
            }
            case 22 -> {
                if(atlas==null || ticks<7)return;
                var editor=(Screen)field(atlas,"planEditor");
                var box=(net.minecraft.client.gui.components.EditBox)field(editor,"nameBox");
                check(box!=null && !box.isBordered(),"engineering field and tabs from the live form");
                screenshot(mc,"small-engineering-editor");editor.onClose();atlas.onClose();next(19);
            }
            case 23 -> {
                if(NavigationMarkerProbe.tick(mc,atlas,map)){
                    mark("Public shop published by right click with zero entrances. Seven categories rendered. Second profile can read the shop; its owner then saved a surveyed entrance with the same marker ID and icon.");
                    next(19);
                }
            }
            }
        }catch(Throwable error){
            try{mark("FAIL phase "+stage+" "+error);}catch(Exception ignored){}
            error.printStackTrace();stage=-1;mc.options.keyUp.setDown(false);mc.stop();
        }
    }
}
