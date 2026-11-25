package com.example.examplemod.client.journey;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.MapLabelLayout;
import com.example.examplemod.client.map.MarkerGroups;
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
        Marker current = JourneyClient.displayTarget();
        boolean atlasObjective = !testing && editor == null && JourneyClient.hasAtlasGoal();
        String target=!testing && editor!=null?editor.selectedMarker():!atlasObjective && current!=null?current.id:"";
        var destination = atlasObjective ? JourneyClient.atlasMapDestination() : null;
        if (current != null && destination != null && destination.dimension().equals(dim.toString())) {
            var steps=current.route;
            for(int i=1;i<steps.size();i++)line(g,map,steps.get(i-1),steps.get(i),0xd878d7c1);
            if (JourneyClient.directAtlasBearing() && mc.player != null) {
                var feet = mc.player.blockPosition();
                line(g,map,new Point(dim.toString(),feet.getX(),feet.getY(),feet.getZ()),destination,BRASS);
            }
            double sx=map.worldToScreenXExact(destination.x()+.5), sy=map.worldToScreenZExact(destination.z()+.5);
            boolean inside=sx>=x && sx<x+w && sy>=y && sy<y+h;
            if (inside || hud && w>18 && h>18) {
                if (!inside) {
                    double cx=x+w/2.0, cy=y+h/2.0, dx=sx-cx, dy=sy-cy;
                    double scale=Math.max(Math.abs(dx)/(w/2.0-8),Math.abs(dy)/(h/2.0-8));
                    sx=cx+dx/scale;
                    sy=cy+dy/scale;
                }
                int px=(int)Math.round(sx), py=(int)Math.round(sy);
                int radius = hud ? 6 : 12;
                JourneyAtlasStyle.line(g,px-radius,py,px,py-radius,BRASS);
                JourneyAtlasStyle.line(g,px,py-radius,px+radius,py,BRASS);
                JourneyAtlasStyle.line(g,px+radius,py,px,py+radius,BRASS);
                JourneyAtlasStyle.line(g,px,py+radius,px-radius,py,BRASS);
                if (!hud) map.labels().offer("atlas/navigation",current.title,BRASS,px,py,14,
                        mc.font.width(current.title),MapLabelLayout.Priority.TARGET);
            }
        }
        boolean reduced=map.reducedMotion()||JourneyClient.reduced();long now=net.minecraft.Util.getMillis();
        var selected=markers.stream().filter(m->m.id.equals(target)).findFirst().orElse(null);
        if(selected!=null&&selected.point.dimension().equals(dim.toString())&&!selected.route.isEmpty()){
            var path=editor==null?JourneyClient.displayPath(selected):JourneyClient.path(selected);
            for(int i=1;i<path.size();i++)line(g,map,path.get(i-1),path.get(i),0xb0d0ad73);
            for(int i=0;i<path.size()-1;i++){var p=path.get(i);int px=map.worldToScreenX(p.x()+.5),py=map.worldToScreenY(p.z()+.5);if(px>x+8&&px<x+w-8&&py>y+8&&py<y+h-8){diamond(g,px,py,3,BRASS);}}
        }
        if(editor!=null){var steps=editor.expedition();for(int i=0;i<steps.size();i++){var m=steps.get(i);if(!m.point.dimension().equals(dim.toString()))continue;if(i>0)line(g,map,steps.get(i-1).point,m.point,0x55d0ad73);int px=map.worldToScreenX(m.point.x()+.5),py=map.worldToScreenY(m.point.z()+.5);String label=Integer.toString(i+1); map.labels().offer("step/"+m.id,label,BRASS,px,py,16,mc.font.width(label),MapLabelLayout.Priority.TARGET);}}
        var ordered=new ArrayList<>(markers);
        ordered.sort(Comparator.<Marker,Boolean>comparing(m->!m.id.equals(target)).thenComparing(m->m.id));
        var groups=new MarkerGroups<Marker>();
        for (var marker : ordered) {
            if ((!marker.onMap && (editor==null || editor.previewing())) || !marker.point.dimension().equals(dim.toString())) continue;
            int sx=map.worldToScreenX(marker.point.x()+.5), sy=map.worldToScreenY(marker.point.z()+.5);
            if (sx>=x+18 && sx<=x+w-18 && sy>=y+18 && sy<=y+h-18) groups.add(marker,sx,sy,marker.size,5);
        }
        var next=new ArrayList<Hit>();
        var markerById=new HashMap<String,Marker>();
        var byMarker=new HashMap<String,Hit>();
        var completed=new HashSet<String>();
        var unfinished=new HashSet<String>();
        var questById=new HashMap<String,net.minecraft.nbt.CompoundTag>();
        for (var quest : JourneyClient.quests) {
            questById.put(quest.getString("Id"),quest);
            (quest.getBoolean("Done") ? completed : unfinished).add(quest.getString("Marker"));
        }
        Hit hover=null;
        for (var group : groups.visible()) {
            var m=group.members().getFirst();
            boolean active=m.id.equals(target);
            var hit=new Hit(group.x(),group.y(),m.size+(active?2:0),m.id,group.members().size());
            next.add(hit); markerById.put(m.id,m); byMarker.put(m.id,hit);
            boolean over=!hud && Math.hypot(mx-hit.x,my-hit.y)<=hit.radius;
            boolean done=!testing && editor==null && !active && completed.contains(m.id) && !unfinished.contains(m.id);
            mark(g,hit.x,hit.y,m,active,over,now,reduced,done);
            map.labels().reserve(hit.x-hit.radius,hit.y-hit.radius,2*hit.radius+1,2*hit.radius+1);
            if (hit.count>1) {
                String count=Integer.toString(hit.count);
                map.labels().offer("journey/count/"+m.id,count,BRASS,hit.x,hit.y,hit.radius+3,mc.font.width(count),
                        active?MapLabelLayout.Priority.TARGET:MapLabelLayout.Priority.NODE);
            }
            if(over)hover=hit;
        }
        // Numbered expedition steps use the same markers; completed steps stay quiet.
        if(!testing&&!atlasObjective&&!JourneyClient.serverGuidanceHidden()&&editor==null&&!JourneyClient.chain.isEmpty())for(var chain:JourneyClient.chains)if(chain.getString("Id").equals(JourneyClient.chain)){
            var steps=JourneyCodec.strings(chain,"Steps");for(int i=0;i<steps.size();i++){String id=steps.get(i);var q=questById.get(id);if(q==null)continue;var hit=byMarker.get(q.getString("Marker"));if(hit!=null){String step=Integer.toString(i+1);map.labels().offer("journey/quest/"+id,step,q.getBoolean("Done")?MUTED:BRASS,hit.x,hit.y,hit.radius+3,mc.font.width(step),MapLabelLayout.Priority.TARGET);}}
        }
        var done=JourneyClient.displayCompletion();long elapsed=now-JourneyClient.displayCompletionAt();
        if(editor==null&&done!=null&&elapsed<950&&done.point.dimension().equals(dim.toString())){int sx=map.worldToScreenX(done.point.x()+.5),sy=map.worldToScreenY(done.point.z()+.5);mark(g,sx,sy,done,false,false,now,true,true);}
        if(hover!=null){var m=markerById.get(hover.id);int cw=Math.min(206,w-12),cx=Math.clamp(hover.x+18,x+6,x+w-cw-6),cy=Math.clamp(hover.y-48,y+6,y+h-55);map.labels().reserve(cx,cy,cw,49);panel(g,cx,cy,cw,49);g.drawString(mc.font,mc.font.plainSubstrByWidth(m.title,cw-16),cx+8,cy+7,TEXT,false);g.drawString(mc.font,mc.font.plainSubstrByWidth(m.description.isBlank()?JourneyClient.tr("kind."+m.kind.name()).getString():m.description,cw-16),cx+8,cy+21,MUTED,false);g.drawString(mc.font,JourneyClient.tr(hover.count>1?"cluster_hint":"marker_hint"),cx+8,cy+35,BRASS,false);}
        hits.put(map,hud||testing?List.of():next);
        if(!testing&&!hud&&editor==null&&mc.screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen&&w>=200&&h>=140){int bx=x+w-90,by=y+8;map.labels().reserve(bx,by,82,20);panel(g,bx,by,82,20);g.drawCenteredString(mc.font,JourneyClient.tr("markers"),bx+41,by+6,BRASS);controls.put(map,new int[]{bx,by});}else controls.remove(map);
    }
    public static void line(GuiGraphics g,CityMapPanel map,Point a,Point b,int color){
        if(!a.dimension().equals(b.dimension()))return;
        double ax=map.worldToScreenXExact(a.x()+.5),ay=map.worldToScreenZExact(a.z()+.5),bx=map.worldToScreenXExact(b.x()+.5),by=map.worldToScreenZExact(b.z()+.5),dx=bx-ax,dy=by-ay;
        double lo=0,hi=1;double[] p={-dx,dx,-dy,dy},q={ax-map.viewportLeft(),map.viewportRight()-ax,ay-map.viewportTop(),map.viewportBottom()-ay};
        for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)return;}else{double t=q[i]/p[i];if(p[i]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);}}if(lo>hi)return;
        JourneyAtlasStyle.line(g,(int)(ax+dx*lo),(int)(ay+dy*lo),(int)(ax+dx*hi),(int)(ay+dy*hi),color);
    }
    public static void circle(GuiGraphics g,int x,int y,double r,int color){for(int i=0;i<64;i++){double a=i*Math.PI/32;int px=x+(int)(Math.cos(a)*r),py=y+(int)(Math.sin(a)*r);g.fill(px,py,px+1,py+1,color);}}
    public static boolean click(CityMapPanel map,double x,double y){var mc=Minecraft.getInstance();var control=controls.get(map);if(control!=null&&x>=control[0]&&x<control[0]+82&&y>=control[1]&&y<control[1]+20){if(mc.screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas)atlas.openJourneyWorkspace("");return true;}
        for(var hit:hits.getOrDefault(map,List.of()))if(Math.hypot(hit.x-x,hit.y-y)<=hit.radius){
            if(mc.screen instanceof JourneyAtlasScreen editor){editor.selectMarker(hit.id);return true;}
            if(hit.count>1){map.restoreView(map.screenToWorldX(hit.x),map.screenToWorldZ(hit.y),map.zoom()*1.8);}
            if(mc.screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen atlas)atlas.openJourneyWorkspace(hit.id);else mc.setScreen(new JourneyDetailScreen(mc.screen,hit.id,false));return true;
        }return false;
    }
    private JourneyMapLayer(){}
}
