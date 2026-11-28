package com.example.examplemod.client.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Files;
import java.util.*;

public final class AtlasWorkspaceCheck {
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    public static void run() throws Exception {
        PlanSurveyCheck.run();
        SharedPlanCheck.run();
        var directory=Files.createTempDirectory("ccec-search-views");
        try {
            var dim=ResourceLocation.parse("minecraft:overworld");
            var area=new AtlasWorkspace.Area(dim.toString(),UUID.randomUUID());
            UUID request=UUID.randomUUID();String project="PROJECT/"+dim+"/id";
            check(AtlasSearch.arrival(request,request,dim,dim,project,false,1,10)==AtlasSearch.Arrival.WAIT,"directory before project snapshot");
            check(AtlasSearch.arrival(request,request,dim,dim,project,true,2,10)==AtlasSearch.Arrival.READY,"new authorized snapshot resumes selection");
            check(AtlasSearch.arrival(request,UUID.randomUUID(),dim,dim,project,true,2,10)==AtlasSearch.Arrival.EXPIRED,"obsolete view cannot resume selection");
            check(AtlasSearch.arrival(request,request,dim,dim,project,false,10,10)==AtlasSearch.Arrival.EXPIRED,"unanswered project lookup expires");
            var world=AtlasWorkspace.load(directory,area);
            var camera=new AtlasWorkspace.Camera(-12345.75,293.5,.875,9,3,1,true,"кузня",2,true,false,true);
            UUID id=world.create("Вечерний рейс",camera);
            check(id!=null&&world.create("  ",camera)==null,"views require names");
            var station=new AtlasSearch.Entry(AtlasSearch.Type.STATION,"station","Речная","Берег",dim,BlockPos.ZERO,false,false,null);
            var city=new AtlasSearch.Entry(AtlasSearch.Type.CITY,"city","Речная","",dim,BlockPos.ZERO,true,false,null);
            List<AtlasSearch.Entry> catalog=List.of(city,station);
            check(AtlasSearch.find(catalog,"РЕЧ",AtlasSearch.Type.ALL,0,false,AtlasSearch.Tab.ALL,world,dim).size()==2,"same names remain distinct");
            check(AtlasSearch.find(catalog,"берег",AtlasSearch.Type.STATION,0,false,AtlasSearch.Tab.ALL,world,dim).equals(List.of(station)),"city and type filter");
            world.toggle(station.key()); world.visit(city.key());world.visit(station.key());world.visit(city.key());
            check(AtlasSearch.find(catalog,"",AtlasSearch.Type.ALL,0,false,AtlasSearch.Tab.RECENT,world,dim).equals(List.of(city,station)),"recent order is deduplicated");
            check(AtlasSearch.find(List.of(city),"",AtlasSearch.Type.ALL,0,false,AtlasSearch.Tab.FAVORITES,world,dim).isEmpty(),"withdrawn favorite does not expose old data");
            var point=AtlasSearch.find(List.of(),"-30000000, +4",AtlasSearch.Type.ALL,0,false,AtlasSearch.Tab.ALL,world,dim).getFirst();
            check(point.pos().equals(new BlockPos(-30000000,0,4))&&AtlasSearch.coordinates("40000000,0")==null&&AtlasSearch.coordinates("0,0,5")==null,"coordinate validation");
            world.toggle(point.key());world.visit(point.key());
            check(AtlasSearch.find(List.of(),"",AtlasSearch.Type.COORDINATES,0,false,AtlasSearch.Tab.FAVORITES,world,dim).equals(List.of(point)),"coordinate favorites restore");
            var other=new AtlasWorkspace.Area("minecraft:the_nether",UUID.randomUUID());world.link(other);world.save();
            var loaded=AtlasWorkspace.load(directory,area);
            check(loaded.favorite(station.key())&&loaded.linkedAreas().contains(other)&&loaded.views().getFirst().camera().equals(camera),"complete view and stable keys persist");
            check(AtlasWorkspace.load(directory,new AtlasWorkspace.Area(dim.toString(),UUID.randomUUID())).views().isEmpty(),"another save at same address");
            check(AtlasWorkspace.load(directory.resolve("other-player"),area).views().isEmpty(),"another player");
            var updated=new AtlasWorkspace.Camera(1.5,.5,3,4,2,0,false,"",1,false,true,false);
            check(loaded.edit(id,"Причал",null)&&loaded.edit(id,null,updated),"rename and update");
            loaded.save();loaded=AtlasWorkspace.load(directory,area);
            check(loaded.views().equals(List.of(new AtlasWorkspace.View(id,"Причал",updated))),"identity survives edits");
            check(loaded.delete(id)&&!loaded.delete(id),"delete is repeatable");
            for(int i=0;i<200;i++){loaded.visit("project/"+i);loaded.toggle("city/"+i);loaded.create("view"+i,camera);}
            check(loaded.views().size()==AtlasWorkspace.MAX_VIEWS&&loaded.resultKeys(false).size()==AtlasWorkspace.MAX_RECENT
                    &&loaded.resultKeys(true).size()==AtlasWorkspace.MAX_FAVORITES,"bounded library");
            try(var files=Files.list(directory)){Files.write(files.filter(Files::isRegularFile).findFirst().orElseThrow(),new byte[]{0,7,99});}
            check(AtlasWorkspace.load(directory,area).views().isEmpty(),"damaged local library recovers");
        } finally {
            try(var paths=Files.walk(directory)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
    private AtlasWorkspaceCheck() {}
}
