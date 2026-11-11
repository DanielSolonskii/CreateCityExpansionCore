package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import java.util.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Searchable, grouped server-authorized actions. Opening a menu never mutates an object. */
public final class CenterActionsScreen extends Screen implements CenterResponseScreen {
    private record Entry(int group,int top,ActionButton button) {}
    private record Group(int index,int top) {}
    private final LogisticsCenterScreen parent;
    private final List<Entry> entries=new ArrayList<>();
    private final List<Group> groups=new ArrayList<>();
    private CompoundTag snapshot;
    private EditBox search;
    private int left,panelWidth,scroll,contentHeight,buttonHeight;
    private String query="";
    private boolean rebuild;
    public CenterActionsScreen(LogisticsCenterScreen parent,CompoundTag snapshot){super(tr("actions"));this.parent=parent;this.snapshot=snapshot.copy();}
    private int bodyTop(){return height<240?82:102;}
    @Override protected void init(){
        panelWidth=Math.min(680,width-24);left=(width-panelWidth)/2;entries.clear();groups.clear();
        search=new EditBox(font,left+12,height<240?58:68,panelWidth-24,20,tr("search_actions"));
        search.setHint(tr("search_actions"));search.setValue(query);search.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("ui.available_actions")));
        search.setResponder(value->{query=value;scroll=0;rebuild=true;});addRenderableWidget(search);
        var forms=new ArrayList<CompoundTag>();
        for(Tag raw:snapshot.getList("Forms",10)){
            var form=(CompoundTag)raw;
            if(text("action."+form.getString("Action")).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))forms.add(form);
        }
        forms.sort(Comparator.comparingInt(form->CenterActionCatalog.group(form.getString("Action"))));
        int columns=CenterActionCatalog.columns(width),buttonWidth=CenterActionCatalog.buttonWidth(width),y=0,previous=-1,column=0;
        buttonHeight=height<240?28:42;int step=buttonHeight+8;
        for(var form:forms){
            String action=form.getString("Action");int group=CenterActionCatalog.group(action);
            if(group!=previous){if(column!=0){y+=step;column=0;}if(previous!=-1)y+=10;groups.add(new Group(group,y));y+=height<240?18:24;previous=group;}
            int x=left+12+column*(buttonWidth+10);
            var button=new ActionButton(x,bodyTop()+y,buttonWidth,buttonHeight,tr("action."+action),()->minecraft.setScreen(action.equals("WAREHOUSE_RECEIVER")?new EquipmentWizardScreen(parent,parent.snapshot()):new CenterActionScreen(parent,form,parent.snapshot())),CenterActionCatalog.icon(action));
            button.hint(I18n.exists("ccec.center.hint."+action)?tr("hint."+action):tr("action_group_help."+group));
            button.narration(tr("action_group_help."+group));
            addWidget(button);entries.add(new Entry(group,y,button));column++;if(column==columns){column=0;y+=step;}
        }
        contentHeight=y+(column==0?0:step)+8;
        scroll=Math.min(scroll,CenterPresentation.scrollLimit(contentHeight,height-48-bodyTop()));
        addRenderableWidget(new ActionButton(left,height-38,panelWidth,24,tr("back"),this::onClose));
    }
    @Override public void accept(CompoundTag data){parent.accept(data);var next=parent.snapshot();rebuild|=!next.getList("Forms",10).equals(snapshot.getList("Forms",10));snapshot=next;}
    @Override public void tick(){super.tick();parent.tick();if(rebuild){boolean focused=search.isFocused();int cursor=search.getCursorPosition();rebuild=false;rebuildWidgets();if(focused){setFocused(search);search.setFocused(true);search.setCursorPosition(cursor);search.setHighlightPos(cursor);}}}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        background(g,width,height);var selected=snapshot.getCompound("SelectedRow");
        header(g,left,height<240?8:12,panelWidth,title,selected.isEmpty()?tr("ui.available_actions"):Component.literal(name(selected)),12);
        int top=bodyTop(),bottom=height-48;surface(g,left,top,panelWidth,Math.max(12,bottom-top),PANEL);
        g.enableScissor(left+6,top+2,left+panelWidth-6,bottom-2);
        for(var group:groups){int y=top+group.top()-scroll;String label=text("action_group."+group.index());
            g.drawString(font,fit(label,panelWidth-28),left+14,y+4,group.index()==4?BRASS:MUTED,false);
            int lineStart=left+24+Math.min(font.width(label),panelWidth-45);if(lineStart<left+panelWidth-14)g.hLine(lineStart,left+panelWidth-14,y+8,EDGE);
        }
        for(var entry:entries){int y=top+entry.top()-scroll;
            entry.button().setY(y);entry.button().visible=y+buttonHeight>top+2&&y<bottom-2;
            entry.button().active=y>=top+2&&y+buttonHeight<=bottom-2;
            if(entry.button().visible)entry.button().render(g,mx,my,dt);
        }
        if(entries.isEmpty()){icon(g,left+16,top+16,12,MUTED);g.drawWordWrap(font,tr("no_actions"),left+40,top+16,panelWidth-56,MUTED);}
        g.disableScissor();super.render(g,mx,my,dt);
        if(contentHeight>bottom-top){int track=bottom-top-8,thumb=Math.max(12,track*track/contentHeight),sy=top+4+(track-thumb)*scroll/Math.max(1,contentHeight-(bottom-top));g.fill(left+panelWidth-4,sy,left+panelWidth-2,sy+thumb,TEAL);}
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,height-48-bodyTop()),scroll-(int)(vertical*30)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==266||key==267){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,height-48-bodyTop()),scroll+(key==267?1:-1)*Math.max(30,height-60-bodyTop())));return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
