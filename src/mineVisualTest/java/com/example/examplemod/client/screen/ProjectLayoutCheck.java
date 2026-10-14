package com.example.examplemod.client.screen;

public final class ProjectLayoutCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        presentation();
        for(int width:new int[]{320,426,560,640,854,1280,1920})for(int height:new int[]{240,360,480,720,1080})for(int top:new int[]{48,78}) {
            var panel=ProjectLayout.panel(width,height,top);var body=ProjectLayout.body(panel);var toggle=ProjectLayout.toggle(width,height);
            require(panel.x()>=0&&panel.right()<=width&&panel.bottom()<=height,"panel stays on screen");
            require(toggle.x()==panel.x()&&toggle.width()==panel.width()&&toggle.bottom()<=height,"toggle shares context column");
            require(!panel.overlaps(toggle),"toggle does not cover content");
            require(body.x()>=panel.x()&&body.right()<=panel.right()&&body.y()>=panel.y()&&body.bottom()<=panel.bottom(),"body remains inside surface");
            for(int content:new int[]{0,20,200,600,2000}) {
                int max=ProjectLayout.clampScroll(10000,content,body.height());
                require(max==Math.max(0,content-body.height()),"full body scroll range");
                require(ProjectLayout.clampScroll(-20,content,body.height())==0,"negative scroll clamped");
                var row=ProjectLayout.row(body,max,max,24);require(row.y()==body.y()&&row.contains(row.x()+1,row.y()+1),"paint and hit geometry agree after scroll");
            }
        }
        System.out.println("PASS: "+checks+" project workspace layout checks");
    }
    private static void presentation() throws Exception {
        var far=com.example.examplemod.client.map.TerritoryPresentation.style(.0625,0,false,false);
        var city=com.example.examplemod.client.map.TerritoryPresentation.style(.8,0,false,false);
        var close=com.example.examplemod.client.map.TerritoryPresentation.style(4,0,false,false);
        require(far.fillAlpha()==0&&far.borderAlpha()==0,"world view has city markers only");
        require(city.fillAlpha()>=30&&city.fillAlpha()<=46,"city view has restrained territory color");
        require(close.fillAlpha()==0&&close.borderAlpha()>0,"construction scale keeps only a quiet boundary");
        require(com.example.examplemod.client.map.TerritoryPresentation.cityRadius(4)<com.example.examplemod.client.map.TerritoryPresentation.cityRadius(.1),"city markers shrink on construction scale");
        require(com.example.examplemod.client.map.TerritoryPresentation.style(.8,0,true,false).fillAlpha()<city.fillAlpha(),"project workspace reduces territory competition");
        require(com.example.examplemod.client.map.TerritoryPresentation.style(.8,2,false,false).borderAlpha()==0,"hidden territories are hidden");
        require(com.example.examplemod.client.map.TerritoryPresentation.style(.0625,1,false,false).borderAlpha()>0,"always mode retains region outlines");
        require(com.example.examplemod.client.map.TerritoryPresentation.style(4,2,true,true).borderAlpha()>0,"zone selection temporarily restores legal boundary");
        var dim=net.minecraft.resources.ResourceLocation.parse("minecraft:overworld");
        require(ProjectPanel.shouldRenderMapLayer(false,true,false),"creation preview survives hidden project layer");
        require(ProjectPanel.shouldRenderMapLayer(false,false,true),"selected saved project survives hidden layer while its card is open");
        require(!ProjectPanel.shouldRenderMapLayer(false,false,false),"hidden layer stays hidden outside project editing or selection");
        for(var shape:com.example.examplemod.projects.ProjectZone.Shape.values()) {
            var preview=ProjectPanel.previewZone(dim,shape,null,null,-10,20);
            require(preview.shape()==com.example.examplemod.projects.ProjectZone.Shape.POINT&&preview.x1()==-10&&preview.z1()==20,"cursor shows placement point before first click for "+shape);
        }
        var preview=ProjectPanel.previewZone(dim,com.example.examplemod.projects.ProjectZone.Shape.RECTANGLE,10,20,30,40);
        require(preview.minX()==10&&preview.maxX()==30&&preview.minZ()==20&&preview.maxZ()==40,"first anchor expands into the selected construction shape");
        var a=new com.example.examplemod.city.CityPlot(dim,-1,0);var b=new com.example.examplemod.city.CityPlot(dim,0,0);
        var edges=com.example.examplemod.client.map.TerritoryPresentation.boundary(java.util.Set.of(a,b));
        require(edges.size()==6,"adjacent plots have no interior edge");
        require(edges.stream().noneMatch(e->e.x1()==0&&e.x2()==0),"shared edge is never drawn through the city");
        require(com.example.examplemod.client.map.TerritoryPresentation.boundary(java.util.Set.of(a,new com.example.examplemod.city.CityPlot(dim,4,4))).size()==8,"disconnected claims stay disconnected");
        var marker=ProjectMapStyle.marker(40,50);require(marker.contains(40,50)&&!marker.contains(60,50),"marker drawing and selection share bounds");
        var viewport=new WorldMapUiLayout.Rect(8,48,240,180);
        for(int x:new int[]{8,128,247})for(int y:new int[]{48,140,227}) {
            var label=ProjectMapStyle.label(x,y,144,viewport);
            require(label.x()>=viewport.x()&&label.right()<=viewport.right()&&label.y()>=viewport.y()&&label.bottom()<=viewport.bottom(),"edge labels stay inside map and share hit bounds");
        }
        require(!ProjectMapStyle.zones(.1,false)&&ProjectMapStyle.zones(.1,true),"selected projects survive regional zoom");
        require(ProjectMapStyle.color(com.example.examplemod.projects.Project.Status.OPEN)!=ProjectMapStyle.color(com.example.examplemod.projects.Project.Status.BUILDING),"open and building colors differ");
        for(var status:com.example.examplemod.projects.Project.Status.values()) {
            require(ProjectMapStyle.color(com.example.examplemod.projects.Project.Scope.CITY,status)==ProjectMapStyle.color(status),"CITY palette remains unchanged");
            require(ProjectMapStyle.color(com.example.examplemod.projects.Project.Scope.SERVER,status)==(status.terminal()?0xFFC49C51:ProjectMapStyle.SERVER_COLOR),"SERVER legend and map use amber semantics");
        }
        for(var category:com.example.examplemod.projects.Project.Category.values()) {
            require(!java.util.Arrays.equals(ProjectMapStyle.glyph(com.example.examplemod.projects.Project.Scope.SERVER,category,com.example.examplemod.projects.Project.Status.OPEN),ProjectMapStyle.glyph(category,com.example.examplemod.projects.Project.Status.OPEN)),"SERVER star differs from every CITY category glyph");
        }
        var signatures=new java.util.HashSet<String>();
        for(var category:com.example.examplemod.projects.Project.Category.values())require(signatures.add(java.util.Arrays.toString(ProjectMapStyle.glyph(category,com.example.examplemod.projects.Project.Status.OPEN))),"category glyphs are distinct");
        var manager=new com.example.examplemod.city.CityManager();
        var own=manager.createCity("Own",java.util.UUID.randomUUID(),new net.minecraft.core.BlockPos(-10000,64,-10000));
        var other=manager.createCity("Other",java.util.UUID.randomUUID(),new net.minecraft.core.BlockPos(550,64,0));
        var add=com.example.examplemod.city.City.class.getDeclaredMethod("addClaim",com.example.examplemod.city.CityPlot.class);add.setAccessible(true);
        add.invoke(other,new com.example.examplemod.city.CityPlot(dim,10,0));add.invoke(other,new com.example.examplemod.city.CityPlot(dim,9,0));
        var views=manager.territoryViews(dim,new net.minecraft.core.BlockPos(450,0,0),20,own.getId(),id->false);
        require(views.stream().anyMatch(v->v.cityId().equals(other.getId())),"foreign border is returned even when its pylon lies outside viewport");
        require(views.getFirst().cityId().equals(own.getId()),"own claims remain available for project validation while panning away");
        for(int i=0;i<20;i++)manager.createCity("City"+i,java.util.UUID.randomUUID(),new net.minecraft.core.BlockPos(i*100,64,200));
        var crowded=manager.territoryViews(dim,net.minecraft.core.BlockPos.ZERO,5000,own.getId(),id->false);
        require(crowded.size()==com.example.examplemod.network.CityMapPeersPayload.MAX_PEERS&&crowded.getFirst().own(),"bounded snapshots prioritize own city");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            var request=new com.example.examplemod.network.RequestCityTerritoriesPayload(own.getPylonPos(),new net.minecraft.core.BlockPos(-4000,0,9000),999999);
            com.example.examplemod.network.RequestCityTerritoriesPayload.STREAM_CODEC.encode(buffer,request);
            var decoded=com.example.examplemod.network.RequestCityTerritoriesPayload.STREAM_CODEC.decode(buffer);
            require(decoded.equals(request)&&decoded.range()==32768,"viewport request codec preserves origin and clamps server work radius");
        }finally{buffer.release();}
    }
}
