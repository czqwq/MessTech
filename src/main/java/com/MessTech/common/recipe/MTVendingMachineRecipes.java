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
 * The machine has no registration API. Its trades are the single JSON file {@code config/vendingmachine/tradeDatabase
 * .json}, which {@code SaveLoadHandler} reads back into {@code TradeDatabase} while the server starts, and the only
 * door into that database is {@link TradeDatabase#readFromNBT(NBTTagCompound, boolean, boolean)} - the machine itself
 * calls it with {@code merge = true} to push the database to a client. This class therefore writes the trade in the
 * machine's own shape ({@link Trade#writeToNBT}, {@link BigItemStack}, {@link CurrencyItem}) and merges that one group
 * into the database the machine has just loaded.
 * <p>
 * The merge is followed by {@link MarkDirtyDbEvent}, the machine's own "the database changed" hook, which writes the
 * file and syncs the database to every client, so from the first run on the group lives in that file. The machine
 * refuses a second group with the same id (it logs an error and drops it), so a change to any of the numbers below
 * needs a new {@link #PIGGY_TRADE_GROUP}; otherwise the copy already in the file keeps winning. Deleting the group
 * from the file lets this class define it again.
 * <p>
 * Where the inputs come from: the sword and the pork are taken out of the machine's own input slots first and out of
 * the ME network of the uplink hatch after that, the coins come from the player's wallet first (i.e. the coins inserted
 * into the machine) and from that same ME network after that. Note that the uplink hatch cannot pull damageable
 * items out of the network ({@code MTEVendingUplinkHatch#removeItem} skips AE's extract for those, since AE matches
 * them by their damage value), so the Infinity Sword has to be placed in the machine's input slots - the pork and the
 * coins arrive from the network as usual.
 */
public final class MTVendingMachineRecipes {

    /**
     * Identity of the trade group. The machine keys its groups by UUID ({@code TradeGroup#getId}), so this constant is
     * what makes {@link #register()} idempotent: the group merged here is written into the trade database file and
     * found again - and then skipped - on the next start.
     */
    private static final UUID PIGGY_TRADE_GROUP = UUID.fromString("8c6b1f3a-52d7-4c9e-9b0a-1e4d7f2c6a35");

    /** Raw pork of the trade. */
    private static final int PORK = 16;
    /** Coins of each of the two coin types: {@link CurrencyType#BLOOD} (吸血鬼币) and {@link CurrencyType#DARK_WIZARD}. */
    private static final int COINS = 64;

    /**
     * Damage of the plain piggy look: the damage value of a piggy is its {@code MTDynamicItemHelper.Effect} index, and
     * the plain pig ({@code Effect.NONE}) has to stay at index 0 - see that enum.
     */
    private static final int PLAIN_PIGGY = 0;

    private MTVendingMachineRecipes() {}

    /**
     * Merges the piggy trade into the trade database the machine has loaded.
     * <p>
     * Called from {@code CommonProxy#serverStarted}, i.e. after every mod's {@code FMLServerStartingEvent} handler -
     * the machine reads its database in its own one, so this is the first moment a trade can be added to it. Does
     * nothing when the trade is already in the database or when Avaritia is missing (the sword would be null and the
     * trade could never be filled).
     */
    public static void register() {
        // Safety net for a call that is too early: version is -1 until the machine has read its file (SaveLoadHandler).
        // Merging - and therefore writing the database back out - before that would replace the pack's whole trade file
        // with this single group.
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

        // merge = true adds this group to the database the machine has loaded instead of replacing it.
        TradeDatabase.INSTANCE.readFromNBT(tradeDatabaseEntry(trade), true, false);
        // The machine's "the database changed" event: writes the file and syncs the database to every client.
        MinecraftForge.EVENT_BUS.post(new MarkDirtyDbEvent());

        MessTech.MT_LOG.info("[VendingMachine] Registered the piggy trade");
    }

    /**
     * One trade group in the shape {@code TradeGroup#readFromNBT} expects, wrapped in the database compound
     * {@link TradeDatabase#readFromNBT} reads its groups from.
     */
    private static NBTTagCompound tradeDatabaseEntry(Trade trade) {
        NBTTagCompound group = new NBTTagCompound();
        group.setTag("id", NBTConverter.UuidValueType.TRADEGROUP.writeId(PIGGY_TRADE_GROUP));
        // -1 is the machine's "no limit" for both fields, and they have to be written out: a missing key is read back
        // as 0, and maxTrades 0 would be a group nobody may ever use (see TradeManager#canExecuteTrade).
        group.setInteger("cooldown", -1);
        group.setInteger("maxTrades", -1);
        group.setString("category", TradeCategory.MAGIC.getKey());

        NBTTagList trades = new NBTTagList();
        trades.appendTag(trade.writeToNBT(new NBTTagCompound()));
        group.setTag("trades", trades);

        // No requirements: the machine files a group without conditions under TradeDatabase#noConditionTrades, so the
        // trade is there for every player. The pack's own groups gate themselves behind a quest instead.
        group.setTag("requirements", new NBTTagList());

        NBTTagCompound database = new NBTTagCompound();
        // readFromNBT takes the version out of what it is given, so the database's own version has to go back in -
        // otherwise the file would be written with version 0.
        database.setInteger("version", TradeDatabase.INSTANCE.version);

        NBTTagList groups = new NBTTagList();
        groups.appendTag(group);
        database.setTag("tradeGroups", groups);
        return database;
    }
}
