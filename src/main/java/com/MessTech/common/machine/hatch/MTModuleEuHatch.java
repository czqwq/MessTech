package com.MessTech.common.machine.hatch;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import net.minecraft.util.StatCollector;

import com.MessTech.common.machine.Base.MTModuleType;
import com.MessTech.common.machine.Base.MTModuleValues;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * EU discount module: multiplies the EU/t of the machine it is built into.
 * <p>
 * The values are TST's {@code PowerConsumptionMultiplierOfPowerConsumptionController}: 0.95 at IV (5% saved) down to
 * 0.0625 at MAX (93.75% saved), see {@link MTModuleValues#euModifier(int)}.
 */
public class MTModuleEuHatch extends MTModuleHatchBase {

    private static final Set<MTModuleType> TYPES = Collections.unmodifiableSet(EnumSet.of(MTModuleType.EU_DISCOUNT));

    public MTModuleEuHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTModuleEuHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTModuleEuHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public Set<MTModuleType> getModuleTypes() {
        return TYPES;
    }

    /**
     * @return The EU/t multiplier of this module, 0.95 at IV down to 0.0625 at MAX.
     */
    public float getEuMultiplier() {
        return MTModuleValues.euModifier(mTier);
    }

    @Override
    public float getEuModifier() {
        return MTModuleValues.euModifier(mTier);
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocalFormatted(
            "machine.module.eu.desc.0",
            MTModuleValues.euModifier(mTier),
            MTModuleValues.euDiscountText(mTier)), StatCollector.translateToLocal("machine.module.desc.install") };
    }
}
