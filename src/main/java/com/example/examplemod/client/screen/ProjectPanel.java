package com.example.examplemod.client.screen;

import com.example.examplemod.client.ProjectClientState;
import com.example.examplemod.client.map.CityMapPanel;

import com.example.examplemod.network.*;
import com.example.examplemod.projects.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static com.example.examplemod.projects.Project.*;
import static com.example.examplemod.projects.ProjectManager.Action;

/** Map-first project workspace. Forms, picking and confirmation stay inside the atlas. */
final class ProjectPanel {
    private record Hit(WorldMapUiLayout.Rect bounds,Runnable action){}
    private final List<Hit> hits=new ArrayList<>();
    private record MapHit(WorldMapUiLayout.Rect bounds,UUID id){}
    private final List<MapHit> mapHits=new ArrayList<>();
    private final List<WorldMapUiLayout.Rect> labels=new ArrayList<>();
    private boolean managementOpen;
    private final List<EditBox> fields=new ArrayList<>();
    private boolean open,editing,picking;
    private boolean saving;
    private int step,scroll,contentHeight,y,serial=-1,width,height,top;
    private UUID selected,editId;
    private ProjectZone zone;
    private ProjectZone.Shape shape=ProjectZone.Shape.POINT;
    private Integer firstX,firstZ;
    private Category category=Category.BUILDING;
    private Funding funding=Funding.FREE;
    private Scope scope=Scope.CITY;
    public boolean territoryPicking(){return picking&&scope==Scope.CITY;}
    private boolean adminMode(){return ProjectClientState.typeFilter()==3&&ProjectClientState.op;}
    private void chooseType(int type){ProjectClientState.setTypeFilter(type);selected=null;scroll=0;ProjectClientState.request(null);}
    private EditBox title,description,stageText,reward,reason;
    private String validation="";
    private Runnable confirmation;
    private Component confirmationLabel;
    private WorldMapUiLayout.Rect panel,body,viewport;
    private Font font;
    private GuiGraphics graphics;
    private int mouseX,mouseY;
    private CityMapPanel map;
    private net.minecraft.resources.ResourceLocation dimension;
    public boolean open(){return open;}
    public boolean picking(){return picking;}
    static boolean shouldRenderMapLayer(boolean layerEnabled,boolean editing,boolean selected){return layerEnabled||editing||selected;}
    public boolean mapLayerVisible(){return shouldRenderMapLayer(map.isOverlayEnabled(CityMapPanel.Overlay.PROJECTS),open&&editing,open&&selected!=null);}
    static ProjectZone previewZone(net.minecraft.resources.ResourceLocation dimension,ProjectZone.Shape shape,Integer firstX,Integer firstZ,int x,int z) {
        return firstX==null?new ProjectZone(dimension,ProjectZone.Shape.POINT,x,z,x,z):new ProjectZone(dimension,shape,firstX,firstZ,x,z);
    }
    public void prepareMap(CityMapPanel map,net.minecraft.resources.ResourceLocation dimension,WorldMapUiLayout.Rect viewport,int mouseX,int mouseY) {
        this.map=map;this.dimension=dimension;this.viewport=viewport;this.mouseX=mouseX;this.mouseY=mouseY;
    }
    public void renderMapLayer(GuiGraphics graphics){drawZones(graphics);}
    public boolean blocksDrag(double x,double y){return open&&(picking||confirmation!=null||panel!=null&&panel.contains(x,y));}
    private static Component tr(String key,Object...args){return Component.translatable("gui.createcityexpansioncore.projects."+key,args);}
    private UUID me(){return Minecraft.getInstance().player.getUUID();}
    private String name(UUID id){String known=ProjectClientState.names.getString(id.toString());return known.isBlank()?tr("member",id.toString().substring(0,8)).getString():known;}
    private Project selected(){return ProjectClientState.records.stream().filter(p->p.id().equals(selected)).findFirst().orElse(null);}
    public void render(GuiGraphics g,Font font,CityMapPanel map,net.minecraft.resources.ResourceLocation dimension,
                       int width,int height,int top,WorldMapUiLayout.Rect viewport,int mx,int my,float partial,boolean enabled) {
        this.graphics=g;this.font=font;this.map=map;this.dimension=dimension;this.width=width;this.height=height;this.top=top;this.viewport=viewport;this.mouseX=mx;this.mouseY=my;
        if(!enabled)return;
        var toggle=ProjectLayout.toggle(width,height);WorldMapChrome.control(g,toggle,open,toggle.contains(mx,my),true,false);
        g.drawCenteredString(font,tr(open?"close_workspace":"open_workspace"),toggle.x()+toggle.width()/2,toggle.y()+7,WorldMapChrome.ACCENT);
        if(!open)return;
        panel=ProjectLayout.panel(width,height,top);body=ProjectLayout.body(panel);
        WorldMapChrome.panel(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.drawString(font,tr(adminMode()?"admin_title":editing?"create":selected==null?"title":"details"),panel.x()+10,panel.y()+10,WorldMapChrome.TEXT,false);
        g.drawString(font,WorldMapChrome.fit(font,editing?tr("balances",ProjectClientState.wallet,ProjectClientState.treasury):tr("workspace_hint"),panel.width()-20),panel.x()+10,panel.y()+24,WorldMapChrome.MUTED,false);
        if(serial!=ProjectClientState.serial) {
            serial=ProjectClientState.serial;
            if(ProjectClientState.created!=null&&ProjectClientState.result.equals("ok")){selected=ProjectClientState.created;editing=false;scroll=0;ProjectClientState.created=null;}
            if(saving&&!ProjectClientState.pending()){if(ProjectClientState.result.equals("ok"))editing=false;saving=false;}
        }
        hits.clear();fields.forEach(f->f.setVisible(false));y=0;
        g.enableScissor(body.x(),body.y(),body.right(),body.bottom());
        if(confirmation!=null)renderConfirmation();
        else if(editing)renderForm(partial);
        else if(selected!=null&&selected()!=null)renderDetails(selected(),partial);
        else renderList();
        contentHeight=y;scroll=ProjectLayout.clampScroll(scroll,contentHeight,body.height());
        g.disableScissor();
        if(contentHeight>body.height()) {
            int thumb=Math.max(10,body.height()*body.height()/contentHeight);
            int sy=body.y()+(body.height()-thumb)*scroll/Math.max(1,contentHeight-body.height());
            g.fill(panel.right()-5,sy,panel.right()-3,sy+thumb,WorldMapChrome.ACCENT);
        }
        String key=validation.isBlank()?ProjectClientState.result:validation;
        g.enableScissor(panel.x()+8,panel.bottom()-20,panel.right()-8,panel.bottom()-2);
        g.drawString(font,WorldMapChrome.fit(font,tr("result."+key),panel.width()-20),panel.x()+10,panel.bottom()-16,key.equals("ok")?WorldMapChrome.MUTED:WorldMapChrome.ACCENT,false);
        g.disableScissor();
    }
    private void text(Component value,int color) {
        int h=font.wordWrapHeight(value,body.width());graphics.drawWordWrap(font,value,body.x(),body.y()+y-scroll,body.width(),color);y+=h+7;
    }
    private void button(Component label,Runnable action,boolean primary,boolean enabled) {
        var r=ProjectLayout.row(body,scroll,y,24);boolean available=enabled&&!ProjectClientState.pending();
        WorldMapChrome.control(graphics,r,false,r.contains(mouseX,mouseY)&&body.contains(mouseX,mouseY),available,primary);
        graphics.drawString(font,WorldMapChrome.fit(font,label,r.width()-12),r.x()+6,r.y()+8,available?WorldMapChrome.TEXT:WorldMapChrome.MUTED,false);
        if(available)hits.add(new Hit(r,action));y+=29;
    }
    private void button(String key,Runnable action,boolean primary){button(tr(key),action,primary,true);}
    private void field(EditBox field,String label,float partial) {
        text(tr(label),WorldMapChrome.MUTED);var r=ProjectLayout.row(body,scroll,y,20);
        field.setX(r.x());field.setY(r.y());field.setWidth(r.width());field.setVisible(true);field.render(graphics,mouseX,mouseY,partial);y+=28;
    }
    private EditBox make(int max,String value) {
        EditBox box=new EditBox(font,0,0,100,20,Component.empty());box.setMaxLength(max);box.setValue(value);fields.add(box);return box;
    }
    private void start(Project p) {
        map.setOverlay(CityMapPanel.Overlay.PROJECTS,true);
        scope=p==null?(adminMode()?Scope.SERVER:Scope.CITY):p.scope();
        fields.clear();title=make(96,p==null?"":p.title());description=make(2048,p==null?"":p.description());
        stageText=make(2048,p==null?"":String.join("; ",p.stages().stream().map(Stage::title).toList()));reward=make(19,p==null?"0":Long.toString(p.reward()));
        reason=make(512,"");editing=true;saving=false;managementOpen=false;editId=p==null?null:p.id();selected=p==null?null:p.id();step=scroll=0;zone=p==null?null:p.zone();
        category=p==null?Category.BUILDING:p.category();funding=p==null?(scope==Scope.SERVER?Funding.SERVER_ISSUANCE:Funding.FREE):p.funding();shape=zone==null?ProjectZone.Shape.POINT:zone.shape();picking=false;firstX=firstZ=null;validation="";
    }
    private void renderList() {
        text(tr("intro"),WorldMapChrome.MUTED);
        button(tr("type."+ProjectClientState.typeFilter()),()->chooseType((ProjectClientState.typeFilter()+1)%3),false,true);
        if(ProjectClientState.op)button(adminMode()?"admin_exit":"admin_open",()->chooseType(adminMode()?0:3),false);
        if(adminMode()) {
            text(tr("server_budget",ProjectClientState.serverAvailable,ProjectClientState.serverReserved,ProjectClientState.serverIssued,ProjectClientState.serverLimit),WorldMapChrome.ACCENT);
            text(tr("server_budget_hint"),WorldMapChrome.MUTED);
            button("server_create",()->start(null),true);
        }else if(ProjectClientState.city!=null&&ProjectClientState.typeFilter()!=2)button("create",()->start(null),true);
        button(tr("filter."+ProjectClientState.statusFilter()),()->{ProjectClientState.filter=ProjectClientState.typeFilter()*4+(ProjectClientState.statusFilter()+1)%4;ProjectClientState.page=0;selected=null;scroll=0;ProjectClientState.request(null);},false,true);
        text(tr("count",ProjectClientState.total,ProjectClientState.page+1,Math.max(1,(ProjectClientState.total+15)/16)),WorldMapChrome.MUTED);
        if(ProjectClientState.records.isEmpty())text(tr("empty"),WorldMapChrome.MUTED);
        for(Project p:ProjectClientState.records) {
            button(Component.literal(p.title()),()->{selected=p.id();scroll=0;reason=null;map.centerOn(new BlockPos(p.zone().x1(),64,p.zone().z1()));},false,true);
            text(tr("scope."+p.scope().name().toLowerCase(Locale.ROOT)),ProjectMapStyle.color(p.scope(),p.status()));
            text(tr("status."+p.status().name().toLowerCase(Locale.ROOT)),statusColor(p.status()));
        }
        if(ProjectClientState.page>0)button("previous",()->{ProjectClientState.page--;scroll=0;ProjectClientState.request(null);},false);
        if((ProjectClientState.page+1)*16<ProjectClientState.total)button("next",()->{ProjectClientState.page++;scroll=0;ProjectClientState.request(null);},false);
        section(tr("legend_title"));
        text(tr("server_legend"),ProjectMapStyle.SERVER_COLOR);
        for(Status status:Status.values()) {
            var row=ProjectLayout.row(body,scroll,y,16);int color=ProjectClientState.typeFilter()>=2?ProjectMapStyle.color(Scope.SERVER,status):statusColor(status);
            for(int x=0;x<18;x++)if(!ProjectMapStyle.dashed(status)||(x/4)%2==0)graphics.fill(row.x()+x,row.y()+7,row.x()+x+1,row.y()+9,color);
            graphics.drawString(font,WorldMapChrome.fit(font,tr("status."+status.name().toLowerCase(Locale.ROOT)),row.width()-26),row.x()+26,row.y()+4,color,false);y+=18;
        }
    }
    private void renderForm(float partial) {
        if(scope==Scope.SERVER&&!ProjectClientState.op){text(tr("result.permission"),WorldMapChrome.ACCENT);button("discard",()->{editing=false;picking=false;fields.clear();scroll=0;},false);return;}
        text(tr("step",step+1,4),WorldMapChrome.ACCENT);
        if(step==0){field(title,"name",partial);field(description,"description",partial);button(tr("category."+category.name().toLowerCase(Locale.ROOT)),()->category=Category.values()[(category.ordinal()+1)%Category.values().length],false,true);}
        if(step==1) {
            button(tr("shape."+shape.name().toLowerCase(Locale.ROOT)),()->{shape=ProjectZone.Shape.values()[(shape.ordinal()+1)%3];zone=null;picking=false;firstX=firstZ=null;},false,true);
            text(tr(scope==Scope.SERVER?"server_zone_hint":"zone_hint"),WorldMapChrome.MUTED);
            button(picking?"pick_again":"pick",()->{picking=true;firstX=firstZ=null;validation="";},true);
            if(picking)text(tr(firstX==null?"pick_first":"pick_second"),WorldMapChrome.ACCENT);
            if(zone!=null){text(Component.literal(zone.x1()+", "+zone.z1()+" → "+zone.x2()+", "+zone.z2()),WorldMapChrome.TEXT);text(tr("zone_size",zone.maxX()-zone.minX()+1,zone.maxZ()-zone.minZ()+1),WorldMapChrome.MUTED);}
        }
        if(step==2) {
            field(stageText,"stages",partial);text(tr("stages_hint"),WorldMapChrome.MUTED);
            if(scope==Scope.SERVER)text(tr("funding.server_issuance"),WorldMapChrome.ACCENT);
            else button(tr("funding."+funding.name().toLowerCase(Locale.ROOT)),()->funding=Funding.values()[(funding.ordinal()+1)%3],false,true);
            if(funding!=Funding.FREE)field(reward,"reward",partial);
            text(tr(scope==Scope.SERVER?"server_reserve_hint":"reserve_hint"),WorldMapChrome.MUTED);
        }
        if(step==3) {
            text(Component.literal(title.getValue()),WorldMapChrome.TEXT);text(Component.literal(description.getValue()),WorldMapChrome.MUTED);
            text(tr("funding."+funding.name().toLowerCase(Locale.ROOT)),WorldMapChrome.ACCENT);
            text(tr("reward_amount",funding==Funding.FREE?0:reward.getValue()),WorldMapChrome.TEXT);
            text(tr("draft_hint"),WorldMapChrome.MUTED);button("save_draft",this::saveDraft,true);
        }
        if(step>0)button("back",()->{step--;scroll=0;unfocus();},false);
        if(step<3)button("next",()->{step++;scroll=0;unfocus();},true);
        button("discard",()->confirm(tr("discard"),()->{editing=false;picking=false;fields.clear();scroll=0;}),false);
    }
    private void saveDraft() {
        try {
            if(zone==null) {validation="territory";step=1;return;}
            List<Stage> stages=Arrays.stream(stageText.getValue().split(";",-1)).map(String::strip).map(s->new Stage(s,false)).toList();
            long amount=funding==Funding.FREE?0:Long.parseLong(reward.getValue());
            if(stages.size()>16||amount<0||amount>ProjectClientState.maximum||funding!=Funding.FREE&&funding!=Funding.SERVER_ISSUANCE&&amount==0||title.getValue().isBlank())throw new IllegalArgumentException();
            var draft=new ProjectManager.Draft(scope,title.getValue(),description.getValue(),category,zone,stages,funding,amount);
            Project p=selected();if(editId!=null&&p==null){validation="stale";return;}
            ProjectClientState.filter=ProjectClientState.typeFilter()*4;saving=true;send(editId==null?Action.CREATE:Action.UPDATE,p,null,0,false,"",draft);
        }catch(RuntimeException error){validation="invalid";}
    }
    private void renderDetails(Project p,float partial) {
        button("list",()->{selected=null;scroll=0;fields.clear();reason=null;},false);
        text(tr("category."+p.category().name().toLowerCase(Locale.ROOT)),WorldMapChrome.MUTED);
        heading(Component.literal(p.title()));
        text(tr("scope."+p.scope().name().toLowerCase(Locale.ROOT)),ProjectMapStyle.color(p.scope(),p.status()));
        if(p.scope()==Scope.SERVER) {
            var overlaps=ProjectClientState.conflicts.getList(p.id().toString(),net.minecraft.nbt.Tag.TAG_STRING);
            text(overlaps.isEmpty()?tr("no_conflicts"):tr("conflicts",String.join(", ",overlaps.stream().map(net.minecraft.nbt.Tag::getAsString).toList())),WorldMapChrome.ACCENT);
        }
        var badge=ProjectLayout.row(body,scroll,y,20);
        WorldMapChrome.rounded(graphics,badge.x(),badge.y(),badge.width(),20,3,0xFF263137);
        graphics.fill(badge.x(),badge.y()+3,badge.x()+3,badge.bottom()-3,ProjectMapStyle.color(p.scope(),p.status()));
        graphics.drawString(font,tr("status."+p.status().name().toLowerCase(Locale.ROOT)),badge.x()+9,badge.y()+6,ProjectMapStyle.color(p.scope(),p.status()),false);y+=28;
        if(!p.description().isBlank())text(Component.literal(p.description()),WorldMapChrome.TEXT);
        var money=ProjectLayout.row(body,scroll,y,42);
        WorldMapChrome.rounded(graphics,money.x(),money.y(),money.width(),money.height(),4,WorldMapChrome.INSET);
        graphics.drawString(font,WorldMapChrome.fit(font,tr("reward_amount",p.reward()),money.width()-14),money.x()+7,money.y()+7,WorldMapChrome.ACCENT,false);
        graphics.drawString(font,WorldMapChrome.fit(font,tr(p.paid()?"paid":"reserved",p.reserved()),money.width()-14),money.x()+7,money.y()+23,WorldMapChrome.MUTED,false);y+=49;
        text(tr("funding."+p.funding().name().toLowerCase(Locale.ROOT)),WorldMapChrome.MUTED);
        text(tr("executor",p.executor()==null?tr("unassigned"):applicantName(p.executor())),WorldMapChrome.TEXT);
        text(tr("author",name(p.author())),WorldMapChrome.MUTED);
        if(!p.reviewNote().isBlank())text(tr("review_note",p.reviewNote()),WorldMapChrome.ACCENT);
        int done=(int)p.stages().stream().filter(Stage::done).count();
        section(tr("stage_progress",done,p.stages().size()));
        var progress=ProjectLayout.row(body,scroll,y,3);
        graphics.fill(progress.x(),progress.y(),progress.right(),progress.bottom(),WorldMapChrome.EDGE);
        graphics.fill(progress.x(),progress.y(),progress.x()+progress.width()*done/p.stages().size(),progress.bottom(),ProjectMapStyle.color(p.scope(),p.status()));y+=12;
        boolean server=p.scope()==Scope.SERVER;
        boolean author=p.author().equals(me()),manage=server?ProjectClientState.op:author||ProjectClientState.admin,executor=p.executor()!=null&&p.executor().representative().equals(me());
        for(int i=0;i<p.stages().size();i++) {
            int index=i;Stage s=p.stages().get(i);
            if(executor&&(p.status()==Status.BUILDING||p.status()==Status.REWORK))button(Component.literal((s.done()?"[✓] ":"[ ] ")+s.title()),()->send(Action.STAGE,p,null,index,!s.done(),"",null),false,true);
            else text(Component.literal((s.done()?"✓ ":"○ ")+s.title()),s.done()?0xFF86CFA0:WorldMapChrome.MUTED);
        }
        if(p.status().terminal())return;
        if(p.status()==Status.DRAFT&&manage) {
            button("edit",()->start(p),false);
            button(p.funding()==Funding.TREASURY?"request_funding":"publish",()->confirm(operationConfirm(p,tr("reserve_confirm",p.reward())),()->send(Action.PUBLISH,p,null,0,server&&!ProjectClientState.conflicts.getList(p.id().toString(),net.minecraft.nbt.Tag.TAG_STRING).isEmpty(),"",null)),true);
        }
        if(p.status()==Status.PENDING_FUNDING&&ProjectClientState.admin) {
            button("approve",()->confirm(tr("reserve_confirm",p.reward()),()->send(Action.APPROVE,p,null,0,false,"",null)),true);
            ensureReason();field(reason,"reason",partial);button("reject_funding",()->confirm(tr("reject_funding"),()->send(Action.REJECT_FUNDING,p,null,0,false,reason.getValue(),null)),false);
        }
        if(p.status()==Status.OPEN) {
            if(server&&manage)button("withdraw",()->confirm(tr("withdraw_confirm"),()->send(Action.WITHDRAW,p,null,0,false,"",null)),false);
            boolean applied=p.applicants().contains(Applicant.player(me()));
            button(tr(applied?"applied":"apply"),()->send(Action.APPLY,p,null,0,false,"",null),true,!applied);
            if(server&&ProjectClientState.cityRepresentative)button(tr("apply_city"),()->send(Action.APPLY_CITY,p,null,0,false,"",null),false,p.applicants().stream().noneMatch(a->a.kind()==ApplicantKind.CITY&&a.id().equals(ProjectClientState.city)));
        }
        boolean abandoned=!server&&p.executor()!=null&&!ProjectClientState.cityMembers.contains(p.executor().representative());
        if(abandoned)text(tr("executor_left"),WorldMapChrome.ACCENT);
        if(manage&&(p.status()==Status.OPEN||abandoned||server&&(p.status()==Status.BUILDING||p.status()==Status.REWORK||p.status()==Status.REVIEW))) {
            text(tr("applications",p.applicants().size()),WorldMapChrome.MUTED);
            for(Applicant a:p.applicants())button(tr("assign",applicantName(a)),()->confirm(tr(server?"server_assign_confirm":"assign",applicantName(a)),()->send(a.kind()==ApplicantKind.CITY?Action.ASSIGN_CITY:Action.ASSIGN,p,a.id(),0,false,"",null)),false,server||ProjectClientState.cityMembers.contains(a.representative()));
        }
        if(executor&&(p.status()==Status.BUILDING||p.status()==Status.REWORK))button("submit",()->confirm(tr("submit"),()->send(Action.SUBMIT,p,null,0,false,"",null)),true);
        if(manage&&!executor&&p.status()==Status.REVIEW) {
            button("accept",()->confirm(operationConfirm(p,tr("payout_confirm",p.reward(),applicantName(p.executor()))),()->send(Action.ACCEPT,p,null,0,server&&!ProjectClientState.conflicts.getList(p.id().toString(),net.minecraft.nbt.Tag.TAG_STRING).isEmpty(),"",null)),true);
            ensureReason();field(reason,"reason",partial);button("return",()->confirm(tr("return"),()->send(Action.RETURN,p,null,0,false,reason.getValue(),null)),false);
        }
        if(manage)button(tr(managementOpen?"management_close":"management"),()->managementOpen=!managementOpen,false,true);
        if(managementOpen) {
            text(tr("scope."+p.scope().name().toLowerCase(Locale.ROOT)),WorldMapChrome.MUTED);
            text(tr("zone_size",p.zone().maxX()-p.zone().minX()+1,p.zone().maxZ()-p.zone().minZ()+1),WorldMapChrome.MUTED);
            text(tr("build_rights"),WorldMapChrome.MUTED);
        }
        if(managementOpen&&(server?manage:p.executor()==null?manage:ProjectClientState.admin)) {
            if(p.executor()!=null&&!(manage&&!executor&&p.status()==Status.REVIEW)){ensureReason();field(reason,"reason",partial);}
            button("cancel",()->confirm(tr(server?"server_cancel_confirm":"refund_confirm",p.reserved()),()->send(Action.CANCEL,p,null,0,false,reason==null?"":reason.getValue(),null)),false);
        }
    }
    private void heading(Component value) {
        int w=(int)(body.width()/1.25);int h=font.wordWrapHeight(value,w);
        graphics.pose().pushPose();graphics.pose().translate(body.x(),body.y()+y-scroll,0);graphics.pose().scale(1.25F,1.25F,1);
        graphics.drawWordWrap(font,value,0,0,w,WorldMapChrome.TEXT);graphics.pose().popPose();y+=(int)Math.ceil(h*1.25)+12;
    }
    private void section(Component label) {
        y+=5;var r=ProjectLayout.row(body,scroll,y,1);graphics.fill(r.x(),r.y(),r.right(),r.bottom(),WorldMapChrome.EDGE);y+=9;text(label,WorldMapChrome.MUTED);
    }
    private String applicantName(Applicant applicant){return applicant.kind()==ApplicantKind.CITY?tr("city_applicant",name(applicant.id()),name(applicant.representative())).getString():name(applicant.id());}
    private Component operationConfirm(Project project,Component action) {
        var overlaps=ProjectClientState.conflicts.getList(project.id().toString(),net.minecraft.nbt.Tag.TAG_STRING);
        return project.scope()==Scope.SERVER&&!overlaps.isEmpty()?tr("conflict_confirm",action,String.join(", ",overlaps.stream().map(net.minecraft.nbt.Tag::getAsString).toList())):action;
    }
    private void ensureReason(){if(reason==null){fields.clear();reason=make(512,"");}}
    private void confirm(Component label,Runnable action){confirmationLabel=label;confirmation=action;scroll=0;unfocus();}
    private void renderConfirmation(){text(confirmationLabel,WorldMapChrome.ACCENT);button("confirm",()->{Runnable action=confirmation;confirmation=null;action.run();},true);button("back",()->confirmation=null,false);}
    private void send(Action action,Project p,UUID target,int stage,boolean done,String note,ProjectManager.Draft draft) {
        validation="";ProjectClientState.send(new ProjectManager.Command(UUID.randomUUID(),p==null?new UUID(0,0):p.id(),p==null?0:p.revision(),action,target==null?new UUID(0,0):target,stage,done,note,draft));
    }
    public boolean click(double x,double z,int button,boolean enabled) {
        if(!enabled)return false;
        if(ProjectLayout.toggle(width,height).contains(x,z)&&button==0){if(!editing&&confirmation==null){open=!open;if(open)map.setOverlay(CityMapPanel.Overlay.PROJECTS,true);scroll=0;unfocus();}return true;}
        if(!editing&&confirmation==null&&button==0&&viewport!=null&&viewport.contains(x,z)&&map.isOverlayEnabled(CityMapPanel.Overlay.PROJECTS))
            for(MapHit hit:mapHits)if(hit.bounds().contains(x,z)){selectProject(hit.id());return true;}
        if(!open)return false;
        if(panel!=null&&panel.contains(x,z)) {
            if(button!=0)return true;
            // These fields are drawn manually, so Screen does not assign their keyboard focus.
            for(EditBox field:fields)field.setFocused(field.visible&&body.contains(x,z)&&field.isMouseOver(x,z));
            if(body.contains(x,z)) {
                for(EditBox field:fields)if(field.visible)field.mouseClicked(x,z,button);
                for(Hit hit:List.copyOf(hits))if(hit.bounds.contains(x,z)){hit.action.run();break;}
            }return true;
        }
        if(confirmation!=null)return true;
        if(viewport==null||!viewport.contains(x,z)||button!=0)return picking;
        if(picking) {
            int wx=(int)Math.floor(map.screenToWorldX(x)),wz=(int)Math.floor(map.screenToWorldZ(z));
            if(firstX==null){firstX=wx;firstZ=wz;if(shape!=ProjectZone.Shape.POINT)return true;}
            try{zone=new ProjectZone(dimension,shape,firstX,firstZ,wx,wz);picking=false;validation="";}catch(IllegalArgumentException ex){validation="invalid";firstX=firstZ=null;}
            return true;
        }
        if(editing)return false;
        double wx=map.screenToWorldX(x),wz=map.screenToWorldZ(z),radius=8/map.zoom();
        var marker=ProjectClientState.markers.stream().filter(m->m.zone().dimension().equals(dimension)&&ProjectMapStyle.zones(map.zoom(),m.id().equals(selected))&&map.isOverlayEnabled(CityMapPanel.Overlay.PROJECTS)&&m.zone().distance(wx,wz)<=radius)
                .min(Comparator.comparingDouble(m->m.zone().distance(wx,wz))).orElse(null);
        if(marker!=null){selectProject(marker.id());return true;}
        return false;
    }
    public boolean scroll(double x,double y,double amount){if(open&&panel!=null&&panel.contains(x,y)){scroll=ProjectLayout.clampScroll(scroll-(int)(amount*20),contentHeight,body.height());return true;}return confirmation!=null;}
    private void unfocus(){fields.forEach(f->f.setFocused(false));}
    public boolean key(int key,int scan,int mods) {
        if(!open)return false;
        if(key==GLFW.GLFW_KEY_ESCAPE){if(confirmation!=null)confirmation=null;else if(picking){picking=false;firstX=firstZ=null;}else if(editing)confirm(tr("discard"),()->{editing=false;fields.clear();scroll=0;});else if(selected!=null){selected=null;fields.clear();reason=null;scroll=0;}else open=false;return true;}
        if(confirmation!=null)return true;
        for(EditBox f:fields)if(f.visible&&f.isFocused()){f.keyPressed(key,scan,mods);return true;}
        return false;
    }
    public boolean character(char c,int mods){if(!open)return false;for(EditBox f:fields)if(f.visible&&f.isFocused()){f.charTyped(c,mods);return true;}return confirmation!=null;}
    private static int statusColor(Status s){return ProjectMapStyle.color(s);}
    private void drawZones(GuiGraphics g) {
        mapHits.clear();labels.clear();
        g.enableScissor(viewport.x(),viewport.y(),viewport.right(),viewport.bottom());
        var visible=ProjectClientState.markers.stream().filter(m->m.zone().dimension().equals(dimension))
                .filter(m->map.isOverlayEnabled(CityMapPanel.Overlay.PROJECTS)||m.id().equals(selected))
                .sorted(Comparator.comparing(m->!m.id().equals(selected))).toList();
        for(var marker:visible.reversed())if(ProjectMapStyle.zones(map.zoom(),marker.id().equals(selected)))
            drawZone(g,marker.zone(),ProjectMapStyle.color(marker.scope(),marker.status()),marker.id().equals(selected),ProjectMapStyle.dashed(marker.status()));
        for(var marker:visible)drawProjectMarker(g,marker);
        if(editing&&zone!=null&&zone.dimension().equals(dimension))drawZone(g,zone,WorldMapChrome.ACCENT,true,true);
        if(picking&&viewport.contains(mouseX,mouseY)) {
            try{drawZone(g,previewZone(dimension,shape,firstX,firstZ,(int)Math.floor(map.screenToWorldX(mouseX)),(int)Math.floor(map.screenToWorldZ(mouseY))),WorldMapChrome.ACCENT,true,true);}catch(IllegalArgumentException ignored){}
        }
        g.disableScissor();
    }
    private void drawProjectMarker(GuiGraphics g,ProjectClientState.Marker marker) {
        boolean chosen=marker.id().equals(selected);
        if(!ProjectMapStyle.markers(map.zoom(),chosen))return;
        int x=map.worldToScreenX((marker.zone().minX()+marker.zone().maxX()+1)/2.0),z=map.worldToScreenY((marker.zone().minZ()+marker.zone().maxZ()+1)/2.0);
        var box=ProjectMapStyle.marker(x,z);
        if(!viewport.contains(x,z))return;
        if(!chosen&&mapHits.stream().anyMatch(h->overlaps(h.bounds(),box)))return;
        int color=ProjectMapStyle.color(marker.scope(),marker.status());boolean hover=box.contains(mouseX,mouseY);
        WorldMapChrome.rounded(g,box.x(),box.y(),box.width(),box.height(),5,chosen?0xFFECE8D5:hover?0xFF59686C:color);
        WorldMapChrome.rounded(g,box.x()+1,box.y()+1,box.width()-2,box.height()-2,4,WorldMapChrome.INSET);
        int[] pixels=ProjectMapStyle.glyph(marker.scope(),marker.category(),marker.status());
        for(int row=0;row<8;row++)for(int col=0;col<8;col++)if((pixels[row]&(1<<col))!=0)g.fill(x-4+col,z-4+row,x-3+col,z-3+row,color);
        mapHits.add(new MapHit(box,marker.id()));
        Font f=Minecraft.getInstance().font;
        if(!chosen&&!hover&&map.zoom()<.55)return;
        String label=WorldMapChrome.fit(f,Component.literal(marker.title()),Math.min(144,viewport.width()-16));
        var rect=ProjectMapStyle.label(x,z,f.width(label),viewport);
        if(!chosen&&!hover&&labels.stream().anyMatch(r->overlaps(r,rect)))return;
        labels.add(rect);mapHits.add(new MapHit(rect,marker.id()));
        WorldMapChrome.rounded(g,rect.x(),rect.y(),rect.width(),rect.height(),3,WorldMapChrome.INSET);
        g.fill(rect.x()+4,rect.y()+16,rect.right()-4,rect.y()+17,color);g.drawString(f,label,rect.x()+6,rect.y()+5,chosen?WorldMapChrome.TEXT:color,false);
    }
    private static boolean overlaps(WorldMapUiLayout.Rect a,WorldMapUiLayout.Rect b){return a.x()<b.right()&&a.right()>b.x()&&a.y()<b.bottom()&&a.bottom()>b.y();}
    private void selectProject(UUID id) {
        selected=id;open=true;scroll=0;reason=null;fields.clear();managementOpen=false;
        if(selected()==null){ProjectClientState.filter=ProjectClientState.typeFilter()*4+(ProjectClientState.statusFilter()==3?3:0);ProjectClientState.request(selected);}
    }
    private void drawZone(GuiGraphics g,ProjectZone z,int color,boolean selected,boolean dashed) {
        int x1=map.worldToScreenX(z.shape()==ProjectZone.Shape.RECTANGLE?z.minX():z.x1()+.5),y1=map.worldToScreenY(z.shape()==ProjectZone.Shape.RECTANGLE?z.minZ():z.z1()+.5);
        int x2=map.worldToScreenX(z.shape()==ProjectZone.Shape.RECTANGLE?z.maxX()+1:z.x2()+.5),y2=map.worldToScreenY(z.shape()==ProjectZone.Shape.RECTANGLE?z.maxZ()+1:z.z2()+.5);
        if(Math.max(x1,x2)<viewport.x()-8||Math.min(x1,x2)>viewport.right()+8||Math.max(y1,y2)<viewport.y()-8||Math.min(y1,y2)>viewport.bottom()+8)return;
        if(z.shape()==ProjectZone.Shape.POINT){g.fill(x1-6,y1-6,x1+7,y1+7,0xFF11191D);g.fill(x1-4,y1-4,x1+5,y1+5,color);if(selected)g.fill(x1-1,y1-1,x1+2,y1+2,0xFFFFFFFF);}
        else if(z.shape()==ProjectZone.Shape.RECTANGLE){int l=Math.min(x1,x2),r=Math.max(x1,x2),t=Math.min(y1,y2),b=Math.max(y1,y2);
            g.fill(l,t,r,b,(selected?0x20000000:0x10000000)|(color&0xFFFFFF));line(g,l,t,r,t,color,dashed,selected);line(g,r,t,r,b,color,dashed,selected);line(g,r,b,l,b,color,dashed,selected);line(g,l,b,l,t,color,dashed,selected);
            if(selected) {
                int arm=Math.min(8,Math.min(r-l,b-t)/3);
                for(int x:new int[]{l,r})for(int y:new int[]{t,b}) {
                    int dx=x==l?arm:-arm,dy=y==t?arm:-arm;
                    line(g,x,y,x+dx,y,0xFFECE8D5,false,false);line(g,x,y,x,y+dy,0xFFECE8D5,false,false);
                }
            }
        }
        else {line(g,x1,y1,x2,y2,color,dashed,selected);for(int[] end:new int[][]{{x1,y1},{x2,y2}})g.fill(end[0]-2,end[1]-2,end[0]+3,end[1]+3,color);}
    }
    /** Clip before rasterizing: distant endpoints never create unbounded GUI work. */
    private void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color,boolean dashed,boolean selected) {
        double dx=(double)x2-x1,dy=(double)y2-y1,lo=0,hi=1;
        double[] p={-dx,dx,-dy,dy},q={x1-viewport.x(),viewport.right()-x1,y1-viewport.y(),viewport.bottom()-y1};
        for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)return;}else{double t=q[i]/p[i];if(p[i]<0)lo=Math.max(lo,t);else hi=Math.min(hi,t);if(lo>hi)return;}}
        double ax=x1+lo*dx,ay=y1+lo*dy,bx=x1+hi*dx,by=y1+hi*dy;int steps=(int)Math.ceil(Math.max(Math.abs(bx-ax),Math.abs(by-ay)));int thick=selected?2:1;
        for(int i=0;i<=steps;i++){if(dashed&&(i/5)%2==1)continue;double f=steps==0?0:i/(double)steps;int x=(int)Math.round(ax+(bx-ax)*f),y=(int)Math.round(ay+(by-ay)*f);
            if(selected)g.fill(x-1,y-1,x+thick+1,y+thick+1,0x4011191D);g.fill(x,y,x+thick,y+thick,color);}
    }
}
