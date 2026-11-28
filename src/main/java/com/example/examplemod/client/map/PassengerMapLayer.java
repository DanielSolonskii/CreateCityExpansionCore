package com.example.examplemod.client.map;

import com.example.examplemod.client.map.CityMapPanel.Selection;
import com.example.examplemod.client.map.CityMapPanel.SelectionKind;
import com.example.examplemod.passenger.PassengerRailMap;
import com.example.examplemod.passenger.PassengerRailMap.LineInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The public train marker, hit area and card are all derived from the same server observation. */
public final class PassengerMapLayer {
    private PassengerMapLayer() {}
    private static List<PassengerRailMap.Segment> indexed=List.of();
    private static PassengerTrackIndex geometry=new PassengerTrackIndex(indexed);

    private static PassengerTrackIndex tracks(CityMapPanel map) {
        var segments=CityMapFeatures.rails(map.dimensionId()).segments();
        if (segments!=indexed) { indexed=segments; geometry=new PassengerTrackIndex(segments); }
        return geometry;
    }
    private static Component tr(String key,Object... args) {
        return Component.translatable("gui.createcityexpansioncore.transit_map."+key,args);
    }
    public static boolean isPassenger(SelectionKind kind) {
        return kind==SelectionKind.PASSENGER_LINE || kind==SelectionKind.PASSENGER_TRAIN;
    }
    private static LineInfo line(SelectionKind kind,UUID id,ResourceLocation dimension) {
        if(id==null || !isPassenger(kind))return null;
        for(var line:CityMapFeatures.rails(dimension).lines()) {
            if(id.equals(kind==SelectionKind.PASSENGER_TRAIN?line.trainId():line.id()))return line;
        }
        return null;
    }
    private static UUID emphasized(CityMapPanel map) {
        var selected=map.getSelection();
        if(selected==null || !isPassenger(selected.kind())) selected=map.getHoveredSelection();
        if(selected==null)return null;
        var active=line(selected.kind(),selected.id(),map.dimensionId());
        return active==null?null:active.id();
    }
    static void drawRails(GuiGraphics g,CityMapPanel m) {
        var index=tracks(m);
        double x1=m.screenToWorldX(m.viewportLeft()-4), x2=m.screenToWorldX(m.viewportRight()+4);
        double z1=m.screenToWorldZ(m.viewportTop()-4), z2=m.screenToWorldZ(m.viewportBottom()+4);
        UUID active=emphasized(m);
        // Draw the selected service last so coincident services never cover its course.
        for(int pass=0;pass<(active==null?1:2);pass++) {
            boolean highlight=pass==1;
            index.visit(Math.min(x1,x2),Math.min(z1,z2),Math.max(x1,x2),Math.max(z1,z2),s->{
                if(s.line().equals(active)!=highlight)return;
                var clipped=MapLineClip.clip(m.worldToScreenX(s.x()),m.worldToScreenY(s.z()),
                        m.worldToScreenX(s.toX()),m.worldToScreenY(s.toZ()),
                        m.viewportLeft()-4,m.viewportTop()-4,m.viewportRight()+4,m.viewportBottom()+4);
                if(clipped==null)return;
                if(highlight) CityCartographyLayer.stroke(g,clipped.x1(),clipped.y1(),clipped.x2(),clipped.y2(),6,0xfff5e6b9);
                CityCartographyLayer.stroke(g,clipped.x1(),clipped.y1(),clipped.x2(),clipped.y2(),3,0xff000000|s.color());
            });
        }
    }
    static void drawTrains(GuiGraphics g,CityMapPanel m) {
        int radius=AtlasPlaceIcon.radius(m.zoom());
        for(var line:CityMapFeatures.rails(m.dimensionId()).lines()) {
            var position=line.train();
            if(position==null)continue;
            int x=m.worldToScreenX(position.x()),railY=m.worldToScreenY(position.z());
            if(x+radius<m.viewportLeft() || x-radius>m.viewportRight() || railY+radius<m.viewportTop() || railY-radius>m.viewportBottom())continue;
            int y=badgeY(m,line,x,railY,radius);
            boolean selected=matchesTrain(m.getSelection(),line),hovered=matchesTrain(m.getHoveredSelection(),line);
            if(y!=railY) g.vLine(x,Math.min(y,railY),Math.max(y,railY),0xff000000|line.color());
            AtlasPlaceIcon.TRAIN.badge(g,x,y,radius,selected,hovered);
            m.labels().reserve(x-radius,y-radius,2*radius,2*radius);
            g.fill(x-radius+2,y+radius-2,x+radius-1,y+radius,0xff000000|line.color());
            if(selected || hovered) m.labels().offer("passenger/"+line.trainId(),line.trainName(),0xffefe7d3,x,y,radius+2,
                    Minecraft.getInstance().font.width(line.trainName()),MapLabelLayout.Priority.SELECTED);
        }
    }
    // At a platform, leave room to select both the station and its train. The stem marks the real rail position.
    private static int badgeY(CityMapPanel map,LineInfo line,int x,int y,int radius) {
        for(var stop:line.stops()) {
            var entrance=stop.entrance();
            if(Math.abs(map.worldToScreenX(entrance.getX()+.5)-x)>2*radius ||
                    Math.abs(map.worldToScreenY(entrance.getZ()+.5)-y)>2*radius)continue;
            int gap=2*radius+3;
            int stationY=map.worldToScreenY(entrance.getZ()+.5);
            int above=Math.min(stationY,y)-gap, below=Math.max(stationY,y)+gap;
            return above-radius>=map.viewportTop()?above:below+radius<=map.viewportBottom()?below:y;
        }
        return y;
    }
    private static boolean matchesTrain(Selection selection,LineInfo line) {
        return selection!=null && selection.kind()==SelectionKind.PASSENGER_TRAIN && selection.id().equals(line.trainId());
    }
    static Selection trainAt(CityMapPanel map,double mx,double my) {
        LineInfo best=null; double distance=Double.POSITIVE_INFINITY;
        int radius=AtlasPlaceIcon.radius(map.zoom());
        for(var info:CityMapFeatures.rails(map.dimensionId()).lines()) {
            var position=info.train();
            if(position==null)continue;
            int x=map.worldToScreenX(position.x()), y=map.worldToScreenY(position.z());
            if(x+radius<map.viewportLeft() || x-radius>map.viewportRight() || y+radius<map.viewportTop() || y-radius>map.viewportBottom())continue;
            y=badgeY(map,info,x,y,radius);
            double d=Math.hypot(mx-x,my-y);
            if(d<distance && AtlasPlaceIcon.contains(mx,my,x,y,radius)) { best=info; distance=d; }
        }
        return best==null?null:card(best,SelectionKind.PASSENGER_TRAIN,null,map.dimensionId());
    }
    static Selection lineAt(CityMapPanel map,double mx,double my,UUID previous) {
        double x=map.screenToWorldX(mx), z=map.screenToWorldZ(my);
        UUID id=tracks(map).pick(x,z,Math.abs(map.screenToWorldX(mx+1)-x),previous);
        var info=line(SelectionKind.PASSENGER_LINE,id,map.dimensionId());
        return info==null?null:card(info,SelectionKind.PASSENGER_LINE,BlockPos.containing(x,0,z),map.dimensionId());
    }
    public static Selection resolve(SelectionKind kind,UUID id,BlockPos last,ResourceLocation dim) {
        var info=line(kind,id,dim);
        return info==null?null:card(info,kind,last,dim);
    }
    public static Selection related(Selection selection,ResourceLocation dim) {
        var info=line(selection.kind(),selection.id(),dim);
        if(info==null)return null;
        if(selection.kind()==SelectionKind.PASSENGER_TRAIN) return card(info,SelectionKind.PASSENGER_LINE,selection.pos(),dim);
        return info.train()==null?null:card(info,SelectionKind.PASSENGER_TRAIN,null,dim);
    }
    private static Selection card(LineInfo info,SelectionKind kind,BlockPos anchor,ResourceLocation dim) {
        return card(info,kind,anchor,CityMapFeatures.rails(dim).pending());
    }
    static Selection card(LineInfo info,SelectionKind kind,BlockPos anchor,boolean loadingTrack) {
        boolean train=kind==SelectionKind.PASSENGER_TRAIN;
        if(train && info.train()!=null) {
            var p=info.train(); anchor=BlockPos.containing(p.x(),p.y(),p.z());
        }
        if(anchor==null && !train && !info.stops().isEmpty())anchor=info.stops().getFirst().entrance();
        if(anchor==null)return null;
        var details=new ArrayList<Component>();
        details.add(Component.literal("■ ").withColor(info.color()).append(
                (train?tr("line",info.name()):tr("train",info.trainName())).copy().withColor(0xe6e7df)));
        if(!info.description().isBlank())details.add(Component.literal(info.description()));
        details.add(Component.translatable("ccec.passenger."+info.status()));
        if(train && info.train()==null)details.add(tr("location_unavailable"));
        var stops=Component.empty();
        for(var stop:info.stops()) {
            if(!stops.getSiblings().isEmpty())stops.append(" → ");
            stops.append(Component.literal(stop.name()));
        }
        if(!info.stops().isEmpty())details.add(tr("stops",stops));
        if(info.unavailableLegs()>0)details.add(tr("missing_sections",info.unavailableLegs()));
        else if(loadingTrack)details.add(tr("loading_track"));
        return new Selection(kind,train?info.trainId():info.id(),Component.literal(train?info.trainName():info.name()),
                anchor,false,details,ItemStack.EMPTY,false,train?AtlasPlaceIcon.TRAIN:AtlasPlaceIcon.STATION);
    }
}
