package com.MessTech.common.machine.Base;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The per tier values of the module hatches: EU discount from TST's
 * {@code PowerConsumptionMultiplierOfPowerConsumptionController}, parallel from the {@code 1 << (2 * (tier - 2))}
 * ceiling, speed from a fixed table of our own ({@code 0.95, 0.85, ..., 0.01}), not TST's
 * {@code SpeedMultiplierOfSpeedController} x2 law.
 * <p>
 * TST registers its controllers on ZPM..MAX (tier 7..14, T1..T8), our modules run IV (tier 5)..MAX (tier 14): TST's T1
 * sits on IV and the two extra tiers continue its progression (0.125, 0.0625, the only values not literally from TST).
 * TST keeps its tables in a config file, here they are constants a harness can check.
 */
public final class MTModuleValues {

    private MTModuleValues() {}

    /** Recipe duration multiplier per module tier, IV..MAX: the fraction of the original duration left. */
    private static final float[] SPEED_BONUS = { 0.95F, 0.85F, 0.75F, 0.70F, 0.65F, 0.40F, 0.35F, 0.20F, 0.10F, 0.01F };

    /**
     * EU/t multiplier per module tier, IV..MAX: TST's
     * {@code {0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.5, 0.25}}, the tail continuing the halving of its last step.
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

    /** @return The duration multiplier of that tier: 0.95 at IV (5% faster) down to 0.01 at MAX (100x faster). */
    public static float speedBonus(int aTier) {
        return SPEED_BONUS[index(aTier)];
    }

    /**
     * @return The duration reduction as display text: the fraction of the original duration left, in two decimals like
     *         the table above (which keeps {@code 0.95F} = {@code 0.949999988079071} from leaking).
     */
    public static String speedBonusText(int aTier) {
        return speedBonusText(speedBonus(aTier));
    }

    /**
     * @param aBonus The duration multiplier, 1 when the machine has no speed module.
     * @return The duration reduction of that folded machine bonus as display text.
     */
    public static String speedBonusText(float aBonus) {
        return String.format(Locale.ROOT, "%.2f", aBonus);
    }

    /** @return The EU/t multiplier of that tier, 0.95 at IV down to 0.0625 at MAX. */
    public static float euModifier(int aTier) {
        return EU_MULTIPLIER[index(aTier)];
    }

    /**
     * @return The EU/t multiplier as display text: {@code "0.95"} means 5% is saved, no trailing zeros like the EU
     *         table above, four decimals so {@code 0.95F} = {@code 0.949999988079071} cannot leak.
     */
    public static String euModifierText(int aTier) {
        return euModifierText(euModifier(aTier));
    }

    /**
     * @param aModifier The EU/t multiplier, 1 when the machine has no EU discount module.
     * @return The EU/t modifier of that folded machine bonus as display text.
     */
    public static String euModifierText(float aModifier) {
        return BigDecimal.valueOf(Math.round(aModifier * 10000.0) / 10000.0)
            .stripTrailingZeros()
            .toPlainString();
    }

    /**
     * @return How much EU that tier saves, two decimals so the float table cannot leak noise ({@code 1 - 0.7F} is not
     *         exactly 0.3), e.g. {@code "5%"}, {@code "30%"} or {@code "93.75%"}.
     */
    public static String euDiscountText(int aTier) {
        double saved = Math.round((1.0 - euModifier(aTier)) * 100.0 * 100.0) / 100.0;
        if (saved == Math.round(saved)) return (long) saved + "%";
        return saved + "%";
    }

    /**
     * @return The parallel a parallel module of that tier supplies, {@code 1 << (2 * (tier - 2))}: 64 at IV (tier 5) up
     *         to 16,777,216 at MAX (tier 14), 1 below MV.
     */
    public static int maxParallel(int aTier) {
        if (aTier <= 1) return 1;
        // 1 << 31 would be negative; 2 * (tier - 2) is at most 24 for MAX (tier 14).
        return 1 << Math.min(2 * (aTier - 2), 30);
    }
}
