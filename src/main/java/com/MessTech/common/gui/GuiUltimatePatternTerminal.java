package com.MessTech.common.gui;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiPatternTermEx;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.container.implementations.ContainerPatternTermEx;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * GUI of the Ultimate Pattern Terminal.
 * <p>
 * It <em>is</em> AE's extended pattern terminal GUI ({@link GuiPatternTermEx}), background, item list, buttons, scroll
 * bar and all; the one thing it owns is the arrangement of the input grid, which has
 * {@link com.MessTech.common.parts.PartUltimatePatternTerminal#INPUT_PAGES} pages instead of AE's two. The page count
 * itself comes from the container, which AE's own {@code ContainerPatternTermEx} is taught to take from the part by
 * {@code MixinContainerPatternTermEx}; the scroll bar that reaches those pages is unscrambled by
 * {@code MixinGuiPatternTermEx}. The outputs keep AE's own loop.
 * <p>
 * The only deviation from AE's arrangement is the inverted input grid: it is a single four cell column whose row the
 * active page picks, and this terminal has eight pages for AE's four rows, so that index is wrapped. Everything else -
 * including the page change itself - is AE's own code path.
 */
@SideOnly(Side.CLIENT)
public class GuiUltimatePatternTerminal extends GuiPatternTermEx {

    /** AE keeps its container private, and the GUI has to read the page counts back out of it. */
    private final ContainerPatternTermEx container;

    public GuiUltimatePatternTerminal(InventoryPlayer ip, ITerminalHost host) {
        super(ip, host);
        this.container = (ContainerPatternTermEx) this.inventorySlots;
    }

    @Override
    protected void updateSlotVisibility() {
        final boolean inverted = this.container.invertedSync.get();
        final int activePage = this.container.activePageSync.get();

        final int inputRows = this.container.getPatternInputsHeigh();
        final int inputCols = this.container.getPatternInputsWidth();
        final int inputPages = this.container.getPatternInputPages();

        for (int page = 0; page < inputPages; page++) {
            for (int y = 0; y < inputRows; y++) {
                for (int x = 0; x < inputCols; x++) {
                    final int slotIndex = x + y * inputCols + page * (inputCols * inputRows);
                    final VirtualMEPatternSlot slot = this.craftingSlots[slotIndex];

                    slot.setHidden(
                        inverted ? y != Math.floorMod(activePage, inputRows) || page > 0 : page != activePage);
                    slot.setX(getInputSlotOffsetX() + 18 * (inverted ? 0 : x));
                    slot.setY(this.rows * 18 + getInputSlotOffsetY() + 18 * (inverted ? x : y));
                }
            }
        }

        final int outputRows = this.container.getPatternOutputsHeigh();
        final int outputCols = this.container.getPatternOutputsWidth();
        final int outputPages = this.container.getPatternOutputPages();

        for (int page = 0; page < outputPages; page++) {
            for (int y = 0; y < outputRows; y++) {
                for (int x = 0; x < outputCols; x++) {
                    final int slotIndex = x + y * outputCols + page * (outputCols * outputRows);
                    final VirtualMEPhantomSlot slot = this.outputSlots[slotIndex];

                    slot.setHidden(
                        !inverted ? y != Math.floorMod(activePage, outputRows) || page != 0
                            : page != Math.floorMod(activePage, outputPages));

                    slot.setX((inverted ? getOutputSlotOffsetX() : 112) + 18 * (!inverted ? 0 : x));
                    slot.setY(this.rows * 18 + getOutputSlotOffsetY() + 18 * (!inverted ? x : y));
                }
            }
        }
    }
}
