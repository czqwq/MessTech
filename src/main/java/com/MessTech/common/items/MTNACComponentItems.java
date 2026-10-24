package com.MessTech.common.items;

import net.minecraft.item.ItemStack;

import gregtech.api.items.CircuitComponentFakeItem;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;

/**
 * Conversion helpers between NAC fake CircuitComponent stacks and their real item forms: the original real item when
 * {@link CircuitComponent#realComponent} exists, {@link MTNACComponentItem} when the component only ever existed as a
 * fake item.
 */
public final class MTNACComponentItems {

    private MTNACComponentItems() {}

    public static ItemStack getRealItemStack(CircuitComponent component, int amount) {
        if (component == null || amount <= 0) return null;
        // The "packet" is a physical, holdable item; every CircuitComponent has one of these forms.
        if (MTNACComponentItem.INSTANCE != null) {
            return MTNACComponentItem.getStack(component, amount);
        }
        // Fallback: the hidden fake item, so the mapping still means something in early registration.
        return component.getFakeStack(amount);
    }

    public static ItemStack getFakeStack(ItemStack realStack) {
        if (realStack == null) return null;
        CircuitComponent component = getComponent(realStack);
        if (component == null) return null;
        return component.getFakeStack(realStack.stackSize);
    }

    public static CircuitComponent getComponent(ItemStack stack) {
        if (stack == null) return null;
        if (MTNACComponentItem.isStackOfThis(stack)) {
            return CircuitComponent.tryGetFromFakeStack(stack);
        }
        if (stack.getItem() == CircuitComponentFakeItem.INSTANCE) {
            return CircuitComponent.tryGetFromFakeStack(stack);
        }
        // Also accept original real item forms by looking up every component.
        for (CircuitComponent component : CircuitComponent.VALUES) {
            if (component.realComponent == null) continue;
            ItemStack real = component.realComponent.get();
            if (real != null && real.isItemEqual(stack)) {
                return component;
            }
        }
        return null;
    }

    public static boolean isRealItemForm(ItemStack stack) {
        if (stack == null) return false;
        if (MTNACComponentItem.isStackOfThis(stack)) return true;
        for (CircuitComponent component : CircuitComponent.VALUES) {
            if (component.realComponent == null) continue;
            ItemStack real = component.realComponent.get();
            if (real != null && real.isItemEqual(stack)) return true;
        }
        return false;
    }
}
