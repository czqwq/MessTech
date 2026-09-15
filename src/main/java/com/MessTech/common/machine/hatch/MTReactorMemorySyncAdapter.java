package com.MessTech.common.machine.hatch;

import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.serialization.IByteBufAdapter;

/**
 * MUI2 adapter for syncing the per-slot memory item array to the client, so empty locked slots can render a faded
 * ghost icon of the remembered item.
 */
public class MTReactorMemorySyncAdapter implements IByteBufAdapter<ItemStack[]> {

    public static final MTReactorMemorySyncAdapter INSTANCE = new MTReactorMemorySyncAdapter();

    private MTReactorMemorySyncAdapter() {}

    @Override
    public void serialize(PacketBuffer buffer, ItemStack[] value) throws IOException {
        if (value == null) {
            buffer.writeInt(-1);
            return;
        }
        buffer.writeInt(value.length);
        for (ItemStack stack : value) {
            NetworkUtils.writeItemStack(buffer, stack);
        }
    }

    @Override
    public ItemStack[] deserialize(PacketBuffer buffer) throws IOException {
        int length = buffer.readInt();
        if (length < 0) return null;
        ItemStack[] value = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            value[i] = NetworkUtils.readItemStack(buffer);
        }
        return value;
    }

    @Override
    public boolean areEqual(ItemStack[] a, ItemStack[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            ItemStack first = a[i];
            ItemStack second = b[i];
            if (first == null || second == null) {
                if (first != second) return false;
                continue;
            }
            if (!ItemStack.areItemStacksEqual(first, second)) return false;
        }
        return true;
    }
}
