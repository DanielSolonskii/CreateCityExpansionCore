package com.example.examplemod.client.map;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

final class MapLabelLayoutCheck {
    private static void require(boolean value,String message) { if (!value) throw new AssertionError(message); }

    static void run() throws Exception {
        var layout = new MapLabelLayout();
        var output = new HashMap<String,String>();
        for (int pass=0; pass<2; pass++) {
            layout.begin(50,20,160,50);
            layout.reserve(100,20,110,50);
            layout.offer("ordinary","ordinary",0,96,45,6,32,MapLabelLayout.Priority.NORMAL);
            layout.offer("selected","selected",0,96,45,6,32,MapLabelLayout.Priority.SELECTED);
            var drawn = new ArrayList<String>();
            layout.paint((text,color,x,y) -> {
                drawn.add(text);
                require(x<100 && x>=50 && y>=20 && y<70,"label overlaps panel or viewport");
                if(passCoords(output,text,x,y)) throw new AssertionError("unchanged placement moved");
            });
            require(drawn.getFirst().equals("selected"),"submission order beat selected marker");
        }
        // Once the obstacle disappears the previously successful alternative remains in use.
        layout.begin(50,20,160,50);
        layout.offer("selected","selected",0,96,45,6,32,MapLabelLayout.Priority.SELECTED);
        layout.paint((text,color,x,y)->require(!passCoords(output,text,x,y),"label jitter after space opened"));
        var groups=new MarkerGroups<String>();
        groups.add("active",-16,5,9,0);
        for (int i=0;i<2000;i++) groups.add("near"+i,-15,5,9,0);
        groups.add("other",90,90,9,0);
        var iterator=groups.visible().iterator();
        var group=iterator.next();
        require(group.x()==-16 && group.y()==5 && group.members().getFirst().equals("active") && group.members().size()==2001,"cluster moved or lost members");
        require(iterator.next().members().getFirst().equals("other") && !iterator.hasNext(),"separate marker vanished");

        var dir=Files.createTempDirectory("ccec-map-display");
        var file=dir.resolve("client.properties");
        try {
            var settings=new MinimapPreferences(file);
            require(settings.inertia && !settings.smoothTerrain,"defaults");
            settings.importReducedMotion(true);
            require(new MinimapPreferences(file).reducedMotion,"existing reduced-motion preference lost");
            settings.setReducedMotion(false);
            settings.inertia=false;settings.smoothTerrain=true;settings.save();
            var restored=new MinimapPreferences(file);
            restored.importReducedMotion(true);
            require(!restored.inertia && restored.smoothTerrain && !restored.reducedMotion,"explicit choices failed to round-trip");
        } finally { Files.deleteIfExists(file);Files.delete(dir); }
    }

    private static boolean passCoords(HashMap<String,String> output,String text,int x,int y) {
        String coordinates=x+","+y;
        var old=output.put(text,coordinates);
        return old!=null && !old.equals(coordinates);
    }
}
