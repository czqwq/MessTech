package com.MessTech.common.machine.loaders;

import static net.minecraft.util.StatCollector.translateToLocal;

import com.MessTech.common.machine.MTDTPF;
import com.MessTech.common.misc.MTItemList;
import com.MessTech.common.util.AuthorDynamic;

public class MTMachineLoader {

    public static void loadMachines() {
        final int MT_ID = 32400;
        MTItemList.MTDTPF.set(
            new MTDTPF(
                MT_ID,
                translateToLocal("machine.dtpf.name"),
                "Dimensionally Transcendent Plasma Fusion Computer").getStackForm(1L));
        // 注册实时动画作者行(gtnhlib AnimatedTooltipHandler 在渲染时逐帧求值,同 GT5U LoaderMetaTileEntities 的做法)
        AuthorDynamic.registerOn(MTItemList.MTDTPF.get(1));
    }
}
