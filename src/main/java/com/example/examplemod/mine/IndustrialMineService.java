package com.example.examplemod.mine;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.geology.GeologicalRegion;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import com.example.examplemod.geology.GeologyManager;
import com.example.examplemod.multiblock.BlueprintCatalog;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class IndustrialMineService {

    private static final int MACHINE_STEP_TICKS = 5;
    private static final String BLUEPRINT_LOCKED = "Чертёж шахты закрыт для города.";

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

        MineInteraction.redstone(level, controller);
        IndustrialMineState previousState = controller.getRuntimeState();
        MineElectricalState previousElectrical = controller.getElectricalState();
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        ensureSelection(level, controllerPos, controller, validation);
        Tier tier = operatingTier(level, controllerPos, controller, validation);
        if (tier == null && controller.isUserRequestedStart()) {
            fail(controller, IndustrialMineState.STOPPED, BLUEPRINT_LOCKED, false);
        }
        if (controller.getRuntimeState() == IndustrialMineState.STOPPED && controller.isUserRequestedStart()) {
            // A loaded machine retains intent, never its previous running/electrical state.
            // Retry normal startup while chunks, geology, output or supply become available.
            start(level, controllerPos, controller, validation, tier);
            if (MinePowerAccess.isUnsafe(controller.getElectricalState())) {
                fail(controller, IndustrialMineState.OVERLOAD, "Автозапуск отменён: авария питания.", false);
            }
        }
        MinePowerSnapshot power = refreshPower(level, controller, validation, tier);
        MineElectricalState electrical = controller.getElectricalState();

        switch (controller.getRuntimeState()) {
            case STARTING -> tickStarting(controller, validation, electrical);
            case RUNNING -> tickRunning(level, controllerPos, controller, validation, electrical, tier, power);
            case LOW_POWER -> tickLowPower(controller, validation, electrical);
            case OUTPUT_BLOCKED -> tickOutputBlocked(
                    level, controllerPos, controller, validation, electrical, tier
            );
            case STOPPING -> tickStopping(controller);
            default -> {
            }
        }

        controller.syncVisualState(validation);

        boolean importantChange = previousState != controller.getRuntimeState()
                || previousElectrical != controller.getElectricalState();
        if (importantChange || Math.floorMod(syncPhase, 10L) == 0L) {
            MineInstrumentation.publish(level, controller, validation, tier, power);
        }
    }

    public static MineControllerSnapshot inspectSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller
    ) {
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        ensureSelection(level, controllerPos, controller, validation);
        Tier tier = operatingTier(level, controllerPos, controller, validation);
        refreshPower(level, controller, validation, tier);
        MineInstrumentation.publish(level, controller, validation, tier,
                MinePowerAccess.read(level, validation.powerPort(), usesWorkingLoad(controller), tier == null ? Tier.I : tier));
        return createSnapshot(level, controllerPos, controller, validation, tier);
    }

    public static MineControllerSnapshot selectDepositSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            ResourceLocation requestedDepositId
    ) {
        // Compatibility entry point: callers cannot choose a different deposit.
        return inspectSnapshot(level, controllerPos, controller);
    }

    public static MineControllerSnapshot controlSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineControlCommand command
    ) {
        MineMultiblock.ValidationResult validation = MineMultiblock.validate(level, controllerPos);
        Tier tier = operatingTier(level, controllerPos, controller, validation);
        switch (command) {
            case START -> start(level, controllerPos, controller, validation, tier);
            case STOP -> stop(controller);
            case EMERGENCY -> emergency(controller);
        }
        refreshPower(level, controller, validation, tier);
        return createSnapshot(level, controllerPos, controller, validation, tier);
    }

    private static void start(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            Tier tier
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
        if (tier == null) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, BLUEPRINT_LOCKED);
            return;
        }
        if (!GeologyManager.get(level).isStorageAvailable()) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Хранилище геологии недоступно.");
            return;
        }
        ensureSelection(level, controllerPos, controller, validation);
        GeologicalRegion region = GeologyManager.getRegion(level, controllerPos);
        Optional<GeologicalDeposit> selected = controller.getActiveDepositId()
                .flatMap(region::getDeposit);
        if (selected.isEmpty()) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Нет доступной выбранной залежи.");
            return;
        }
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        GeologicalDeposit deposit = selected.get();
        MineProductionProfile profile = productionProfile(controllerPos, deposit, tier);
        if (!profile.viable()) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "В точке установки нет потенциала выбранной залежи.");
            return;
        }
        long planned = profile.outputPerCycle();
        if (!output.available() || !output.supports(deposit.getResourceId())
                || !output.canAccept(deposit.getResourceId(), planned)) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Шлюз шахты не готов принять продукцию.");
            return;
        }
        MinePowerSnapshot standby = MinePowerAccess.read(level, validation.powerPort(), false, tier);
        MineElectricalState electrical = MinePowerAccess.classify(standby, false, tier);
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
    }

    private static void stop(MineControllerBlockEntity controller) {
        controller.setUserRequestedStart(false);
        if (controller.getRuntimeState() == IndustrialMineState.STOPPED) {
            controller.setRuntimeState(IndustrialMineState.STOPPED, "Шахта остановлена.");
            return;
        }
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
                    "Запуск приостановлен: " + electricalDescription(electrical), true);
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
            MineElectricalState electrical,
            Tier tier,
            MinePowerSnapshot power
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
        if (selected.isEmpty()) {
            fail(controller, IndustrialMineState.NO_DEPOSIT,
                    "Выбранная залежь недоступна.", false);
            return;
        }
        MineProductionProfile profile = productionProfile(controllerPos, selected.get(), tier);
        if (!profile.viable()) {
            fail(controller, IndustrialMineState.NO_DEPOSIT, "В точке установки нет потенциала выбранной залежи.", false);
            return;
        }
        long planned = profile.outputPerCycle();
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
        if (controller.getMiningTicks() >= profile.exactCycleTicks()) {
            runProductionCycle(level, controllerPos, controller, validation, profile, power);
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
            boolean starting = controller.getStartupTicks() < MineControllerBlockEntity.STARTUP_DURATION_TICKS;
            controller.setRuntimeState(starting ? IndustrialMineState.STARTING : IndustrialMineState.RUNNING,
                    starting ? "Питание восстановлено; запуск продолжен." : "Питание восстановлено; добыча продолжена.");
        }
    }

    private static void tickOutputBlocked(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            MineElectricalState electrical,
            Tier tier
    ) {
        if (!controller.isUserRequestedStart()) {
            return;
        }
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
        if (selected.isEmpty()) {
            fail(controller, IndustrialMineState.NO_DEPOSIT,
                    "Выбранная залежь недоступна.", false);
            return;
        }
        MineProductionProfile profile = productionProfile(controllerPos, selected.get(), tier);
        if (!profile.viable()) {
            fail(controller, IndustrialMineState.NO_DEPOSIT, "В точке установки нет потенциала выбранной залежи.", false);
            return;
        }
        long planned = profile.outputPerCycle();
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        if (!output.available()
                || !output.supports(selected.get().getResourceId())
                || !output.canAccept(selected.get().getResourceId(), planned)) {
            return;
        }
        controller.setRuntimeState(IndustrialMineState.RUNNING, "Шлюз готов; добыча продолжена.");
        MinePowerDemand.publish(level, validation.powerPort(), true, tier);
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
            MineMultiblock.ValidationResult validation,
            MineProductionProfile profile,
            MinePowerSnapshot power
    ) {
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
            controller.setMiningTicks(Math.min(MACHINE_STEP_TICKS,
                    controller.getMiningTicks() - profile.exactCycleTicks()));
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
        if (!GeologyManager.get(level).isStorageAvailable()) {
            return;
        }
        GeologicalRegion region = GeologyManager.getRegion(level, controllerPos);
        if (controller.isCommissioned()) {
            return;
        }
        controller.setPreviewDepositId(region.getDeposits().stream()
                .filter(deposit -> deposit.localPotentialPerHour(controllerPos.getX(), controllerPos.getZ()) > 0)
                .map(GeologicalDeposit::getId).findFirst().orElse(null));
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

    private static MinePowerSnapshot refreshPower(
            ServerLevel level,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            Tier tier
    ) {
        boolean working = tier != null && usesWorkingLoad(controller);
        Tier powerTier = tier == null ? Tier.I : tier;
        MinePowerDemand.publish(level, validation.powerPort(), working, powerTier);
        MinePowerSnapshot power = MinePowerAccess.read(level, validation.powerPort(), working, powerTier);
        controller.setElectricalState(MinePowerAccess.classify(power, working, powerTier));
        return power;
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

    private static Tier operatingTier(
            ServerLevel level, BlockPos controllerPos, MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation
    ) {
        if (!validation.assembled() || !controller.isCommissioned()) {
            return Tier.I;
        }
        CityManager cities = CityManager.get(level.getServer().overworld());
        City city = controller.getCityId().map(cities::getCity).orElse(null);
        int unlocked = city == null ? Tier.I.level() : city.getUnlockedBlueprintTier(Structure.MINE);
        if (unlocked == 0) {
            return null;
        }
        if (unlocked == Tier.I.level()) {
            return Tier.I;
        }
        var facing = level.getBlockState(controllerPos).getValue(BlockStateProperties.HORIZONTAL_FACING);
        return BlueprintCatalog.mineTier(level, controllerPos, facing, Structure.MINE.tierFromLevel(unlocked));
    }

    private static MineProductionProfile productionProfile(BlockPos controllerPos, GeologicalDeposit deposit, Tier tier) {
        return MineProductionProfile.from(deposit, controllerPos.getX(), controllerPos.getZ(), tier);
    }

    private static MineControllerSnapshot createSnapshot(
            ServerLevel level,
            BlockPos controllerPos,
            MineControllerBlockEntity controller,
            MineMultiblock.ValidationResult validation,
            Tier tier
    ) {
        boolean working = tier != null && usesWorkingLoad(controller);
        MinePowerSnapshot power = MinePowerAccess.read(level, validation.powerPort(), working, tier == null ? Tier.I : tier);
        GatewayMineOutputPort output = outputPort(level, validation.gateway());
        Optional<GeologyRegionSnapshot> region = Optional.empty();
        boolean commissioned = controller.isCommissioned();
        Optional<ResourceLocation> depositId = controller.getDisplayDepositId();
        Optional<GeologicalDeposit> selectedDeposit = Optional.empty();
        if (GeologyManager.get(level).isStorageAvailable()) {
            GeologicalRegion authoritative = GeologyManager.getRegion(level, controllerPos);
            region = Optional.of(GeologyRegionSnapshot.from(
                    authoritative,
                    controllerPos.getX(),
                    controllerPos.getZ()
            ));
            selectedDeposit = depositId.flatMap(authoritative::getDeposit);
        }
        if (!commissioned && region.isEmpty()) depositId = Optional.empty();
        long requested = controller.getLastResult().map(MineOperationResult::requestedAmount).orElse(0L);
        long extracted = controller.getLastResult().map(MineOperationResult::extractedAmount).orElse(0L);
        MineProductionProfile profile = tier == null ? MineProductionProfile.unavailable() : selectedDeposit
                .map(deposit -> productionProfile(controllerPos, deposit, tier))
                .orElseGet(MineProductionProfile::unavailable);
        return new MineControllerSnapshot(
                controllerPos,
                level.dimension().location(),
                controller.getRuntimeState(),
                controller.getElectricalState(),
                controller.machineProgress(),
                controller.miningProgress(profile.exactCycleTicks()),
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
                commissioned ? depositId : selectedDeposit.map(GeologicalDeposit::getId),
                controller.getCreatorId(),
                controller.getActiveDepositId().isPresent(),
                commissioned,
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
        };
    }
}
