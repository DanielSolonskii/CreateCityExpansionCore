package com.example.examplemod.city;

import com.example.examplemod.network.GazetteEditionPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class GazetteArchiveCheck {
    public static void run() {
        var manager = new CityManager();
        UUID oldMayor = UUID.randomUUID(), newMayor = UUID.randomUUID();
        var city = manager.createCity("Port",oldMayor,BlockPos.ZERO);
        var rural = manager.createCity("Vale",UUID.randomUUID(),new BlockPos(1000,64,0));
        var audit = new CompoundTag();
        audit.putUUID("City",city.getId());
        audit.putUUID("Actor",oldMayor);
        audit.putUUID("Target",newMayor);
        audit.putLong("Time",1000);
        audit.putString("Action","ownership_transferred");
        var paper = new CityGazette();
        paper.importArchive(manager,List.of(audit),id -> id.equals(oldMayor) ? "Alex" : "Sam");
        var mayor = paper.edition(city.getId()).getFirst();
        if (!mayor.equals(new CityGazette.Story(CityGazette.Kind.LEADER,"Alex","Port","Sam",1000))
                || paper.edition(rural.getId()).getFirst().kind()!=CityGazette.Kind.CITY_REGISTER)
            throw new AssertionError("dated archive and undated register");
        paper = CityGazette.load(paper.save(new CompoundTag(),null),null);
        paper.importArchive(manager,List.of(audit),id -> "unused");
        if (paper.edition(city.getId()).size()!=1) throw new AssertionError("archive imported twice");
        paper.publish(CityGazette.Kind.FIRST_SERVICE,null,UUID.randomUUID(),"Coast","Port","Vale",2000);
        paper.publish(CityGazette.Kind.ALLY,city.getId(),oldMayor,"Port","League","4",3000);
        paper.publish(CityGazette.Kind.LEVEL,city.getId(),oldMayor,"Port","2","2",4000);
        if (paper.edition(rural.getId()).size()!=3) throw new AssertionError("edition scope");
        var payload = new GazetteEditionPayload.V3(new GazetteEditionPayload(1,paper.edition(city.getId())));
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            GazetteEditionPayload.V3.CODEC.encode(wire,payload);
            if (!payload.equals(GazetteEditionPayload.V3.CODEC.decode(wire))) throw new AssertionError("story details lost");
        } finally { wire.release(); }
    }
}
