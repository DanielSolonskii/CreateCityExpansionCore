package com.example.examplemod.mine;

import com.example.examplemod.geology.DepositExtractionResult;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class IndustrialMineService {

    private static final int MACHINE_STEP_TICKS = 5;

    private IndustrialMineService() {
    }

    public static void serverTick(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller
    ) {
        long syncPhase = level.getGameTime() + controllerPos.asLong();
        if (Math.floorMod(syncPhase, MACHINE_STEP_TICKS) != 0) {
            return;
        }

        IndustrialMineState previousState = controller.getRuntimeState();
        MineElectricalState previousElectrical = controller.getElectricalState();
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        boolean workingLoad = usesWorkingLoad(controller);
        MinePowerDemand.publish(level, validation.powerPort(), workingLoad);
        MinePowerSnapshot power = MinePowerAccess.read(level, validation.powerPort(), workingLoad);
        MineElectricalState electrical = MinePowerAccess.classify(power, workingLoad);
        controller.setElectricalState(electrical);

        switch (controller.getRuntimeState()) {
            case STARTING -> tickStarting(controller, validation, electrical);
            case RUNNING -> tickRunning(level, controllerPos, controller, validation, electrical);
            case LOW_POWER -> tickLowPower(controller, validation, electrical);
            case OUTPUT_BLOCKED -> tickOutputBlocked(
                    level, controllerPos, controller, validation, electrical
            );
            case STOPPING -> tickStopping(controller);
            default -> {
            }
        }

        controller.syncVisualState(validation);

        boolean importantChange = previousState != controller.getRuntimeState()
                || previousElectrical != controller.getElectricalState();
        if (importantChange || Math.floorMod(syncPhase, 10L) == 0L) {
            MineNetworkHandlers.syncNearby(
                    level,
                    controllerPos,
                    createSnapshot(level, controllerPos, controller, validation),
                    false
            );
        }
    }

    public static MineControllerSnapshot inspectSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller
    ) {
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        ensureSelection(level, controllerPos, controller, validation);
        refreshPower(level, controller, validation);
        return createSnapshot(level, controllerPos, controller, validation);
    }

    public static MineControllerSnapshot selectDepositSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            ResourceLocation requestedDepositId
    ) {
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        if (controller.isCommissioned() || controller.getActiveDepositId().isPresent()) {
            controller.setRuntimeState(
                    controller.getRuntimeState(),
                    "Специализация этого контроллера уже зафиксирована."
            );
            return createSnapshot(level, controllerPos, controller, validation);
        }
        if (controller.getRuntimeState() != IndustrialMineState.STOPPED) {
            controller.setRuntimeState(
                    controller.getRuntimeState(),
                    "Выбор залежи заблокирован, пока шахта активна."
            );
            return createSnapshot(level, controllerPos, controller, validation);
        }
        GeologyManager manager = GeologyManager.get(level);
        if (!validation.assembled() || !manager.isStorageAvailable()) {
            return inspectSnapshot(level, controllerPos, controller);
        }
        GeologicalRegion region = GeologyManager.getRegion(level, controllerPos);
        if (!IndustrialMineOperation.isDepositSelectable(region, requestedDepositId)) {
            controller.setRuntimeState(
                    IndustrialMineState.STOPPED,
                    "Выбор отклонён: залежь не принадлежит региону этой шахты."
            );
        } else {
            controller.setPreviewDepositId(requestedDepositId);
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Выбрана руда для commissioning.");
        }
        refreshPower(level, controller, validation);
        return createSnapshot(level, controllerPos, controller, validation);
    }

    public static MineControllerSnapshot controlSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineControlCommand command
    ) {
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        switch (command) {
            case START -> start(level, controllerPos, controller, validation);
            case STOP -> stop(controller);
            case EMERGENCY -> emergency(controller);
        }
        refreshPower(level, controller, validation);
        return createSnapshot(level, controllerPos, controller, validation);
    }

    private static void start(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation
    ) {
        if (controller.getRuntimeState() != IndustrialMineState.STOPPED) {
            controller.setRuntimeState(controller.getRuntimeState(), "Пуск отклонён: шахта не остановлена.");
            return;
        }
        if (!controller.isCommissioned()) {
            controller.setRuntimeState(
                    IndustrialMineState.STOPPED,
                    "Пуск отклонён: сначала зарегистрируйте городской проект."
            );
            return;
        }
        if (!validation.assembled()) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, validation.message());
            return;
        }
        if (!GeologyManager.get(level).isStorageAvailable()) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Хранилище геологии недоступно.");
            return;
        }
        ensureSelection(level, controllerPos, controller, validation);
        GeologicalRegion region = GeologyManager.getRegion(level, controllerPos);
        Optional<GeologicalDeposit> selected = controller.getActiveDepositId()
                .flatMap(region::getDeposit)
                .filter(deposit -> !deposit.isDepleted());
        if (selected.isEmpty()) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Нет доступной выбранной залежи.");
            return;
        }
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        GeologicalDeposit deposit = selected.get();
        MineProductionProfile profile = MineProductionProfile.from(deposit);
        long planned = Math.min(
                profile.outputPerCycle(),
                deposit.getRemainingReserve()
        );
        if (!output.available() || !output.supports(deposit.getResourceId())
                || !output.canAccept(deposit.getResourceId(), planned)) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Шлюз шахты не готов принять продукцию.");
            return;
        }
        MinePowerSnapshot standby = MinePowerAccess.read(level, validation.powerPort(), false);
        MineElectricalState electrical = MinePowerAccess.classify(standby, false);
        if (electrical != MineElectricalState.NORMAL) {
            controller.setElectricalState(electrical);
            controller.setRuntimeState(
                    IndustrialMineState.STOPPED,
                    "Пуск отклонён: " + electricalDescription(electrical)
            );
            return;
        }
        controller.setUserRequestedStart(true);
        controller.setStartupTicks(0);
        controller.setRuntimeState(IndustrialMineState.STARTING, "Запуск промышленной нагрузки.");
        MinePowerDemand.publish(level, validation.powerPort(), true);
    }

    private static void stop(MineControllerBlockEntity controller) {
        if (controller.getRuntimeState() == IndustrialMineState.STOPPED) {
            return;
        }
        controller.setUserRequestedStart(false);
        controller.setShutdownTicks(0);
        controller.setRuntimeState(IndustrialMineState.STOPPING, "Выполняется штатная остановка.");
    }

    private static void emergency(MineControllerBlockEntity controller) {
        controller.setUserRequestedStart(false);
        controller.setStartupTicks(0);
        controller.setShutdownTicks(0);
        controller.setRuntimeState(IndustrialMineState.STOPPED, "Аварийная остановка выполнена.");
    }

    private static void tickStarting(
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            MineElectricalState electrical
    ) {
        if (!validation.assembled()) {
            fail(controller, IndustrialMineState.ERROR, validation.message(), false);
        } else if (MinePowerAccess.isUnsafe(electrical)) {
            fail(controller, IndustrialMineState.OVERLOAD, "Авария питания: перегрузка.", false);
        } else if (electrical != MineElectricalState.NORMAL) {
            fail(controller, IndustrialMineState.LOW_POWER,
                    "Запуск отменён: " + electricalDescription(electrical), false);
        } else {
            controller.setStartupTicks(controller.getStartupTicks() + MACHINE_STEP_TICKS);
            if (controller.getStartupTicks() >= MineControllerBlockEntity.STARTUP_DURATION_TICKS) {
                controller.setRuntimeState(IndustrialMineState.RUNNING, "Шахта вышла на рабочий режим.");
            }
        }
    }

    private static void tickRunning(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            MineElectricalState electrical
    ) {
        if (!validation.assembled()) {
            fail(controller, IndustrialMineState.ERROR, validation.message(), false);
            return;
        }
        if (MinePowerAccess.isUnsafe(electrical)) {
            fail(controller, IndustrialMineState.OVERLOAD, "Авария питания: перегрузка.", false);
            return;
        }
        if (electrical != MineElectricalState.NORMAL) {
            fail(controller, IndustrialMineState.LOW_POWER,
                    "Добыча приостановлена: " + electricalDescription(electrical), true);
            return;
        }
        Optional<GeologicalDeposit> selected = selectedDeposit(level, controllerPos, controller);
        if (selected.isEmpty() || selected.get().isDepleted()) {
            fail(controller, IndustrialMineState.DEPOSIT_DEPLETED,
                    "Выбранная залежь недоступна или истощена.", false);
            return;
        }
        MineProductionProfile profile = MineProductionProfile.from(selected.get());
        long planned = Math.min(profile.outputPerCycle(), selected.get().getRemainingReserve());
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        if (!output.available()
                || !output.supports(selected.get().getResourceId())
                || !output.canAccept(selected.get().getResourceId(), planned)) {
            controller.setRuntimeState(
                    IndustrialMineState.OUTPUT_BLOCKED,
                    "Добыча приостановлена: шлюз шахты заполнен или недоступен."
            );
            return;
        }
        controller.setMiningTicks(controller.getMiningTicks() + MACHINE_STEP_TICKS);
        if (controller.getMiningTicks() >= profile.cycleDurationTicks()) {
            runProductionCycle(level, controllerPos, controller, validation);
        }
    }

    private static void tickLowPower(
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            MineElectricalState electrical
    ) {
        if (!controller.isUserRequestedStart()) {
            return;
        }
        if (!validation.assembled()) {
            fail(controller, IndustrialMineState.ERROR, validation.message(), false);
        } else if (MinePowerAccess.isUnsafe(electrical)) {
            fail(controller, IndustrialMineState.OVERLOAD, "Авария питания: перегрузка.", false);
        } else if (electrical == MineElectricalState.NORMAL) {
            controller.setRuntimeState(IndustrialMineState.RUNNING, "Питание восстановлено; добыча продолжена.");
        }
    }

    private static void tickOutputBlocked(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            MineElectricalState electrical
    ) {
        if (!controller.isUserRequestedStart()) {
            return;
        }
        if (electrical != MineElectricalState.NORMAL) {
            if (!validation.assembled()) {
                fail(controller, IndustrialMineState.ERROR, validation.message(), false);
            } else if (MinePowerAccess.isUnsafe(electrical)) {
                fail(controller, IndustrialMineState.OVERLOAD, "Авария питания: перегрузка.", false);
            } else {
                fail(controller, IndustrialMineState.LOW_POWER,
                        "Добыча приостановлена: " + electricalDescription(electrical), true);
            }
            return;
        }
        Optional<GeologicalDeposit> selected = selectedDeposit(level, controllerPos, controller);
        if (selected.isEmpty() || selected.get().isDepleted()) {
            fail(controller, IndustrialMineState.DEPOSIT_DEPLETED,
                    "Выбранная залежь недоступна или истощена.", false);
            return;
        }
        MineProductionProfile profile = MineProductionProfile.from(selected.get());
        long planned = Math.min(profile.outputPerCycle(), selected.get().getRemainingReserve());
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        if (!output.available()
                || !output.supports(selected.get().getResourceId())
                || !output.canAccept(selected.get().getResourceId(), planned)) {
            return;
        }
        controller.setRuntimeState(IndustrialMineState.RUNNING, "Шлюз готов; добыча продолжена.");
        if (controller.getMiningTicks() >= profile.cycleDurationTicks()) {
            runProductionCycle(level, controllerPos, controller, validation);
        }
    }

    private static void tickStopping(MineControllerBlockEntity controller) {
        controller.setShutdownTicks(controller.getShutdownTicks() + MACHINE_STEP_TICKS);
        if (controller.getShutdownTicks() >= MineControllerBlockEntity.STOP_DURATION_TICKS) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Шахта остановлена.");
            controller.setStartupTicks(0);
            controller.setShutdownTicks(0);
        }
    }

    private static void runProductionCycle(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation
    ) {
        MinePowerSnapshot power = MinePowerAccess.read(level, validation.powerPort(), true);
        Optional<GeologicalDeposit> selected = selectedDeposit(level, controllerPos, controller);
        if (selected.isEmpty() || selected.get().isDepleted()) {
            fail(controller, IndustrialMineState.DEPOSIT_DEPLETED,
                    "Выбранная залежь недоступна или истощена.", false);
            return;
        }
        MineProductionProfile profile = MineProductionProfile.from(selected.get());
        MineOperationResult result = MineProductionOperation.execute(
                validation.assembled(),
                validation.message(),
                controller.getActiveDepositId(),
                profile.outputPerCycle(),
                MinePowerAccess.gate(power),
                outputPort(level, validation.gateway()),
                geologyAccess(level, controllerPos)
        );
        controller.setLastResult(result);
        if (result.extractedAmount() > 0L) {
            controller.setMiningTicks(0);
            controller.setRuntimeState(IndustrialMineState.RUNNING, result.message());
        } else if (result.state() == IndustrialMineState.OUTPUT_BLOCKED) {
            controller.setRuntimeState(IndustrialMineState.OUTPUT_BLOCKED, result.message());
        } else {
            controller.setUserRequestedStart(false);
            controller.setRuntimeState(result.state(), result.message());
        }
    }

    private static void fail(
            MineControllerBlockEntity controller,
            IndustrialMineState state,
            String message,
            boolean keepStartRequest
    ) {
        controller.setUserRequestedStart(keepStartRequest);
        controller.setRuntimeState(state, message);
    }

    private static void ensureSelection(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation
    ) {
        if (!validation.assembled() || !GeologyManager.get(level).isStorageAvailable()) {
            return;
        }
        GeologicalRegion region = GeologyManager.getRegion(level, controllerPos);
        if (controller.isCommissioned()) {
            return;
        }
        IndustrialMineOperation.Selection selection = IndustrialMineOperation.selectDeposit(
                region,
                controller.getDisplayDepositId()
        );
        if (selection.changed()) {
            selection.deposit().ifPresent(deposit -> controller.setPreviewDepositId(deposit.getId()));
        }
    }

    private static Optional<GeologicalDeposit> selectedDeposit(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller
    ) {
        if (!GeologyManager.get(level).isStorageAvailable()) {
            return Optional.empty();
        }
        GeologicalRegion region = GeologyManager.getRegion(level, controllerPos);
        return controller.getActiveDepositId().flatMap(region::getDeposit);
    }

    private static void refreshPower(
            ServerLevel level,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation
    ) {
        boolean working = usesWorkingLoad(controller);
        MinePowerDemand.publish(level, validation.powerPort(), working);
        MinePowerSnapshot power = MinePowerAccess.read(level, validation.powerPort(), working);
        controller.setElectricalState(MinePowerAccess.classify(power, working));
    }

    private static boolean usesWorkingLoad(MineControllerBlockEntity controller) {
        return switch (controller.getRuntimeState()) {
            case STARTING, RUNNING -> true;
            case LOW_POWER -> controller.isUserRequestedStart();
            default -> false;
        };
    }

    private static String electricalDescription(MineElectricalState state) {
        return switch (state) {
            case NO_POWER -> "нет питания";
            case LOW_VOLTAGE -> "низкое напряжение";
            case INSUFFICIENT_POWER -> "недостаточная мощность";
            case NORMAL -> "параметры в норме";
            case OVERVOLTAGE -> "перенапряжение";
            case OVERLOAD -> "перегрузка";
        };
    }

    private static MineControllerSnapshot createSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation
    ) {
        boolean working = usesWorkingLoad(controller);
        MinePowerSnapshot power = MinePowerAccess.read(level, validation.powerPort(), working);
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        Optional<GeologyRegionSnapshot> region = Optional.empty();
        Optional<ResourceLocation> selected = Optional.empty();
        Optional<GeologicalDeposit> selectedDeposit = Optional.empty();
        if (validation.assembled() && GeologyManager.get(level).isStorageAvailable()) {
            GeologicalRegion authoritative = GeologyManager.getRegion(level, controllerPos);
            region = Optional.of(GeologyRegionSnapshot.from(authoritative));
            selected = controller.getDisplayDepositId().filter(id -> authoritative.getDeposit(id).isPresent());
            selectedDeposit = selected.flatMap(authoritative::getDeposit);
        }
        long requested = controller.getLastResult().map(MineOperationResult::requestedAmount).orElse(0L);
        long extracted = controller.getLastResult().map(MineOperationResult::extractedAmount).orElse(0L);
        MineProductionProfile profile = selectedDeposit
                .map(MineProductionProfile::from)
                .orElseGet(MineProductionProfile::unavailable);
        return new MineControllerSnapshot(
                controllerPos,
                level.dimension().location(),
                controller.getRuntimeState(),
                controller.getElectricalState(),
                controller.machineProgress(),
                controller.miningProgress(profile.cycleDurationTicks()),
                profile.richness(),
                profile.itemsPerMinute(),
                profile.outputPerCycle(),
                profile.cycleDurationTicks(),
                validation.assembled(),
                validation.message(),
                GeologyManager.get(level).isStorageAvailable(),
                power,
                new MineOutputSnapshot(output.available(), output.itemCount(), output.totalCapacity()),
                region,
                selected,
                controller.getCreatorId(),
                controller.getActiveDepositId().isPresent(),
                controller.isCommissioned(),
                controller.getCityId(),
                controller.getProjectId(),
                requested,
                extracted,
                false,
                controller.getStatusMessage()
        );
    }

    private static GatewayMineOutputPort outputPort(ServerLevel level, BlockPos gatewayPos) {
        MineGatewayBlockEntity gateway = gatewayPos != null
                && level.getBlockEntity(gatewayPos) instanceof MineGatewayBlockEntity found
                ? found : null;
        return new GatewayMineOutputPort(gateway);
    }

    private static MineGeologyAccess geologyAccess(ServerLevel level, BlockPos controllerPos) {
        return new MineGeologyAccess() {
            @Override public boolean storageAvailable() {
                return GeologyManager.get(level).isStorageAvailable();
            }
            @Override public GeologicalRegion getRegion() {
                return GeologyManager.getRegion(level, controllerPos);
            }
            @Override public DepositExtractionResult extract(ResourceLocation id, long amount) {
                return GeologyManager.extractDeposit(level, id, amount);
            }
        };
    }
}
