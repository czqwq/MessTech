package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryElement;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryGrid;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyorInput;

/**
 * Wireless NAC Vacuum Conveyor Input.
 * <p>
 * Extends the GT5U input hatch so existing NAC module structure code still sees a VCI. Instead of a coloured pipe run
 * it
 * receives Circuit Components from one data-stick-paired wireless output hatch, possibly in another NAC or dimension.
 * The output pushes the packet; this hatch only keeps the reverse half of the address so the pair is strictly 1:1 and
 * both ends can report the same link state.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessVacuumConveyorInput extends MTEHatchVacuumConveyorInput {

    /** Address of the single paired output hatch. */
    private WirelessVacuumConveyorUtil.Target outputLink;

    public MTWirelessVacuumConveyorInput(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 11);
    }

    public MTWirelessVacuumConveyorInput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTWirelessVacuumConveyorInput(mName, mTier, mDescriptionArray, mTextures);
    }

    // ---------------------------------------------------------------- link state

    public WirelessVacuumConveyorUtil.Target getLinkTarget() {
        return outputLink;
    }

    public void setLinkTarget(WirelessVacuumConveyorUtil.Target target) {
        if (outputLink == null ? target == null : outputLink.equals(target)) return;
        outputLink = target;
        markDirty();
    }

    @Override
    public boolean canConnectOnSide(ForgeDirection side) {
        return false;
    }

    @Override
    public void getNeighbours(Collection<VacuumFactoryElement> neighbours) {}

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        // The wired input pulls from the vacuum factory grid; this hatch must not join it, or a second source could
        // hand packets to the same input through the grid.
        VacuumFactoryGrid.INSTANCE.removeElement(this);
    }

    @Override
    public void onColorChangeServer(byte aColor) {
        super.onColorChangeServer(aColor);
        VacuumFactoryGrid.INSTANCE.removeElement(this);
    }

    @Override
    public void onFacingChange() {
        super.onFacingChange();
        VacuumFactoryGrid.INSTANCE.removeElement(this);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (aTick % 20 != VACUUM_MOVE_TICK) return;

        boolean dirty = false;
        if (WirelessVacuumConveyorUtil.hasNoComponents(contents)) {
            if (contents != null) {
                contents = null;
                dirty = true;
            }
        }
        if (outputLink != null
            && WirelessVacuumConveyorUtil.isDeadLink(WirelessVacuumConveyorUtil.evaluateInput(this, outputLink))) {
            // the counterpart was broken or re-pointed while we were not looking: drop the one-sided address
            outputLink = null;
            dirty = true;
        }
        if (dirty) markDirty();
        aBaseMetaTileEntity.setActive(contents != null);
    }

    // ---------------------------------------------------------------- data stick pairing

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aPlayer instanceof EntityPlayerMP && tryLinkWithDataStick(aBaseMetaTileEntity, aPlayer)) return true;
        openGui(aPlayer);
        return true;
    }

    private boolean tryLinkWithDataStick(IGregTechTileEntity base, EntityPlayer player) {
        ItemStack stick = player.inventory.getCurrentItem();
        if (!WirelessVacuumConveyorUtil.isLinkDataStick(stick)) return false;

        WirelessVacuumConveyorUtil.Target stickAddress = WirelessVacuumConveyorUtil.readDataStick(stick);
        // binding is an explicit player action: loading the target chunk once is fine and lets a far-away output be
        // paired from this input, unlike the move tick which never loads chunks
        TileEntity tile = WirelessVacuumConveyorUtil.resolveForBinding(stickAddress);
        if (tile == null) {
            WirelessVacuumConveyorUtil.sendChat(player, "machine.wirelessvacuum.chat.fail.target");
            return true;
        }
        MTWirelessVacuumConveyorOutput output = WirelessVacuumConveyorUtil.asOutput(tile);
        if (output == null) {
            WirelessVacuumConveyorUtil.sendChat(player, "machine.wirelessvacuum.chat.fail.role");
            return true;
        }

        IGregTechTileEntity outputBase = output.getBaseMetaTileEntity();
        if (outputBase == null || !WirelessVacuumConveyorUtil.canConfigure(player, outputBase)) {
            WirelessVacuumConveyorUtil.sendChat(player, "machine.wirelessvacuum.chat.fail.permission");
            return true;
        }
        if (!WirelessVacuumConveyorUtil.sameColour(this, output)) {
            int ownColour = WirelessVacuumConveyorUtil.channelColour(this);
            int otherColour = WirelessVacuumConveyorUtil.channelColour(output);
            WirelessVacuumConveyorUtil.sendChat(
                player,
                ownColour < 0 || otherColour < 0 ? "machine.wirelessvacuum.chat.fail.unpainted"
                    : "machine.wirelessvacuum.chat.fail.color");
            return true;
        }

        WirelessVacuumConveyorUtil.Target selfAddress = WirelessVacuumConveyorUtil.targetOf(base);
        WirelessVacuumConveyorUtil.Target outputAddress = WirelessVacuumConveyorUtil.targetOf(outputBase);
        if (outputAddress.equals(outputLink) && selfAddress.equals(output.getLinkTarget())) {
            WirelessVacuumConveyorUtil.sendChat(player, "machine.wirelessvacuum.chat.already");
            return true;
        }

        detachOldOutput(player, selfAddress);
        detachOldInput(player, outputAddress, output.getLinkTarget());

        output.setLinkTarget(selfAddress);
        setLinkTarget(outputAddress);
        WirelessVacuumConveyorUtil.sendChat(player, "machine.wirelessvacuum.chat.linked");
        return true;
    }

    /** Releases this input's previous output. */
    private void detachOldOutput(EntityPlayer player, WirelessVacuumConveyorUtil.Target selfAddress) {
        MTWirelessVacuumConveyorOutput oldOutput = WirelessVacuumConveyorUtil
            .asOutput(WirelessVacuumConveyorUtil.resolve(outputLink));
        if (oldOutput != null && WirelessVacuumConveyorUtil.canConfigure(player, oldOutput.getBaseMetaTileEntity())
            && selfAddress.equals(oldOutput.getLinkTarget())) {
            oldOutput.setLinkTarget(null);
        }
    }

    /**
     * Releases the target output's previous input; an unloaded or foreign hatch keeps a stale address and self-heals.
     */
    private void detachOldInput(EntityPlayer player, WirelessVacuumConveyorUtil.Target outputAddress,
        WirelessVacuumConveyorUtil.Target targetInput) {
        if (targetInput == null || targetInput.equals(WirelessVacuumConveyorUtil.targetOf(getBaseMetaTileEntity()))) {
            return;
        }
        MTWirelessVacuumConveyorInput oldInput = WirelessVacuumConveyorUtil
            .asInput(WirelessVacuumConveyorUtil.resolve(targetInput));
        if (oldInput != null && WirelessVacuumConveyorUtil.canConfigure(player, oldInput.getBaseMetaTileEntity())
            && outputAddress.equals(oldInput.getLinkTarget())) {
            oldInput.setLinkTarget(null);
        }
    }

    // ---------------------------------------------------------------- unbinding

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (!(aPlayer instanceof EntityPlayerMP)) return false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || !aPlayer.isSneaking()) return false;
        if (outputLink == null) return false;

        MTWirelessVacuumConveyorOutput output = WirelessVacuumConveyorUtil
            .asOutput(WirelessVacuumConveyorUtil.resolve(outputLink));
        if (output != null && WirelessVacuumConveyorUtil.canConfigure(aPlayer, output.getBaseMetaTileEntity())
            && WirelessVacuumConveyorUtil.targetOf(base)
                .equals(output.getLinkTarget())) {
            output.setLinkTarget(null);
        }
        setLinkTarget(null);
        WirelessVacuumConveyorUtil.sendChat(aPlayer, "machine.wirelessvacuum.chat.unlinked");
        return true;
    }

    // ---------------------------------------------------------------- tooltip / scanner / NBT

    @Override
    public String[] getDescription() {
        return WirelessVacuumConveyorUtil.decorateDescription(super.getDescription(), "input");
    }

    @Override
    public String[] getInfoData() {
        List<String> info = new ArrayList<>(Arrays.asList(super.getInfoData()));
        WirelessVacuumConveyorUtil.appendLinkInfo(info, this, outputLink, false);
        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        WirelessVacuumConveyorUtil.writeTarget(aNBT, outputLink);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        outputLink = WirelessVacuumConveyorUtil.readTarget(aNBT);
        // the old channel-mode implementation stored a frequency and a private flag here; data-stick pairing has no
        // use for either, and an old world's hatches simply start unpaired until they are re-paired
        aNBT.removeTag("wirelessFreq");
        aNBT.removeTag("wirelessOwner");
    }
}
