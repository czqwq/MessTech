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
 * "A Piggy" - a decoration item showing one of GT5U's "dynamic" materials at a time. Damage value picks the look (see
 * {@link MTDynamicItemHelper}): shift + right click walks the {@link Effect}s - plain pig, Transcendent Metal,
 * Infinity, MagMatter, Eternity, Universium, Six-Phased Copper - each with its own pre-rendered icon
 * (<code>items/pigs/pig&lt;Suffix&gt;.png</code>) and renderer. Universium adds one icon, the face the cosmic shader
 * would paint its stars over (<code>items/pigs/pigUniversiumFace.png</code>), registered here and drawn last by the
 * renderer.
 * <p>
 * Right click throws it (not consumed: one piggy lasts forever); it also fits the helmet slot, where
 * {@code MTPiggyHatRenderer} draws the same look on the player's head, effect and animation included.
 */
public class MTItemPiggy extends Item {

    /**
     * Helmet slot in {@link Item#isValidArmor}'s numbering (0 helmet, 1 chest, 2 legs, 3 boots): the vanilla head slot
     * the pumpkin and skulls use. Not the {@code InventoryPlayer#armorItemInSlot} numbering (helmet = 3), which
     * {@code MTPiggyHatRenderer} uses to read the worn stack.
     */
    public static final int HELMET_ARMOR_SLOT = 0;

    /**
     * Icon path of the plain look, without the per-effect suffix the helper appends. No {@code items/} prefix: the
     * item atlas resolves names against {@code assets/messtech/textures/items/} itself (as {@link MTFuelRod} registers
     * {@code messtech:FuelRod/...}), so this is {@code textures/items/pigs/pig.png}.
     */
    public static final String TEXTURE_BASE = "pigs/pig";

    private final IIcon[] icons = new IIcon[Effect.VALUES.length];

    public MTItemPiggy() {
        setUnlocalizedName("messtech.piggy");
        // A pet, not a consumable: throwing does not use it up (see onItemRightClick), so a stack is never needed.
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
        // The Universium look is a shader replacing the icon's colour with a star field, so the pig's face is handed
        // over separately and the renderer draws it back on top of the sky (pigUniversiumFace.png).
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
     * Wearable on the head: vanilla allows armour, pumpkins and skulls there and asks the item itself
     * ({@code ContainerPlayer.SlotArmor#isItemValid} calls {@link Item#isValidArmor}), numbering from the helmet, so
     * {@code 0} is the head. A decorative hat with no armour points, no durability, just the look -
     * {@link MTPiggyHatRenderer} hands the stack to {@link MTDynamicItemHelper#renderOnHead}, so the hat is the item's
     * own effect, animation and all.
     */
    @Override
    public boolean isValidArmor(ItemStack stack, int armorType, Entity entity) {
        return armorType == HELMET_ARMOR_SLOT;
    }

    /**
     * Plain right click throws the piggy where the player looks; shift + right click walks the looks. The thrown piggy
     * is not used up (hence stacking to one, see the constructor) and its damage value travels with the projectile, so
     * the piggy in the air looks like the one that was held. The cycle is written on both sides, so the server stays
     * authoritative and the change survives a relog; only the server plays the oink, so it is heard once.
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

        // Vanilla throw style: sound on both sides, entity spawned by the server only. The stack is returned
        // untouched - the piggy comes back by never leaving the inventory.
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
