package com.MessTech.common.nei;

import com.MessTech.common.misc.MTItemList;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

/**
 * MessTech NEI configuration.
 * <p>
 * Registers the infinite Space Pump as a catalyst for the original
 * GTNH-Intergalactic Space Pump recipe handler, so its custom
 * {@code SpacePumpingRecipes} show up in NEI.
 */
public class NEI_MessTechConfig implements IConfigureNEI {

    public static boolean isAdded = true;

    @Override
    public void loadConfig() {
        API.addRecipeCatalyst(
            MTItemList.SpaceModulePumpInfinity.get(1),
            "gtnhintergalactic.nei.SpacePumpModuleRecipeHandler");
        isAdded = true;
    }

    @Override
    public String getName() {
        return "MessTech NEI Plugin";
    }

    @Override
    public String getVersion() {
        return com.MessTech.init.Tags.VERSION;
    }
}
