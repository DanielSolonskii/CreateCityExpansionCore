package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Optional;

final class IndustrialMineOperation {

    private IndustrialMineOperation() {
    }

    /**
     * Выбирает залежь для шахты.
     *
     * Приоритет отдаётся не общему богатству залежи, а её
     * ЛОКАЛЬНОМУ потенциалу в точке стояния шахты: залежь,
     * богатое ядро которой находится в двухстах блоках, не должна
     * обгонять залежь, которую шахта стоит прямо на ней.
     * Это позволяет игроку реально искать богатые участки
     * перемещением шахты, а не гаданием по среднему grade.
     */
    static Selection selectDeposit(
            GeologicalRegion region,
            Optional<ResourceLocation> activeDepositId,
            int mineX,
            int mineZ
    ) {
        if (activeDepositId.isPresent()) {
            Optional<GeologicalDeposit> existing = region.getDeposit(
                    activeDepositId.get()
            );
            if (existing.isPresent()) {
                return new Selection(existing, false);
            }
        }

        Optional<GeologicalDeposit> selected = region.getDeposits()
                .stream()
                .sorted(byLocalPotential(mineX, mineZ))
                .findFirst();

        boolean changed = activeDepositId.isPresent() || selected.isPresent();
        return new Selection(selected, changed);
    }

    /**
     * Совместимый путь без позиции шахты: порядок по богатству.
     */
    static Selection selectDeposit(
            GeologicalRegion region,
            Optional<ResourceLocation> activeDepositId
    ) {
        return selectDeposit(region, activeDepositId, 0, 0);
    }

    private static Comparator<GeologicalDeposit> byLocalPotential(
            int mineX,
            int mineZ
    ) {
        return Comparator
                .<GeologicalDeposit>comparingDouble(
                        deposit -> deposit.localPotentialPerHour(mineX, mineZ)
                )
                .reversed()
                .thenComparing(deposit -> deposit.getId().toString());
    }

    static boolean isDepositSelectable(
            GeologicalRegion region,
            ResourceLocation requestedDepositId
    ) {
        return region.getDeposit(requestedDepositId).isPresent();
    }

    record Selection(
            Optional<GeologicalDeposit> deposit,
            boolean changed
    ) {
    }
}
