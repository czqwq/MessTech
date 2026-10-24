package com.MessTech.common.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.IBeekeepingMode;
import forestry.apiculture.genetics.Bee;
import gregtech.api.enums.GTValues;

/**
 * Runs one Forestry bee the way the Mega Industrial Apiary does, so a space module can produce bee drops without a
 * real apiary. Maths from kubatech's {@code MTEMegaIndustrialApiary} via TST's
 * {@code TST_SpaceApiary.BeeSimulator} ("from kubatech, modified").
 * <p>
 * Each product's final chance is {@code Bee.getFinalChance(chance, beeSpeed, productionModifier, t)}, with {@code t}
 * from the apiary formula {@code 2.8 * b^0.52 * (p + t)^0.52 * s^0.37}: a real apiary hardcodes {@code t = 8}, here it
 * comes from the module's voltage tier, which is what makes a higher tier produce more (see
 * {@link #voltageTierExact(int)}).
 * <p>
 * Stateless on purpose: a module keeps its own partial-drop carry; this class only answers "what does one queen of
 * this genome give over this much bee time".
 */
public final class MTBeeSimulator {

    /**
     * The production modifier the industrial apiary reaches with a full set of production upgrades:
     * {@code 4 * 1.2^8}, the constant kubatech and TST use.
     */
    public static final float MAX_PRODUCTION_MODIFIER_FROM_UPGRADES = 17.19926784F;

    /**
     * Bee time one run accounts for, in the units {@code Bee.getFinalChance} expects. TST feeds its simulator
     * {@code 6400} and divides that by this, so the two numbers stay as they are.
     */
    public static final double BEE_TIME_PER_RUN = 6400D;
    private static final double PRODUCTION_TIME_DIVISOR = 550D;

    private MTBeeSimulator() {}

    /**
     * The {@code t} term of the apiary production formula for a voltage tier: TST's
     * {@code Math.log(GTValues.V[tier] / 8d) / Math.log(4d)}, i.e. how many doublings of voltage the tier is above
     * ULV.
     *
     * @param aTier a GT voltage tier index
     * @return the {@code t} of that tier, e.g. ~8.5 for UEV
     */
    public static float voltageTierExact(int aTier) {
        int tier = Math.max(0, Math.min(GTValues.V.length - 1, aTier));
        return (float) (Math.log((double) GTValues.V[tier] / 8D) / Math.log(4D) + 1e-8D);
    }

    /** @return true when the stack is a queen, i.e. something this simulator can run. */
    public static boolean isQueen(ItemStack stack) {
        return stack != null && BeeManager.beeRoot.getType(stack) == EnumBeeType.QUEEN;
    }

    /**
     * The identity two queens share when the Mega Industrial Apiary shows them as one stacked entry: primary species,
     * secondary species, speed allele - exactly kubatech's {@code BeeSimulator#speciesKey}. Same key, same drops, so a
     * GUI may show them as one button with a count.
     *
     * @param queen a queen stack, not modified
     * @return the key, or {@code null} when the stack is not a queen whose genome can be read
     */
    public static String speciesKey(ItemStack queen) {
        if (!isQueen(queen)) return null;
        IBee member = BeeManager.beeRoot.getMember(queen.copy());
        if (member == null) return null;
        IBeeGenome genome = member.getGenome();
        return genome.getPrimary()
            .getUID() + "\0"
            + genome.getSecondary()
                .getUID()
            + "\0"
            + genome.getSpeed();
    }

    /**
     * The drops of one queen over {@link #BEE_TIME_PER_RUN} of bee time, before any parallel.
     * <p>
     * Primary products roll at full chance, the secondary species at half, the primary's specialty products at theirs
     * - the split kubatech and TST use. A drop worth less than one item is dropped, not rounded up, so a low tier
     * really does produce less.
     *
     * @param queen the queen to run, not modified
     * @param world the world the module sits in, for the beekeeping mode
     * @param t     the {@code t} term of the production formula, see {@link #voltageTierExact(int)}
     * @return the drops of one run, to be multiplied by the parallel by the caller
     */
    public static List<ItemStack> simulate(ItemStack queen, World world, float t) {
        List<ItemStack> out = new ArrayList<>();
        if (!isQueen(queen) || world == null) return out;

        IBeekeepingMode mode = BeeManager.beeRoot.getBeekeepingMode(world);
        if (mode == null) return out;

        IBee member = BeeManager.beeRoot.getMember(queen.copy());
        if (member == null) return out;

        IBeeModifier beeModifier = mode.getBeeModifier();
        IBeeGenome genome = member.getGenome();
        float beeSpeed = genome.getSpeed();
        float productionModifier = MAX_PRODUCTION_MODIFIER_FROM_UPGRADES
            + beeModifier.getProductionModifier(null, MAX_PRODUCTION_MODIFIER_FROM_UPGRADES);

        genome.getPrimary()
            .getProductChances()
            .forEach(
                (key, chance) -> add(
                    out,
                    key,
                    Bee.getFinalChance(chance, beeSpeed, productionModifier, t) / PRODUCTION_TIME_DIVISOR
                        * BEE_TIME_PER_RUN));
        genome.getSecondary()
            .getProductChances()
            .forEach(
                (key, chance) -> add(
                    out,
                    key,
                    Bee.getFinalChance(chance / 2F, beeSpeed, productionModifier, t) / PRODUCTION_TIME_DIVISOR
                        * BEE_TIME_PER_RUN));
        genome.getPrimary()
            .getSpecialtyChances()
            .forEach(
                (key, chance) -> add(
                    out,
                    key,
                    Bee.getFinalChance(chance, beeSpeed, productionModifier, t) / PRODUCTION_TIME_DIVISOR
                        * BEE_TIME_PER_RUN));
        return out;
    }

    /** Adds {@code amount} of a drop to the list, refusing the amounts below one whole item. */
    private static void add(List<ItemStack> out, ItemStack stack, double amount) {
        if (stack == null) return;
        long size = (long) amount;
        if (size <= 0) return;
        ItemStack copy = stack.copy();
        copy.stackSize = (int) Math.min(Integer.MAX_VALUE, size);
        out.add(copy);
    }
}
