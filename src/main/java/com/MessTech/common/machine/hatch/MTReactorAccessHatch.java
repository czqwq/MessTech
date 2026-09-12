package com.MessTech.common.machine.hatch;

import java.util.Arrays;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.common.items.ItemDepletedCell;
import ic2.api.reactor.IReactorComponent;
import ic2.core.Ic2Items;
import ic2.core.item.ItemGradualInt;
import ic2.core.item.reactor.ItemReactorHeatStorage;
import ic2.core.item.reactor.ItemReactorUranium;

/**
 * Tiered reactor access hatch holding the IC2 reactor component inventory for {@code MTReactor}.
 * <p>
 * The inventory is split into independent pages of 9x6 slots (exactly one IC2 reactor grid each). The tier
 * determines the page count: Tier EV has 1 page, every tier above adds one more, up to 8 pages at Tier UIV.
 * <p>
 * The base texture is always the machine casing of the hatch's own tier; the reactor access decal from
 * {@code assets/messtech/textures/blocks/hatch/Hatch_Reactor_Access.png} is layered on the front face. Because
 * of that, {@code updateTexture} is intentionally never called for this hatch, so multiblock casing updating
 * cannot overwrite the tier base.
 */
public class MTReactorAccessHatch extends MTEHatch {

    public static final int PAGE_WIDTH = 9;
    public static final int PAGE_HEIGHT = 6;
    public static final int SLOTS_PER_PAGE = PAGE_WIDTH * PAGE_HEIGHT;

    private static final String OVERLAY_DOMAIN = "messtech";
    /** Icon path relative to {@code textures/blocks}, matching {@code Textures.BlockIcons.custom}. */
    private static final String OVERLAY_PATH = "hatch/Hatch_Reactor_Access";

    /** Highest tier supported by this hatch (UIV). */
    public static final int MAX_TIER = 11;
    /** Lowest tier supported by this hatch (EV). */
    public static final int MIN_TIER = 4;

    private static IIconContainer sReactorOverlay;

    private final int pageCount;

    /** Per-slot lock state: -1 = unlocked, 0..100 = locked auto-output threshold in percent. */
    private final byte[] mSlotState;
    /** Remembered item type of every locked slot (matching ignores NBT on purpose). */
    private final ItemStack[] mSlotMemory;

    public MTReactorAccessHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            getPageCountForTier(aTier) * SLOTS_PER_PAGE,
            new String[] { StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.0") });
        this.pageCount = getPageCountForTier(aTier);
        this.mSlotState = new byte[mInventory.length];
        Arrays.fill(this.mSlotState, (byte) -1);
        this.mSlotMemory = new ItemStack[mInventory.length];
    }

    public MTReactorAccessHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, getPageCountForTier(aTier) * SLOTS_PER_PAGE, aDescription, aTextures);
        this.pageCount = getPageCountForTier(aTier);
        this.mSlotState = new byte[mInventory.length];
        Arrays.fill(this.mSlotState, (byte) -1);
        this.mSlotMemory = new ItemStack[mInventory.length];
    }

    /**
     * Pages per tier: EV = 1, IV = 2, ... UIV = 8.
     */
    public static int getPageCountForTier(int aTier) {
        return Math.max(1, Math.min(aTier - MIN_TIER + 1, MAX_TIER - MIN_TIER + 1));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTReactorAccessHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTReactorAccessHatchGui(this).build(data, syncManager, uiSettings);
    }

    /** MTEHatch defaults to MUI1; this hatch needs the custom MUI2 page GUI. */
    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        openGui(aPlayer);
        return true;
    }

    /**
     * Only horizontal facings are allowed. The base tile entity defaults to {@link ForgeDirection#DOWN}, and
     * {@code setFrontFacing} refuses to change the facing if it is invalid, which previously left the front decal
     * pointing down. Restricting to horizontal sides also prevents the decal from ever ending up on the top or
     * bottom face, so it is always visible from the side.
     */
    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing.offsetY == 0;
    }

    /**
     * Placement can still fail to pick a horizontal facing (for example when a player places the hatch while
     * looking straight down, or when structure auto-placement only offers a vertical side). In that case the base
     * tile entity keeps its default {@code DOWN} facing, so force a horizontal fallback once on the first tick.
     */
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()
            && aBaseMetaTileEntity.getFrontFacing().offsetY != 0) {
            aBaseMetaTileEntity.setFrontFacing(ForgeDirection.NORTH);
        }
    }

    @Override
    public String[] getDescription() {
        return new String[] { EnumChatFormatting.AQUA + StatCollector
            .translateToLocalFormatted("machine.mtreactor.accesshatch.desc.0", pageCount, pageCount * SLOTS_PER_PAGE),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.2"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("machine.mtreactor.accesshatch.desc.3") };
    }

    // region Inventory layout

    public int getPageCount() {
        return pageCount;
    }

    /**
     * Reads a slot from a 9x6 page. Components on one page can never see items of another page, because the
     * coordinates are always clamped to that single page.
     */
    public ItemStack getReactorItem(int page, int x, int y) {
        if (!isInPage(page, x, y)) return null;
        return mInventory[toIndex(page, x, y)];
    }

    public void setReactorItem(int page, int x, int y, ItemStack stack) {
        if (!isInPage(page, x, y)) return;
        int index = toIndex(page, x, y);
        mInventory[index] = stack;
        onContentsChanged(index);
    }

    public boolean isPageEmpty(int page) {
        if (page < 0 || page >= pageCount) return true;
        int start = page * SLOTS_PER_PAGE;
        for (int i = start; i < start + SLOTS_PER_PAGE; i++) {
            if (mInventory[i] != null) return false;
        }
        return true;
    }

    public void clearAllPages() {
        Arrays.fill(mInventory, null);
        markDirty();
    }

    private boolean isInPage(int page, int x, int y) {
        return page >= 0 && page < pageCount && x >= 0 && x < PAGE_WIDTH && y >= 0 && y < PAGE_HEIGHT;
    }

    private static int toIndex(int page, int x, int y) {
        return page * SLOTS_PER_PAGE + y * PAGE_WIDTH + x;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** Also cap the legacy IInventory path used by GT automation, not just the MUI2 item handler. */
    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    /**
     * Every reactor slot is a real inventory slot. {@code MTEBasicTank} treats slot 2 as a fluid display slot,
     * which would hide the third reactor slot from pipes/OpenComputers, so it is re-enabled here.
     */
    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex >= 0 && aIndex < mInventory.length;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        if (!isUsefulReactorItem(itemStack)) return false;
        // A locked slot only accepts items matching its remembered type (durability/heat ignored).
        if (isSlotLocked(index)) {
            ItemStack memory = getSlotMemory(index);
            if (memory != null && !isSameItemType(itemStack, memory)) return false;
        }
        return true;
    }

    /**
     * IC2 style: insertion is validated per slot, extraction is allowed from every slot, so logistics mods
     * (GT pipes, AE, OpenComputers, ...) can address individual reactor slots.
     */
    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return isValidSlot(aIndex) && isItemValidForSlot(aIndex, aStack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return isValidSlot(aIndex);
    }

    /**
     * Direct IInventory writes (for example OpenComputers' {@code inventory.setItem}) must not bypass the slot
     * validation/lock rules. Internal reactor writes use {@link #setSlotStack(int, ItemStack)} instead.
     */
    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aStack != null && !isItemValidForSlot(aIndex, aStack)) return;
        super.setInventorySlotContents(aIndex, aStack);
    }

    /** Readable name for logistics/OpenComputers instead of the raw localization key. */
    @Override
    public String getInventoryName() {
        return getLocalName();
    }

    /**
     * Mirrors IC2's {@code isUsefulItem}: reactor components plus the inert items IC2 also lets you keep in the
     * reactor inventory (depleted fuel cells and tritium cells).
     */
    public static boolean isUsefulReactorItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (stack.getItem() instanceof IReactorComponent) return true;
        Item item = stack.getItem();
        return matchesIc2Item(item, Ic2Items.TritiumCell) || matchesIc2Item(item, Ic2Items.reactorDepletedUraniumSimple)
            || matchesIc2Item(item, Ic2Items.reactorDepletedUraniumDual)
            || matchesIc2Item(item, Ic2Items.reactorDepletedUraniumQuad)
            || matchesIc2Item(item, Ic2Items.reactorDepletedMOXSimple)
            || matchesIc2Item(item, Ic2Items.reactorDepletedMOXDual)
            || matchesIc2Item(item, Ic2Items.reactorDepletedMOXQuad);
    }

    private static boolean matchesIc2Item(Item item, ItemStack template) {
        return template != null && template.getItem() == item;
    }

    // endregion

    // region Slot lock / memory

    public int getSlotCount() {
        return mInventory.length;
    }

    public ItemStack getSlotStack(int index) {
        if (index < 0 || index >= mInventory.length) return null;
        return mInventory[index];
    }

    public void setSlotStack(int index, ItemStack stack) {
        if (index < 0 || index >= mInventory.length) return;
        mInventory[index] = stack;
        onContentsChanged(index);
    }

    public boolean isSlotLocked(int index) {
        return index >= 0 && index < mSlotState.length && mSlotState[index] >= 0;
    }

    /** @return the locked auto-output percentage, or -1 when the slot is not locked. */
    public int getSlotThreshold(int index) {
        if (!isSlotLocked(index)) return -1;
        return mSlotState[index];
    }

    public ItemStack getSlotMemory(int index) {
        if (index < 0 || index >= mSlotMemory.length) return null;
        return mSlotMemory[index];
    }

    /** The raw memory array used by the MUI2 sync handler; MUI2 copies it for its cache. */
    public ItemStack[] getSlotMemoryArray() {
        return mSlotMemory;
    }

    /** The raw array used by the MUI2 sync handler; MUI2 copies it for its cache. */
    public byte[] getSlotStateArray() {
        return mSlotState;
    }

    /**
     * Locks the clicked slot and every slot of this hatch holding the same item type (all pages, durability/heat
     * and NBT ignored). If this item type already has a locked threshold somewhere in the hatch, that threshold is
     * kept instead of being reset to the item default.
     */
    public void lockSlotGroup(int index) {
        if (index < 0 || index >= mInventory.length) return;
        ItemStack reference = mInventory[index];
        if (reference == null) return;
        byte threshold = (byte) getDefaultOutputPercent(reference);
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] >= 0 && isSameItemType(mSlotMemory[i], reference)) {
                threshold = mSlotState[i];
                break;
            }
        }
        for (int i = 0; i < mInventory.length; i++) {
            if (isSameItemType(mInventory[i], reference)) {
                mSlotState[i] = threshold;
                mSlotMemory[i] = mInventory[i].copy();
            }
        }
        markDirty();
    }

    /** Unlocks only the clicked slot, so a single misclick cannot remove a whole item group. */
    public void unlockSlot(int index) {
        if (index < 0 || index >= mSlotState.length) return;
        mSlotState[index] = -1;
        mSlotMemory[index] = null;
        markDirty();
    }

    /**
     * Global unlock (Alt + Shift + right click): removes the lock from every locked slot of this hatch holding
     * the same item type as the clicked slot, all pages included.
     */
    public void unlockSlotGroup(int index) {
        if (index < 0 || index >= mInventory.length) return;
        ItemStack reference = mSlotMemory[index] != null ? mSlotMemory[index] : mInventory[index];
        if (reference == null) {
            unlockSlot(index);
            return;
        }
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] >= 0 && isSameItemType(mSlotMemory[i], reference)) {
                mSlotState[i] = -1;
                mSlotMemory[i] = null;
            }
        }
        markDirty();
    }

    /**
     * The auto-output percentage is a property of the item type inside one hatch: setting it on any locked slot
     * updates every locked slot of this hatch holding the same item type (all pages included).
     */
    public void setSlotThreshold(int index, int percent) {
        if (!isSlotLocked(index)) return;
        byte clamped = (byte) Math.max(0, Math.min(100, percent));
        ItemStack reference = mSlotMemory[index];
        if (reference == null) {
            mSlotState[index] = clamped;
            markDirty();
            return;
        }
        for (int i = 0; i < mSlotState.length; i++) {
            if (mSlotState[i] >= 0 && isSameItemType(mSlotMemory[i], reference)) {
                mSlotState[i] = clamped;
            }
        }
        markDirty();
    }

    /** True when a locked slot does not hold its remembered item type. */
    public boolean hasMissingLockedComponents() {
        for (int i = 0; i < mInventory.length; i++) {
            if (mSlotState[i] < 0) continue;
            if (!isSameItemType(mInventory[i], mSlotMemory[i])) return true;
        }
        return false;
    }

    /**
     * Item type comparison for slot memory. Reactor components such as GT fuel rods ({@code ItemRadioactiveCellIC})
     * and GT coolant cells ({@code ItemCoolantCellIC}) keep their real state in NBT but update the vanilla item
     * damage to draw their durability/heat bar, so damage must be ignored whenever the item has a damage bar.
     * For items without one, damage is treated as part of the type (metadata variants).
     */
    public static boolean isSameItemType(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getMaxDamage() > 0 || b.getMaxDamage() > 0) return true;
        return a.getItemDamage() == b.getItemDamage();
    }

    /**
     * True for rods that still burn fuel: IC2/GT burnable fuel rods (including MessTech's transcendent rods) but not
     * depleted cells. Used by the slot sub-panel to decide whether the fuel durability line is shown.
     */
    public static boolean isFuelRod(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (stack.getItem() instanceof ItemReactorUranium) return true;
        // GT's ItemRadioactiveCellIC stores its real state in NBT (advDmg) and paints the vanilla 0..99 bar.
        return stack.getItem() instanceof ItemRadioactiveCell && !(stack.getItem() instanceof ItemDepletedCell);
    }

    public static boolean isCoolingCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemReactorHeatStorage;
    }

    /** Remaining durability in percent (rounded) for display; IC2 fuel rods and coolant cells use custom damage. */
    public static int getDurabilityPercent(ItemStack stack) {
        return (int) Math.round(getDurabilityFraction(stack));
    }

    /**
     * Exact remaining durability in percent (fractional). Used for the auto-output/input threshold comparison so a
     * fuel rod that still has a tiny bit left is not rounded down to 0% and output early.
     */
    public static double getDurabilityFraction(ItemStack stack) {
        if (stack == null) return 0.0D;
        if (stack.getItem() instanceof ItemGradualInt gradual) {
            int max = gradual.getMaxCustomDamage(stack);
            if (max <= 0) return 100.0D;
            int remaining = max - gradual.getCustomDamage(stack);
            if (remaining <= 0) return 0.0D;
            return remaining * 100.0D / max;
        }
        if (stack.getMaxDamage() > 0) {
            int remaining = stack.getMaxDamage() - stack.getItemDamage();
            if (remaining <= 0) return 0.0D;
            return remaining * 100.0D / stack.getMaxDamage();
        }
        return 100.0D;
    }

    /** Current IC2 custom damage (heat for coolant cells, used damage for fuel rods). */
    public static int getCurrentHeat(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemGradualInt gradual)) return 0;
        return gradual.getCustomDamage(stack);
    }

    public static int getMaxHeat(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemGradualInt gradual)) return 0;
        return gradual.getMaxCustomDamage(stack);
    }

    /** Fuel rods default to 0%, coolant cells to 5%, everything else to 0%. */
    public static int getDefaultOutputPercent(ItemStack stack) {
        return isCoolingCell(stack) ? 5 : 0;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setByteArray("mtReactorSlotState", mSlotState);
        NBTTagList memoryList = new NBTTagList();
        for (int i = 0; i < mSlotMemory.length; i++) {
            if (mSlotMemory[i] == null) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("slot", i);
            NBTTagCompound itemTag = new NBTTagCompound();
            mSlotMemory[i].writeToNBT(itemTag);
            entry.setTag("item", itemTag);
            memoryList.appendTag(entry);
        }
        aNBT.setTag("mtReactorSlotMemory", memoryList);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        Arrays.fill(mSlotState, (byte) -1);
        byte[] state = aNBT.getByteArray("mtReactorSlotState");
        if (state != null) {
            System.arraycopy(state, 0, mSlotState, 0, Math.min(state.length, mSlotState.length));
        }
        Arrays.fill(mSlotMemory, null);
        NBTTagList memoryList = aNBT.getTagList("mtReactorSlotMemory", 10);
        for (int i = 0; i < memoryList.tagCount(); i++) {
            NBTTagCompound entry = memoryList.getCompoundTagAt(i);
            int slot = entry.getInteger("slot");
            if (slot < 0 || slot >= mSlotMemory.length) continue;
            ItemStack item = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("item"));
            if (item != null) {
                mSlotMemory[slot] = item;
            }
        }
    }

    // endregion

    // region Textures

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        sReactorOverlay = Textures.BlockIcons.custom(OVERLAY_DOMAIN, OVERLAY_PATH);
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return withOverlay(aBaseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return withOverlay(aBaseTexture);
    }

    private ITexture[] withOverlay(ITexture aBaseTexture) {
        if (sReactorOverlay == null) {
            return new ITexture[] { aBaseTexture };
        }
        return new ITexture[] { aBaseTexture, TextureFactory.of(sReactorOverlay) };
    }

    // endregion
}
