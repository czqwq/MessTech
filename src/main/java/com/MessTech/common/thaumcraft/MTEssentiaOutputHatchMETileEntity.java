package com.MessTech.common.thaumcraft;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.block.MTBlocks;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;

/**
 * ME variant of the essentia output hatch. The Small Essentia Smeltery fills it like any other essentia output hatch
 * ({@link IEssentiaTransport#addEssentia}), and every tick the hatch pushes what it holds into the AE network as
 * {@link AEEssentiaStack}s. The local buffer is {@link Integer#MAX_VALUE}; a missing or full network only pauses the
 * push, it never blocks the machine.
 */
public class MTEssentiaOutputHatchMETileEntity extends TileEntity
    implements IAspectContainer, IEssentiaTransport, IActionHost, IGridProxyable {

    private final AspectList aspects = new AspectList();
    private final MachineSource machineSource = new MachineSource(this);
    private AENetworkProxy gridProxy;
    private int tickTimer;

    public int getCapacity() {
        return Integer.MAX_VALUE;
    }

    /** @return Essentia stored right now, capped to the int limit. */
    public int getStoredAmount() {
        long total = 0;
        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            total += aspects.getAmount(aspect);
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private int remainingCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, (long) getCapacity() - getStoredAmount());
    }

    private void markChanged() {
        markDirty();
    }

    // region AE network
    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            ItemStack visual = MTBlocks.essentiaOutputHatchMEBlock == null ? null
                : new ItemStack(MTBlocks.essentiaOutputHatchMEBlock);
            gridProxy = new AENetworkProxy(this, "proxy", visual, true);
            gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        }
        return gridProxy;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection direction) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection direction) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {}

    @Override
    public void gridChanged() {}

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(worldObj, xCoord, yCoord, zCoord);
    }

    @SuppressWarnings("unchecked")
    private IMEMonitor<AEEssentiaStack> getEssentiaMonitor() {
        try {
            return (IMEMonitor<AEEssentiaStack>) getProxy().getStorage()
                .getMEMonitor(AEEssentiaStackType.ESSENTIA_STACK_TYPE);
        } catch (GridAccessException e) {
            return null;
        }
    }
    // endregion

    // region Generation
    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (tickTimer++ == 0) {
            getProxy().onReady();
        }
        pushToNetwork();
    }

    /** Injects the buffered essentia into the AE network; whatever the network rejects stays in the buffer. */
    private void pushToNetwork() {
        if (aspects.size() == 0) return;
        IMEMonitor<AEEssentiaStack> monitor = getEssentiaMonitor();
        if (monitor == null) return;

        boolean changed = false;
        for (Aspect aspect : new ArrayList<>(aspects.aspects.keySet())) {
            if (aspect == null) continue;
            int amount = aspects.getAmount(aspect);
            if (amount <= 0) continue;
            AEEssentiaStack rejected = monitor
                .injectItems(new AEEssentiaStack(aspect, amount), Actionable.MODULATE, machineSource);
            long accepted = amount - (rejected == null ? 0L : rejected.getStackSize());
            if (accepted <= 0) continue;
            aspects.remove(aspect, (int) Math.min(accepted, amount));
            changed = true;
        }
        if (changed) markChanged();
    }
    // endregion

    // region Persistence
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        aspects.readFromNBT(tag);
        if (tag.hasKey("proxy")) getProxy().readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        aspects.writeToNBT(tag);
        getProxy().writeToNBT(tag);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (gridProxy != null) gridProxy.invalidate();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (gridProxy != null) gridProxy.onChunkUnload();
    }
    // endregion

    // region IAspectContainer
    @Override
    public AspectList getAspects() {
        return aspects;
    }

    @Override
    public void setAspects(AspectList aspectList) {
        aspects.aspects.clear();
        if (aspectList != null) aspects.add(aspectList);
        markChanged();
    }

    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        return aspect != null && remainingCapacity() > 0;
    }

    @Override
    public int addToContainer(Aspect aspect, int amount) {
        if (!doesContainerAccept(aspect) || amount <= 0) return amount;
        int accepted = Math.min(remainingCapacity(), amount);
        if (accepted <= 0) return amount;
        aspects.add(aspect, accepted);
        markChanged();
        return amount - accepted;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int amount) {
        return false;
    }

    @Deprecated
    @Override
    public boolean takeFromContainer(AspectList aspectList) {
        return false;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        return aspects.getAmount(aspect) >= amount;
    }

    @Deprecated
    @Override
    public boolean doesContainerContain(AspectList aspectList) {
        if (aspectList == null) return false;
        for (Aspect aspect : aspectList.getAspects()) {
            if (!doesContainerContainAmount(aspect, aspectList.getAmount(aspect))) return false;
        }
        return true;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return aspects.getAmount(aspect);
    }
    // endregion

    // region IEssentiaTransport
    @Override
    public boolean isConnectable(ForgeDirection face) {
        return true;
    }

    @Override
    public boolean canInputFrom(ForgeDirection face) {
        return false;
    }

    @Override
    public boolean canOutputTo(ForgeDirection face) {
        return false;
    }

    @Override
    public void setSuction(Aspect aspect, int amount) {}

    @Override
    public Aspect getSuctionType(ForgeDirection face) {
        return null;
    }

    @Override
    public int getSuctionAmount(ForgeDirection face) {
        return 0;
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return 0;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return amount - addToContainer(aspect, amount);
    }

    @Override
    public Aspect getEssentiaType(ForgeDirection face) {
        Aspect[] stored = aspects.getAspects();
        return stored.length == 0 ? null : stored[0];
    }

    @Override
    public int getEssentiaAmount(ForgeDirection face) {
        return getStoredAmount();
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }
    // endregion
}
