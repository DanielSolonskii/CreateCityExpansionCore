package com.example.examplemod.client.screen;

import com.example.examplemod.geology.DepositStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.Locale;

final class GeologyUiFormat {

    private GeologyUiFormat() {
    }

    static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String percent(double ratio) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        return String.format(Locale.ROOT, "%.1f%%", clamped * 100.0);
    }

    static String resource(ResourceLocation id) {
        String key = "resource.createcityexpansioncore." + id.getPath();
        String translated = Component.translatable(key).getString();
        return translated.equals(key)
                ? id.getPath().replace('_', ' ').toUpperCase(Locale.ROOT)
                : translated;
    }

    static String stage(DepositStage stage) {
        return Component.translatable(
                "stage.createcityexpansioncore.deposit."
                        + stage.name().toLowerCase(Locale.ROOT)
        ).getString();
    }
}
