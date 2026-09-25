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
 * NEI recipe transfer into the Ultimate Pattern Terminal.
 * <p>
 * It serves every GT machine recipe page - the Nano-Scale Foundry "24" pool and all of GT's other recipe maps, see
 * {@code NEI_MessTechConfig} - because the terminal encodes processing patterns, and a GT page is exactly that: real
 * inputs, real outputs, fluids included.
 * <p>
 * NEI only hands over the recipe's ingredients and its result; the terminal decides where they go. The ingredients are
 * laid into the input grid <em>densely</em>, in the order the recipe page reads (top to bottom, left to right): a GT
 * page spreads a handful of ingredients over wide panels, and copying that spread into a grid whose pages are four
 * cells wide would leave most of every page empty. Dense cells keep the first page full and the rest of the recipe on
 * the following ones.
 * <p>
 * Which of the stacks NEI hands over are ingredients is decided by GT itself, see {@link #isIngredient}: a page also
 * draws outputs, a ghost selector and "required but not consumed" catalysts, and none of those belongs in a pattern.
 * <p>
 * Everything that reaches the server is keyed by cell index - the inputs under {@code in}, the outputs under
 * {@code out} - and each cell carries its real item count, see {@link PatternImportHandler#writeCell}. The server side
 * needs no geometry at all.
 */
public class PatternImportOverlayHandler implements IOverlayHandler {

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        // The terminal's container is AE's own on the client, so the GUI is what identifies it.
        if (!(firstGui instanceof GuiUltimatePatternTerminal)) return;

        final NBTTagCompound cells = new NBTTagCompound();
        cells.setTag("in", getInputInv(recipe.getIngredientStacks(recipeIndex)));
        cells.setTag("out", getOutputInv(recipe, recipeIndex));
        MTNetwork.NETWORK.sendToServer(new PatternImportHandler(cells));
    }

    /**
     * The recipe's ingredients, packed into input cells 0, 1, 2 .. in the order the page reads them. That order is the
     * page's own: the item panel sits above the fluid panel, so the items come first and the fluids follow them.
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
     * Whether NEI handed over something that belongs in this pattern rather than one of the page's decorations.
     * <p>
     * GT marks that itself: every item and fluid input of its NEI pages is a
     * {@link GTNEIDefaultHandler.FixedPositionedStack} created with {@code isInput} true, while outputs and the "24"
     * pool's non-consumed selector circuit - drawn as a ghost in that page's header row, outside both panels - are
     * created with it false. Of the inputs, {@code isNotConsumed()} singles out the "required but not consumed" ones
     * (a mold, a programming circuit, ...), which GT itself stores with a zero size or amount; a pattern holding them
     * would have to consume them, so they stay out, the same choice NotEnoughEnergistics makes by default for the AE
     * pattern terminals it serves.
     * <p>
     * It must not be asked as a position test against the frontend's own constants: GT's NEI slots sit at the
     * frontend position <em>plus</em> {@code GTNEIDefaultHandler.WINDOW_OFFSET} (which is {@code (-5, -11)}, the
     * recipe panel's interior offset) plus the one pixel {@code FixedPositionedStack} adds, so a pool's item panel
     * first row lands at {@code rely} 16 instead of {@code ITEM_Y} 26 and the first column of both panels at
     * {@code relx} -1. A geometric filter built from {@code ITEM_X}/{@code ITEM_Y} therefore silently dropped a
     * whole first item row, the first item of every other row and the first fluid of every fluid row - that is the
     * "the terminal's grid starts with the recipe's eleventh item" report - and a page whose item inputs fit into
     * those ten slots lost every item and kept only its fluids.
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
     * The recipe's results, in the terminal's output cells.
     * <p>
     * NEI hands results over separately from the ingredients, and GT5U keeps them in two places: its
     * {@code GTNEIDefaultHandler.CachedDefaultRecipe#getResult()} answers <em>null</em> and the page draws its item
     * output as an "other stack" instead, which is why this reads both. Without that a recipe imported from this pool
     * page arrived with an empty output grid, and AE encoded it as an input-only tunnel pattern.
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
