package com.MessTech.common.machine.Base;

/**
 * The per tier values of the module hatches.
 * <p>
 * The numbers are taken from TST's modular machine controllers:
 * <ul>
 * <li>the speed module and the EU discount module use TST's {@code SpeedMultiplierOfSpeedController} and
 * {@code PowerConsumptionMultiplierOfPowerConsumptionController};</li>
 * <li>the parallel module uses the {@code 1 << (2 * (tier - 2))} ceiling.</li>
 * </ul>
 * TST registers its controllers on ZPM..MAX (tier 7..14, T1..T8), our modules run from IV (tier 5) to MAX (tier 14),
 * so TST's T1 sits on IV here and the two remaining tiers continue TST's own progression: the speed multiplier keeps
 * doubling (512, 1024) and the EU multiplier keeps the halving of TST's last step (0.125, 0.0625). Those last two
 * tiers are the only values in this class that are not literally from TST.
 * <p>
 * TST keeps its tables in a config file; the numbers live here as constants instead, so they are in one readable
 * place and a harness can check them.
 */
public final class MTModuleValues {

    private MTModuleValues() {}

    /**
     * Speed multiplier per module tier, IV..MAX. TST's {@code SpeedMultiplierOfSpeedController} is
     * {@code {2, 4, 8, 16, 32, 64, 128, 256}}, which is 2^(T+1) for its T1..T8; the tail continues that law.
     */
    private static final int[] SPEED_MULTIPLIER = { 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024 };

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
        if (index >= SPEED_MULTIPLIER.length) return SPEED_MULTIPLIER.length - 1;
        return index;
    }

    /**
     * @param aTier The GT tier index, IV .. MAX.
     * @return How much faster recipes run at that tier, 2 at IV up to 1024 at MAX.
     */
    public static int speedMultiplier(int aTier) {
        return SPEED_MULTIPLIER[index(aTier)];
    }

    /**
     * @param aTier The GT tier index, IV .. MAX.
     * @return The duration multiplier of that tier, {@code 1 / speedMultiplier(aTier)}.
     */
    public static float speedBonus(int aTier) {
        return 1.0F / speedMultiplier(aTier);
    }

    /**
     * @param aTier The GT tier index, IV .. MAX.
     * @return The EU/t multiplier of that tier, 0.95 at IV down to 0.0625 at MAX.
     */
    public static float euModifier(int aTier) {
        return EU_MULTIPLIER[index(aTier)];
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
