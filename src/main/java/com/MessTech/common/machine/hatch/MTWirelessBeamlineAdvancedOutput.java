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
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import tectech.util.CommonValues;

/**
 * Wireless filtered particle beam output hatch.
 * <p>
 * Like {@link MTWirelessBeamlineOutput} (data-stick address pairing, strict 1:1, no pipes) but keeps the GT5U filtered
 * hatch's accepted-particle blacklist, which the LHC/beam splitter fill in and the inherited ModularUI lets the player
 * edit. Only the transport changed.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessBeamlineAdvancedOutput extends MTEHatchAdvancedOutputBeamline
    implements WirelessBeamlineUtil.LinkHolder {

    /** Same tier as the wired filtered beam output hatch (UV). */
    public static final int WIRELESS_TIER = 8;

    /** Address of the single paired input hatch. */
    private WirelessBeamlineUtil.Target inputLink;

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
        // super returns the GT5U blacklist description; the wireless rules are appended
        return WirelessBeamlineUtil.decorateDescription(super.getDescription(), "advanced");
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
