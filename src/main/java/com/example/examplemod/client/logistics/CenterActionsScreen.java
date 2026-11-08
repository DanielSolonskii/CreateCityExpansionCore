package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Searchable, grouped actions. Opening this menu or clicking an object never mutates server data. */
public final class CenterActionsScreen extends Screen implements CenterResponseScreen {
    private record Entry(int group,int top,ActionButton button) {}
    private final LogisticsCenterScreen parent;
    private final List<Entry> entries=new ArrayList<>();
    private CompoundTag snapshot;
    private EditBox search;
    private int left,panelWidth,scroll,contentHeight;
    private String query="";
    private boolean rebuild;
    public CenterActionsScreen(LogisticsCenterScreen parent,CompoundTag snapshot){super(tr("actions"));this.parent=parent;this.snapshot=snapshot.copy();}
    @Override protected void init(){
        panelWidth=Math.min(680,width-24);left=(width-panelWidth)/2;entries.clear();
        search=new EditBox(font,left+12,65,panelWidth-24,20,tr("search_actions"));search.setHint(tr("search_actions"));search.setValue(query);
        search.setResponder(value->{query=value;scroll=0;rebuild=true;});addRenderableWidget(search);
        var forms=new ArrayList<CompoundTag>();for(Tag raw:snapshot.getList("Forms",10)){var form=(CompoundTag)raw;if(text("action."+form.getString("Action")).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))forms.add(form);}
        forms.sort(Comparator.comparingInt(form->CenterActionCatalog.group(form.getString("Action"))));
        int columns=CenterActionCatalog.columns(width),buttonWidth=CenterActionCatalog.buttonWidth(width),y=12,previous=-1,column=0;
        for(var form:forms){String action=form.getString("Action");int group=CenterActionCatalog.group(action);
            if(group!=previous){if(column!=0){y+=44;column=0;}y+=27;previous=group;}
            int x=left+12+column*(buttonWidth+10);var button=new ActionButton(x,100+y,buttonWidth,38,tr("action."+action),()->minecraft.setScreen(new CenterActionScreen(parent,form,parent.snapshot())),CenterActionCatalog.icon(action));
            button.setTooltip(Tooltip.create(I18n.exists("ccec.center.hint."+action)?tr("hint."+action):tr("action_group_help."+group)));
            addWidget(button);entries.add(new Entry(group,y,button));column++;if(column==columns){column=0;y+=44;}
        }
        contentHeight=y+(column==0?0:44)+12;scroll=Math.min(scroll,CenterPresentation.scrollLimit(contentHeight,height-150));
        addRenderableWidget(new ActionButton(left,height-38,panelWidth,24,tr("back"),this::onClose));
    }
    @Override public void accept(CompoundTag data){parent.accept(data);var next=parent.snapshot();rebuild|=!next.getList("Forms",10).equals(snapshot.getList("Forms",10));snapshot=next;}
    @Override public void tick(){super.tick();parent.tick();if(rebuild){boolean focused=search.isFocused();int cursor=search.getCursorPosition();rebuild=false;rebuildWidgets();if(focused){setFocused(search);search.setFocused(true);search.setCursorPosition(cursor);search.setHighlightPos(cursor);}}}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fillGradient(0,0,width,height,BG,0xff14272c);surface(g,left,12,panelWidth,44,PANEL);icon(g,left+14,25,12,TEAL);
        g.drawString(font,text("actions"),left+37,22,TEXT,false);var selected=snapshot.getCompound("SelectedRow");
        g.drawString(font,fit(selected.isEmpty()?text("general_actions"):name(selected),panelWidth-52),left+37,38,MUTED,false);
        int top=94,bottom=height-48;surface(g,left,top,panelWidth,bottom-top,PANEL);g.enableScissor(left+6,top+4,left+panelWidth-6,bottom-4);
        int previous=-1;for(var entry:entries){int y=top+entry.top()-scroll;
            if(entry.group()!=previous){g.drawString(font,text("action_group."+entry.group()),left+14,y-18,MUTED,false);previous=entry.group();}
            entry.button().setY(y);entry.button().visible=y+38>top+4&&y<bottom-4;entry.button().active=y>=top+4&&y+38<=bottom-4;
            if(entry.button().visible)entry.button().render(g,mx,my,dt);
        }
        if(entries.isEmpty())g.drawWordWrap(font,tr("no_actions"),left+14,top+18,panelWidth-28,MUTED);
        g.disableScissor();super.render(g,mx,my,dt);
        if(contentHeight>bottom-top){int track=bottom-top-12,thumb=Math.max(14,track*(bottom-top)/contentHeight),sy=top+6+(track-thumb)*scroll/Math.max(1,contentHeight-bottom+top);g.fill(left+panelWidth-4,sy,left+panelWidth-2,sy+thumb,TEAL);}
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,height-150),scroll-(int)(vertical*30)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==266||key==267){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,height-150),scroll+(key==267?1:-1)*Math.max(40,height-160)));return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
