package com.MessTech.common.machine.hatch;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import tectech.util.CommonValues;

/**
 * Wireless particle beam input hatch.
 * <p>
 * A normal {@link MTEHatchInputBeamline} to the beam machines (their structure check only tests
 * {@code instanceof MTEHatchInputBeamline}), but fed only by the data-stick-paired {@link MTWirelessBeamlineOutput} /
 * {@link MTWirelessBeamlineAdvancedOutput}, from anywhere in the world. Pair it the way the wireless vacuum conveyor
 * works: left-click an output with a GT data stick, then right-click this hatch with that stick.
 * <p>
 * The inherited expiry logic is kept on purpose: the input drops the beam when no output refreshes it, so a broken or
 * re-pointed link stops feeding the machine instead of caching a stale stream.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessBeamlineInput extends MTEHatchInputBeamline implements WirelessBeamlineUtil.LinkHolder {

    /** Same tier as the wired beamline hatches (LuV). */
    public static final int WIRELESS_TIER = 6;

    /** Address of the single paired output hatch. */
    private WirelessBeamlineUtil.Target outputLink;

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

    // ---------------------------------------------------------------- link state

    @Override
    public WirelessBeamlineUtil.Target getLinkTarget() {
        return outputLink;
    }

    @Override
    public void setLinkTarget(WirelessBeamlineUtil.Target target) {
        if (outputLink == null ? target == null : outputLink.equals(target)) return;
        outputLink = target;
        markDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == CommonValues.MOVE_AT
            && outputLink != null
            && WirelessBeamlineUtil.isDeadLink(WirelessBeamlineUtil.evaluateInput(this, outputLink))) {
            // the counterpart was broken or re-pointed while we were not looking: drop the one-sided address
            setLinkTarget(null);
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    /** No pipes: the input is fed wirelessly only (the stored address decides the partner). */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return false;
    }

    /**
     * Accepts the paired wireless output's beam only: {@code canConnect} does not protect the input, as the wired
     * output hatch walks its line of sight and calls {@code setContents} on any {@code MTEHatchInputBeamline} it meets.
     * Rejecting anything not coming through {@link WirelessBeamlineUtil#moveBeam} keeps "one output + one input" to a
     * single explicit pair; {@code null} (the inherited expiry call) still passes.
     */
    @Override
    public void setContents(BeamLinePacket in) {
        // null is the inherited expiry call and must always go through, otherwise a stale beam would never clear
        if (in != null && !WirelessBeamlineUtil.isWirelessDelivery(in)) return;
        super.setContents(in);
    }

    // ---------------------------------------------------------------- data stick pairing

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aPlayer instanceof EntityPlayerMP
            && WirelessBeamlineUtil.tryLinkInput(this, aBaseMetaTileEntity, aPlayer)) {
            return true;
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        return WirelessBeamlineUtil.unbind(this, aPlayer);
    }

    // ---------------------------------------------------------------- tooltip / scanner / WAILA / NBT

    @Override
    public String[] getDescription() {
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "input");
    }

    @Override
    public String[] getInfoData() {
        return WirelessBeamlineUtil.appendLinkInfo(super.getInfoData(), this);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        WirelessBeamlineUtil.writeWailaData(tag, this);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        WirelessBeamlineUtil.appendWailaBody(currentTip, accessor.getNBTData());
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        WirelessBeamlineUtil.writeTarget(aNBT, outputLink);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        outputLink = WirelessBeamlineUtil.readTarget(aNBT);
    }
}
