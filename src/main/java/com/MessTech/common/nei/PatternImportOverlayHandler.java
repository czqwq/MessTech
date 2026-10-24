package com.MessTech.common.nei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.nbt.NBTTagCompound;

import com.MessTech.common.gui.GuiUltimatePatternTerminal;
import com.MessTech.common.network.MTNetwork;
import com.MessTech.common.network.PatternImportHandler;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * NEI recipe transfer into the Ultimate Pattern Terminal: every GT machine recipe page (the Nano-Scale Foundry "24"
 * pool
 * and all of GT's other maps - see {@code NEI_MessTechConfig}), because the terminal encodes processing patterns and a
 * GT
 * page is exactly that: real inputs, real outputs, fluids included.
 * <p>
 * Ingredients go into the input grid <em>densely</em>, in the order the page reads (top to bottom, left to right): a GT
 * page
 * spreads a handful over wide panels, which copied into a grid four cells wide would leave most of every page empty.
 * {@link #isIngredient} picks the ingredients (GT marks them) - outputs, ghost selectors and "required but not
 * consumed"
 * catalysts stay out of a pattern. Cells travel keyed by index ({@code in}/{@code out}) with their real count, see
 * {@link PatternImportHandler#writeCell}: the server side needs no geometry.
 */
public class PatternImportOverlayHandler implements IOverlayHandler {

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        // NEI looks the overlay handler up by the GUI's exact class; the container is this mod's own, but the GUI class
        // is
        // what identifies this terminal here.
        if (!(firstGui instanceof GuiUltimatePatternTerminal)) return;

        final NBTTagCompound cells = new NBTTagCompound();
        cells.setTag("in", getInputInv(recipe.getIngredientStacks(recipeIndex)));
        cells.setTag("out", getOutputInv(recipe, recipeIndex));
        MTNetwork.NETWORK.sendToServer(new PatternImportHandler(cells));
    }

    /**
     * The recipe's ingredients, packed into input cells 0, 1, 2 .. in page order: the item panel sits above the fluid
     * panel,
     * so items come first and fluids follow.
     */
    private NBTTagCompound getInputInv(List<PositionedStack> ingredientStacks) {
        final List<PositionedStack> ordered = new ArrayList<>(ingredientStacks);
        // The rows come out in page order; the exact coordinates do not matter beyond that, see #isIngredient.
        ordered.sort(
            Comparator.comparingInt((PositionedStack stack) -> stack.rely)
                .thenComparingInt(stack -> stack.relx));

        var cells = new NBTTagCompound();
        int cell = 0;
        for (var stack : ordered) {
            if (stack == null || stack.item == null || !isIngredient(stack)) continue;
            cells.setTag(Integer.toString(cell++), PatternImportHandler.writeCell(stack.item));
        }
        return cells;
    }

    /**
     * Whether NEI handed over something that belongs in this pattern rather than a page decoration.
     * <p>
     * GT marks it: every item and fluid input of its NEI pages is a {@link GTNEIDefaultHandler.FixedPositionedStack}
     * created
     * with {@code isInput} true, while outputs and the "24" pool's non-consumed selector circuit (a ghost in the page's
     * header row) are created with it false. Of the inputs, {@code isNotConsumed()} singles out the "required but not
     * consumed" ones (a mold, a programming circuit, ...), which GT stores with zero size or amount - a pattern would
     * have to
     * consume them, so they stay out, as in NotEnoughEnergistics.
     * <p>
     * Not a position test against the frontend's constants: GT's NEI slots sit at the frontend position <em>plus</em>
     * {@code GTNEIDefaultHandler.WINDOW_OFFSET} ({@code (-5, -11)}) plus the one pixel {@code FixedPositionedStack}
     * adds, so a
     * pool's first item row lands at {@code rely} 16 instead of {@code ITEM_Y} 26 and the first column of both panels
     * at
     * {@code relx} -1. A geometric filter on {@code ITEM_X}/{@code ITEM_Y} silently dropped a whole first item row, the
     * first
     * item of every other row and the first fluid of every fluid row (the "grid starts with the recipe's eleventh item"
     * report).
     */
    private static boolean isIngredient(PositionedStack stack) {
        if (stack instanceof GTNEIDefaultHandler.FixedPositionedStack fixed) {
            return fixed.isInput() && !fixed.isNotConsumed();
        }
        // A handler that is not GT's (any page registered under a GT category by an addon): NEI's own ingredient list
        // is what the page shows as inputs.
        return true;
    }

    /**
     * The recipe's results, in the terminal's output cells. NEI hands results over separately, and GT5U keeps them in
     * two
     * places: {@code GTNEIDefaultHandler.CachedDefaultRecipe#getResult()} answers <em>null</em> and the page draws its
     * item
     * output as an "other stack" instead, so both are read - otherwise an imported pool page arrives with an empty
     * output
     * grid and AE encodes an input-only tunnel pattern.
     */
    private NBTTagCompound getOutputInv(IRecipeHandler recipe, int recipeIndex) {
        var cells = new NBTTagCompound();
        final List<PositionedStack> results = new ArrayList<>();
        final PositionedStack result = recipe.getResultStack(recipeIndex);
        if (result != null) results.add(result);
        final List<PositionedStack> others = recipe.getOtherStacks(recipeIndex);
        if (others != null) results.addAll(others);

        int cell = 0;
        for (var stack : results) {
            if (stack == null || stack.item == null) continue;
            cells.setTag(Integer.toString(cell++), PatternImportHandler.writeCell(stack.item));
        }
        return cells;
    }
}
