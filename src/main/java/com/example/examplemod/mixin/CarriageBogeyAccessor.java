package com.example.examplemod.mixin;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimal accessor for the single package-private Create value required by route compatibility checks. */
@Mixin(CarriageBogey.class)
public interface CarriageBogeyAccessor {
    @Accessor("type")
    AbstractBogeyBlock<?> ccec$getType();
}
