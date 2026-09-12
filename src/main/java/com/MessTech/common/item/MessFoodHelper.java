package com.MessTech.common.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import squeek.applecore.api.AppleCoreAPI;
import squeek.applecore.api.food.FoodValues;

/**
 * Shared helpers for {@link ItemMessFood}.
 * <p>
 * The helpers are also used by the Spice of Life mixins, which need a food identity that takes the
 * stored ingredient list (and therefore its order) into account.
 */
public final class MessFoodHelper {

    private MessFoodHelper() {}

    public static boolean isMessFood(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemMessFood;
    }

    /**
     * Reads the ordered ingredient list from the item's NBT.
     */
    public static List<ItemStack> getIngredients(ItemStack stack) {
        if (!isMessFood(stack) || stack.getTagCompound() == null) return Collections.emptyList();

        NBTTagList list = stack.getTagCompound()
            .getTagList(ItemMessFood.NBT_INGREDIENTS, Constants.NBT.TAG_COMPOUND);
        if (list.tagCount() == 0) return Collections.emptyList();

        List<ItemStack> ingredients = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack ingredient = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (ingredient != null) ingredients.add(ingredient);
        }
        return ingredients;
    }

    /**
     * @return the display names of the stored ingredients, joined in their crafting order.
     */
    public static String getIngredientNames(ItemStack stack) {
        StringBuilder builder = new StringBuilder();
        for (ItemStack ingredient : getIngredients(stack)) {
            if (builder.length() > 0) builder.append(',');
            builder.append(ingredient.getDisplayName());
        }
        return builder.toString();
    }

    /**
     * Writes the given ingredient list into a new mess food stack. The order of the passed stacks is
     * preserved, so it defines the identity of the resulting food. Duplicate ingredients are not
     * allowed, so e.g. apple+apple can never become a mess food.
     */
    public static ItemStack createStack(ItemStack... ingredients) {
        if (ItemMessFood.INSTANCE == null) return null;
        if (ingredients == null || ingredients.length < ItemMessFood.MIN_INGREDIENTS
            || ingredients.length > ItemMessFood.MAX_INGREDIENTS) return null;
        if (hasDuplicateIngredients(ingredients)) return null;

        NBTTagList list = new NBTTagList();
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getItem() == null) return null;
            ItemStack single = ingredient.copy();
            single.stackSize = 1;
            NBTTagCompound tag = new NBTTagCompound();
            single.writeToNBT(tag);
            list.appendTag(tag);
        }

        ItemStack result = new ItemStack(ItemMessFood.INSTANCE, 1);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag(ItemMessFood.NBT_INGREDIENTS, list);
        result.setTagCompound(compound);
        return result;
    }

    /**
     * Whether the given stack may be used as a mess food ingredient. Mess Food itself is excluded,
     * so it can never be used to craft another mess food.
     */
    public static boolean isFoodIngredient(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (isMessFood(stack)) return false;
        return AppleCoreAPI.accessor != null && AppleCoreAPI.accessor.isFood(stack);
    }

    /**
     * Two stacks are the same ingredient when item, damage and NBT all match, i.e. when they are
     * exactly the same item. Stack size is irrelevant.
     */
    public static boolean sameIngredient(ItemStack first, ItemStack second) {
        if (first == null || second == null) return false;
        if (first.getItem() == null || second.getItem() == null) return false;
        return first.getItem() == second.getItem() && first.getItemDamage() == second.getItemDamage()
            && ItemStack.areItemStackTagsEqual(first, second);
    }

    /**
     * @return true when the same item appears more than once, e.g. {@code [apple, apple]}.
     */
    public static boolean hasDuplicateIngredients(ItemStack... ingredients) {
        if (ingredients == null) return false;
        for (int i = 0; i < ingredients.length; i++) {
            for (int j = i + 1; j < ingredients.length; j++) {
                if (sameIngredient(ingredients[i], ingredients[j])) return true;
            }
        }
        return false;
    }

    /**
     * @return true when the candidate is already present in the given ingredient list.
     */
    public static boolean containsIngredient(List<ItemStack> ingredients, ItemStack candidate) {
        if (ingredients == null || candidate == null) return false;
        for (ItemStack ingredient : ingredients) {
            if (sameIngredient(ingredient, candidate)) return true;
        }
        return false;
    }

    private static FoodValues getFoodValues(ItemStack stack) {
        if (stack == null || stack.getItem() == null || AppleCoreAPI.accessor == null) return null;
        return AppleCoreAPI.accessor.getFoodValues(stack);
    }

    public static int getTotalHunger(List<ItemStack> ingredients) {
        int total = 0;
        for (ItemStack ingredient : ingredients) {
            FoodValues values = getFoodValues(ingredient);
            if (values != null) total += values.hunger;
        }
        return total;
    }

    /**
     * @return the summed saturation that the ingredients would restore, before the player's food
     *         level clamps it.
     */
    public static float getTotalSaturationIncrement(List<ItemStack> ingredients) {
        float total = 0F;
        for (ItemStack ingredient : ingredients) {
            FoodValues values = getFoodValues(ingredient);
            if (values != null) total += values.getSaturationIncrement();
        }
        return total;
    }

    /**
     * The food identity used by Spice of Life.
     * <p>
     * For mess foods the whole NBT tag (the ordered ingredient list) is part of the identity, so
     * every ingredient order counts as a distinct food. For every other item the behaviour of
     * Spice of Life's {@code FoodEaten#equals} is reproduced: registry name, item and damage.
     */
    public static boolean sameFoodIdentity(ItemStack first, ItemStack second) {
        if (first == null || second == null) return false;

        Item firstItem = first.getItem();
        Item secondItem = second.getItem();
        if (firstItem == null || secondItem == null) return false;
        if (firstItem != secondItem) return false;
        if (first.getItemDamage() != second.getItemDamage()) return false;

        if (firstItem instanceof ItemMessFood) return ItemStack.areItemStackTagsEqual(first, second);

        Object firstName = Item.itemRegistry.getNameForObject(firstItem);
        return firstName != null && firstName.equals(Item.itemRegistry.getNameForObject(secondItem));
    }

    /**
     * Hash code matching {@link #sameFoodIdentity}. Only mess foods mix the NBT into the hash, every
     * other item keeps Spice of Life's original hash code.
     */
    public static int identityHash(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;

        int hash = stack.getItem()
            .hashCode();
        if (stack.getItem() instanceof ItemMessFood) {
            hash = 31 * hash + stack.getItemDamage();
            NBTTagCompound compound = stack.getTagCompound();
            hash = 31 * hash + (compound == null ? 0 : compound.hashCode());
        }
        return hash;
    }
}
