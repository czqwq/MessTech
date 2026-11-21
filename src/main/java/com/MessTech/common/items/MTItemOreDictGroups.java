package com.MessTech.common.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.init.MessTech;

import cpw.mods.fml.common.registry.GameData;

/**
 * Ore dictionary filter groups over whole item categories, so that one ore dictionary entry selects every item of that
 * kind in a filter:
 * <ul>
 * <li>{@value #PLAYER_ARMOR} - every item whose class extends {@link ItemArmor};</li>
 * <li>{@value #SWORD} - every item whose class extends {@link ItemSword};</li>
 * <li>{@value #UNSTACKABLE_ITEM} - every item whose max stack size is 1, armor and swords included.</li>
 * </ul>
 * The scan is pack-wide (every loaded mod's items, not just this mod's), so it runs at load complete, after the item
 * registry is final. Each item is registered once with wildcard metadata: armor and swords are damageable, and a used
 * piece has an arbitrary damage value that only the wildcard entry matches.
 * <p>
 * The armor tag is {@code playerArmor}, not {@code armor}: {@code OrePrefixes.armor} owns the name {@code armor} in
 * GT5U, and GT's ore dictionary handler treats a prefix used as a full ore dictionary name as invalid - it renames the
 * registered piles' stacks to "Invalid OreDictionary Tag". {@value #SWORD} and {@value #UNSTACKABLE_ITEM} collide with
 * no GT prefix.
 * <p>
 * Called from {@link MessTech#completeInit}; switched off by {@code Config.general.ITEM_OREDICT_GROUPS}.
 */
public final class MTItemOreDictGroups {

    /** Every item extending {@link ItemArmor}. */
    public static final String PLAYER_ARMOR = "playerArmor";

    /** Every item extending {@link ItemSword}. */
    public static final String SWORD = "sword";

    /** Every item whose max stack size is 1; a superset of {@value #PLAYER_ARMOR} and {@value #SWORD}. */
    public static final String UNSTACKABLE_ITEM = "unstackableItem";

    private MTItemOreDictGroups() {}

    /** Registers the three groups over the finished item registry. */
    public static void register() {
        int armor = 0;
        int sword = 0;
        int unstackable = 0;
        int unregistered = 0;
        for (Item item : GameData.getItemRegistry()
            .typeSafeIterable()) {
            if (item == null) continue;
            // OreDictionary rejects stacks of items the game registry does not know (with a big warning), and such an
            // item cannot show up in a filter anyway.
            if (GameData.getItemRegistry()
                .getNameForObject(item) == null) {
                unregistered++;
                continue;
            }

            final ItemStack wildcard = new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE);
            if (new ItemStack(item).getMaxStackSize() == 1) {
                OreDictionary.registerOre(UNSTACKABLE_ITEM, wildcard);
                unstackable++;
            }
            if (item instanceof ItemArmor) {
                OreDictionary.registerOre(PLAYER_ARMOR, wildcard);
                armor++;
            }
            if (item instanceof ItemSword) {
                OreDictionary.registerOre(SWORD, wildcard);
                sword++;
            }
        }
        MessTech.MT_LOG.info(
            "Ore dictionary filter groups: {} armor, {} sword, {} unstackable item(s) ({} unregistered item(s) skipped)",
            armor,
            sword,
            unstackable,
            unregistered);
    }
}
