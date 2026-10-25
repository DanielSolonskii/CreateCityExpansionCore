package com.example.examplemod.client.freight;

import com.example.examplemod.network.FreightGuideRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** A compact confirmation screen; no registration occurs on selecting a world block. */
public final class FreightGuideScreen extends Screen {
    private FreightGuideLayout layout;
    private EditBox name;
    private int mode,scroll;
    private String draft="";
    private Button save,modeButton;
    public FreightGuideScreen(){super(FreightGuideClient.tr("title"));mode=FreightGuideClient.draftMode;draft=FreightGuideClient.draft;}
    public int mode(){return mode;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        layout=FreightGuideLayout.of(width,height);boolean existing=FreightGuideClient.state().getBoolean("Existing");
        name=new EditBox(font,layout.x()+12,layout.contentTop(),layout.width()-24,20,title);
        name.setMaxLength(64);name.setValue(draft);name.setResponder(v->{draft=v;FreightGuideClient.draft=v;});
        name.setHint(FreightGuideClient.tr("name_hint"));name.active=!existing;addRenderableWidget(name);
        add(0,FreightGuideClient.tr("select_world"),()->FreightGuideClient.enterWorld(!existing||FreightGuideClient.state().getBoolean("Rebind")));
        add(1,FreightGuideClient.tr("build"),()->FreightGuideClient.enterWorld(false));
        add(2,FreightGuideClient.tr("ponder"),()->FreightPonderPlugin.open(this));
        modeButton=add(3,FreightGuideClient.tr("mode."+mode),()->{mode=(mode+1)%3;FreightGuideClient.draftMode=mode;modeButton.setMessage(FreightGuideClient.tr("mode."+mode));FreightGuideClient.command(FreightGuideRequest.Action.REFRESH,BlockPos.ZERO,mode,"");});modeButton.active=!existing;
        save=add(4,FreightGuideClient.tr("finish"),()->FreightGuideClient.command(FreightGuideRequest.Action.FINISH,BlockPos.ZERO,mode,draft));
        add(5,FreightGuideClient.tr("cancel"),()->FreightGuideClient.command(FreightGuideRequest.Action.CANCEL,BlockPos.ZERO,mode,""));
        update();
    }
    private Button add(int i,Component text,Runnable action){return addRenderableWidget(Button.builder(text,b->action.run()).bounds(layout.buttonX(i),layout.buttonY(i),layout.buttonWidth(),20).build());}
    public void update(){if(save!=null)save.active=(!FreightGuideClient.state().getBoolean("Existing")||FreightGuideClient.state().getBoolean("Rebind"))&&FreightGuideClient.state().getBoolean("Ready")&&!draft.isBlank()&&FreightGuideClient.state().getInt("Mode")==mode;}
    @Override public void tick(){update();}
    @Override public void onClose(){FreightGuideClient.enterWorld(false);}
    private List<Component> content(){
        var state=FreightGuideClient.state();List<Component> lines=new ArrayList<>();
        lines.add(FreightGuideClient.tr(state.getBoolean("Existing")?"diagnostics":"step."+state.getInt("Step")));
        lines.add(FreightGuideClient.tr("explain."+state.getInt("Step")));
        String result=state.getString("Result");
        if(!result.isBlank()&&!result.equals("ok"))lines.add(Component.translatable("gui.createcityexpansioncore.freight."+result));
        for(Tag raw:state.getList("Checks",10)){var c=(net.minecraft.nbt.CompoundTag)raw;lines.add(Component.literal(c.getBoolean("Ok")?"✓ ":"! ").append(FreightGuideClient.tr("check."+c.getString("Key")+"."+c.getBoolean("Ok"))));}
        if(state.contains("Warehouse"))lines.add(FreightGuideClient.tr("warehouse_pos",BlockPos.of(state.getLong("Warehouse")).toShortString()));
        lines.add(FreightGuideClient.tr("physical_unverified"));
        lines.add(FreightGuideClient.tr("supply_help"));
        lines.add(FreightGuideClient.tr("receiver_help"));
        lines.add(FreightGuideClient.tr("network_help"));
        lines.add(FreightGuideClient.tr("first_batch"));
        return lines;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        renderBackground(g,mx,my,delta);g.fill(layout.x(),layout.y(),layout.x()+layout.width(),layout.y()+layout.height(),0xFA172125);
        g.fill(layout.x(),layout.y(),layout.x()+layout.width(),layout.y()+2,0xFFD5AA62);
        g.drawString(font,title,layout.x()+12,layout.y()+13,0xFFD5AA62,false);
        int top=layout.contentTop()+28,bottom=layout.contentBottom();int total=0;
        for(var c:content())total+=font.split(c,layout.width()-30).size()*11+7;
        scroll=Math.clamp(scroll,0,Math.max(0,total-(bottom-top)));
        g.enableScissor(layout.x()+8,top,layout.x()+layout.width()-8,bottom);
        int y=top-scroll;
        for(var c:content()){
            int color=c.getString().startsWith("!")?0xFFF0B581:c.getString().startsWith("✓")?0xFF88CABA:0xFFD1DCD7;
            for(var line:font.split(c,layout.width()-30)){g.drawString(font,line,layout.x()+12,y,color,false);y+=11;}y+=7;
        }
        g.disableScissor();super.render(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,scroll-(int)(dy*22));return true;}
}
