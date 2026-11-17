package com.example.examplemod.electrical.bridge.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class PowerMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!target.startsWith("com.drmangotea.tfmg.")) return true;
        var file = LoadingModList.get().getModFileById("tfmg");
        return file != null && file.getMods().stream()
                .anyMatch(mod -> mod.getModId().equals("tfmg") && mod.getVersion().toString().equals("1.2.0"));
    }
}
