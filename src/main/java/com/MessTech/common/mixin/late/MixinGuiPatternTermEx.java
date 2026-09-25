package com.MessTech.common.mixin.late;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.MessTech.common.parts.PartUltimatePatternTerminal;

import appeng.client.gui.implementations.GuiPatternTermEx;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.implementations.ContainerPatternTermEx;

/**
 * Widens AE's extended pattern terminal scroll bar to the Ultimate Pattern Terminal's own input page count.
 * <p>
 * {@code GuiPatternTermEx} hardcodes its private {@code processingScrollBar} to {@code setRange(0, 1, 1)} - two pages -
 * while its slot arrangement already loops over whatever {@code getPatternInputPages} answers. This redirect replaces
 * that one call with the page count of the terminal the GUI was opened on, so the Ultimate Pattern Terminal (eight
 * pages) can actually scroll to them.
 * <p>
 * The redirect fires for <em>every</em> {@code GuiPatternTermEx}, AE's own extended pattern terminal and any addon's
 * included, so it must hand AE's call straight back unless the container belongs to this mod's terminal: AE's own
 * GUI owes its scroll range to AE, and silently re-implementing the call for it would break that terminal the day AE
 * (or an addon such as AE2FC, whose fluid pattern terminal uses this same GUI) answers something other than its
 * hardcoded two pages. {@code MixinContainerPatternTermEx} makes the same check on the container side.
 */
@Mixin(value = GuiPatternTermEx.class, remap = false)
public abstract class MixinGuiPatternTermEx {

    @Redirect(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/widgets/GuiScrollbar;setRange(III)V"),
        remap = false)
    private void messTech$scrollThroughAllInputPages(GuiScrollbar bar, int min, int max, int pageSize) {
        // The target constructor has already stored its container in the GUI's own inventorySlots field.
        final Container slots = ((GuiContainer) (Object) this).inventorySlots;
        if (slots instanceof ContainerPatternTermEx ex
            && ex.getPatternTerminal() instanceof PartUltimatePatternTerminal) {
            // The count comes from the container because that is what GuiPatternTermEx lays its slots out from; the
            // container takes it from the part (MixinContainerPatternTermEx).
            bar.setRange(min, Math.max(min, ex.getPatternInputPages() - 1), pageSize);
            return;
        }

        // AE's own extended pattern terminal, and every other GUI on this class: AE's own call, untouched.
        bar.setRange(min, max, pageSize);
    }
}
