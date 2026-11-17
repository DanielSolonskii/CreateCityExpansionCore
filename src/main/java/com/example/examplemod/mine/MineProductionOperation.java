package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

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
                    "Хранилище геологии недоступно."
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

        if (!output.available()
                || !output.canAccept(deposit.getResourceId(), requestedAmount)) {
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

        output.insertExact(deposit.getResourceId(), requestedAmount);
        return withDeposit(IndustrialMineState.RUNNING, region, deposit, requestedAmount,
                requestedAmount, selection.changed(),
                "Цикл добычи завершён; продукция помещена в шлюз шахты.");
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
                selectionChanged,
                false,
                message
        );
    }
}
