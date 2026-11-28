package com.example.examplemod.client;

import com.example.examplemod.network.CityDirectoryPayload;
import com.example.examplemod.network.RequestCityDirectoryPayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Correlates every page before any consumer may change its session or published directory. */
public final class DirectoryPages {
    private final UUID view, request;
    private final ResourceLocation requestedDimension;
    private UUID token, world, member;
    private ResourceLocation dimension;
    private BlockPos anchor;
    private int expected, total;
    private boolean complete;

    public DirectoryPages(UUID view, UUID request, ResourceLocation requestedDimension) {
        this.view = view;
        this.request = request;
        this.requestedDimension = requestedDimension;
    }

    public boolean accept(CityDirectoryPayload p) {
        if (complete || !view.equals(p.viewId()) || !request.equals(p.requestId()) || p.offset() != expected
                || (requestedDimension != null && !requestedDimension.equals(p.dimension()))
                || p.worldId().equals(RequestCityDirectoryPayload.NEW)
                || (p.cities().isEmpty() && p.offset() < p.total())) return false;
        if (token == null) {
            token = p.token();
            world = p.worldId();
            dimension = p.dimension();
            member = p.memberCityId();
            anchor = p.anchor();
            total = p.total();
        } else if (!token.equals(p.token()) || !world.equals(p.worldId()) || !anchor.equals(p.anchor())
                || !member.equals(p.memberCityId()) || !dimension.equals(p.dimension()) || total != p.total()) return false;
        expected += p.cities().size();
        complete = expected == total;
        return true;
    }
}
