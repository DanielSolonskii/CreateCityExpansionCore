package com.example.examplemod.client.map;

import com.example.examplemod.client.screen.AtlasPlaces;
import com.example.examplemod.client.screen.PassengerLinesScreen;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.example.examplemod.navigation.JourneyTripGameTest;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import static com.example.examplemod.client.map.NavigationClientProbe.*;

/** Drives the real editor and network channel in the isolated integrated world. */
final class NavigationRideEditorProbe {
    private static final String LINE="Речной экспресс";
    private static int stage, ticks;
    private static UUID selected;
    private static java.util.concurrent.CompletableFuture<Boolean> coloredRails;
    private static void next(int step) throws Exception {
        NavigationRideProbe.log("Passenger editor step "+step);
        stage=step;ticks=0;
    }
    static boolean tick(Minecraft mc) throws Exception {
        var scene=JourneyTripGameTest.interactive();
        if(!JourneyTripGameTest.interactiveFailure().isEmpty())throw new AssertionError(JourneyTripGameTest.interactiveFailure());
        if(scene==null || mc.player==null)return false;
        if(++ticks>700)throw new AssertionError("Passenger editor stalled at "+stage+" screen="+mc.screen);
        if(ticks<9)return false;
        var rail=scene.rails().getFirst();
        var atlas=mc.screen instanceof PolishedWorldMapScreen a?a:null;
        var editor=atlas!=null && field(atlas,"atlasTool") instanceof PassengerLinesScreen e?e:null;
        if(editor!=null && field(editor,"pending")!=null)return false;
        switch(stage){
            case 0 -> {if(mc.player.blockPosition().equals(scene.start())){
                int scale=mc.getWindow().getScreenWidth()>=800?2:1;
                mc.options.guiScale().set(scale);mc.resizeDisplay();atlasKey();next(1);
            }}
            case 1,11 -> {
                if(atlas==null || !(field(atlas,"map") instanceof CityMapPanel map) || !AtlasPlaces.ready(map.dimensionId()))return false;
                activate(atlas,"view.16");next(stage==1?2:12);
            }
            case 2 -> {if(editor!=null){
                pressButton(atlas,editor,"create");
                name(atlas,editor,"Резерв");pressButton(atlas,editor,"create");next(3);
            }}
            case 3 -> {if(editor!=null){
                if(!row(editor).getString("Name").equals("Резерв"))throw new AssertionError("First draft: "+row(editor));
                pressButton(atlas,editor,"all_lines");pressButton(atlas,editor,"create");
                name(atlas,editor,LINE);pressButton(atlas,editor,"create");next(4);
            }}
            case 4 -> {
                var line=row(editor);if(!LINE.equals(line.getString("Name")))throw new AssertionError("Saved Cyrillic name: "+line);
                selected=line.getUUID("Id");pressButton(atlas,editor,"choose_station");next(5);
            }
            case 5,7 -> {
                var filter=(EditBox)field(editor,"stationFilter");type(atlas,filter,stage==5?"West":"East");
                next(stage==5?6:8);
            }
            case 6,8 -> {
                var station=rail.name()+(stage==6?" West":" East");
                var button=editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(b->b.getMessage().getString().startsWith("✓ "+station+"  ·")).findFirst().orElseThrow();
                if(stage==6)screenshot(mc,"passenger-station-search");
                click(atlas,button);next(stage==6?9:10);
            }
            case 9 -> next(7);
            case 10 -> {
                var line=row(editor);var stops=line.getList("Stops",8);
                if(!selected.equals(line.getUUID("Id")) || stops.size()!=2 || !stops.getString(0).equals(rail.name()+" West")
                        || !stops.getString(1).equals(rail.name()+" East"))throw new AssertionError("Ordered station names: "+line);
                pressButton(atlas,editor,"done_stops");pressButton(atlas,editor,"all_lines");pressButton(atlas,editor,"back");next(11);
            }
            case 12 -> {
                if(editor==null)return false;
                var line=row(editor);
                if(!selected.equals(line.getUUID("Id"))||!line.hasUUID("Train")||!rail.train().equals(line.getUUID("Train")))
                    throw new AssertionError("Retained selection and docked native train: "+line);
                pressButton(atlas,editor,"service");pressButton(atlas,editor,"choose_train");next(21);
            }
            case 21 -> {
                var button=editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(b->b.getMessage().getString().endsWith(rail.train().toString().substring(0,8))).findFirst().orElseThrow();
                click(atlas,button);next(22);
            }
            case 22 -> {
                if(!row(editor).hasUUID("ChosenTrain") || !row(editor).getUUID("ChosenTrain").equals(rail.train()))
                    throw new AssertionError("Train UUID selected through tablet and editor");
                pressButton(atlas,editor,"back");pressButton(atlas,editor,"settings");
                pressButton(atlas,editor,"color");next(23);
            }
            case 23 -> {
                var label=Component.translatable("color.minecraft.blue").getString();
                var button=editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();
                verifySwatch(mc,atlas,button);
                screenshot(mc,"passenger-line-palette");
                click(atlas,button);next(24);
            }
            case 24 -> {
                if(!"blue".equals(row(editor).getString("Color")))throw new AssertionError("Blue line color saved");
                var control=editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(b->b.getMessage().equals(Component.translatable("gui.createcityexpansioncore.passenger_editor.color"))).findFirst().orElseThrow();
                verifySwatch(mc,atlas,control);
                pressButton(atlas,editor,"back");pressButton(atlas,editor,"service");
                screenshot(mc,"passenger-line-ready");pressButton(atlas,editor,"assign");next(13);
            }
            case 13 -> {
                if(!row(editor).getBoolean("Assigned"))throw new AssertionError("Train assignment: "+field(editor,"status"));
                pressButton(atlas,editor,"refresh");next(14);
            }
            case 14 -> {
                var line=row(editor);
                if(!line.getBoolean("Assigned")||!rail.train().equals(line.getUUID("Train"))||!selected.equals(line.getUUID("Id")))
                    throw new AssertionError("Fresh server read: "+line);
                NavigationRideProbe.log("Created "+LINE+"; searched both platforms; closed and reopened; assigned native train "
                        +line.getString("TrainName")+" "+rail.train()+"; refreshed line "+selected+" status="+line.getString("Status"));
                screenshot(mc,"passenger-line-dispatched");next(25);
            }
            case 25 -> {
                if (coloredRails==null) {
                    var server=mc.getSingleplayerServer();
                    coloredRails=server.submit(()->{
                        var actor=server.getPlayerList().getPlayer(scene.player());
                        var map=com.example.examplemod.passenger.PassengerRailMap.forAtlas(actor,actor.level().dimension().location());
                        return map.segments().stream().anyMatch(s->selected.equals(s.line()) &&
                                (s.color()&0xffffff)==(net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor()&0xffffff)
                                && Math.hypot(s.x()-s.toX(),s.z()-s.toZ())>0);
                    });
                } else if(coloredRails.isDone()) {
                    boolean published=coloredRails.join();coloredRails=null;
                    if(published)next(26);
                }
            }
            case 26 -> {
                if (coloredRails==null) {
                    var server=mc.getSingleplayerServer();
                    coloredRails=server.submit(()->rail.east().equals(
                            com.example.examplemod.passenger.PassengerService.stoppedAt(server,rail.train())));
                } else if(coloredRails.isDone()) {
                    boolean arrived=coloredRails.join();coloredRails=null;
                    if(arrived){
                        NavigationRideProbe.log("Native train reached the second selected platform");
                        pressButton(atlas,editor,"back");
                        pressButton(atlas,editor,"all_lines");pressButton(atlas,editor,"back");
                        next(27);
                    }
                }
            }
            case 27 -> {
                var map=(CityMapPanel)field(atlas,"map");
                var segments=blueSegments(map);
                if(segments.isEmpty())return false;
                var first=segments.getFirst();
                var last=segments.getLast();
                map.restoreView((first.x()+last.toX())/2,(first.z()+last.toZ())/2,4);
                map.setOverlay(CityMapPanel.Overlay.TRACKS,true);
                next(28);
            }
            case 28 -> {
                if(ticks<24)return false;
                screenshot(mc,"passenger-blue-rail-map");next(29);
            }
            case 29 -> {
                var file=new java.io.File(mc.gameDirectory,"screenshots/passenger-blue-rail-map.png");
                if(!file.isFile())return false;
                var frame=javax.imageio.ImageIO.read(file);
                var map=(CityMapPanel)field(atlas,"map");
                int count=0, rgb=net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor()&0xffffff;
                for(var segment:blueSegments(map)){
                    double wx=(segment.x()+segment.toX())/2, wz=(segment.z()+segment.toZ())/2;
                    int cx=map.worldToScreenX(wx), cy=map.worldToScreenY(wz);
                    if(cx<map.viewportLeft()+2 || cx>=map.viewportRight()-2 || cy<map.viewportTop()+2 || cy>=map.viewportBottom()-2) continue;
                    for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++) {
                        int px=(int)((cx+dx+.5)*frame.getWidth()/atlas.width);
                        int py=(int)((cy+dy+.5)*frame.getHeight()/atlas.height);
                        int pixel=frame.getRGB(px,py);
                        double brightness=(pixel&255)/(double)(rgb&255);
                        if(brightness>.65 && brightness<=1.05 &&
                                Math.abs(((pixel>>16)&255)-((rgb>>16)&255)*brightness)<5 &&
                                Math.abs(((pixel>>8)&255)-((rgb>>8)&255)*brightness)<5) count++;
                    }
                }
                if(count<3)throw new AssertionError("Expected blue line pixels on the selected native track, found "+count);
                NavigationRideProbe.log("Public atlas packet and projected blue rail pixels verified: "+count);
                activate(atlas,"view.16");next(15);
            }
            case 15 -> {
                if(editor==null || field(editor,"pending")!=null)return false;
                pressButton(atlas,editor,"settings");
                pressButton(atlas,editor,"clear");
                pressButton(atlas,editor,"clear");
                next(16);
            }
            case 16 -> {
                var line=row(editor);
                if(line.getBoolean("Assigned")||!line.getList("Stops",8).isEmpty())
                    throw new AssertionError("Clearing a running line must release the train and remove its stops");
                if(((ListTag)field(editor,"stations")).size()<2)throw new AssertionError("Shared platforms must survive");
                pressButton(atlas,editor,"settings");
                pressButton(atlas,editor,"delete");pressButton(atlas,editor,"delete");
                next(17);
            }
            case 17 -> {
                var remaining=(ListTag)field(editor,"lines");
                if(remaining.size()!=1 || !remaining.getCompound(0).getString("Name").equals("Резерв"))
                    throw new AssertionError("Deleted line remained visible or removed its neighbour");
                editor.onClose();next(18);
            }
            case 18 -> {
                if(NavigationRideStationCardProbe.tick(mc,scene)){
                    JourneyTripGameTest.completed(scene.player(),scene.goal());return true;
                }
            }
        }
        return false;
    }
    private static java.util.List<com.example.examplemod.passenger.PassengerRailMap.Segment> blueSegments(CityMapPanel map){
        int blue=net.minecraft.world.item.DyeColor.BLUE.getTextureDiffuseColor() & 0xffffff;
        return CityMapFeatures.rails(map.dimensionId()).segments().stream()
                .filter(s->selected.equals(s.line()) && (s.color()&0xffffff)==blue).toList();
    }
    private static void verifySwatch(Minecraft mc,PolishedWorldMapScreen atlas,Button button){
        try(var frame=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int px=(int)((button.getX()+7.5)*frame.getWidth()/atlas.width);
            int py=(int)((button.getY()+8.5)*frame.getHeight()/atlas.height);
            int abgr=frame.getPixelRGBA(px,py);
            int red=abgr&255, green=(abgr>>8)&255, blue=(abgr>>16)&255;
            if (blue<80 || blue<green*1.8 || blue<red*1.8)throw new AssertionError("Blue UI swatch was "+Integer.toHexString(abgr));
        }
    }
    private static void name(PolishedWorldMapScreen atlas,PassengerLinesScreen editor,String value){
        var input=editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        type(atlas,input,value);
    }
    private static void type(PolishedWorldMapScreen atlas,EditBox box,String value){
        click(atlas,box);atlas.keyPressed(GLFW.GLFW_KEY_END,0,0);
        for(int i=box.getValue().length();i>0;i--)atlas.keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0);
        for(char c:value.toCharArray())atlas.charTyped(c,0);
        if(!value.equals(box.getValue()))throw new AssertionError("Text entry: "+box.getValue());
    }
    private static CompoundTag row(PassengerLinesScreen editor)throws Exception{
        var selected=field(editor,"selected");var lines=(ListTag)field(editor,"lines");
        for(var entry:lines){var line=(CompoundTag)entry;if(selected.equals(line.getUUID("Id")))return line;}
        throw new AssertionError("No selected line: "+lines+" status="+field(editor,"status"));
    }
    private static void pressButton(PolishedWorldMapScreen atlas,PassengerLinesScreen editor,String key){
        var label=Component.translatable("gui.createcityexpansioncore.passenger_editor."+key).getString();
        var button=editor.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b->b.getMessage().getString().equals(label) || b.getMessage().getString().equals("■ "+label)).findFirst().orElseThrow();
        click(atlas,button);
    }
    private static void click(PolishedWorldMapScreen atlas,AbstractWidget widget){
        if(!widget.active)throw new AssertionError("Disabled control "+widget.getMessage().getString());
        int x=widget.getX()+widget.getWidth()/2,y=widget.getY()+widget.getHeight()/2;
        if(!atlas.mouseClicked(x,y,0))throw new AssertionError("Unconsumed click "+widget.getMessage().getString());
        atlas.mouseReleased(x,y,0);
    }
}
