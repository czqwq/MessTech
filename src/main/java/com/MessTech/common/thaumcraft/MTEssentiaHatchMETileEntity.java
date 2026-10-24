package com.MessTech.common.thaumcraft;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.MessTech.common.block.MTBlocks;
import com.MessTech.init.MessTech;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

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
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;

/**
 * ME variant of the essentia input hatch. It connects to an AE network (one channel) and, once an aspect is marked by
 * right-clicking the block with an essentia container, pulls up to {@link #getMaxInput()} essentia of that aspect from
 * the network every tick. The GUI configures that per-tick limit and shows the stored amount.
 *
 * <p>
 * Capacity and per-tick limit live in the tile entity and in the item's NBT ({@value #NBT_CAPACITY} /
 * {@value #NBT_MAX_INPUT}): the assembled upgrade ({@link Integer#MAX_VALUE} capacity) keeps its capacity when the
 * block is broken, dropped and placed again, like GT's own ME hatches.
 */
public class MTEssentiaHatchMETileEntity extends MTEssentiaHatchTileEntity
    implements IActionHost, IGridProxyable, IGuiHolder<GuiData> {

    public static final int DEFAULT_MAX_INPUT = 64;
    public static final String NBT_CAPACITY = "capacity";
    public static final String NBT_MAX_INPUT = "maxInput";
    private static final String CLEAR_ACTION = "clear_filter";

    private AENetworkProxy gridProxy;
    private final MachineSource machineSource = new MachineSource(this);
    private int capacity = CAPACITY;
    private int maxInput = DEFAULT_MAX_INPUT;
    private int tickTimer;

    @Override
    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int value) {
        capacity = Math.max(CAPACITY, value);
        maxInput = Math.min(maxInput, capacity);
        markDirty();
    }

    public boolean hasCustomSettings() {
        return capacity != CAPACITY || maxInput != DEFAULT_MAX_INPUT;
    }

    /** Writes capacity and per-tick limit into a tag (tile entity NBT or the dropped item). */
    public void writeSettings(NBTTagCompound tag) {
        tag.setInteger(NBT_CAPACITY, capacity);
        tag.setInteger(NBT_MAX_INPUT, maxInput);
    }

    /** Restores capacity and per-tick limit from a tag (tile entity NBT or the placed item). */
    public void readSettings(NBTTagCompound tag) {
        if (tag.hasKey(NBT_CAPACITY)) capacity = Math.max(CAPACITY, tag.getInteger(NBT_CAPACITY));
        if (tag.hasKey(NBT_MAX_INPUT)) maxInput = tag.getInteger(NBT_MAX_INPUT);
        maxInput = Math.max(1, Math.min(capacity, maxInput));
    }

    // region AE network
    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            ItemStack visual = MTBlocks.essentiaHatchMEBlock == null ? null
                : new ItemStack(Item.getItemFromBlock(MTBlocks.essentiaHatchMEBlock));
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

    private boolean isOnline() {
        return gridProxy != null && gridProxy.isActive();
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
        pullFromNetwork();
    }

    /** Moves up to {@link #maxInput} essentia of the marked aspect out of the AE network and into this hatch. */
    private void pullFromNetwork() {
        Aspect aspect = getLockedAspect();
        if (aspect == null || !doesContainerAccept(aspect)) return;
        int space = getCapacity() - getStoredAmount();
        if (space <= 0) return;

        IMEMonitor<AEEssentiaStack> monitor = getEssentiaMonitor();
        if (monitor == null) return;
        long request = Math.min(maxInput, space);
        AEEssentiaStack extracted = monitor
            .extractItems(new AEEssentiaStack(aspect, request), Actionable.MODULATE, machineSource);
        if (extracted == null || extracted.getStackSize() <= 0) return;
        addToContainer(aspect, (int) Math.min(extracted.getStackSize(), space));
    }

    public int getMaxInput() {
        return maxInput;
    }

    public void setMaxInput(int value) {
        maxInput = Math.max(1, Math.min(getCapacity(), value));
        markDirty();
    }
    // endregion

    // region Persistence
    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readSettings(tag);
        if (tag.hasKey("proxy")) getProxy().readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeSettings(tag);
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

    // region GUI
    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        syncManager.syncValue("stored", new IntSyncValue(this::getStoredAmount));
        syncManager.syncValue("capacity", new IntSyncValue(this::getCapacity));
        syncManager.syncValue("online", new BooleanSyncValue(this::isOnline));
        syncManager.syncValue("max_input", new IntSyncValue(this::getMaxInput, this::setMaxInput).allowC2S());
        syncManager.syncValue("aspect", new StringSyncValue(() -> {
            Aspect aspect = getLockedAspect();
            return aspect == null ? "" : aspect.getTag();
        }));
        syncManager.registerSyncedAction(CLEAR_ACTION, Side.SERVER, packet -> setLockedAspect(null));

        IntSyncValue stored = syncManager.findSyncHandler("stored", IntSyncValue.class);
        IntSyncValue capacity = syncManager.findSyncHandler("capacity", IntSyncValue.class);
        BooleanSyncValue online = syncManager.findSyncHandler("online", BooleanSyncValue.class);
        StringSyncValue aspect = syncManager.findSyncHandler("aspect", StringSyncValue.class);

        TextFieldWidget maxInputField = new TextFieldWidget().formatAsInteger(true)
            .defaultNumber(maxInput)
            .numbersInt(1, Integer.MAX_VALUE)
            .size(64, 14);
        maxInputField.syncHandler("max_input");

        return new ModularPanel("me_essentia_hatch").size(176, 116)
            .background(GuiTextures.MC_BACKGROUND)
            .child(ButtonWidget.panelCloseButton())
            .child(
                Flow.column()
                    .sizeRel(1f)
                    .padding(8)
                    .childPadding(4)
                    .child(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocalFormatted(
                                "machine.essentiahatch.me.gui.aspect",
                                aspectName(aspect.getValue())))
                            .asWidget())
                    .child(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocalFormatted(
                                "machine.essentiahatch.me.gui.stored",
                                stored.getValue(),
                                capacity.getValue()))
                            .asWidget())
                    .child(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocal(
                                online.getValue() ? "machine.essentiahatch.me.gui.online"
                                    : "machine.essentiahatch.me.gui.offline"))
                            .asWidget())
                    .child(
                        Flow.row()
                            .childPadding(4)
                            .child(
                                IKey.lang("machine.essentiahatch.me.gui.max")
                                    .asWidget())
                            .child(maxInputField))
                    .child(
                        new ButtonWidget<>().size(80, 16)
                            .overlay(IKey.lang("machine.essentiahatch.me.gui.clear"))
                            .onMousePressed(button -> {
                                syncManager.callSyncedAction(CLEAR_ACTION);
                                return true;
                            })));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(MessTech.MODID, mainPanel);
    }

    private static String aspectName(String tag) {
        Aspect aspect = Aspect.getAspect(tag);
        return aspect == null ? StatCollector.translateToLocal("machine.essentiahatch.me.gui.no_aspect")
            : aspect.getLocalizedDescription();
    }
    // endregion
}
