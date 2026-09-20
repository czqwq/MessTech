package com.MessTech.common.machine.hatch;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;

/**
 * Wireless particle beam output hatch.
 * <p>
 * The beam machines hand it a {@link gtnhlanth.common.beamline.BeamLinePacket} exactly like they do to the wired
 * hatch, but it is not pushed into beamline pipes. Instead it is handed to the {@link MTWirelessBeamlineInput} of
 * the same dye colour, and only while that colour is a clean 1:1 pair (one output, one input) - see
 * {@link WirelessBeamlineUtil#moveBeam(MTEHatchOutputBeamline)}.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessBeamlineOutput extends MTEHatchOutputBeamline {

    /** Same tier as the wired beamline hatches (LuV). */
    public static final int WIRELESS_TIER = 6;

    public MTWirelessBeamlineOutput(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, WIRELESS_TIER);
    }

    public MTWirelessBeamlineOutput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTWirelessBeamlineOutput(mName, mTier, mDescriptionArray, mTextures);
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
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "output");
    }

    @Override
    public String[] getInfoData() {
        return WirelessBeamlineUtil.appendLinkInfo(super.getInfoData(), this);
    }
}
