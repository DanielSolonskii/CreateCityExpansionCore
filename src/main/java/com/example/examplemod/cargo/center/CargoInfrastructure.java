package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Resolved physical references never force chunks or accept client-provided coordinates. */
public final class CargoInfrastructure {
    private CargoInfrastructure() {}
    public record Location(ResourceLocation dimension, BlockPos pos) {
        public Location { Objects.requireNonNull(dimension); pos=Objects.requireNonNull(pos).immutable(); }
        public CompoundTag save(){var t=new CompoundTag();t.putString("Dimension",dimension.toString());t.putLong("Pos",pos.asLong());return t;}
        public static Location load(CompoundTag t){if(!t.contains("Pos",4))throw new IllegalArgumentException("Position required");return new Location(ResourceLocation.parse(t.getString("Dimension")),BlockPos.of(t.getLong("Pos")));}
        public BlockEntity loaded(MinecraftServer server){var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));return level!=null&&level.hasChunkAt(pos)?level.getBlockEntity(pos):null;}
    }
    public static BlockEntity lookedAt(ServerPlayer player) {
        var hit=player.serverLevel().clip(new ClipContext(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK&&player.serverLevel().hasChunkAt(hit.getBlockPos())?player.serverLevel().getBlockEntity(hit.getBlockPos()):null;
    }
    public static CargoRouterBlockEntity router(MinecraftServer server,CargoPointRegistry.RouterRef ref){
        var be=new Location(ref.dimension(),ref.pos()).loaded(server);
        return be instanceof CargoRouterBlockEntity router&&!router.isRemoved()&&router.reference().equals(ref)?router:null;
    }
    public static String name(String value){if(value==null||value.isBlank()||value.strip().length()>96)throw new IllegalArgumentException("Invalid name");return value.strip();}
}
