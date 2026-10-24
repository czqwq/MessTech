package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import com.MessTech.common.items.ItemMessFood;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.items.MessFoodHelper;

/**
 * Shapeless "throw any 2-9 foods into a crafting grid" recipe that produces a {@link ItemMessFood}.
 * <p>
 * Order sensitive: ingredients are read in crafting slot order (top-left to bottom-right), so the same foods in
 * different slots make a different mess food. They must also be distinct (apple+apple is rejected), and Mess Food
 * itself
 * cannot be an ingredient.
 */
public class RecipeMessFood implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null) continue;
            if (!MessFoodHelper.isFoodIngredient(stack)) return false;
            // No duplicate ingredients: apple+apple+apple is not a mess food.
            if (MessFoodHelper.containsIngredient(ingredients, stack)) return false;
            ingredients.add(stack);
            if (ingredients.size() > ItemMessFood.MAX_INGREDIENTS) return false;
        }
        return ingredients.size() >= ItemMessFood.MIN_INGREDIENTS;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null) ingredients.add(stack);
        }
        return MessFoodHelper.createStack(ingredients.toArray(new ItemStack[0]));
    }

    @Override
    public int getRecipeSize() {
        return ItemMessFood.MAX_INGREDIENTS;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(MTItems.messFood, 1);
    }

    /**
     * Appends the recipe to the crafting manager: appended instead of inserted, so every pre-existing recipe keeps
     * priority over this catch-all.
     */
    @SuppressWarnings("unchecked")
    public static void register() {
        CraftingManager.getInstance()
            .getRecipeList()
            .add(new RecipeMessFood());
    }
}
