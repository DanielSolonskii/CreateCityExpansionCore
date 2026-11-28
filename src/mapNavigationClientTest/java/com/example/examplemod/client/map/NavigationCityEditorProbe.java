package com.example.examplemod.client.map;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.citymap.CityMapNetwork;
import com.example.examplemod.citymap.CityMapService;
import com.example.examplemod.navigation.walk.WalkingRoutes;
import net.minecraft.world.level.block.Blocks;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.screen.CityCartographyScreen;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import static com.example.examplemod.client.map.NavigationClientProbe.*;

/** Exercises the normal atlas toolbar and feature card on a compact client display. */
final class NavigationCityEditorProbe {
    private static final String NAME = "Городская библиотека";
    private static CompletableFuture<?> job;
    private static UUID city, feature, path;
    private static final UUID STREET = UUID.randomUUID(), STATION = UUID.randomUUID();
    private static CompletableFuture<List<WalkingRoutes.Result>> walk, detour;
    private static String address;
    private static BlockPos center;
    private static int step, ticks;

    static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var user = mc.player.getUUID();
        job = server.submit(() -> {
            var actor = server.getPlayerList().getPlayer(user);
            var dim = actor.level().dimension().location();
            var plot = CityPlot.at(dim, actor.blockPosition());
            center = new BlockPos(plot.centerX(), actor.getBlockY(), plot.centerZ());
            var manager = CityManager.get(server.overworld());
            var town = manager.createCity("Картографы", UUID.randomUUID(), dim, center);
            check(town != null, "fixture city");
            town.addMember(user);
            city = town.getId();
            var level=actor.serverLevel();
            for(int x=-18;x<=18;x++) for(int z=-18;z<=18;z++) {
                level.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=0;y<=2;y++)level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            actor.connection.teleport(center.getX()+16.5,center.getY(),center.getZ()+14.5,0,0);
        });
    }

    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void next() { step++; ticks = 0; }

    static boolean tick(Minecraft mc, PolishedWorldMapScreen atlas, CityMapPanel map) throws Exception {
        if (++ticks > 420) throw new AssertionError("City editor stalled at " + step);
        if (ticks < 7) return false;
        var editor = atlas != null && field(atlas, "atlasTool") instanceof CityCartographyScreen e ? e : null;
        if (editor != null && field(editor, "pending") != null) return false;
        switch (step) {
            case 0 -> {
                if (!job.isDone()) return false;
                job.join();
                atlasKey();
                next();
            }
            case 1 -> {
                if (map == null || CityPublicDirectory.profile(city)==null || CityPublicDirectory.worldId(map.dimensionId())==null
                        || field(atlas,"drawingCity")==null) return false;
                var dim=map.dimensionId();
                var road=new CityMapFeature(STREET,city,0,dim,CityMapFeature.Kind.STREET,CityMapFeature.Shape.LINE,
                        "Вокзальная","","",List.of(center.offset(-15,0,-8),center.offset(15,0,-8)),List.of());
                var station=new CityMapFeature(STATION,city,0,dim,CityMapFeature.Kind.STATION,CityMapFeature.Shape.POINT,
                        "Старый вокзал","","Из прежней версии",List.of(center.offset(13,0,-3)),List.of(center.offset(13,0,0)));
                var view=CityPublicDirectory.viewId();var world=CityPublicDirectory.worldId(dim);
                for(var f:List.of(road,station)) net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new CityMapNetwork.Request(UUID.randomUUID(),view,world,dim,city,CityMapNetwork.Action.SAVE,f.id(),0,f));
                next();
            }
            case 2 -> {if(CityMapFeatures.get(STREET)!=null && CityMapFeatures.get(STATION)!=null){activate(atlas,"view.17");next();}}
            case 3 -> {
                if(editor==null || !(boolean)field(editor,"allowed"))return false;
                check(((List<?>)field(editor,"catalog")).size()==2,"both legacy objects received");
                map.setOverlay(CityMapPanel.Overlay.CITIES,false);
                action(atlas,editor,"kind.building");
                map.restoreView(center.getX()+.5,center.getZ()+.5,4);
                next();
            }
            case 4 -> {
                clickMap(atlas,map,center.offset(-3,0,-3));clickMap(atlas,map,center.offset(3,0,3));
                check(((List<?>)field(editor,"vertices")).size()==4 && field(editor,"page").toString().equals("DETAILS"),"two corners completed the building");
                input(atlas,editor,"optional_name",NAME);input(atlas,editor,"number","12А");
                action(atlas,editor,"street_choice");literalAction(atlas,editor,"Вокзальная");
                action(atlas,editor,"adjust");
                clickMap(atlas,map,center.offset(-3,0,-3));action(atlas,editor,"remove");
                atlas.keyPressed(GLFW.GLFW_KEY_Z,0,GLFW.GLFW_MOD_CONTROL);
                check(((List<?>)field(editor,"vertices")).size()==4,"undo restores removed vertex");
                screenshot(mc,"city-editor-polygon");action(atlas,editor,"done");
                action(atlas,editor,"entrance_action");
                clickMap(atlas,map,center.offset(3,0,0));
                check(field(editor,"page").toString().equals("DETAILS"),"adding entrance returns to card");
                input(atlas,editor,"description","Читальный зал");
                action(atlas,editor,"save");next();
            }
            case 5 -> {
                var saved=(CityMapFeature)field(editor,"saved");
                check(saved!=null && saved.links().street().equals(STREET) && NAME.equals(saved.name()) && saved.houseNumber().equals("12А")
                        && saved.entrances().equals(List.of(center.offset(3,0,0))),"building address, entrance and geometry confirmed: "+saved);
                feature=saved.id();screenshot(mc,"city-editor-card");
                address=saved.label(CityMapFeatures::get);
                check(address.contains("Вокзальная 12А"),"calculated house address: "+address);
                action(atlas,editor,"new");next();
            }
            case 6 -> {
                if(editor==null || !(boolean)field(editor,"allowed"))return false;
                literalAction(atlas,editor,"Старый вокзал");next();
            }
            case 7 -> {
                var old=(CityMapFeature)field(editor,"saved");
                check(old!=null && old.id().equals(STATION) && old.shape()==CityMapFeature.Shape.POINT &&
                        old.entrances().equals(List.of(center.offset(13,0,0))) && old.links().equals(CityMapFeature.Links.EMPTY),
                        "legacy point station opened without inventing a platform");
                action(atlas,editor,"new");next();
            }
            case 8 -> {
                if(editor==null || !(boolean)field(editor,"allowed"))return false;
                action(atlas,editor,"kind.path");
                map.restoreView(center.getX()+6.5,center.getZ()+5.5,4);
                next();
            }
            case 9 -> {
                for(var p:List.of(center.offset(3,0,0),center.offset(3,0,12),center.offset(13,0,12),center.offset(13,0,0)))
                    clickMap(atlas,map,p);
                action(atlas,editor,"done");input(atlas,editor,"name","Через сквер");
                action(atlas,editor,"from_link");clickMap(atlas,map,center.offset(3,0,0));
                action(atlas,editor,"to_link");clickMap(atlas,map,center.offset(13,0,0));
                action(atlas,editor,"save");next();
            }
            case 10 -> {
                var footway=(CityMapFeature)field(editor,"saved");
                check(footway!=null && footway.links().start().feature().equals(feature) && footway.links().end().feature().equals(STATION),
                        "path joins two saved entrances");
                path=footway.id();
                var server=mc.getSingleplayerServer();var user=mc.player.getUUID();walk=new CompletableFuture<>();
                job=server.submit(()->{
                    var actor=server.getPlayerList().getPlayer(user);
                    var outsider=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"AtlasVisitor"),ClientInformation.createDefault());
                    check(!outsider.hasPermissions(2) && CityManager.get(server.overworld()).getMemberCity(outsider.getUUID())==null,"ordinary visitor");
                    check(CityMapService.resolve(outsider,path).equals(footway),"public visitor sees linked path");
                    WalkingRoutes.find(actor,List.of(new WalkingRoutes.Leg(footway.links().start().point(),footway.links().end().point())),walk::complete);
                });
                next();
            }
            case 11 -> {
                if (!job.isDone() || !walk.isDone())return false;
                job.join();var route=walk.join().getFirst();
                check(route.status()==WalkingRoutes.Status.FOUND && route.path().stream().anyMatch(p->
                        p.getX()==center.getX()+3 && p.getZ()==center.getZ()+12),"route through the mapped turn: "+route);
                var corner=center.offset(3,0,12);
                if(detour==null){
                    var server=mc.getSingleplayerServer();var user=mc.player.getUUID();detour=new CompletableFuture<>();
                    job=server.submit(()->{
                        var actor=server.getPlayerList().getPlayer(user);var level=actor.serverLevel();
                        level.setBlockAndUpdate(corner,Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(corner.above(),Blocks.STONE.defaultBlockState());
                        WalkingRoutes.find(actor,List.of(new WalkingRoutes.Leg(center.offset(3,0,0),center.offset(13,0,0))),detour::complete);
                    });
                    return false;
                }
                if(!detour.isDone())return false;
                var alternative=detour.join().getFirst();
                check(alternative.status()==WalkingRoutes.Status.FOUND && alternative.path().getLast().equals(center.offset(13,0,0))
                        && alternative.path().stream().noneMatch(p->p.getX()==corner.getX() && p.getZ()==corner.getZ()),"wall bypass: "+alternative);
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("navigation-client.txt"),
                        "Opened v1 street and station. Saved v2 address: "+address+System.lineSeparator()
                                +"Two linked entrances. Walking route uses the drawn turn at "+corner.toShortString()
                                +" ("+route.path().size()+" positions). After placing a two-block wall there, the route bypassed it and reached the same station door ("
                                +alternative.path().size()+" positions)."+System.lineSeparator(), java.nio.file.StandardOpenOption.APPEND);
                press(atlas,GLFW.GLFW_KEY_ESCAPE);check(field(atlas,"atlasTool")==null,"back to atlas");
                check(!map.isOverlayEnabled(CityMapPanel.Overlay.CITIES),"drawing preserves hidden city layer");
                map.setOverlay(CityMapPanel.Overlay.CITIES,true);
                map.restoreView(center.getX()+.5,center.getZ()+.5,4);
                next();
            }
            case 12 -> {
                if(CityMapFeatures.get(feature)==null || CityMapFeatures.get(path)==null)return false;
                clickMap(atlas,map,center.offset(0,0,-1));next();
            }
            case 13 -> {
                var selection=map.getSelection();
                check(selection!=null && feature.equals(selection.id()),"public polygon is selectable above the pylon");
                var actions=(List<?>)field(atlas,"cardActions");int index=-1;
                for(int i=0;i<actions.size();i++) if("EDIT_CARTOGRAPHY".equals(get(actions.get(i),"action").toString()))index=i;
                check(index>=0,"city resident can reopen feature");activate(atlas,"card."+index);next();
            }
            case 14 -> {
                if(editor==null || !(boolean)field(editor,"allowed"))return false;
                var saved=(CityMapFeature)field(editor,"saved");
                var revised=new CityMapFeature(saved.id(),saved.city(),saved.revision(),saved.dimension(),saved.kind(),
                        saved.shape(),saved.name(),"14",saved.description(),saved.vertices(),saved.entrances(),saved.links());
                var request=new CityMapNetwork.Request(UUID.randomUUID(),CityPublicDirectory.viewId(),
                        CityPublicDirectory.worldId(saved.dimension()),saved.dimension(),city,CityMapNetwork.Action.SAVE,
                        feature,saved.revision(),revised);
                var server=mc.getSingleplayerServer();var user=mc.player.getUUID();
                job=server.submit(()->check(CityMapService.handle(server.getPlayerList().getPlayer(user),request).status().equals("ok"),
                        "concurrent save without changing linked entrance"));next();
            }
            case 15 -> {if(job.isDone()){job.join();action(atlas,editor,"save");next();}}
            case 16 -> {
                check((boolean)field(editor,"needsReload"),"stale edit rejected");
                action(atlas,editor,"reload");next();
            }
            case 17 -> {
                check(((CityMapFeature)field(editor,"saved")).houseNumber().equals("14"),"stale recovery");
                action(atlas,editor,"delete");action(atlas,editor,"confirm_delete");next();
            }
            case 18 -> {
                check(editor!=null && field(editor,"notice").toString().contains("link_in_use"),"referenced entrance protected");
                action(atlas,editor,"new");next();
            }
            case 19 -> {
                if(editor==null || !(boolean)field(editor,"allowed"))return false;
                literalAction(atlas,editor,"Через сквер");next();
            }
            case 20 -> {
                if(editor==null || !(boolean)field(editor,"allowed"))return false;
                action(atlas,editor,"delete");action(atlas,editor,"confirm_delete");next();
            }
            case 21 -> {
                if(editor!=null || CityMapFeatures.get(path)!=null)return false;
                atlas.onClose();return true;
            }
        }
        return false;
    }

    private static void clickMap(PolishedWorldMapScreen atlas,CityMapPanel map,BlockPos point) {
        int x=map.worldToScreenX(point.getX()+.5), y=map.worldToScreenY(point.getZ()+.5);
        check(atlas.mouseClicked(x,y,0),"click map at "+x+","+y);
        atlas.mouseReleased(x,y,0);
    }

    private static void click(PolishedWorldMapScreen atlas,AbstractWidget widget) {
        int x=widget.getX()+widget.getWidth()/2, y=widget.getY()+widget.getHeight()/2;
        check(atlas.mouseClicked(x,y,0),"click "+widget.getMessage()); atlas.mouseReleased(x,y,0);
    }

    private static void type(PolishedWorldMapScreen atlas,EditBox box,String value) {
        click(atlas,box);
        for (char c:value.toCharArray()) atlas.charTyped(c,0);
        check(box.getValue().equals(value),"typed "+value);
    }

    private static void input(PolishedWorldMapScreen atlas,CityCartographyScreen editor,String key,String value) {
        var field=(EditBox)find(atlas,editor,key,true,false);
        type(atlas,field,value);
    }
    private static void literalAction(PolishedWorldMapScreen atlas,CityCartographyScreen editor,String name) {
        click(atlas,find(atlas,editor,name,false,true));
    }
    private static AbstractWidget find(PolishedWorldMapScreen atlas,CityCartographyScreen editor,String name,boolean field,boolean literal) {
        for(int sweep=0;sweep<2;sweep++)for(int n=0;n<20;n++){
            for(var child:editor.children())if(child instanceof AbstractWidget widget && widget.active && (field?widget instanceof EditBox:widget instanceof Button)){
                var text=widget.getMessage();
                if(literal ? text.getString().equals(name) : text.getContents() instanceof TranslatableContents t
                        && t.getKey().equals("gui.createcityexpansioncore.city_editor."+name))return widget;
            }
            atlas.mouseScrolled(atlas.width-18,atlas.height/2,0,sweep==0?-1:1);
        }
        throw new AssertionError("Missing "+name+" in city editor");
    }
    private static void action(PolishedWorldMapScreen atlas,CityCartographyScreen editor,String key){
        click(atlas,find(atlas,editor,key,false,false));
    }
}
