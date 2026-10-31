package com.MessTech.common.gui;

import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.EnumChatFormatting;

import com.MessTech.common.gui.base.MTMultiMachineBaseGui;
import com.MessTech.common.machine.MTProtocalCore;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.DragHandle;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * The 协议核心 / Protocal Core machine GUI: the normal MessTech multiblock screen plus a potion-effect program button.
 * <p>
 * The button opens a draggable {@code protocalEffects} window listing every registered potion id (the potion's status
 * icon from vanilla's {@code inventory.png} next to its localized name). Each row has an enable/disable toggle (all
 * effects are disabled by default) and a black/white list button using the space mining module's filter icons
 * ({@link GTGuiTextures#TT_OVERLAY_BUTTON_BLACKLIST} / {@link GTGuiTextures#TT_OVERLAY_BUTTON_WHITELIST}); that button
 * opens a second draggable window per effect with the mode toggle on top and the blacklist / whitelist player name
 * fields below it.
 * <p>
 * Everything is synced through {@link PanelSyncManager} sync values registered here: booleans and the strings are
 * {@code allowC2S()} so the client edits reach the machine's NBT, while the read-only status values (hatch level,
 * round lengths, bound player / team) are pushed from the server by MUI2's change detection.
 */
public class MTProtocalCoreGui extends MTMultiMachineBaseGui<MTProtocalCore> {

    // region Sync keys
    private static final String SYNC_ENABLED = "protocalEnabled";
    private static final String SYNC_WHITELIST_MODE = "protocalWhitelistMode";
    private static final String SYNC_BLACKLIST = "protocalBlacklist";
    private static final String SYNC_WHITELIST = "protocalWhitelist";
    private static final String SYNC_HATCH_LEVEL = "protocalHatchLevel";
    private static final String SYNC_HATCH_VOLTAGE = "protocalHatchVoltage";
    private static final String SYNC_ENABLED_COUNT = "protocalEnabledCount";
    private static final String SYNC_PLANNED_ROUND = "protocalPlannedRound";
    private static final String SYNC_ROUND_TOTAL = "protocalRoundTotal";
    private static final String SYNC_ROUND_LEFT = "protocalRoundLeft";
    private static final String SYNC_TEAM = "protocalTeam";
    private static final String SYNC_OWNER = "protocalOwner";
    // endregion

    // region Layout
    private static final int ROW_HEIGHT = 18;
    private static final int BUTTON_SIZE = 16;
    private static final int EFFECT_NAME_WIDTH = 128;
    private static final int DRAG_BAR_HEIGHT = 10;

    private static final int EFFECTS_PANEL_WIDTH = 220;
    private static final int EFFECTS_PANEL_HEIGHT = 218;
    private static final int EFFECT_LIST_WIDTH = 210;
    private static final int EFFECT_LIST_HEIGHT = 152;

    private static final int FILTER_PANEL_WIDTH = 216;
    private static final int FILTER_PANEL_HEIGHT = 148;
    private static final int FILTER_FIELD_WIDTH = 200;
    // endregion

    /** Lazily built 18x18 potion status icons (vanilla keeps them in the inventory texture). */
    private static final UITexture[] POTION_ICONS = new UITexture[MTProtocalCore.MAX_EFFECTS];

    public MTProtocalCoreGui(MTProtocalCore multiblock) {
        super(multiblock);
    }

    // region Sync values
    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        for (int potionId = 0; potionId < MTProtocalCore.MAX_EFFECTS; potionId++) {
            // High ids that GTNH's fixPotionLimit would truncate are not enable-able; they only get a greyed out row.
            if (!MTProtocalCore.isEffectApplyable(potionId)) continue;
            final int effectId = potionId;
            syncManager.syncValue(
                SYNC_ENABLED + effectId,
                new BooleanSyncValue(
                    () -> multiblock.isEffectEnabled(effectId),
                    enabled -> multiblock.setEffectEnabled(effectId, enabled)).allowC2S());
            syncManager.syncValue(
                SYNC_WHITELIST_MODE + effectId,
                new BooleanSyncValue(
                    () -> multiblock.isEffectWhitelistMode(effectId),
                    whitelist -> multiblock.setEffectWhitelistMode(effectId, whitelist)).allowC2S());
            syncManager.syncValue(
                SYNC_BLACKLIST + effectId,
                new StringSyncValue(
                    () -> multiblock.getEffectBlacklist(effectId),
                    names -> multiblock.setEffectBlacklist(effectId, names)).allowC2S());
            syncManager.syncValue(
                SYNC_WHITELIST + effectId,
                new StringSyncValue(
                    () -> multiblock.getEffectWhitelist(effectId),
                    names -> multiblock.setEffectWhitelist(effectId, names)).allowC2S());
        }
        syncManager.syncValue(SYNC_HATCH_LEVEL, new IntSyncValue(multiblock::getHatchLevel));
        syncManager.syncValue(SYNC_HATCH_VOLTAGE, new LongSyncValue(multiblock::getHatchVoltage));
        syncManager.syncValue(SYNC_ENABLED_COUNT, new IntSyncValue(multiblock::getEnabledEffectCount));
        syncManager.syncValue(SYNC_PLANNED_ROUND, new IntSyncValue(multiblock::getPlannedRoundSeconds));
        syncManager.syncValue(SYNC_ROUND_TOTAL, new IntSyncValue(multiblock::getRoundTotalSeconds));
        syncManager.syncValue(SYNC_ROUND_LEFT, new IntSyncValue(multiblock::getRoundRemainingSeconds));
        syncManager.syncValue(SYNC_TEAM, new StringSyncValue(multiblock::getTeamNameForGui));
        syncManager.syncValue(SYNC_OWNER, new StringSyncValue(multiblock::getOwnerNameForGui));
    }
    // endregion

    // region Sub panels
    @Override
    protected void initPanelMap(ModularPanel parent, PanelSyncManager syncManager) {
        super.initPanelMap(parent, syncManager);
        panelMap.put(
            "protocalEffects",
            syncManager.syncedPanel(
                "protocalEffects",
                true,
                (panelSyncManager, panelSyncHandler) -> createEffectsPanel(syncManager, parent, panelSyncHandler)));
        for (int potionId = 0; potionId < MTProtocalCore.MAX_EFFECTS; potionId++) {
            if (!MTProtocalCore.isEffectApplyable(potionId)) continue;
            final int effectId = potionId;
            panelMap.put(
                "protocalFilter" + effectId,
                syncManager.syncedPanel(
                    "protocalFilter" + effectId,
                    true,
                    (panelSyncManager,
                        panelSyncHandler) -> createFilterPanel(syncManager, parent, effectId, panelSyncHandler)));
        }
    }

    /**
     * The draggable effect list window: one row per registered potion (status icon, name, enable toggle, black/white
     * list button), with the potion list itself scrolling inside the window.
     */
    private ModularPanel createEffectsPanel(PanelSyncManager syncManager, ModularPanel parent,
        IPanelHandler panelHandler) {
        ModularPanel panel = new ModularPanel("protocalEffects").size(EFFECTS_PANEL_WIDTH, EFFECTS_PANEL_HEIGHT)
            .relative(parent)
            .top(0)
            .left(0);

        ListWidget<IWidget, ?> list = new ListWidget<>().size(EFFECT_LIST_WIDTH, EFFECT_LIST_HEIGHT)
            .padding(2);

        for (int potionId = 0; potionId < MTProtocalCore.MAX_EFFECTS; potionId++) {
            if (Potion.potionTypes[potionId] == null) continue;
            list.child(
                MTProtocalCore.isEffectApplyable(potionId) ? createEffectRow(syncManager, potionId)
                    : createUnsupportedEffectRow(potionId));
        }

        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(createDragBar())
                .child(
                    Flow.row()
                        .fullWidth()
                        .coverChildrenHeight()
                        .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(
                            IKey.lang("machine.protocalcore.gui.effects.title")
                                .asWidget())
                        .child(createCloseButton(panelHandler)))
                .child(list)
                .child(
                    IKey.lang("machine.protocalcore.gui.effects.hint")
                        .asWidget()
                        .width(EFFECTS_PANEL_WIDTH - 12)
                        .textAlign(Alignment.TopLeft)
                        .marginTop(2)));
        return panel;
    }

    /** One row of the effect list: potion icon, localized name, enable toggle and the black/white list button. */
    private IWidget createEffectRow(PanelSyncManager syncManager, int potionId) {
        BooleanSyncValue enabled = syncManager.findSyncHandler(SYNC_ENABLED + potionId, BooleanSyncValue.class);
        BooleanSyncValue whitelistMode = syncManager
            .findSyncHandler(SYNC_WHITELIST_MODE + potionId, BooleanSyncValue.class);
        IPanelHandler filterPanel = panelMap.get("protocalFilter" + potionId);
        Potion potion = Potion.potionTypes[potionId];

        Flow row = Flow.row()
            .fullWidth()
            .height(ROW_HEIGHT)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .childPadding(2);
        row.child(createPotionIcon(potionId));
        row.child(
            // Enabled effects are green; disabled ones simply use the GUI theme's normal text colour.
            IKey.dynamic(
                () -> (enabled.getBoolValue() ? EnumChatFormatting.GREEN.toString() : "")
                    + translateToLocal(potion.getName()))
                .asWidget()
                .width(EFFECT_NAME_WIDTH)
                .textAlign(Alignment.CenterLeft));
        row.child(createEnableButton(enabled));
        row.child(createFilterButton(whitelistMode, filterPanel));
        return row;
    }

    /**
     * A button-less row for a registered potion whose id cannot survive the vanilla
     * {@link net.minecraft.potion.PotionEffect}
     * constructor (GTNH's fixPotionLimit truncates ids above 255, which would crash the player tick). It is listed so
     * the window still shows every registered effect, and stays readable - the tooltip explains why it cannot be used.
     */
    private IWidget createUnsupportedEffectRow(int potionId) {
        Potion potion = Potion.potionTypes[potionId];
        Flow row = Flow.row()
            .fullWidth()
            .height(ROW_HEIGHT)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .childPadding(2);
        row.child(createPotionIcon(potionId));
        row.child(
            IKey.dynamic(() -> translateToLocal(potion.getName()))
                .asWidget()
                .width(EFFECT_NAME_WIDTH)
                .textAlign(Alignment.CenterLeft)
                .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("machine.protocalcore.gui.effects.unsupported"))));
        // Keep the same two-button footprint as the interactive rows so the names line up.
        row.child(new IDrawable.DrawableWidget(IDrawable.EMPTY).size(BUTTON_SIZE, BUTTON_SIZE));
        row.child(new IDrawable.DrawableWidget(IDrawable.EMPTY).size(BUTTON_SIZE, BUTTON_SIZE));
        return row;
    }

    /** @return the potion's 18x18 status icon, or a same-sized empty widget when the potion has no status icon. */
    private static IWidget createPotionIcon(int potionId) {
        // hasStatusIcon/getStatusIconIndex are @SideOnly(CLIENT): the server-side panel build must not touch them.
        IDrawable drawable = IDrawable.EMPTY;
        if (NetworkUtils.isClient() && Potion.potionTypes[potionId].hasStatusIcon()) {
            drawable = potionIcon(potionId);
        }
        return new IDrawable.DrawableWidget(drawable).size(ROW_HEIGHT);
    }

    /** Lazily builds the {@code inventory.png} sub texture of one potion status icon. */
    private static UITexture potionIcon(int potionId) {
        UITexture icon = POTION_ICONS[potionId];
        if (icon == null) {
            int index = Potion.potionTypes[potionId].getStatusIconIndex();
            icon = UITexture.builder()
                .location("minecraft", "textures/gui/container/inventory.png")
                .imageSize(256, 256)
                .subAreaXYWH(index % 8 * 18, 198 + index / 8 * 18, 18, 18)
                .build();
            POTION_ICONS[potionId] = icon;
        }
        return icon;
    }

    /**
     * The per-effect black/white list window: the mode toggle in the top center (the same blacklist / whitelist filter
     * icons the space mining module uses), then the blacklist field and the whitelist field one above the other.
     */
    private ModularPanel createFilterPanel(PanelSyncManager syncManager, ModularPanel parent, int potionId,
        IPanelHandler panelHandler) {
        Potion potion = Potion.potionTypes[potionId];
        BooleanSyncValue whitelistMode = syncManager
            .findSyncHandler(SYNC_WHITELIST_MODE + potionId, BooleanSyncValue.class);
        StringSyncValue blacklist = syncManager.findSyncHandler(SYNC_BLACKLIST + potionId, StringSyncValue.class);
        StringSyncValue whitelist = syncManager.findSyncHandler(SYNC_WHITELIST + potionId, StringSyncValue.class);

        ModularPanel panel = new ModularPanel("protocalFilter" + potionId).size(FILTER_PANEL_WIDTH, FILTER_PANEL_HEIGHT)
            .relative(parent)
            .top(ROW_HEIGHT * 2)
            .left(ROW_HEIGHT * 2);

        panel.child(
            Flow.column()
                .full()
                .padding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(createDragBar())
                .child(createFilterModeButton(whitelistMode))
                .child(
                    Flow.row()
                        .fullWidth()
                        .coverChildrenHeight()
                        .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(
                            IKey.dynamic(
                                () -> translateToLocal("machine.protocalcore.gui.filter.title") + ": "
                                    + EnumChatFormatting.AQUA
                                    + translateToLocal(potion.getName()))
                                .asWidget()
                                .marginTop(2))
                        .child(createCloseButton(panelHandler)))
                .child(
                    IKey.lang("machine.protocalcore.gui.filter.blacklist")
                        .asWidget()
                        .fullWidth()
                        .textAlign(Alignment.CenterLeft))
                .child(
                    new TextFieldWidget().size(FILTER_FIELD_WIDTH, 16)
                        .value(blacklist)
                        .autoUpdateOnChange(true))
                .child(
                    IKey.lang("machine.protocalcore.gui.filter.whitelist")
                        .asWidget()
                        .fullWidth()
                        .textAlign(Alignment.CenterLeft)
                        .marginTop(2))
                .child(
                    new TextFieldWidget().size(FILTER_FIELD_WIDTH, 16)
                        .value(whitelist)
                        .autoUpdateOnChange(true))
                .child(
                    IKey.lang("machine.protocalcore.gui.filter.hint")
                        .asWidget()
                        .width(FILTER_FIELD_WIDTH)
                        .textAlign(Alignment.TopLeft)
                        .marginTop(2)));
        return panel;
    }

    /** A thin always-draggable strip at the top of a window (the rest of the window is covered by widgets). */
    private static IWidget createDragBar() {
        return new DragHandle().fullWidth()
            .height(DRAG_BAR_HEIGHT)
            .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("machine.protocalcore.gui.drag")));
    }

    /** The enable / disable toggle of one effect; the overlay follows the synced state. */
    private static IWidget createEnableButton(BooleanSyncValue enabled) {
        return new ButtonWidget<>().size(BUTTON_SIZE, BUTTON_SIZE)
            .overlay(
                new DynamicDrawable(
                    () -> enabled.getBoolValue() ? GTGuiTextures.OVERLAY_BUTTON_CHECKMARK
                        : GTGuiTextures.OVERLAY_BUTTON_CROSS))
            .tooltipBuilder(
                tooltip -> tooltip.addLine(
                    IKey.dynamic(
                        () -> translateToLocal(
                            enabled.getBoolValue() ? "machine.protocalcore.gui.effects.enable"
                                : "machine.protocalcore.gui.effects.disable"))))
            .onMousePressed(mouseButton -> {
                enabled.setBoolValue(!enabled.getBoolValue());
                return true;
            });
    }

    /** The filter button of one effect: shows the current mode and toggles the effect's black/white list window. */
    private static IWidget createFilterButton(BooleanSyncValue whitelistMode, IPanelHandler filterPanel) {
        return new ButtonWidget<>().size(BUTTON_SIZE, BUTTON_SIZE)
            .overlay(new DynamicDrawable(() -> filterModeIcon(whitelistMode.getBoolValue())))
            .tooltipBuilder(
                tooltip -> tooltip
                    .addLine(
                        IKey.dynamic(
                            () -> translateToLocal(
                                whitelistMode.getBoolValue() ? "machine.protocalcore.gui.filter.mode.whitelist"
                                    : "machine.protocalcore.gui.filter.mode.blacklist")))
                    .addLine(IKey.lang("machine.protocalcore.gui.effects.filter")))
            .onMousePressed(mouseButton -> {
                if (filterPanel != null) filterPanel.togglePanel();
                return true;
            });
    }

    /** The black/white list mode toggle of the filter window (top center of the window). */
    private static IWidget createFilterModeButton(BooleanSyncValue whitelistMode) {
        return new ButtonWidget<>().size(BUTTON_SIZE, BUTTON_SIZE)
            .overlay(new DynamicDrawable(() -> filterModeIcon(whitelistMode.getBoolValue())))
            .tooltipBuilder(
                tooltip -> tooltip
                    .addLine(
                        IKey.dynamic(
                            () -> translateToLocal(
                                whitelistMode.getBoolValue() ? "machine.protocalcore.gui.filter.mode.whitelist"
                                    : "machine.protocalcore.gui.filter.mode.blacklist")))
                    .addLine(IKey.lang("machine.protocalcore.gui.filter.toggle")))
            .onMousePressed(mouseButton -> {
                whitelistMode.setBoolValue(!whitelistMode.getBoolValue());
                return true;
            });
    }

    private static IDrawable filterModeIcon(boolean whitelistMode) {
        return whitelistMode ? GTGuiTextures.TT_OVERLAY_BUTTON_WHITELIST : GTGuiTextures.TT_OVERLAY_BUTTON_BLACKLIST;
    }

    private static IWidget createCloseButton(IPanelHandler panelHandler) {
        return new ButtonWidget<>().size(12, 12)
            .overlay(GuiTextures.CROSS_TINY)
            .onMousePressed(mouseButton -> {
                panelHandler.closePanel();
                return true;
            });
    }
    // endregion

    // region Main screen
    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        BooleanSyncValue active = syncManager.findSyncHandler("machineActive", BooleanSyncValue.class);
        IntSyncValue hatchLevel = syncManager.findSyncHandler(SYNC_HATCH_LEVEL, IntSyncValue.class);
        LongSyncValue hatchVoltage = syncManager.findSyncHandler(SYNC_HATCH_VOLTAGE, LongSyncValue.class);
        IntSyncValue enabledCount = syncManager.findSyncHandler(SYNC_ENABLED_COUNT, IntSyncValue.class);
        IntSyncValue plannedRound = syncManager.findSyncHandler(SYNC_PLANNED_ROUND, IntSyncValue.class);
        IntSyncValue roundTotal = syncManager.findSyncHandler(SYNC_ROUND_TOTAL, IntSyncValue.class);
        IntSyncValue roundLeft = syncManager.findSyncHandler(SYNC_ROUND_LEFT, IntSyncValue.class);
        StringSyncValue team = syncManager.findSyncHandler(SYNC_TEAM, StringSyncValue.class);
        StringSyncValue owner = syncManager.findSyncHandler(SYNC_OWNER, StringSyncValue.class);

        ListWidget<IWidget, ?> info = new ListWidget<>().fullWidth()
            .crossAxisAlignment(Alignment.CrossAxis.START);
        info.child(
            IKey.dynamic(
                () -> translateToLocal(
                    active.getBoolValue() ? "machine.protocalcore.gui.status.running"
                        : "machine.protocalcore.gui.status.idle"))
                .asWidget()
                .fullWidth()
                .marginBottom(2));
        info.child(
            IKey.dynamic(
                () -> hatchLevel.getIntValue() <= 0 ? translateToLocal("machine.protocalcore.gui.noHatch")
                    : translateToLocalFormatted(
                        "machine.protocalcore.gui.hatch",
                        hatchLevel.getIntValue(),
                        hatchVoltage.getValue()))
                .asWidget()
                .fullWidth()
                .marginBottom(2));
        info.child(
            IKey.dynamic(
                () -> translateToLocalFormatted("machine.protocalcore.gui.enabled", enabledCount.getIntValue()))
                .asWidget()
                .fullWidth()
                .marginBottom(2));
        info.child(
            IKey.dynamic(() -> translateToLocalFormatted("machine.protocalcore.gui.round", plannedRound.getIntValue()))
                .asWidget()
                .fullWidth()
                .marginBottom(2));
        info.child(
            IKey.dynamic(
                () -> translateToLocalFormatted(
                    "machine.protocalcore.gui.progress",
                    roundLeft.getIntValue(),
                    roundTotal.getIntValue()))
                .asWidget()
                .fullWidth()
                .marginBottom(2));
        info.child(
            IKey.dynamic(
                () -> translateToLocalFormatted(
                    "machine.protocalcore.gui.owner",
                    owner.getStringValue()
                        .isEmpty() ? translateToLocal("machine.protocalcore.gui.unknown") : owner.getStringValue()))
                .asWidget()
                .fullWidth()
                .marginBottom(2));
        info.child(
            IKey.dynamic(
                () -> translateToLocalFormatted(
                    "machine.protocalcore.gui.team",
                    team.getStringValue()
                        .isEmpty() ? translateToLocal("machine.protocalcore.gui.team.none") : team.getStringValue()))
                .asWidget()
                .fullWidth()
                .marginBottom(2));

        // GT's own terminal lines (structure errors, recipe result, ...) come after the protocol status block.
        ListWidget<IWidget, ?> baseTerminal = super.createTerminalTextWidget(syncManager, parent);
        for (IWidget widget : baseTerminal.getChildren()) {
            info.child(widget);
        }
        return info;
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        Flow column = super.createButtonColumn(panel, syncManager);
        column.child(createEffectsButton());
        return column;
    }

    /** The potion bottle button that toggles the effect list window. */
    private IWidget createEffectsButton() {
        IPanelHandler effectsPanel = panelMap.get("protocalEffects");
        return new ButtonWidget<>().size(18, 18)
            .overlay(
                new ItemDrawable(new ItemStack(Items.potionitem)).asIcon()
                    .size(16))
            .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("machine.protocalcore.gui.button.effects")))
            .onMousePressed(mouseButton -> {
                if (effectsPanel != null) effectsPanel.togglePanel();
                return true;
            });
    }
    // endregion
}
