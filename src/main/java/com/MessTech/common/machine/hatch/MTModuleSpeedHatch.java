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
 * Speed module: multiplies the recipe duration of the machine it is built into.
 * <p>
 * MessTech's own fixed table, see {@link MTModuleValues#speedBonus(int)}: 0.95x duration at IV, 0.01x at MAX (100x as
 * fast).
 */
public class MTModuleSpeedHatch extends MTModuleHatchBase {

    private static final Set<MTModuleType> TYPES = Collections.unmodifiableSet(EnumSet.of(MTModuleType.SPEED_BONUS));

    /** Decal: {@code assets/messtech/textures/blocks/ModuleHatch/OVERLAY_SpeedController.png}. */
    private static final String OVERLAY_PATH = "ModuleHatch/OVERLAY_SpeedController";

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

    @Override
    protected String getOverlayPath() {
        return OVERLAY_PATH;
    }

    @Override
    public float getSpeedBonus() {
        return MTModuleValues.speedBonus(mTier);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector
                .translateToLocalFormatted("machine.module.speed.desc.0", MTModuleValues.speedBonusText(mTier)),
            StatCollector.translateToLocal("machine.module.desc.install") };
    }
}
