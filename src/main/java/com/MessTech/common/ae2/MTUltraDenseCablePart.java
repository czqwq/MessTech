package com.MessTech.common.ae2;

import net.minecraft.item.ItemStack;

import appeng.api.parts.PartItemStack;
import appeng.parts.networking.PartDenseCable;

/**
 * 超致密压缩ME线缆 as an AE2 dense cable part, see {@code docs/ultra-ae2.md} §1.5 / §2 T-B.
 * <p>
 * Everything about cable behaviour is inherited from {@link PartDenseCable}: connection type {@code AECableType.DENSE},
 * collision boxes, the static renderer, the channel display and the cable bus NBT handling. Only two things are ours:
 * <ul>
 * <li>{@link #mtChannelCapacity()} is consulted by AE2 through {@code MixinGridNode_ChannelCapacity}, because
 * {@code GridNode#getMaxChannels()} would otherwise report 32 channels for a dense cable;</li>
 * <li>{@link #getItemStack()} / {@link #getItemStack(PartItemStack)} return our own item stack
 * ({@link MTUltraDenseCableItem}, damage = the colour), because the cable bus saves {@code getItemStack(World)} to
 * NBT and drops {@code getItemStack(Break)}, and the saved stack has to reconstruct this part through
 * {@link MTUltraDenseCableItem#createPartFromItemStack} on load.</li>
 * </ul>
 * The part itself is constructed on AE2's dense cable stack of the same colour: {@code PartDenseCable}'s constructor
 * casts it to {@code ItemMultiPart} to read the colour and passes it to the network proxy, which keeps the AE2
 * colour/material/rendering of the original cable.
 */
public class MTUltraDenseCablePart extends PartDenseCable implements IMTChannelCapacity {

    /** Our item stack (damage = {@link appeng.api.util.AEColor#ordinal()}), reported by {@link #getItemStack()}. */
    private final ItemStack displayStack;

    /**
     * @param ae2BaseStack AE2 dense cable stack of the wanted colour; {@code PartDenseCable} reads the colour from it.
     * @param displayStack our own cable item stack, kept for the item/NBT round-trip.
     */
    public MTUltraDenseCablePart(ItemStack ae2BaseStack, ItemStack displayStack) {
        super(ae2BaseStack);
        this.displayStack = displayStack.copy();
    }

    @Override
    public int mtChannelCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack getItemStack() {
        return this.displayStack.copy();
    }

    @Override
    public ItemStack getItemStack(PartItemStack type) {
        final ItemStack stack = this.displayStack.copy();
        if (type == PartItemStack.Network) {
            stack.setTagCompound(null);
        }
        return stack;
    }
}
