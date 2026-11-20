package com.MessTech.common.machine.hatch;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import com.MessTech.common.render.highlight.BlockPosHighlighter;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.MTEHatchVacuumConveyorGui;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyor;

/**
 * MUI2 GUI of {@link MTWirelessVacuumConveyorInput} / {@link MTWirelessVacuumConveyorOutput}: GT5U's vacuum conveyor
 * content (the 4x4 circuit-component grid, the storage-cell drain row and the void button) plus a live link bar.
 * <p>
 * The link bar sits in the strip under the 4x4 grid: a coloured dot and status text (green linked / red unbound /
 * yellow broken), the stored counterpart address, and an 18x18 highlight button that outlines the counterpart in the
 * world through {@link BlockPosHighlighter} and closes the screen. The bar is one row tall; the panel uses the nanochip
 * theme texture's native 198x203 size, which leaves exactly that strip free below the centred 72x72 grid instead of
 * overlapping GT's content.
 * <p>
 * The link state and address are server-side data, so they reach the client through {@link IntSyncValue}s that MUI2
 * re-reads every tick in {@code detectAndSendChanges}; the getters are neutral when they run on the client (see
 * {@link WirelessVacuumConveyorUtil#linkState}).
 */
public class MTWirelessVacuumConveyorGui extends MTEHatchVacuumConveyorGui {

    /** Native size of the nanochip panel texture, see {@code GTGuiTextures.BACKGROUND_NANOCHIP}. */
    private static final int PANEL_WIDTH = 198;
    private static final int PANEL_HEIGHT = 203;

    /** Sync keys of the link bar. Both sides build the GUI with these exact keys. */
    private static final String SYNC_STATE = "linkState";
    private static final String SYNC_HAS_TARGET = "linkHasTarget";
    private static final String SYNC_TARGET_DIM = "linkTargetDim";
    private static final String SYNC_TARGET_X = "linkTargetX";
    private static final String SYNC_TARGET_Y = "linkTargetY";
    private static final String SYNC_TARGET_Z = "linkTargetZ";

    /** Lang keys of the link bar. */
    private static final String KEY_STATE_LINKED = "machine.wirelessvacuum.gui.state.linked";
    private static final String KEY_STATE_UNBOUND = "machine.wirelessvacuum.gui.state.unbound";
    private static final String KEY_STATE_BROKEN = "machine.wirelessvacuum.gui.state.broken";
    private static final String KEY_TARGET = "machine.wirelessvacuum.gui.target";
    private static final String KEY_TARGET_NONE = "machine.wirelessvacuum.gui.target.none";
    private static final String KEY_HIGHLIGHT = "machine.wirelessvacuum.gui.highlight";
    private static final String KEY_HIGHLIGHT_DISABLED = "machine.wirelessvacuum.gui.highlight.disabled";

    private static final int STATUS_DOT_SIZE = 8;

    /** Coloured square drawables of the three link states; the colours follow AE2's wireless status indicator. */
    private static final IDrawable DOT_LINKED = new Rectangle().color(Color.GREEN.main);
    private static final IDrawable DOT_UNBOUND = new Rectangle().color(Color.RED.main);
    private static final IDrawable DOT_BROKEN = new Rectangle().color(Color.YELLOW.main);

    public MTWirelessVacuumConveyorGui(MTEHatchVacuumConveyor hatch) {
        super(hatch);
    }

    // ---------------------------------------------------------------- panel size / sync values

    @Override
    protected int getBasePanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getBasePanelHeight() {
        return PANEL_HEIGHT;
    }

    @Override
    public void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            SYNC_STATE,
            new IntSyncValue(
                () -> WirelessVacuumConveyorUtil.linkState(machine)
                    .ordinal()));
        syncManager.syncValue(
            SYNC_HAS_TARGET,
            new BooleanSyncValue(() -> WirelessVacuumConveyorUtil.linkTargetOf(machine) != null));
        syncManager.syncValue(SYNC_TARGET_DIM, new IntSyncValue(this::targetDim));
        syncManager.syncValue(SYNC_TARGET_X, new IntSyncValue(this::targetX));
        syncManager.syncValue(SYNC_TARGET_Y, new IntSyncValue(this::targetY));
        syncManager.syncValue(SYNC_TARGET_Z, new IntSyncValue(this::targetZ));
    }

    private WirelessVacuumConveyorUtil.Target linkTarget() {
        return WirelessVacuumConveyorUtil.linkTargetOf(machine);
    }

    /** Stored counterpart address component; 0 while no counterpart is paired. */
    private int targetDim() {
        WirelessVacuumConveyorUtil.Target target = linkTarget();
        return target == null ? 0 : target.dimension;
    }

    private int targetX() {
        WirelessVacuumConveyorUtil.Target target = linkTarget();
        return target == null ? 0 : target.x;
    }

    private int targetY() {
        WirelessVacuumConveyorUtil.Target target = linkTarget();
        return target == null ? 0 : target.y;
    }

    private int targetZ() {
        WirelessVacuumConveyorUtil.Target target = linkTarget();
        return target == null ? 0 : target.z;
    }

    // ---------------------------------------------------------------- link bar

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createContentSection(panel, syncManager).child(createLinkStatusRow(syncManager));
    }

    /** One-row link bar under the 4x4 component grid, bottom-left of the content section. */
    private Flow createLinkStatusRow(PanelSyncManager syncManager) {
        IntSyncValue stateSync = syncManager.findSyncHandler(SYNC_STATE, IntSyncValue.class);
        BooleanSyncValue hasTargetSync = syncManager.findSyncHandler(SYNC_HAS_TARGET, BooleanSyncValue.class);
        IntSyncValue dimSync = syncManager.findSyncHandler(SYNC_TARGET_DIM, IntSyncValue.class);
        IntSyncValue xSync = syncManager.findSyncHandler(SYNC_TARGET_X, IntSyncValue.class);
        IntSyncValue ySync = syncManager.findSyncHandler(SYNC_TARGET_Y, IntSyncValue.class);
        IntSyncValue zSync = syncManager.findSyncHandler(SYNC_TARGET_Z, IntSyncValue.class);

        return Flow.row()
            .coverChildren()
            .bottomRel(0)
            .leftRel(0)
            .childPadding(4)
            .child(createStateDot(stateSync))
            .child(createStateText(stateSync))
            .child(createHighlightButton(hasTargetSync, dimSync, xSync, ySync, zSync));
    }

    /** Small coloured square carrying the state at a glance (AE2's wireless connector indicator idea). */
    private IWidget createStateDot(IntSyncValue stateSync) {
        return new IDrawable.DrawableWidget(
            new DynamicDrawable(() -> stateDot(WirelessVacuumConveyorUtil.decodeState(stateSync.getIntValue()))))
                .size(STATUS_DOT_SIZE);
    }

    private static IDrawable stateDot(WirelessVacuumConveyorUtil.LinkState state) {
        if (state == WirelessVacuumConveyorUtil.LinkState.LINKED) return DOT_LINKED;
        if (state == WirelessVacuumConveyorUtil.LinkState.UNBOUND) return DOT_UNBOUND;
        return DOT_BROKEN;
    }

    /** Coloured status text: green linked, red unbound, yellow for every other broken state. */
    private IWidget createStateText(IntSyncValue stateSync) {
        return IKey.dynamic(() -> stateLine(WirelessVacuumConveyorUtil.decodeState(stateSync.getIntValue())))
            .asWidget();
    }

    private static String stateLine(WirelessVacuumConveyorUtil.LinkState state) {
        String key = state == WirelessVacuumConveyorUtil.LinkState.LINKED ? KEY_STATE_LINKED
            : state == WirelessVacuumConveyorUtil.LinkState.UNBOUND ? KEY_STATE_UNBOUND : KEY_STATE_BROKEN;
        return WirelessVacuumConveyorUtil.stateColor(state) + StatCollector.translateToLocal(key);
    }

    /**
     * Stored counterpart address, or the "not set" line while the hatch is not paired. Shown in the highlight button's
     * tooltip rather than inline: the link bar is one 18-px strip, and the address (up to four numbers) does not fit
     * next to the state text in both languages without being clipped by the panel edge.
     */
    private static String targetLine(BooleanSyncValue hasTargetSync, IntSyncValue dimSync, IntSyncValue xSync,
        IntSyncValue ySync, IntSyncValue zSync) {
        return hasTargetSync.getBoolValue() ? StatCollector.translateToLocalFormatted(
            KEY_TARGET,
            dimSync.getIntValue(),
            xSync.getIntValue(),
            ySync.getIntValue(),
            zSync.getIntValue()) : StatCollector.translateToLocal(KEY_TARGET_NONE);
    }

    /**
     * Highlight button: outlines the paired hatch in the world and closes the screen. It stays visible while no
     * counterpart is stored (GT5U's drone-centre highlight button does the same) but does nothing and explains why in
     * its tooltip, because a disabled MUI2 widget is not rendered at all.
     */
    private IWidget createHighlightButton(BooleanSyncValue hasTargetSync, IntSyncValue dimSync, IntSyncValue xSync,
        IntSyncValue ySync, IntSyncValue zSync) {
        return new ButtonWidget<>().size(18, 18)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_HIGHLIGHT_BLOCK)
            .tooltipBuilder(tooltip -> {
                tooltip.addLine(IKey.lang(() -> hasTargetSync.getBoolValue() ? KEY_HIGHLIGHT : KEY_HIGHLIGHT_DISABLED));
                tooltip.addLine(IKey.dynamic(() -> targetLine(hasTargetSync, dimSync, xSync, ySync, zSync)));
            })
            .tooltipAutoUpdate(true)
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0) return false;
                if (!hasTargetSync.getBoolValue()) return true;
                highlightAndClose(dimSync.getIntValue(), xSync.getIntValue(), ySync.getIntValue(), zSync.getIntValue());
                return true;
            });
    }

    /** Client-only: outline the paired hatch and close the GUI so the player can look at it. */
    @SideOnly(Side.CLIENT)
    private static void highlightAndClose(int dimension, int x, int y, int z) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        BlockPosHighlighter.highlight(player, dimension, x, y, z);
        player.closeScreen();
    }
}
