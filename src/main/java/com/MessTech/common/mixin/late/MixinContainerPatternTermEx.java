package com.MessTech.common.mixin.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.common.parts.PartUltimatePatternTerminal;

import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;

/**
 * Lets AE's extended pattern terminal container use the input page count of the terminal it was opened on.
 * <p>
 * {@code ContainerPatternTermEx} answers {@code getPatternInputPages} with the constant
 * {@code PartPatternTerminalEx.exPatternInputsPages} (= 2) - it is inlined into the method, so it cannot be changed by
 * subclassing the part. {@code GuiPatternTerm} sizes the whole virtual input grid from that answer, so without this
 * the Ultimate Pattern Terminal (8 input pages) would only ever see the first two.
 * <p>
 * The part check is what keeps this away from every other extended pattern terminal: AE's own terminal, AE2FC's fluid
 * one and any addon's keep the container's own answer, because only this mod's part is a
 * {@link PartUltimatePatternTerminal}. {@code MixinGuiPatternTermEx} asks the same question before it touches that
 * terminal's scroll bar.
 */
@Mixin(value = ContainerPatternTermEx.class, remap = false)
public abstract class MixinContainerPatternTermEx {

    @Inject(method = "getPatternInputPages", at = @At("HEAD"), cancellable = true, remap = false)
    private void messTech$inputPagesFromPart(CallbackInfoReturnable<Integer> cir) {
        if (((ContainerPatternTerm) (Object) this)
            .getPatternTerminal() instanceof PartUltimatePatternTerminal terminal) {
            cir.setReturnValue(terminal.getPatternInputPages());
        }
    }
}
