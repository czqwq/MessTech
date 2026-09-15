package com.MessTech.common.items;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.MessTech.common.entity.MTEntityPiggy;
import com.MessTech.common.util.MTDynamicItemHelper;
import com.MessTech.common.util.MTDynamicItemHelper.Effect;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTUtility;

/**
 * "A Piggy" - a decoration item that shows one of the "dynamic" materials of GT5U at a time.
 * <p>
 * The look is picked by the damage value (see {@link MTDynamicItemHelper}), so shift + right click walks through the
 * list of {@link Effect}s: the plain pig, then Transcendent Metal, Infinity, MagMatter, Eternity, Universium and
 * Six-Phased Copper. Every effect brings its own pre-rendered icon (<code>items/pigs/pig&lt;Suffix&gt;.png</code>) and
 * its own renderer, both supplied by {@link MTDynamicItemHelper}. Universium brings one icon more, the face the
 * cosmic shader would otherwise paint its stars over (<code>items/pigs/pigUniversiumFace.png</code>); the item
 * registers it and the renderer draws it last, which is all the item has to do for it.
 * <p>
 * Right click throws it (the projectile is not consumed, one piggy lasts forever) and it can be worn in the helmet
 * slot, where {@code MTPiggyHatRenderer} draws the same look standing on the player's head - effect and animation
 * included.
 */
public class MTItemPiggy extends Item {

    /**
     * Helmet slot as {@link Item#isValidArmor} numbers them (0 helmet, 1 chest, 2 legs, 3 boots) - the vanilla head
     * slot, i.e. the one the pumpkin and skulls use. Note that this is not the same numbering as
     * {@code InventoryPlayer#armorItemInSlot}, where the helmet is slot 3; {@code MTPiggyHatRenderer} reads the
     * worn stack with that second numbering.
     */
    public static final int HELMET_ARMOR_SLOT = 0;

    /**
     * Icon path of the plain look, without the per-effect suffix the helper appends.
     * <p>
     * Note that there is no {@code items/} prefix: the item atlas resolves registered names against
     * {@code assets/messtech/textures/items/} by itself (the same way {@link MTFuelRod} registers
     * {@code messtech:FuelRod/...}), so this constant points at {@code textures/items/pigs/pig.png}.
     */
    public static final String TEXTURE_BASE = "pigs/pig";

    /** Icons, indexed by {@link Effect#ordinal()}. */
    private final IIcon[] icons = new IIcon[Effect.VALUES.length];

    public MTItemPiggy() {
        setUnlocalizedName("messtech.piggy");
        // One piggy is a pet, not a consumable: throwing it does not use it up (see
        // onItemRightClick), so there is never a reason to carry a stack of them.
        setMaxStackSize(1);
        // The damage value is the effect index, not a durability, so every effect is a separate variant.
        setHasSubtypes(true);
        setCreativeTab(CreativeTabs.tabMisc);
        setMaxDamage(0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        IIcon[] registered = MTDynamicItemHelper.registerIcons(register, TEXTURE_BASE);
        System.arraycopy(registered, 0, icons, 0, icons.length);
        // The Universium look is a shader that replaces the icon's colour with a star field, so the pig's face is
        // handed over separately and the renderer draws it back on top of the sky (pigUniversiumFace.png).
        MTDynamicItemHelper.registerUniversiumOverlay(this, register, TEXTURE_BASE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return icons[MTDynamicItemHelper.getEffect(stack)
            .ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icons[Effect.byIndex(damage)
            .ordinal()];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + MTDynamicItemHelper.getEffect(stack).langSuffix;
    }

    /**
     * One stack per effect, so the creative tab and NEI show every look instead of only the first one.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        for (Effect effect : Effect.VALUES) {
            list.add(new ItemStack(item, 1, effect.ordinal()));
        }
    }

    /**
     * Makes the piggy wearable on the head.
     * <p>
     * Vanilla only allows armour, pumpkins and skulls in the helmet slot, and it asks the item itself
     * ({@code ContainerPlayer.SlotArmor#isItemValid} calls {@link Item#isValidArmor}); the hook's slot numbering
     * starts at the helmet, so {@code 0} is the head and nothing else. That is the same hook the pumpkin uses, so
     * the piggy behaves exactly like a decorative hat: no armour points, no durability, just the look.
     * <p>
     * What is drawn up there is {@link MTPiggyHatRenderer}, which reads the stack back out of the helmet slot and
     * hands it to {@link MTDynamicItemHelper#renderOnHead}, so the hat is the item's own effect, animation and all.
     */
    @Override
    public boolean isValidArmor(ItemStack stack, int armorType, Entity entity) {
        return armorType == HELMET_ARMOR_SLOT;
    }

    /**
     * A plain right click throws the piggy at whatever the player is looking at, shift + right click walks through
     * the looks instead. The thrown piggy is not used up: one piggy lasts forever, which is also why it stacks to
     * one (see the constructor). The damage value (i.e. the effect) of the thrown stack travels with the
     * projectile, so the piggy in the air looks exactly like the one that was held.
     * <p>
     * The effect is written on both sides for the cycle, so the server stays authoritative and the change survives a
     * relog; only the server plays the oink, so it is heard once.
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            Effect effect = MTDynamicItemHelper.cycleEffect(stack);

            if (!world.isRemote) {
                world.playSoundAtEntity(player, "mob.pig.say", 0.6F, 1.4F);
            } else {
                GTUtility.sendChatToPlayer(
                    player,
                    StatCollector.translateToLocalFormatted("messtech.piggy.switched", effect.getDisplayName()));
            }

            return stack;
        }

        // Vanilla throw style: the sound plays on both sides, the entity is only spawned by the server. The stack
        // itself is returned untouched - the piggy comes back to the owner by never leaving the inventory.
        world.playSoundAtEntity(player, "random.bow", 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));
        if (!world.isRemote) {
            world.spawnEntityInWorld(new MTEntityPiggy(world, player, stack.getItemDamage()));
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("messtech.piggy.effect")
                + ": "
                + EnumChatFormatting.RESET
                + MTDynamicItemHelper.getEffect(stack)
                    .getDisplayName());
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("messtech.piggy.tooltip"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("messtech.piggy.hat"));
    }
}
