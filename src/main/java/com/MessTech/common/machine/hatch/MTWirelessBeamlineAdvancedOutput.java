package com.MessTech.common.machine.hatch;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline;

/**
 * Wireless filtered particle beam output hatch.
 * <p>
 * Like {@link MTWirelessBeamlineOutput} (dye colour channel, strict 1:1, no pipes) but keeps the GT5U filtered hatch's
 * accepted-particle blacklist, which the LHC/beam splitter fill in and the inherited ModularUI lets the player edit.
 * Only
 * the transport changed.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessBeamlineAdvancedOutput extends MTEHatchAdvancedOutputBeamline {

    /** Same tier as the wired filtered beam output hatch (UV). */
    public static final int WIRELESS_TIER = 8;

    public MTWirelessBeamlineAdvancedOutput(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, WIRELESS_TIER);
    }

    public MTWirelessBeamlineAdvancedOutput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTWirelessBeamlineAdvancedOutput(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        WirelessBeamlineUtil.register(this);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        // Re-register every tick: hatches placed before this feature existed (or whose chunk reloaded) are picked up
        // without breaking them.
        if (aBaseMetaTileEntity.isServerSide()) WirelessBeamlineUtil.register(this);
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void onUnload() {
        WirelessBeamlineUtil.unregister(this);
        super.onUnload();
    }

    @Override
    public void onRemoval() {
        WirelessBeamlineUtil.unregister(this);
        super.onRemoval();
    }

    /** Replaces the wired hatch's pipe walk: the beam only travels over the colour channel. */
    @Override
    public void moveAround(IGregTechTileEntity aBaseMetaTileEntity) {
        WirelessBeamlineUtil.moveBeam(this);
    }

    /** No pipes: the output is wireless only (the colour channel decides the partner). */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return false;
    }

    @Override
    public String[] getDescription() {
        // super returns the GT5U blacklist description; the wireless rules are appended
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "advanced");
    }

    @Override
    public String[] getInfoData() {
        return WirelessBeamlineUtil.appendLinkInfo(super.getInfoData(), this);
    }
}
