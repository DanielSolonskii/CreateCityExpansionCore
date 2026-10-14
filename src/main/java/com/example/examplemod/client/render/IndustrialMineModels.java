package com.example.examplemod.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;

@OnlyIn(Dist.CLIENT)
public final class IndustrialMineModels {

    private static final String[] PATHS = {
            "block/industrial_mine/fixed_core",
            "block/industrial_mine/power_module",
            "block/industrial_mine/main_drive",
            "block/industrial_mine/gearbox",
            "block/industrial_mine/drill_shaft",
            "block/industrial_mine/indicator_green",
            "block/industrial_mine/indicator_amber",
            "block/industrial_mine/indicator_red",
            "block/industrial_mine/output_module",
            "block/industrial_mine/power_contacts"
    };

    private static final BakedModel[] MODELS = new BakedModel[PATHS.length];
    private static boolean resolved;

    private IndustrialMineModels() {
    }

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (String path : PATHS) {
            event.register(id(path));
        }
    }

    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        resolved = false;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        for (int i = 0; i < PATHS.length; i++) {
            MODELS[i] = Minecraft.getInstance().getModelManager().getModel(id(PATHS[i]));
        }
    }

    private static ModelResourceLocation id(String path) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", path));
    }

    public static BakedModel fixedCore() { resolve(); return MODELS[0]; }
    public static BakedModel powerModule() { resolve(); return MODELS[1]; }
    public static BakedModel mainDrive() { resolve(); return MODELS[2]; }
    public static BakedModel gearbox() { resolve(); return MODELS[3]; }
    public static BakedModel drillShaft() { resolve(); return MODELS[4]; }
    public static BakedModel greenIndicator() { resolve(); return MODELS[5]; }
    public static BakedModel amberIndicator() { resolve(); return MODELS[6]; }
    public static BakedModel redIndicator() { resolve(); return MODELS[7]; }
    public static BakedModel outputModule() { resolve(); return MODELS[8]; }
    public static BakedModel powerContacts() { resolve(); return MODELS[9]; }
}
