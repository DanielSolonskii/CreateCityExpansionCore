package com.example.examplemod.client.screen;

import com.example.examplemod.network.RailwayMapPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Headless checks do not claim to verify Minecraft font rendering or live screen input. */
public final class LogisticsLayoutCheck {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        for(int[] size:new int[][]{{320,180},{320,240},{427,240},{480,270},{640,360},{854,480},{960,540},{1280,720},{1920,1080}}){
            var l=LogisticsLayout.of(size[0],size[1]);
            require(l.left()>=0&&l.right()<=size[0],"horizontal bounds");
            require(l.top()<l.bottom()&&l.bottom()<l.footer()&&l.footer()+22<=size[1],"viewport / footer separation");
            for(int i=0;i<3;i++){var tab=l.tab(i);require(tab.x()>=l.left()&&tab.x()+tab.width()<=l.right()&&tab.y()+tab.height()<l.top(),"three tabs use real geometry");}
            var embedded=CityPylonWorkspaceLayout.readable(size[0],size[1]);require(embedded.scale()==1,"native font size without whole-page shrink");var e=LogisticsLayout.of(embedded.width(),embedded.height());require(e.viewport()>=22,"embedded content exposes a complete control");
            for(int i=0;i<3;i++){var button=e.footerButton(i,3);require(button.y()+button.height()<=embedded.height()&&button.width()>=50,"pinned actions remain reachable");}
            var r=FreightRouteLayout.of(embedded.width(),embedded.height());require(r.viewport().y()+r.viewport().height()<r.confirm().y(),"route body and confirmation separated");for(int i=0;i<3;i++){var row=r.row(i,r.clamp(999,3));require(row.contains(row.x()+1,row.y()+1),"route hit bounds match render bounds");}
            require(l.contains(l.left(),l.top())&&!l.contains(l.right(),l.bottom()),"exclusive hit bounds");
            for(int content:new int[]{0,60,142,400,800,4000}){
                require(l.clampScroll(-99,content)==0,"negative scrolling");
                int end=l.clampScroll(Integer.MAX_VALUE,content);
                require(end==Math.max(0,content-l.viewport()),"last row reachable");
                require(l.clampScroll(end,content)==end,"stable clamp");
            }
        }
        var map=new LogisticsBlueprint();var dimension=ResourceLocation.parse("minecraft:overworld");
        map.update(BlockPos.ZERO,dimension,List.of(),List.of());
        var prepare=LogisticsBlueprint.class.getDeclaredMethod("prepare",int.class,int.class);prepare.setAccessible(true);
        var spans=LogisticsBlueprint.class.getDeclaredField("spans");spans.setAccessible(true);
        map.receive(new RailwayMapPayload(BlockPos.ZERO,dimension,List.of(
                new RailwayMapPayload.TrackSegment(-500,0,500,0),
                new RailwayMapPayload.TrackSegment(0,-500,0,500),
                new RailwayMapPayload.TrackSegment(Float.NaN,0,4,4),
                new RailwayMapPayload.TrackSegment(10000,10000,11000,11000))));
        prepare.invoke(map,400,160);
        @SuppressWarnings("unchecked") List<int[]> pixels=(List<int[]>)spans.get(map);
        require(pixels.size()==160,"crossing rails clip to viewport; invalid and outside geometry ignored");
        require(pixels.stream().allMatch(p->p[0]>=0&&p[2]<=400&&p[1]>=0&&p[1]<160),"raster bounds");
        require(pixels.stream().anyMatch(p->p[0]==0&&p[2]==400),"horizontal rail reaches both edges");
        prepare.invoke(map,400,160);require(spans.get(map)==pixels,"unchanged geometry cached across frames");
        map.receive(new RailwayMapPayload(BlockPos.ZERO,dimension,List.of()));prepare.invoke(map,400,160);
        require(((List<?>)spans.get(map)).isEmpty(),"empty update clears old rails");
        System.out.println("PASS: "+checks+" logistics layout, clipping and blueprint cache checks");
    }
}
