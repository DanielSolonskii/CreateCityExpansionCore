package com.example.examplemod.block;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.network.TransportOutlinePayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Optional Sable bridge, reached only after the compatibility guard. */
public final class TransportOutlineServer {
    public static List<TransportOutlinePayload.Entry> collect(ServerPlayer player){
        var result=new ArrayList<TransportOutlinePayload.Entry>();
        var level=player.serverLevel();var container=SubLevelContainer.getContainer(level);
        if(container==null)return result;
        var manager=CityManager.get(player.getServer().overworld());
        for(var raw:container.getAllSubLevels()){
            if(!(raw instanceof ServerSubLevel sub)||sub.isRemoved())continue;
            // Avoid terminal discovery on distant vessels.
            var bounds=sub.boundingBox();
            if(bounds!=null&&(player.getX()<bounds.minX()-80||player.getX()>bounds.maxX()+80
                    ||player.getY()<bounds.minY()-80||player.getY()>bounds.maxY()+80
                    ||player.getZ()<bounds.minZ()-80||player.getZ()>bounds.maxZ()+80))continue;
            var terminal=TransportSubLevelProtection.outlineTerminal(sub,level);
            if(terminal==null)continue;
            var city=manager.getCity(terminal.cityId());
            if(city==null||!city.hasMember(player.getUUID()))continue;
            for(var grant:manager.transportAccessSnapshot(city,terminal.token()).entrySet()){
                var pos=terminal.pos().offset(grant.getKey());
                if(sub.logicalPose().transformPosition(Vec3.atCenterOf(pos)).distanceToSqr(player.position())>72*72)continue;
                var plot=sub.getPlot();
                var holder=plot.getChunkHolder(plot.toLocal(new net.minecraft.world.level.ChunkPos(pos)));
                if(holder==null||holder.getChunk()==null)continue;
                if(!BuiltInRegistries.BLOCK.getKey(holder.getChunk().getBlockState(pos).getBlock()).equals(grant.getValue()))continue;
                result.add(new TransportOutlinePayload.Entry(sub.getUniqueId(),pos,grant.getValue()));
            }
        }
        result.sort(Comparator.comparing(TransportOutlinePayload.Entry::sublevel).thenComparingLong(e->e.pos().asLong()));
        if(result.size()>TransportOutlinePayload.MAX_ENTRIES)result.subList(TransportOutlinePayload.MAX_ENTRIES,result.size()).clear();
        return result;
    }
}
