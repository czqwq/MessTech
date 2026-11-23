package com.MessTech.common.mixin.late;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.common.ae2.IMTChannelCapacity;

import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.me.GridNode;

/**
 * Makes AE2's per-node channel limit consult {@link IMTChannelCapacity} (implemented by the two MessTech cable parts)
 * instead of {@code CHANNEL_COUNT[compressedData & 0x3]}. See {@code docs/ultra-ae2.md} §1.4.4 / §1.5.
 * <p>
 * {@code GridNode#getMaxChannels()} reports 8 for a normal cable, 32 for a dense one and {@link Integer#MAX_VALUE}
 * only when the channel feature is disabled. The two new cables need 1024 (compressed glass) and 2.1G (ultra dense)
 * instead. A node's {@code IGridBlock} is AE2's {@code AENetworkProxy}, whose {@code getMachine()} is the
 * {@code IGridProxyable} owner - for a cable part, the part itself - so the override only fires for nodes whose machine
 * implements the interface; every other node keeps AE2's value.
 */
@Mixin(value = GridNode.class, remap = false)
public abstract class MixinGridNode_ChannelCapacity {

    @Shadow(remap = false)
    @Final
    private IGridBlock gridProxy;

    @Inject(method = "getMaxChannels", at = @At("RETURN"), cancellable = true, remap = false)
    private void messtech$overrideChannelCapacity(CallbackInfoReturnable<Integer> cir) {
        final IGridHost machine = this.gridProxy.getMachine();
        if (machine instanceof IMTChannelCapacity capacity) {
            cir.setReturnValue(capacity.mtChannelCapacity());
        }
    }
}
