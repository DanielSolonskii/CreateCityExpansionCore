package com.example.examplemod.client.logistics;

import com.example.examplemod.client.CommunityFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Guided receiver setup on the existing authoritative Cargo selection/confirmation path. */
public final class EquipmentWizardScreen extends Screen implements CenterResponseScreen,LogisticsClient.EquipmentPicker {
    private static final int PAPER=0xff121a1f,CARD=0xff1a252b,LINE=0xff34434a,SOFT=0xffa2b0b5;
    private final LogisticsCenterScreen parent;
    private final CompoundTag warehouse;
    private final EquipmentWizardFlow flow=new EquipmentWizardFlow();
    private EquipmentWizardLayout layout;
    private int infoScroll,infoHeight;
    private boolean closeAfterCancel;
    private long opened;

    public EquipmentWizardScreen(LogisticsCenterScreen parent,CompoundTag snapshot){
        super(setup("title"));this.parent=parent;this.warehouse=snapshot.getCompound("SelectedRow").copy();
    }
    static Component setup(String key,Object... args){return Component.translatable("ccec.center.setup."+key,args);}
    @Override protected void init(){
        CommunityFx.load();if(opened==0)opened=net.minecraft.Util.getMillis();
        layout=EquipmentWizardLayout.of(width,height);
        var close=addRenderableWidget(button(EquipmentWizardLayout.CLOSE,flow.stage()==EquipmentWizardFlow.Stage.DONE?tr("back"):tr("cancel"),this::onClose,-1));
        close.active=!flow.busy()||flow.uncertain();
        if(flow.stage()==EquipmentWizardFlow.Stage.REVIEW||flow.canResume()){
            var again=addRenderableWidget(button(EquipmentWizardLayout.RESELECT,setup("again"),this::begin,14));again.active=!flow.busy()&&!flow.uncertain();
        }
        Component label=flow.stage()==EquipmentWizardFlow.Stage.DONE?setup("done"):
                flow.busy()?setup("waiting"):flow.uncertain()?setup("unknown"):
                flow.canSave()?setup("save"):flow.canResume()?setup("resume"):setup("start");
        var primary=addRenderableWidget(button(EquipmentWizardLayout.PRIMARY,label,()->{
            if(flow.stage()==EquipmentWizardFlow.Stage.DONE)leave();
            else if(flow.canSave())save();
            else if(flow.canResume())minecraft.setScreen(null);
            else begin();
        },flow.stage()==EquipmentWizardFlow.Stage.DONE?13:flow.canSave()?8:10));
        primary.active=!flow.busy()&&!flow.uncertain();
    }
    private ActionButton button(EquipmentWizardLayout.Rect r,Component label,Runnable action,int glyph){return new ActionButton(r.x(),r.y(),r.width(),r.height(),label,action,glyph);}
    private void begin(){
        if(flow.busy()||flow.uncertain())return;
        infoScroll=0;closeAfterCancel=false;
        flow.request(EquipmentWizardFlow.Operation.START,parent.mutate("PICK_RECEIVER",new CompoundTag()),net.minecraft.Util.getMillis());
        rebuildWidgets();
    }
    private void save(){
        if(!flow.canSave())return;
        var values=new CompoundTag();values.putString("EquipmentToken",flow.equipment().getUUID("EquipmentToken").toString());
        flow.request(EquipmentWizardFlow.Operation.SAVE,parent.mutate("WAREHOUSE_RECEIVER",values),net.minecraft.Util.getMillis());
        rebuildWidgets();
    }
    @Override public void accept(CompoundTag response){
        parent.accept(response);
        var event=flow.response(response);if(event==EquipmentWizardFlow.Event.IGNORE)return;
        infoScroll=0;
        if(event==EquipmentWizardFlow.Event.WORLD){LogisticsClient.awaitEquipment(this);minecraft.setScreen(null);return;}
        if(event==EquipmentWizardFlow.Event.CANCELLED&&closeAfterCancel){leave();return;}
        if(event==EquipmentWizardFlow.Event.SAVED)LogisticsClient.clearEquipment(this);
        rebuildWidgets();
    }
    @Override public boolean matchesEquipment(CompoundTag data){return flow.matches(data);}
    @Override public void equipmentProgress(CompoundTag data){flow.progress(data);}
    @Override public void acceptEquipment(CompoundTag data){
        var event=flow.selection(data);if(event==EquipmentWizardFlow.Event.IGNORE)return;
        infoScroll=0;Minecraft.getInstance().setScreen(this);
    }
    @Override public void returnToWizard(){Minecraft.getInstance().setScreen(this);}
    @Override public void tick(){super.tick();if(flow.timeout(net.minecraft.Util.getMillis()))rebuildWidgets();}
    @Override public void onClose(){
        if(flow.busy()&&!flow.uncertain())return;
        if(flow.stage()==EquipmentWizardFlow.Stage.DONE||!flow.needsCancel()&&!flow.busy()) {leave();return;}
        closeAfterCancel=true;
        flow.request(EquipmentWizardFlow.Operation.CANCEL,parent.mutate("CANCEL_RECEIVER_PICK",new CompoundTag()),net.minecraft.Util.getMillis());
        rebuildWidgets();
    }
    private void leave(){LogisticsClient.clearEquipment(this);minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseClicked(double x,double y,int button){return super.mouseClicked(layout.x(x),layout.y(y),button);}
    @Override public boolean mouseReleased(double x,double y,int button){return super.mouseReleased(layout.x(x),layout.y(y),button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(EquipmentWizardLayout.SIDE.contains(layout.x(x),layout.y(y))){infoScroll=Math.clamp(infoScroll-(int)(vertical*22),0,Math.max(0,infoHeight-158));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xff080d11,0xff17262c);
        for(int x=0;x<width;x+=32)g.vLine(x,0,height,0x102e484e);
        for(int y=0;y<height;y+=32)g.hLine(0,width,y,0x102e484e);
        g.pose().pushPose();g.pose().translate(layout.left(),layout.top(),0);g.pose().scale((float)layout.scale(),(float)layout.scale(),1);
        try{
            panel(g,0,0,760,442,PAPER);
            float reveal=CommunityFx.ease((net.minecraft.Util.getMillis()-opened)/220d);
            g.fill(18,0,18+(int)(724*reveal),2,BRASS);
            for(int x:new int[]{10,747})for(int y:new int[]{10,429}){g.fill(x,y,x+3,y+3,LINE);g.fill(x+1,y+1,x+2,y+2,SOFT);}
            g.drawString(font,setup("eyebrow"),24,21,BRASS,false);
            g.pose().pushPose();g.pose().translate(24,41,0);g.pose().scale(1.7f,1.7f,1);g.drawString(font,title,0,0,TEXT,false);g.pose().popPose();
            g.drawString(font,fit(name(warehouse),440),25,66,SOFT,false);
            pill(g,566,28,170,flow.stage()==EquipmentWizardFlow.Stage.DONE?setup("status.done"):setup("status.draft"),flow.stage()==EquipmentWizardFlow.Stage.DONE?TEAL:BRASS);
            steps(g);
            panel(g,24,150,448,218,CARD);panel(g,488,150,248,218,CARD);
            if(flow.stage()==EquipmentWizardFlow.Stage.DONE)success(g);
            else if(flow.stage()==EquipmentWizardFlow.Stage.REVIEW)review(g);
            else preparation(g);
            information(g);
            Component foot=flow.uncertain()?setup("uncertain_footer"):flow.stage()==EquipmentWizardFlow.Stage.DONE?setup("saved_footer"):flow.busy()?setup("waiting_footer"):setup("draft_footer");
            g.drawString(font,fit(foot.getString(),712),24,375,SOFT,false);
            super.render(g,(int)layout.x(mx),(int)layout.y(my),partial);
        }finally{g.pose().popPose();}
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,int color){
        g.fill(x+3,y+4,x+w+3,y+h+4,0x40000000);g.fill(x+2,y,x+w-2,y+h,color);g.fill(x,y+2,x+w,y+h-2,color);
        g.hLine(x+3,x+w-4,y,LINE);g.hLine(x+3,x+w-4,y+h-1,0xff0b1217);
    }
    private void pill(GuiGraphics g,int x,int y,int w,Component label,int color){
        g.fill(x,y,x+w,y+23,0xff111a1f);g.fill(x+9,y+10,x+12,y+13,color);
        g.drawString(font,fit(label.getString(),w-29),x+20,y+8,color,false);
    }
    private void steps(GuiGraphics g){
        int active=flow.stage()==EquipmentWizardFlow.Stage.PREPARE?0:flow.stage()==EquipmentWizardFlow.Stage.SELECT?1:2;
        boolean done=flow.stage()==EquipmentWizardFlow.Stage.DONE;
        for(int i=0;i<3;i++){
            int x=24+i*242;boolean past=i<active||done;int color=past?TEAL:i==active?BRASS:SOFT;
            g.fill(x,137,x+226,139,past?0xff46766b:i==active?BRASS:LINE);
            g.fill(x,98,x+20,119,i==active&&!done?0xff453d2d:0xff10191f);g.renderOutline(x,98,20,21,color);
            g.drawCenteredString(font,past?"✓":Integer.toString(i+1),x+10,105,color);
            g.drawString(font,setup("step."+i),x+30,101,color,false);g.drawString(font,setup("step_help."+i),x+30,116,SOFT,false);
        }
    }
    private void preparation(GuiGraphics g){
        Component heading=flow.canResume()?setup("select.title."+flow.pickStep()):setup("diagram.title");
        g.drawString(font,heading,40,168,TEXT,false);
        g.drawString(font,setup("diagram.caption"),40,184,SOFT,false);
        ItemStack[] items={createItem("brown_postbox"),router(),createItem("packager"),new ItemStack(Items.BARREL)};
        String[] labels={"postbox","router","packager","inventory"};
        for(int i=0;i<4;i++){
            int x=40+i*105;g.fill(x,211,x+88,282,0xff111a20);g.hLine(x,x+87,211,i==0||i==2?BRASS:LINE);
            item(g,items[i],x+27,226,2.1f);
            g.drawCenteredString(font,setup("block."+labels[i]),x+44,292,i==0||i==2?BRASS:SOFT);
            if(i<3)arrow(g,x+92,244,TEAL);
        }
        Component note=flow.canResume()?setup("selection_running"):setup("diagram.note");
        g.drawWordWrap(font,note,40,319,414,SOFT);
    }
    private void review(GuiGraphics g){
        g.drawString(font,setup("review.title"),40,168,TEXT,false);
        equipmentCard(g,40,190,createItem("packager"),setup("block.packager"),flow.equipment().getCompound("EquipmentPackager"),false);
        equipmentCard(g,40,271,createItem("brown_postbox"),setup("block.postbox"),flow.equipment().getCompound("EquipmentPostbox"),flow.equipment().getBoolean("EquipmentAuto"));
    }
    private void equipmentCard(GuiGraphics g,int x,int y,ItemStack icon,Component label,CompoundTag location,boolean automatic){
        g.fill(x,y,x+416,y+68,0xff101a20);g.fill(x,y,x+2,y+68,BRASS);item(g,icon,x+17,y+19,1.9f);
        g.drawString(font,label,x+62,y+12,TEXT,false);
        if(automatic)g.drawString(font,setup("automatic"),x+230,y+12,TEAL,false);
        String coords=CenterText.location(location,key->Component.translatable(key).getString());
        g.drawString(font,fit(coords,340),x+62,y+31,SOFT,false);
        g.drawString(font,setup("selected"),x+62,y+48,BRASS,false);
    }
    private void success(GuiGraphics g){
        int cx=248;g.fill(cx-21,186,cx+21,228,0xff213e36);
        for(int i=0;i<10;i++)g.fill(cx-13+i,207+i,cx-10+i,210+i,TEAL);
        for(int i=0;i<18;i++)g.fill(cx-4+i,214-i,cx-1+i,217-i,TEAL);
        g.drawCenteredString(font,setup("success.title"),cx,247,TEAL);
        g.drawWordWrap(font,setup("success.body"),54,274,388,TEXT);
        g.drawString(font,fit(name(warehouse),388),54,333,BRASS,false);
    }
    private void information(GuiGraphics g){
        boolean failure=!flow.result().isEmpty()||flow.uncertain();
        g.drawString(font,setup(failure?"issue.title":flow.stage()==EquipmentWizardFlow.Stage.REVIEW?"checks.title":"before.title"),504,168,failure?BRASS:TEXT,false);
        clip(g,502,188,232,168);
        int y=192-infoScroll,start=y;
        if(failure){
            Component message=flow.uncertain()?tr("uncertain"):Component.literal(result(flow.result()));
            g.drawWordWrap(font,message,506,y,208,BRASS);y+=font.wordWrapHeight(message,208)+18;
            var help=setup(flow.uncertain()?"issue.uncertain":"issue.retry");g.drawWordWrap(font,help,506,y,208,SOFT);y+=font.wordWrapHeight(help,208)+14;
        }else if(flow.stage()==EquipmentWizardFlow.Stage.DONE){
            var body=setup("success.next");g.drawWordWrap(font,body,506,y,208,TEXT);y+=font.wordWrapHeight(body,208)+18;
        }else if(flow.hint().equals("cancelled")||flow.hint().equals("expired")){
            var body=setup("selection."+flow.hint());g.drawWordWrap(font,body,506,y,208,BRASS);y+=font.wordWrapHeight(body,208)+18;
        }else{
            for(int i=0;i<3;i++){
                int color=flow.stage()==EquipmentWizardFlow.Stage.REVIEW&&i==0?TEAL:BRASS;
                g.fill(505,y+2,510,y+7,color);var body=setup((flow.stage()==EquipmentWizardFlow.Stage.REVIEW?"checks.":"before.")+i);
                g.drawWordWrap(font,body,519,y,197,i==0?TEXT:SOFT);y+=font.wordWrapHeight(body,197)+18;
            }
        }
        infoHeight=y-start+6;g.disableScissor();
        if(infoHeight>158){int thumb=Math.max(18,158*158/infoHeight),sy=192+(158-thumb)*infoScroll/Math.max(1,infoHeight-158);g.fill(729,sy,731,sy+thumb,BRASS);}
        if(flow.busy()){
            int x=504+(CommunityFx.reduced?0:(int)(net.minecraft.Util.getMillis()%1400)/7);
            g.fill(504,359,720,360,LINE);g.fill(x,359,Math.min(720,x+16),360,BRASS);
        }
    }
    private void clip(GuiGraphics g,int x,int y,int w,int h){g.flush();g.enableScissor((int)Math.floor(layout.screenX(x)),(int)Math.floor(layout.screenY(y)),(int)Math.ceil(layout.screenX(x+w)),(int)Math.ceil(layout.screenY(y+h)));}
    private static ItemStack router(){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","cargo_router")));}
    private static ItemStack createItem(String path){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create",path)));}
    private static void item(GuiGraphics g,ItemStack stack,int x,int y,float scale){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(stack,0,0);g.pose().popPose();}
    private static void arrow(GuiGraphics g,int x,int y,int color){g.hLine(x,x+8,y,color);for(int n=0;n<4;n++)g.vLine(x+8-n,y-n,y+n,color);}

    @Override public void renderEquipmentHud(GuiGraphics g,Component returnKey){
        var mc=Minecraft.getInstance();int w=Math.min(360,mc.getWindow().getGuiScaledWidth()-24),x=(mc.getWindow().getGuiScaledWidth()-w)/2;
        if(w<160)return;
        String key=switch(flow.hint()){
            case "too_far","packager_required","postbox_invalid","ambiguous","postbox_required"->flow.hint();
            default->flow.pickStep()==0?"packager":"postbox";
        };
        Component help=setup("hud."+key);int keysTop=61+font.wordWrapHeight(help,w-26)+10;
        panel(g,x,12,w,keysTop+24-12,0xf2111c23);g.fill(x+2,12,x+w-2,14,BRASS);
        item(g,flow.pickStep()==0?createItem("packager"):createItem("brown_postbox"),x+13,35,1.35f);
        g.drawString(font,setup("hud.step",flow.pickStep()+1),x+13,22,BRASS,false);
        g.drawString(font,fit(setup("select.title."+flow.pickStep()).getString(),w-54),x+45,39,TEXT,false);
        g.drawWordWrap(font,help,x+13,61,w-26,flow.hint().equals("postbox_invalid")||flow.hint().equals("packager_required")?BRASS:SOFT);
        g.drawString(font,fit(setup("hud.cancel_key",mc.options.keyShift.getTranslatedKeyMessage()).getString(),w-26),x+13,keysTop,SOFT,false);
        g.drawString(font,fit(setup("hud.return_key",returnKey).getString(),w-26),x+13,keysTop+12,SOFT,false);
    }
}
