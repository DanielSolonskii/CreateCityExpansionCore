package com.example.examplemod.client.map;
import com.example.examplemod.client.NearbySceneClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import java.util.*;
/** Uses the terrain panel's live transform, including its smoothed centre. */
public final class FriendMinimapLayer {
    private FriendMinimapLayer(){}
    private static final int FACE=10,BORDER=0xFF78D7C1;
    private record Cluster(int x,int y,List<NearbySceneClient.Friend> friends){}
    public static void render(GuiGraphics g,CityMapPanel panel,MinimapLayout.View view){
        var mc=Minecraft.getInstance();if(mc.getConnection()==null||mc.player==null)return;long now=net.minecraft.Util.getMillis();var clusters=new ArrayList<Cluster>();
        for(var friend:NearbySceneClient.friends()){
            if(mc.getConnection().getPlayerInfo(friend.id())==null)continue;
            int x=panel.worldToScreenX(friend.x(now)),y=panel.worldToScreenY(friend.z(now));
            if(x<view.x()+8||y<view.y()+8||x>=view.x()+view.size()-8||y>=view.y()+view.size()-8)continue;
            Cluster group=null;for(var c:clusters)if(Math.hypot(c.x-x,c.y-y)<15){group=c;break;}
            if(group==null){group=new Cluster(x,y,new ArrayList<>());clusters.add(group);}group.friends.add(friend);
        }
        for(var group:clusters){int x=group.x,y=group.y;var info=mc.getConnection().getPlayerInfo(group.friends.getFirst().id());if(info==null)continue;
            g.fill(x-7,y-7,x+7,y+7,0xED132329);g.renderOutline(x-7,y-7,14,14,BORDER);PlayerFaceRenderer.draw(g,info.getSkin(),x-FACE/2,y-FACE/2,FACE);
            if(group.friends.size()>1){String n=""+group.friends.size();g.fill(x+1,y+2,x+2+mc.font.width(n),y+12,0xF0132329);g.drawString(mc.font,n,x+2,y+3,BORDER,false);}
        }
    }
}
