package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryElement;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryGrid;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyorOutput;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponentPacket;

/**
 * Wireless NAC Vacuum Conveyor Output.
 * <p>
 * Extends the GT5U output hatch so existing NAC module structure code still sees a VCO. Instead of a coloured pipe run
 * it pushes Circuit Components to one data-stick-paired wireless input hatch, possibly in another NAC or dimension.
 * This hatch is the only mover of the link: the packet is handed over once per {@code VACUUM_MOVE_TICK} and only while
 * the input hatch points back at us, so the pair is strictly 1:1.
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTWirelessVacuumConveyorOutput extends MTEHatchVacuumConveyorOutput {

    /** Address of the single paired input hatch. */
    private WirelessVacuumConveyorUtil.Target inputLink;

    public MTWirelessVacuumConveyorOutput(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 11);
    }

    public MTWirelessVacuumConveyorOutput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTWirelessVacuumConveyorOutput(mName, mTier, mDescriptionArray, mTextures);
    }

    // ---------------------------------------------------------------- link state

    public WirelessVacuumConveyorUtil.Target getLinkTarget() {
        return inputLink;
    }

    public void setLinkTarget(WirelessVacuumConveyorUtil.Target target) {
        if (inputLink == null ? target == null : inputLink.equals(target)) return;
        inputLink = target;
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
        // the wired output publishes itself to the vacuum factory grid; this hatch must not join it, the wireless
        // counterpart is the only consumer of its packet
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

    // ---------------------------------------------------------------- data stick copy

    @Override
    public void onLeftclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!(aPlayer instanceof EntityPlayerMP)) return;
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) return;
        ItemStack stick = aPlayer.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(stick, false, true)) return;

        WirelessVacuumConveyorUtil.writeDataStick(stick, WirelessVacuumConveyorUtil.targetOf(aBaseMetaTileEntity));
        WirelessVacuumConveyorUtil.sendChat(aPlayer, "machine.wirelessvacuum.chat.saved");
    }

    // ---------------------------------------------------------------- packet move

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
        if (inputLink != null) {
            WirelessVacuumConveyorUtil.LinkState state = WirelessVacuumConveyorUtil.evaluateOutput(this, inputLink);
            if (WirelessVacuumConveyorUtil.isDeadLink(state)) {
                // the counterpart was broken or re-pointed while we were not looking: drop the one-sided address
                inputLink = null;
                dirty = true;
            } else if (state == WirelessVacuumConveyorUtil.LinkState.LINKED && contents != null) {
                MTWirelessVacuumConveyorInput input = WirelessVacuumConveyorUtil
                    .asInput(WirelessVacuumConveyorUtil.resolve(inputLink));
                if (input != null) {
                    CircuitComponentPacket packet = extractPacket();
                    if (packet != null) input.unifyPacket(packet);
                }
            }
        }
        if (dirty) markDirty();
        aBaseMetaTileEntity.setActive(contents != null);
    }

    // ---------------------------------------------------------------- unbinding

    @Override
    public boolean onWireCutterRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        if (!(aPlayer instanceof EntityPlayerMP)) return false;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || !aPlayer.isSneaking()) return false;
        if (inputLink == null) return false;

        MTWirelessVacuumConveyorInput input = WirelessVacuumConveyorUtil
            .asInput(WirelessVacuumConveyorUtil.resolve(inputLink));
        if (input != null && WirelessVacuumConveyorUtil.canConfigure(aPlayer, input.getBaseMetaTileEntity())
            && WirelessVacuumConveyorUtil.targetOf(base)
                .equals(input.getLinkTarget())) {
            input.setLinkTarget(null);
        }
        setLinkTarget(null);
        WirelessVacuumConveyorUtil.sendChat(aPlayer, "machine.wirelessvacuum.chat.unlinked");
        return true;
    }

    // ---------------------------------------------------------------- tooltip / scanner / NBT

    @Override
    public String[] getDescription() {
        return WirelessVacuumConveyorUtil.decorateDescription(super.getDescription(), "output");
    }

    @Override
    public String[] getInfoData() {
        List<String> info = new ArrayList<>(Arrays.asList(super.getInfoData()));
        WirelessVacuumConveyorUtil.appendLinkInfo(info, this, inputLink, true);
        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        WirelessVacuumConveyorUtil.writeTarget(aNBT, inputLink);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        inputLink = WirelessVacuumConveyorUtil.readTarget(aNBT);
        // the old channel-mode implementation stored a frequency and a private flag here; data-stick pairing has no
        // use for either, and an old world's hatches simply start unpaired until they are re-paired
        aNBT.removeTag("wirelessFreq");
        aNBT.removeTag("wirelessOwner");
    }
}
