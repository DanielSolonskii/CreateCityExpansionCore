package com.example.examplemod.client.screen;

import com.example.examplemod.geology.GeologyDepositSnapshot;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import com.example.examplemod.mine.MineControlCommand;
import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.client.sound.MineSoundController;
import com.example.examplemod.mine.MineControllerSnapshot;
import com.example.examplemod.mine.MineElectricalState;
import com.example.examplemod.multiblock.MultiblockPreviewRenderer;
import com.example.examplemod.client.gui.CityButton;
import com.example.examplemod.network.SelectMineDepositPayload;
import com.example.examplemod.network.MineControlPayload;
import com.example.examplemod.network.CommissionMineProjectPayload;
import com.example.examplemod.network.MineCommissionContextPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.math.Axis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MineControllerScreen extends Screen {

    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 500;
    private static final int BACKGROUND = 0xF0081016;
    private static final int PANEL = 0xE80B141A;
    private static final int BORDER = 0xFF263A44;
    private static final int CYAN = 0xFF58D7FF;
    private static final int GREEN = 0xFF35E875;
    private static final int ORANGE = 0xFFB45B22;
    private static final int RED = 0xFFFF4A4A;
    private static final int TEXT = 0xFFD3DEE2;
    private static final int TEXT_DIM = 0xFF748A94;

    private MineControllerSnapshot snapshot;
    private int left;
    private int top;
    private CityButton previousDepositButton;
    private CityButton nextDepositButton;
    private CityButton startButton;
    private CityButton stopButton;
    private CityButton emergencyButton;
    private CityButton schematicButton;
    private CityButton commissionButton;
    private float interfaceScale = 1.0F;
    private Optional<UUID> commissionCityId = Optional.empty();
    private String commissionCityName = "";
    private String commissionMessage = tr("gui.createcityexpansioncore.mine.commission.loading");
    private boolean commissionAllowed;
    private boolean commissionConfirmation;
    private long snapshotReceivedNanos;
    private Optional<String> railwayStationName = Optional.empty();
    private boolean railwayStationAvailable;

    public MineControllerScreen(MineControllerSnapshot snapshot) {
        super(Component.translatable(
                "gui.createcityexpansioncore.mine_controller.title"
        ));
        this.snapshot = snapshot;
        snapshotReceivedNanos = System.nanoTime();
    }

    public static void openOrUpdate(MineControllerSnapshot snapshot) {
        openOrUpdate(snapshot, Optional.empty(), false);
    }

    public static void openOrUpdate(MineControllerSnapshot snapshot,
                                    Optional<String> stationName, boolean stationAvailable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MineControllerScreen screen
                && screen.snapshot.controllerPos().equals(snapshot.controllerPos())
                && screen.snapshot.dimensionId().equals(snapshot.dimensionId())) {
            screen.applySnapshot(snapshot);
            screen.railwayStationName = stationName;
            screen.railwayStationAvailable = stationAvailable;
            return;
        }
        MineControllerScreen screen = new MineControllerScreen(snapshot);
        screen.railwayStationName = stationName;
        screen.railwayStationAvailable = stationAvailable;
        minecraft.setScreen(screen);
    }

    public static void updateIfOpen(MineControllerSnapshot snapshot) {
        updateIfOpen(snapshot, Optional.empty(), false);
    }

    public static void updateIfOpen(MineControllerSnapshot snapshot,
                                    Optional<String> stationName, boolean stationAvailable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MineControllerScreen screen
                && screen.snapshot.controllerPos().equals(snapshot.controllerPos())
                && screen.snapshot.dimensionId().equals(snapshot.dimensionId())) {
            screen.applySnapshot(snapshot);
            screen.railwayStationName = stationName;
            screen.railwayStationAvailable = stationAvailable;
        }
    }

    public static void receiveCommissionContext(MineCommissionContextPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MineControllerScreen screen
                && screen.snapshot.controllerPos().equals(payload.controllerPos())) {
            screen.commissionCityId = payload.cityId();
            screen.commissionCityName = payload.cityName();
            screen.commissionAllowed = payload.allowed();
            screen.commissionMessage = tr(payload.message());
            screen.updateButtonState();
        }
    }

    @Override
    public void onClose() {
        /*
         * Петли шахты останавливаются при закрытии интерфейса,
         * иначе бурение продолжало бы звучать за закрытым экраном.
         */
        if (snapshot != null) {
            MineSoundController.stopAll(snapshot.controllerPos());
        }
        super.onClose();
    }

    private void applySnapshot(MineControllerSnapshot updated) {
        snapshot = updated;
        snapshotReceivedNanos = System.nanoTime();
        updateButtonState();
        // Звук выводится из фактической смены состояния шахты.
        MineSoundController.onState(
                updated.controllerPos(),
                updated.state()
        );
    }

    @Override
    protected void init() {
        interfaceScale = Math.min(1.0F, Math.min(
                (width - 12.0F) / PANEL_WIDTH,
                (height - 12.0F) / PANEL_HEIGHT));
        left = Math.round((width / interfaceScale - PANEL_WIDTH) / 2.0F);
        top = Math.round((height / interfaceScale - PANEL_HEIGHT) / 2.0F);

        schematicButton = addRenderableWidget(new CityButton(
                left + 565, top + 445, 170, 26,
                Component.translatable("gui.createcityexpansioncore.mine.show_schematic"),
                button -> {
                    if (MultiblockPreviewRenderer.isShowing()) {
                        MultiblockPreviewRenderer.hide();
                        button.setMessage(Component.translatable(
                                "gui.createcityexpansioncore.mine.show_schematic"));
                    } else {
                        showSchematic();
                        button.setMessage(Component.translatable(
                                "gui.createcityexpansioncore.mine.schematic_active"));
                    }
                }, CityButton.Style.INDUSTRIAL));

        commissionButton = addRenderableWidget(new CityButton(
                left + 565, top + 445, 170, 26,
                Component.translatable("gui.createcityexpansioncore.mine.commission"),
                button -> commissionProject()
        , CityButton.Style.INDUSTRIAL));

        previousDepositButton = addRenderableWidget(new CityButton(
                left + 565, top + 375, 82, 26,
                Component.translatable("gui.createcityexpansioncore.mine.previous_resource"),
                button -> requestAdjacentDeposit(-1)
        , CityButton.Style.INDUSTRIAL));

        nextDepositButton = addRenderableWidget(new CityButton(
                left + 653, top + 375, 82, 26,
                Component.translatable("gui.createcityexpansioncore.mine.next_resource"),
                button -> requestAdjacentDeposit(1)
        , CityButton.Style.INDUSTRIAL));

        startButton = controlButton(Component.translatable("gui.createcityexpansioncore.mine.start"),
                MineControlCommand.START, left + 565, top + 410, 53);
        stopButton = controlButton(Component.translatable("gui.createcityexpansioncore.mine.stop"),
                MineControlCommand.STOP, left + 621, top + 410, 53);
        emergencyButton = controlButton(Component.translatable("gui.createcityexpansioncore.mine.emergency"),
                MineControlCommand.EMERGENCY,
                left + 677, top + 410, 58);

        updateButtonState();
    }

    private void updateButtonState() {
        if (previousDepositButton == null) {
            return;
        }
        int depositCount = snapshot.region()
                .map(region -> region.deposits().size())
                .orElse(0);
        boolean stopped = snapshot.state() == IndustrialMineState.STOPPED;
        previousDepositButton.active = stopped && !snapshot.specialized()
                && !snapshot.commissioned() && depositCount > 1;
        nextDepositButton.active = stopped && !snapshot.specialized()
                && !snapshot.commissioned() && depositCount > 1;
        // START is an intent. The server is the sole authority for all launch
        // prerequisites and returns the concrete rejection in actionMessage.
        startButton.active = stopped;
        stopButton.active = !stopped && snapshot.state() != IndustrialMineState.STOPPING;
        emergencyButton.active = !stopped;
        if (schematicButton != null && commissionButton != null) {
            schematicButton.visible = snapshot.commissioned();
            commissionButton.visible = !snapshot.commissioned();
            commissionButton.active = commissionAllowed
                    && commissionCityId.isPresent()
                    && snapshot.structureValid()
                    && snapshot.selectedDepositId().isPresent();
        }
    }

    private void commissionProject() {
        if (!commissionConfirmation) {
            commissionConfirmation = true;
            commissionMessage = tr("gui.createcityexpansioncore.mine.commission.lock_warning");
            commissionButton.setMessage(Component.translatable(
                    "gui.createcityexpansioncore.mine.commission.confirm"));
            return;
        }
        if (commissionCityId.isEmpty() || snapshot.selectedDepositId().isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new CommissionMineProjectPayload(
                snapshot.controllerPos(),
                commissionCityId.get(),
                snapshot.selectedDepositId().get()
        ));
        commissionConfirmation = false;
        commissionButton.setMessage(Component.translatable(
                "gui.createcityexpansioncore.mine.commission"));
    }

    private CityButton controlButton(
            Component label,
            MineControlCommand command,
            int x,
            int y,
            int width
    ) {
        return addRenderableWidget(new CityButton(
                x, y, width, 30, label,
                button -> PacketDistributor.sendToServer(
                        new MineControlPayload(snapshot.controllerPos(), command)
                ), command == MineControlCommand.EMERGENCY
                ? CityButton.Style.DANGER : CityButton.Style.INDUSTRIAL));
    }

    private void requestAdjacentDeposit(int direction) {
        Optional<GeologyRegionSnapshot> regionResult = snapshot.region();
        if (regionResult.isEmpty() || regionResult.get().deposits().isEmpty()) {
            return;
        }

        List<GeologyDepositSnapshot> deposits = regionResult.get().deposits();
        int current = 0;
        if (snapshot.selectedDepositId().isPresent()) {
            for (int index = 0; index < deposits.size(); index++) {
                if (deposits.get(index).depositId().equals(
                        snapshot.selectedDepositId().get()
                )) {
                    current = index;
                    break;
                }
            }
        }
        int next = Math.floorMod(current + direction, deposits.size());
        PacketDistributor.sendToServer(new SelectMineDepositPayload(
                snapshot.controllerPos(),
                deposits.get(next).depositId()
        ));
    }

    private void showSchematic() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(snapshot.controllerPos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        MultiblockPreviewRenderer.show(
                snapshot.controllerPos(),
                state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(0, 0, width, height, 0xD0080D10);
        graphics.pose().pushPose();
        graphics.pose().scale(interfaceScale, interfaceScale, 1.0F);
        int scaledMouseX = Math.round(mouseX / interfaceScale);
        int scaledMouseY = Math.round(mouseY / interfaceScale);
        drawMainPanel(graphics);
        drawHeader(graphics);
        drawPowerSystem(graphics);
        drawDeposit(graphics);
        drawSystemInfo(graphics);
        drawExtractionPanel(graphics, partialTick);
        drawOutputPanel(graphics);
        if (snapshot.state() == IndustrialMineState.OVERLOAD
                && (System.currentTimeMillis() / 300L) % 2L == 0L) {
            graphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4,
                    0x28FF0000);
        }
        drawScanlines(graphics);

        /*
         * Виджеты рисуются ДО вызова super.render().
         *
         * Раньше порядок был обратным: super.render() вызывался в конце,
         * а он внутри вызывает renderBackground() -> renderBlurredBackground().
         * В итоге blur применялся уже ПОСЛЕ отрисовки панелей, из-за чего
         * весь интерфейс размывался, а кнопки, нарисованные следом,
         * оставались резкими поверх него.
         *
         * Теперь сначала рисуем виджеты, затем фон/размытие — весь
         * готовый слой ретушируется единообразно.
         */
        for (var renderable : this.renderables) {
            renderable.render(graphics, scaledMouseX, scaledMouseY, partialTick);
        }
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX / interfaceScale, mouseY / interfaceScale, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX / interfaceScale, mouseY / interfaceScale, button);
    }

    private void drawMainPanel(GuiGraphics graphics) {
        graphics.fill(
                left + 6,
                top + 7,
                left + PANEL_WIDTH + 6,
                top + PANEL_HEIGHT + 7,
                0x90000000
        );
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, ORANGE);
        graphics.fill(
                left,
                top + PANEL_HEIGHT - 2,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF6A3518
        );
        graphics.fill(left, top, left + 2, top + PANEL_HEIGHT, 0xFF6A3518);
        graphics.fill(
                left + PANEL_WIDTH - 2,
                top,
                left + PANEL_WIDTH,
                top + PANEL_HEIGHT,
                0xFF6A3518
        );
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.title"),
                left + 25, top + 18, 0xFFE8EEF2, false);
        graphics.drawString(font, "CREATE CITY EXPANSION", left + 25, top + 33, TEXT_DIM, false);
        drawFitted(graphics, snapshot.actionMessage(),
                left + 190, top + 33, left + 590, TEXT_DIM);

        int stateColor = stateColor(snapshot.state());
        String state = displayState(snapshot.state());
        graphics.fill(left + 604, top + 25, left + 612, top + 33, stateColor);
        drawFitted(graphics, state, left + 620, top + 24, left + 740, stateColor);
        graphics.fill(left + 20, top + 52, left + PANEL_WIDTH - 20, top + 53, BORDER);
    }

    private void drawPowerSystem(GuiGraphics graphics) {
        int x = left + 20;
        int y = top + 70;
        drawPanel(graphics, x, y, 235, 145, tr("gui.createcityexpansioncore.mine.power_system"));
        if (!snapshot.power().connected()) {
            graphics.drawString(font, tr("gui.createcityexpansioncore.mine.power_disconnected"),
                    x + 14, y + 45, RED, false);
            graphics.drawString(font, tr("gui.createcityexpansioncore.mine.ee_unavailable"),
                    x + 14, y + 67, TEXT_DIM, false);
        } else {
            int powerColor = electricalColor();
            valueLine(graphics, tr("gui.createcityexpansioncore.mine.voltage"), tr(
                            "gui.createcityexpansioncore.mine.voltage_value",
                            "%.0f".formatted(snapshot.power().voltage())),
                    x + 14, y + 42, 116);
            valueLine(graphics, tr("gui.createcityexpansioncore.mine.current"), tr(
                            "gui.createcityexpansioncore.mine.current_value",
                            "%.1f".formatted(snapshot.power().current())),
                    x + 14, y + 60, 116);
            valueLine(graphics, tr("gui.createcityexpansioncore.mine.load"), tr(
                            "gui.createcityexpansioncore.mine.power_value",
                            "%.2f".formatted(snapshot.power().power() / 1000.0)),
                    x + 14, y + 78, 116);
            graphics.fill(x + 14, y + 98, x + 20, y + 104, powerColor);
            graphics.drawString(font, displayElectricalState(), x + 27, y + 97,
                    powerColor, false);
        }
        drawPowerWaveform(graphics, x + 14, y + 116, 207, 18);

        drawPanel(graphics, x, y + 155, 235, 90, tr("gui.createcityexpansioncore.mine.work_cycle"));
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.machine_progress",
                        GeologyUiFormat.percent(snapshot.machineProgress())),
                x + 14, y + 195, TEXT, false);
        drawReserveBar(graphics, x + 14, y + 210, 207, 9, snapshot.machineProgress());
        double miningProgress = displayedMiningProgress();
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.mining_progress",
                        GeologyUiFormat.percent(miningProgress)),
                x + 14, y + 226, TEXT, false);
        drawReserveBar(graphics, x + 14, y + 241, 207, 9, miningProgress);
    }

    private void drawDeposit(GuiGraphics graphics) {
        int x = left + 265;
        int y = top + 70;
        int panelWidth = 280;
        drawPanel(graphics, x, y, panelWidth, 335, tr("gui.createcityexpansioncore.mine.selected_deposit"));

        Optional<GeologyDepositSnapshot> selected = snapshot.selectedDeposit();
        if (selected.isEmpty()) {
            String message = snapshot.geologyAvailable()
                    ? tr("gui.createcityexpansioncore.mine.deposit_not_selected")
                    : tr("status.createcityexpansioncore.mine.geology_unavailable");
            graphics.drawCenteredString(font, message, x + panelWidth / 2, y + 70, RED);
            return;
        }

        GeologyDepositSnapshot deposit = selected.get();
        graphics.drawCenteredString(
                font,
                GeologyUiFormat.resource(deposit.resourceId()),
                x + panelWidth / 2,
                y + 43,
                CYAN
        );
        graphics.drawCenteredString(
                font,
                GeologyUiFormat.stage(deposit.stage()),
                x + panelWidth / 2,
                y + 62,
                deposit.depleted() ? RED : GREEN
        );

        int barX = x + 15;
        int barY = y + 89;
        int barWidth = panelWidth - 30;
        drawReserveBar(graphics, barX, barY, barWidth, 18, deposit.remainingRatio());
        graphics.drawCenteredString(
                font,
                GeologyUiFormat.percent(deposit.remainingRatio()),
                x + panelWidth / 2,
                barY + 5,
                TEXT
        );
        graphics.drawCenteredString(
                font,
                GeologyUiFormat.count(deposit.remainingReserve()) + " / "
                        + GeologyUiFormat.count(deposit.initialReserve()),
                x + panelWidth / 2,
                y + 118,
                TEXT
        );

        valueLine(graphics, tr("gui.createcityexpansioncore.mine.stage"), GeologyUiFormat.stage(deposit.stage()), x + 15, y + 151, 145);
        valueLine(graphics, tr("gui.createcityexpansioncore.mine.richness"), GeologyUiFormat.percent(snapshot.richness()),
                x + 15, y + 177, 145);
        valueLine(graphics, tr("gui.createcityexpansioncore.mine.production"), tr(
                        "gui.createcityexpansioncore.mine.production_value",
                        "%.1f".formatted(snapshot.productionPerMinute())),
                x + 15, y + 203, 112);
        valueLine(graphics, tr("gui.createcityexpansioncore.mine.per_cycle"), GeologyUiFormat.count(snapshot.outputPerCycle()),
                x + 15, y + 229, 145);
        valueLine(graphics, tr("gui.createcityexpansioncore.mine.cycle_time"), snapshot.cycleDurationTicks() <= 0
                        ? "--" : tr("gui.createcityexpansioncore.mine.seconds_value",
                        "%.1f".formatted(snapshot.cycleDurationTicks() / 20.0)),
                x + 15, y + 255, 145);
        graphics.drawString(font,
                snapshot.commissioned()
                        ? tr("gui.createcityexpansioncore.mine.specialization_locked")
                        : commissionConfirmation
                        ? tr("gui.createcityexpansioncore.mine.confirm_locks_resource")
                        : tr("gui.createcityexpansioncore.mine.select_for_commissioning"),
                x + 15, y + 286,
                snapshot.commissioned() ? GREEN : 0xFFFFB347,
                false);
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.server_authoritative"),
                x + 15, y + 307, TEXT_DIM, false);
    }

    private void drawSystemInfo(GuiGraphics graphics) {
        int x = left + 560;
        int y = top + 70;
        drawPanel(graphics, x, y, 180, 105, tr("gui.createcityexpansioncore.mine.structure"));
        graphics.drawString(
                font,
                tr(snapshot.structureValid()
                        ? "gui.createcityexpansioncore.mine.structure_valid"
                        : "gui.createcityexpansioncore.mine.structure_invalid"),
                x + 12,
                y + 43,
                snapshot.structureValid() ? GREEN : RED,
                false
        );
        drawFitted(graphics, snapshot.structureMessage(), x + 12, y + 65, x + 168, TEXT_DIM);

        drawPanel(graphics, x, y + 115, 180, 95, tr("gui.createcityexpansioncore.mine.region"));
        if (snapshot.region().isPresent()) {
            GeologyRegionSnapshot region = snapshot.region().get();
            graphics./*
             * Это индексный macro-cell, а не идентичность
             * месторождения: залежи, видимые шахтой, могут
             * принадлежать соседним ячейкам и пересекать границу.
             */
            drawString(
                    font,
                    "CELL " + region.regionX() + ", " + region.regionZ(),
                    x + 12,
                    y + 158,
                    TEXT,
                    false
            );
            graphics.drawString(
                    font,
                    tr("gui.createcityexpansioncore.mine.deposit_count", region.deposits().size()),
                    x + 12,
                    y + 178,
                    TEXT_DIM,
                    false
            );
        } else {
            graphics.drawString(font, tr("gui.createcityexpansioncore.mine.no_geology_data"),
                    x + 12, y + 158, RED, false);
        }

        drawPanel(graphics, x, top + 290, 180, 75,
                tr("gui.createcityexpansioncore.mine.city_project"));
        graphics.drawString(
                font,
                tr(snapshot.commissioned()
                        ? "gui.createcityexpansioncore.mine.registered"
                        : "gui.createcityexpansioncore.mine.unregistered"),
                x + 12,
                top + 318,
                snapshot.commissioned() ? GREEN : ORANGE,
                false
        );
        String projectInfo = snapshot.commissioned()
                ? tr("gui.createcityexpansioncore.mine.project_belongs_to_city")
                : commissionCityId.isPresent() ? commissionCityName : commissionMessage;
        drawFitted(graphics, projectInfo, x + 12, top + 339, x + 168, TEXT_DIM);
        String railwayInfo = railwayStationName
                .map(name -> tr("gui.createcityexpansioncore.mine.railway_station",
                        name, tr(railwayStationAvailable
                                ? "status.createcityexpansioncore.railway.available"
                                : "status.createcityexpansioncore.railway.missing")))
                .orElse(tr("gui.createcityexpansioncore.mine.railway_station_none"));
        drawFitted(graphics, railwayInfo, x + 12, top + 353, x + 168,
                railwayStationAvailable ? GREEN : TEXT_DIM);
    }

    private void drawExtractionPanel(GuiGraphics graphics, float partialTick) {
        int x = left + 20;
        int y = top + 325;
        drawPanel(graphics, x, y, 235, 155, tr("gui.createcityexpansioncore.mine.live_extraction"));
        double progress = displayedMiningProgress();
        drawMiningAnimation(graphics, x + 12, y + 35, progress, partialTick);

        String resource = snapshot.selectedDeposit()
                .map(deposit -> GeologyUiFormat.resource(deposit.resourceId()))
                .orElse(tr("gui.createcityexpansioncore.common.unavailable"));
        graphics.drawString(font, resource, x + 112, y + 40,
                snapshot.selectedDeposit().isPresent() ? CYAN : RED, false);
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.richness_value",
                        GeologyUiFormat.percent(snapshot.richness())),
                x + 112, y + 59, TEXT, false);
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.speed_value",
                        "%.1f".formatted(snapshot.productionPerMinute())),
                x + 112, y + 78, TEXT, false);
        graphics.drawString(font, extractionStatus(), x + 112, y + 97,
                stateColor(snapshot.state()), false);
        graphics.drawString(font, tr("gui.createcityexpansioncore.mine.cycle_value",
                        GeologyUiFormat.percent(progress)),
                x + 12, y + 119, TEXT_DIM, false);
        drawReserveBar(graphics, x + 12, y + 133, 211, 10, progress);
    }

    private void drawOutputPanel(GuiGraphics graphics) {
        int x = left + 265;
        int y = top + 420;
        drawPanel(graphics, x, y, 280, 60, tr("gui.createcityexpansioncore.mine.gateway_output"));
        if (!snapshot.output().available()) {
            graphics.drawString(font, tr("gui.createcityexpansioncore.mine.gateway_unavailable"),
                    x + 12, y + 36, RED, false);
        } else {
            boolean full = snapshot.output().itemCount() >= snapshot.output().totalCapacity();
            boolean blocked = snapshot.state() == IndustrialMineState.OUTPUT_BLOCKED;
            graphics.drawString(
                    font,
                    tr(blocked ? "gui.createcityexpansioncore.mine.gateway_blocked"
                                    : full ? "gui.createcityexpansioncore.mine.gateway_full"
                                    : "gui.createcityexpansioncore.mine.gateway_ready",
                            snapshot.output().itemCount(), snapshot.output().totalCapacity()),
                    x + 12,
                    y + 36,
                    full || blocked ? RED : GREEN,
                    false
            );
        }
    }

    private void drawMiningAnimation(
            GuiGraphics graphics,
            int x,
            int y,
            double progress,
            float partialTick
    ) {
        boolean running = snapshot.state() == IndustrialMineState.RUNNING
                && snapshot.electricalState() == MineElectricalState.NORMAL;
        double phase = (progress * 4.0) % 1.0;
        double impact = running && phase > 0.44 && phase < 0.62
                ? 1.0 - Math.abs(phase - 0.53) / 0.09 : 0.0;
        int shake = impact > 0.35 ? ((System.currentTimeMillis() / 45L) % 2L == 0L ? 1 : -1) : 0;

        int stoneX = x + 49 + shake;
        int stoneY = y + 48;
        graphics.fill(stoneX, stoneY, stoneX + 31, stoneY + 31, 0xFF26343A);
        graphics.fill(stoneX + 3, stoneY + 3, stoneX + 28, stoneY + 28, 0xFF3D4B50);
        graphics.fill(stoneX + 9, stoneY + 8, stoneX + 15, stoneY + 14, CYAN);
        graphics.fill(stoneX + 19, stoneY + 17, stoneX + 25, stoneY + 23, ORANGE);
        graphics.fill(stoneX + 7, stoneY + 22, stoneX + 11, stoneY + 26, 0xFF8AA0A8);

        double swing;
        if (running) {
            swing = -58.0 + 78.0 * Math.sin(phase * Math.PI);
        } else if (snapshot.state() == IndustrialMineState.STARTING) {
            swing = -32.0 + Math.sin((System.currentTimeMillis() + partialTick) / 180.0) * 5.0;
        } else {
            swing = -34.0;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + 45.0F, y + 42.0F, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) swing));
        graphics.fill(-2, -5, 2, 35, 0xFF9B6338);
        graphics.fill(-14, -7, 14, -3, 0xFFB9C6CB);
        graphics.fill(-16, -6, -11, 0, 0xFF7D8D94);
        graphics.fill(11, -6, 16, 0, 0xFF7D8D94);
        graphics.pose().popPose();

        if (impact > 0.45) {
            int alpha = Math.min(255, (int) (impact * 255.0));
            int particle = alpha << 24 | 0x00FFD36A;
            graphics.fill(stoneX - 4, stoneY + 5, stoneX - 2, stoneY + 7, particle);
            graphics.fill(stoneX + 3, stoneY - 5, stoneX + 5, stoneY - 3, particle);
            graphics.fill(stoneX + 30, stoneY - 1, stoneX + 32, stoneY + 1, particle);
        }
    }

    private void drawPowerWaveform(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF061015);
        int color = electricalColor();
        double amplitude = switch (snapshot.electricalState()) {
            case NORMAL -> 5.0;
            case LOW_VOLTAGE, INSUFFICIENT_POWER -> 2.0;
            case OVERLOAD, OVERVOLTAGE -> 7.0;
            case NO_POWER -> 0.0;
        };
        double speed = snapshot.electricalState() == MineElectricalState.OVERLOAD
                || snapshot.electricalState() == MineElectricalState.OVERVOLTAGE ? 35.0 : 80.0;
        double time = System.currentTimeMillis() / speed;
        int center = y + height / 2;
        for (int offset = 2; offset < width - 2; offset += 4) {
            int signalY = center + (int) Math.round(Math.sin(offset * 0.15 + time) * amplitude);
            graphics.fill(x + offset, signalY, x + offset + 2, signalY + 2, color);
        }
        if (amplitude > 0.0) {
            int pulse = (int) Math.floorMod(System.currentTimeMillis() / 8L, width - 6L);
            graphics.fill(x + pulse, y + 2, x + pulse + 4, y + height - 2, color & 0x66FFFFFF);
        }
    }

    private void drawPanel(
            GuiGraphics graphics,
            int x,
            int y,
            int panelWidth,
            int panelHeight,
            String title
    ) {
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        graphics.hLine(x, x + panelWidth - 1, y, BORDER);
        graphics.hLine(x, x + panelWidth - 1, y + panelHeight - 1, 0xFF17252D);
        graphics.vLine(x, y, y + panelHeight - 1, BORDER);
        graphics.vLine(x + panelWidth - 1, y, y + panelHeight - 1, 0xFF17252D);
        graphics.drawString(font, title, x + 12, y + 14, CYAN, false);
        graphics.fill(x + 12, y + 27, x + 122, y + 28, 0xFF164C5E);
    }

    private void valueLine(
            GuiGraphics graphics,
            String label,
            String value,
            int x,
            int y,
            int valueOffset
    ) {
        graphics.drawString(font, label, x, y, TEXT_DIM, false);
        graphics.drawString(font, value, x + valueOffset, y, TEXT, false);
    }

    private void drawReserveBar(
            GuiGraphics graphics,
            int x,
            int y,
            int barWidth,
            int barHeight,
            double ratio
    ) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF071015);
        int fill = (int) Math.round((barWidth - 4) * clamped);
        if (fill > 0) {
            graphics.fill(
                    x + 2,
                    y + 2,
                    x + 2 + fill,
                    y + barHeight - 2,
                    clamped <= 0.15 ? RED : GREEN
            );
        }
    }

    private void drawFitted(
            GuiGraphics graphics,
            String value,
            int x,
            int y,
            int right,
            int color
    ) {
        String fitted = value;
        while (!fitted.isEmpty() && font.width(fitted) > right - x) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (!fitted.equals(value) && fitted.length() > 3) {
            fitted = fitted.substring(0, fitted.length() - 3) + "...";
        }
        graphics.drawString(font, fitted, x, y, color, false);
    }

    private void drawScanlines(GuiGraphics graphics) {
        for (int y = top + 8; y < top + PANEL_HEIGHT - 8; y += 4) {
            graphics.fill(left + 8, y, left + PANEL_WIDTH - 8, y + 1, 0x08000000);
        }
    }

    private double displayedMiningProgress() {
        double progress = snapshot.miningProgress();
        if (snapshot.state() != IndustrialMineState.RUNNING
                || snapshot.electricalState() != MineElectricalState.NORMAL
                || snapshot.cycleDurationTicks() <= 0) {
            return progress;
        }
        double elapsedTicks = (System.nanoTime() - snapshotReceivedNanos) / 50_000_000.0;
        return Math.min(1.0, progress + elapsedTicks / snapshot.cycleDurationTicks());
    }

    private int electricalColor() {
        return switch (snapshot.electricalState()) {
            case NORMAL -> GREEN;
            case LOW_VOLTAGE, INSUFFICIENT_POWER -> 0xFFFFB347;
            case OVERLOAD, OVERVOLTAGE -> RED;
            case NO_POWER -> TEXT_DIM;
        };
    }

    private String extractionStatus() {
        return displayState(snapshot.state());
    }

    private String displayElectricalState() {
        return tr("status.createcityexpansioncore.electrical."
                + snapshot.electricalState().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int stateColor(IndustrialMineState state) {
        return switch (state) {
            case READY, RUNNING -> GREEN;
            case STOPPED -> TEXT_DIM;
            case STARTING, STOPPING, LOW_POWER, POWER_INTEGRATION_PENDING,
                    NO_POWER, DEPOSIT_DEPLETED, OUTPUT_BLOCKED -> 0xFFFFB347;
            case OVERLOAD -> RED;
            default -> RED;
        };
    }

    private static String displayState(IndustrialMineState state) {
        return tr("status.createcityexpansioncore.mine."
                + state.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
