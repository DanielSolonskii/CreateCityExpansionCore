package com.example.examplemod.client.screen;

import com.example.examplemod.client.*;
import com.example.examplemod.network.ProjectPlanRequest;
import com.example.examplemod.projects.*;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class SharedPlanPanel {
    private static WeakReference<SharedPlanPanel> recipient=new WeakReference<>(null);
    private final AtlasPlanEditor editor;
    private final ProjectPlanDrafts drafts;
    private UUID project, selected;
    private List<CompoundTag> rows=List.of();
    private List<ProjectClientState.Marker> sourceMarkers, candidates=List.of();
    private final Map<UUID,String> titles=new HashMap<>();
    private ProjectPlanRequest pending;
    private EngineeringPlan original, submitted;
    private long base, remote, sent, confirmed, epoch;
    private boolean canPublish, editable, opening;
    String status="";
    SharedPlanPanel(AtlasPlanEditor editor,ProjectPlanDrafts drafts){this.editor=editor;this.drafts=drafts;}
    private static boolean supported(){
        var c=Minecraft.getInstance().getConnection();return c!=null && c.hasChannel(ProjectPlanRequest.TYPE);
    }
    private boolean authorized(long now){return confirmed>0 && now-sentAtConfirmation<8000 && epoch==CityPublicDirectory.accessEpoch();}
    private long sentAtConfirmation;
    private boolean emptyPicker=true;
    private List<ProjectClientState.Marker> projects(){
        var markers=ProjectClientState.mapMarkers();
        if(sourceMarkers!=markers){
            sourceMarkers=markers;
            candidates=markers.stream().filter(m->editor.map.dimensionId().equals(m.zone().dimension())).toList();
            titles.clear();for(var candidate:candidates)titles.put(candidate.id(),candidate.title());
        }
        return candidates;
    }
    private String projectName(){
        projects();return titles.getOrDefault(project,"");
    }
    private void reset(){
        if(editor.projectDraft())editor.discardProjectDraft();
        sourceMarkers=null;candidates=List.of();titles.clear();
        rows=List.of();project=selected=null;original=submitted=null;pending=null;
        confirmed=base=remote=0;editable=canPublish=opening=false;
    }
    boolean saveLocal(){
        if(!editor.projectDraft() || original==null)return true;
        try {drafts.remember(project,base,original,editor.current());return true;}
        catch(IOException failed){status="shared_save_failed";return false;}
    }
    void close(){saveLocal();reset();if(recipient.get()==this)recipient.clear();}
    void widgets(int x,int y,int w,int bottom){
        if(!supported()){status="shared_unsupported";return;}
        int h=18, step=20;
        emptyPicker=projects().isEmpty();
        editor.sharedButton(x,y,w,AtlasPlanEditor.text("shared_project",projectName()),this::cycleProject).active=!projects().isEmpty();
        if(y+step+h>bottom)return;
        var row=rows.stream().filter(r->r.getUUID("Id").equals(selected)).findFirst().orElse(null);
        editor.sharedButton(x,y+step,w,AtlasPlanEditor.text("shared_choice",row==null?"—":row.getString("Name")),this::cyclePlan).active=!rows.isEmpty();
        if(y+2*step+h>bottom)return;
        int half=(w-2)/2;
        editor.sharedButton(x,y+2*step,half,AtlasPlanEditor.text("shared_open"),this::open).active=selected!=null && pending==null;
        editor.sharedButton(x+half+2,y+2*step,half,AtlasPlanEditor.text("shared_personal"),this::personal).active=editor.projectDraft();
        if(y+3*step+h>bottom)return;
        boolean conflict=editor.projectDraft() && remote>base;
        editor.sharedButton(x,y+3*step,w,AtlasPlanEditor.text(conflict?"shared_fork":editor.projectDraft()?"shared_commit":"shared_publish"),
                ()->publish(conflict)).active=pending==null && authorized(Util.getMillis()) && canPublish && editor.current().complete() &&
                (!editor.projectDraft() || editable || conflict);
    }
    Component info(){
        if(!status.isEmpty())return AtlasPlanEditor.text(status);
        if(project==null)return AtlasPlanEditor.text("shared_pick");
        return AtlasPlanEditor.text("shared_versions",base,remote);
    }
    private void cycleProject(){
        if(!saveLocal())return;
        var list=projects();if(list.isEmpty())return;
        int n=0;while(n<list.size() && !list.get(n).id().equals(project))n++;
        var id=list.get(n==list.size()?0:(n+1)%list.size()).id();
        reset();project=id;send(ProjectPlanRequest.LIST,0,null,false);
    }
    private void cyclePlan(){
        if(rows.isEmpty())return;
        int i=0;while(i<rows.size() && !rows.get(i).getUUID("Id").equals(selected))i++;
        selected=rows.get(i==rows.size()?0:(i+1)%rows.size()).getUUID("Id");editor.sharedRefresh();
    }
    private void open(){
        if(selected==null || pending!=null || !saveLocal())return;
        send(selected,0,null,true);
    }
    private void personal(){
        if(!saveLocal())return;
        // A late read or write receipt still belongs to the document the player just left.
        pending=null;submitted=null;opening=false;
        editor.discardProjectDraft();original=null;base=remote=0;editable=false;status="";editor.sharedPage();
    }
    private void publish(boolean fork){
        long now=Util.getMillis();
        if(!authorized(now)||!canPublish||pending!=null)return;
        var doc=editor.current();
        if(!doc.complete()||!saveLocal())return;
        submitted=doc;
        if(fork)doc=new EngineeringPlan(UUID.randomUUID(),doc.dimension(),doc.kind(),doc.name(),doc.note(),doc.nodes());
        send(doc.id(),editor.projectDraft()&&!fork?base:0,doc,false);
    }
    private void send(UUID id,long expected,EngineeringPlan doc,boolean open){
        if(project==null || !supported())return;
        opening=open;sent=Util.getMillis();epoch=CityPublicDirectory.accessEpoch();
        pending=new ProjectPlanRequest(UUID.randomUUID(),project,id,expected,doc);recipient=new WeakReference<>(this);
        PacketDistributor.sendToServer(pending);
        if(doc!=null || open || confirmed==0)status="shared_wait";
        editor.sharedRefresh();
    }
    void tick(boolean visible){
        long now=Util.getMillis();
        if(project==null){
            if(visible && emptyPicker!=projects().isEmpty())editor.sharedRefresh();
            return;
        }
        if(epoch!=CityPublicDirectory.accessEpoch() || projectName().isEmpty()){
            saveLocal();reset();status="shared_permission";editor.sharedRefresh();return;
        }
        if(confirmed>0 && !authorized(now)){saveLocal();reset();status="shared_expired";editor.sharedRefresh();return;}
        if(pending!=null && now-sent>=6000){pending=null;opening=false;status="shared_timeout";editor.sharedRefresh();}
        if((visible || editor.projectDraft()) && pending==null && now-sent>=3500)
            send(editor.projectDraft()?editor.current().id():ProjectPlanRequest.LIST,0,null,false);
    }
    static void receive(CompoundTag data){
        var self=recipient.get();if(self!=null)self.accept(data);
    }
    private void accept(CompoundTag data){
        if(pending==null || !data.hasUUID("Request") || !data.getUUID("Request").equals(pending.request()))return;
        var req=pending;pending=null;
        if(epoch!=CityPublicDirectory.accessEpoch() || projectName().isEmpty()){saveLocal();reset();status="shared_permission";editor.sharedRefresh();return;}
        String code=data.getString("Result");
        if(!code.equals("ok") && !code.equals("saved")){
            if(code.equals("permission") || code.equals("missing") || code.equals("incompatible")){saveLocal();reset();}
            status=switch(code){case "conflict"->"shared_conflict";case "permission"->"shared_permission";
                case "closed"->"shared_closed";case "bounds"->"shared_bounds";case "limit"->"shared_limit";
                case "busy"->"shared_busy";case "incompatible"->"shared_unsupported";default->"shared_missing";};
            editor.sharedRefresh();return;
        }
        long now=Util.getMillis();
        if(!data.hasUUID("Project") || !data.getUUID("Project").equals(project) || now-sent>=6000)return;
        sentAtConfirmation=sent;confirmed=now;canPublish=data.getBoolean("CanPublish");
        var indices=data.getList("Rows",Tag.TAG_COMPOUND);var next=new ArrayList<CompoundTag>();
        for(int i=0;i<Math.min(indices.size(),16);i++)if(indices.getCompound(i).hasUUID("Id"))next.add(indices.getCompound(i));
        rows=List.copyOf(next);
        if(rows.stream().noneMatch(r->r.getUUID("Id").equals(selected)))selected=rows.isEmpty()?null:rows.getFirst().getUUID("Id");
        if(!data.contains("Document")){status="";editor.sharedRefresh();return;}
        var version=ProjectPlanVersion.load(data.getCompound("Document"));
        if(!version.project().equals(project)||!version.plan().id().equals(req.plan())||!editor.map.dimensionId().equals(version.plan().dimension()))return;
        editable=data.getBoolean("Editable");remote=version.version();
        if(opening || req.changes()!=null){
            opening=false;selected=version.plan().id();
            var working=version.plan();long expected=remote;
            try {
                if(req.changes()!=null){
                    if(editor.projectDraft()){
                        drafts.clear(project,submitted.id());
                        if(!submitted.equals(editor.current()))working=new EngineeringPlan(working.id(),working.dimension(),editor.current().kind(),
                                editor.current().name(),editor.current().note(),editor.current().nodes());
                    }
                    status="shared_saved";CommunityFx.sound("map_ok");
                } else {
                    var backup=drafts.read(project,working.id());
                    if(backup!=null){working=backup.plan();expected=backup.base();}
                    status=backup==null?"":"shared_restored";
                }
            }catch(IOException failed){status="shared_save_failed";}
            original=version.plan();base=expected;editor.loadProjectDraft(working);
            if(req.changes()!=null || expected<remote)editor.sharedPage();
        }
        if(remote>base && editor.projectDraft())status="shared_conflict";
        editor.sharedRefresh();
    }
}
