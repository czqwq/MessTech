package com.MessTech.common.recipe;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;

/**
 * Reads the GT programming circuits an {@code MTAssFactory}'s Assembly Line matching may use as conflict selectors.
 * <p>
 * A selector can sit in three places, all of them GT5U conventions:
 * <ul>
 * <li>an ordinary input bus' ghost circuit slot. The bus stores the configured circuit with stack size 0 (see
 * {@code MetaTileEntity#setInventorySlotContents}) and the ME and compressed buses expose that slot through
 * {@code getStackInSlot} during the recipe-processing window, so the scan walks the highest slots first and stops at
 * the first zero-sized integrated circuit - a bus contributes at most one number;</li>
 * <li>a dual input hatch's own circuit slot ({@link IConfigurationCircuitSupport}, e.g. the Crafting Input Buffer's
 * ghost circuit). {@code MTEMultiBlockBase#doCheckRecipe} routes such hatches into {@code mDualInputHatches} instead of
 * {@code mInputBusses}, so the bus scan above cannot see them;</li>
 * <li>a zero-sized integrated circuit among the current recipe input items. This is the same CRIB / programmable
 * pattern hatch circuit once more: GT5U adds an {@link IDualInputHatch}'s shared items - its circuit slot included - to
 * the item array handed to the recipe check, and the pattern cache
 * ({@code ProcessingLogic#tryCachePossibleRecipesFromPattern}
 * via {@code getPatternInputs}) carries them too. Reading the array covers every dual input hatch without special
 * casing.</li>
 * </ul>
 * A <b>physical</b> circuit stack (stack size above 0) is a recipe ingredient, not a configuration: it is ignored, the
 * same way GT5U tells non-consumed circuit inputs apart.
 */
public final class MTAssemblyLineCircuitSelectors {

    private MTAssemblyLineCircuitSelectors() {}

    /** Union of all three sources; this is what the machine hands to {@code MTAssemblyLineMatcher}. */
    public static Set<Integer> resolve(@Nullable Collection<? extends MTEHatchInputBus> inputBuses,
        @Nullable Collection<IDualInputHatch> dualInputHatches, @Nullable ItemStack[] inputItems) {
        Set<Integer> circuits = new HashSet<>(4);
        collectFromInputBuses(circuits, inputBuses);
        collectFromDualInputHatches(circuits, dualInputHatches);
        collectFromInputItems(circuits, inputItems);
        return circuits;
    }

    /** Only the ordinary input buses' ghost circuit slots. */
    public static Set<Integer> fromInputBuses(@Nullable Collection<? extends MTEHatchInputBus> inputBuses) {
        Set<Integer> circuits = new HashSet<>(4);
        collectFromInputBuses(circuits, inputBuses);
        return circuits;
    }

    /** Only the zero-sized integrated circuits among the recipe input items (the Crafting Input Buffer path). */
    public static Set<Integer> fromInputItems(@Nullable ItemStack[] inputItems) {
        Set<Integer> circuits = new HashSet<>(4);
        collectFromInputItems(circuits, inputItems);
        return circuits;
    }

    /**
     * Whether the stack is a GT ghost circuit - a configured integrated circuit, as opposed to a physical circuit
     * stack that a recipe consumes.
     */
    public static boolean isSelector(@Nullable ItemStack stack) {
        return stack != null && stack.stackSize == 0 && GTUtility.isAnyIntegratedCircuit(stack);
    }

    private static void collectFromInputBuses(Set<Integer> circuits,
        @Nullable Collection<? extends MTEHatchInputBus> inputBuses) {
        if (inputBuses == null) return;
        for (MTEHatchInputBus bus : inputBuses) {
            if (bus == null) continue;
            for (int slot = bus.getSizeInventory() - 1; slot >= 0; slot--) {
                ItemStack stack = bus.getStackInSlot(slot);
                if (!isSelector(stack)) continue;
                circuits.add(stack.getItemDamage());
                break;
            }
        }
    }

    private static void collectFromDualInputHatches(Set<Integer> circuits,
        @Nullable Collection<IDualInputHatch> dualInputHatches) {
        if (dualInputHatches == null) return;
        for (IDualInputHatch hatch : dualInputHatches) {
            if (!(hatch instanceof MetaTileEntity machine)) continue;
            if (!(machine instanceof IConfigurationCircuitSupport support) || !support.allowSelectCircuit()) continue;
            final int slot = support.getCircuitSlot();
            if (slot < 0 || slot >= machine.getSizeInventory()) continue;
            ItemStack stack = machine.getStackInSlot(slot);
            if (isSelector(stack)) circuits.add(stack.getItemDamage());
        }
    }

    private static void collectFromInputItems(Set<Integer> circuits, @Nullable ItemStack[] inputItems) {
        if (inputItems == null) return;
        for (ItemStack stack : inputItems) {
            if (!isSelector(stack)) continue;
            circuits.add(stack.getItemDamage());
        }
    }
}
