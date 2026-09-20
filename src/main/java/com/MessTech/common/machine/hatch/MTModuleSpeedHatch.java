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
 * Speed module: multiplies the duration of the recipes of the machine it is built into.
 * <p>
 * The values are TST's {@code SpeedMultiplierOfSpeedController}: IV runs recipes 2x as fast, MAX 1024x, see
 * {@link MTModuleValues#speedMultiplier(int)}.
 */
public class MTModuleSpeedHatch extends MTModuleHatchBase {

    private static final Set<MTModuleType> TYPES = Collections.unmodifiableSet(EnumSet.of(MTModuleType.SPEED_BONUS));

    public MTModuleSpeedHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTModuleSpeedHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTModuleSpeedHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public Set<MTModuleType> getModuleTypes() {
        return TYPES;
    }

    /**
     * @return How much faster recipes run with this module, 2 at IV up to 1024 at MAX.
     */
    public int getSpeedMultiplier() {
        return MTModuleValues.speedMultiplier(mTier);
    }

    @Override
    public float getSpeedBonus() {
        return MTModuleValues.speedBonus(mTier);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocalFormatted("machine.module.speed.desc.0", getSpeedMultiplier()),
            StatCollector.translateToLocal("machine.module.desc.install") };
    }
}
