package com.MessTech.common.items;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;

public final class MTItems {

    /** Texture domain of the fuel rods (files live in {@code assets/messtech/textures/items/FuelRod/}). */
    public static final String TEXTURE_DOMAIN = "messtech";

    // region Transcendent Metal fuel rods

    /**
     * IC2 pulse maths for a bare rod: every one of the {@code cells} passes adds {@code 1 + cells / 2} pulses and
     * every pulse adds {@code energy} to the reactor output, so the EU/t of a lone rod is
     * {@code energy * cells * (1 + cells / 2) * 5}. The energies below are picked so the output scales with the rod
     * size instead of all three sizes sharing one base value:
     * <ul>
     * <li>single: 9,000,000 x 1 x 1 x 5 = 45,000,000 EU/t (1.34A UIV, the requested "at least 1A UIV" floor)</li>
     * <li>dual: 4,500,000 x 2 x 2 x 5 = 90,000,000 EU/t (exactly 2x the single rod)</li>
     * <li>quad: 3,000,000 x 4 x 3 x 5 = 180,000,000 EU/t (exactly 4x the single rod)</li>
     * </ul>
     * Durability 250,000 cycles (2.5x The Core) and the MOX heat bonus are unchanged.
     * <p>
     * Heat: per cycle a bare rod dumps {@code cells * triangular(1 + cells / 2) * heat} into its neighbours and the
     * hull, so heat 4,096 means 4,096 / 24,576 / 98,304 HU per second for single / dual / quad. GT5U's
     * {@code ItemList.neutroniumHeatCapacitor} ("1G Neutronium Heat Capacitor", 1,000,000,000 HU) therefore buffers
     * a bare quad rod for ~2.8 hours and the UIV heat control hatch alone for ~6 hours, which is why the heat factor
     * can be this high without making the rods unusable.
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

    /** Decoration item whose look is cycled through the GT5U material effects (see {@code MTDynamicItemHelper}). */
    public static final MTItemPiggy piggy = new MTItemPiggy();

    /** The Ultimate Pattern Terminal (终极样板编码终端) as an AE cable part item. */
    public static final ItemUltimatePatternTerminal ultimatePatternTerminal = new ItemUltimatePatternTerminal();

    private MTItems() {}

    public static void registerItems() {
        GameRegistry.registerItem(meCreativeOreDisk, "MECreativeOreDisk");
        GameRegistry.registerItem(nacComponentItem, "MTNACComponentItem");
        GameRegistry.registerItem(messFood, "MessFood");
        GameRegistry.registerItem(piggy, "MTItemPiggy");
        GameRegistry.registerItem(ultimatePatternTerminal, "UltimatePatternTerminal");
        MTItemList.UltimatePatternTerminal.set(new ItemStack(ultimatePatternTerminal, 1));
        // The fuel rods extend GT's GTGenericItem, whose constructor already calls
        // GameRegistry.registerItem(this, "gt.<unlocalized>") (the same path GT's own rods use), so they must
        // not be registered here a second time.
    }
}
