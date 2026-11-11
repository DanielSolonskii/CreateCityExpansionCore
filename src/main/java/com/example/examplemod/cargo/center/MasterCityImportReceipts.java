package com.example.examplemod.cargo.center;

import net.minecraft.nbt.*;
import java.util.*;

/** Pure persistent custody state used by every side of the customs item-handler capability. */
public final class MasterCityImportReceipts {
    private final Map<UUID,CompoundTag> receipts=new LinkedHashMap<>();
    private UUID pending;
    private String quarantine="";
    public boolean locked(){return pending!=null||!quarantine.isEmpty();}public UUID pending(){return pending;}public String problem(){return quarantine;}
    public CompoundTag receipt(UUID id){var r=receipts.get(id);return r==null?new CompoundTag():r.copy();}
    public boolean durable(UUID id){var r=receipts.get(id);return r!=null&&r.getBoolean("Durable");}
    public void durable(UUID id,boolean value){var r=receipts.get(id);if(r==null)throw new IllegalArgumentException("master_import_receipt_missing");r.putBoolean("Durable",value);}
    public static boolean matches(CompoundTag receipt,UUID id,CompoundTag item,int quantity){return receipt!=null&&receipt.hasUUID("Id")&&receipt.getUUID("Id").equals(id)&&receipt.getInt("Quantity")==quantity&&receipt.getCompound("Item").equals(item);}
    /** false is a proven replay: callers must not insert physical items again. */
    public boolean begin(UUID id,CompoundTag item,int quantity){if(id==null||item==null||item.isEmpty()||quantity<1)throw new IllegalArgumentException("Invalid import receipt");var old=receipts.get(id);if(old!=null){if(old.getInt("Quantity")!=quantity||!old.getCompound("Item").equals(item))throw new IllegalArgumentException("Import receipt identity conflict");return false;}if(locked()||receipts.size()>=8192)throw new IllegalArgumentException("master_buffer_full_or_locked");var t=new CompoundTag();t.putUUID("Id",id);t.put("Item",item.copy());t.putInt("Quantity",quantity);receipts.put(id,t);pending=id;return true;}
    public void partial(UUID id,int actual){var t=receipts.get(id);if(t==null||!id.equals(pending)||actual<0||actual>t.getInt("Quantity"))throw new IllegalArgumentException("Invalid partial import");t.putInt("Quantity",actual);quarantine="Partial import insertion";}
    public void release(UUID id){if(!receipts.containsKey(id))throw new IllegalArgumentException("master_import_receipt_missing");if(pending!=null&&!pending.equals(id))throw new IllegalArgumentException("master_buffer_other_batch");pending=null;}
    public void quarantine(String reason){quarantine=reason==null?"Import reconciliation required":reason;}
    public void resolve(UUID id,String reason){if(reason==null||reason.isBlank())throw new IllegalArgumentException("master_recovery_reason_required");if(pending!=null&&!pending.equals(id))throw new IllegalArgumentException("master_buffer_other_batch");pending=null;quarantine="";}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("Version",1);if(pending!=null)t.putUUID("Pending",pending);t.putString("Quarantine",quarantine);var list=new ListTag();receipts.values().forEach(r->list.add(r.copy()));t.put("Receipts",list);return t;}
    public void load(CompoundTag t){if(t.isEmpty())return;if(t.getInt("Version")!=1)throw new IllegalArgumentException("Customs receipt version");pending=t.hasUUID("Pending")?t.getUUID("Pending"):null;quarantine=t.getString("Quarantine");receipts.clear();for(Tag raw:t.getList("Receipts",10)){var r=(CompoundTag)raw;if(!r.hasUUID("Id")||r.getInt("Quantity")<0||r.getCompound("Item").isEmpty()||receipts.putIfAbsent(r.getUUID("Id"),r.copy())!=null)throw new IllegalArgumentException("Invalid import buffer receipt");}if(receipts.size()>8192||pending!=null&&!receipts.containsKey(pending))throw new IllegalArgumentException("Missing pending import receipt");}
}
