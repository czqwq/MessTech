package com.MessTech.common.machine.loaders;

import static net.minecraft.util.StatCollector.translateToLocal;

import com.MessTech.common.block.AdvAssMatrixBlock;
import com.MessTech.common.block.AssMatrixBlock;
import com.MessTech.common.machine.MTAssFactory;
import com.MessTech.common.machine.MTComputingCenter;
import com.MessTech.common.machine.MTDTPF;
import com.MessTech.common.machine.MTNQDAFReactor;
import com.MessTech.common.machine.MTNanoScaleFoundry;
import com.MessTech.common.machine.hatch.MTHatchRack;
import com.MessTech.common.machine.hatch.MTInventoryInputBusME;
import com.MessTech.common.machine.hatch.MTInventoryInputHatchME;
import com.MessTech.common.machine.hatch.MTWirelessVacuumConveyorInput;
import com.MessTech.common.machine.hatch.MTWirelessVacuumConveyorOutput;
import com.MessTech.common.machine.module.SpaceModuleMinerInfinity;
import com.MessTech.common.machine.module.SpaceModulePumpInfinity;
import com.MessTech.common.misc.MTItemList;
import com.MessTech.common.recipe.MTRecipeMaps;
import com.MessTech.common.util.AuthorDynamic;

import tectech.thing.metaTileEntity.hatch.MTEHatchRack;

public class MTMachineLoader {

    public static void loadMachines() {
        final int MT_ID = 32400;
        MTItemList.MTDTPF.set(
            new MTDTPF(
                MT_ID,
                "Dimensionally Transcendent Plasma Fusion Computer",
                translateToLocal("machine.dtpf.name")).getStackForm(1L));
        // 注册实时动画作者行(gtnhlib AnimatedTooltipHandler 在渲染时逐帧求值,同 GT5U LoaderMetaTileEntities 的做法)
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTDTPF.get(1));

        MTEHatchRack.run();
        // Register the rack components into our own Computing Center NEI list (independent from GT5U's QC list).
        MTRecipeMaps.populateComputingCenterFakeRecipes();
        // Register an independent Assembly Line NEI list for the Assembly Factory.
        MTRecipeMaps.populateAssFactoryAssemblyLineRecipes();

        MTItemList.MTComputingCenter.set(
            new MTComputingCenter(MT_ID + 1, "Nano Computing Center", translateToLocal("machine.computingcenter.name"))
                .getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTComputingCenter.get(1));
        MTItemList.MTHatchRack.set(
            new MTHatchRack(MT_ID + 2, "Computing Rack", translateToLocal("machine.computingcenter.hatchrack"), 8)
                .getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTHatchRack.get(1));

        MTItemList.MTAssFactory.set(
            new MTAssFactory(MT_ID + 3, "Assembly Factory", translateToLocal("machine.assfactory.name"))
                .getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTAssFactory.get(1));

        MTItemList.AssMatrixBlock.set(AssMatrixBlock.getItemStack());
        MTItemList.AdvAssMatrixBlock.set(AdvAssMatrixBlock.getItemStack());

        MTItemList.SpaceModulePumpInfinity.set(
            new SpaceModulePumpInfinity(
                MT_ID + 4,
                "Space Module Pump Infinity",
                translateToLocal("machine.spacemodulepump.name")).getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.SpaceModulePumpInfinity.get(1));
        MTItemList.SpaceModuleMinerInfinity.set(
            new SpaceModuleMinerInfinity(
                MT_ID + 5,
                "Space Module Miner Infinity",
                translateToLocal("machine.spacemoduleminer.name")).getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.SpaceModuleMinerInfinity.get(1));
        MTItemList.MTNQDAFReactor.set(
            new MTNQDAFReactor(
                MT_ID + 6,
                "Large Naquadah Annihilation Field Reactor",
                translateToLocal("machine.nqdafreactor.name")).getStackForm(1));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTNQDAFReactor.get(1));

        MTItemList.MTInventoryInputBusME.set(
            new MTInventoryInputBusME(
                MT_ID + 7,
                "Inventory Input Bus (ME)",
                translateToLocal("machine.meinventory.inputbus.name")).getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTInventoryInputBusME.get(1));
        MTItemList.MTInventoryInputHatchME.set(
            new MTInventoryInputHatchME(
                MT_ID + 8,
                "Inventory Input Hatch (ME)",
                translateToLocal("machine.meinventory.inputhatch.name")).getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTInventoryInputHatchME.get(1));

        MTItemList.MTWirelessVacuumConveyorInput.set(
            new MTWirelessVacuumConveyorInput(
                MT_ID + 9,
                "Wireless Vacuum Conveyor Input",
                translateToLocal("machine.wirelessvacuum.input.name")).getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTWirelessVacuumConveyorInput.get(1));
        MTItemList.MTWirelessVacuumConveyorOutput.set(
            new MTWirelessVacuumConveyorOutput(
                MT_ID + 10,
                "Wireless Vacuum Conveyor Output",
                translateToLocal("machine.wirelessvacuum.output.name")).getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTWirelessVacuumConveyorOutput.get(1));

        MTItemList.MTNanoScaleFoundry.set(
            new MTNanoScaleFoundry(MT_ID + 11, "Nano-Scale Foundry", translateToLocal("machine.nanoscale.name"))
                .getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTNanoScaleFoundry.get(1));

        // Populate after the machine item list is set so the NEI handler can reference the catalyst.
        MTRecipeMaps.populateNanoScaleFoundryRecipes();
        MTRecipeMaps.populateNanoScaleFoundry24PoolRecipes();
    }
}
