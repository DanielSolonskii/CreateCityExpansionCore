package com.example.examplemod.client.screen;
import com.example.examplemod.client.map.CityMapPanel;

import com.example.examplemod.client.NearbySceneClient;
import com.example.examplemod.client.screen.*;
import com.example.examplemod.network.NearbyScenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Read-only social projection; uses the map's transform and shared marker bounds. */
public final class MapSocialLayer {
    private record Hit(WorldMapUiLayout.Rect bounds,List<UUID> friends,NearbyScenePayload.Site site){}
    private static final Map<CityMapPanel,List<Hit>> hits=new WeakHashMap<>();
    private MapSocialLayer(){}
    public static void render(GuiGraphics g,CityMapPanel map,int x,int y,int w,int h,int mx,int my){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return;
        var next=new ArrayList<Hit>();long now=net.minecraft.Util.getMillis();
        for(var site:NearbySceneClient.projects()){
            if(!site.friendProject())continue;
            int sx=map.worldToScreenX((site.zone().minX()+site.zone().maxX())/2d),sy=map.worldToScreenY((site.zone().minZ()+site.zone().maxZ())/2d);
            boolean markerVisible=sx>=x+10&&sx<=x+w-10&&sy>=y+10&&sy<=y+h-10;
            int color=ProjectMapStyle.color(site.scope(),site.status());
            int l=map.worldToScreenX(site.zone().minX()),r=map.worldToScreenX(site.zone().maxX()),t=map.worldToScreenY(site.zone().minZ()),b=map.worldToScreenY(site.zone().maxZ());
            if(site.zone().shape()==com.example.examplemod.projects.ProjectZone.Shape.RECTANGLE&&Math.abs(r-l)>20&&Math.abs(b-t)>20){
                if(r>=x&&l<=x+w&&b>=y&&t<=y+h&&r>l&&b>t)g.renderOutline(l,t,r-l,b-t,color);
            }
            if(site.zone().shape()==com.example.examplemod.projects.ProjectZone.Shape.LINE){int x1=map.worldToScreenX(site.zone().x1()),z1=map.worldToScreenY(site.zone().z1()),x2=map.worldToScreenX(site.zone().x2()),z2=map.worldToScreenY(site.zone().z2());int steps=Math.min(512,Math.max(Math.abs(x2-x1),Math.abs(z2-z1)));for(int i=0;i<=steps;i++){int px=(int)(x1+(x2-(double)x1)*i/Math.max(1,steps)),py=(int)(z1+(z2-(double)z1)*i/Math.max(1,steps));if(px>=x&&px<x+w&&py>=y&&py<y+h)g.fill(px,py,px+2,py+2,color);}}
            if(!markerVisible)continue;
            var bounds=ProjectMapStyle.marker(sx,sy);WorldMapChrome.rounded(g,bounds.x(),bounds.y(),bounds.width(),bounds.height(),3,WorldMapChrome.SURFACE);
            g.renderOutline(bounds.x(),bounds.y(),bounds.width(),bounds.height(),color);g.drawCenteredString(mc.font,Component.literal("+"),sx,sy-3,color);
            next.add(new Hit(bounds,null,site));
            if(bounds.contains(mx,my))g.drawString(mc.font,WorldMapChrome.fit(mc.font,Component.literal(site.title()),Math.max(20,x+w-sx-14)),sx+12,sy-3,color,false);
        }
        // Group overlapping heads at small scales without hiding the individual profiles on click.
        var groups=new ArrayList<List<NearbySceneClient.Friend>>();
        for(var friend:NearbySceneClient.friends()){
            int sx=map.worldToScreenX(friend.x(now)),sy=map.worldToScreenY(friend.z(now));
            if(sx<x+10||sx>x+w-10||sy<y+10||sy>y+h-10)continue;
            List<NearbySceneClient.Friend> group=null;
            for(var candidate:groups){var first=candidate.getFirst();if(Math.hypot(sx-map.worldToScreenX(first.x(now)),sy-map.worldToScreenY(first.z(now)))<18){group=candidate;break;}}
            if(group==null){group=new ArrayList<>();groups.add(group);}group.add(friend);
        }
        for(var group:groups){var f=group.getFirst();var info=mc.getConnection().getPlayerInfo(f.id());if(info==null)continue;
            int sx=map.worldToScreenX(f.x(now)),sy=map.worldToScreenY(f.z(now));var rect=ProjectMapStyle.marker(sx,sy);
            WorldMapChrome.rounded(g,rect.x(),rect.y(),18,18,3,0xF0132329);g.renderOutline(rect.x(),rect.y(),18,18,0xFF78D7C1);
            PlayerFaceRenderer.draw(g,info.getSkin(),sx-6,sy-6,12);
            if(group.size()>1)g.drawString(mc.font,"+"+(group.size()-1),sx+7,sy+5,0xFF78D7C1,true);
            next.add(new Hit(rect,group.stream().map(NearbySceneClient.Friend::id).toList(),null));
            if(rect.contains(mx,my))g.drawString(mc.font,info.getProfile().getName(),sx+13,sy-3,0xFF78D7C1,true);
        }
        hits.put(map,next);
    }
    public static void clear(CityMapPanel map){hits.remove(map);}
    public static boolean click(CityMapPanel map,double x,double y){
        var list=hits.getOrDefault(map,List.of());
        for(int i=list.size()-1;i>=0;i--){var hit=list.get(i);if(!hit.bounds.contains(x,y))continue;var mc=Minecraft.getInstance();
            if(hit.friends!=null){var valid=hit.friends.stream().filter(id->NearbySceneClient.friends().stream().anyMatch(f->f.id().equals(id))).toList();if(valid.size()==1)PlayerProfileScreen.open(mc.screen,valid.getFirst());else if(!valid.isEmpty())mc.setScreen(new FriendClusterScreen(mc.screen,valid));}
            else if(hit.site!=null&&NearbySceneClient.projects().stream().anyMatch(p->p.id().equals(hit.site.id())))mc.setScreen(new ProjectPublicCardScreen(mc.screen,hit.site));
            return true;
        }return false;
    }
}
