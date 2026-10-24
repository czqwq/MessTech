package com.MessTech.common.mixin.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.MessTech.common.items.MessFoodHelper;

import squeek.spiceoflife.foodtracker.FoodHistory;

/**
 * Spice of Life matches a food against the player's history with {@link ItemStack#isItemEqual}, which ignores NBT; mess
 * foods are matched by their full ordered ingredient tag instead, so each order keeps its own counter and entry.
 */
@Mixin(value = FoodHistory.class, remap = false)
public abstract class MixinFoodHistory {

    @Redirect(
        method = "getFoodCountForFoodGroup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isItemEqual(Lnet/minecraft/item/ItemStack;)Z",
            remap = true),
        remap = false)
    private boolean messtech$isItemEqualCount(ItemStack self, ItemStack other) {
        return MessFoodHelper.sameFoodIdentity(self, other);
    }

    @Redirect(
        method = "containsFoodOrItsFoodGroups",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isItemEqual(Lnet/minecraft/item/ItemStack;)Z",
            remap = true),
        remap = false)
    private boolean messtech$isItemEqualContains(ItemStack self, ItemStack other) {
        return MessFoodHelper.sameFoodIdentity(self, other);
    }

    @Redirect(
        method = "getTotalFoodValuesForFoodGroup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isItemEqual(Lnet/minecraft/item/ItemStack;)Z",
            remap = true),
        remap = false)
    private boolean messtech$isItemEqualTotals(ItemStack self, ItemStack other) {
        return MessFoodHelper.sameFoodIdentity(self, other);
    }
}
