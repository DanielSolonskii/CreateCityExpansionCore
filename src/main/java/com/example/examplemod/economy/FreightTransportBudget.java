package com.example.examplemod.economy;
import net.minecraft.nbt.*;
import java.util.*;
/** A spending permission over existing transport escrow, never a second CR balance. */
public final class FreightTransportBudget {
    private long limit,spent,reserved;
    private final Set<UUID> settled=new HashSet<>();
    private final Map<UUID,Long> trips=new LinkedHashMap<>();
    public long limit(){return limit;}public long spent(){return spent;}public long reserved(){return reserved;}
    public long remaining(){return Math.max(0,limit-spent-reserved);}
    public boolean limit(long value){if(value<0||value<spent||value-spent<reserved)return false;limit=value;return true;}
    public boolean canReserve(long value){return value>=0&&value<=remaining();}
    public boolean reserve(UUID trip,long value){if(settled.contains(trip))return false;if(trips.containsKey(trip))return Objects.equals(trips.get(trip),value);if(!canReserve(value))return false;trips.put(trip,value);reserved+=value;return true;}
    public boolean settle(UUID trip,long paid){Long amount=trips.get(trip);if(amount==null||paid<0||paid>amount)return false;trips.remove(trip);settled.add(trip);reserved-=amount;spent=Math.addExact(spent,paid);return true;}
    public CompoundTag save(){var t=new CompoundTag();t.putLong("Limit",limit);t.putLong("Spent",spent);var list=new ListTag();trips.forEach((id,value)->{var r=new CompoundTag();r.putUUID("Id",id);r.putLong("Reserved",value);list.add(r);});t.put("Trips",list);var done=new ListTag();settled.forEach(id->{var r=new CompoundTag();r.putUUID("Id",id);done.add(r);});t.put("Settled",done);return t;}
    public static FreightTransportBudget load(CompoundTag t){var b=new FreightTransportBudget();b.limit=Math.max(0,t.getLong("Limit"));b.spent=Math.max(0,t.getLong("Spent"));for(Tag raw:t.getList("Trips",10)){var r=(CompoundTag)raw;if(r.hasUUID("Id")&&r.getLong("Reserved")>=0&&!b.trips.containsKey(r.getUUID("Id"))){b.trips.put(r.getUUID("Id"),r.getLong("Reserved"));b.reserved=Math.addExact(b.reserved,r.getLong("Reserved"));}}for(Tag raw:t.getList("Settled",10)){var r=(CompoundTag)raw;if(r.hasUUID("Id"))b.settled.add(r.getUUID("Id"));}if(b.spent>b.limit||b.reserved>b.limit-b.spent)throw new IllegalStateException("Invalid freight transport limit");return b;}
}
