package com.MessTech.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.network.base.ServerboundPacket;
import com.MessTech.common.parts.PartUltimatePatternTerminal;
import com.MessTech.init.MessTech;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;

/**
 * Client to server half of this mod's NEI import into the Ultimate Pattern Terminal.
 * <p>
 * NEI runs on the client and the grid lives in the part, so the cells the overlay computed travel over as one
 * compound: {@code in} and {@code out} are maps of cell index to ItemStack. Every cell is written by
 * {@link #writeCell(ItemStack)} and read back by {@link #readCell(NBTTagCompound)}, never by plain
 * {@code ItemStack#writeToNBT}/{@code loadItemStackFromNBT}: see {@link #COUNT_KEY}.
 */
public class PatternImportHandler extends ServerboundPacket {

    /**
     * NBT key holding a cell's item count.
     * <p>
     * 1.7.10 writes {@code ItemStack}'s count into the tag <em>byte</em> {@code Count} and reads it back as a byte,
     * so any count outside -128..127 survives the round trip as a different number: 1024 and 4096 both come back as
     * 0, 1025 and 4097 as 1. The Nano-Scale Foundry "24" pool needs exactly those amounts - its flattened Planck
     * chain asks for 1024 Optical Fiber Cables and 4096 ASoC RAMs <em>in a single input cell</em> - and an imported
     * cell whose count collapsed to 0 is a stack AE shows without a count and encodes into the pattern as
     * {@code Cnt: 0}, so the material is effectively lost. Each cell therefore carries its real count again under
     * this long tag, which is the same fix NotEnoughEnergistics applies to its own pattern transfer.
     */
    public static final String COUNT_KEY = "MTCount";

    private NBTTagCompound cells = new NBTTagCompound();

    public PatternImportHandler() {

    }

    public PatternImportHandler(NBTTagCompound cells) {
        this.cells = cells;
    }

    /**
     * Writes one grid cell: the stack as vanilla NBT plus its real count under {@link #COUNT_KEY}.
     * <p>
     * GT hands NEI fluid inputs over as {@code ItemList.Display_Fluid} display stacks whose NBT carries the amount,
     * so their count is the display stack's own 1 and the amount survives untouched in that tag.
     */
    public static NBTTagCompound writeCell(ItemStack stack) {
        final NBTTagCompound cell = stack.writeToNBT(new NBTTagCompound());
        cell.setLong(COUNT_KEY, stack.stackSize);
        return cell;
    }

    /**
     * Reads one grid cell back. A cell without {@link #COUNT_KEY} - only a payload from another build of this mod -
     * keeps whatever vanilla read, and a count that arrives as zero or negative (the vanilla byte mangled it) settles
     * on one, because a zero-count cell is not a stack that can be encoded at all.
     */
    public static ItemStack readCell(NBTTagCompound cell) {
        final ItemStack stack = ItemStack.loadItemStackFromNBT(cell);
        if (stack == null) return null;
        final long count = cell.hasKey(COUNT_KEY) ? cell.getLong(COUNT_KEY) : stack.stackSize;
        stack.stackSize = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, count));
        return stack;
    }

    @Override
    protected void read(ByteBuf buf) {
        cells = ByteBufUtils.readTag(buf);
    }

    @Override
    protected void write(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, cells);
    }

    @Override
    public void handleServer(EntityPlayerMP player) {
        final NBTTagCompound payload = cells;
        ServerThreadUtil.addScheduledTask(() -> {
            // The terminal opens AE's own container, so the part is what identifies the target here.
            if (player.openContainer instanceof ContainerPatternTerm c
                && c.getPatternTerminal() instanceof PartUltimatePatternTerminal) {
                writeIntoGrid(c, payload);
            }
        });
    }

    /**
     * Fills the terminal's grid from the compound. The compound holds two maps of cell index to stack - {@code in}
     * for the inputs and {@code out} for the expected outputs. GT5U hands NEI fluid inputs over as
     * {@code ItemList.Display_Fluid} stacks, which are turned back into real fluids here.
     */
    private static void writeIntoGrid(ContainerPatternTerm container, NBTTagCompound cells) {
        // A pool page is a machine recipe, so it can only become a processing pattern. While AE's container is in
        // crafting mode it mirrors the first nine cells into its crafting matrix on every later
        // detectAndSendChanges() (ContainerPatternTerm#copyToMatrix), which clamps those cells to one and clears them
        // if they are not items: an import must not lose its amounts to that. NotEnoughEnergistics switches the mode
        // the same way before it writes its own transfer.
        if (container.isCraftingMode()) {
            container.setCraftingMode(false);
        }

        final IAEStackInventory inputs = container.getPatternTerminal()
            .getAEInventoryByName(StorageName.CRAFTING_INPUT);
        final IAEStackInventory outputs = container.getPatternTerminal()
            .getAEInventoryByName(StorageName.CRAFTING_OUTPUT);

        for (int i = 0; i < inputs.getSizeInventory(); i++) {
            inputs.putAEStackInSlot(i, null);
        }
        for (int i = 0; i < outputs.getSizeInventory(); i++) {
            outputs.putAEStackInSlot(i, null);
        }

        final int droppedInputs = fill(inputs, cells.getCompoundTag("in"));
        final int droppedOutputs = fill(outputs, cells.getCompoundTag("out"));
        if (droppedInputs > 0 || droppedOutputs > 0) {
            // The page can be wider than this terminal: never drop a material without saying so.
            MessTech.LOG.warn(
                "NEI import dropped {} input and {} output cells: the recipe has more materials than the Ultimate "
                    + "Pattern Terminal's {} input and {} output cells.",
                droppedInputs,
                droppedOutputs,
                inputs.getSizeInventory(),
                outputs.getSizeInventory());
        }

        container.inputsSync.markDirty();
        container.outputsSync.markDirty();
    }

    /** Writes one map into an inventory; returns the number of cells the inventory is too small for. */
    private static int fill(IAEStackInventory inventory, NBTTagCompound cells) {
        int dropped = 0;
        for (var key : cells.func_150296_c()) {
            final int index = Integer.parseInt((String) key);
            if (index < 0 || index >= inventory.getSizeInventory()) {
                dropped++;
                continue;
            }
            final IAEStack<?> aes = toAEStack(readCell(cells.getCompoundTag((String) key)));
            if (aes != null) inventory.putAEStackInSlot(index, aes);
        }
        return dropped;
    }

    /** A NEI fluid display stack becomes the fluid it stands for, anything else a plain AE item stack. */
    private static IAEStack<?> toAEStack(ItemStack is) {
        if (is == null) return null;
        if (ItemList.Display_Fluid.isStackEqual(is, true, true)) {
            final FluidStack fluid = GTUtility.getFluidFromDisplayStack(is);
            return fluid == null ? null : AEFluidStack.create(fluid);
        }
        return AEItemStack.create(is);
    }
}
