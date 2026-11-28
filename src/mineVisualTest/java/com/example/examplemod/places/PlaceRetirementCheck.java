package com.example.examplemod.places;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PlaceRetirementCheck {
    public static void run() {
        var db=new PlaceBook();
        var place=new AtlasPlace(UUID.randomUUID(),UUID.randomUUID(),3,"Station",AtlasPlace.Category.STATION,"Hall",
                ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO,List.of(new BlockPos(2,64,4)),true,
                new AtlasPlace.Source(AtlasPlace.SourceKind.PROJECT,UUID.randomUUID()));
        db.entries.put(place.id(),place);
        var restored=PlaceBook.load(db.save(new CompoundTag(),null),null);
        var record=restored.entries.get(place.id()).save();
        var expected=place.save();
        expected.remove("Source");expected.remove("SourceId");
        if(!restored.isDirty() || !record.equals(expected))throw new AssertionError("Linked place lost data");
        if(PlaceBook.load(restored.save(new CompoundTag(),null),null).isDirty())throw new AssertionError("Repeated place migration");
    }
}
