package com.MessTech.common.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.MessTech.common.ae2.MTUltraDenseControllers;

import appeng.api.networking.IGrid;
import appeng.me.pathfinding.PathingCalculation;

/**
 * Detects whether the grid being path calculated contains an ultra dense ME controller, so that
 * {@link MixinControllerFace_UltraDense} can raise the per face channel capacity of every controller face of that
 * calculation.
 *
 * <p>
 * AE2's {@code PathingCalculation} constructor creates the {@code ControllerFace} instances of the grid: one per
 * controller connection/face, seeded with {@code controllerFaceCapacity} (32, or {@code Integer.MAX_VALUE} when the
 * channel feature is disabled). Those instances are created deep inside the two controller loops and the capacity
 * argument is a private field, so the cleanest place to hook is the constructor: the HEAD injection scans the grid's
 * controller machine sets for one of our blocks and publishes the result on the current thread through
 * {@link MTUltraDenseControllers}, the RETURN injection clears it again.
 *
 * <p>
 * The two {@code getMachines(...)} sets have to be scanned separately: {@code IGrid#getMachines(Class)} is keyed by
 * the exact machine class, and although {@code TileCreativeEnergyController} extends {@code TileController} it is not
 * part of the {@code TileController} set. The flag is thread local because only the thread performing the calculation
 * (the server thread; pathing is triggered from the grid tick) and the {@code ControllerFace} constructors it runs are
 * involved. A leftover flag after an exception in the constructor is accepted: the next calculation resets it.
 */
@Mixin(value = PathingCalculation.class, remap = false)
public abstract class MixinPathingCalculation_UltraDense {

    /**
     * Runs before {@code super()} of the constructor, so the handler <b>must be static</b>: mixin rejects an instance
     * handler there ({@code InvalidInjectionException: handler before super() invocation must be static}). It only
     * needs the {@code grid} argument, so nothing is lost.
     */
    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private static void messtech$beginUltraDensePathing(IGrid grid, CallbackInfo callbackInfo) {
        MTUltraDenseControllers.beginUltraDensePathing(MTUltraDenseControllers.hasUltraDenseController(grid));
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void messtech$endUltraDensePathing(IGrid grid, CallbackInfo callbackInfo) {
        MTUltraDenseControllers.endUltraDensePathing();
    }
}
