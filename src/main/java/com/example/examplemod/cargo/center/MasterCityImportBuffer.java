package com.example.examplemod.cargo.center;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;
import java.util.*;

/** One chunk-persisted unit: physical contents, receipt and the automation lock. */
public final class MasterCityImportBuffer implements IItemHandler {
    private final Runnable dirty;
    private final ItemStackHandler contents;
    private final MasterCityImportReceipts custody=new MasterCityImportReceipts();
    public MasterCityImportBuffer(int slots,Runnable dirty){if(slots<1||slots>4096)throw new IllegalArgumentException("Buffer slots");this.dirty=dirty;contents=new ItemStackHandler(slots){@Override protected void onContentsChanged(int slot){dirty.run();}};}
    public boolean locked(){return custody.locked();} public UUID pending(){return custody.pending();} public String problem(){return custody.problem();}
    public int capacity(ItemStack item){if(locked())return 0;return item.getCount()-ItemHandlerHelper.insertItemStacked(contents,item,true).getCount();}
    public CompoundTag receipt(UUID id){return custody.receipt(id);}
    public boolean durable(UUID id){return custody.durable(id);}public void durable(UUID id,boolean value){custody.durable(id,value);dirty.run();}
    public void create(UUID id,ItemStack item,CompoundTag exactItem){var existing=custody.receipt(id);if(!existing.isEmpty()){custody.begin(id,exactItem,item.getCount());return;}if(locked()||item.isEmpty()||capacity(item)!=item.getCount())throw new IllegalArgumentException("master_buffer_full_or_locked");custody.begin(id,exactItem,item.getCount());var left=ItemHandlerHelper.insertItemStacked(contents,item,false);if(!left.isEmpty())custody.partial(id,item.getCount()-left.getCount());dirty.run();}
    public void release(UUID id){custody.release(id);dirty.run();}
    public void quarantine(String reason){custody.quarantine(reason);dirty.run();}
    public void resolve(UUID id,String reason){custody.resolve(id,reason);dirty.run();}
    public List<ItemStack> drainUnlocked(){if(locked())return List.of();var items=new ArrayList<ItemStack>();for(int slot=0;slot<contents.getSlots();slot++){var item=contents.extractItem(slot,Integer.MAX_VALUE,false);if(!item.isEmpty())items.add(item);}return items;}
    @Override public int getSlots(){return contents.getSlots();}
    @Override public ItemStack getStackInSlot(int slot){return locked()?ItemStack.EMPTY:contents.getStackInSlot(slot).copy();}
    @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return locked()?stack.copy():contents.insertItem(slot,stack,simulate);}
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return locked()?ItemStack.EMPTY:contents.extractItem(slot,amount,simulate);}
    @Override public int getSlotLimit(int slot){return contents.getSlotLimit(slot);}
    @Override public boolean isItemValid(int slot,ItemStack stack){return !locked()&&contents.isItemValid(slot,stack);}
    public CompoundTag save(HolderLookup.Provider registries){var t=new CompoundTag();t.putInt("Version",1);t.put("Contents",contents.serializeNBT(registries));t.put("Custody",custody.save());return t;}
    public void load(CompoundTag t,HolderLookup.Provider registries){if(t.isEmpty())return;if(t.getInt("Version")!=1)throw new IllegalArgumentException("Customs buffer version");contents.deserializeNBT(registries,t.getCompound("Contents"));custody.load(t.getCompound("Custody"));}
}
