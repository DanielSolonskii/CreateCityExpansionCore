package com.example.examplemod.client.screen;
import com.example.examplemod.client.CommunityFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.example.examplemod.client.screen.WorldMapChrome.*;
/** Shared visual rhythm and one geometry for scrolling/clicks. No gameplay or networking policy. */
abstract class CommunityPage extends Screen {
    protected CommunityV2Layout layout;protected int scroll,content;protected double mx,my;protected long changed;protected final Screen parent;
    private record Hit(SocialHubLayout.Rect rect,Runnable action,boolean body){}
    private final List<Hit> hits=new ArrayList<>();private boolean clipped;private Component tooltip;
    protected int reservedBottom(){return 0;}
    protected SocialHubLayout.Rect body(){var r=layout.body();return new SocialHubLayout.Rect(r.x(),r.y(),r.width(),Math.max(1,r.height()-reservedBottom()));}
    protected void clearHits(){hits.clear();tooltip=null;}
    protected CommunityPage(Screen parent,String title){super(tr(title));this.parent=parent;}
    public static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.community2."+key,args);}
    @Override protected void init(){layout=CommunityV2Layout.calculate(width,height,SocialHubScreen.geometry(this));if(changed==0)changed=net.minecraft.Util.getMillis();CommunityFx.load();}
    protected void transition(){scroll=0;changed=net.minecraft.Util.getMillis();CommunityFx.sound("click");}
    private float tabX=12;private long tabFrame;
    protected void tabButton(GuiGraphics g,int index,int count,Component label,SocialHubScreen.Section icon,Runnable action,boolean enabled,boolean active){var r=layout.tab(index,count);button(g,r,Component.empty(),action,enabled,active);SocialHubIcons.navigation(g,r,icon,active||r.contains(mx,my));text(g,label,r.x()+32,r.y()+9,r.width()-39,active?ACCENT:TEXT);if(active){long now=net.minecraft.Util.getMillis();float step=CommunityFx.reduced?1:Math.min(1,(now-(tabFrame==0?now-16:tabFrame))/65f);tabFrame=now;tabX+=(r.x()-tabX)*step;g.fill((int)tabX+8,r.bottom()-2,(int)tabX+r.width()-8,r.bottom(),ACCENT);}}
    protected void text(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,fit(font,text,Math.max(1,width)),x,y,color,false);}
    protected void button(GuiGraphics g,SocialHubLayout.Rect r,Component label,Runnable action,boolean enabled,boolean selected){control(g,new WorldMapUiLayout.Rect(r.x(),r.y(),r.width(),r.height()),selected,enabled&&r.contains(mx,my),enabled,false);text(g,label,r.x()+8,r.y()+8,r.width()-16,enabled?(selected?ACCENT:TEXT):MUTED);if(enabled&&action!=null)hits.add(new Hit(r,action,clipped));}
    protected void hint(SocialHubLayout.Rect r,Component text){if(r.contains(mx,my)&&(!clipped||body().contains(mx,my)))tooltip=text;}
    protected SocialHubLayout.Rect row(int y,int height){var b=body();content=Math.max(content,y+height);return new SocialHubLayout.Rect(b.x()+4,b.y()+y-scroll,b.width()-8,height);}
    protected void card(GuiGraphics g,SocialHubLayout.Rect r,int accent){rounded(g,r.x(),r.y(),r.width(),r.height(),5,r.contains(mx,my)?RAISED:INSET);g.fill(r.x()+1,r.y()+10,r.x()+3,r.bottom()-10,accent);}
    protected void clip(GuiGraphics g){g.flush();var s=layout.screen(body());g.enableScissor(s.x(),s.y(),s.right(),s.bottom());clipped=true;content=0;}
    protected void unclip(GuiGraphics g){g.flush();g.disableScissor();clipped=false;scroll=Math.clamp(scroll,0,Math.max(0,content-body().height()));}
    protected abstract void draw(GuiGraphics g,float partial);
    @Override public final void render(GuiGraphics g,int x,int y,float partial){mx=layout.localX(x);my=layout.localY(y);hits.clear();tooltip=null;if(!SocialHubScreen.embedded(this))g.fill(0,0,width,height,0x99090E10);g.pose().pushPose();g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);panel(g,0,0,CommunityV2Layout.WIDTH,layout.height());draw(g,partial);float fade=1-CommunityFx.ease((net.minecraft.Util.getMillis()-changed)/160d);if(fade>0){var r=body();g.fill(r.x(),r.y(),r.right(),r.bottom(),((int)(85*fade)<<24)|(SURFACE&0xffffff));}g.flush();g.pose().popPose();if(tooltip!=null)g.renderTooltip(font,font.split(tooltip,240),x,y);}
    @Override public boolean mouseClicked(double x,double y,int b){if(b!=0)return false;double lx=layout.localX(x),ly=layout.localY(y);for(int i=hits.size()-1;i>=0;i--){var hit=hits.get(i);if(hit.rect.contains(lx,ly)&&(!hit.body||body().contains(lx,ly))){CommunityFx.sound("click");hit.action.run();return true;}}return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(body().contains(layout.localX(x),layout.localY(y))){scroll=Math.max(0,scroll-(int)(dy*30));return true;}return false;}
    @Override public void onClose(){if(!SocialHubScreen.back(this))minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
