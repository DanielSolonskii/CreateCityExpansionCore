package com.example.examplemod.client.journey;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.journey.JourneyCodec;
import com.example.examplemod.journey.JourneyModel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.*;

/** Shared atlas/minimap overlay. The existing map owns projection and clipping. */
public final class JourneyMapLayer {
    private record Hit(int x,int y,int radius,String id,int count){}
    private static final Map<CityMapPanel,List<Hit>> hits=new WeakHashMap<>();
    private static final Map<CityMapPanel,int[]> controls=new WeakHashMap<>();
    public static void clear(CityMapPanel map){hits.remove(map);controls.remove(map);}
    public static void render(GuiGraphics g,CityMapPanel map,ResourceLocation dim,int x,int y,int w,int h,int mx,int my,boolean hud){
        var mc=Minecraft.getInstance();
        if (mc.screen instanceof JourneyRouteEditorScreen route && route.uses(map)) {
            clear(map);
            return;
        }
        var editor=mc.screen instanceof JourneyAtlasScreen s&&s.map==map?s:null;
        boolean testing = JourneyPlaytest.running();
        Marker trialMarker = JourneyPlaytest.marker();
        var markers=testing?(trialMarker==null?List.<Marker>of():List.of(trialMarker)):
                editor==null?JourneyClient.markers.values():editor.displayMarkers();
        String target=testing?(trialMarker==null?"":trialMarker.id):editor==null?JourneyClient.target:editor.selectedMarker();
        boolean reduced=map.reducedMotion()||JourneyClient.reduced();long now=net.minecraft.Util.getMillis();
        var selected=markers.stream().filter(m->m.id.equals(target)).findFirst().orElse(null);
        if(selected!=null&&selected.point.dimension().equals(dim.toString())&&!selected.route.isEmpty()){
            var path=editor==null?JourneyClient.displayPath(selected):JourneyClient.path(selected);
            for(int i=1;i<path.size();i++)line(g,map,path.get(i-1),path.get(i),0xb0d0ad73);
            for(int i=0;i<path.size()-1;i++){var p=path.get(i);int px=map.worldToScreenX(p.x()+.5),py=map.worldToScreenY(p.z()+.5);if(px>x+8&&px<x+w-8&&py>y+8&&py<y+h-8){diamond(g,px,py,3,BRASS);}}
        }
        if(editor!=null){var steps=editor.expedition();for(int i=0;i<steps.size();i++){var m=steps.get(i);if(!m.point.dimension().equals(dim.toString()))continue;if(i>0)line(g,map,steps.get(i-1).point,m.point,0x55d0ad73);int px=map.worldToScreenX(m.point.x()+.5),py=map.worldToScreenY(m.point.z()+.5);g.drawString(mc.font,""+(i+1),px-17,py-17,BRASS,false);}}
        var ordered=new ArrayList<>(markers);ordered.sort(Comparator.comparing(m->!m.id.equals(target)));
        var next=new ArrayList<Hit>();var markerById=new HashMap<String,Marker>();
        for(var m:ordered){if((!m.onMap&&(editor==null||editor.previewing()))||!m.point.dimension().equals(dim.toString()))continue;int sx=map.worldToScreenX(m.point.x()+.5),sy=map.worldToScreenY(m.point.z()+.5);if(sx<x+18||sx>x+w-18||sy<y+18||sy>y+h-18)continue;
            int overlap=-1;for(int i=0;i<next.size();i++)if(Math.hypot(next.get(i).x-sx,next.get(i).y-sy)<m.size+next.get(i).radius+5){overlap=i;break;}
            if(overlap>=0){var old=next.get(overlap);next.set(overlap,new Hit(old.x,old.y,old.radius,old.id,old.count+1));continue;}
            next.add(new Hit(sx,sy,m.size,m.id,1));markerById.put(m.id,m);
        }
        Hit hover=null;
        for(var hit:next){var m=markerById.get(hit.id);boolean active=m.id.equals(target),over=!hud&&Math.hypot(mx-hit.x,my-hit.y)<hit.radius+4;
            int lift=active&&editor==null?(int)((1-reveal(now-JourneyClient.displayTargetAt(),reduced))*5):0;
            boolean done=!testing&&editor==null&&!active&&JourneyClient.quests.stream().anyMatch(q->q.getString("Marker").equals(m.id)&&q.getBoolean("Done"))
                    &&JourneyClient.quests.stream().noneMatch(q->q.getString("Marker").equals(m.id)&&!q.getBoolean("Done"));
            mark(g,hit.x,hit.y-lift,m,active,over,now,reduced,done);
            if(hit.count>1){g.fill(hit.x+7,hit.y+5,hit.x+23,hit.y+16,SURFACE);g.drawString(mc.font,""+hit.count,hit.x+10,hit.y+7,BRASS,false);}
            if(over)hover=hit;
        }
        // Numbered expedition steps use the same markers; completed steps stay quiet.
        if(!testing&&editor==null&&!JourneyClient.chain.isEmpty())for(var chain:JourneyClient.chains)if(chain.getString("Id").equals(JourneyClient.chain)){
            var steps=JourneyCodec.strings(chain,"Steps");for(int i=0;i<steps.size();i++){String id=steps.get(i);var q=JourneyClient.quests.stream().filter(v->v.getString("Id").equals(id)).findFirst().orElse(null);if(q==null)continue;var hit=next.stream().filter(v->v.id.equals(q.getString("Marker"))).findFirst().orElse(null);if(hit!=null)g.drawString(mc.font,""+(i+1),hit.x-15,hit.y-15,q.getBoolean("Done")?MUTED:BRASS,false);}
        }
        var done=JourneyClient.displayCompletion();long elapsed=now-JourneyClient.displayCompletionAt();
        if(editor==null&&done!=null&&elapsed<950&&done.point.dimension().equals(dim.toString())){int sx=map.worldToScreenX(done.point.x()+.5),sy=map.worldToScreenY(done.point.z()+.5);mark(g,sx,sy,done,false,false,now,true,true);}
        if(hover!=null){var m=markerById.get(hover.id);int cw=Math.min(206,w-12),cx=Math.clamp(hover.x+18,x+6,x+w-cw-6),cy=Math.clamp(hover.y-48,y+6,y+h-55);panel(g,cx,cy,cw,49);g.drawString(mc.font,mc.font.plainSubstrByWidth(m.title,cw-16),cx+8,cy+7,TEXT,false);g.drawString(mc.font,mc.font.plainSubstrByWidth(m.description.isBlank()?JourneyClient.tr("kind."+m.kind.name()).getString():m.description,cw-16),cx+8,cy+21,MUTED,false);g.drawString(mc.font,JourneyClient.tr(hover.count>1?"cluster_hint":"marker_hint"),cx+8,cy+35,BRASS,false);}
        hits.put(map,hud||testing?List.of():next);
        if(!testing&&!hud&&editor==null&&mc.screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen&&w>=200&&h>=140){int bx=x+w-90,by=y+8;panel(g,bx,by,82,20);g.drawCenteredString(mc.font,JourneyClient.tr("markers"),bx+41,by+6,BRASS);controls.put(map,new int[]{bx,by});}else controls.remove(map);
    }
    public static void line(GuiGraphics g,CityMapPanel map,Point a,Point b,int color){
        if(!a.dimension().equals(b.dimension()))return;
        double ax=map.worldToScreenX(a.x()+.5),ay=map.worldToScreenY(a.z()+.5),bx=map.worldToScreenX(b.x()+.5),by=map.worldToScreenY(b.z()+.5),dx=bx-ax,dy=by-ay;
        double lo=0,hi=1;double[] p={-dx,dx,-dy,dy},q={ax-map.viewportLeft(),map.viewportRight()-ax,ay-map.viewportTop(),map.viewportBottom()-ay};
        for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)return;}else{double t=q[i]/p[i];if(p[i]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);}}if(lo>hi)return;
        JourneyAtlasStyle.line(g,(int)(ax+dx*lo),(int)(ay+dy*lo),(int)(ax+dx*hi),(int)(ay+dy*hi),color);
    }
    public static void circle(GuiGraphics g,int x,int y,double r,int color){for(int i=0;i<64;i++){double a=i*Math.PI/32;int px=x+(int)(Math.cos(a)*r),py=y+(int)(Math.sin(a)*r);g.fill(px,py,px+1,py+1,color);}}
    public static boolean click(CityMapPanel map,double x,double y){var mc=Minecraft.getInstance();var control=controls.get(map);if(control!=null&&x>=control[0]&&x<control[0]+82&&y>=control[1]&&y<control[1]+20){if(mc.screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas)atlas.openJourneyWorkspace("");return true;}
        for(var hit:hits.getOrDefault(map,List.of()))if(Math.hypot(hit.x-x,hit.y-y)<hit.radius+5){
            if(mc.screen instanceof JourneyAtlasScreen editor){editor.selectMarker(hit.id);return true;}
            if(hit.count>1){map.restoreView(map.screenToWorldX(hit.x),map.screenToWorldZ(hit.y),map.zoom()*1.8);}
            if(mc.screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas)atlas.openJourneyWorkspace(hit.id);else mc.setScreen(new JourneyDetailScreen(mc.screen,hit.id,false));return true;
        }return false;
    }
    private JourneyMapLayer(){}
}
