package com.MessTech.common.recipe;

import static goodgenerator.loader.Loaders.compactFusionCoil;
import static gregtech.api.enums.TierEU.RECIPE_UV;
import static gregtech.api.enums.TierEU.RECIPE_ZPM;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;

import net.minecraft.item.ItemStack;

import com.MessTech.common.misc.MTItemList;

import goodgenerator.items.GGMaterial;
import goodgenerator.loader.Loaders;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.core.material.MaterialsAlloy;

public class GTRecipes {

    public static void loadRecipes() {
        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, GTOreDictUnificator.get(OrePrefixes.wireGt12, Materials.SuperconductorLuV, 1))
            .metadata(SCANNING, new Scanning(10 * MINUTES, RECIPE_ZPM))
            .itemInputs(
                new ItemStack(compactFusionCoil, 64, 1),
                new ItemStack(Loaders.LFC[0].getItem(), 64, 32019),
                GTOreDictUnificator.get(OrePrefixes.plateDense, GGMaterial.marCeM200.getGTMaterial(), 64),
                ItemList.Field_Generator_LuV.get(64),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.NaquadahAlloy, 64),
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                GTOreDictUnificator.get(OrePrefixes.stickLong, GGMaterial.marCeM200.getGTMaterial(), 64),
                ItemList.Circuit_Wafer_UHPIC.get(64),
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.SuperconductorLuV, 64))
            .fluidInputs(
                (MaterialsAlloy.INDALLOY_140.getFluidStack(144 * 1024)),
                (Materials.VanadiumGallium.getMolten(9216)))
            .itemOutputs(MTItemList.MTDTPF.get(1))
            .eut(RECIPE_UV)
            .duration(600 * 20)
            .addTo(AssemblyLine);
    }
}
