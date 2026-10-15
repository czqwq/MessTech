package com.MessTech.common.items;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;

import com.MessTech.common.entity.MTEntityVoidstarArrow;
import com.MessTech.common.particle.MTVoidstarFx;
import com.MessTech.common.util.MTBowKill;
import com.MessTech.init.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Voidstar Piercer (混沌终焉·虚无贯星弓): a bow whose arrows fly dead straight and blast a tunnel through
 * everything on the line they flew - the blocks are destroyed and everything alive inside it is killed for good by
 * {@link MTBowKill}, the bow's branch of the true kill ladder. Letting go also heals the archer back to full.
 * <p>
 * It is a {@link ItemBow} and keeps every part of the vanilla bow's behaviour that is not its projectile: the
 * minimum draw that fires at all, the arrow (and the Infinity enchantment that replaces it), the Power, Punch and
 * Flame enchantments, the durability and the enchantment table. What it changes:
 * <ul>
 * <li>{@link #onPlayerStoppedUsing} launches an {@link MTEntityVoidstarArrow} instead of a vanilla
 * {@code EntityArrow}.</li>
 * <li><b>Its draw is longer than vanilla's.</b> A full draw takes {@link Config#VOIDSTAR_BOW_DRAW_TICKS} ticks,
 * four seconds by default, and vanilla's speed curve is stretched over that instead of over twenty ticks. The
 * minimum draw that fires at all moves with it (the same curve, so the same fifth of the draw, about 0.7 seconds).
 * The magic circle unfolds over exactly this time.</li>
 * <li>Its magic circle is not this class's business either: {@code MTMagicCircleRenderer} draws it in world space
 * while the bow is drawn (it reads the same use state this item sets up).</li>
 * <li><b>Letting go heals the archer</b> back to full health ({@link Config#VOIDSTAR_BOW_HEALS_SHOOTER}), on the
 * server, in the same branch that spawns the arrow - so a shot that never happens (a draw too weak to fire, no arrow
 * in the inventory) heals nothing. Everything the arrow kills dies through {@link MTBowKill} instead, the bow's
 * branch of the true kill ladder, so no kill of this weapon depends on that heal any more.</li>
 * <li>Its icons are three frame strips: {@code bow/voidstarBow_standby} and {@code _pulling_0/1/2}, each 16 frames
 * of an animated, enchanted void-metal bow with a shimmer travelling through the art (see the {@code .mcmeta} next
 * to each PNG).</li>
 * <li>It always carries the enchantment glint ({@link #hasEffect}), because the bow "is" enchanted: the glint is
 * the moving half of the enchantment look vanilla draws over an enchanted item, and the frame strips are the
 * other.</li>
 * <li>Its name wears the {@code STARFIELD} tooltip animation, registered in {@code ClientProxy.preInit} with
 * {@link com.MessTech.common.util.MTAnimatedTooltipHandler#addItemNameAnimation}. That registration has to use
 * {@code OreDictionary.WILDCARD_VALUE} as the stack's damage: the damage of a damageable stack is its durability
 * damage, and {@code ItemStackMap} keys on it, so a registration at damage 0 stops matching the bow the moment it
 * is fired once.</li>
 * </ul>
 * The pull icons have to come from {@link #getIcon(ItemStack, int, EntityPlayer, ItemStack, int)}: the icon of the
 * bow a player is drawing is picked by {@code EntityPlayer#getItemIcon}, and that method only knows vanilla's own
 * bow by identity ({@code itemStackIn.getItem() == Items.bow}) before it hands over to Forge's player sensitive
 * icon hook. A subclass of {@code ItemBow} is therefore never asked for an icon by the vanilla branch, and the
 * hook is the one place the draw can be seen.
 */
public class MTItemVoidstarBow extends ItemBow {

    /** Icon path of the frame strips, relative to {@code textures/items} (no {@code items/} prefix). */
    public static final String TEXTURE_BASE = "bow/voidstarBow";

    /** Pull frame strips: {@code _pulling_0} is the first pixel of the draw, {@code _pulling_2} a full one. */
    private static final int PULL_FRAMES = 3;

    /** Durability: 1,024 shots, i.e. under three stacks of arrows. */
    public static final int MAX_DAMAGE = 1024;

    /**
     * Draw of the pull frames, as fractions of a full draw ({@link Config#VOIDSTAR_BOW_DRAW_TICKS}): vanilla's own
     * bow switches at 18 and 13 of its 20 ticks, i.e. at 90% and 65% of the draw, and the same two fractions are kept
     * here so the frames are spread over whatever draw the config asks for instead of over twenty ticks.
     */
    private static final float PULL_FULL_FRACTION = 0.90F, PULL_MID_FRACTION = 0.65F;

    /** Icons, indexed as {@code [standby, pulling_0, pulling_1, pulling_2]}. */
    private final IIcon[] icons = new IIcon[1 + PULL_FRAMES];

    /**
     * NBT key of the sub-mode of a bow, and the modes themselves. The mode belongs to the stack, not to the player:
     * two bows in one inventory can be in different modes, and the mode survives a relog like any other NBT.
     */
    public static final String MODE_TAG = "MTVoidstarMode";

    /** 贯星: the tunnel - the arrow drills through everything and never stops on the first wall. */
    public static final int MODE_PIERCE = 0;

    /** DE: one reactor explosion where the arrow hits, and the arrow is gone. */
    public static final int MODE_DE = 1;

    /** Number of sub-modes; the switch cycles through {@code 0 .. MODE_COUNT - 1}. */
    public static final int MODE_COUNT = 2;

    public MTItemVoidstarBow() {
        setUnlocalizedName("messtech.voidstarBow");
        // One bow is a weapon, not a stack of them; ItemBow's own constructor sets the same value, this is the
        // explicit statement of the item's specification.
        setMaxStackSize(1);
        setMaxDamage(MAX_DAMAGE);
        setCreativeTab(CreativeTabs.tabCombat);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons[0] = register.registerIcon(MTItems.TEXTURE_DOMAIN + ":" + TEXTURE_BASE + "_standby");
        for (int pull = 0; pull < PULL_FRAMES; pull++) {
            icons[1 + pull] = register.registerIcon(MTItems.TEXTURE_DOMAIN + ":" + TEXTURE_BASE + "_pulling_" + pull);
        }
        // Vanilla's own field as well, so anything reading it directly (rather than through getIconIndex) sees the
        // bow instead of a null icon.
        this.itemIcon = icons[0];

        // The magic circle and the arrow trail are drawn from this atlas too (item atlas = particle layer 2).
        MTVoidstarFx.registerIcons(register);
    }

    /** The bow at rest. The pull frames are picked by the player sensitive hook below. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icons[0];
    }

    /**
     * The icon of the bow while it is being drawn: Forge's player sensitive hook, which is the one
     * {@code EntityPlayer#getItemIcon} reaches for an item that is not vanilla's own bow (see the class comment).
     * The two cut-offs are vanilla's fractions of the draw (18 and 13 of its 20 ticks), applied to this bow's own
     * draw length, so the three frames are spread over the whole charge however long it is.
     */
    @Override
    public IIcon getIcon(ItemStack stack, int pass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
        if (usingItem != null && usingItem.getItem() == this) {
            int drawn = usingItem.getMaxItemUseDuration() - useRemaining;
            int fullDraw = Math.max(1, Config.VOIDSTAR_BOW_DRAW_TICKS);
            if (drawn >= PULL_FULL_FRACTION * fullDraw) return icons[3];
            if (drawn > PULL_MID_FRACTION * fullDraw) return icons[2];
            if (drawn > 0) return icons[1];
        }
        return getIconIndex(stack);
    }

    /**
     * Vanilla's own "icon for this stage of the draw" method, which the hardcoded branch in
     * {@code EntityPlayer#getItemIcon} calls on {@code Items.bow}. Nothing calls it for this item, but a mod that
     * asks the same question (JEI-like previews, a renderer of its own) gets the right answer.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getItemIconForUseDuration(int pull) {
        return icons[1 + MathHelper.clamp_int(pull, 0, PULL_FRAMES - 1)];
    }

    /**
     * The bow is enchanted: vanilla draws the purple glint over an item whose {@code hasEffect} answers true, and
     * this one always does, drawn bow or not. Enchanting it for real on top (Power, Infinity, ...) adds the
     * enchantments themselves; the look does not change, it is already there.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    /** The rarity of the tooltip name: the bow is an endgame weapon, and it reads like one when it is unenchanted. */
    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    /**
     * Sneaking with the bow in hand switches its sub-mode instead of drawing it, and the switch is announced in the
     * chat. It is the same call on both sides, but only the server writes the mode - a client that wrote it would
     * hold a mode the server does not have, and the next item sync would overwrite it anyway.
     * <p>
     * The sneak branch must not fall through to {@code super}: vanilla's {@code ItemBow} starts the draw right here
     * ({@code player.setItemInUse}), and a switch that also started the draw would fire an arrow the moment the
     * player let go of shift. Both sides return early, so neither of them begins a use.
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                int mode = cycleMode(stack);
                // A translation component, not a translated string: the key travels to the client and every player
                // reads the message in their own language (the client does the translating, not the server).
                player.addChatMessage(new ChatComponentTranslation(modeMessageKey(mode)));
            }
            return stack;
        }

        return super.onItemRightClick(stack, world, player);
    }

    /**
     * The sub-mode of a bow, from its own NBT. Anything missing or out of range reads as
     * {@link #MODE_PIERCE} - a bow that has never been switched fires the way it did before the modes existed.
     */
    public static int getMode(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return MODE_PIERCE;

        int mode = stack.stackTagCompound.getInteger(MODE_TAG);
        return mode < 0 || mode >= MODE_COUNT ? MODE_PIERCE : mode;
    }

    /** Switches a bow to the given sub-mode. */
    public static void setMode(ItemStack stack, int mode) {
        if (stack == null) return;

        if (stack.stackTagCompound == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.stackTagCompound.setInteger(MODE_TAG, MathHelper.clamp_int(mode, 0, MODE_COUNT - 1));
    }

    /**
     * Switches a bow to the next sub-mode and answers which one that is.
     *
     * @return the new mode, i.e. {@code (old + 1) % MODE_COUNT}
     */
    public static int cycleMode(ItemStack stack) {
        int mode = (getMode(stack) + 1) % MODE_COUNT;
        setMode(stack, mode);
        return mode;
    }

    /** @return the translation key that announces a switch to this mode; see the {@code .mode.} keys of the lang. */
    public static String modeMessageKey(int mode) {
        return "messtech.voidstarBow.mode." + (mode == MODE_DE ? "de" : "pierce");
    }

    /** @return the translation key of the name of a mode, as the tooltip and the switch message show it. */
    public static String modeNameKey(int mode) {
        return "messtech.voidstarBow.modeName." + (mode == MODE_DE ? "de" : "pierce");
    }

    /**
     * Fires the arrow. This is vanilla's {@code ItemBow#onPlayerStoppedUsing} with two differences: the projectile
     * is an {@link MTEntityVoidstarArrow} (created server side only - every client gets it from the spawn packet,
     * with the muzzle position in its data watcher), and the arrow is never pickable, so it is not given back to
     * the shooter.
     * <p>
     * Everything else is kept as it is, including the events: {@link ArrowLooseEvent} can still cancel the shot,
     * the Infinity enchantment still saves the arrow, and the Power, Punch and Flame enchantments still apply to
     * the projectile.
     */
    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int useRemaining) {
        int drawn = getMaxItemUseDuration(stack) - useRemaining;

        ArrowLooseEvent event = new ArrowLooseEvent(player, stack, drawn);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return;
        }
        drawn = event.charge;

        boolean infinite = player.capabilities.isCreativeMode
            || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack) > 0;

        if (!infinite && !player.inventory.hasItem(Items.arrow)) {
            return;
        }

        // Vanilla's curve, over this bow's own draw length instead of vanilla's twenty ticks: the speed of the
        // arrow and the range of the tunnel both read the result. See Config#VOIDSTAR_BOW_DRAW_TICKS.
        float draw = (float) drawn / Math.max(1, Config.VOIDSTAR_BOW_DRAW_TICKS);
        draw = (draw * draw + draw * 2.0F) / 3.0F;

        // Vanilla's floor: a bow that was barely drawn does not fire at all.
        if ((double) draw < 0.1D) {
            return;
        }
        if (draw > 1.0F) {
            draw = 1.0F;
        }

        if (!world.isRemote) {
            MTEntityVoidstarArrow arrow = new MTEntityVoidstarArrow(world, player, draw, getMode(stack));

            if (draw == 1.0F) {
                arrow.setIsCritical(true);
            }

            int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
            if (power > 0) {
                arrow.setDamage(arrow.getDamage() + (double) power * 0.5D + 0.5D);
            }

            int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
            if (punch > 0) {
                arrow.setKnockbackStrength(punch);
            }

            if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) > 0) {
                arrow.setFire(100);
            }

            world.spawnEntityInWorld(arrow);

            // The bow heals whoever draws it: letting go hands back everything the charge cost them. It is a real
            // heal of the difference, not a health assignment, so it goes through the game's own heal path (the
            // Forge heal event and the healing effects included) and can never push the archer over the maximum.
            if (Config.VOIDSTAR_BOW_HEALS_SHOOTER) {
                player.heal(Math.max(0.0F, player.getMaxHealth() - player.getHealth()));
            }
        }

        stack.damageItem(1, player);
        world.playSoundAtEntity(player, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + draw * 0.5F);

        if (!infinite) {
            player.inventory.consumeInventoryItem(Items.arrow);
        }
    }

    /**
     * The tooltip: what the item does, in the same shape the machine tooltips use - a bullet per mechanic, with
     * the colours in the lang file and one line per key here. The charge line is the one formatted line, because its
     * number is {@link Config#VOIDSTAR_BOW_DRAW_TICKS}: a tooltip that named a hardcoded four seconds would start
     * lying the moment somebody changed the config.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        // The sub-mode first: which of the two weapons this bow currently is decides how every line below reads.
        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "messtech.voidstarBow.tooltip.mode",
                StatCollector.translateToLocal(modeNameKey(getMode(stack)))));
        tooltip.add(StatCollector.translateToLocalFormatted("messtech.voidstarBow.tooltip.charge", drawnSeconds()));
        tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.flight"));
        if (getMode(stack) == MODE_DE) {
            tooltip.add(StatCollector.translateToLocalFormatted("messtech.voidstarBow.tooltip.deBlast", dePowerText()));
        } else {
            tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.pierce"));
        }
        tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.kill"));
        if (Config.VOIDSTAR_BOW_HEALS_SHOOTER) {
            tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.heal"));
        }
        tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.detail"));
    }

    /**
     * The configured draw length as the tooltip shows it: whole seconds when it is a whole number of them (80 ticks
     * reads "4"), one decimal otherwise.
     */
    private static String drawnSeconds() {
        int ticks = Math.max(1, Config.VOIDSTAR_BOW_DRAW_TICKS);
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    /** The configured DE power as the tooltip shows it, in the same shape {@link #drawnSeconds()} uses. */
    private static String dePowerText() {
        float power = Config.VOIDSTAR_BOW_DE_POWER;
        if (power == (float) (int) power) {
            return Integer.toString((int) power);
        }
        return String.format(Locale.ROOT, "%.1f", power);
    }
}
