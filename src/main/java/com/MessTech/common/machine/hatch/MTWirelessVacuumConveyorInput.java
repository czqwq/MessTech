package com.MessTech.common.machine.hatch;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryElement;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyorInput;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyorOutput;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponentPacket;

/**
 * Wireless NAC Vacuum Conveyor Input.
 * <p>
 * Extends the normal GT5U input hatch so existing NAC module structure code still recognises it as a
 * VCI. Instead of using coloured pipes, it pairs with wireless output hatches that share the same
 * frequency and privacy scope, including across NACs/dimensions.
 */
public class MTWirelessVacuumConveyorInput extends MTEHatchVacuumConveyorInput implements IWirelessVacuumConveyor {

    protected String frequency = "0";
    protected UUID ownerUuid = null;

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

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTWirelessVacuumConveyorGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        WirelessVacuumConveyorUtil.register(this);
        WirelessVacuumConveyorUtil.onWirelessSettingsChanged(this);
    }

    @Override
    public void onUnload() {
        WirelessVacuumConveyorUtil.unregister(this);
        super.onUnload();
    }

    @Override
    public void onRemoval() {
        WirelessVacuumConveyorUtil.unregister(this);
        super.onRemoval();
    }

    @Override
    public String getFrequency() {
        return frequency;
    }

    @Override
    public void setFrequency(String frequency) {
        this.frequency = frequency == null ? "" : frequency;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            WirelessVacuumConveyorUtil.onWirelessSettingsChanged(this);
        }
    }

    @Override
    public boolean isPrivate() {
        return ownerUuid != null;
    }

    @Override
    public void setPrivate(boolean isPrivate) {
        if (isPrivate) {
            IGregTechTileEntity base = getBaseMetaTileEntity();
            this.ownerUuid = base == null ? null : base.getOwnerUuid();
        } else {
            this.ownerUuid = null;
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            WirelessVacuumConveyorUtil.onWirelessSettingsChanged(this);
        }
    }

    @Override
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            WirelessVacuumConveyorUtil.onWirelessSettingsChanged(this);
        }
    }

    @Override
    public String[] getDescription() {
        String[] base = super.getDescription();
        String[] result = Arrays.copyOf(base, base.length + 4);
        int idx = base.length;
        result[idx++] = EnumChatFormatting.AQUA + translateToLocal("machine.wirelessvacuum.desc.0");
        result[idx++] = EnumChatFormatting.GRAY + translateToLocal("machine.wirelessvacuum.desc.1") + " " + frequency;
        result[idx++] = EnumChatFormatting.GRAY + translateToLocal("machine.wirelessvacuum.desc.2")
            + " "
            + translateToLocal(
                ownerUuid == null ? "machine.wirelessvacuum.desc.public" : "machine.wirelessvacuum.desc.private");
        result[idx] = EnumChatFormatting.GRAY + translateToLocal("machine.wirelessvacuum.desc.input");
        return result;
    }

    @Override
    public boolean canConnectOnSide(ForgeDirection side) {
        return false;
    }

    @Override
    public void getNeighbours(Collection<VacuumFactoryElement> neighbours) {
        WirelessVacuumConveyorUtil.collectWirelessNeighbours(this, neighbours);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) {
            // Re-register every tick so hatches that existed before the wireless registry was added
            // still get picked up without needing to be broken and replaced.
            WirelessVacuumConveyorUtil.register(this);
            if (aTick % 20 == VACUUM_MOVE_TICK) {
                // Query the dedicated wireless registry directly. This avoids depending on whether the
                // two isolated hatches successfully formed a VacuumFactoryGrid edge.
                List<MTEHatchVacuumConveyorOutput> outputs = WirelessVacuumConveyorUtil.findMatchingOutputs(this);
                // Keep the original 1:1 rule: one wireless output per colour/frequency channel.
                if (outputs.size() == 1) {
                    CircuitComponentPacket newPacket = outputs.get(0)
                        .extractPacket();
                    this.unifyPacket(newPacket);
                }

                if (contents == null) {
                    aBaseMetaTileEntity.setActive(false);
                } else {
                    aBaseMetaTileEntity.setActive(true);
                }
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setString("wirelessFreq", frequency);
        if (ownerUuid != null) {
            aNBT.setString("wirelessOwner", ownerUuid.toString());
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        frequency = aNBT.getString("wirelessFreq");
        ownerUuid = aNBT.hasKey("wirelessOwner") ? UUID.fromString(aNBT.getString("wirelessOwner")) : null;
    }
}
