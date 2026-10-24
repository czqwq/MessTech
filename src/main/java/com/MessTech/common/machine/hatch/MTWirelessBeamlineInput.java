package com.MessTech.common.machine.hatch;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;

/**
 * Wireless particle beam input hatch.
 * <p>
 * A normal {@link MTEHatchInputBeamline} to the beam machines (their structure check only tests
 * {@code instanceof MTEHatchInputBeamline}), but fed only by the matching {@link MTWirelessBeamlineOutput} /
 * {@link MTWirelessBeamlineAdvancedOutput} of the same dye colour, from anywhere in the world.
 * <p>
 * The inherited expiry logic is kept on purpose: the input drops the beam when no output refreshes it, so a broken or
 * repainted channel stops feeding the machine instead of caching a stale stream.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessBeamlineInput extends MTEHatchInputBeamline {

    /** Same tier as the wired beamline hatches (LuV). */
    public static final int WIRELESS_TIER = 6;

    public MTWirelessBeamlineInput(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, WIRELESS_TIER);
    }

    public MTWirelessBeamlineInput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTWirelessBeamlineInput(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        WirelessBeamlineUtil.register(this);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        // Re-register every tick: hatches placed before this feature existed (or whose chunk reloaded) are picked up
        // without breaking and replacing them.
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

    /** No pipes: the input is fed wirelessly only (the colour channel decides the partner). */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return false;
    }

    /**
     * Accepts the paired wireless output's beam only: {@code canConnect} does not protect the input, as the wired
     * output
     * hatch walks its line of sight and calls {@code setContents} on any {@code MTEHatchInputBeamline} it meets.
     * Rejecting
     * anything not coming through {@link WirelessBeamlineUtil#moveBeam} keeps "one output + one input per colour"
     * absolute; {@code null} (the inherited expiry call) still passes.
     */
    @Override
    public void setContents(BeamLinePacket in) {
        // null is the inherited expiry call and must always go through, otherwise a stale beam would never clear
        if (in != null && !WirelessBeamlineUtil.isWirelessDelivery(in)) return;
        super.setContents(in);
    }

    @Override
    public String[] getDescription() {
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "input");
    }

    @Override
    public String[] getInfoData() {
        return WirelessBeamlineUtil.appendLinkInfo(super.getInfoData(), this);
    }
}
