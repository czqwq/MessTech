package com.MessTech.common.mixin.late;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.MessTech.common.ae2.MTUltraDenseControllers;

/**
 * Raises the per face channel capacity of an AE2 controller face to
 * {@link MTUltraDenseControllers#ULTRA_DENSE_CONTROLLER_FACE_CHANNELS} when the pathing calculation was started for a
 * grid containing an ultra dense ME controller.
 *
 * <p>
 * {@code PathingCalculation.ControllerFace} is a private static nested class of
 * {@code appeng.me.pathfinding.PathingCalculation}; its {@code capacity} is a private final field filled from the
 * constructor argument ({@code 32} normally). The capacity is read through {@code canUseChannel()}, and the instance
 * itself is created by {@code PathingCalculation}'s constructor inside {@code EnumMap#computeIfAbsent} lambdas, so
 * neither the field nor the call site is reachable for a normal {@code @Redirect}/{@code @ModifyArg} injection. The
 * shadow field below is therefore marked {@link Mutable}: with it Mixin allows the RETURN injection to overwrite the
 * final field after the constructor body assigned the stock value. Writing the field inside {@code <init>} is legal
 * bytecode, which is exactly where this injection is merged.
 *
 * <p>
 * The flag is published on the current thread by {@link MixinPathingCalculation_UltraDense}: it is set when the
 * calculation starts, {@code ControllerFace} instances are created inside that constructor, and it is cleared when the
 * calculation is done. Faces created for any other grid keep AE2's native 32 channels.
 */
@Mixin(targets = "appeng.me.pathfinding.PathingCalculation$ControllerFace", remap = false)
public abstract class MixinControllerFace_UltraDense {

    @Shadow(remap = false)
    @Final
    @Mutable
    private int capacity;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void messtech$raiseControllerFaceCapacity(int initialCapacity, CallbackInfo callbackInfo) {
        if (!MTUltraDenseControllers.isUltraDensePathing()) return;
        this.capacity = MTUltraDenseControllers.ULTRA_DENSE_CONTROLLER_FACE_CHANNELS;
    }
}
