package com.MessTech.common.machine.hatch;

import static net.minecraft.util.StatCollector.translateToLocal;
import static net.minecraft.util.StatCollector.translateToLocalFormatted;

import java.util.Arrays;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.MTGuiTextures;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;
import gregtech.common.modularui2.widget.builder.ItemSlotGridBuilder;

/**
 * MUI2 GUI for {@link MTReactorAccessHatch}.
 * <p>
 * Shows exactly one 9x6 reactor page at a time. The page switching buttons sit in the bottom right corner:
 * "previous page" is hidden on the first page and "next page" is hidden on the last page.
 * <p>
 * Slot memory:
 * <ul>
 * <li>Shift + right click on a filled, unlocked slot locks every slot of this hatch holding the same item type
 * (all pages, NBT ignored) and keeps the threshold of an already locked group of that type.</li>
 * <li>Shift + right click on a locked slot unlocks only that single slot.</li>
 * <li>Alt + Shift + right click on a locked slot unlocks every locked slot of the same item type.</li>
 * <li>Middle click on a locked slot opens the auto-output panel with a percentage text field.</li>
 * </ul>
 */
public class MTReactorAccessHatchGui extends MTEHatchBaseGui<MTReactorAccessHatch> {

    private static final int SLOT_SIZE = 18;
    private static final int GRID_WIDTH = MTReactorAccessHatch.PAGE_WIDTH * SLOT_SIZE;
    private static final int GRID_HEIGHT = MTReactorAccessHatch.PAGE_HEIGHT * SLOT_SIZE;
    private static final int EXTRA_HEIGHT = 80;
    /** Faded wash drawn over the ghost icon of a locked but empty slot. */
    private static final int GHOST_WASH_COLOR = 0x66FFFFFF;

    /** A half-size lock icon drawn into the top right corner of the 18x18 slot. */
    private static final IDrawable SMALL_LOCK_ICON = new IDrawable() {

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            int size = Math.max(1, Math.min(width, height) / 2);
            // This GT5U line still calls the lock overlay OVERLAY_BUTTON_LOCK (it is OVERLAY_BUTTON_LOCKED later).
            GTGuiTextures.OVERLAY_BUTTON_LOCK.draw(context, x + width - size, y, size, size, widgetTheme);
        }
    };

    /** Currently opened slot of the shared slot panel; synced so both sides agree. */
    private int selectedSlot = 0;
    private GenericSyncValue<byte[], ?> slotStates;
    private GenericSyncValue<ItemStack[], ?> slotMemories;
    private MTReactorSlotActionSyncHandler slotAction;
    private IntSyncValue selectedSlotSync;
    private StringSyncValue thresholdEdit;
    private IPanelHandler slotPanel;

    public MTReactorAccessHatchGui(MTReactorAccessHatch hatch) {
        super(hatch);
    }

    @Override
    protected int getBasePanelHeight() {
        return super.getBasePanelHeight() + EXTRA_HEIGHT;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);

        slotStates = new GenericSyncValue<>(
            byte[].class,
            machine::getSlotStateArray,
            null,
            ByteBufAdapters.BYTE_ARR,
            ByteBufAdapters.BYTE_ARR,
            Arrays::equals,
            byte[]::clone);
        syncManager.syncValue("mtReactorSlotStates", slotStates);

        slotMemories = new GenericSyncValue<>(
            ItemStack[].class,
            machine::getSlotMemoryArray,
            null,
            MTReactorMemorySyncAdapter.INSTANCE,
            MTReactorMemorySyncAdapter.INSTANCE,
            MTReactorMemorySyncAdapter.INSTANCE,
            MTReactorAccessHatchGui::copyMemoryArray);
        syncManager.syncValue("mtReactorSlotMemories", slotMemories);

        slotAction = new MTReactorSlotActionSyncHandler(machine);
        syncManager.syncValue("mtReactorSlotAction", slotAction);

        selectedSlotSync = new IntSyncValue(() -> selectedSlot, value -> selectedSlot = value).allowC2S();
        syncManager.syncValue("mtReactorSelectedSlot", selectedSlotSync);

        thresholdEdit = new StringSyncValue(() -> String.valueOf(Math.max(0, getThresholdForEdit())), value -> {
            if (baseMetaTileEntity == null || baseMetaTileEntity.isClientSide()) return;
            int percent = parsePercent(value);
            if (percent < 0 || percent == machine.getSlotThreshold(selectedSlot)) return;
            machine.setSlotThreshold(selectedSlot, percent);
        }).allowC2S();
        syncManager.syncValue("mtReactorThresholdEdit", thresholdEdit);
    }

    private static ItemStack[] copyMemoryArray(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].copy();
        }
        return copy;
    }

    /** Reads the selected slot's threshold (server: real hatch state, client: synced byte array). */
    private int getThresholdForEdit() {
        if (baseMetaTileEntity != null && !baseMetaTileEntity.isClientSide()) {
            return machine.getSlotThreshold(selectedSlot);
        }
        byte[] states = slotStates == null ? null : slotStates.getValue();
        if (states == null || selectedSlot < 0 || selectedSlot >= states.length) return -1;
        return states[selectedSlot];
    }

    private static int parsePercent(String value) {
        if (value == null) return -1;
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        final int pageCount = machine.getPageCount();
        final PagedWidget.Controller controller = new PagedWidget.Controller();
        final PagedWidget paged = new PagedWidget();

        slotPanel = syncManager.syncedPanel("mtReactorSlotPanel", true, (manager, sync) -> createSlotPanel(panel));

        for (int page = 0; page < pageCount; page++) {
            paged.addPage(
                new ItemSlotGridBuilder(machine.inventoryHandler, syncManager)
                    .size(MTReactorAccessHatch.PAGE_WIDTH, MTReactorAccessHatch.PAGE_HEIGHT)
                    .slotGroupKey("mt_reactor_page_" + page)
                    .indexOffset(page * MTReactorAccessHatch.SLOTS_PER_PAGE)
                    .filter(MTReactorAccessHatch::isUsefulReactorItem)
                    .itemSlotSupplier(MTReactorSlotWidget::new)
                    .modularSlotSupplier((handler, index) -> new MTReactorModularSlot(handler, index))
                    .build());
        }
        paged.controller(controller);
        paged.size(GRID_WIDTH, GRID_HEIGHT);

        ButtonWidget<?> previous = new ButtonWidget<>().size(SLOT_SIZE, SLOT_SIZE)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_SIDE_SELECTION_LEFT)
            .tooltip(t -> t.addLine(translateToLocal("machine.mtreactor.gui.previous")))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0 && controller.isInitialised() && controller.getActivePageIndex() > 0) {
                    controller.previousPage();
                }
                return true;
            })
            .setEnabledIf(w -> controller.isInitialised() && controller.getActivePageIndex() > 0);

        ButtonWidget<?> next = new ButtonWidget<>().size(SLOT_SIZE, SLOT_SIZE)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_SIDE_SELECTION_RIGHT)
            .tooltip(t -> t.addLine(translateToLocal("machine.mtreactor.gui.next")))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0 && controller.isInitialised() && controller.getActivePageIndex() < pageCount - 1) {
                    controller.nextPage();
                }
                return true;
            })
            .setEnabledIf(w -> controller.isInitialised() && controller.getActivePageIndex() < pageCount - 1);

        Flow buttonRow = Flow.row()
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(2)
            .mainAxisAlignment(Alignment.MainAxis.END)
            .child(previous)
            .child(next);

        return super.createContentSection(panel, syncManager).child(
            Flow.column()
                .full()
                .childPadding(2)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(
                    IKey.dynamic(() -> pageText(controller, pageCount))
                        .asWidget()
                        .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE))
                .child(paged)
                .child(buttonRow));
    }

    private static String pageText(PagedWidget.Controller controller, int pageCount) {
        int current = controller.isInitialised() ? controller.getActivePageIndex() + 1 : 1;
        return translateToLocalFormatted("machine.mtreactor.gui.page", current, pageCount);
    }

    /**
     * Server side already enforces the lock through {@code MTEItemStackHandler#isItemValid}; this client side check
     * additionally uses the synced lock/memory arrays, so a locked slot does not even accept a wrong item visually.
     */
    private class MTReactorModularSlot extends ModularSlot {

        MTReactorModularSlot(IItemHandler itemHandler, int index) {
            super(itemHandler, index);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            if (!super.isItemValid(stack)) return false;
            int slot = getSlotIndex();
            byte[] states = slotStates == null ? null : slotStates.getValue();
            ItemStack[] memories = slotMemories == null ? null : slotMemories.getValue();
            if (states == null || memories == null || slot < 0 || slot >= states.length || slot >= memories.length) {
                return true;
            }
            if (states[slot] < 0) return true;
            ItemStack memory = memories[slot];
            return memory == null || MTReactorAccessHatch.isSameItemType(stack, memory);
        }
    }

    /** Slot widget adding the lock overlay, the ghost memory icon and the custom interactions. */
    private class MTReactorSlotWidget extends ItemSlot {

        MTReactorSlotWidget() {
            overlay(
                new DynamicDrawable(() -> isLockedSlot(getSlot().getSlotIndex()) ? SMALL_LOCK_ICON : IDrawable.EMPTY));
            tooltipDynamic(t -> appendHint(t, getSlot().getSlotIndex()));
        }

        @Override
        public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
            super.buildTooltip(stack, tooltip);
            appendHint(tooltip, getSlot().getSlotIndex());
        }

        @Override
        protected void drawOverlay() {
            super.drawOverlay();
            if (getSlot().getStack() != null) return;
            int slot = getSlot().getSlotIndex();
            if (!isLockedSlot(slot)) return;
            ItemStack[] memories = slotMemories == null ? null : slotMemories.getValue();
            if (memories == null || slot < 0 || slot >= memories.length || memories[slot] == null) return;
            GuiDraw.drawItem(memories[slot], 1, 1, 16.0F, 16.0F, 0);
            GuiDraw.drawRect(1, 1, 16, 16, GHOST_WASH_COLOR);
        }

        @Override
        public @NotNull Result onMousePressed(int mouseButton) {
            int slot = getSlot().getSlotIndex();
            if (mouseButton == 1 && Interactable.hasShiftDown()) {
                if (isLockedSlot(slot)) {
                    int action = Interactable.hasAltDown() ? MTReactorSlotActionSyncHandler.ACTION_UNLOCK_GROUP
                        : MTReactorSlotActionSyncHandler.ACTION_UNLOCK_SLOT;
                    slotAction.syncToServer(action, buf -> buf.writeInt(slot));
                } else if (getSlot().getStack() != null) {
                    slotAction
                        .syncToServer(MTReactorSlotActionSyncHandler.ACTION_LOCK_GROUP, buf -> buf.writeInt(slot));
                }
                return Result.SUCCESS;
            }
            if (mouseButton == 2) {
                if (isLockedSlot(slot)) {
                    selectedSlotSync.setValue(slot);
                    if (slotPanel != null && !slotPanel.isPanelOpen()) {
                        slotPanel.openPanel();
                    }
                }
                return Result.SUCCESS;
            }
            return super.onMousePressed(mouseButton);
        }

        private boolean isLockedSlot(int slot) {
            byte[] states = slotStates == null ? null : slotStates.getValue();
            return states != null && slot >= 0 && slot < states.length && states[slot] >= 0;
        }

        private void appendHint(RichTooltip tooltip, int slot) {
            tooltip.addLine(translateToLocal("machine.mtreactor.slot.hint"));
            byte[] states = slotStates == null ? null : slotStates.getValue();
            if (states != null && slot >= 0 && slot < states.length && states[slot] >= 0) {
                tooltip.addLine(translateToLocalFormatted("machine.mtreactor.slot.threshold.short", states[slot]));
            }
        }
    }

    private ModularPanel createSlotPanel(ModularPanel parent) {
        ModularPanel panel = new ModularPanel("mtReactorSlotPanel").size(170, 96)
            .relative(parent)
            .leftRel(1)
            .topRel(0.55f);

        Flow column = Flow.column()
            .full()
            .padding(6)
            .childPadding(3);

        column.child(
            IKey.dynamic(() -> translateToLocalFormatted("machine.mtreactor.slot.panel.title", selectedSlot + 1))
                .asWidget()
                .widgetTheme(GTWidgetThemes.DISPLAY_TEXT_WHITE)
                .fullWidth());

        column.child(IKey.dynamic(() -> {
            ItemStack stack = machine.getSlotStack(selectedSlot);
            return stack == null ? translateToLocal("machine.mtreactor.slot.empty") : stack.getDisplayName();
        })
            .asWidget()
            .fullWidth());

        column.child(
            IKey.dynamic(
                () -> translateToLocalFormatted(
                    "machine.mtreactor.slot.fuel_durability",
                    MTReactorAccessHatch.getDurabilityPercent(machine.getSlotStack(selectedSlot))))
                .asWidget()
                .fullWidth()
                .setEnabledIf(w -> MTReactorAccessHatch.isFuelRod(machine.getSlotStack(selectedSlot))));

        column.child(
            IKey.dynamic(
                () -> translateToLocalFormatted(
                    "machine.mtreactor.slot.coolant_heat",
                    MTReactorAccessHatch.getCurrentHeat(machine.getSlotStack(selectedSlot)),
                    MTReactorAccessHatch.getMaxHeat(machine.getSlotStack(selectedSlot)),
                    MTReactorAccessHatch.getDurabilityPercent(machine.getSlotStack(selectedSlot))))
                .asWidget()
                .fullWidth()
                .setEnabledIf(w -> MTReactorAccessHatch.isCoolingCell(machine.getSlotStack(selectedSlot))));

        column.child(
            Flow.row()
                .coverChildren()
                .childPadding(4)
                .child(
                    IKey.lang("machine.mtreactor.slot.threshold")
                        .asWidget()
                        .verticalCenter())
                .child(
                    new TextFieldWidget().formatAsInteger(true)
                        .numbersInt(0, 100)
                        .setTextAlignment(Alignment.CENTER)
                        .value(thresholdEdit)
                        .size(46, 16))
                .child(
                    IKey.str("%")
                        .asWidget()
                        .verticalCenter()));

        panel.child(column);
        return panel;
    }

    @Override
    protected boolean supportsFluidScreen() {
        return false;
    }

    /** MessTech logo instead of the default GregTech logo. */
    @Override
    protected UITexture getLogoTexture() {
        return MTGuiTextures.PICTURE_MT_LOGO;
    }
}
