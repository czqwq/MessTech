package com.MessTech.common.gui.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;

import com.MessTech.common.machine.module.SpaceModuleApiary;
import com.MessTech.common.util.MTBeeSimulator;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.utils.serialization.IByteBufDeserializer;
import com.cleanroommc.modularui.utils.serialization.IByteBufSerializer;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.modularui2.widget.SlotLikeButtonWidget;

/**
 * MUI2 GUI of the space apiary modules: the shared space-module GUI plus the bee slots.
 * <p>
 * The bee slots are not a machine inventory - a queen put in one is never consumed (see
 * {@link SpaceModuleApiary#checkProcessing_EM()}) - so they are drawn as
 * {@link SlotLikeButtonWidget}s and edited over clicks, the way GT5's own Mega Industrial Apiary does it. The terminal
 * area switches between the usual machine status text and a scrollable grid of those buttons; the switch is the
 * button this GUI adds to the right of the panel gap.
 * <p>
 * Two details are copied from the Mega Industrial Apiary on purpose:
 * <ul>
 * <li>Queens that share a species, a secondary species and a speed allele collapse into <b>one</b> button with their
 * number on it ({@link MTBeeSimulator#speciesKey}), so a module full of one bee is one button, and the free capacity is
 * the single empty button at the end.</li>
 * <li>An invisible one-item <b>queen buffer</b> slot is registered as the shift-click target. Without it a
 * shift-clicked queen would land in the machine's controller slot, which is the only shift-click target the shared
 * space-module GUI offers (see {@link #createButtonColumn}).</li>
 * </ul>
 * Slot contents are a single synced list (the machine's own array, which GT's tile NBT already persists): the client
 * only ever reads it, and every modification happens on the server in {@link #handleBeeClick(int)}. The clicks
 * themselves travel as one encoded integer - see {@link #sendClick(int, int)} - because the slot buttons carry no
 * inventory of their own.
 */
public class SpaceModuleApiaryGui extends SpaceModuleInfinityGui {

    /** One bee slot button is exactly as big as an item slot, so the grid looks like an inventory. */
    private static final int SLOT_SIZE = 18;

    /** How many buttons fit next to each other before the grid wraps into the next row. */
    private static final int SLOTS_PER_ROW = 10;

    private final SpaceModuleApiary apiary;

    private GenericListSyncHandler<ItemStack> beeSlotsSyncer;
    private IntSyncValue beeClickSyncer;
    private PanelSyncManager syncManager;

    /** Whether the terminal area shows the bee slots instead of the machine status text. Always true on open. */
    private boolean showBeeSlots = true;

    /** The aggregated view of the synced bee slots; rebuilt whenever the sync handler reports a change. */
    private List<BeeEntry> entries;

    public SpaceModuleApiaryGui(SpaceModuleApiary apiary) {
        super(apiary);
        this.apiary = apiary;
    }

    /**
     * One button of the grid: queens of one species and speed, or - with a {@code null} stack - the free capacity that
     * is left. {@code firstSlot} is the real bee slot a click on this button acts on, -1 for the free entry.
     */
    private static final class BeeEntry {

        private final ItemStack stack;
        private final int firstSlot;
        private int count;

        private BeeEntry(ItemStack stack, int firstSlot) {
            this.stack = stack;
            this.firstSlot = firstSlot;
            this.count = 1;
        }

        private BeeEntry(int freeSlots) {
            this.stack = null;
            this.firstSlot = -1;
            this.count = freeSlots;
        }
    }

    /** The apiary has one parallel knob and no second recipe to fill, so the cross-recipe field stays hidden. */
    @Override
    protected boolean shouldShowCrossRecipeParallelField() {
        return false;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        this.syncManager = syncManager;

        // The machine's bee array is the single source of truth for both sides. GT's sync framework polls the getter
        // and pushes it to the client whenever it changes, so showing or editing a queen needs no extra bookkeeping.
        this.beeSlotsSyncer = GenericListSyncHandler.<ItemStack>builder()
            .getterArray(apiary::getBeeSlots)
            .serializer(IByteBufSerializer.wrapNullSafe((buffer, stack) -> buffer.writeItemStackToBuffer(stack)))
            .deserializer(IByteBufDeserializer.wrapNullSafe(buffer -> buffer.readItemStackFromBuffer()))
            .equals(ItemStack::areItemStacksEqual)
            .build();
        // Anything the client sees has to come from this list, so a change invalidates the aggregation.
        this.beeSlotsSyncer.setChangeListener(() -> this.entries = null);
        syncManager.syncValue("apiaryBeeSlots", beeSlotsSyncer);

        // One integer per click, handled on the server: the buttons below have no inventory to transfer through.
        this.beeClickSyncer = new IntSyncValue(() -> 0, this::handleBeeClick).allowC2S();
        syncManager.syncValue("apiaryBeeClick", beeClickSyncer);

        registerQueenBufferSlot(syncManager);
    }

    /**
     * Registers the invisible queen buffer slot that catches shift-clicked queens.
     * <p>
     * Shift-click transfer walks every registered slot group in priority order, and the only storage group the shared
     * space-module GUI registers is the controller slot in its button column - which takes anything, so a queen ends up
     * there instead of in a bee slot. This slot sits in the same priority bracket but only accepts queens and only
     * while a bee slot is free, and it moves what it receives straight into the bee slots. Like the Mega Industrial
     * Apiary's queen slot it needs no widget of its own: MUI2 finds transfer targets through the slot group.
     */
    private void registerQueenBufferSlot(PanelSyncManager syncManager) {
        ItemStackHandler buffer = new ItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        ModularSlot bufferSlot = new ModularSlot(buffer, 0).filter(this::canBufferQueen)
            .singletonSlotGroup(SlotGroup.STORAGE_SLOT_PRIO)
            .changeListener((newItem, onlyAmountChanged, client, init) -> {
                if (client || init || newItem == null) return;
                if (insertQueen(newItem)) buffer.setStackInSlot(0, null);
            });
        syncManager.syncValue("apiaryQueenBuffer", new ItemSlotSH(bufferSlot));
    }

    private boolean canBufferQueen(ItemStack stack) {
        return MTBeeSimulator.isQueen(stack) && hasEmptyBeeSlot();
    }

    @Override
    protected ParentWidget<?> createTerminalParentWidget(ModularPanel panel, PanelSyncManager syncManager) {
        return new ParentWidget<>().size(getTerminalWidgetWidth(), getTerminalWidgetHeight())
            .paddingTop(4)
            .paddingBottom(4)
            .paddingLeft(4)
            .paddingRight(0)
            .widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL)
            .child(
                createTerminalTextWidget(syncManager, panel)
                    .size(getTerminalWidgetWidth() - 4, getTerminalWidgetHeight() - 8)
                    .collapseDisabledChild()
                    .setEnabledIf(w -> !showBeeSlots))
            .child(
                Flow.column()
                    .size(getTerminalWidgetWidth() - 4, getTerminalWidgetHeight() - 8)
                    .setEnabledIf(w -> showBeeSlots)
                    .child(createBeeSlotGrid()))
            .childIf(
                multiblock.supportsTerminalRightCornerColumn(),
                () -> createTerminalRightCornerColumn(panel, syncManager));
    }

    /**
     * The scrollable bee slot grid.
     * <p>
     * One button per possible entry is created once - at most one per bee slot plus the free entry - and each button
     * enables itself while its entry exists. A disabled button collapses in its row (and a row without any enabled
     * button collapses in the list), so the grid grows and shrinks with the bee population without being rebuilt; the
     * buttons read their content from the synced list while they are drawn.
     */
    private IWidget createBeeSlotGrid() {
        int buttons = beeSlotCapacity() + 1;
        ListWidget<IWidget, ?> grid = new ListWidget<>().crossAxisAlignment(Alignment.CrossAxis.START)
            .widthRel(1f)
            .heightRel(1f);

        for (int rowStart = 0; rowStart < buttons; rowStart += SLOTS_PER_ROW) {
            Flow row = Flow.row()
                .coverChildren()
                .collapseDisabledChild();
            for (int i = rowStart; i < Math.min(rowStart + SLOTS_PER_ROW, buttons); i++) {
                row.child(createBeeSlot(i));
            }
            grid.child(row);
        }
        return grid;
    }

    private IWidget createBeeSlot(int index) {
        SlotLikeButtonWidget slot = new SlotLikeButtonWidget(() -> entryStack(index)) {

            @Override
            public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetThemeEntry) {
                super.draw(context, widgetThemeEntry);
                // The Mega Industrial Apiary puts the number of identical bees on the button; the free entry shows how
                // much room is left instead.
                int count = entryCount(index);
                if (count > 1 || isFreeEntry(index)) {
                    GuiDraw.drawStandardSlotAmountText(count, null, getArea());
                }
            }
        };
        slot.size(SLOT_SIZE, SLOT_SIZE);
        slot.setEnabledIf(w -> index < entries().size());
        slot.onMousePressed(button -> {
            sendClick(entrySlot(index), button);
            return true;
        });
        slot.tooltipBuilder(tooltip -> {
            tooltip.setAutoUpdate(true);
            buildBeeTooltip(tooltip, index);
        });
        return slot;
    }

    /** @return the number of bee slots the machine has; the same on both sides, it is fixed by the module tier. */
    private int beeSlotCapacity() {
        return apiary.getBeeSlots().length;
    }

    /**
     * The bee slots as the Mega Industrial Apiary shows them: queens of one species and speed become one entry with
     * their number on it, and the free capacity becomes the single empty entry at the end. Rebuilt only when the synced
     * slots change.
     */
    private List<BeeEntry> entries() {
        if (this.entries != null) return this.entries;

        List<BeeEntry> built = new ArrayList<>();
        Map<String, BeeEntry> bySpecies = new LinkedHashMap<>();
        List<ItemStack> snapshot = beeSlotSnapshot();
        int occupied = 0;
        for (int i = 0; i < snapshot.size(); i++) {
            ItemStack queen = snapshot.get(i);
            if (!MTBeeSimulator.isQueen(queen)) continue;
            occupied++;
            String key = MTBeeSimulator.speciesKey(queen);
            if (key == null) continue;
            BeeEntry entry = bySpecies.get(key);
            if (entry == null) {
                // The click on this entry acts on the bee slot it was first seen in.
                bySpecies.put(key, new BeeEntry(displayStack(queen), i));
            } else {
                entry.count++;
            }
        }
        built.addAll(bySpecies.values());
        int free = Math.max(0, beeSlotCapacity() - occupied);
        if (free > 0) built.add(new BeeEntry(free));

        this.entries = built;
        return built;
    }

    /** @return the synced bee slots on the client, the machine's own array on the server. */
    private List<ItemStack> beeSlotSnapshot() {
        if (beeSlotsSyncer != null) return beeSlotsSyncer.getValue();
        ItemStack[] slots = apiary.getBeeSlots();
        List<ItemStack> list = new ArrayList<>(slots.length);
        Collections.addAll(list, slots);
        return list;
    }

    private ItemStack entryStack(int index) {
        List<BeeEntry> entries = entries();
        return index < entries.size() ? entries.get(index).stack : null;
    }

    private int entryCount(int index) {
        List<BeeEntry> entries = entries();
        return index < entries.size() ? entries.get(index).count : 0;
    }

    private boolean isFreeEntry(int index) {
        List<BeeEntry> entries = entries();
        return index < entries.size() && entries.get(index).stack == null;
    }

    private int entrySlot(int index) {
        List<BeeEntry> entries = entries();
        return index < entries.size() ? entries.get(index).firstSlot : 0;
    }

    /**
     * Strips a queen down to what identifies her in the grid, the way the Mega Industrial Apiary does: chromosome 0
     * (species) and chromosome 1 (speed) plus the analysed flag. Without this every bee would carry her whole genome on
     * the button and two identical-looking bees would still differ.
     */
    private static ItemStack displayStack(ItemStack original) {
        ItemStack display = new ItemStack(original.getItem(), 1, original.getItemDamage());
        if (original.getTagCompound() == null) return display;

        NBTTagCompound originalTag = original.getTagCompound();
        NBTTagCompound newTag = new NBTTagCompound();

        NBTTagCompound genomeTag = originalTag.getCompoundTag("Genome");
        if (!genomeTag.hasNoTags()) {
            NBTTagList oldChromosomes = genomeTag.getTagList("Chromosomes", 10);
            if (oldChromosomes.tagCount() > 1) {
                NBTTagList newChromosomes = new NBTTagList();
                newChromosomes.appendTag(
                    oldChromosomes.getCompoundTagAt(0)
                        .copy());
                newChromosomes.appendTag(
                    oldChromosomes.getCompoundTagAt(1)
                        .copy());
                NBTTagCompound newGenome = new NBTTagCompound();
                newGenome.setTag("Chromosomes", newChromosomes);
                newTag.setTag("Genome", newGenome);
            }
        }
        if (originalTag.hasKey("IsAnalyzed")) {
            newTag.setBoolean("IsAnalyzed", originalTag.getBoolean("IsAnalyzed"));
        }
        display.setTagCompound(newTag);
        return display;
    }

    private void buildBeeTooltip(RichTooltip tooltip, int index) {
        List<BeeEntry> entries = entries();
        if (index >= entries.size()) return;

        BeeEntry entry = entries.get(index);
        if (entry.stack == null) {
            tooltip.addLine(StatCollector.translateToLocal("machine.spacemoduleapiary.gui.empty"));
            if (entry.count > 1) {
                tooltip.addLine(
                    StatCollector
                        .translateToLocalFormatted("machine.spacemoduleapiary.gui.free", String.valueOf(entry.count)));
            }
            tooltip.addLine(StatCollector.translateToLocal("machine.spacemoduleapiary.gui.empty_hint"));
            return;
        }
        tooltip.addLine(entry.stack.getDisplayName());
        if (entry.count > 1) {
            tooltip.addLine(
                StatCollector
                    .translateToLocalFormatted("machine.spacemoduleapiary.gui.identical", String.valueOf(entry.count)));
        }
        tooltip.addLine(StatCollector.translateToLocal("machine.spacemoduleapiary.gui.extract_hint"));
        tooltip.addLine(StatCollector.translateToLocal("machine.spacemoduleapiary.gui.shift_hint"));
    }

    /**
     * Packs a click into the single synced integer: the bee slot it acts on (-1 for the free entry, which the server
     * resolves to its first free slot), the mouse button and whether shift was held. Both are offset by two so that no
     * real click can ever be the value 0, which is what the framework resets the handler to between clicks.
     */
    private void sendClick(int slot, int button) {
        if (beeClickSyncer == null) return;
        int encoded = ((slot + 2) << 4) | ((button & 0x3) << 1) | (Interactable.hasShiftDown() ? 1 : 0);
        beeClickSyncer.setIntValue(encoded, true, true);
    }

    /**
     * Runs on the server whenever a client clicked a bee slot; on the client this is the local echo and does nothing.
     */
    private void handleBeeClick(int encoded) {
        if (encoded == 0 || syncManager == null || syncManager.isClient()) return;

        EntityPlayer player = syncManager.getPlayer();
        if (!(player instanceof EntityPlayerMP playerMP)) return;

        ItemStack[] slots = apiary.getBeeSlots();
        int selected = (encoded >>> 4) - 2;
        int index = selected < 0 ? firstFreeBeeSlot(slots) : selected;
        if (index < 0 || index >= slots.length) return;
        int button = (encoded >>> 1) & 0x3;
        boolean shift = (encoded & 1) != 0;

        ItemStack cursor = player.inventory.getItemStack();
        if (slots[index] == null) {
            insertBee(player, playerMP, slots, index, cursor, button);
        } else {
            takeBee(player, playerMP, slots, index, cursor, button, shift);
        }
    }

    /**
     * Puts a queen from the cursor into the clicked slot and the ones after it: one per right click, the stack
     * otherwise.
     */
    private void insertBee(EntityPlayer player, EntityPlayerMP playerMP, ItemStack[] slots, int index, ItemStack cursor,
        int button) {
        if (cursor == null || !MTBeeSimulator.isQueen(cursor)) return;

        int remaining = button == 1 ? 1 : cursor.stackSize;
        int inserted = 0;
        for (int i = index; i < slots.length && remaining > 0; i++) {
            if (slots[i] != null) continue;
            ItemStack bee = cursor.copy();
            bee.stackSize = 1;
            slots[i] = bee;
            remaining--;
            inserted++;
        }
        if (inserted == 0) return;

        cursor.stackSize -= inserted;
        if (cursor.stackSize <= 0) player.inventory.setItemStack(null);
        playerMP.isChangingQuantityOnly = cursor.stackSize > 0;
        playerMP.updateHeldItem();
    }

    /** Takes the queen out of the clicked slot: shift sends it to the inventory, otherwise it goes to the cursor. */
    private void takeBee(EntityPlayer player, EntityPlayerMP playerMP, ItemStack[] slots, int index, ItemStack cursor,
        int button, boolean shift) {
        ItemStack bee = slots[index];
        if (shift) {
            slots[index] = null;
            if (!player.inventory.addItemStackToInventory(bee)) player.entityDropItem(bee, 0.0F);
            player.inventoryContainer.detectAndSendChanges();
            return;
        }
        if (button == 2) {
            // Middle click: creative-only pick, the same shortcut the Mega Industrial Apiary offers.
            if (!player.capabilities.isCreativeMode) return;
            ItemStack picked = bee.copy();
            picked.stackSize = picked.getMaxStackSize();
            player.inventory.setItemStack(picked);
            playerMP.isChangingQuantityOnly = false;
            playerMP.updateHeldItem();
            return;
        }
        if (cursor == null) {
            slots[index] = null;
            player.inventory.setItemStack(bee);
            playerMP.isChangingQuantityOnly = false;
            playerMP.updateHeldItem();
            return;
        }
        // Swapping is only well defined for a single queen: a bee slot holds exactly one.
        if (cursor.stackSize != 1 || !MTBeeSimulator.isQueen(cursor)) return;
        slots[index] = cursor.copy();
        player.inventory.setItemStack(bee);
        playerMP.isChangingQuantityOnly = false;
        playerMP.updateHeldItem();
    }

    /** Puts one queen into the first free bee slot; @return whether there was room for her. */
    private boolean insertQueen(ItemStack queen) {
        ItemStack[] slots = apiary.getBeeSlots();
        int index = firstFreeBeeSlot(slots);
        if (index < 0) return false;
        ItemStack bee = queen.copy();
        bee.stackSize = 1;
        slots[index] = bee;
        return true;
    }

    private boolean hasEmptyBeeSlot() {
        return firstFreeBeeSlot(apiary.getBeeSlots()) >= 0;
    }

    private static int firstFreeBeeSlot(ItemStack[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) return i;
        }
        return -1;
    }

    /**
     * The apiary has no controller slot to offer: the shared button column would register one, it would take any item
     * a shift-click sends its way (queens included) and nothing in this machine ever reads it. Dropping it leaves the
     * queen buffer as the only shift-click target, exactly like the Mega Industrial Apiary, and matches the other
     * space modules that have nothing to put in a controller slot.
     */
    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.column()
            .width(18)
            .leftRel(1, -2, 1)
            .mainAxisAlignment(Alignment.MainAxis.END)
            .reverseLayout(true)
            .child(createPowerSwitchButton())
            .child(createStructureUpdateButton(syncManager));
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        return super.createRightPanelGapRow(parent, syncManager).child(createBeeSlotToggleButton());
    }

    private IWidget createBeeSlotToggleButton() {
        return new ButtonWidget<>().size(SLOT_SIZE, SLOT_SIZE)
            .overlay(
                new DynamicDrawable(
                    () -> showBeeSlots ? GTGuiTextures.OVERLAY_BUTTON_WHITELIST
                        : GTGuiTextures.TT_OVERLAY_BUTTON_STATISTICS))
            .onMousePressed(button -> {
                showBeeSlots = !showBeeSlots;
                return true;
            })
            .tooltipBuilder(
                t -> t.addLine(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            showBeeSlots ? "machine.spacemoduleapiary.gui.show_status"
                                : "machine.spacemoduleapiary.gui.show_slots"))));
    }
}
