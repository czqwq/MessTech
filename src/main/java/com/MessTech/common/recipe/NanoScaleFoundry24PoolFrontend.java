package com.MessTech.common.recipe;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;

import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.util.MethodsReturnNonnullByDefault;
import gregtech.common.gui.modularui.UIHelper;

/**
 * NEI frontend for the Nano-Scale Foundry "24 pool" one-step recipes.
 * <p>
 * The generic {@code LargeNEIFrontend} lays out large recipes in 3 columns, which turns 48 item
 * inputs into a very tall 3x16 grid. This frontend instead uses 6 columns, producing a compact
 * 6x8 item grid; fluids also use 6 columns so the whole recipe fits on screen.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class NanoScaleFoundry24PoolFrontend extends RecipeMapFrontend {

    private static final int COLUMNS = 6;
    private static final int ITEM_X = 6;
    private static final int FLUID_X = 6;
    private static final int Y_ORIGIN = 26;
    private static final int SPECIAL_X = 6;
    private static final int SPECIAL_Y = 8;
    private static final int OUTPUT_X = 138;
    private static final int OUTPUT_Y = 80;
    private static final int SLOT_SPACING = 18;

    private static final int PROGRESS_X = 116;
    private static final int PROGRESS_Y = OUTPUT_Y;

    private static final UITexture MT_LOGO = UITexture.fullImage("messtech", "gui/picture/mt_logo");

    public NanoScaleFoundry24PoolFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(
            uiPropertiesBuilder.logo(MT_LOGO)
                .logoPos(new Pos2d(152, 8))
                .progressBarPos(new Pos2d(PROGRESS_X, PROGRESS_Y)),
            neiPropertiesBuilder);
    }

    private int getItemRowCount() {
        return (Math.max(uiProperties.maxItemInputs, uiProperties.maxItemOutputs) - 1) / COLUMNS + 1;
    }

    private int getFluidRowCount() {
        return (Math.max(uiProperties.maxFluidInputs, uiProperties.maxFluidOutputs) - 1) / COLUMNS + 1;
    }

    @Override
    protected NEIRecipePropertiesBuilder modifyNEIProperties(NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        // +1 row reserves the non-consumed 1-4 selector slot at the top.
        int rows = 1 + getItemRowCount() + getFluidRowCount();
        int height = 82 + Math.max(rows - 4, 0) * SLOT_SPACING;
        return neiPropertiesBuilder.recipeBackgroundSize(new Size(170, height));
    }

    @Override
    public List<Pos2d> getItemInputPositions(int itemInputCount) {
        return UIHelper.getGridPositions(itemInputCount, ITEM_X, Y_ORIGIN, COLUMNS);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int itemOutputCount) {
        return UIHelper.getGridPositions(itemOutputCount, OUTPUT_X, OUTPUT_Y, 1);
    }

    @Override
    public Pos2d getSpecialItemPosition() {
        // Non-consumed 1-4 selector circuit is shown as a ghost slot in the top-left header row.
        return new Pos2d(SPECIAL_X, SPECIAL_Y);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int fluidInputCount) {
        int fluidY = Y_ORIGIN + getItemRowCount() * SLOT_SPACING;
        return UIHelper.getGridPositions(fluidInputCount, FLUID_X, fluidY, COLUMNS);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return UIHelper.getGridPositions(fluidOutputCount, OUTPUT_X, OUTPUT_Y, 1);
    }
}
