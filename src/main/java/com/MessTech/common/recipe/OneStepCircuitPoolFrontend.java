package com.MessTech.common.recipe;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.math.Size;

import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MethodsReturnNonnullByDefault;
import gregtech.common.gui.modularui.UIHelper;

/**
 * NEI frontend for the Nano-Scale Foundry's one-step circuit pool (the old "24" pool).
 * <p>
 * These recipes are recursively flattened Assembly Matrix chains whose raw material components are packed into molten
 * fluids (see {@code MTRecipeMaps#packMaterialInputs}), so a page is dominated by two wide grids, kept inside the
 * standard 170px background: progress bar, single item output and logo on the header row, {@link #COLUMNS} item columns
 * underneath, then the fluid rows. The 1-6 selector circuit is a real (non-consumed) recipe input since the pool
 * switched
 * to {@code GTRecipeBuilder#circuit(int)}, so it is drawn like any other item input, not in a special slot.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class OneStepCircuitPoolFrontend extends RecipeMapFrontend {

    /**
     * Slot capacity of the pool. {@code MTRecipeMaps.oneStepCircuitPoolRecipes} takes its {@code maxIO} from these
     * constants and every position below is derived from them, so raising a limit re-flows the whole grid: edit only
     * these values and never hardcode the numbers again.
     * <p>
     * 54 = 9 columns x 6 rows on both sides, one page height, because {@code MTRecipeMaps#packMaterialInputs} moves the
     * raw material parts (and the wraps' polyethylene) into the fluid grid: without packing the measurement was 61 item
     * inputs and 24 fluid inputs for the Planck chain. The circuit input takes one of the 54 item slots.
     * {@code MTRecipeMaps#populateOneStepCircuitPoolRecipes()} logs a recipe's name and both counts if it ever outgrows
     * these limits, so the log shows the current peaks, not this comment.
     */
    public static final int MAX_ITEM_INPUTS = 54;
    public static final int MAX_ITEM_OUTPUTS = 1;
    public static final int MAX_FLUID_INPUTS = 54;
    public static final int MAX_FLUID_OUTPUTS = 0;

    /** 9 columns of 18px slots span 162px, which still fits inside the 170px recipe background. */
    public static final int COLUMNS = 9;
    private static final int SLOT = 18;
    private static final int BACKGROUND_WIDTH = 170;

    private static final int HEADER_Y = 6;
    private static final int PROGRESS_X = 60;
    private static final int OUTPUT_X = 86;
    private static final int LOGO_X = 147;

    public static final int ITEM_X = 3;
    public static final int ITEM_Y = 26;

    /** Blank row between the item grid and the fluid grid. */
    private static final int BLOCK_GAP = 6;
    private static final int BOTTOM_MARGIN = 6;

    public OneStepCircuitPoolFrontend(BasicUIPropertiesBuilder uiPropertiesBuilder,
        NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        super(
            uiPropertiesBuilder.logo(MTRecipeMaps.MT_LOGO)
                .logoPos(new Pos2d(LOGO_X, HEADER_Y))
                .progressBarPos(new Pos2d(PROGRESS_X, HEADER_Y)),
            neiPropertiesBuilder);
    }

    /** Rows a grid of {@code slots} slots needs. */
    public static int rowCount(int slots) {
        return (Math.max(slots, 1) - 1) / COLUMNS + 1;
    }

    /** Rows of the item input grid, i.e. the grid's height in slots. */
    public static int itemRows() {
        return rowCount(MAX_ITEM_INPUTS);
    }

    /** Rows of the fluid input grid. */
    public static int fluidRows() {
        return rowCount(MAX_FLUID_INPUTS);
    }

    /**
     * Y of the first fluid row.
     */
    public static int fluidY() {
        return ITEM_Y + itemRows() * SLOT + BLOCK_GAP;
    }

    /** Height of the recipe background, derived from the two grids above. */
    public static int backgroundHeight() {
        return fluidY() + rowCount(MAX_FLUID_INPUTS) * SLOT + BOTTOM_MARGIN;
    }

    /** NEI handler height for this pool, keeping the recipe panel in step with the background. */
    public static int handlerHeight() {
        return backgroundHeight() + 4;
    }

    @Override
    protected NEIRecipePropertiesBuilder modifyNEIProperties(NEIRecipePropertiesBuilder neiPropertiesBuilder) {
        return neiPropertiesBuilder.recipeBackgroundSize(new Size(BACKGROUND_WIDTH, backgroundHeight()))
            .recipeComparator(OneStepCircuitPoolFrontend::compareRecipes);
    }

    /**
     * Orders the page list the way the machine's selector reads: the circuit ladder (1 Processor, 2 Assembly,
     * 3 Supercomputer, 4 Mainframe, 5 the original GT ladder), then 6 for the special Pico/Quantum/Planck chains;
     * within
     * one level the output name decides, so the list is stable.
     */
    private static int compareRecipes(GTRecipe left, GTRecipe right) {
        int byLevel = Integer.compare(selectorLevel(left), selectorLevel(right));
        if (byLevel != 0) return byLevel;
        return outputName(left).compareTo(outputName(right));
    }

    private static int selectorLevel(GTRecipe recipe) {
        int level = recipe.getMetadataOrDefault(MTRecipeMaps.ONE_STEP_CIRCUIT_LEVEL, 0);
        // Level 0 means "no selector"; park those at the end of the list.
        return level == 0 ? Integer.MAX_VALUE : level;
    }

    private static String outputName(GTRecipe recipe) {
        return recipe.mOutputs != null && recipe.mOutputs.length > 0 && recipe.mOutputs[0] != null
            ? recipe.mOutputs[0].getDisplayName()
            : "";
    }

    @Override
    public List<Pos2d> getItemInputPositions(int itemInputCount) {
        return UIHelper.getGridPositions(itemInputCount, ITEM_X, ITEM_Y, COLUMNS);
    }

    @Override
    public List<Pos2d> getItemOutputPositions(int itemOutputCount) {
        return UIHelper.getGridPositions(itemOutputCount, OUTPUT_X, HEADER_Y, 1);
    }

    @Override
    public List<Pos2d> getFluidInputPositions(int fluidInputCount) {
        return UIHelper.getGridPositions(fluidInputCount, ITEM_X, fluidY(), COLUMNS);
    }

    @Override
    public List<Pos2d> getFluidOutputPositions(int fluidOutputCount) {
        return UIHelper.getGridPositions(fluidOutputCount, OUTPUT_X, HEADER_Y, 1);
    }
}
