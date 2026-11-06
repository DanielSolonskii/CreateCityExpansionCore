package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Read-only explanation. Returning preserves the form and never repeats its request. */
final class CenterErrorScreen extends Screen implements CenterResponseScreen {
    private final Screen parent;
    private final String explanation;
    private int scroll;
    CenterErrorScreen(Screen parent,String explanation){super(tr("error.explain"));this.parent=parent;this.explanation=explanation;}
    @Override public void accept(net.minecraft.nbt.CompoundTag response){if(parent instanceof CenterResponseScreen screen)screen.accept(response);}
    @Override public void tick(){super.tick();parent.tick();}
    @Override protected void init(){addRenderableWidget(new ActionButton(16,height-38,width-32,24,tr("back"),this::onClose));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,BG);g.drawString(font,title,24,20,BRASS,false);
        g.enableScissor(24,48,width-24,height-50);
        g.drawWordWrap(font,Component.literal(explanation),24,52-scroll,width-48,TEXT);
        g.disableScissor();super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(font.wordWrapHeight(Component.literal(explanation),width-48),height-106),scroll-(int)(vertical*20)));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
