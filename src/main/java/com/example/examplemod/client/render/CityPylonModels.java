package com.example.examplemod.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Реестр дополнительных моделей City Pylon.
 *
 * <p>Модели City Pylon не связаны ни с одним blockstate, поэтому Minecraft
 * не считает их загруженными. Здесь они регистрируются через
 * {@link ModelEvent.RegisterAdditional} и кэшируются после bake.
 *
 * <p>Ссылки на {@link BakedModel} обновляются только при перезагрузке
 * ресурсов. В игровом цикле обращений к ModelManager нет.
 */
@OnlyIn(Dist.CLIENT)
public final class CityPylonModels {

    public static final String NAMESPACE = "createcityexpansioncore";

    /**
     * Опорная модель, которую рисует обычный blockstate.
     * Она же используется как BlockState для выборки quads.
     */
    private static final String STATIC_BODY = "block/city_pylon/static_body";

    private static final String[] ADDITIONAL_MODELS = {
            "block/city_pylon/screen_surface",
            "block/city_pylon/screen_surface_emissive",
            "block/city_pylon/status_lamp_green",
            "block/city_pylon/status_lamp_green_emissive",
            "block/city_pylon/status_lamp_amber",
            "block/city_pylon/status_lamp_amber_emissive",
            "block/city_pylon/status_lamp_red",
            "block/city_pylon/status_lamp_red_emissive",
            "block/city_pylon/top_indicator",
            "block/city_pylon/top_indicator_emissive",
            "block/city_pylon/top_ring",
            "block/city_pylon/small_gear",
            "block/city_pylon/selector_knob"
    };

    /*
     * =========================================
     * PIVOT (единицы модели 1/16 блока)
     * =========================================
     *
     * Взяты из integration_manifest.json, предоставленного Astra.
     */

    public static final double PIVOT_TOP_RING_X = 8.0D / 16.0D;
    public static final double PIVOT_TOP_RING_Y = 14.3D / 16.0D;
    public static final double PIVOT_TOP_RING_Z = 9.0D / 16.0D;

    public static final double PIVOT_SMALL_GEAR_X = 10.6D / 16.0D;
    public static final double PIVOT_SMALL_GEAR_Y = 4.8D / 16.0D;
    public static final double PIVOT_SMALL_GEAR_Z = 3.6D / 16.0D;

    public static final double PIVOT_SELECTOR_KNOB_X = 6.1D / 16.0D;
    public static final double PIVOT_SELECTOR_KNOB_Y = 7.25D / 16.0D;
    public static final double PIVOT_SELECTOR_KNOB_Z = 2.6D / 16.0D;

    private static BakedModel topRing;
    private static BakedModel smallGear;
    private static BakedModel selectorKnob;
    private static BakedModel screenSurface;
    private static BakedModel screenSurfaceEmissive;
    private static BakedModel lampGreen;
    private static BakedModel lampGreenEmissive;
    private static BakedModel lampAmber;
    private static BakedModel lampAmberEmissive;
    private static BakedModel lampRed;
    private static BakedModel lampRedEmissive;
    private static BakedModel topIndicator;
    private static BakedModel topIndicatorEmissive;
    private static boolean resolved;

    private CityPylonModels() {
    }

    /**
     * Сообщает загрузчику моделей, что эти JSON нужно загрузить.
     */
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (String path : ADDITIONAL_MODELS) {
            event.register(
                    ModelResourceLocation.standalone(
                            ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)
                    )
            );
        }
    }

    /**
     * Сбрасывает кэш перед новым bake.
     */
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        resolved = false;
    }

    /**
     * Ленивое разрешение ссылок. Вызывается один раз после загрузки ресурсов.
     */
    public static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        topRing = get(ADDITIONAL_MODELS[10]);
        smallGear = get(ADDITIONAL_MODELS[11]);
        selectorKnob = get(ADDITIONAL_MODELS[12]);
        screenSurface = get(ADDITIONAL_MODELS[0]);
        screenSurfaceEmissive = get(ADDITIONAL_MODELS[1]);
        lampGreen = get(ADDITIONAL_MODELS[2]);
        lampGreenEmissive = get(ADDITIONAL_MODELS[3]);
        lampAmber = get(ADDITIONAL_MODELS[4]);
        lampAmberEmissive = get(ADDITIONAL_MODELS[5]);
        lampRed = get(ADDITIONAL_MODELS[6]);
        lampRedEmissive = get(ADDITIONAL_MODELS[7]);
        topIndicator = get(ADDITIONAL_MODELS[8]);
        topIndicatorEmissive = get(ADDITIONAL_MODELS[9]);
    }

    private static BakedModel get(String path) {
        return Minecraft.getInstance().getModelManager().getModel(
                ModelResourceLocation.standalone(
                        ResourceLocation.fromNamespaceAndPath(NAMESPACE, path)
                )
        );
    }

    public static ResourceLocation staticBodyId() {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, STATIC_BODY);
    }

    public static BakedModel topRing() {
        resolve();
        return topRing;
    }

    public static BakedModel smallGear() {
        resolve();
        return smallGear;
    }

    public static BakedModel selectorKnob() {
        resolve();
        return selectorKnob;
    }

    public static BakedModel screenSurface() {
        resolve();
        return screenSurface;
    }

    public static BakedModel screenSurfaceEmissive() {
        resolve();
        return screenSurfaceEmissive;
    }

    public static BakedModel lampGreen() {
        resolve();
        return lampGreen;
    }

    public static BakedModel lampGreenEmissive() {
        resolve();
        return lampGreenEmissive;
    }

    public static BakedModel lampAmber() {
        resolve();
        return lampAmber;
    }

    public static BakedModel lampAmberEmissive() {
        resolve();
        return lampAmberEmissive;
    }

    public static BakedModel lampRed() {
        resolve();
        return lampRed;
    }

    public static BakedModel lampRedEmissive() {
        resolve();
        return lampRedEmissive;
    }

    public static BakedModel topIndicator() {
        resolve();
        return topIndicator;
    }

    public static BakedModel topIndicatorEmissive() {
        resolve();
        return topIndicatorEmissive;
    }
}
