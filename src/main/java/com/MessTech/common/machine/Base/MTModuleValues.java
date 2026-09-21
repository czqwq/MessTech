package com.MessTech.common.machine.Base;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The per tier values of the module hatches.
 * <p>
 * The EU discount and the parallel module use TST's modular machine controllers:
 * <ul>
 * <li>the EU discount module uses TST's {@code PowerConsumptionMultiplierOfPowerConsumptionController};</li>
 * <li>the parallel module uses the {@code 1 << (2 * (tier - 2))} ceiling.</li>
 * </ul>
 * The speed module has a fixed duration table of its own ({@code 0.95, 0.85, ..., 0.01}) and does not follow TST's
 * {@code SpeedMultiplierOfSpeedController} x2 law any more.
 * <p>
 * TST registers its controllers on ZPM..MAX (tier 7..14, T1..T8), our modules run from IV (tier 5) to MAX (tier 14),
 * so TST's T1 sits on IV here and the two remaining tiers continue TST's own progression: the EU multiplier keeps
 * the halving of TST's last step (0.125, 0.0625). Those last two tiers are the only values in this class that are
 * not literally from TST.
 * <p>
 * TST keeps its tables in a config file; the numbers live here as constants instead, so they are in one readable
 * place and a harness can check them.
 */
public final class MTModuleValues {

    private MTModuleValues() {}

    /**
     * Recipe duration multiplier per module tier, IV..MAX: the fraction of the original duration that is left, so
     * the module is 5% faster at IV and 100x faster at MAX. MessTech's own fixed table.
     */
    private static final float[] SPEED_BONUS = { 0.95F, 0.85F, 0.75F, 0.70F, 0.65F, 0.40F, 0.35F, 0.20F, 0.10F, 0.01F };

    /**
     * EU/t multiplier per module tier, IV..MAX. TST's {@code PowerConsumptionMultiplierOfPowerConsumptionController}
     * is {@code {0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.5, 0.25}}; the tail continues the halving of its last step.
     */
    private static final float[] EU_MULTIPLIER = { 0.95F, 0.9F, 0.85F, 0.8F, 0.75F, 0.7F, 0.5F, 0.25F, 0.125F,
        0.0625F };

    /** Index into the tables above for a tier, clamped into IV..MAX. */
    private static int index(int aTier) {
        int index = aTier - IMTModule.MIN_TIER;
        if (index < 0) return 0;
        if (index >= SPEED_BONUS.length) return SPEED_BONUS.length - 1;
        return index;
    }

    /**
     * @param aTier The GT tier index, IV .. MAX.
     * @return The duration multiplier of that tier: 0.95 at IV (5% faster) down to 0.01 at MAX (100x faster).
     */
    public static float speedBonus(int aTier) {
        return SPEED_BONUS[index(aTier)];
    }

    /**
     * The recipe duration reduction of that tier as display text: the fraction of the original duration that is left,
     * so {@code "0.95"} means a recipe takes 95% of the time it would without the module. Always two decimals, the way
     * the table above is written, and that also keeps the float entries from leaking noise ({@code 0.95F} is
     * {@code 0.949999988079071}).
     *
     * @param aTier The GT tier index, IV .. MAX.
     * @return The reduction as display text, e.g. {@code "0.95"}, {@code "0.70"} or {@code "0.01"}.
     */
    public static String speedBonusText(int aTier) {
        return speedBonusText(speedBonus(aTier));
    }

    /**
     * The duration reduction of a speed bonus as display text, see {@link #speedBonusText(int)}. Used for the folded
     * bonus of a machine, which is not a table entry any more.
     *
     * @param aBonus The duration multiplier, 1 when the machine has no speed module.
     * @return The reduction as display text, two decimals.
     */
    public static String speedBonusText(float aBonus) {
        return String.format(Locale.ROOT, "%.2f", aBonus);
    }

    /**
     * @param aTier The GT tier index, IV .. MAX.
     * @return The EU/t multiplier of that tier, 0.95 at IV down to 0.0625 at MAX.
     */
    public static float euModifier(int aTier) {
        return EU_MULTIPLIER[index(aTier)];
    }

    /**
     * The EU/t multiplier of that tier as display text: the factor the EU/t of the machine is multiplied with, so
     * {@code "0.95"} means 5% is saved. Written the way the EU table above is written - no trailing zeros - and
     * rounded to four decimals so the float entries cannot leak noise ({@code 0.95F} is
     * {@code 0.949999988079071}); four decimals because the last two tiers are 0.125 and 0.0625.
     *
     * @param aTier The GT tier index, IV .. MAX.
     * @return The modifier as display text, e.g. {@code "0.95"}, {@code "0.7"} or {@code "0.0625"}.
     */
    public static String euModifierText(int aTier) {
        return euModifierText(euModifier(aTier));
    }

    /**
     * The EU/t modifier as display text, see {@link #euModifierText(int)}. Used for the folded modifier of a
     * machine, which is not a table entry any more.
     *
     * @param aModifier The EU/t multiplier, 1 when the machine has no EU discount module.
     * @return The modifier as display text, at most four decimals.
     */
    public static String euModifierText(float aModifier) {
        return BigDecimal.valueOf(Math.round(aModifier * 10000.0) / 10000.0)
            .stripTrailingZeros()
            .toPlainString();
    }

    /**
     * How much EU that tier saves, rounded to two decimals so the float table above cannot leak noise
     * ({@code 1 - 0.7F} is not exactly 0.3).
     *
     * @param aTier The GT tier index, IV .. MAX.
     * @return The saving as display text, e.g. {@code "5%"}, {@code "30%"} or {@code "93.75%"}.
     */
    public static String euDiscountText(int aTier) {
        double saved = Math.round((1.0 - euModifier(aTier)) * 100.0 * 100.0) / 100.0;
        if (saved == Math.round(saved)) return (long) saved + "%";
        return saved + "%";
    }

    /**
     * The parallel a parallel module of that tier supplies, {@code 1 << (2 * (tier - 2))}: 64 at IV (tier 5) up to
     * 16,777,216 at MAX (tier 14).
     *
     * @param aTier The GT tier index.
     * @return The parallel value of that tier, 1 for the tiers below MV.
     */
    public static int maxParallel(int aTier) {
        if (aTier <= 1) return 1;
        // 1 << 31 would be negative; 2 * (tier - 2) is at most 24 for MAX (tier 14).
        return 1 << Math.min(2 * (aTier - 2), 30);
    }
}
