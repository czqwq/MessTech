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
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import tectech.util.CommonValues;

/**
 * Wireless particle beam output hatch.
 * <p>
 * Beam machines hand it a {@link gtnhlanth.common.beamline.BeamLinePacket} exactly as to the wired hatch, but it is not
 * pushed into pipes: it goes to the data-stick-paired {@link MTWirelessBeamlineInput}, and only while both sides still
 * point at each other - see {@link WirelessBeamlineUtil#moveBeam(MTEHatchOutputBeamline)}. Pairing works the way the
 * wireless vacuum conveyor does: left-click this hatch with a GT data stick to store its address, then right-click the
 * input hatch with that stick.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessBeamlineOutput extends MTEHatchOutputBeamline implements WirelessBeamlineUtil.LinkHolder {

    /** Same tier as the wired beamline hatches (LuV). */
    public static final int WIRELESS_TIER = 6;

    /** Address of the single paired input hatch. */
    private WirelessBeamlineUtil.Target inputLink;

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

    // ---------------------------------------------------------------- link state

    @Override
    public WirelessBeamlineUtil.Target getLinkTarget() {
        return inputLink;
    }

    @Override
    public void setLinkTarget(WirelessBeamlineUtil.Target target) {
        if (inputLink == null ? target == null : inputLink.equals(target)) return;
        inputLink = target;
        markDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == CommonValues.MOVE_AT
            && inputLink != null
            && WirelessBeamlineUtil.isDeadLink(WirelessBeamlineUtil.evaluateOutput(this, inputLink))) {
            // the counterpart was broken or re-pointed while we were not looking: drop the one-sided address
            setLinkTarget(null);
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    /** Replaces the wired hatch's pipe walk: the beam only travels over the stored link. */
    @Override
    public void moveAround(IGregTechTileEntity aBaseMetaTileEntity) {
        WirelessBeamlineUtil.moveBeam(this);
    }

    /** No pipes: the output is wireless only (the stored address decides the partner). */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return false;
    }

    // ---------------------------------------------------------------- data stick copy / unbinding

    @Override
    public void onLeftclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        WirelessBeamlineUtil.saveAddressToStick(this, aPlayer);
    }

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        return WirelessBeamlineUtil.unbind(this, aPlayer);
    }

    // ---------------------------------------------------------------- tooltip / scanner / WAILA / NBT

    @Override
    public String[] getDescription() {
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "output");
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
        WirelessBeamlineUtil.writeTarget(aNBT, inputLink);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        inputLink = WirelessBeamlineUtil.readTarget(aNBT);
    }
}
