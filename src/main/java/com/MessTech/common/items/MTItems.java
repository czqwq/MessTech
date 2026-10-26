package com.MessTech.common.items;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

public final class MTItems {

    /** Texture domain of the fuel rods (files live in {@code assets/messtech/textures/items/FuelRod/}). */
    public static final String TEXTURE_DOMAIN = "messtech";

    // region Transcendent Metal fuel rods

    /**
     * IC2 pulse maths for a bare rod: each of the {@code cells} passes adds {@code 1 + cells / 2} pulses, each pulse
     * adds {@code energy}, so a lone rod makes {@code energy * cells * (1 + cells / 2) * 5} EU/t. The energies below
     * scale with rod size instead of one shared base value:
     * <ul>
     * <li>single: 9,000,000 x 1 x 1 x 5 = 45,000,000 EU/t (1.34A UIV, the "at least 1A UIV" floor)</li>
     * <li>dual: 4,500,000 x 2 x 2 x 5 = 90,000,000 EU/t (2x the single rod)</li>
     * <li>quad: 3,000,000 x 4 x 3 x 5 = 180,000,000 EU/t (4x the single rod)</li>
     * </ul>
     * Durability 250,000 cycles (2.5x The Core); MOX heat bonus unchanged.
     * <p>
     * Heat: per cycle a bare rod dumps {@code cells * triangular(1 + cells / 2) * heat} into neighbours and hull, so
     * heat 4,096 gives 4,096 / 24,576 / 98,304 HU per second for single / dual / quad. GT5U's
     * {@code ItemList.neutroniumHeatCapacitor} ("1G Neutronium Heat Capacitor", 1,000,000,000 HU) buffers a bare quad
     * rod for ~2.8 hours and the UIV heat control hatch alone for ~6 hours, so the heat factor can be this high
     * without making the rods unusable.
     */
    public static final int TRANSCENDENT_ROD_MAX_DAMAGE = 250_000;
    public static final float TRANSCENDENT_ROD_ENERGY_SINGLE = 9_000_000F;
    public static final float TRANSCENDENT_ROD_ENERGY_DUAL = 4_500_000F;
    public static final float TRANSCENDENT_ROD_ENERGY_QUAD = 3_000_000F;
    public static final int TRANSCENDENT_ROD_RADIATION = 32;
    public static final float TRANSCENDENT_ROD_HEAT = 4_096F;
    public static final boolean TRANSCENDENT_ROD_MOX = true;
    public static final float TRANSCENDENT_ROD_HEAT_BONUS = 2F;

    // Depleted rods are created first: the active rods need their ItemStack as the depletion result.
    public static final MTDepletedFuelRod rodTranscendentMetalDepleted = new MTDepletedFuelRod(
        "rodTranscendentMetalDepleted",
        "Fuel Rod (Depleted Transcendent Metal)",
        1,
        "FuelRod/RodTranscendentMetalDepleted");
    public static final MTDepletedFuelRod rodTranscendentMetalDepleted2 = new MTDepletedFuelRod(
        "rodTranscendentMetalDepleted2",
        "Dual Fuel Rod (Depleted Transcendent Metal)",
        1,
        "FuelRod/RodTranscendentMetalDepleted2");
    public static final MTDepletedFuelRod rodTranscendentMetalDepleted4 = new MTDepletedFuelRod(
        "rodTranscendentMetalDepleted4",
        "Quad Fuel Rod (Depleted Transcendent Metal)",
        1,
        "FuelRod/RodTranscendentMetalDepleted4");

    public static final MTFuelRod rodTranscendentMetal = new MTFuelRod(
        "rodTranscendentMetal",
        "Fuel Rod (Transcendent Metal)",
        1,
        TRANSCENDENT_ROD_MAX_DAMAGE,
        TRANSCENDENT_ROD_ENERGY_SINGLE,
        TRANSCENDENT_ROD_RADIATION,
        TRANSCENDENT_ROD_HEAT,
        new ItemStack(rodTranscendentMetalDepleted),
        TRANSCENDENT_ROD_MOX,
        TRANSCENDENT_ROD_HEAT_BONUS,
        "FuelRod/RodTranscendentMetal");
    public static final MTFuelRod rodTranscendentMetal2 = new MTFuelRod(
        "rodTranscendentMetal2",
        "Dual Fuel Rod (Transcendent Metal)",
        2,
        TRANSCENDENT_ROD_MAX_DAMAGE,
        TRANSCENDENT_ROD_ENERGY_DUAL,
        TRANSCENDENT_ROD_RADIATION,
        TRANSCENDENT_ROD_HEAT,
        new ItemStack(rodTranscendentMetalDepleted2),
        TRANSCENDENT_ROD_MOX,
        TRANSCENDENT_ROD_HEAT_BONUS,
        "FuelRod/RodTranscendentMetal2");
    public static final MTFuelRod rodTranscendentMetal4 = new MTFuelRod(
        "rodTranscendentMetal4",
        "Quad Fuel Rod (Transcendent Metal)",
        4,
        TRANSCENDENT_ROD_MAX_DAMAGE,
        TRANSCENDENT_ROD_ENERGY_QUAD,
        TRANSCENDENT_ROD_RADIATION,
        TRANSCENDENT_ROD_HEAT,
        new ItemStack(rodTranscendentMetalDepleted4),
        TRANSCENDENT_ROD_MOX,
        TRANSCENDENT_ROD_HEAT_BONUS,
        "FuelRod/RodTranscendentMetal4");

    // endregion

    public static final MECreativeOreDisk meCreativeOreDisk = new MECreativeOreDisk();
    public static final MTNACComponentItem nacComponentItem = new MTNACComponentItem();
    public static final ItemMessFood messFood = new ItemMessFood();

    public static final MTItemPiggy piggy = new MTItemPiggy();

    /** The Voidstar Piercer (混沌终焉·虚无贯星弓): a bow whose arrows blast a tunnel through everything they fly into. */
    public static final MTItemVoidstarBow voidstarBow = new MTItemVoidstarBow();

    /**
     * The Rainbow Wrench (彩虹小扳手): a GT tool item - see {@link MTRainbowWrench} for the pinned stats and the animated
     * icon. Like the fuel rods it is registered by its GT base class' constructor, not by {@link #registerItems()}.
     */
    public static final MTRainbowWrench rainbowWrench = new MTRainbowWrench();

    /** The Ultimate Pattern Terminal (终极样板编码终端) as an AE cable part item. */
    public static final ItemUltimatePatternTerminal ultimatePatternTerminal = new ItemUltimatePatternTerminal();

    /** The game console (游戏机): right click opens a window running a copy of DOOM. */
    public static final MTItemGameConsole gameConsole = new MTItemGameConsole();

    /** LEG upgrade items (源质发电机升级): metadata 0 empty, 1..9 the essentia categories. */
    public static final com.MessTech.common.thaumcraft.MTEssentiaUpgradeItem essentiaUpgrade = new com.MessTech.common.thaumcraft.MTEssentiaUpgradeItem();

    /**
     * The comb processing module (蜂窝处理模块) of the Chemical Twister's mode 2: the recipes of the comb processing
     * pool carry it as a non-consumed input.
     */
    public static final MTItemCombProcessingModule combProcessingModule = new MTItemCombProcessingModule();

    private MTItems() {}

    public static void registerItems() {
        GameRegistry.registerItem(meCreativeOreDisk, "MECreativeOreDisk");
        GameRegistry.registerItem(nacComponentItem, "MTNACComponentItem");
        GameRegistry.registerItem(messFood, "MessFood");
        GameRegistry.registerItem(piggy, "MTItemPiggy");
        GameRegistry.registerItem(voidstarBow, "VoidstarBow");
        GameRegistry.registerItem(ultimatePatternTerminal, "UltimatePatternTerminal");
        GameRegistry.registerItem(gameConsole, "GameConsole");
        GameRegistry.registerItem(essentiaUpgrade, "MTEssentiaUpgrade");
        GameRegistry.registerItem(combProcessingModule, "CombProcessingModule");
        MTItemList.UltimatePatternTerminal.set(new ItemStack(ultimatePatternTerminal, 1));
        MTItemList.GameConsole.set(new ItemStack(gameConsole, 1));
        MTItemList.MTEssentiaUpgrade.set(new ItemStack(essentiaUpgrade, 1));
        MTItemList.MTCombProcessingModule.set(new ItemStack(combProcessingModule, 1));
        // The fuel rods extend GT's GTGenericItem, whose constructor already calls
        // GameRegistry.registerItem(this, "gt.<unlocalized>") (as GT's own rods do), so they must not be registered
        // here a second time. Same for the Rainbow Wrench (MetaGeneratedTool -> GTGenericItem).
    }
}
