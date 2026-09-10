package com.MessTech.common.gui.module;

import com.MessTech.common.machine.module.SpaceModuleAssemblerInfinity;

/**
 * GUI for the infinite space assembler module.
 * <p>
 * Parallel and cross-recipe behaviour are fixed (Integer.MAX_VALUE parallel, automatic
 * cross-recipe cycles), so no parallel configuration widgets are exposed.
 */
public class SpaceModuleAssemblerInfinityGui extends SpaceModuleInfinityGui {

    public SpaceModuleAssemblerInfinityGui(SpaceModuleAssemblerInfinity multiblock) {
        super(multiblock);
    }

    @Override
    protected boolean shouldShowParallelField() {
        return false;
    }

    @Override
    protected boolean shouldShowCrossRecipeParallelField() {
        return false;
    }
}
