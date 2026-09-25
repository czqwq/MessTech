package com.MessTech.common.parts;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import com.MessTech.init.CommonProxy;
import com.MessTech.init.MessTech;

import appeng.core.sync.GuiBridge;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.util.Platform;

/**
 * The Ultimate Pattern Terminal (终极样板编码终端), as an AE <em>cable part</em>.
 * <p>
 * It is AE's extended pattern terminal ({@link PartPatternTerminalEx}) and nothing else: the same
 * {@code IPatternTerminalEx} contract, the same two {@link appeng.tile.inventory.IAEStackInventory}s, the same
 * "inverted" flag and active page, and the same 4x4 slot pages - which is what lets the GUI stay a subclass of AE's
 * {@code GuiPatternTermEx} and the container AE's own {@code ContainerPatternTermEx}. Being a part it lives on a
 * cable bus like every other AE terminal and never becomes a block of its own.
 * <p>
 * The one deliberate difference is the size of the <em>input</em> grid: 4x4 x {@value #INPUT_PAGES} = 128 cells
 * instead of AE's 4x4 x 2 = 32, so the Nano-Scale Foundry "24" pool (up to 54 item plus 54 fluid inputs) fits on one
 * grid. The outputs keep AE's own 4x4 x 2 = 32 cells. {@code MixinContainerPatternTermEx} forwards that page count
 * to AE's container, because AE's own container reports the constant {@code 2} it gets from
 * {@code PartPatternTerminalEx}.
 * <p>
 * The inverted/active page state is kept here instead of using AE's fields: {@code PartPatternTerminalEx#setInverted}
 * throws away every input past the cells the inverted layout can show, and this terminal has four times as many.
 */
public class PartUltimatePatternTerminal extends PartPatternTerminalEx {

    /** Input pages of this terminal; AE's extended pattern terminal has 2. */
    public static final int INPUT_PAGES = 8;

    private boolean inverted;
    private int activePage;

    public PartUltimatePatternTerminal(ItemStack is) {
        super(is);
    }

    @Override
    public int getPatternInputPages() {
        return INPUT_PAGES;
    }

    @Override
    public boolean isInverted() {
        return this.inverted;
    }

    @Override
    public void setInverted(boolean inverted) {
        // AE's own override clears the input cells the inverted layout cannot show. This terminal's input grid is
        // four times that size and the outputs keep AE's placement, so nothing has to be thrown away - only the
        // arrangement changes.
        this.inverted = inverted;
    }

    @Override
    public int getActivePage() {
        return this.activePage;
    }

    @Override
    public void setActivePage(int activePage) {
        this.activePage = Math.max(0, Math.min(activePage, INPUT_PAGES - 1));
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        // The super class writes its own fields, which this terminal never sets; write the live ones over them.
        data.setBoolean("inverted", this.inverted);
        data.setInteger("activePage", this.activePage);
    }

    /**
     * Opens this terminal's GUI through Forge instead of AE's {@code GuiBridge}: the bridge maps a container class to
     * a GUI class by name and cannot be extended with a new terminal, so the side this part sits on travels in the GUI
     * id and {@code CommonProxy}/{@code ClientProxy} resolve the part again from the cable bus.
     */
    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
        if (player.isSneaking()) {
            return super.onPartActivate(player, pos);
        }
        if (Platform.isClient()) {
            return true;
        }

        // AE's own gate: PartPatternTerminal#getGui answers with the plain ME terminal when the player may not craft.
        if (this.getGui(player) != GuiBridge.GUI_PATTERN_TERMINAL_EX) {
            return false;
        }

        final TileEntity tile = this.getTile();
        player.openGui(
            MessTech.instance,
            CommonProxy.ultimatePatternTerminalGuiId(this.getSide()),
            tile.getWorldObj(),
            tile.xCoord,
            tile.yCoord,
            tile.zCoord);
        return true;
    }
}
