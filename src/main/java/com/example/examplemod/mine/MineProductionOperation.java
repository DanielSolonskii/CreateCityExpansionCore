package com.example.examplemod.mine;

import com.example.examplemod.geology.DepositExtractionResult;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.OptionalLong;

final class MineProductionOperation {

    private MineProductionOperation() {
    }

    static MineOperationResult execute(
            boolean assembled,
            String validationMessage,
            Optional<ResourceLocation> activeDepositId,
            long requestedAmount,
            MineEnergyGate energyGate,
            MineOutputPort output,
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
        IndustrialMineOperation.Selection selection =
                IndustrialMineOperation.selectDeposit(region, activeDepositId);
        if (selection.deposit().isEmpty()) {
            return withoutDeposit(
                    IndustrialMineState.NO_DEPOSIT,
                    region,
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
                    "Выбранная залежь истощена."
            );
        }
        if (!output.supports(deposit.getResourceId())) {
            return withDeposit(
                    IndustrialMineState.ERROR,
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    "Для геологического ресурса не задан выходной предмет: "
                            + deposit.getResourceId()
            );
        }

        MineEnergyGate.EnergyDecision power = energyGate.evaluate();
        if (!power.allowed()) {
            return withDeposit(
                    power.deniedState(),
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    power.message()
            );
        }

        long plannedAmount = Math.min(
                requestedAmount,
                deposit.getRemainingReserve()
        );
        if (!output.available()
                || !output.canAccept(deposit.getResourceId(), plannedAmount)) {
            return withDeposit(
                    IndustrialMineState.OUTPUT_BLOCKED,
                    region,
                    deposit,
                    requestedAmount,
                    0L,
                    selection.changed(),
                    "Шлюз шахты отсутствует или выходной буфер заполнен."
            );
        }

        DepositExtractionResult extraction = geology.extract(
                deposit.getId(),
                plannedAmount
        );
        if (extraction.status() != DepositExtractionResult.Status.EXTRACTED) {
            return mapExtractionFailure(
                    extraction,
                    region,
                    deposit,
                    requestedAmount,
                    selection.changed()
            );
        }

        output.insertExact(
                deposit.getResourceId(),
                extraction.extractedAmount()
        );
        return new MineOperationResult(
                IndustrialMineState.RUNNING,
                Optional.of(region.getId()),
                Optional.of(deposit.getId()),
                Optional.of(deposit.getResourceId()),
                requestedAmount,
                extraction.extractedAmount(),
                extraction.remainingReserve(),
                extraction.stage(),
                selection.changed(),
                false,
                "Цикл добычи завершён; продукция помещена в шлюз шахты."
        );
    }

    private static MineOperationResult mapExtractionFailure(
            DepositExtractionResult extraction,
            GeologicalRegion region,
            GeologicalDeposit deposit,
            long requestedAmount,
            boolean selectionChanged
    ) {
        IndustrialMineState state = switch (extraction.status()) {
            case DEPLETED -> IndustrialMineState.DEPOSIT_DEPLETED;
            case NOT_FOUND -> IndustrialMineState.NO_DEPOSIT;
            case STORAGE_UNAVAILABLE -> IndustrialMineState.GEOLOGY_UNAVAILABLE;
            default -> IndustrialMineState.ERROR;
        };
        return withDeposit(
                state,
                region,
                deposit,
                requestedAmount,
                0L,
                selectionChanged,
                "Извлечение из залежи отклонено: "
                        + extractionStatusDescription(extraction.status())
        );
    }

    private static String extractionStatusDescription(
            DepositExtractionResult.Status status
    ) {
        return switch (status) {
            case EXTRACTED -> "ресурс извлечён";
            case DEPLETED -> "залежь истощена";
            case NOT_FOUND -> "залежь не найдена";
            case STORAGE_UNAVAILABLE -> "хранилище недоступно";
            case INVALID_REQUEST -> "недопустимый запрос";
            case ZERO_REQUEST -> "нулевой объём";
        };
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
            GeologicalRegion region,
            long requestedAmount,
            boolean selectionChanged,
            String message
    ) {
        return new MineOperationResult(
                state,
                Optional.of(region.getId()),
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
                false,
                message
        );
    }
}
