package com.example.examplemod.client.map;

import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.railway.RailwayOwnershipTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class WorldMapV2Check {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var segment=new RailwayMapPayload.TrackSegment(0,0,1,1);
        List<RailwayMapPayload.TrackSegment> base=Collections.nCopies(RailwayMapPayload.MAX_TRACK_SEGMENTS,segment);
        List<RailwayMapPayload.TrackSegment> semantic=Collections.nCopies(RailwayMapPayload.MAX_SEMANTIC_SEGMENTS,segment);
        UUID network=UUID.randomUUID();
        var payload=new RailwayMapPayload(BlockPos.ZERO,ResourceLocation.parse("minecraft:overworld"),base,
                List.of(new RailwayMapPayload.NetworkView(network,"Main",RailwayOwnershipTypes.OwnerType.CITY,
                        UUID.randomUUID(),"City",RailwayOwnershipTypes.Access.PUBLIC,500,1200,1,1)),
                List.of(new RailwayMapPayload.SectionView(UUID.randomUUID(),network,1200,
                        RailwayOwnershipTypes.SectionStatus.ONLINE,semantic)),
                List.of(new RailwayMapPayload.RouteView(UUID.randomUUID(),UUID.randomUUID(),true,"selected",
                        1200,600,2400,semantic)));
        require(payload.segments().size()==RailwayMapPayload.MAX_TRACK_SEGMENTS,"base segment cap model");
        require(payload.sections().getFirst().segments().size()==RailwayMapPayload.MAX_SEMANTIC_SEGMENTS,"semantic cap model");
        require(payload.estimatedBytes()<700_000,"bounded map payload estimate");
        require(payload.networks().getFirst().access()==RailwayOwnershipTypes.Access.PUBLIC,"network metadata retained");
        System.out.println("PASS: "+checks+" World Map v2 payload bound and layer checks");
    }
}
