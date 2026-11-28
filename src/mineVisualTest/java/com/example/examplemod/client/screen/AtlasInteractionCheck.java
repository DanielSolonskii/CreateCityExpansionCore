package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel.SelectionKind;
import net.minecraft.network.chat.Component;

final class AtlasInteractionCheck {
    private static void require(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    static void run() {
        var city=AtlasObjectCard.rows(SelectionKind.CITY,true,true,false,false,false);
        require(city.stream().anyMatch(row -> row.action() == AtlasObjectCard.Action.DIRECTIONS), "Pinned city offers station directions");
        checkStationDirections();
        require(city.getFirst().action()==AtlasObjectCard.Action.TRACK,"A public city can be a navigation target");
        var edit=city.stream().filter(a->a.action()==AtlasObjectCard.Action.EDIT_CITY).findFirst().orElseThrow();
        require(!edit.enabled() && !edit.unavailable().isBlank(),"Missing permission explains the disabled edit");
        require(city.stream().noneMatch(a->a.action()==AtlasObjectCard.Action.RELATIONS),"Public viewing does not open management");
        require(AtlasObjectCard.rows(SelectionKind.WAYPOINT,true,true,true,false,false).getFirst().action()==AtlasObjectCard.Action.STOP,"Same target can be cancelled");
        require(AtlasObjectCard.rows(SelectionKind.LOGISTICS,false,true,false,false,false).getFirst().enabled()==false,"A station without its linked point cannot open a random logistics row");

        var first=Component.literal("Search");
        var next=Component.literal("Layers");
        var focus=new AtlasFocus();
        var bounds=new WorldMapUiLayout.Rect(10,12,40,16);
        int[] actions={0};
        focus.add("search",first,first,bounds,()->actions[0]=1);
        focus.add("layers",next,next,bounds,()->actions[0]=2);
        focus.move(true).activate().run();
        require(actions[0]==2 && focus.move(false).name().equals(first),"Reverse traversal and forward wrap reach the painted actions");
        focus.begin();
        focus.add("close",next,next,bounds,()->actions[0]=3);
        require(focus.current()==null,"A hidden control cannot keep input on the next surface");
        focus.move(false).activate().run();
        require(actions[0]==3,"Modal owns Enter");
        var delay=new AtlasHoverDelay(350);
        require(delay.sample(first,1000)==null && delay.sample(first,1349)==null && first.equals(delay.sample(first,1350)),"Hover waits 350 ms");
        require(delay.sample(null,1500)==null && delay.sample(first,2000)==null,"Drag or pointer exit disarms hover");
        require(delay.sample(next,2349)==null && next.equals(delay.sample(next,2699)),"A new target gets its own delay");
        delay.keyboardInput(90,40);
        require(delay.samplePointer(next,5000,90,40)==null,"A parked pointer cannot cover keyboard search results");
        require(delay.samplePointer(next,5010,91,40)==null && next.equals(delay.samplePointer(next,5360,91,40)),
                "Pointer motion rearms the tooltip after the usual dwell");
        for(int width:new int[]{320,426,640}) for(int x:new int[]{4,width/2,width-5}) for(int y:new int[]{4,120,234}) {
            var tip=WorldMapUiLayout.tooltip(width,240,x,y,120,36);
            require(!tip.contains(x,y) && WorldMapUiLayout.withinScreen(width,240,tip),"Edge tooltip clears its hot spot");
        }
    }

    private static void checkStationDirections() {
        var dim = net.minecraft.world.level.Level.OVERWORLD.location();
        var nether = net.minecraft.world.level.Level.NETHER.location();
        var start = new net.minecraft.core.BlockPos(-29_990_000, 10, -100);
        var end = new net.minecraft.core.BlockPos(29_990_000, 60, 100);
        var departure = mapMarker("station", dim, start.offset(-3, 60, 4), 1);
        var arrival = mapMarker("station", dim, end.offset(0, -30, 2), 2);
        var wrongDimension = mapMarker("station", nether, end, 3);
        var freight = mapMarker("line", dim, end, 4);
        var rails = mapMarker("rail", dim, start, 5);
        var slots = java.util.List.of(freight, rails, wrongDimension, departure, arrival);
        var nearest = com.example.examplemod.client.logistics.StationDirections.nearest(slots, dim, start, end);
        require(nearest.origin().id().equals(departure.getUUID("Id")) && nearest.origin().distance() == 5
                        && nearest.destination().id().equals(arrival.getUUID("Id")) && nearest.destination().distance() == 2,
                "Only matching station landmarks enter directions, using horizontal distances at opposite world borders");
        var foreign = com.example.examplemod.client.logistics.StationDirections.nearest(slots, dim, null, end);
        require(foreign.origin() == null && foreign.destination().equals(nearest.destination()), "Another dimension has no local departure");
        var removed = com.example.examplemod.client.logistics.StationDirections.nearest(
                java.util.List.of(freight, rails, wrongDimension), dim, start, end);
        require(removed.origin() == null && removed.destination() == null, "Lost stations do not survive a changed directory; rail and freight cannot imply a transfer");
        var many = new java.util.ArrayList<>(java.util.Collections.nCopies(256, freight));
        many.add(arrival);
        require(com.example.examplemod.client.logistics.StationDirections.nearest(many, dim, start, end).destination() == null,
                "Scan observes the same hard station directory limit as the server");
    }

    private static net.minecraft.nbt.CompoundTag mapMarker(String kind, net.minecraft.resources.ResourceLocation dimension,
                                                          net.minecraft.core.BlockPos pos, int id) {
        var location = new net.minecraft.nbt.CompoundTag();
        location.putString("Dimension", dimension.toString());
        location.putLong("Pos", pos.asLong());
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putString("Kind", kind);
        marker.putUUID("Id", new java.util.UUID(0, id));
        marker.putString("Name", "Landmark " + id);
        marker.put("Location", location);
        return marker;
    }
}
