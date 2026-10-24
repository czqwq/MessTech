package com.MessTech.common.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Registry of MessTech mixins: the late loader asks this enum which mixin classes to apply for the loaded mods
 * (the GTNH FMusic pattern).
 */
@SuppressWarnings("unused")
public enum Mixins {

    /**
     * Makes Spice of Life treat every distinct mess food (i.e. every ordered ingredient list) as a separate food.
     * Applied only when Spice of Life is present.
     */
    SPICE_OF_LIFE_FOOD_IDENTITY(new MixinClass("MixinFoodEaten").setClass("MixinFoodEaten")
        .setPhase(Phase.LATE)
        .addTargetMod(TargetMod.SPICE_OF_LIFE),
        new MixinClass("MixinFoodHistory").setClass("MixinFoodHistory")
            .setPhase(Phase.LATE)
            .addTargetMod(TargetMod.SPICE_OF_LIFE)),

    /**
     * Raises the ceiling of vanilla's "moved too quickly!" server-side speed check
     * ({@code NetHandlerPlayServer#processPlayer}) from {@code Config.general.MOVED_TOO_QUICKLY_THRESHOLD}. The target
     * is
     * vanilla, so there is no mod to gate on and the mixin always applies - values at or below vanilla's 100.0 hand the
     * constant straight back. See {@code MixinNetHandlerPlayServer_MovedTooQuickly} for why it coexists with
     * Hodgepodge's own mixin on the same constant instead of replacing it.
     */
    MOVED_TOO_QUICKLY_THRESHOLD(new MixinClass("MixinNetHandlerPlayServer_MovedTooQuickly")
        .setClass("MixinNetHandlerPlayServer_MovedTooQuickly")
        .setPhase(Phase.LATE)),

    /**
     * NEI's formatted input field: keeps editable effect declarations and draws through the vanilla input renderer.
     * (Angelica's batching font renderer is mixed in from the early config instead - see
     * {@code MixinBatchingFontRenderer_TextEffect}.)
     */
    NEI_TEXT_EFFECTS(new MixinClass("MixinFormattedTextField_TextEffect").setClass("MixinFormattedTextField_TextEffect")
        .setPhase(Phase.LATE)
        .addTargetMod(TargetMod.NOT_ENOUGH_ITEMS));

    private final MixinClass[] MIXIN_CLASS;
    private final Supplier<Boolean> shouldApplyThisMixinGroup;

    Mixins(MixinClass... MIXIN_CLASS) {
        this(() -> true, MIXIN_CLASS);
    }

    Mixins(Supplier<Boolean> shouldApplyThisMixinGroup, MixinClass... MIXIN_CLASS) {
        this.MIXIN_CLASS = MIXIN_CLASS;
        this.shouldApplyThisMixinGroup = shouldApplyThisMixinGroup;
    }

    public static List<String> getLateMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        for (Mixins value : values()) {
            if (!value.shouldApplyThisMixinGroup.get()) continue;
            for (MixinClass mixinClass : value.MIXIN_CLASS) {
                if (mixinClass.mClass.equals(MixinClass.ERROR)) continue;
                if (!mixinClass.phase.equals(Phase.LATE)) continue;
                if (!mixinClass.classPredicate.test(mixinClass)) continue;
                if (!loadedMods.containsAll(
                    mixinClass.targetMods.stream()
                        .map(TargetMod::getModId)
                        .collect(Collectors.toSet())))
                    continue;
                if (mixinClass.excludedMods.stream()
                    .map(TargetMod::getModId)
                    .anyMatch(loadedMods::contains)) continue;
                mixins.add(mixinClass.getMixinClassPath());
            }
        }
        return mixins;
    }

    enum Phase {
        LATE,
        EARLY,
        ERROR_PHASE
    }

    static class MixinClass {

        static final String ERROR = "MessTech_MIXIN_ERROR";

        final String id;
        String mClass = ERROR;
        Phase phase = Phase.ERROR_PHASE;
        List<TargetMod> targetMods = new ArrayList<>();
        List<TargetMod> excludedMods = new ArrayList<>();
        Predicate<MixinClass> classPredicate = mixinClass -> true;

        MixinClass(String id) {
            this.id = id;
        }

        MixinClass setClass(String mClass) {
            this.mClass = mClass;
            return this;
        }

        MixinClass setPhase(Phase phase) {
            this.phase = phase;
            return this;
        }

        String getMixinClassPath() {
            return mClass;
        }

        MixinClass addTargetMod(TargetMod... targetMod) {
            targetMods.addAll(Arrays.asList(targetMod));
            return this;
        }

        MixinClass addExcludedMod(TargetMod... targetMod) {
            excludedMods.addAll(Arrays.asList(targetMod));
            return this;
        }

        MixinClass addCondition(boolean condition) {
            classPredicate = classPredicate.and(mixinClass -> condition);
            return this;
        }
    }
}
