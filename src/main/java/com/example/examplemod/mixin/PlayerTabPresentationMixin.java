package com.example.examplemod.mixin;

import com.example.examplemod.client.hud.QuietPlayerTab;
import com.example.examplemod.hud.TabCityLabel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Presentation only; vanilla retains scoreboard objectives and other servers overlays. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabPresentationMixin {
    @Shadow private Component header;
    @Shadow protected abstract List<PlayerInfo> getPlayerInfos();

    @Inject(method="getPlayerInfos",at=@At("RETURN"),cancellable=true)
    private void ccec$order(CallbackInfoReturnable<List<PlayerInfo>> callback) {
        if(!ccec$enabled())return;
        callback.setReturnValue(QuietPlayerTab.sorted(callback.getReturnValue()));
    }

    private boolean ccec$enabled() {
        return header!=null && header.getContents() instanceof TranslatableContents text
                && TabCityLabel.HEADER.equals(text.getKey());
    }

    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void ccec$render(GuiGraphics graphics,int width,Scoreboard scoreboard,Objective objective,CallbackInfo callback) {
        if(objective!=null || !ccec$enabled())return;
        QuietPlayerTab.render(graphics,getPlayerInfos(),header);
        callback.cancel();
    }

    @ModifyVariable(method="setFooter",at=@At("HEAD"),argsOnly=true)
    private Component ccec$keys(Component footer) {
        return ccec$enabled() ? QuietPlayerTab.hints() : footer;
    }
}
