package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.screen.MagneticAnalyzerScreen;
import com.example.examplemod.network.GeologyAnalysisPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class HandheldInstruments {
    private static final int REPORT_AFTER_TICKS = 14;
    private static final int SWEEP_TICKS = 32;
    private static ClientLevel scanLevel;
    private static long scanStart;
    private static GeologyAnalysisPayload report;

    private HandheldInstruments() {}

    public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(CreateCityExpansionCore.MAGNETIC_ANALYZER.get(),
                    ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "measuring"),
                    (stack, level, holder, seed) -> holder != null && holder == Minecraft.getInstance().player
                            && level != null && level == scanLevel
                            && (holder.getMainHandItem() == stack || holder.getOffhandItem() == stack)
                            ? sweepAmplitude(level.getGameTime() - scanStart) : 0);
            ItemProperties.register(CreateCityExpansionCore.BLUEPRINT_TERMINAL.get(),
                    ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "projection_lens"),
                    (stack, level, holder, seed) -> holder != null && holder == Minecraft.getInstance().player
                            && (holder.getMainHandItem() == stack || holder.getOffhandItem() == stack)
                            ? BlueprintPlacement.lensState(level) : 0);
        });
    }

    private static float sweepAmplitude(long age) {
        if (age < 0 || age >= SWEEP_TICKS) return 0;
        float ramp = Math.min(1f, Math.min(age / 8f, (SWEEP_TICKS - age) / 12f));
        return ramp * ramp * (3 - 2 * ramp);
    }

    public static void beginSweep() {
        scanLevel = Minecraft.getInstance().level;
        if (scanLevel != null) scanStart = scanLevel.getGameTime();
    }

    public static void receiveReport(GeologyAnalysisPayload payload) {
        beginSweep();
        if (scanLevel != null && Minecraft.getInstance().screen == null) {
            report = payload;
        } else {
            MagneticAnalyzerScreen.open(payload);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (scanLevel == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != scanLevel) {
            scanLevel = null;
            report = null;
            return;
        }
        long age = scanLevel.getGameTime() - scanStart;
        if (report != null && age >= REPORT_AFTER_TICKS && minecraft.screen == null) {
            MagneticAnalyzerScreen.open(report);
            report = null;
        }
        if (age >= SWEEP_TICKS && report == null) scanLevel = null;
    }
}
