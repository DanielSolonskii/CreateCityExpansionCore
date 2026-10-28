package com.example.examplemod.mixin;

import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Условное применение mixin'ов к необязательным модам.
 *
 * <p>Mixin'ы в субпакете {@code mixin.simulated} целятся в классы
 * Create Simulated. Если мод не установлен, они должны молча
 * пропускаться, иначе конфиг упадёт на отсутствующих целях.
 *
 * <p>Детекция многоступенчатая, потому что этап загрузки mixin
 * конфигов ранний: ModList может быть ещё не готов, а ресурсы
 * вложенных (jar-in-jar) модов не всегда видны через getResource.
 * Проверка выполняется в {@link #shouldApplyMixin}, а не в
 * {@link #onLoad} — там классы модов ещё не видны classloader'у.
 */
public class TransportMixinPlugin implements IMixinConfigPlugin {

    private static final String SIMULATED_MIXIN_PREFIX = "com.example.examplemod.mixin.simulated.";
    private static final String SIMULATED_CLASS = "dev.simulated_team.simulated.Simulated";
    private static final String SIMULATED_CLASS_RESOURCE = "dev/simulated_team/simulated/Simulated.class";

    private Boolean simulatedPresent;

    @Override
    public void onLoad(String mixinPackage) {
        // Намеренно пусто: здесь классы модов ещё не видны.
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(SIMULATED_MIXIN_PREFIX)) {
            return true;
        }
        if (simulatedPresent == null) {
            simulatedPresent = detectSimulated();
        }
        return simulatedPresent;
    }

    private static boolean detectSimulated() {
        try {
            ModList modList = ModList.get();
            if (modList != null && modList.isLoaded("simulated")) {
                return true;
            }
        } catch (Throwable ignored) {
            // ModList ещё не построен — пробуем classloader.
        }
        try {
            Class.forName(SIMULATED_CLASS, false, TransportMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return TransportMixinPlugin.class.getClassLoader().getResource(SIMULATED_CLASS_RESOURCE) != null;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
