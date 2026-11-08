package ru.recreated.yuushyaquiet;

import java.lang.reflect.Modifier;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run explicitly during packaging; never included in the game artifact. */
public final class VerifyQuiet {
    public static void main(String[] args) throws Exception {
        var method = Class.forName("ru.recreated.yuushyaquiet.mixin.YuushyaJoinNoticeMixin").getDeclaredMethod("recreated$quietJoin", CallbackInfo.class);
        if (!Modifier.isStatic(method.getModifiers())) throw new AssertionError("Handler must be static");
        method.setAccessible(true);
        var callback = new CallbackInfo("join", true);
        method.invoke(null, callback);
        if (!callback.isCancelled()) throw new AssertionError("Disclaimer not cancelled");
        System.out.println("PASS: isolated join-disclaimer callback cancels");
    }
}
