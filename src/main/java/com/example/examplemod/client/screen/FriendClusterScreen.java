package com.example.examplemod.client.screen;
import com.example.examplemod.client.NearbySceneClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
/** Selecting a stacked marker keeps every friend reachable. */
public final class FriendClusterScreen extends Screen {
    private final Screen parent;private final List<UUID> ids;private int scroll,left,top,w,visible;
    public FriendClusterScreen(Screen parent,List<UUID> ids){super(Component.translatable("gui.createcityexpansioncore.trade.friends_here"));this.parent=parent;this.ids=List.copyOf(ids);}
    @Override protected void init(){clearWidgets();w=Math.min(280,width-20);visible=Math.max(1,Math.min(7,(height-88)/27));left=(width-w)/2;top=(height-(visible*27+72))/2;scroll=Math.clamp(scroll,0,Math.max(0,ids.size()-visible));
        for(int i=0;i<visible&&scroll+i<ids.size();i++){UUID id=ids.get(scroll+i);var info=minecraft.getConnection().getPlayerInfo(id);int row=i;addRenderableWidget(Button.builder(Component.literal(info==null?"—":info.getProfile().getName()),b->{if(NearbySceneClient.friends().stream().anyMatch(f->f.id().equals(id)))PlayerProfileScreen.open(this,id);}).bounds(left+34,top+32+row*27,w-46,22).build());}
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(left+12,top+visible*27+39,w-24,22).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float dt){renderBackground(g,x,y,dt);panel(g,left,top,w,visible*27+72);g.drawString(font,title,left+12,top+12,ACCENT,false);for(int i=0;i<visible&&scroll+i<ids.size();i++){var info=minecraft.getConnection().getPlayerInfo(ids.get(scroll+i));if(info!=null)PlayerFaceRenderer.draw(g,info.getSkin(),left+12,top+35+i*27,16);}super.render(g,x,y,dt);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.clamp(scroll+(dy<0?1:-1),0,Math.max(0,ids.size()-visible));init();return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
