package com.MessTech.common.recipe;

import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.maps.QuantumComputerFrontend;
import gregtech.api.util.GTRecipe;

/**
 * Custom recipe maps for MessTech machines.
 */
public final class MTRecipeMaps {

    private MTRecipeMaps() {}

    /** Fake recipe pool for the Computing Center (Nano Computing mode), with proper NEI/frontend registration. */
    public static final RecipeMap<RecipeMapBackend> computingCenterFakeRecipes = RecipeMapBuilder
        .of("mt.recipe.computingcenter")
        .maxIO(1, 0, 0, 0)
        .minInputs(1, 0)
        .dontUseProgressBar()
        .frontend(QuantumComputerFrontend::new)
        .neiHandlerInfo(builder -> builder.setHeight(110))
        .build();

    /**
     * Populates {@link #computingCenterFakeRecipes} with the same rack-component fake recipes that
     * {@code MTEHatchRack.run()} already registers into {@code quantumComputerFakeRecipes}. This gives
     * the Computing Center its own independent NEI list while reusing the shared rack components.
     * Must be called after {@code MTEHatchRack.run()}.
     */
    public static void populateComputingCenterFakeRecipes() {
        RecipeCategory defaultCategory = computingCenterFakeRecipes.getDefaultRecipeCategory();
        for (GTRecipe recipe : RecipeMaps.quantumComputerFakeRecipes.getAllRecipes()) {
            // Recipes copied from the shared QC pool keep their original recipe category. NEI's handler
            // reads getRecipesByCategory(defaultCategory) — not getAllRecipes() — so we must re-tag every
            // recipe to this map's own category, otherwise our independent list stays empty.
            recipe.setRecipeCategory(defaultCategory);
            computingCenterFakeRecipes.addFakeRecipe(false, recipe);
        }
    }
}
