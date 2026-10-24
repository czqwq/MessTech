package com.MessTech.common.gui.module;

import com.MessTech.common.machine.module.SpaceModuleAssemblerInfinity;

/**
 * GUI for the infinite space assembler module: parallel (Integer.MAX_VALUE) and cross-recipe cycles are fixed, so no
 * parallel configuration widgets are exposed.
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
