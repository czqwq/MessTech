package com.MessTech.common.recipe;

import static gregtech.api.enums.Mods.Computronics;
import static gregtech.api.util.GTModHandler.getModItem;

import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

public class ExtraRecipes {

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
