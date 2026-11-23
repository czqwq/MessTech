package com.MessTech.common.recipe;

import static gregtech.api.enums.TierEU.RECIPE_EV;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.MessTech.common.wireless.MTWirelessContent;

import appeng.api.AEApi;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/**
 * Assembler recipes of the wireless ME network content, ported from GTLCore's
 * {@code MachineRecipe.java:918-936}:
 *
 * <ul>
 * <li>core: EV hull + 4 AE2 wireless hubs + 4 quantum bridges + 36 quantum rings + platinum hex wire + 8 LuV
 * circuits + an EV field generator + 1000 mB radon, 400t at EV;</li>
 * <li>tool: the AE2 wireless kit + an EV circuit + an EV emitter, 200t at EV.</li>
 * </ul>
 *
 * Two ingredients are mapped to their 1.7.10 counterparts: GT Modern's {@code wireGtHex} is GT5U's
 * {@code wireGt16}, and GT Modern's {@code UraniumTriplatinum} wire does not exist in GT5U 5.09.54.264, so the same
 * EV-tier role is filled with platinum.
 */
public final class MTWirelessRecipes {

    private MTWirelessRecipes() {}

    public static void load() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                nonNull(
                    ItemList.Hull_EV.get(1),
                    ae2Block(
                        "wirelessHub()",
                        AEApi.instance()
                            .definitions()
                            .blocks()
                            .wirelessHub()
                            .maybeStack(4)),
                    ae2Block(
                        "quantumLink()",
                        AEApi.instance()
                            .definitions()
                            .blocks()
                            .quantumLink()
                            .maybeStack(4)),
                    ae2Block(
                        "quantumRing()",
                        AEApi.instance()
                            .definitions()
                            .blocks()
                            .quantumRing()
                            .maybeStack(36)),
                    GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.Platinum, 2),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.LuV, 8),
                    ItemList.Field_Generator_EV.get(1)))
            .fluidInputs(Materials.Radon.getGas(1000))
            .itemOutputs(MTWirelessContent.coreStack())
            .duration(400)
            .eut(RECIPE_EV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                nonNull(
                    ae2Item(
                        "toolWirelessKit()",
                        AEApi.instance()
                            .definitions()
                            .items()
                            .toolWirelessKit()
                            .maybeStack(1)),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 1),
                    ItemList.Emitter_EV.get(1)))
            .itemOutputs(MTWirelessContent.toolStack())
            .duration(200)
            .eut(RECIPE_EV)
            .addTo(assemblerRecipes);
    }

    /** AE2 definitions hand out Guava {@code Optional}; a missing definition is skipped instead of crashing. */
    private static ItemStack ae2Block(String name, com.google.common.base.Optional<ItemStack> stack) {
        return ae2Item(name, stack);
    }

    private static ItemStack ae2Item(String name, com.google.common.base.Optional<ItemStack> stack) {
        if (stack == null || !stack.isPresent()) {
            com.MessTech.init.MessTech.LOG
                .warn("AE2 definition {} is unavailable; skipping it in the wireless recipe", name);
            return null;
        }
        return stack.get();
    }

    private static ItemStack[] nonNull(ItemStack... stacks) {
        List<ItemStack> list = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            if (stack != null) list.add(stack);
        }
        return list.toArray(new ItemStack[0]);
    }
}
