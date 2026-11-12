package com.example.examplemod.client.logistics;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Real presentation transitions: stale sessions, explicit saving, timeout and scaled inputs. */
final class EquipmentWizardCheck {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static CompoundTag ack(UUID request,String result){var t=new CompoundTag();t.putUUID("Request",request);t.putString("Result",result);return t;}
    private static CompoundTag started(UUID request,UUID session){var t=ack(request,"ok");t.putBoolean("PickingEquipment",true);t.putUUID("EquipmentSession",session);return t;}
    private static CompoundTag pair(UUID session){
        var t=new CompoundTag();t.putBoolean("EquipmentSelection",true);t.putUUID("EquipmentSession",session);t.putUUID("EquipmentToken",session);
        var location=new CompoundTag();location.putString("Dimension","minecraft:overworld");location.putLong("Pos",1234);
        t.put("EquipmentPackager",location.copy());t.put("EquipmentPostbox",location.copy());return t;
    }
    private static void layout(){
        for(int[] size:new int[][]{{240,135},{320,180},{426,240},{640,360},{854,480},{960,540},{1280,720},{1920,1080}}){
            var l=EquipmentWizardLayout.of(size[0],size[1]);
            require(l.left()>=7.99&&l.top()>=7.99,"wizard stays inside scaled screen margins");
            require(l.screenX(EquipmentWizardLayout.WIDTH)<=size[0]-7.99&&l.screenY(EquipmentWizardLayout.HEIGHT)<=size[1]-7.99,"whole wizard fits native GUI scales");
            for(var r:List.of(EquipmentWizardLayout.CLOSE,EquipmentWizardLayout.RESELECT,EquipmentWizardLayout.PRIMARY)){
                double sx=l.screenX(r.x()+r.width()/2d),sy=l.screenY(r.y()+r.height()/2d);
                require(r.contains(l.x(sx),l.y(sy)),"painted button center and input mapping agree");
                require(r.bottom()<=EquipmentWizardLayout.HEIGHT&&r.right()<=EquipmentWizardLayout.WIDTH,"footer controls stay inside canvas");
            }
            require(EquipmentWizardLayout.MAIN.right()<EquipmentWizardLayout.SIDE.x(),"illustration and diagnostics panels do not overlap");
            require(EquipmentWizardLayout.RESELECT.right()<EquipmentWizardLayout.PRIMARY.x(),"secondary and primary actions do not overlap");
        }
    }
    private static void transitions(){
        var f=new EquipmentWizardFlow();UUID request=UUID.randomUUID(),session=UUID.randomUUID();
        require(f.stage()==EquipmentWizardFlow.Stage.PREPARE&&!f.canSave(),"initial page cannot save a guessed pair");
        f.request(EquipmentWizardFlow.Operation.START,request,1000);
        require(f.response(started(UUID.randomUUID(),session))==EquipmentWizardFlow.Event.IGNORE&&f.busy(),"unrelated server replies cannot start the picker");
        require(!f.timeout(12999)&&f.timeout(13000)&&f.uncertain(),"timeout has a real deadline and no automatic retry");
        require(f.response(started(request,session))==EquipmentWizardFlow.Event.WORLD&&f.canResume(),"late valid acknowledgement can resume the original request");
        var progress=new CompoundTag();progress.putBoolean("EquipmentProgress",true);progress.putUUID("EquipmentSession",session);progress.putInt("EquipmentStep",1);progress.putString("EquipmentHint","ambiguous");
        require(f.progress(progress)&&f.pickStep()==1&&f.hint().equals("ambiguous")&&!f.canSave(),"postbox prompt and ambiguity do not imply a complete pair");
        require(f.selection(pair(UUID.randomUUID()))==EquipmentWizardFlow.Event.IGNORE,"old picker session cannot replace the current selection");
        var wrongToken=pair(session);wrongToken.putUUID("EquipmentToken",UUID.randomUUID());
        require(f.selection(wrongToken)==EquipmentWizardFlow.Event.IGNORE,"selection token must belong to the acknowledged session");
        var chosen=pair(session);require(f.selection(chosen)==EquipmentWizardFlow.Event.REVIEW&&f.canSave(),"selected pair becomes reviewable, not saved");
        chosen.remove("EquipmentToken");require(f.canSave(),"incoming network data is copied rather than retained by reference");
        var extracted=f.equipment();extracted.remove("EquipmentToken");require(f.canSave(),"view cannot mutate the stored selection");
        UUID save=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.SAVE,save,20000);
        require(f.stage()==EquipmentWizardFlow.Stage.REVIEW&&!f.canSave()&&f.busy(),"saving remains pending until acknowledgement");
        boolean refused=false;try{f.request(EquipmentWizardFlow.Operation.SAVE,UUID.randomUUID(),20001);}catch(IllegalStateException expected){refused=true;}
        require(refused,"double click cannot schedule another save while the first one is pending");
        require(f.response(ack(save,"denied: setup_receiver_invalid"))==EquipmentWizardFlow.Event.ERROR&&f.stage()==EquipmentWizardFlow.Stage.REVIEW&&f.canSave(),"failed validation keeps the reviewed pair and exposes an actionable refusal");
        save=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.SAVE,save,30000);
        require(f.response(ack(save,"ok"))==EquipmentWizardFlow.Event.SAVED&&f.stage()==EquipmentWizardFlow.Stage.DONE,"success is shown only for the explicit matching save");
        require(f.selection(pair(session))==EquipmentWizardFlow.Event.IGNORE&&!f.canSave(),"late selection cannot reopen a saved connection");
    }
    private static void cancellation(){
        var f=new EquipmentWizardFlow();UUID start=UUID.randomUUID(),session=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.START,start,0);f.response(started(start,session));f.selection(pair(session));
        UUID restart=UUID.randomUUID(),second=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.START,restart,1000);
        require(!f.canSave()&&f.equipment().isEmpty(),"selecting again immediately discards the stale confirmation token");
        f.response(started(restart,second));require(f.selection(pair(session))==EquipmentWizardFlow.Event.IGNORE,"a previous selection cannot race a restarted wizard");
        var cancelled=new CompoundTag();cancelled.putBoolean("EquipmentSelection",true);cancelled.putBoolean("EquipmentCancelled",true);cancelled.putUUID("EquipmentSession",second);cancelled.putString("EquipmentHint","expired");
        require(f.selection(cancelled)==EquipmentWizardFlow.Event.CANCELLED&&f.stage()==EquipmentWizardFlow.Stage.PREPARE&&f.hint().equals("expired"),"server expiry resets selection and explains why");
        start=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.START,start,2000);f.response(started(start,second));
        UUID cancel=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.CANCEL,cancel,2100);
        require(f.selection(pair(second))==EquipmentWizardFlow.Event.IGNORE,"a world click cannot overtake an explicit cancellation");
        require(f.response(ack(cancel,"ok"))==EquipmentWizardFlow.Event.CANCELLED&&!f.canResume(),"cancel acknowledgement releases the picker session");
        require(f.selection(pair(second))==EquipmentWizardFlow.Event.IGNORE,"cancelled session cannot reopen the wizard");
        start=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.START,start,3000);f.response(started(start,session));f.selection(pair(session));
        UUID save=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.SAVE,save,4000);f.timeout(16000);
        cancel=UUID.randomUUID();f.request(EquipmentWizardFlow.Operation.CANCEL,cancel,17000);
        require(f.response(ack(save,"ok"))==EquipmentWizardFlow.Event.IGNORE,"late save acknowledgement cannot overwrite the current cancellation state");
        require(f.response(ack(cancel,"ok"))==EquipmentWizardFlow.Event.CANCELLED&&f.equipment().isEmpty(),"safe explicit cancellation remains available after timeout");
        var broken=new EquipmentWizardFlow();UUID request=UUID.randomUUID();broken.request(EquipmentWizardFlow.Operation.START,request,0);
        require(broken.response(ack(request,"ok"))==EquipmentWizardFlow.Event.ERROR&&!broken.canResume(),"missing server session is a refusal, never an optimistic picker");
        require(broken.needsCancel(),"an ambiguous start must still be explicitly cancelled when closing");
        UUID cleanup=UUID.randomUUID();broken.request(EquipmentWizardFlow.Operation.CANCEL,cleanup,200);
        broken.response(ack(cleanup,"ok"));require(!broken.needsCancel(),"successful cleanup releases even an unacknowledged picker");
        var restartFailure=new EquipmentWizardFlow();start=UUID.randomUUID();restartFailure.request(EquipmentWizardFlow.Operation.START,start,0);restartFailure.response(started(start,session));
        restart=UUID.randomUUID();restartFailure.request(EquipmentWizardFlow.Operation.START,restart,50);restartFailure.response(ack(restart,"rate_limit"));
        require(restartFailure.needsCancel()&&!restartFailure.canSave(),"failed reselect retains cleanup obligation for the previous server picker");
    }
    static void run(){layout();transitions();cancellation();System.out.println("PASS: "+checks+" equipment wizard layout, correlation, cancellation and confirmation checks");}
}
