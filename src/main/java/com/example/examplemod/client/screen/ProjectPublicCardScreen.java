package com.example.examplemod.client.screen;
import com.example.examplemod.network.NearbyScenePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.example.examplemod.client.NearbySceneClient;
import java.util.Locale;
import static com.example.examplemod.client.screen.WorldMapChrome.*;

/** Public passport only: never fetches private project room data. */
public final class ProjectPublicCardScreen extends Screen {
    private final Screen parent;private final NearbyScenePayload.Site original;private int left,top,w;private long opened;private float scale=1;
    public ProjectPublicCardScreen(Screen parent,NearbyScenePayload.Site site){super(Component.literal(site.title()));this.parent=parent;original=site;}
    private NearbyScenePayload.Site current(){return NearbySceneClient.projects().stream().filter(s->s.id().equals(original.id())).findFirst().orElse(null);}
    @Override protected void init(){scale=Math.min(1,Math.min(width/400f,height/212f));w=380;left=((int)(width/scale)-w)/2;top=Math.max(8,((int)(height/scale)-192)/2);opened=net.minecraft.Util.getMillis();clearWidgets();
        if(original.author()!=null)addRenderableWidget(Button.builder(Component.literal(original.authorName()),b->{if(current()!=null)PlayerProfileScreen.open(this,current().author());}).bounds(left+16,top+100,w-32,20).build());
        addRenderableWidget(Button.builder(Component.literal(original.executorName().isBlank()?"—":original.executorName()),b->{if(current()!=null&&current().executor()!=null)PlayerProfileScreen.open(this,current().executor());}).bounds(left+16,top+135,w-32,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(left+16,top+164,w-32,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g,mx,my,dt);g.pose().pushPose();g.pose().scale(scale,scale,1);mx=(int)(mx/scale);my=(int)(my/scale);rounded(g,left,top,w,192,5,EDGE);rounded(g,left+1,top+1,w-2,190,5,SURFACE);
        var site=current();if(site==null){g.drawString(font,Component.translatable("gui.createcityexpansioncore.trade.unavailable"),left+16,top+24,MUTED,false);for(var child:renderables)if(child instanceof Button b)b.active=b.getY()==top+164;super.render(g,mx,my,dt);g.pose().popPose();return;}
        for(var child:renderables)if(child instanceof Button button){button.active=true;if(button.getY()==top+100)button.setMessage(Component.literal(site.authorName()));if(button.getY()==top+135){button.active=site.executor()!=null;button.setMessage(Component.literal(site.executorName().isBlank()?"—":site.executorName()));}}
        g.fill(left+1,top+1,left+1+(int)((w-2)*com.example.examplemod.client.CommunityFx.ease((net.minecraft.Util.getMillis()-opened)/200d)),top+2,ACCENT);
        g.fill(left+1,top+12,left+3,top+54,ProjectMapStyle.color(site.scope(),site.status()));g.drawString(font,fit(font,title,w-32),left+16,top+16,TEXT,false);
        g.drawString(font,Component.translatable("gui.createcityexpansioncore.projects.status."+site.status().name().toLowerCase(Locale.ROOT)),left+16,top+36,ACCENT,false);
        g.drawString(font,Component.translatable("gui.createcityexpansioncore.trade.stages",site.completed(),site.stages()),left+16,top+59,MUTED,false);
        g.drawString(font,Component.translatable("gui.createcityexpansioncore.trade.author"),left+16,top+87,MUTED,false);
        if(site.executor()!=null)g.drawString(font,Component.translatable("gui.createcityexpansioncore.trade.executor"),left+16,top+123,MUTED,false);
        super.render(g,mx,my,dt);
        drawHead(g,site.author(),left+19,top+102);if(site.executor()!=null)drawHead(g,site.executor(),left+19,top+137);g.pose().popPose();
    }
    private void drawHead(GuiGraphics g,java.util.UUID id,int x,int y){if(id==null)return;var info=minecraft.getConnection().getPlayerInfo(id);net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,info==null?net.minecraft.client.resources.DefaultPlayerSkin.get(id):info.getSkin(),x,y,16);}
    @Override public boolean mouseClicked(double x,double y,int b){return super.mouseClicked(x/scale,y/scale,b);}
    @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(x/scale,y/scale,b);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
