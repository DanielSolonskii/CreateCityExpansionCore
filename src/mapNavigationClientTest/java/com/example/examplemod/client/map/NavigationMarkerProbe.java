package com.example.examplemod.client.map;

import com.example.examplemod.client.screen.AtlasPlaces;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.places.AtlasPlace;
import com.example.examplemod.places.PlaceService;
import com.mojang.authlib.GameProfile;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** A player publishes the seventh point using the actual atlas, atop six illustrative QA places. */
final class NavigationMarkerProbe {
    private enum Step { OPEN, CENTER, RIGHT_CLICK, EDIT, PUBLISH, CONFIRM, SHOW, FINISH }
    private record Example(AtlasPlace.Category category,String name,int x,int z) {}
    private static final List<Example> EXAMPLES=List.of(
            new Example(AtlasPlace.Category.HOME,"Дом у реки",-30,-17),
            new Example(AtlasPlace.Category.FACTORY,"Литейный цех",0,-22),
            new Example(AtlasPlace.Category.WAREHOUSE,"Пакгауз",30,-13),
            new Example(AtlasPlace.Category.MINE,"Рудник",-28,7),
            new Example(AtlasPlace.Category.PUBLIC,"Площадь",-17,25),
            new Example(AtlasPlace.Category.OTHER,"Причал",25,22));
    private static final String SHOP="Лавка часовщика";
    private static Step step=Step.OPEN;
    private static int ticks;
    private static CompletableFuture<?> job;
    private static CompletableFuture<Void> photoWritten;
    private static BlockPos entrance,marker;
    private static UUID published;

    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void next(Step value){step=value;ticks=0;}

    static void prepare(Minecraft mc) {
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,768);
        mc.options.guiScale().set(2);mc.resizeDisplay();
        var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
        job=server.submit(()->{
            var player=server.getPlayerList().getPlayer(owner);var level=player.serverLevel();
            entrance=player.blockPosition();
            level.setBlockAndUpdate(entrance.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(entrance,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(entrance.above(),Blocks.AIR.defaultBlockState());
            for(var e:EXAMPLES){
                var place=new AtlasPlace(UUID.randomUUID(),owner,0,e.name(),e.category(),"",level.dimension().location(),
                        entrance.offset(e.x(),0,e.z()),List.of(entrance),true,null);
                require("saved".equals(PlaceService.save(player,place)),"server fixture "+e.name());
            }
        });
    }

    static boolean tick(Minecraft mc,PolishedWorldMapScreen atlas,CityMapPanel map)throws Exception {
        if(++ticks>350)throw new AssertionError("Marker stage timed out: "+step);
        switch(step){
            case OPEN -> {if(job.isDone()){job.join();NavigationClientProbe.atlasKey();next(Step.CENTER);}}
            case CENTER -> {
                if(map==null || AtlasPlaces.selections(map.dimensionId()).size()!=EXAMPLES.size())return false;
                map.restoreView(entrance.getX()+.5,entrance.getZ()+.5,3.5);next(Step.RIGHT_CLICK);
            }
            case RIGHT_CLICK -> {
                if(ticks<8 || map==null)return false;
                marker=entrance.offset(10,0,5);
                int x=map.worldToScreenX(marker.getX()+.5),y=map.worldToScreenY(marker.getZ()+.5);
                require(atlas.mouseClicked(x,y,1),"right click on free terrain opens a public place");
                atlas.mouseReleased(x,y,1);next(Step.EDIT);
            }
            case EDIT -> {
                var editor=(Screen)NavigationClientProbe.field(atlas,"atlasTool");
                require(editor!=null && (boolean)NavigationClientProbe.field(editor,"published"),"public draft on right click");
                var box=(EditBox)NavigationClientProbe.field(editor,"nameBox");
                atlas.mouseClicked(box.getX()+6,box.getY()+6,0);atlas.mouseReleased(box.getX()+6,box.getY()+6,0);
                for(char c:SHOP.toCharArray())atlas.charTyped(c,0);
                require(box.getValue().equals(SHOP),"place name typed into live editor");
                for(int i=0;i<AtlasPlace.Category.values().length;i++){
                    var category=(AtlasPlace.Category)NavigationClientProbe.field(editor,"category");
                    if(category==AtlasPlace.Category.SHOP)break;
                    click(atlas,editor,"category."+category.name().toLowerCase(java.util.Locale.ROOT));
                }
                require(NavigationClientProbe.field(editor,"category")==AtlasPlace.Category.SHOP,"shop category chosen");
                published=(UUID)NavigationClientProbe.field(editor,"id");next(Step.PUBLISH);
            }
            case PUBLISH -> {
                if(ticks<4)return false;
                NavigationClientProbe.screenshot(mc,"public-marker-editor");
                var editor=(Screen)NavigationClientProbe.field(atlas,"atlasTool");
                require(((List<?>)NavigationClientProbe.field(editor,"entrances")).isEmpty(),"no entrance on a new marker");
                click(atlas,editor,"publish");next(Step.CONFIRM);
            }
            case CONFIRM -> {
                if(map==null || NavigationClientProbe.field(atlas,"atlasTool")!=null)return false;
                var row=AtlasPlaces.get(published,map.dimensionId());
                require(row!=null && row.published() && row.category()==AtlasPlace.Category.SHOP && SHOP.equals(row.name()),"server acknowledged public shop");
                require(row.icon().getX()==marker.getX() && row.icon().getZ()==marker.getZ() && row.entrances().isEmpty(),"published marker keeps clicked X/Z with zero entrances");
                var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
                job=server.submit(()->{
                    var actor=server.getPlayerList().getPlayer(owner);
                    var other=new ServerPlayer(server,actor.serverLevel(),new GameProfile(UUID.randomUUID(),"MarkerGuest"),ClientInformation.createDefault());
                    require(row.equals(PlaceService.resolve(other,row.id())),"different profile can read the published place");
                });next(Step.SHOW);
            }
            case SHOW -> {
                if(!job.isDone() || ticks<25 || TerrainAtlas.occupied()==0)return false;
                job.join();
                require(AtlasPlaces.selections(map.dimensionId()).size()==EXAMPLES.size()+1,"all categories received");
                var actions=(List<?>)NavigationClientProbe.field(atlas,"cardActions");
                require(!actions.isEmpty() && map.getSelection()!=null && published.equals(map.getSelection().id()),"published marker card is open");
                for(Object action:actions){
                    String name=NavigationClientProbe.get(action,"action").toString();
                    require(!name.equals("ROUTE") && !name.equals("FROM"),"no journey action without a surveyed entrance");
                }
                photoWritten=new CompletableFuture<>();
                Screenshot.grab(mc.gameDirectory,"public-markers-map.png",mc.getMainRenderTarget(),message->photoWritten.complete(null));
                var row=AtlasPlaces.get(published,map.dimensionId());var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
                job=server.submit(()->{
                    var actor=server.getPlayerList().getPlayer(owner);
                    var edit=new AtlasPlace(row.id(),owner,row.version(),row.name(),row.category(),row.description(),row.dimension(),row.icon(),List.of(entrance),true,null);
                    require("saved".equals(PlaceService.save(actor,edit)),"add surveyed entrance to published marker");
                    var updated=PlaceService.resolve(actor,row.id());
                    require(updated!=null && updated.icon().equals(row.icon()) && updated.entrances().equals(List.of(entrance)),"same marker gained a real entrance");
                });next(Step.FINISH);
            }
            case FINISH -> {
                if(!job.isDone() || !photoWritten.isDone())return false;
                job.join();
                var path=mc.gameDirectory.toPath().resolve(Path.of("screenshots","public-markers-map.png"));
                var image=ImageIO.read(path.toFile());require(image!=null,"read captured atlas PNG");
                var texture=ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","textures/gui/map/place_markers.png");
                try(var stream=mc.getResourceManager().getResourceOrThrow(texture).open()){
                    var sheet=ImageIO.read(stream);
                    var shop=badgePixels(image,atlas,map,marker);
                    var factory=badgePixels(image,atlas,map,entrance.offset(0,0,-22));
                    double brightness=frameBrightness(shop,sheet);
                    checkIconPixels(shop,sheet,1,"shop",brightness);
                    checkIconPixels(factory,sheet,2,"factory",brightness);
                }
                atlas.onClose();return true;
            }
        }
        return false;
    }

    private record BadgePixels(BufferedImage frame,int cx,int cy,int radius,double scaleX,double scaleY){
        int sample(int x,int y){
            int px=(int)Math.floor((cx-radius+(x+.5)*radius/10)*scaleX);
            int py=(int)Math.floor((cy-radius+(y+.5)*radius/10)*scaleY);
            return frame.getRGB(px,py);
        }
    }
    private static BadgePixels badgePixels(BufferedImage frame,Screen screen,CityMapPanel map,BlockPos point){
        int cx=map.worldToScreenX(point.getX()+.5),cy=map.worldToScreenY(point.getZ()+.5),radius=AtlasPlaceIcon.radius(map.zoom());
        require(cx-radius>=map.viewportLeft() && cx+radius<map.viewportRight() && cy-radius>=map.viewportTop() && cy+radius<map.viewportBottom(),"marker on canvas");
        return new BadgePixels(frame,cx,cy,radius,(double)frame.getWidth()/screen.width,(double)frame.getHeight()/screen.height);
    }
    /** The selected border provides a single neutral calibration for the complete captured frame. */
    private static double frameBrightness(BadgePixels shop,BufferedImage sheet){
        int reference=sheet.getRGB(30,60); // middle of top edge, selected shop
        int sum=0;
        for(int x=4;x<16;x++)sum+=(shop.sample(x,0)>>8)&255;
        double brightness=sum/(12.0*((reference>>8)&255));
        require(brightness>=.65 && brightness<=1.05,"selected border brightness "+brightness);
        int matches=0;
        for(int x=4;x<16;x++)if(matchesColor(shop.sample(x,0),reference,brightness))matches++;
        require(matches>=10,"selected border at the projected map location");
        System.out.println("QA selected map border brightness: "+brightness);
        return brightness;
    }
    private static boolean matchesColor(int pixel,int reference,double brightness){
        for(int shift=0;shift<=16;shift+=8){
            int expected=(int)Math.round(((reference>>shift)&255)*brightness);
            if(Math.abs(((pixel>>shift)&255)-expected)>6)return false;
        }
        return true;
    }
    /** Match the opaque glyph interior on terrain. Frame, tooltip, label and sidebar are excluded. */
    private static void checkIconPixels(BadgePixels badge,BufferedImage sheet,int tile,String name,double brightness){
        int expected=0,matched=0;
        for(int y=2;y<18;y++)for(int x=2;x<18;x++){
            int ink=sheet.getRGB(tile*20+x,y),red=(ink>>16)&255,green=(ink>>8)&255,blue=ink&255;
            int light=Math.max(red,Math.max(green,blue));
            if((ink>>>24)!=255 || light<150 || light-Math.min(red,Math.min(green,blue))<24)continue;
            expected++;
            if(matchesColor(badge.sample(x,y),ink,brightness))matched++;
        }
        require(expected>=12 && matched*4>=expected*3,name+" glyph in captured terrain at "+badge.cx()+","+badge.cy()+": "+matched+"/"+expected+" matching samples");
        System.out.println("QA visible "+name+" terrain glyph: "+matched+"/"+expected+" matching colour samples");
    }

    private static void click(PolishedWorldMapScreen atlas,Screen editor,String key){
        for(var child:editor.children())if(child instanceof Button b && b.visible && b.active &&
                b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("gui.createcityexpansioncore.places."+key)){
            int x=b.getX()+b.getWidth()/2,y=b.getY()+b.getHeight()/2;
            require(atlas.mouseClicked(x,y,0),"click "+key);atlas.mouseReleased(x,y,0);return;
        }
        throw new AssertionError("Missing place action "+key);
    }
}
