package com.MessTech.common.ae2;

import static gregtech.api.enums.TierEU.RECIPE_IV;
import static gregtech.api.recipe.RecipeMaps.compressorRecipes;
import static gregtech.api.util.GTRecipeBuilder.HOURS;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.util.AEColor;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTUtility;

/**
 * Assembler recipes for the ultra dense AE2 content, ported to GT's compressor ({@code gt.recipe.compressor}):
 * <ul>
 * <li>1000 × AE2 ME Controller → 1 × 超致密ME控制器 (IV, 1 hour);</li>
 * <li>1000 × AE2 ME中子注入控制器 ({@code BlockCreativeEnergyController}) → 1 × 超致密ME中子注入控制器 (IV, 1 hour);</li>
 * <li>64 × AE2 致密线缆 (per colour) → 1 × 超致密压缩ME线缆 (IV, 1 hour);</li>
 * <li>64 × AE2 玻璃线缆 (per colour) → 1 × 压缩ME玻璃线缆 (IV, 1 hour).</li>
 * </ul>
 * The controller recipes use 1000 inputs, so they can only be supplied by a GT multiblock compressor (a single block
 * compressor has one input slot, i.e. at most 64 items); the cable recipes fit a single slot.
 */
public final class MTUltraAeRecipes {

    private MTUltraAeRecipes() {}

    /** Registers everything; called from {@code GTRecipes#loadRecipes} before the rest of the GT recipe pass. */
    public static void load() {
        addControllerRecipes();
        addCableRecipes();
    }

    private static void addControllerRecipes() {
        ItemStack meController = AEApi.instance()
            .definitions()
            .blocks()
            .controller()
            .maybeStack(1)
            .orNull();
        if (meController != null) {
            GTValues.RA.stdBuilder()
                .itemInputsUnsafe(GTUtility.copyAmountUnsafe(1000, meController))
                .itemOutputs(new ItemStack(MTUltraDenseControllerBlock.INSTANCE, 1))
                .eut(RECIPE_IV)
                .duration(1 * HOURS)
                .addTo(compressorRecipes);
        }

        ItemStack neutronController = AEApi.instance()
            .definitions()
            .blocks()
            .creativeEnergyController()
            .maybeStack(1)
            .orNull();
        if (neutronController != null) {
            GTValues.RA.stdBuilder()
                .itemInputsUnsafe(GTUtility.copyAmountUnsafe(1000, neutronController))
                .itemOutputs(new ItemStack(MTUltraDenseNeutronControllerBlock.INSTANCE, 1))
                .eut(RECIPE_IV)
                .duration(1 * HOURS)
                .addTo(compressorRecipes);
        }
    }

    private static void addCableRecipes() {
        for (AEColor color : MTUltraDenseCables.colors()) {
            GTValues.RA.stdBuilder()
                .itemInputs(MTUltraDenseCables.ae2DenseCable(color, 64))
                .itemOutputs(MTUltraDenseCables.ultraDenseStack(color))
                .eut(RECIPE_IV)
                .duration(1 * HOURS)
                .addTo(compressorRecipes);

            GTValues.RA.stdBuilder()
                .itemInputs(MTUltraDenseCables.ae2GlassCable(color, 64))
                .itemOutputs(MTUltraDenseCables.compressedGlassStack(color))
                .eut(RECIPE_IV)
                .duration(1 * HOURS)
                .addTo(compressorRecipes);
        }
    }
}
