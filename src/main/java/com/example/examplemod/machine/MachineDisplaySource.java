package com.example.examplemod.machine;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import java.util.List;
import java.util.Locale;

/** Future structures publish the same instruments without their own menus or tooltip renderers. */
public interface MachineDisplaySource extends IHaveGoggleInformation {
    enum Role implements StringRepresentable {
        CONTROLLER, ENERGY, OUTPUT;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    MachineTelemetry telemetry();
    Role displayRole();

    @Override
    default boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        var t = telemetry();
        GoggleTooltip.add(tooltip, Component.translatable("gui.createcityexpansioncore.machine."
                + displayRole().getSerializedName()).withStyle(ChatFormatting.GOLD), 0);
        if (displayRole() == Role.CONTROLLER || !t.assembled()) {
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.tier", t.tier()));
            if (!t.message().isBlank()) detail(tooltip, Component.literal(t.message()));
            return true;
        }
        if (displayRole() == Role.ENERGY) {
            var p = t.power();
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.voltage", number(p.voltage()), number(p.minVoltage()), number(p.maxVoltage())));
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.current", number(p.current()), number(p.minCurrent()), number(p.maxCurrent())));
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.power", number(p.watts()/1000), number(p.nominalWatts()/1000)));
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.safe_power", number(p.minWatts()/1000), number(p.maxWatts()/1000)));
        } else {
            var p = t.production();
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.rate", number(p.perMinute())));
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.cycle", number(p.cycleSeconds()), number(p.progress()*100)));
            detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.stored", p.stored(), p.capacity()));
        }
        detail(tooltip, Component.translatable("gui.createcityexpansioncore.machine.state." + t.state().toLowerCase(Locale.ROOT)));
        return true;
    }

    private static void detail(List<Component> tooltip, net.minecraft.network.chat.MutableComponent line) {
        GoggleTooltip.add(tooltip, line.withStyle(ChatFormatting.GRAY), 1);
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
