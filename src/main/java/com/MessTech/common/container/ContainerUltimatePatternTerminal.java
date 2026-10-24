package com.MessTech.common.container;

import net.minecraft.entity.player.InventoryPlayer;

import com.MessTech.common.parts.PartUltimatePatternTerminal;

import appeng.api.storage.ITerminalHost;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.parts.reporting.PartPatternTerminalEx;

/**
 * Container of the Ultimate Pattern Terminal: AE's extended pattern terminal container
 * ({@link ContainerPatternTermEx}) whose page counts this mod answers instead of AE's inlined constants
 * ({@link PartUltimatePatternTerminal#INPUT_PAGES} = 8 instead of
 * {@link PartPatternTerminalEx#exPatternInputsPages} = 2; the output count is answered the same way and stays 2). The
 * client's virtual grids ({@code GuiPatternTerm#initVirtualSlots}) and the server side of NotEnoughEnergistics' recipe
 * transfer ({@code PacketNEIPatternRecipe}) both read those answers; the sync handlers, the pattern slots and the
 * virtual slot write path stay AE's own code.
 * <p>
 * Being this mod's own container on both sides (server side from {@code CommonProxy#getServerGuiElement}, client side
 * from {@link com.MessTech.common.gui.GuiUltimatePatternTerminal}'s constructor) is what replaced
 * {@code MixinContainerPatternTermEx}: overriding the methods here is enough, AE's shared class is no longer patched.
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
     * Answered from the part instead of being inherited, deliberately: other mods (GT-Not-Leisure's processing-pattern
     * capacity feature, for one) inject their own page-count answers into AE's container classes, and inheriting one of
     * those would hand AE's GUI a count that does not match this part's inventories and layout. The outputs are AE's
     * 4x4 x 2 = 32 cells, hence the constant fallback.
     */
    @Override
    public int getPatternOutputPages() {
        if (this.getPatternTerminal() instanceof PartUltimatePatternTerminal terminal) {
            return terminal.getPatternOutputPages();
        }
        return PartPatternTerminalEx.exPatternOutputPages;
    }
}
