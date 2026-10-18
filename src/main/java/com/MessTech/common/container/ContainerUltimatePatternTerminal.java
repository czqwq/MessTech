package com.MessTech.common.container;

import net.minecraft.entity.player.InventoryPlayer;

import com.MessTech.common.parts.PartUltimatePatternTerminal;

import appeng.api.storage.ITerminalHost;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.parts.reporting.PartPatternTerminalEx;

/**
 * Container of the Ultimate Pattern Terminal.
 * <p>
 * It is AE's extended pattern terminal container ({@link ContainerPatternTermEx}) with the page counts answered by
 * this mod instead of AE's inlined constants: the input page count is the count this mod's part reports
 * ({@link PartUltimatePatternTerminal#INPUT_PAGES} = 8) instead of
 * {@link PartPatternTerminalEx#exPatternInputsPages} = 2, and the output page count is answered the same way even
 * though it stays at 2. Both the client's virtual grids ({@code GuiPatternTerm#initVirtualSlots} sizes them from
 * those answers) and the server side of NotEnoughEnergistics' recipe transfer ({@code PacketNEIPatternRecipe} reads
 * the same answers) need them; everything else - the sync handlers, the pattern slots and the virtual slot write
 * path - stays AE's own code.
 * <p>
 * This class is what replaced {@code MixinContainerPatternTermEx}: the container is this mod's own on both sides
 * (server side from {@code CommonProxy#getServerGuiElement}, client side from
 * {@link com.MessTech.common.gui.GuiUltimatePatternTerminal}'s constructor), so overriding the methods here is enough
 * and AE's shared class no longer has to be patched. Answering the output page count too is what keeps another mod's
 * page-count mixin (GT-Not-Leisure's processing-pattern capacity feature, for one) from changing it behind this
 * terminal's back.
 */
public class ContainerUltimatePatternTerminal extends ContainerPatternTermEx {

    public ContainerUltimatePatternTerminal(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable);
    }

    @Override
    public int getPatternInputPages() {
        if (this.getPatternTerminal() instanceof PartUltimatePatternTerminal terminal) {
            return terminal.getPatternInputPages();
        }
        // This mod's opening paths always pass its own part; any other host keeps AE's answer.
        return PartPatternTerminalEx.exPatternInputsPages;
    }

    /**
     * Both page counts are answered from the part instead of being inherited, and that is deliberate: other mods
     * (GT-Not-Leisure's processing-pattern capacity feature, for one) inject their own page-count answers into AE's
     * container classes. Inheriting one of those would hand AE's GUI a page count that does not match this part's
     * inventories and its own layout. The outputs are AE's 4x4 x 2 = 32 cells, hence the constant fallback.
     */
    @Override
    public int getPatternOutputPages() {
        if (this.getPatternTerminal() instanceof PartUltimatePatternTerminal terminal) {
            return terminal.getPatternOutputPages();
        }
        return PartPatternTerminalEx.exPatternOutputPages;
    }
}
