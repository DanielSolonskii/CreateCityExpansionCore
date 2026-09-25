package com.example.examplemod.mine;

import com.example.examplemod.geology.DepositExtractionResult;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalLong;

final class IndustrialMineOperation {

    private static final Comparator<GeologicalDeposit> DEPOSIT_ORDER =
            Comparator.comparingDouble(GeologicalDeposit::getGrade)
                    .reversed()
                    .thenComparing(deposit -> deposit.getId().toString());

    private IndustrialMineOperation() {
    }

    static MineOperationResult inspect(
            boolean assembled,
            String validationMessage,
            Optional<ResourceLocation> activeDepositId,
            MineGeologyAccess geology
    ) {
        MineOperationResult prerequisiteFailure = checkPrerequisites(
                assembled,
                validationMessage,
                0L,
                geology
        );
        if (prerequisiteFailure != null) {
            return prerequisiteFailure;
        }

        GeologicalRegion region = geology.getRegion();
        Selection selection = selectDeposit(region, activeDepositId);
        if (selection.deposit().isEmpty()) {
            return withoutDeposit(
                    IndustrialMineState.NO_DEPOSIT,
                    region.getId(),
                    0L,
                    selection.changed(),
                    "В геологическом регионе нет доступных залежей."
            );
        }

        GeologicalDeposit deposit = selection.deposit().get();
        IndustrialMineState state = deposit.isDepleted()
                ? IndustrialMineState.DEPOSIT_DEPLETED
                : IndustrialMineState.READY;
        String message = deposit.isDepleted()
                ? "Выбранная залежь полностью истощена."
                : "Шахта готова к контролируемой добыче.";

        return withDeposit(
                state,
                region,
                deposit,
                0L,
                0L,
                selection.changed(),
                false,
                message
        );
    }

    static MineOperationResult extract(
            boolean assembled,
            String validationMessage,
            Optional<ResourceLocation> activeDepositId,
            long requestedAmount,
            MineEnergyGate energyGate,
            MineGeologyAccess geology
    ) {
        if (!assembled) {
            return basic(
                    IndustrialMineState.UNFORMED,
                    requestedAmount,
                    validationMessage
            );
        }
        if (requestedAmount <= 0) {
            return basic(
                    IndustrialMineState.INVALID_REQUEST,
                    requestedAmount,
                    "Объём добычи должен быть положительным."
            );
        }
        if (!geology.storageAvailable()) {
            return basic(
                    IndustrialMineState.GEOLOGY_UNAVAILABLE,
                    requestedAmount,
                    "Хранилище геологии недоступно; запасы не изменены."
            );
        }

        GeologicalRegion region = geology.getRegion();
        Selection selection = selectDeposit(region, activeDepositId);
        if (selection.deposit().isEmpty()) {
            return withoutDeposit(
                    IndustrialMineState.NO_DEPOSIT,
                    region.getId(),
                    requestedAmount,
                    selection.changed(),
                    "В геологическом регионе нет залежей для добычи."
            );
        }

        GeologicalDeposit deposit = selection.deposit().get();
        if (deposit.isDepleted()) {
            return withDeposit(
                    IndustrialMineState.DEPOSIT_DEPLETED,
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    false,
                    "Выбранная залежь истощена; запасы не изменены."
            );
        }

        MineEnergyGate.EnergyDecision energyDecision = energyGate.evaluate();
        if (!energyDecision.allowed()) {
            return withDeposit(
                    energyDecision.deniedState(),
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    energyDecision.integrationPending(),
                    energyDecision.message()
            );
        }

        DepositExtractionResult extraction = geology.extract(
                deposit.getId(),
                requestedAmount
        );

        return switch (extraction.status()) {
            case EXTRACTED -> new MineOperationResult(
                    IndustrialMineState.RUNNING,
                    Optional.of(region.getId()),
                    Optional.of(deposit.getId()),
                    Optional.of(deposit.getResourceId()),
                    requestedAmount,
                    extraction.extractedAmount(),
                    extraction.remainingReserve(),
                    extraction.stage(),
                    selection.changed(),
                    energyDecision.integrationPending(),
                    energyDecision.message()
            );
            case DEPLETED -> withDeposit(
                    IndustrialMineState.DEPOSIT_DEPLETED,
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    energyDecision.integrationPending(),
                    "Залежь истощена; запасы не изменены."
            );
            case NOT_FOUND -> withoutDeposit(
                    IndustrialMineState.NO_DEPOSIT,
                    region.getId(),
                    requestedAmount,
                    true,
                    "Выбранная залежь больше не существует."
            );
            case STORAGE_UNAVAILABLE -> basic(
                    IndustrialMineState.GEOLOGY_UNAVAILABLE,
                    requestedAmount,
                    "Хранилище геологии недоступно; запасы не изменены."
            );
            case INVALID_REQUEST, ZERO_REQUEST -> withDeposit(
                    IndustrialMineState.INVALID_REQUEST,
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    energyDecision.integrationPending(),
                    "Геологическая система отклонила объём добычи."
            );
        };
    }

    static Selection selectDeposit(
            GeologicalRegion region,
            Optional<ResourceLocation> activeDepositId
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
                .filter(deposit -> !deposit.isDepleted())
                .sorted(DEPOSIT_ORDER)
                .findFirst();

        if (selected.isEmpty()) {
            selected = region.getDeposits()
                    .stream()
                    .sorted(DEPOSIT_ORDER)
                    .findFirst();
        }

        boolean changed = activeDepositId.isPresent() || selected.isPresent();
        return new Selection(selected, changed);
    }

    static boolean isDepositSelectable(
            GeologicalRegion region,
            ResourceLocation requestedDepositId
    ) {
        return region.getDeposit(requestedDepositId).isPresent();
    }

    private static MineOperationResult checkPrerequisites(
            boolean assembled,
            String validationMessage,
            long requestedAmount,
            MineGeologyAccess geology
    ) {
        if (!assembled) {
            return basic(
                    IndustrialMineState.UNFORMED,
                    requestedAmount,
                    validationMessage
            );
        }
        if (!geology.storageAvailable()) {
            return basic(
                    IndustrialMineState.GEOLOGY_UNAVAILABLE,
                    requestedAmount,
                    "Хранилище геологии недоступно."
            );
        }
        return null;
    }

    private static MineOperationResult basic(
            IndustrialMineState state,
            long requestedAmount,
            String message
    ) {
        return new MineOperationResult(
                state,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                requestedAmount,
                0L,
                OptionalLong.empty(),
                Optional.empty(),
                false,
                false,
                message
        );
    }

    private static MineOperationResult withoutDeposit(
            IndustrialMineState state,
            ResourceLocation regionId,
            long requestedAmount,
            boolean selectionChanged,
            String message
    ) {
        return new MineOperationResult(
                state,
                Optional.of(regionId),
                Optional.empty(),
                Optional.empty(),
                requestedAmount,
                0L,
                OptionalLong.empty(),
                Optional.empty(),
                selectionChanged,
                false,
                message
        );
    }

    private static MineOperationResult withDeposit(
            IndustrialMineState state,
            GeologicalRegion region,
            GeologicalDeposit deposit,
            long requestedAmount,
            long extractedAmount,
            boolean selectionChanged,
            boolean energyIntegrationPending,
            String message
    ) {
        return new MineOperationResult(
                state,
                Optional.of(region.getId()),
                Optional.of(deposit.getId()),
                Optional.of(deposit.getResourceId()),
                requestedAmount,
                extractedAmount,
                OptionalLong.of(deposit.getRemainingReserve()),
                Optional.of(deposit.getStage()),
                selectionChanged,
                energyIntegrationPending,
                message
        );
    }

    record Selection(
            Optional<GeologicalDeposit> deposit,
            boolean changed
    ) {
    }
}
