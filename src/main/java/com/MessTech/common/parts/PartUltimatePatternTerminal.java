package com.MessTech.common.parts;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import com.MessTech.init.CommonProxy;
import com.MessTech.init.MessTech;

import appeng.api.AEApi;
import appeng.api.parts.IPatternTerminalEx;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.GuiBridge;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.util.Platform;

/**
 * The Ultimate Pattern Terminal (终极样板编码终端), as an AE <em>cable part</em>: AE's extended pattern terminal contract
 * ({@code IPatternTerminalEx} - inverted flag, active input page), the same two
 * {@link appeng.tile.inventory.IAEStackInventory}s and encoding path, and AE's behaviour for the contract's part-class
 * parts
 * ({@code GUI_PATTERN_TERMINAL_EX} bridge, forced processing mode, the "orientation an encoded pattern fits" call, NBT
 * keys,
 * GUI icon), copied from AE rv3-beta-1080-GTNH's {@code PartPatternTerminalEx}.
 * <p>
 * Deliberately <em>not</em> extending {@code PartPatternTerminalEx}: mods mix into that class and treat every instance
 * as AE's
 * standard terminal - GT-Not-Leisure's capacity feature resizes the {@code crafting}/{@code output} inventories to
 * {@code 16 x multiplier} cells (NBT too), which shrank this terminal's 128-cell input grid to 32 and crashed page
 * three with
 * {@code ArrayIndexOutOfBoundsException}. Extending only {@link PartPatternTerminal} keeps such {@code instanceof}
 * gates off
 * this part.
 * <p>
 * The one difference from AE's part is the <em>input</em> grid: 4x4 x {@value #INPUT_PAGES} = 128 cells against AE's
 * 4x4 x 2
 * = 32, so the Nano-Scale Foundry "24" pool (54 item + 54 fluid inputs) fits one grid; the outputs keep 4x4 x 2 = 32.
 * That
 * page count reaches AE's layout through this mod's container and GUI ({@code ContainerUltimatePatternTerminal},
 * {@code GuiUltimatePatternTerminal}), since AE's reports the constant {@code 2}. The inverted/active page state lives
 * in this
 * class' own fields: {@code PartPatternTerminalEx#setInverted} throws away inputs past what the inverted layout shows,
 * and this
 * terminal has four times as many.
 */
public class PartUltimatePatternTerminal extends PartPatternTerminal implements IPatternTerminalEx {

    /** Input pages of this terminal; AE's extended pattern terminal has 2. */
    public static final int INPUT_PAGES = 8;

    /** AE's extended pattern terminal's input/output cells per page, kept for the copied layout. */
    private static final int SIDE_WIDTH = 4;
    private static final int SIDE_HEIGH = 4;
    private static final int OUTPUT_PAGES = 2;

    private boolean inverted;
    private int activePage;

    public PartUltimatePatternTerminal(ItemStack is) {
        super(is);
        // AE's extended pattern terminal is a processing-only terminal; PartPatternTerminalEx forces that here too.
        setCraftingRecipe(false);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        // Same keys AE's PartPatternTerminalEx reads; the setters below are this class' own.
        this.setInverted(data.getBoolean("inverted"));
        this.setActivePage(data.getInteger("activePage"));
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("inverted", this.inverted);
        data.setInteger("activePage", this.activePage);
    }

    @Override
    protected GuiBridge getPatternGui() {
        // Makes PartPatternTerminal#getGui hand the caller the extended pattern terminal's bridge, exactly as AE's own
        // extended part does; onPartActivate below only uses that answer as its permission gate.
        return GuiBridge.GUI_PATTERN_TERMINAL_EX;
    }

    /**
     * AE's orientation choice for a freshly loaded pattern, from {@code PartPatternTerminalEx}: an inverted pattern
     * (few
     * inputs, many outputs) gets the single-column layout, and the page goes back to 0.
     */
    @Override
    public void exPatternTerminalCall(IAEStack<?>[] in, IAEStack<?>[] out) {
        int inputsCount = 0;
        int outputCount = 0;

        for (IAEStack<?> inItem : in) {
            if (inItem != null) {
                inputsCount++;
            }
        }

        for (IAEStack<?> outItem : out) {
            if (outItem != null) {
                outputCount++;
            }
        }

        this.setInverted(inputsCount <= 8 && outputCount >= 8);
        this.setActivePage(0);
    }

    @Override
    public boolean isInverted() {
        return this.inverted;
    }

    @Override
    public void setInverted(boolean inverted) {
        // AE's override clears the input cells the inverted layout cannot show; this terminal's input grid is four
        // times
        // that size and the outputs keep AE's placement, so only the arrangement changes here.
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
    public void setCraftingRecipe(boolean craftingMode) {
        // AE's extended pattern terminal cannot encode crafting patterns (no crafting grid) and ignores the mode its
        // pattern items carry; PartPatternTerminalEx does exactly this.
        super.setCraftingRecipe(false);
    }

    @Override
    protected int getPatternInputsWidth() {
        return SIDE_WIDTH;
    }

    @Override
    protected int getPatternInputsHeigh() {
        return SIDE_HEIGH;
    }

    /**
     * Widened to public for {@code ContainerUltimatePatternTerminal}: the container reports page counts to AE's GUI and
     * must report this part's own numbers, not anything a mixin puts into AE's container classes.
     */
    @Override
    public int getPatternInputPages() {
        return INPUT_PAGES;
    }

    @Override
    protected int getPatternOutputsWidth() {
        return SIDE_WIDTH;
    }

    @Override
    protected int getPatternOutputsHeigh() {
        return SIDE_HEIGH;
    }

    /**
     * Widened to public for {@code ContainerUltimatePatternTerminal}: the container reports page counts to AE's GUI and
     * must report this part's own numbers, not anything a mixin puts into AE's container classes.
     */
    @Override
    public int getPatternOutputPages() {
        return OUTPUT_PAGES;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance()
            .definitions()
            .parts()
            .patternTerminalEx()
            .maybeStack(1)
            .orNull();
    }

    /**
     * Opens this terminal's GUI through Forge instead of AE's {@code GuiBridge}: the bridge maps container class to GUI
     * class
     * by name and cannot take a new terminal, so the part's side travels in the GUI id and
     * {@code CommonProxy}/{@code ClientProxy} resolve the part again from the cable bus.
     * <p>
     * Every click opens this terminal, sneaking included; AE's own path would report AE's two input pages and show a
     * quarter
     * of the input grid.
     */
    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
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
