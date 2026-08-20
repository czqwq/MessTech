package com.MessTech.common.machine.Base;

import gregtech.api.interfaces.ISecondaryDescribable;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

public abstract class MTTTMultiMachineBaseEM extends TTMultiblockBase implements ISecondaryDescribable {

    public MTTTMultiMachineBaseEM(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTTTMultiMachineBaseEM(String aName) {
        super(aName);
    }

    public void repairMachine() {
        mHardHammer = true;
        mScrewdriver = true;
        mCrowbar = true;
        mSolderingTool = true;
        mWrench = true;
    }

    /**
     * No more machine error
     */
    @Override
    public boolean doRandomMaintenanceDamage() {
        return true;
    }

    /**
     * No more machine error
     */
    @Override
    public void checkMaintenance() {}

    /**
     * No more machine error
     */
    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * No more machine error
     */
    @Override
    public final boolean shouldCheckMaintenance() {
        return false;
    }

}
