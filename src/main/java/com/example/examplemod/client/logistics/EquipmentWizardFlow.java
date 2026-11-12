package com.example.examplemod.client.logistics;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Presentation state only. A selected pair is never treated as a saved connection. */
final class EquipmentWizardFlow {
    enum Stage { PREPARE, SELECT, REVIEW, DONE }
    enum Operation { START, SAVE, CANCEL }
    enum Event { IGNORE, WORLD, REVIEW, SAVED, CANCELLED, ERROR }
    private Stage stage=Stage.PREPARE;
    private Operation operation;
    private UUID pending,session;
    private long sent;
    private boolean uncertain,selectionRequested;
    private int pickStep;
    private String result="",hint="";
    private CompoundTag equipment=new CompoundTag(),packager=new CompoundTag();
    Stage stage(){return stage;}
    Operation operation(){return operation;}
    UUID pending(){return pending;}
    boolean busy(){return pending!=null;}
    boolean uncertain(){return uncertain;}
    boolean needsCancel(){return selectionRequested;}
    int pickStep(){return pickStep;}
    String result(){return result;}
    String hint(){return hint;}
    CompoundTag equipment(){return equipment.copy();}
    CompoundTag packager(){return packager.copy();}
    boolean canSave(){return stage==Stage.REVIEW&&!busy()&&!uncertain&&equipment.hasUUID("EquipmentToken");}
    boolean canResume(){return stage==Stage.SELECT&&session!=null&&!busy()&&!uncertain;}
    void request(Operation next,UUID request,long now){
        if(request==null||busy()&&!(uncertain&&next==Operation.CANCEL))throw new IllegalStateException("Request already pending");
        if(next==Operation.SAVE&&!canSave())throw new IllegalStateException("No reviewed pair");
        if(next==Operation.START){selectionRequested=true;equipment=new CompoundTag();packager=new CompoundTag();session=null;stage=Stage.PREPARE;pickStep=0;hint="";}
        operation=next;pending=request;sent=now;uncertain=false;result="";
    }
    boolean timeout(long now){
        if(busy()&&!uncertain&&CenterPresentation.timedOut(sent,now)){uncertain=true;return true;}
        return false;
    }
    boolean matches(CompoundTag data){return session!=null&&data.hasUUID("EquipmentSession")&&session.equals(data.getUUID("EquipmentSession"));}
    Event response(CompoundTag data){
        if(pending==null||!data.hasUUID("Request")||!pending.equals(data.getUUID("Request")))return Event.IGNORE;
        Operation completed=operation;pending=null;operation=null;uncertain=false;
        if(!data.getString("Result").equals("ok")){result=data.getString("Result");return Event.ERROR;}
        if(completed==Operation.CANCEL){clear();return Event.CANCELLED;}
        if(completed==Operation.SAVE){stage=Stage.DONE;session=null;selectionRequested=false;hint="";return Event.SAVED;}
        if(!data.getBoolean("PickingEquipment")||!data.hasUUID("EquipmentSession")){
            result="denied: setup_selection_changed";return Event.ERROR;
        }
        session=data.getUUID("EquipmentSession");stage=Stage.SELECT;return Event.WORLD;
    }
    boolean progress(CompoundTag data){
        if(!matches(data)||stage!=Stage.SELECT||!data.getBoolean("EquipmentProgress"))return false;
        pickStep=Math.clamp(data.getInt("EquipmentStep"),0,1);hint=data.getString("EquipmentHint");
        if(data.contains("EquipmentPackager"))packager=data.getCompound("EquipmentPackager").copy();
        return true;
    }
    Event selection(CompoundTag data){
        if(!matches(data)||stage!=Stage.SELECT||busy()||!data.getBoolean("EquipmentSelection"))return Event.IGNORE;
        if(data.getBoolean("EquipmentCancelled")){String reason=data.getString("EquipmentHint");clear();hint=reason;return Event.CANCELLED;}
        if(!data.hasUUID("EquipmentToken")||!session.equals(data.getUUID("EquipmentToken"))
                ||!data.contains("EquipmentPackager")||!data.contains("EquipmentPostbox"))return Event.IGNORE;
        equipment=data.copy();packager=data.getCompound("EquipmentPackager").copy();stage=Stage.REVIEW;pickStep=2;hint="";
        return Event.REVIEW;
    }
    private void clear(){stage=Stage.PREPARE;pending=session=null;operation=null;equipment=new CompoundTag();packager=new CompoundTag();hint=result="";pickStep=0;uncertain=selectionRequested=false;}
}
