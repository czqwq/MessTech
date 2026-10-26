package com.MessTech.common.recipe;

import static gregtech.api.enums.Mods.Computronics;
import static gregtech.api.enums.TierEU.RECIPE_UIV;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;

import com.MessTech.common.items.MTItemList;

import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.items.CombType;
import gregtech.loaders.misc.GTBees;

public class ExtraRecipes {

    public static void loadBeeRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Infinity, 64),
                ItemList.Emitter_UEV.get(16),
                ItemList.Sensor_UEV.get(16),
                GTBees.combs.getStackForType(CombType.INFINITYCATALYST, 64),
                GTBees.combs.getStackForType(CombType.COSMICNEUTRONIUM, 64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UIV, 16))
            .fluidInputs(Materials.Grade8PurifiedWater.getFluid(1000000))
            .itemOutputs(MTItemList.MTCombProcessingModule.get(1))
            .eut(RECIPE_UIV)
            .duration(20 * 325)
            .addTo(assemblerRecipes);
    }

    public static void loadReplacementRecipes() {
        ExtremeCraftingManager.getInstance()
            .addExtremeShapedOreRecipe(
                getModItem(Computronics.ID, "computronics.ocSpecialParts", 1),
                "aaaaaaaaa",
                "abbbbbbbc",
                "abdefedbc",
                "abgggggbc",
                "abdhihdbc",
                "abgggggbc",
                "abdefedbc",
                "abbbbbbbc",
                "aaaaaaaaa",
                'a',
                "plateNeutronium",
                'b',
                "oc:ram6",
                'c',
                "wireGt04ElectrumFlux",
                'd',
                "circuitBio",
                'e',
                getModItem(Mods.Thaumcraft.ID, "ItemResource", 1, 14),
                'f',
                getModItem(Mods.OpenComputers.ID, "item", 1, 103),
                'g',
                ItemList.Circuit_Chip_PPIC.get(1L),
                'h',
                GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Infinity, 1),
                'i',
                ItemList.Field_Generator_UIV.get(1L));
    }
}
