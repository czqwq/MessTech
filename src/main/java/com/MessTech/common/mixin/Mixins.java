package com.MessTech.common.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Registry of MessTech mixins.
 * <p>
 * The late loader asks this enum which mixin classes should be applied based on the currently
 * loaded mods. This mirrors the pattern used by GTNH FMusic.
 */
@SuppressWarnings("unused")
public enum Mixins {

    /**
     * Makes Spice of Life treat every distinct mess food (i.e. every ordered ingredient list) as a
     * separate food. The mixins are only applied when Spice of Life is present.
     */
    SPICE_OF_LIFE_FOOD_IDENTITY(new MixinClass("MixinFoodEaten").setClass("MixinFoodEaten")
        .setPhase(Phase.LATE)
        .addTargetMod(TargetMod.SPICE_OF_LIFE),
        new MixinClass("MixinFoodHistory").setClass("MixinFoodHistory")
            .setPhase(Phase.LATE)
            .addTargetMod(TargetMod.SPICE_OF_LIFE));

    /*
     * SPACE_ELEVATOR_MODULES(new MixinClass("MixinTileEntitySpaceElevator").setClass("MixinTileEntitySpaceElevator")
     * .setPhase(Phase.LATE)
     * .addTargetMod(TargetMod.GTNH_INTERGALACTIC));
     */

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
