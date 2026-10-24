package com.MessTech.common.items;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.MessTech.common.items.tools.ToolRainbowWrench;
import com.MessTech.common.material.MTRainbow;
import com.MessTech.init.MessTech;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.ToolDictNames;
import gregtech.api.items.MetaGeneratedTool;

/**
 * The Rainbow Wrench (彩虹小扳手): a GT wrench as a {@link MetaGeneratedTool} entry, the same way GT's own wrenches live
 * in {@code MetaGeneratedTool01}.
 * <p>
 * GT turns every registered tool ID into: the wrench behaviour ({@link ToolRainbowWrench#onStatsAddedToTool}, inherited
 * from {@code ToolWrench}: dismantle machines, rotate blocks/pipes, AE2/IC2 wrenchables, mode switch), the standard
 * tooltip, the durability bar, the tool-box entry, and - because GT's client init registers
 * {@code MetaGeneratedToolRenderer} for every instance of this item class - the icon rendering. The item class itself
 * is registered by {@code GTGenericItem}'s constructor (as {@code gregtech:gt.metatool.messtech}), so it must not be
 * passed to {@code GameRegistry#registerItem} again.
 * <p>
 * <b>The three numbers.</b> The wrench's stats are not material-derived, so they are pinned in two places:
 * <ul>
 * <li>harvest level 325 and mining speed 999 live on {@link ToolRainbowWrench} ({@code getBaseQuality} /
 * {@code getSpeedMultiplier}); the primary material {@link MTRainbow} keeps its own quality at 0 and speed at 1.0 so
 * these are exactly what the tooltip shows;</li>
 * <li>durability exactly {@link Integer#MAX_VALUE} can only live in the stack's {@code GT.ToolStats.MaxDamage} NBT
 * (GT derives it as {@code 100 * material durability * multiplier}), so {@link #prepareStack(ItemStack)} writes it.
 * </li>
 * </ul>
 * <b>Why {@link #isItemStackUsable} is overridden.</b> {@code MetaGeneratedTool#getSubItems} is final and always feeds
 * the creative tab (and so NEI) the bare stack plus a Neutronium-material copy, neither of which carries the rainbow
 * material. The only hook that sees both of them - and every stack made later by {@code getToolWithStats}, which calls
 * {@code isItemStackUsable} itself - is that method, and GT already uses it for NBT fix-up (enchantments), so the
 * preparation is idempotent and runs before the tooltip/renderer read the stats.
 */
public class MTRainbowWrench extends MetaGeneratedTool {

    /**
     * The tool ID (metadata of the item). Even, like every GT tool ID: the odd value right above it is the "empty"
     * (uncharged) state. This item has no other entries, so the number is only internal.
     */
    public static final int ID = 100;

    /** The tool's exact durability, written to {@code GT.ToolStats.MaxDamage}. */
    public static final int DURABILITY = Integer.MAX_VALUE;

    private static final String NBT_TOOL_STATS = "GT.ToolStats";
    private static final String NBT_PRIMARY_MATERIAL = "PrimaryMaterial";
    private static final String NBT_SECONDARY_MATERIAL = "SecondaryMaterial";
    private static final String NBT_MAX_DAMAGE = "MaxDamage";
    private static final String NBT_DAMAGE = "Damage";
    private static final String NBT_MODE = "Mode";

    /**
     * Set once when the material is missing, so a broken registration is reported instead of silently doing nothing.
     */
    private static boolean missingMaterialReported;

    public MTRainbowWrench() {
        super("metatool.messtech");
        // The item itself was already registered by GTGenericItem's constructor; adding the tool gives it its stats and
        // the ore dictionary name, registerWrench puts it on GT's wrench list (machines, tool box, pipe overlay, ...).
        GregTechAPI.registerWrench(
            addTool(
                ID,
                "Rainbow Wrench",
                "Hold left-click to dismantle machines",
                new ToolRainbowWrench(),
                ToolDictNames.craftingToolWrench));
    }

    /**
     * Builds the obtainable rainbow wrench: a stack of {@link #ID} with the Rainbow material and every stat pinned.
     * The creative tab / NEI do not go through this - {@link #isItemStackUsable} fixes those stacks up instead.
     */
    public ItemStack createStack() {
        Materials material = MTRainbow.getMaterial();
        if (material == null) return null;
        ItemStack stack = getToolWithStats(ID, 1, material, material, null);
        prepareStack(stack);
        return stack;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Also normalises the NBT of every Rainbow Wrench stack it sees, see the class javadoc. The preparation is
     * idempotent: once the tags are right it does not write anything.
     */
    @Override
    public boolean isItemStackUsable(ItemStack aStack) {
        if (aStack != null && aStack.getItem() == this && aStack.getItemDamage() == ID) {
            prepareStack(aStack);
        }
        return super.isItemStackUsable(aStack);
    }

    /**
     * Pins the rainbow stats into a stack's {@code GT.ToolStats} NBT: the Rainbow primary material, the exact
     * {@link #DURABILITY} and the two tags GT's tool code assumes exist.
     */
    public static void prepareStack(ItemStack aStack) {
        if (aStack == null || !(aStack.getItem() instanceof MTRainbowWrench) || aStack.getItemDamage() != ID) return;
        Materials material = MTRainbow.getMaterial();
        if (material == null) {
            if (!missingMaterialReported) {
                missingMaterialReported = true;
                MessTech.MT_LOG
                    .error("Rainbow Wrench: the Rainbow material is not registered, the wrench stays unusable");
            }
            return;
        }

        NBTTagCompound root = aStack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            aStack.setTagCompound(root);
        }
        boolean attached = root.hasKey(NBT_TOOL_STATS, 10);
        NBTTagCompound stats = root.getCompoundTag(NBT_TOOL_STATS);
        boolean changed = !attached;

        if (!material.mName.equals(stats.getString(NBT_PRIMARY_MATERIAL))) {
            stats.setString(NBT_PRIMARY_MATERIAL, material.mName);
            changed = true;
        }
        if (!material.mName.equals(stats.getString(NBT_SECONDARY_MATERIAL))) {
            stats.setString(NBT_SECONDARY_MATERIAL, material.mName);
            changed = true;
        }
        if (stats.getLong(NBT_MAX_DAMAGE) != DURABILITY) {
            stats.setLong(NBT_MAX_DAMAGE, DURABILITY);
            changed = true;
        }
        if (!stats.hasKey(NBT_DAMAGE)) {
            stats.setLong(NBT_DAMAGE, 0L);
            changed = true;
        }
        if (!stats.hasKey(NBT_MODE)) {
            stats.setByte(NBT_MODE, (byte) 0);
            changed = true;
        }

        if (changed) root.setTag(NBT_TOOL_STATS, stats);
    }
}
