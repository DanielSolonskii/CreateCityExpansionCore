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
    public FreightAssignment(UUID subject,UUID source,FreightPoint destination){this.subject=subject;this.source=source;this.destination=destination.id;this.address=destination.address;}
    private FreightAssignment(CompoundTag t){subject=t.getUUID("Subject");source=t.hasUUID("Source")?t.getUUID("Source"):null;destination=t.getUUID("Destination");address=t.getString("Address");running=t.getBoolean("Running");train=t.hasUUID("Train")?t.getUUID("Train"):null;routeSelection=t.hasUUID("RouteSelection")?t.getUUID("RouteSelection"):null;dispatchError=t.getString("DispatchError");}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putUUID("Subject",subject);if(source!=null)t.putUUID("Source",source);t.putUUID("Destination",destination);t.putString("Address",address);t.putBoolean("Running",running);if(train!=null)t.putUUID("Train",train);if(routeSelection!=null)t.putUUID("RouteSelection",routeSelection);if(!dispatchError.isBlank())t.putString("DispatchError",dispatchError);return t;}
    public static FreightAssignment load(CompoundTag t){return new FreightAssignment(t);}
}
