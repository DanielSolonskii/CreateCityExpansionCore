package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.client.map.CityMapPanel;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorldMapPanelLifecycleCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        var session=CityWorldMapSession.INSTANCE;
        session.awaitAnchor();
        var screen=new PolishedWorldMapScreen(session);
        require(!screen.isPauseScreen(),"a cold atlas must allow the integrated server to deliver its first snapshot");
        screen.mouseReleased(1,1,0);
        screen.mouseDragged(1,1,0,2,3);
        screen.mouseScrolled(1,1,0,1);
        screen.onClose();
        int[] dismissed={0};
        screen.closeTo(target -> {
            require(target==null,"an unbound map returns to the game");
            dismissed[0]++;
        });
        require(dismissed[0]==1,"closing the loading shell navigates once");
        Object menu=new Object();
        require(AtlasViewContext.canReturn(menu,menu,true),"same container can resume");
        require(!AtlasViewContext.canReturn(new Object(),menu,true),"replacement container cannot resume");
        require(!AtlasViewContext.canReturn(menu,menu,false),"different dimension cannot resume management");
        require(AtlasViewContext.canReturn(menu,null,false),"read-only atlas has no management requirement");
        var memory=new AtlasViewContext();
        var link=new Object();
        var target=new AtlasViewContext.Target(CityMapPanel.SelectionKind.CITY,UUID.randomUUID(),BlockPos.ZERO);
        var state=new AtlasViewContext.State(345,-70,.5,true,"cargo",2,1,true,4,"city",18,target,AtlasSearch.Tab.FAVORITES,4,null,"");
        memory.put(link,"worldA:atlas",state);
        require(state.equals(memory.get(link,"worldA:atlas")),"camera, query, filters, scroll and target survive return");
        require(memory.get(link,"worldB:atlas")==null,"new save has no previous view");
        require(memory.get(link,"worldA:planning")==null,"logistics camera is independent");
        for(int i=0;i<20;i++)memory.put(link,"world"+i,state);
        require(memory.get(link,"worldA:atlas")==null,"unlikely old scopes are bounded");
        require(memory.get(new Object(),"world19")==null,"reconnect cannot reattach previous view");
        var dimension=net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld");
        session.begin(BlockPos.ZERO,dimension);
        screen.directoryReady(new com.example.examplemod.network.CityDirectoryPayload(UUID.randomUUID(),
                BlockPos.ZERO,dimension,0,0,List.of(dimension),List.of()));
        var overview=new AtlasViewContext.State(87.5,-99,.75,true,"mine",1,2,false,3,"overview:CITIES",12,null,AtlasSearch.Tab.RECENT,3,null,"");
        screen.restoreView(overview);
        require(overview.equals(screen.captureView()),"actual screen restores camera, search, filters and card scroll");
        var panel=new CityMapPanel(BlockPos.ZERO,dimension,List.of());
        var warning=new com.example.examplemod.city.CityProjectSnapshot(UUID.randomUUID(),
                com.example.examplemod.city.CityProjectType.INDUSTRIAL_MINE,dimension,BlockPos.ZERO,
                UUID.randomUUID(),dimension,1,"BLOCKED");
        panel.setProjects(List.of(warning));
        var problems=panel.problemObjects();
        require(problems.size()==1&&problems.getFirst().id().equals(warning.projectId()),"changed industry snapshot updates issues");
        panel.setProjects(List.of(warning));
        require(problems==panel.problemObjects(),"unchanged snapshot shares issue list across frames");
        panel.setProjects(List.of());
        require(panel.problemObjects().isEmpty(),"access loss clears issues");
        var train=UUID.randomUUID();
        panel.restoreView(-100.25,224.5,2);
        panel.toggleFollow(train);
        require(train.equals(panel.followedTrain()),"explicit follow binds one train");
        panel.beginPan();
        require(panel.followedTrain()==null,"a manual drag immediately releases its train");
        panel.setInertiaEnabled(false);
        require(panel.mouseDragged(1,1,0,3.5,-7.25),"the panel handles a fractional drag");
        require(panel.centerX()==-102 && panel.centerZ()==228.125,"drag changes the current camera directly at its zoom");
        require(panel.mouseReleased(1,1,0),"manual drag releases");
        panel.toggleFollow(train);
        panel.toggleMeasureTool();
        require(panel.followedTrain()==null && panel.cameraPrecisionActive(),"measurement cancels tracking");
        panel.toggleMeasureTool();
        var city=new com.example.examplemod.city.CityPublicProfile(target.id(),"Port",dimension,BlockPos.ZERO,
                0x46B29D,2,1,false,false,1,com.example.examplemod.city.CityIdentity.restore("",""));
        panel.receivePublicCities(List.of(city));
        require(panel.resolveSelection(target.kind(),target.id(),target.pos())!=null,"current directory resolves city by id");
        panel.receivePublicCities(List.of());
        require(panel.resolveSelection(target.kind(),target.id(),target.pos())==null,"removed city cannot restore an old card");
        session.clear();
        System.out.println("PASS: loading-screen input and close, container ownership, bounded atlas navigation");
    }
}
