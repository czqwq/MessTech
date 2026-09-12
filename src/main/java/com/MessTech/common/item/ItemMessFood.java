package com.MessTech.common.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import squeek.applecore.api.food.FoodValues;
import squeek.applecore.api.food.IEdible;

/**
 * A food made by throwing several foods into a crafting grid.
 * <p>
 * The ordered list of ingredients is stored in the item's NBT, so two mess foods are only the same
 * food when they were made from exactly the same ingredients in exactly the same order. Its hunger
 * value is the sum of the hunger values of all ingredients.
 */
public class ItemMessFood extends ItemFood implements IEdible {

    public static final String NBT_INGREDIENTS = "Ingredients";
    public static final int MIN_INGREDIENTS = 2;
    public static final int MAX_INGREDIENTS = 9;

    public static ItemMessFood INSTANCE;

    public ItemMessFood() {
        // The values passed here are never used; every value is computed from the stored ingredients.
        super(0, 0F, false);
        setUnlocalizedName("messtech.messFood");
        setTextureName("messtech:food");
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabFood);
        INSTANCE = this;
    }

    /**
     * @return the ordered ingredient list of the given mess food, or an empty list for anything else.
     */
    public static List<ItemStack> getIngredients(ItemStack stack) {
        return MessFoodHelper.getIngredients(stack);
    }

    @Override
    public FoodValues getFoodValues(ItemStack stack) {
        List<ItemStack> ingredients = MessFoodHelper.getIngredients(stack);
        if (ingredients.isEmpty()) return new FoodValues(0, 0F);

        int hunger = MessFoodHelper.getTotalHunger(ingredients);
        float saturationIncrement = MessFoodHelper.getTotalSaturationIncrement(ingredients);
        // FoodValues stores a modifier, so convert the summed saturation increments back into one.
        float saturationModifier = hunger > 0 ? saturationIncrement / (hunger * 2F) : 0F;
        return new FoodValues(hunger, saturationModifier);
    }

    @Override
    public int func_150905_g(ItemStack stack) {
        return getFoodValues(stack).hunger;
    }

    @Override
    public float func_150906_h(ItemStack stack) {
        return getFoodValues(stack).saturationModifier;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        // A mess food without ingredients is only obtainable through commands/creative cheats.
        if (MessFoodHelper.getIngredients(stack)
            .isEmpty()) return stack;
        return super.onItemRightClick(stack, world, player);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (MessFoodHelper.getIngredients(stack)
            .isEmpty()) return super.getItemStackDisplayName(stack);

        // "Mess Food(apple,bread)" so Spice of Life's journal can tell the variants apart.
        return StatCollector.translateToLocalFormatted(
            "item.messtech.messFood.display",
            super.getItemStackDisplayName(stack),
            MessFoodHelper.getIngredientNames(stack));
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        List<ItemStack> ingredients = MessFoodHelper.getIngredients(stack);
        if (ingredients.isEmpty()) return;

        tooltip.add(StatCollector.translateToLocal("item.messtech.messFood.tooltip.ingredients"));
        for (ItemStack ingredient : ingredients) {
            tooltip.add(EnumChatFormatting.GRAY + "- " + EnumChatFormatting.RESET + ingredient.getDisplayName());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        // Show one example in the creative tab so the item can be inspected without crafting it.
        ItemStack example = MessFoodHelper.createStack(new ItemStack(Items.apple), new ItemStack(Items.bread));
        if (example != null) list.add(example);
    }
}
