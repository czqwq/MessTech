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
 * The Voidstar Piercer (混沌终焉·虚无贯星弓): dead-straight arrows that blast a tunnel through everything on the
 * line - blocks destroyed, everything alive killed for good by {@link MTBowKill} (the bow's branch of the true kill
 * ladder).
 * <p>
 * Beyond vanilla's {@link ItemBow} behaviour:
 * <ul>
 * <li><b>Longer draw:</b> {@link Config#VOIDSTAR_BOW_DRAW_TICKS} ticks (four seconds by default) with vanilla's speed
 * curve stretched over it instead of twenty ticks, the draw minimum moving with it (same curve, same fifth, about
 * 0.7 s). {@code MTMagicCircleRenderer} unfolds the circle over exactly that time, in world space, from this item's
 * use state.</li>
 * <li><b>Release heals the archer</b> to full ({@link Config#VOIDSTAR_BOW_HEALS_SHOOTER}), server side, in the arrow
 * branch - a shot that never happens (draw too weak, no arrow) heals nothing; kills use {@link MTBowKill}.</li>
 * <li>Always glinting ({@link #hasEffect}); the name wears the {@code magnetar} text effect: its lang entry carries the
 * inline {@code §[mg]} marker (see {@code docs/text-fx/}), so every place the font draws the name gets the field-line
 * shader - no tooltip handler involved.</li>
 * </ul>
 * Icons are three 16-frame strips ({@code bow/voidstarBow_standby}, {@code _pulling_0/1/2}), an animated enchanted
 * void-metal bow with a shimmer travelling through the art (see the {@code .mcmeta}). The pull icons must come from
 * {@link #getIcon(ItemStack, int, EntityPlayer, ItemStack, int)}: {@code EntityPlayer#getItemIcon} hardcodes vanilla's
 * bow identity ({@code itemStackIn.getItem() == Items.bow}), so only Forge's player sensitive hook sees a
 * {@code ItemBow} subclass' draw.
 */
public class MTItemVoidstarBow extends ItemBow {

    /** Icon path of the frame strips, relative to {@code textures/items} (no {@code items/} prefix). */
    public static final String TEXTURE_BASE = "bow/voidstarBow";

    /** Pull frames: {@code _pulling_0} is the first pixel of the draw, {@code _pulling_2} a full one. */
    private static final int PULL_FRAMES = 3;

    /** Durability: 1,024 shots, i.e. under three stacks of arrows. */
    public static final int MAX_DAMAGE = 1024;

    /**
     * Pull-frame cut-offs as fractions of a full draw ({@link Config#VOIDSTAR_BOW_DRAW_TICKS}): vanilla switches at 18
     * and 13 of its 20 ticks, i.e. 90% and 65%, kept here so the frames spread over whatever draw the config asks for.
     */
    private static final float PULL_FULL_FRACTION = 0.90F, PULL_MID_FRACTION = 0.65F;

    private final IIcon[] icons = new IIcon[1 + PULL_FRAMES];

    /**
     * NBT key of the sub-mode, and the modes. The mode belongs to the stack, not the player: two bows in one inventory
     * can differ, and it survives a relog like any other NBT.
     */
    public static final String MODE_TAG = "MTVoidstarMode";

    /** 贯星: the tunnel - the arrow drills through everything and never stops on the first wall. */
    public static final int MODE_PIERCE = 0;

    /** DE: one reactor explosion where the arrow hits, and the arrow is gone. */
    public static final int MODE_DE = 1;

    public static final int MODE_COUNT = 2;

    public MTItemVoidstarBow() {
        setUnlocalizedName("messtech.voidstarBow");
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
        // Vanilla's own field too, so direct readers (not via getIconIndex) see the bow, not a null icon.
        this.itemIcon = icons[0];

        // The magic circle and the arrow trail are drawn from this atlas too (item atlas = particle layer 2).
        MTVoidstarFx.registerIcons(register);
    }

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

    /** Icon while the bow is drawn: the class comment's player sensitive hook, with vanilla's cut-offs. */
    @Override
    public IIcon getIcon(ItemStack stack, int pass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
        if (usingItem != null && usingItem.getItem() == this) {
            int drawn = usingItem.getMaxItemUseDuration() - useRemaining;
            int fullDraw = Math.max(1, Config.voidstarbow.VOIDSTAR_BOW_DRAW_TICKS);
            if (drawn >= PULL_FULL_FRACTION * fullDraw) return icons[3];
            if (drawn > PULL_MID_FRACTION * fullDraw) return icons[2];
            if (drawn > 0) return icons[1];
        }
        return getIconIndex(stack);
    }

    /**
     * Vanilla's "icon for this stage of the draw", which {@code EntityPlayer#getItemIcon} calls on {@code Items.bow}.
     * Nothing calls it here, but a mod asking the same question (JEI-like previews) gets the right answer.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getItemIconForUseDuration(int pull) {
        return icons[1 + MathHelper.clamp_int(pull, 0, PULL_FRAMES - 1)];
    }

    /**
     * The bow is enchanted: vanilla draws the purple glint over an item whose {@code hasEffect} answers true, and this
     * one always does, drawn or not, the frame strips being the moving half of that look. Real enchantments on top
     * (Power, Infinity, ...) only add themselves; the look is already there.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    /** Tooltip name rarity: an endgame weapon, and it reads like one even unenchanted. */
    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    /**
     * Sneaking with the bow in hand switches its sub-mode and announces it in the chat. Only the server writes the
     * mode - a client's would be overwritten by the next item sync anyway.
     * <p>
     * The sneak branch must not fall through to {@code super}: vanilla's {@code ItemBow} starts the draw right here
     * ({@code player.setItemInUse}), and a switch that also started it would fire an arrow when the player let go of
     * shift. Both sides return early, so neither begins a use.
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                int mode = cycleMode(stack);
                // A translation component, not a translated string: the key travels to the client, which translates
                // it per player.
                player.addChatMessage(new ChatComponentTranslation(modeMessageKey(mode)));
            }
            return stack;
        }

        return super.onItemRightClick(stack, world, player);
    }

    /**
     * The sub-mode from the bow's own NBT; anything missing or out of range reads as {@link #MODE_PIERCE}, so a bow
     * that was never switched fires as before the modes existed.
     */
    public static int getMode(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return MODE_PIERCE;

        int mode = stack.stackTagCompound.getInteger(MODE_TAG);
        return mode < 0 || mode >= MODE_COUNT ? MODE_PIERCE : mode;
    }

    public static void setMode(ItemStack stack, int mode) {
        if (stack == null) return;

        if (stack.stackTagCompound == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.stackTagCompound.setInteger(MODE_TAG, MathHelper.clamp_int(mode, 0, MODE_COUNT - 1));
    }

    public static int cycleMode(ItemStack stack) {
        int mode = (getMode(stack) + 1) % MODE_COUNT;
        setMode(stack, mode);
        return mode;
    }

    public static String modeMessageKey(int mode) {
        return "messtech.voidstarBow.mode." + (mode == MODE_DE ? "de" : "pierce");
    }

    public static String modeNameKey(int mode) {
        return "messtech.voidstarBow.modeName." + (mode == MODE_DE ? "de" : "pierce");
    }

    /**
     * Fire: vanilla's {@code ItemBow#onPlayerStoppedUsing} with two differences - an {@link MTEntityVoidstarArrow}
     * (server side only; clients get it from the spawn packet, muzzle position in its data watcher), and the arrow is
     * never pickable, so it is not given back. Everything else stays, events included: {@link ArrowLooseEvent} can
     * still cancel the shot, Infinity still saves the arrow, Power/Punch/Flame still apply.
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

        // Vanilla's curve over this bow's own draw length instead of twenty ticks: arrow speed and tunnel range read
        // the result. See Config#VOIDSTAR_BOW_DRAW_TICKS.
        float draw = (float) drawn / Math.max(1, Config.voidstarbow.VOIDSTAR_BOW_DRAW_TICKS);
        draw = (draw * draw + draw * 2.0F) / 3.0F;

        // Vanilla's floor: a barely drawn bow does not fire at all.
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

            // Release hands back everything the charge cost: a real heal of the difference, not a health assignment, so
            // it goes through the game's heal path (Forge heal event, healing effects) and never exceeds the maximum.
            if (Config.voidstarbow.VOIDSTAR_BOW_HEALS_SHOOTER) {
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
     * The tooltip: one bullet per mechanic, colours in the lang file, one line per key here. The charge line is
     * formatted because its number is {@link Config#VOIDSTAR_BOW_DRAW_TICKS} - a hardcoded four seconds would start
     * lying when the config changes.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        // Sub-mode first: which of the two weapons this bow is decides how every line below reads.
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
        if (Config.voidstarbow.VOIDSTAR_BOW_HEALS_SHOOTER) {
            tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.heal"));
        }
        tooltip.add(StatCollector.translateToLocal("messtech.voidstarBow.tooltip.detail"));
    }

    /**
     * The configured draw length as the tooltip shows it: whole seconds when it is one (80 ticks reads "4"), one
     * decimal otherwise.
     */
    private static String drawnSeconds() {
        int ticks = Math.max(1, Config.voidstarbow.VOIDSTAR_BOW_DRAW_TICKS);
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    /** The configured DE power as the tooltip shows it, like {@link #drawnSeconds()}. */
    private static String dePowerText() {
        float power = Config.voidstarbow.VOIDSTAR_BOW_DE_POWER;
        if (power == (float) (int) power) {
            return Integer.toString((int) power);
        }
        return String.format(Locale.ROOT, "%.1f", power);
    }
}
