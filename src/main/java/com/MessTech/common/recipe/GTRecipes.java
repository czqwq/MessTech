package com.MessTech.common.recipe;

import static goodgenerator.loader.Loaders.compactFusionCoil;
import static gregtech.api.casing.Casings.NanochipFirewallProjectionCasing;
import static gregtech.api.casing.Casings.NanochipMeshInterfaceCasing;
import static gregtech.api.enums.TierEU.RECIPE_UEV;
import static gregtech.api.enums.TierEU.RECIPE_UHV;
import static gregtech.api.enums.TierEU.RECIPE_UV;
import static gregtech.api.enums.TierEU.RECIPE_ZPM;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;
import static tectech.thing.CustomItemList.DATApipe;
import static tectech.thing.CustomItemList.Machine_Multi_Computer;
import static tectech.thing.CustomItemList.Machine_Multi_DataBank;
import static tectech.thing.CustomItemList.UncertaintyX_Hatch;
import static tectech.thing.CustomItemList.rack_Hatch;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

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
import gtPlusPlus.xmod.thermalfoundation.fluid.TFFluids;

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

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(NanochipMeshInterfaceCasing.getItem(), 8, 1),
                UncertaintyX_Hatch.get(64),
                Machine_Multi_Computer.get(8),
                Machine_Multi_DataBank.get(1),
                DATApipe.get(64),
                new Object[] { OrePrefixes.circuit.get(Materials.UIV), 4 },
                new Object[] { OrePrefixes.circuit.get(Materials.UEV), 64 })
            .fluidInputs(new FluidStack(TFFluids.fluidCryotheum, 1000000))
            .itemOutputs(MTItemList.MTComputingCenter.get(1))
            .eut(RECIPE_UEV)
            .duration(128 * 20)
            .requiresCleanRoom()
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                rack_Hatch.get(1),
                new ItemStack(NanochipFirewallProjectionCasing.getItem(), 1, 4),
                new Object[] { OrePrefixes.circuit.get(Materials.UEV), 2 })
            .itemOutputs(MTItemList.MTHatchRack.get(1))
            .eut(RECIPE_UHV)
            .duration(32 * 20)
            .requiresCleanRoom()
            .addTo(assemblerRecipes);
    }
}
