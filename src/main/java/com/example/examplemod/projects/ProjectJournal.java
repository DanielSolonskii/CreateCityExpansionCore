package com.example.examplemod.projects;
import net.minecraft.nbt.*;
import java.util.*;
/** Domain metadata saved atomically with the existing project ledger, never a second project lifecycle. */
public final class ProjectJournal {
    public record Event(UUID id,UUID actor,String action,long revision,long time){}
    public static final class Trace {public long created,completed,announced=-1;public UUID acceptedBy;public final List<Event> events=new ArrayList<>();}
    private final Map<UUID,Trace> traces=new HashMap<>();
    public Trace trace(UUID project){return traces.get(project);}
    public void record(UUID project,UUID event,UUID actor,String action,long revision,long now){var trace=traces.computeIfAbsent(project,k->new Trace());if(trace.events.stream().anyMatch(e->e.id.equals(event)))return;trace.events.add(new Event(event,actor,action,revision,now));if(action.equals("CREATE"))trace.created=now;if(action.equals("ACCEPT")){trace.completed=now;trace.acceptedBy=actor;}while(trace.events.size()>64)trace.events.removeFirst();}
    public CompoundTag save(){var root=new CompoundTag();root.putInt("Version",1);var entries=new ListTag();traces.forEach((project,trace)->{var t=new CompoundTag();t.putUUID("Project",project);t.putLong("Created",trace.created);t.putLong("Completed",trace.completed);t.putLong("Announced",trace.announced);if(trace.acceptedBy!=null)t.putUUID("AcceptedBy",trace.acceptedBy);var events=new ListTag();for(var e:trace.events){var n=new CompoundTag();n.putUUID("Id",e.id);n.putUUID("Actor",e.actor);n.putString("Action",e.action);n.putLong("Revision",e.revision);n.putLong("Time",e.time);events.add(n);}t.put("Events",events);entries.add(t);});root.put("Traces",entries);return root;}
    public void load(CompoundTag root){traces.clear();if(root.getInt("Version")>1)throw new IllegalArgumentException("Project journal version");for(Tag raw:root.getList("Traces",10)){var t=(CompoundTag)raw;var trace=new Trace();trace.created=t.getLong("Created");trace.completed=t.getLong("Completed");trace.announced=t.getLong("Announced");trace.acceptedBy=t.hasUUID("AcceptedBy")?t.getUUID("AcceptedBy"):null;for(Tag value:t.getList("Events",10)){var e=(CompoundTag)value;if(trace.events.size()<64)trace.events.add(new Event(e.getUUID("Id"),e.getUUID("Actor"),e.getString("Action"),e.getLong("Revision"),e.getLong("Time")));}traces.put(t.getUUID("Project"),trace);}}
}
