package com.example.examplemod.machine;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

public final class GoggleTooltip {
    private GoggleTooltip() {}

    public static void add(List<Component> tooltip, Component line, int indent) {
        // Create computes the padding from the active client font.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CreateLang.builder().add(line).forGoggles(tooltip, indent);
        } else {
            tooltip.add(line);
        }
    }
}
