package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Context-specific instructions stay available without leaving the current workspace. */
public final class EconomicHelpScreen extends Screen implements CenterResponseScreen {
    private final LogisticsCenterScreen parent;
    private final Component body;
    private int left,panelWidth,scroll,contentHeight;
    public EconomicHelpScreen(LogisticsCenterScreen parent,CompoundTag guide){super(Component.translatable(guide.getString("TitleKey")));this.parent=parent;body=Component.translatable(guide.getString("BodyKey"));}
    @Override protected void init(){left=12;panelWidth=width-24;addRenderableWidget(new ActionButton(left,height-34,panelWidth,24,tr("back"),this::onClose));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        background(g,width,height);header(g,left,8,panelWidth,title,tr("ui.help"),13);
        int top=60,bottom=height-44;surface(g,left,top,panelWidth,Math.max(20,bottom-top),PANEL);
        int textWidth=panelWidth-28;contentHeight=font.wordWrapHeight(body,textWidth)+24;scroll=Math.clamp(scroll,0,CenterPresentation.scrollLimit(contentHeight,bottom-top));
        g.enableScissor(left+8,top+6,left+panelWidth-8,bottom-6);g.drawWordWrap(font,body,left+14,top+12-scroll,textWidth,TEXT);g.disableScissor();
        if(contentHeight>bottom-top){int available=bottom-top-12,thumb=Math.max(10,available*available/contentHeight),y=top+6+(available-thumb)*scroll/Math.max(1,contentHeight-available);g.fill(left+panelWidth-4,y,left+panelWidth-2,y+thumb,TEAL);}super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,height-104),scroll-(int)(vertical*24)));return true;}
    @Override public Component getNarrationMessage(){return title.copy().append(". ").append(body);}
    @Override public void accept(CompoundTag response){parent.accept(response);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
