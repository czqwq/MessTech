package com.MessTech.common.items.tools;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.common.tools.ToolWrench;

/**
 * The stats of the Rainbow Wrench (彩虹小扳手).
 * <p>
 * Everything a GT wrench does - dismantling machines by left-click, rotating blocks and pipes, AE2/IC2 wrenchables, the
 * three modes - is {@link ToolWrench}'s and is inherited unchanged. Only the three numbers differ from a normal wrench,
 * and all three are read back by GT's own tooltip:
 * <ul>
 * <li><b>harvest level 325</b> ({@link #HARVEST_LEVEL}): GT computes {@code getBaseQuality() + material.mToolQuality},
 * so the material ({@link com.MessTech.common.material.MTRainbow}) keeps its quality at 0 and the whole 325 comes from
 * here. 325 is above every block's harvest level, so the wrench mines anything that its {@code isMinableBlock} accepts
 * -
 * "lvl 325" in the tooltip.</li>
 * <li><b>mining speed 999</b> ({@link #MINING_SPEED}): GT computes {@code getSpeedMultiplier() * material.mToolSpeed},
 * and the material keeps its speed at 1.0, so 999 is exactly what the tooltip shows.</li>
 * <li><b>durability {@link Integer#MAX_VALUE}</b>: not in here - {@code GT.ToolStats.MaxDamage} is written by
 * {@link com.MessTech.common.items.MTRainbowWrench#prepareStack(ItemStack)}, since GT derives the durability from the
 * material and only the stack NBT can hold the exact value.</li>
 * </ul>
 * The icon is GT's custom item-icon container for {@code assets/messtech/textures/items/tools/rainbowWrench.png}, an
 * animated item texture: 7 frames of 16x16 in a vertical strip, 100 ms per frame (the source GIF's own delay), i.e.
 * {@code frametime: 2} in the {@code .mcmeta}. {@link #getRGBa} returns white so the frames' own rainbow colours are
 * not tinted.
 */
public class ToolRainbowWrench extends ToolWrench {

    /** Harvest level of the tooltip: the material's tool quality (0) plus this. */
    public static final int HARVEST_LEVEL = 325;

    /** Mining speed of the tooltip: the material's tool speed (1.0) times this. */
    public static final float MINING_SPEED = 999.0F;

    /** The animated wrench icon; registered to GT's item icon atlas when this class first loads (our preInit). */
    public static final IIconContainer ICON = Textures.ItemIcons.custom("messtech", "tools/rainbowWrench");

    /** White modulation: the icon already carries its own colours. */
    private static final short[] WHITE = { 255, 255, 255, 255 };

    @Override
    public int getBaseQuality() {
        return HARVEST_LEVEL;
    }

    @Override
    public float getSpeedMultiplier() {
        return MINING_SPEED;
    }

    @Override
    public IIconContainer getIcon(boolean aIsToolHead, ItemStack aStack) {
        // The whole tool is one icon; there is no separate handle/rod layer to draw.
        return aIsToolHead ? ICON : null;
    }

    @Override
    public short[] getRGBa(boolean aIsToolHead, ItemStack aStack) {
        return aIsToolHead ? WHITE : null;
    }
}
