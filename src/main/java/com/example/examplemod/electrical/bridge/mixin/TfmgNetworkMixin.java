package com.example.examplemod.electrical.bridge.mixin;

import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.example.examplemod.electrical.bridge.TfmgBridgePort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork", remap = false)
abstract class TfmgNetworkMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="add",at=@At("HEAD"),cancellable=true)
    private void ccec$realMember(com.drmangotea.tfmg.content.electricity.base.IElectric member,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var real=member instanceof com.example.examplemod.electrical.bridge.TfmgTerminal terminal ? terminal.port() : member;
        if (!(real instanceof TfmgBridgePort)) return;
        // Native add deduplicates by network ID, which cable traversal has already changed.
        // A converter must be counted once by its physical position, not mistaken for the bus.
        var members=((ElectricalNetwork)(Object)this).getMembers();
        if (members.stream().noneMatch(existing->existing.getPos()==real.getPos())) members.add(real);
        ci.cancel();
    }
    @ModifyVariable(method = "updateNetwork", index = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0))
    private int ccec$limitBridgeVoltage(int voltage) {
        return TfmgBridgePort.limitVoltage((ElectricalNetwork) (Object) this, voltage);
    }

    @ModifyVariable(method = "updateNetwork", index = 3,
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0))
    private int ccec$countEnergizedSources(int generation) {
        return TfmgBridgePort.limitCapacity((ElectricalNetwork) (Object) this, generation);
    }
}
