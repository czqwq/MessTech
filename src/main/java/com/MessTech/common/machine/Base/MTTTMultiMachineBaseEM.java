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
     * Maintenance is disabled on every override below, so no machine error can occur.
     */
    @Override
    public boolean doRandomMaintenanceDamage() {
        return true;
    }

    @Override
    public void checkMaintenance() {}

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public final boolean shouldCheckMaintenance() {
        return false;
    }

}
