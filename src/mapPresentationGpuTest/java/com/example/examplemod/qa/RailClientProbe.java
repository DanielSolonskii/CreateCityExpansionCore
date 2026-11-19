package com.example.examplemod.qa;

import com.example.examplemod.cargo.center.CargoRailConnections;
import com.example.examplemod.client.logistics.LogisticsCenterScreen;
import com.example.examplemod.client.logistics.LogisticsMapOverlay;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.TerrainPixelSmoke;
import com.example.examplemod.client.screen.PolishedWorldMapScreen;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Opt-in test client. Uses an isolated integrated world, the production atlas, and its framebuffer. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class RailClientProbe {
    private enum Step { TITLE, WORLD, SAMPLE, ATLAS, READ_PIXELS, FINISHED }
    private static Step step=Step.TITLE;
    private static int ticks;
    private static final long STARTED=System.nanoTime();
    private static volatile CompoundTag preview;
    private static volatile Throwable serverFailure;
    private static Path receipt;

    private static void mark(String message) throws Exception {
        Files.writeString(receipt,message+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        System.out.println("Map GPU check: "+message);
    }

    private static void fail(Throwable error) {
        step=Step.FINISHED;
        try {
            if(receipt!=null) mark("FAIL "+error);
        } catch(Exception ignored) {}
        error.printStackTrace();
        Minecraft.getInstance().stop();
    }

    private static TrackNode node(TrackGraph graph,int id,int x,int z) {
        var location=new TrackNodeLocation(x,64,z).in(Level.OVERWORLD);
        var node=new TrackNode(location,id,new Vec3(0,1,0));
        graph.addNode(node);
        return node;
    }

    private static ListTag sample(MinecraftServer server,TrackGraph graph,List<TrackNode> nodes) {
        for(int i=1;i<nodes.size();i++) {
            var from=nodes.get(i-1);
            var to=nodes.get(i);
            graph.putConnection(from,to,new TrackEdge(from,to,null,TrackMaterial.ANDESITE));
        }
        return CargoRailConnections.geometry(server,new CargoRailConnections.Path(graph.id,nodes));
    }

    private static void buildPreview(MinecraftServer server) {
        try {
            var graph=new TrackGraph();
            var graphs=RailwaySavedData.load(server).getTrackNetworks();
            graphs.put(graph.id,graph);
            try {
                var rails=sample(server,graph,List.of(node(graph,201,-24,0),node(graph,202,-12,0),node(graph,203,-7,5)));
                for(var raw:rails) {
                    var segment=(CompoundTag)raw;
                    segment.putBoolean("Focus",true);
                    segment.putString("Phase","ROAD");
                }
                rails.addAll(sample(server,graph,List.of(node(graph,204,4,0),node(graph,205,24,0))));
                if(rails.size()!=3) throw new AssertionError("Expected three Create samples, got "+rails.size());
                var result=new CompoundTag();
                result.put("Rails",rails);
                result.putBoolean("RouteFound",true);
                preview=result;
            } finally {
                graphs.remove(graph.id);
            }
        } catch(Throwable error) {
            serverFailure=error;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(step==Step.FINISHED) return;
        var mc=Minecraft.getInstance();
        try {
            if(receipt==null) {
                var root=mc.gameDirectory.getCanonicalFile().toPath();
                if(!root.equals(Path.of(System.getProperty("ccec.qa.dir"))))
                    throw new IllegalStateException("Unexpected test game directory");
                receipt=root.resolve("rail-terrain-gl.txt");
                if(Files.exists(receipt)||Files.exists(root.resolve("saves/RailQA")))
                    throw new IllegalStateException("The map GPU check requires a new test directory");
                mark("Started isolated client");
            }
            if(serverFailure!=null) throw new IllegalStateException("Server fixture",serverFailure);
            if(System.nanoTime()-STARTED>360_000_000_000L) throw new IllegalStateException("Timeout waiting for "+step);
            ticks++;
            switch(step) {
                case TITLE -> {
                    if(!(mc.screen instanceof TitleScreen title)||mc.getOverlay()!=null||ticks<25)return;
                    step=Step.WORLD;
                    ticks=0;
                    mc.createWorldOpenFlows().createFreshLevel("RailQA",MinecraftServer.DEMO_SETTINGS,WorldOptions.DEMO_OPTIONS,
                            WorldPresets::createNormalWorldDimensions,title);
                }
                case WORLD -> {
                    if(mc.player==null||mc.level==null||mc.getOverlay()!=null) return;
                    if(mc.screen!=null) {
                        if(ticks>110) mc.setScreen(null);
                        return;
                    }
                    step=Step.SAMPLE;
                    var server=mc.getSingleplayerServer();
                    server.execute(()->buildPreview(server));
                }
                case SAMPLE -> {
                    if(preview==null) return;
                    var center=new LogisticsCenterScreen();
                    LogisticsMapOverlay.reviewRoute(center,center,new CompoundTag(),preview);
                    mark("Opened production route review with three real Create track samples");
                    step=Step.ATLAS;
                    ticks=0;
                }
                case ATLAS -> {
                    if(!(mc.screen instanceof PolishedWorldMapScreen atlas)||ticks<5)return;
                    var map=activePanel(atlas);
                    if(map==null||map.viewportRight()-map.viewportLeft()<70)return;
                    LogisticsMapOverlay.cancelAutomaticFocus();
                    map.restoreView(0,0,2);
                    step=Step.READ_PIXELS;
                }
                default -> {}
            }
        } catch(Throwable error) { fail(error); }
    }

    private static CityMapPanel activePanel(PolishedWorldMapScreen screen)throws ReflectiveOperationException {
        var field=PolishedWorldMapScreen.class.getDeclaredField("map");
        field.setAccessible(true);
        return (CityMapPanel)field.get(screen);
    }

    private static int rgb(double x,double y) {
        var window=Minecraft.getInstance().getWindow();
        double scale=window.getGuiScale();
        try(var stack=MemoryStack.stackPush()) {
            var pixel=stack.malloc(4);
            GL11.glReadPixels((int)Math.floor(x*scale),window.getHeight()-1-(int)Math.floor(y*scale),
                    1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            return ((pixel.get(0)&255)<<16)|((pixel.get(1)&255)<<8)|(pixel.get(2)&255);
        }
    }

    private static int pixelRows(double x,double y,int background) {
        double row=1/Minecraft.getInstance().getWindow().getGuiScale();
        int count=0;
        for(int i=-12;i<=12;i++) if(rgb(x,y+i*row)!=background) count++;
        return count;
    }

    private static void checkRails(GuiGraphics g,CityMapPanel map)throws Exception {
        double x=map.worldToScreenXExact(-18), y=map.worldToScreenZExact(0);
        double plainX=map.worldToScreenXExact(14), gapX=map.worldToScreenXExact(0);
        g.fill(map.viewportLeft(),map.viewportTop(),map.viewportRight(),map.viewportBottom(),0xff19314a);
        g.flush();
        int background=rgb(gapX,y);
        g.enableScissor(map.viewportLeft(),map.viewportTop(),map.viewportRight(),map.viewportBottom());
        try { LogisticsMapOverlay.renderRails(g,map); }
        finally { g.disableScissor(); }
        g.flush();
        int chosen=rgb(x,y), plain=rgb(plainX,y), gap=rgb(gapX,y);
        int joint=rgb(map.worldToScreenXExact(-12),y);
        int selectedRows=pixelRows(x,y,background), plainRows=pixelRows(plainX,y,background);
        mark(String.format("Framebuffer RGB chosen=%06x ordinary=%06x joint=%06x gap=%06x background=%06x, "+
                "stroke pixel rows=%d/%d, GUI scale=%.1f",chosen,plain,joint,gap,background,selectedRows,plainRows,
                Minecraft.getInstance().getWindow().getGuiScale()));
        if(gap!=background || chosen==background || plain==background || chosen==plain || joint==background ||
                selectedRows<plainRows || plainRows<2) throw new AssertionError("Rail framebuffer mismatch");
        g.fill((int)gapX-1,(int)y-1,(int)gapX+2,(int)y+2,0xff26cc4e);
        g.flush();
        if(rgb(gapX,y)!=0x26cc4e) throw new AssertionError("Following GUI pass lost its state");
    }

    @SubscribeEvent public static void drawn(ScreenEvent.Render.Post event) {
        if(step!=Step.READ_PIXELS||!(event.getScreen() instanceof PolishedWorldMapScreen atlas))return;
        try {
            var map=activePanel(atlas);
            if(map==null||Math.abs(map.worldToScreenXExact(1)-map.worldToScreenXExact(0)-2)>.0001)return;
            var g=event.getGuiGraphics();
            checkRails(g,map);
            mark("RAIL_GL_PASS");
            TerrainPixelSmoke.run(g);
            mark("TERRAIN_GL_PASS");
            mark("PASS");
            step=Step.FINISHED;
            Minecraft.getInstance().stop();
        } catch(Throwable error) { fail(error); }
    }

    private RailClientProbe() {}
}
