package com.MessTech.common.mixin.late;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.common.item.MessFoodHelper;

import squeek.spiceoflife.foodtracker.FoodEaten;

/**
 * Mess foods store their ordered ingredient list in NBT. Spice of Life's original
 * {@link FoodEaten#equals} only compares item and metadata, which would merge every mess food into
 * one journal entry, so the NBT is added to the identity here (for mess foods only).
 */
@Mixin(value = FoodEaten.class, remap = false)
public abstract class MixinFoodEaten {

    @Shadow(remap = false)
    public ItemStack itemStack;

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true, remap = false)
    private void messtech$equals(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (!(other instanceof FoodEaten)) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack otherStack = ((FoodEaten) other).itemStack;
        if (itemStack == null || otherStack == null) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(MessFoodHelper.sameFoodIdentity(itemStack, otherStack));
    }

    @Inject(method = "hashCode", at = @At("RETURN"), cancellable = true, remap = false)
    private void messtech$hashCode(CallbackInfoReturnable<Integer> cir) {
        if (MessFoodHelper.isMessFood(itemStack)) {
            cir.setReturnValue(MessFoodHelper.identityHash(itemStack));
        }
    }
}
