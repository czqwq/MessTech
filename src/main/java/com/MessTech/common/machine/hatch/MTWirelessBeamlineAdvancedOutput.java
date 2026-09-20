package com.MessTech.common.machine.hatch;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline;

/**
 * Wireless filtered particle beam output hatch.
 * <p>
 * Same as {@link MTWirelessBeamlineOutput} (dye colour channel, strict 1:1, no pipes), but keeps everything the
 * GT5U filtered hatch brings along: the LHC/beam splitter fill in their accepted particle list, and the inherited
 * ModularUI lets the player edit that blacklist. Only the transport changed.
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
        // Re-register every tick: hatches that were placed before this feature existed (or whose chunk was
        // reloaded) must be picked up without breaking and replacing them.
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

    /** Replaces the pipe walk of the wired hatch: the beam only ever travels over the colour channel. */
    @Override
    public void moveAround(IGregTechTileEntity aBaseMetaTileEntity) {
        WirelessBeamlineUtil.moveBeam(this);
    }

    /** No beamline pipes: the output is wireless only (the colour channel decides the partner). */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return false;
    }

    @Override
    public String[] getDescription() {
        // super returns the GT5U blacklist description, the wireless rules are appended to it
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "advanced");
    }

    @Override
    public String[] getInfoData() {
        return WirelessBeamlineUtil.appendLinkInfo(super.getInfoData(), this);
    }
}
