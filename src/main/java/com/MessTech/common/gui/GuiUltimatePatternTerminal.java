package com.MessTech.common.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import com.MessTech.common.container.ContainerUltimatePatternTerminal;
import com.MessTech.common.parts.PartUltimatePatternTerminal;

import appeng.api.config.PatternSlotConfig;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.core.AppEng;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * GUI of the Ultimate Pattern Terminal: AE's extended pattern terminal GUI copied whole from Applied Energistics 2
 * rv3-beta-1080-GTNH ({@code appeng.client.gui.implementations.GuiPatternTermEx}, LGPL-3.0). Copying rather than
 * extending is what retired {@code MixinGuiPatternTermEx} / {@code MixinContainerPatternTermEx}: the two things AE's
 * GUI cannot be told by subclassing, its private scroll bar range and its client-side container, are chosen here
 * directly, and no other mod's extended pattern terminal is touched.
 * <p>
 * One deliberate difference: {@link PartUltimatePatternTerminal#INPUT_PAGES} pages of 4x4 input cells instead of AE's
 * two, with the scroll bar widened to reach them; everything else, the page change included, is AE's own code path.
 * The inverted (single input column) layout is AE's too, except that the active page wraps over that column's four
 * rows ({@code activePage % rows}) - eight pages do not fit AE's four rows.
 * <p>
 * Only the output side leaves AE's loop: the input pages are grouped four per output page, so the normal layout's
 * four-cell output column walks all 32 output cells in order and the inverted layout's 16-cell output page stays on
 * output page 0 for input pages 0-3 and on page 1 for pages 4-7. AE's two-page loop would wrap page 4 back to the
 * first page's outputs.
 */
@SideOnly(Side.CLIENT)
public class GuiUltimatePatternTerminal extends GuiPatternTerm {

    private final ContainerUltimatePatternTerminal container;
    private GuiImgButton invertBtn;
    private final GuiScrollbar processingScrollBar = new GuiScrollbar();

    public GuiUltimatePatternTerminal(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        // AE's GUI constructor hardcodes new ContainerPatternTermEx(...) here; this mod's container answers the
        // terminal's input page count instead (see ContainerUltimatePatternTerminal).
        super(inventoryPlayer, te, new ContainerUltimatePatternTerminal(inventoryPlayer, te));
        this.container = (ContainerUltimatePatternTerminal) this.inventorySlots;

        this.container.invertedSync.onClientChange((oldValue, newValue) -> {
            if (oldValue != newValue) {
                this.updateSlotVisibility();
            }
        });
        this.container.activePageSync.onClientChange((oldValue, newValue) -> {
            if (oldValue != newValue) {
                this.updateSlotVisibility();
            }
        });

        this.setReservedSpace(81);

        // AE hardcodes its own terminal's two pages into this call; the page count lives in one place (the part).
        processingScrollBar.setHeight(70)
            .setWidth(7)
            .setLeft(6)
            .setRange(0, Math.max(0, this.container.getPatternInputPages() - 1), 1);
        processingScrollBar.setTexture(AppEng.MOD_ID, "guis/pattern3.png", 242, 0);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (this.invertBtn == btn) {
            container.invertedSync.set(!container.invertedSync.get());
            this.updateSlotVisibility();
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        invertBtn = new GuiImgButton(
            this.guiLeft + 87,
            this.guiTop + this.ySize - 145,
            Settings.ACTIONS,
            container.invertedSync.get() ? PatternSlotConfig.C_4_16 : PatternSlotConfig.C_16_4);
        invertBtn.setHalfSize(true);
        this.buttonList.add(this.invertBtn);

        processingScrollBar.setTop(this.ySize - 164);
        this.updateSlotVisibility();
    }

    @Override
    protected int getInputSlotOffsetX() {
        return 15;
    }

    @Override
    protected int getInputSlotOffsetY() {
        return 32;
    }

    @Override
    protected int getOutputSlotOffsetX() {
        return 58;
    }

    @Override
    protected int getOutputSlotOffsetY() {
        return 32;
    }

    @Override
    protected void drawTitle() {
        this.fontRendererObj.drawString(
            GuiText.PatternTerminalEx.getLocal(),
            8,
            this.ySize - 96 + 2 - this.getReservedSpace(),
            ColorUtils.guiTextColorGray.getColor());
        this.processingScrollBar.draw(this);
    }

    @Override
    protected String getBackground() {
        return container.invertedSync.get() ? "guis/pattern4.png" : "guis/pattern3.png";
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        final int offset = container.invertedSync.get() ? 18 * -3 : 0;

        doubleBtn.xPosition = this.guiLeft + 88 + offset;
        clearBtn.xPosition = doubleBtn.xPosition;
        invertBtn.xPosition = doubleBtn.xPosition;

        substitutionsEnabledBtn.xPosition = this.guiLeft + 98 + offset;
        substitutionsDisabledBtn.xPosition = substitutionsEnabledBtn.xPosition;
        beSubstitutionsEnabledBtn.xPosition = substitutionsEnabledBtn.xPosition;
        beSubstitutionsDisabledBtn.xPosition = substitutionsEnabledBtn.xPosition;

        clearBtn.yPosition = this.guiTop + this.ySize - 165;
        substitutionsEnabledBtn.yPosition = clearBtn.yPosition;
        substitutionsDisabledBtn.yPosition = clearBtn.yPosition;

        doubleBtn.yPosition = this.guiTop + this.ySize - 155;
        beSubstitutionsEnabledBtn.yPosition = doubleBtn.yPosition;
        beSubstitutionsDisabledBtn.yPosition = doubleBtn.yPosition;

        invertBtn.yPosition = this.guiTop + this.ySize - 145;

        processingScrollBar.setCurrentScroll(container.activePageSync.get());

        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        final int currentScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);

        super.mouseClicked(xCoord, yCoord, btn);

        if (currentScroll != this.processingScrollBar.getCurrentScroll()) {
            changeActivePage();
        }
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        final int currentScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.clickMove(y - this.guiTop);
        super.mouseClickMove(x, y, c, d);

        if (currentScroll != this.processingScrollBar.getCurrentScroll()) {
            changeActivePage();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        final int wheel = Mouse.getEventDWheel();

        if (wheel != 0) {
            final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight;

            if (this.processingScrollBar.contains(x - this.guiLeft, y - this.guiTop)) {
                final int currentScroll = this.processingScrollBar.getCurrentScroll();
                this.processingScrollBar.wheel(wheel);

                if (currentScroll != this.processingScrollBar.getCurrentScroll()) {
                    changeActivePage();
                }
            }
        }
    }

    private void changeActivePage() {
        container.activePageSync.set(this.processingScrollBar.getCurrentScroll());
        this.updateSlotVisibility();
    }

    public void onUpdateInvertedOrActivePage() {
        updateSlotVisibility();
    }

    @Override
    protected void updateSlotVisibility() {
        final boolean inverted = this.container.invertedSync.get();
        final int inputPages = this.container.getPatternInputPages();
        // The part clamps the page it stores, but a stale sync value must not index outside the grid either.
        final int activePage = Math.max(0, Math.min(this.container.activePageSync.get(), inputPages - 1));

        final int inputRows = this.container.getPatternInputsHeigh();
        final int inputCols = this.container.getPatternInputsWidth();

        for (int page = 0; page < inputPages; page++) {
            for (int y = 0; y < inputRows; y++) {
                for (int x = 0; x < inputCols; x++) {
                    final int slotIndex = x + y * inputCols + page * (inputCols * inputRows);
                    final VirtualMEPhantomSlot slot = this.craftingSlots[slotIndex];

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

        // Input pages are grouped four per output page (8 over 2 here): the normal layout's four-cell output row is
        // the page-in-group, the inverted layout shows the whole 16-cell output page, so both walk pages 0-3 over
        // output page 0 and pages 4-7 over page 1 instead of wrapping back to page 0.
        final int inputPagesPerOutputPage = Math.max(1, inputPages / Math.max(1, outputPages));
        final int outputPage = Math.min(outputPages - 1, activePage / inputPagesPerOutputPage);
        final int outputRow = Math.min(outputRows - 1, activePage % inputPagesPerOutputPage);

        for (int page = 0; page < outputPages; page++) {
            for (int y = 0; y < outputRows; y++) {
                for (int x = 0; x < outputCols; x++) {
                    final int slotIndex = x + y * outputCols + page * (outputCols * outputRows);
                    final VirtualMEPhantomSlot slot = this.outputSlots[slotIndex];

                    slot.setHidden(!inverted ? y != outputRow || page != outputPage : page != outputPage);

                    slot.setX((inverted ? getOutputSlotOffsetX() : 112) + 18 * (!inverted ? 0 : x));
                    slot.setY(this.rows * 18 + getOutputSlotOffsetY() + 18 * (!inverted ? x : y));
                }
            }
        }
    }
}
