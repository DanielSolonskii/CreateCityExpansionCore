package com.example.examplemod.client.status;

import com.example.examplemod.economy.FreightPoint;
import com.example.examplemod.economy.FreightPointRules;
import com.example.examplemod.status.CcecStatus;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.railway.RailwayOwnershipSnapshot;
import com.example.examplemod.railway.RailwayTrainSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/** Read-only client projection of persistent, server-authoritative problems. */
public final class CcecProblemCenter {
    private CcecProblemCenter() {}

    public static int count(EconomyStatePayload economy, List<RailwayOwnershipSnapshot> ownership,
                            List<RailwayTrainSnapshot> trains) {
        if (economy == null) return 1;
        int result = 0;
        for (Tag raw : economy.logistics().getList("Points", Tag.TAG_COMPOUND)) {
            try { if (isProblem(FreightPointRules.ui(FreightPoint.load((CompoundTag) raw).status), true)) result++; }
            catch (RuntimeException ignored) { result++; }
        }
        for (Tag raw : economy.logistics().getList("Assignments", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            if (!value.contains("UiStatus", Tag.TAG_COMPOUND)) continue;
            CcecStatus status=CcecStatus.load(value.getCompound("UiStatus"));
            if (isProblem(status,value.getBoolean("Outgoing"))) result++;
        }
        if (ownership != null) result += ownership.stream()
                .mapToInt(network -> Math.max(0, network.sections() - network.onlineSections())).sum();
        result += (int) trains.stream().filter(train -> train.automationSuspended()
                || "route_invalidated".equals(train.statusKey())
                || "insufficient_funds".equals(train.statusKey())).count();
        return result;
    }

    /** Incoming cargo waiting at its sender is normal; actionable outgoing setup is not. */
    public static boolean isProblem(CcecStatus status, boolean outgoing) {
        if (status == null) return true;
        if (status.category() == CcecStatus.Category.BLOCKED
                || status.category() == CcecStatus.Category.WARNING) return true;
        return outgoing && status.category() == CcecStatus.Category.ACTION_REQUIRED
                && status.code() != CcecStatus.Code.IN_TRANSIT;
    }

    public static String nextStepKey(EconomyStatePayload state, int problemCount) {
        if (state == null) return "gui.createcityexpansioncore.city.hub.next.loading";
        if (state.logistics().getList("Points", Tag.TAG_COMPOUND).isEmpty())
            return "gui.createcityexpansioncore.city.hub.next.freight";
        if (problemCount > 0) return "gui.createcityexpansioncore.city.hub.next.problem";
        if (state.activeObligations() > 0) return "gui.createcityexpansioncore.city.hub.next.shipment";
        return "gui.createcityexpansioncore.city.hub.next.market";
    }
}
