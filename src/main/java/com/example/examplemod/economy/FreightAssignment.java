package com.example.examplemod.economy;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** One assignment per trade; source may change without changing in-flight token destinations. */
public final class FreightAssignment {
    public final UUID subject, destination;
    public final String address;
    public UUID source, train, routeSelection;
    public String dispatchError="";
    public boolean running;
    /**
     * Set only from positive, server-authoritative evidence that the cargo reached the selected
     * train (detected in its cargo, or a trip that could only have started once the cargo was
     * loaded) and cleared when nothing of it is in the air any more. While it is set the source's
     * availability only gates packages that are still to come — it must never be reported against
     * the shipment that has already gone. A source we cannot inspect is never treated as proof;
     * see {@code FreightLogistics.handoff}.
     */
    public boolean handedOff;
    /**
     * Why the assigned train has not left yet: one of the two gates that used to be completely
     * silent, {@code "cargo_not_counted"} (the train is at the source station but the outstanding
     * cargo is not in it) or {@code "train_not_at_source"}. Empty means "nothing specific". Pure
     * presentation state, recomputed from the world every tick and deliberately not persisted —
     * a stale reason from a previous session must never be shown.
     */
    public String waitReason="";
    public FreightAssignment(UUID subject,UUID source,FreightPoint destination){this.subject=subject;this.source=source;this.destination=destination.id;this.address=destination.address;}
    private FreightAssignment(CompoundTag t){subject=t.getUUID("Subject");source=t.hasUUID("Source")?t.getUUID("Source"):null;destination=t.getUUID("Destination");address=t.getString("Address");running=t.getBoolean("Running");train=t.hasUUID("Train")?t.getUUID("Train"):null;routeSelection=t.hasUUID("RouteSelection")?t.getUUID("RouteSelection"):null;dispatchError=t.getString("DispatchError");handedOff=t.getBoolean("HandedOff");}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("Subject",subject);if(source!=null)t.putUUID("Source",source);t.putUUID("Destination",destination);t.putString("Address",address);t.putBoolean("Running",running);if(handedOff)t.putBoolean("HandedOff",true);if(train!=null)t.putUUID("Train",train);if(routeSelection!=null)t.putUUID("RouteSelection",routeSelection);if(!dispatchError.isBlank())t.putString("DispatchError",dispatchError);return t;}
    public static FreightAssignment load(CompoundTag t){return new FreightAssignment(t);}
}
