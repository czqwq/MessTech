package com.MessTech.common.machine.loaders;

import static net.minecraft.util.StatCollector.translateToLocal;

import com.MessTech.common.machine.MTComputingCenter;
import com.MessTech.common.machine.MTDTPF;
import com.MessTech.common.machine.hatch.MTHatchRack;
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

        MTItemList.MTComputingCenter.set(
            new MTComputingCenter(MT_ID + 1, "Nano Computing Center", translateToLocal("machine.computingcenter.name"))
                .getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTComputingCenter.get(1));
        MTItemList.MTHatchRack.set(
            new MTHatchRack(MT_ID + 2, "Computing Rack", translateToLocal("machine.computingcenter.hatchrack"), 8)
                .getStackForm(1L));
        AuthorDynamic.registerOn(AuthorDynamic.author_czqwq(), MTItemList.MTHatchRack.get(1));
    }
}
