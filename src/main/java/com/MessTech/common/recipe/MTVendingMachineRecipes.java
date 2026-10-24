package com.MessTech.common.recipe;

import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.MinecraftForge;

import com.MessTech.common.items.MTItems;
import com.MessTech.init.MessTech;
import com.cubefury.vendingmachine.events.MarkDirtyDbEvent;
import com.cubefury.vendingmachine.trade.CurrencyItem;
import com.cubefury.vendingmachine.trade.CurrencyType;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeCategory;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.util.BigItemStack;
import com.cubefury.vendingmachine.util.NBTConverter;

import gregtech.api.enums.Mods;
import gregtech.api.util.GTModHandler;

/**
 * The trade this mod adds to the Vending Machine (售货机): one Infinity Sword, 16 raw pork and 64 Blood coins plus 64
 * Dark Wizard coins buy one plain piggy.
 * <p>
 * The machine has no registration API: trades are the JSON file {@code config/vendingmachine/tradeDatabase.json}, which
 * {@code SaveLoadHandler} reads into {@code TradeDatabase} at server start, the only door in being
 * {@link TradeDatabase#readFromNBT(NBTTagCompound, boolean, boolean)}. This class writes its trade in the machine's own
 * shape ({@link Trade#writeToNBT}, {@link BigItemStack}, {@link CurrencyItem}), merges it with {@code merge = true} and
 * posts {@link MarkDirtyDbEvent} - the "database changed" hook, which writes the file and syncs every client. A second
 * group with the same id is refused, so changing a number below needs a new {@link #PIGGY_TRADE_GROUP}, or delete the
 * group from the file.
 * <p>
 * Sword and pork come from the machine's input slots, then the uplink hatch's ME network; coins from the player's
 * wallet, then that network. The hatch cannot pull damageable items out of the network
 * ({@code MTEVendingUplinkHatch#removeItem} skips AE's extract for those, AE matching them by damage value), so the
 * sword has to be placed in the input slots; pork and coins arrive from the network as usual.
 */
public final class MTVendingMachineRecipes {

    /**
     * Identity of the trade group, which the machine keys its groups by ({@code TradeGroup#getId}): what makes
     * {@link #register()} idempotent, since the merged group is written into the file and found again on the next
     * start.
     */
    private static final UUID PIGGY_TRADE_GROUP = UUID.fromString("8c6b1f3a-52d7-4c9e-9b0a-1e4d7f2c6a35");

    /** Raw pork of the trade. */
    private static final int PORK = 16;
    /** Coins of each of the two coin types: {@link CurrencyType#BLOOD} (吸血鬼币) and {@link CurrencyType#DARK_WIZARD}. */
    private static final int COINS = 64;

    /**
     * Damage of the plain piggy look: a piggy's damage value is its {@code MTDynamicItemHelper.Effect} index, and the
     * plain pig ({@code Effect.NONE}) has to stay at index 0.
     */
    private static final int PLAIN_PIGGY = 0;

    private MTVendingMachineRecipes() {}

    /**
     * Merges the piggy trade into the trade database the machine has loaded.
     * <p>
     * Called from {@code CommonProxy#serverStarted}, i.e. after every mod's {@code FMLServerStartingEvent} handler -
     * the
     * machine reads its database in its own, so this is the first moment a trade can be added. Does nothing when the
     * trade is already there or Avaritia is missing (the sword would be null and the trade never fillable).
     */
    public static void register() {
        // Safety net for a call that is too early: version is -1 until the machine has read its file (SaveLoadHandler),
        // and merging before that would replace the pack's whole trade file with this single group.
        if (TradeDatabase.INSTANCE.version < 0) {
            MessTech.MT_LOG.warn("[VendingMachine] Trade database not loaded yet, the piggy trade is not registered");
            return;
        }
        if (TradeDatabase.INSTANCE.getTradeGroupFromId(PIGGY_TRADE_GROUP) != null) {
            // Already there: a previous run wrote the group into the file (see the class comment).
            return;
        }

        ItemStack infinitySword = GTModHandler.getModItem(Mods.Avaritia.getID(), "Infinity_Sword", 1);
        if (infinitySword == null) {
            MessTech.MT_LOG.warn("[VendingMachine] Infinity_Sword is missing, the piggy trade is not registered");
            return;
        }

        Trade trade = new Trade();
        trade.fromItems.add(new BigItemStack(infinitySword));
        trade.fromItems.add(new BigItemStack(new ItemStack(Items.porkchop, PORK)));
        trade.fromCurrency.add(new CurrencyItem(CurrencyType.BLOOD, COINS));
        trade.fromCurrency.add(new CurrencyItem(CurrencyType.DARK_WIZARD, COINS));
        trade.toItems.add(new BigItemStack(new ItemStack(MTItems.piggy, 1, PLAIN_PIGGY)));

        // merge = true adds this group to the loaded database instead of replacing it.
        TradeDatabase.INSTANCE.readFromNBT(tradeDatabaseEntry(trade), true, false);
        MinecraftForge.EVENT_BUS.post(new MarkDirtyDbEvent());

        MessTech.MT_LOG.info("[VendingMachine] Registered the piggy trade");
    }

    /**
     * One trade group in the shape {@code TradeGroup#readFromNBT} expects, inside the database compound
     * {@link TradeDatabase#readFromNBT} reads its groups from.
     */
    private static NBTTagCompound tradeDatabaseEntry(Trade trade) {
        NBTTagCompound group = new NBTTagCompound();
        group.setTag("id", NBTConverter.UuidValueType.TRADEGROUP.writeId(PIGGY_TRADE_GROUP));
        // -1 is the machine's "no limit" for both fields, and they have to be written: a missing key reads back as 0,
        // and maxTrades 0 would be a group nobody may ever use (see TradeManager#canExecuteTrade).
        group.setInteger("cooldown", -1);
        group.setInteger("maxTrades", -1);
        group.setString("category", TradeCategory.MAGIC.getKey());

        NBTTagList trades = new NBTTagList();
        trades.appendTag(trade.writeToNBT(new NBTTagCompound()));
        group.setTag("trades", trades);

        // No requirements: the machine files a group without conditions under TradeDatabase#noConditionTrades, so the
        // trade is there for every player (the pack's own groups gate themselves behind a quest instead).
        group.setTag("requirements", new NBTTagList());

        NBTTagCompound database = new NBTTagCompound();
        // readFromNBT takes the version out of what it is given, so the database's own version must go back in, or the
        // file would be written with version 0.
        database.setInteger("version", TradeDatabase.INSTANCE.version);

        NBTTagList groups = new NBTTagList();
        groups.appendTag(group);
        database.setTag("tradeGroups", groups);
        return database;
    }
}
