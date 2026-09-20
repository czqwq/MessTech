package com.MessTech.common.machine.hatch;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.MessTech.common.machine.Base.MTModuleType;
import com.MessTech.common.machine.Base.MTModuleValues;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Parallel control module: supplies the parallel of the machine it is built into.
 * <p>
 * The values are {@code 1 << (2 * (tier - 2))}, i.e. 64 parallel at IV up to 16,777,216 at MAX, see
 * {@link MTModuleValues#maxParallel(int)}.
 * <p>
 * The value is a ceiling, not a fixed number: the player sets the parallel the machine may actually use in the GUI,
 * between 1 and {@link #getMaxParallel()}. A machine runs on that number instead of its own parallel, and it only
 * takes one parallel module at a time.
 */
public class MTModuleParallelHatch extends MTModuleHatchBase {

    private static final Set<MTModuleType> TYPES = Collections
        .unmodifiableSet(EnumSet.of(MTModuleType.PARALLEL_CONTROL));

    private final int maxParallel;
    private int parallel;

    public MTModuleParallelHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        this.maxParallel = MTModuleValues.maxParallel(aTier);
        this.parallel = maxParallel;
    }

    public MTModuleParallelHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.maxParallel = MTModuleValues.maxParallel(aTier);
        this.parallel = maxParallel;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTModuleParallelHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public Set<MTModuleType> getModuleTypes() {
        return TYPES;
    }

    /**
     * @return The highest parallel this module can supply, 64 at IV up to 16,777,216 at MAX.
     */
    public int getMaxParallel() {
        return maxParallel;
    }

    /**
     * @return The parallel the machine currently runs on, {@link #getMaxParallel()} until the player lowers it.
     */
    @Override
    public int getParallel() {
        return parallel;
    }

    /**
     * Set the parallel from the GUI, clamped to 1..{@link #getMaxParallel()}.
     *
     * @param value The requested parallel.
     */
    public void setParallelFromGui(int value) {
        this.parallel = Math.max(1, Math.min(maxParallel, value));
    }

    // region NBT

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("parallel", parallel);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("parallel")) setParallelFromGui(aNBT.getInteger("parallel"));
    }

    // endregion

    // region GUI

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTModuleParallelHatchGui(this).build(data, syncManager, uiSettings);
    }

    // endregion

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocalFormatted("machine.module.parallel.desc.0", maxParallel),
            StatCollector.translateToLocal("machine.module.parallel.desc.1"),
            StatCollector.translateToLocal("machine.module.desc.install") };
    }
}
