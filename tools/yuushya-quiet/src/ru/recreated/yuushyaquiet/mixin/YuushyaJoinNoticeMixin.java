package ru.recreated.yuushyaquiet.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only Yuushya 2.3.0's unconditional server join disclaimer. */
@Mixin(targets = "com.yuushya.Yuushya", remap = false)
abstract class YuushyaJoinNoticeMixin {
    @Inject(method = "lambda$init$4(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void recreated$quietJoin(CallbackInfo callback) {
        callback.cancel();
    }
}
