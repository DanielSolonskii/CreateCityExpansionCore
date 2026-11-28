package com.example.examplemod.client.screen;

import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.city.TerrainPyramid;
import com.example.examplemod.client.map.PlanSurface;
import com.example.examplemod.client.map.TerrainTileStore;
import com.example.examplemod.projects.EngineeringPlan;
import com.example.examplemod.projects.EngineeringPlan.Kind;
import com.example.examplemod.projects.EngineeringPlan.Node;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

final class PlanSurveyCheck {
    private static final ResourceLocation DIM = ResourceLocation.parse("minecraft:overworld");
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static EngineeringPlan plan(Kind kind, Node... nodes) {
        return new EngineeringPlan(UUID.randomUUID(), DIM, kind, "Депо", "Вторая очередь", List.of(nodes));
    }
    static void run() throws Exception {
        var polygon = plan(Kind.AREA, new Node(-20,-10), new Node(12,-10), new Node(12,20), new Node(-20,20));
        check(polygon.complete() && polygon.area()==960 && polygon.length()==124, "polygon measurements");
        check(polygon.touches(0,0,3,3) && polygon.touches(11,19,30,30), "enclosed and intersected plots");
        check(!polygon.touches(13,0,14,1), "adjacent plot outside footprint");
        check(!plan(Kind.AREA,new Node(0,0),new Node(5,5),new Node(0,5),new Node(5,0)).complete(), "self crossing");
        var far = plan(Kind.AREA,new Node(29999997,-30000000),new Node(30000000,-30000000),
                new Node(30000000,-29999998),new Node(29999997,-29999998));
        check(far.area()==6 && far.complete(), "area at world border");
        var diagonal=plan(Kind.LINE,new Node(-3,0),new Node(0,4));
        check(diagonal.length()==5 && diagonal.complete() && diagonal.touches(-2,1,-1,2), "crossed plot without endpoint");
        var line=plan(Kind.LINE,new Node(-30000000,-2),new Node(30000000,-2));
        var profile=PlanSurvey.profile(line, (x,z)->x<0 ? new PlanSurface(-24, 17, true) : null);
        check(profile.samples().size()==PlanSurvey.MAX_SAMPLES && profile.step()>1, "bounded profile");
        check(profile.samples().getFirst().height().stale() && profile.samples().getLast().height()==null, "unknown gap");
        check(profile.samples().getLast().distance()==line.length(), "covers entire long segment");
        check(PlanSurvey.profile(plan(Kind.POINT), (x,z)->{throw new AssertionError();}).samples().isEmpty(), "empty draft");
        heightSamples();
        localDrafts(diagonal);
        System.out.println("PASS: engineering geometry, negative terrain samples, gaps, bounded history and scoped drafts");
    }
    private static void heightSamples() {
        UUID world=UUID.randomUUID();
        int[] colors=new int[1024];
        Arrays.fill(colors,CityMapSavedData.UNKNOWN_COLOR);
        short[] heights=new short[34*34];
        Arrays.fill(heights,TerrainPyramid.NO_HEIGHT);
        // (-1,-33) is local (31,31); the stored survey includes a one-cell halo.
        colors[31*32+31]=0xFF305010;
        heights[32*34+32]=-19;
        var tile=new TerrainTileStore.Tile(colors,new BitSet(),false,4,heights,5,world);
        var known=PlanSurface.sample(tile,world,-1,-33,2_500,false,3_000);
        check(known!=null && known.y()==-19 && !known.stale(), "negative column, halo and sub-sea surface");
        check(PlanSurface.sample(tile,world,-1,-33,2_500,false,9_000).stale(), "partial survey TTL");
        check(PlanSurface.sample(tile,world,-1,-33,2_500,true,3_000).stale(), "invalidated tile");
        check(PlanSurface.sample(tile,UUID.randomUUID(),-1,-33,2_500,false,3_000)==null, "world isolation");
        colors[31*32+31]=CityMapSavedData.UNKNOWN_COLOR;
        var fog=new TerrainTileStore.Tile(colors,new BitSet(),true,4,heights,5,world);
        check(PlanSurface.sample(fog,world,-1,-33,2_500,false,3_000)==null, "known halo does not reveal fog");
        colors[31*32+31]=0xFF305010; heights[32*34+32]=TerrainPyramid.NO_HEIGHT;
        var missing=new TerrainTileStore.Tile(colors,new BitSet(),true,4,heights,5,world);
        check(PlanSurface.sample(missing,world,-1,-33,2_500,false,3_000)==null, "missing height");
    }
    private static void localDrafts(EngineeringPlan doc) throws IOException {
        var history=new PlanHistory(doc);
        for(int i=0;i<PlanHistory.LIMIT+5;i++) history.change(doc.describe("V"+i,""));
        int undone=0;
        while(history.undo()) undone++;
        check(undone==PlanHistory.LIMIT && history.redo(), "bounded undo and redo");
        history.change(doc);
        check(!history.redo(), "editing discards abandoned redo branch");
        var dir=Files.createTempDirectory("ccec-plan-check-");
        var area=new AtlasWorkspace.Area(DIM.toString(),UUID.randomUUID());
        try {
            var store=new PlanDraftStore(dir,area);
            check(store.put(doc), "save local draft");
            store.save();
            check(doc.equals(new PlanDraftStore(dir,area).selected()), "persisted geometry and text");
            check(new PlanDraftStore(dir,new AtlasWorkspace.Area(DIM.toString(),UUID.randomUUID())).all().isEmpty(), "new save at same address");
            check(new PlanDraftStore(dir,new AtlasWorkspace.Area("minecraft:the_nether",area.world())).all().isEmpty(), "dimension isolation");
            for(int i=1;i<PlanDraftStore.LIMIT;i++) check(store.put(plan(Kind.POINT)), "draft capacity");
            check(!store.put(plan(Kind.POINT)), "bounded plan count");
            java.nio.file.Path file;
            try(var files=Files.list(dir)) { file=files.findFirst().orElseThrow(); }
            Files.write(file,new byte[]{1,2,3});
            var broken=new PlanDraftStore(dir,area);
            check(broken.unreadable && broken.all().isEmpty(), "malformed local cache");
            try { broken.save(); throw new AssertionError("overwrote corrupt library"); }
            catch(IOException expected) { check(Files.size(file)==3,"retained source bytes"); }
        } finally {
            try(var files=Files.list(dir)) { for(var path:files.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(dir);
        }
    }
}
