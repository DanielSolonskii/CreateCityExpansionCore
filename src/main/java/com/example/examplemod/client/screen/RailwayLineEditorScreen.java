package com.example.examplemod.client.screen;

import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.railway.RailLineStop;
import com.example.examplemod.railway.RailwayLineSnapshot;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Compact server-authoritative editor for a Create schedule-backed city rail line. */
public final class RailwayLineEditorScreen extends Screen {
    private final Screen parent;
    private final BlockPos pylonPos;
    private final RailwayLineSnapshot existing;
    private final List<RailwayStationSnapshot> stations;
    private final List<RailLineStop> stops;
    private RailwayTypes.LineType type;
    private boolean cyclic;
    private EditBox name;
    private String errorKey = "";
    private int nextStation;
    private int stopScroll;

    public RailwayLineEditorScreen(Screen parent, BlockPos pylonPos,
                                   RailwayLineSnapshot existing,
                                   List<RailwayStationSnapshot> stations) {
        super(Component.translatable("gui.createcityexpansioncore.railway.line_editor"));
        this.parent = parent;
        this.pylonPos = pylonPos.immutable();
        this.existing = existing;
        this.stations = stations.stream().filter(RailwayStationSnapshot::cityOwned).toList();
        this.stops = new ArrayList<>(existing == null ? List.of() : existing.stops());
        this.type = existing == null ? RailwayTypes.LineType.MIXED : existing.type();
        this.cyclic = existing == null || existing.cyclic();
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - 430) / 2;
        int top = (this.height - 300) / 2;
        this.name = new EditBox(this.font, left + 20, top + 48, 390, 22,
                Component.translatable("gui.createcityexpansioncore.railway.line_name"));
        this.name.setMaxLength(64);
        this.name.setValue(this.existing == null ? "" : this.existing.name());
        this.name.setHint(Component.translatable("gui.createcityexpansioncore.railway.line_name"));
        this.addRenderableWidget(this.name);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.createcityexpansioncore.railway.add_stop"),
                button -> addStop()).bounds(left + 20, top + 226, 125, 22).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.createcityexpansioncore.railway.change_type"),
                button -> this.type = RailwayTypes.LineType.values()[
                        (this.type.ordinal() + 1) % RailwayTypes.LineType.values().length])
                .bounds(left + 152, top + 226, 125, 22).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.createcityexpansioncore.railway.cyclic"),
                button -> this.cyclic = !this.cyclic)
                .bounds(left + 284, top + 226, 126, 22).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.createcityexpansioncore.common.cancel"),
                button -> onClose()).bounds(left + 20, top + 260, 190, 22).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.createcityexpansioncore.common.save"),
                button -> save()).bounds(left + 220, top + 260, 190, 22).build());
        this.setInitialFocus(this.name);
    }

    private void addStop() {
        if (this.stations.isEmpty() || this.stops.size() >= 64) {
            this.errorKey = "no_stations";
            return;
        }
        RailwayStationSnapshot station = this.stations.get(this.nextStation++ % this.stations.size());
        this.stops.add(new RailLineStop(station.registrationId(), RailwayTypes.StopMode.CARGO_IDLE, 5));
        this.stopScroll = Math.max(0, this.stops.size() - 6);
        this.errorKey = "";
    }

    private void save() {
        String value = this.name.getValue().trim();
        if (value.isEmpty()) {
            this.errorKey = "name_required";
            return;
        }
        if (this.stops.size() < 2) {
            this.errorKey = "two_stops_required";
            return;
        }
        PacketDistributor.sendToServer(new RailwayCommandPayload(
                this.pylonPos,
                this.existing == null ? RailwayCommandPayload.Action.CREATE_LINE
                        : RailwayCommandPayload.Action.UPDATE_LINE,
                this.existing == null ? null : this.existing.lineId(), null,
                value, this.type.ordinal(), this.cyclic, this.stops
        ));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (this.width - 430) / 2;
            int top = (this.height - 300) / 2;
            int end = Math.min(this.stops.size(), this.stopScroll + 6);
            for (int i = this.stopScroll; i < end; i++) {
                int rowY = top + 84 + (i - this.stopScroll) * 22;
                if (mouseY < rowY || mouseY >= rowY + 19) continue;
                RailLineStop current = this.stops.get(i);
                if (current.mode() == RailwayTypes.StopMode.TIMED
                        && mouseX >= left + 228 && mouseX < left + 246) {
                    this.stops.set(i, new RailLineStop(current.stationRegistrationId(), current.mode(),
                            current.waitSeconds() - 5));
                    return true;
                }
                if (current.mode() == RailwayTypes.StopMode.TIMED
                        && mouseX >= left + 250 && mouseX < left + 276) {
                    this.stops.set(i, new RailLineStop(current.stationRegistrationId(), current.mode(),
                            current.waitSeconds() + 5));
                    return true;
                }
                if (mouseX >= left + 280 && mouseX < left + 310 && i > 0) {
                    java.util.Collections.swap(this.stops, i, i - 1);
                    return true;
                }
                if (mouseX >= left + 314 && mouseX < left + 344 && i + 1 < this.stops.size()) {
                    java.util.Collections.swap(this.stops, i, i + 1);
                    return true;
                }
                if (mouseX >= left + 348 && mouseX < left + 380) {
                    RailLineStop old = this.stops.get(i);
                    RailwayTypes.StopMode mode = RailwayTypes.StopMode.values()[
                            (old.mode().ordinal() + 1) % RailwayTypes.StopMode.values().length];
                    int seconds = mode == RailwayTypes.StopMode.TIMED ? 10
                            : mode == RailwayTypes.StopMode.CARGO_IDLE ? 5 : 0;
                    this.stops.set(i, new RailLineStop(old.stationRegistrationId(), mode, seconds));
                    return true;
                }
                if (mouseX >= left + 384 && mouseX < left + 410) {
                    this.stops.remove(i);
                    this.stopScroll = Math.min(this.stopScroll, Math.max(0, this.stops.size() - 6));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int left = (this.width - 430) / 2;
        int top = (this.height - 300) / 2;
        if (mouseX >= left + 20 && mouseX < left + 410
                && mouseY >= top + 84 && mouseY < top + 216) {
            this.stopScroll = Math.clamp(this.stopScroll + (scrollY < 0 ? 1 : -1),
                    0, Math.max(0, this.stops.size() - 6));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - 430) / 2;
        int top = (this.height - 300) / 2;
        graphics.fill(left, top, left + 430, top + 300, 0xFF202426);
        graphics.fill(left, top, left + 430, top + 38, 0xFF353A3C);
        graphics.fill(left, top + 36, left + 430, top + 39, 0xFFD09A3A);
        graphics.drawString(this.font, this.title, left + 18, top + 14, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable(
                "gui.createcityexpansioncore.railway.line_options",
                Component.translatable("gui.createcityexpansioncore.railway.line_type." + this.type.name().toLowerCase()),
                this.cyclic ? Component.translatable("gui.createcityexpansioncore.common.yes")
                        : Component.translatable("gui.createcityexpansioncore.common.no")),
                left + 20, top + 73, 0xFFAEB8BB, false);
        int end = Math.min(this.stops.size(), this.stopScroll + 6);
        for (int i = this.stopScroll; i < end; i++) {
            RailLineStop stop = this.stops.get(i);
            RailwayStationSnapshot station = this.stations.stream()
                    .filter(s -> s.registrationId().equals(stop.stationRegistrationId()))
                    .findFirst().orElse(null);
            int y = top + 84 + (i - this.stopScroll) * 22;
            graphics.fill(left + 20, y, left + 410, y + 19, 0xFF292F31);
            graphics.drawString(this.font, (i + 1) + ". " + (station == null ? "?" : station.name()),
                    left + 25, y + 5, station != null && station.available() ? 0xFFE2E7E8 : 0xFFD07B72, false);
            graphics.drawString(this.font, Component.translatable(
                            "gui.createcityexpansioncore.railway.stop_mode." + stop.mode().name().toLowerCase()),
                    left + 172, y + 5, 0xFFAEB8BB, false);
            if (stop.mode() == RailwayTypes.StopMode.TIMED) {
                graphics.drawString(this.font, "−", left + 232, y + 5, 0xFFD09A3A, false);
                graphics.drawString(this.font, Component.translatable(
                                "gui.createcityexpansioncore.railway.seconds_short", stop.waitSeconds()),
                        left + 242, y + 5,
                        0xFFE2E7E8, false);
                graphics.drawString(this.font, "+", left + 264, y + 5, 0xFFD09A3A, false);
            }
            graphics.drawString(this.font, "↑", left + 291, y + 5, 0xFFD09A3A, false);
            graphics.drawString(this.font, "↓", left + 325, y + 5, 0xFFD09A3A, false);
            graphics.drawString(this.font, "↻", left + 359, y + 5, 0xFFD09A3A, false);
            graphics.drawString(this.font, "×", left + 393, y + 5, 0xFFD07872, false);
        }
        if (!this.errorKey.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable(
                    "gui.createcityexpansioncore.railway.error." + this.errorKey),
                    left + 215, top + 250, 0xFFD07872);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
