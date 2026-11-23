package com.MessTech.common.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

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
        .addTargetMod(TargetMod.NOT_ENOUGH_ITEMS)),

    /**
     * The 「无线网络」 (wireless ME network) tab ported from GTLCore: every AE2 based GT hatch GUI gets a tab that lists
     * the accessible wireless networks and connects/disconnects the hatch. The first mixin injects the tab attachment
     * into the shared GT single block hatch GUI base class, the second one (client only) opens the always visible tab
     * panel once the ModularUI2 screen was opened.
     * <p>
     * The screen mixin targets {@code ModularScreen}, which is {@code @SideOnly(Side.CLIENT)} and is stripped from the
     * dedicated server jar by Forge, so it is only registered when the client side loads the late mixins. The GT mixin
     * is unconditional: GT5U is a hard dependency of this mod.
     */
    WIRELESS_HATCH_TAB(
        new MixinClass("MixinMTETieredMachineBlockBaseGui_WirelessTab")
            .setClass("MixinMTETieredMachineBlockBaseGui_WirelessTab")
            .setPhase(Phase.LATE),
        new MixinClass("MixinModularScreen_WirelessTab").setClass("MixinModularScreen_WirelessTab")
            .setPhase(Phase.LATE)
            .addCondition(Side.CLIENT.equals(FMLLaunchHandler.side()))),

    /**
     * The ultra dense AE2 content (超致密ME控制器 / 超致密ME中子注入控制器 / 超致密压缩ME线缆 / 压缩ME玻璃线缆):
     * AE2 natively caps a cable node at 8 or 32 channels and every controller face at 32 channels, so the requested
     * 1024 / 2.1G values need these two facts changed. See {@code docs/ultra-ae2.md}.
     * <ul>
     * <li>{@code MixinGridNode_ChannelCapacity}: a node whose machine implements {@code IMTChannelCapacity} reports its
     * own capacity from {@code GridNode#getMaxChannels}.</li>
     * <li>{@code MixinPathingCalculation_UltraDense} / {@code MixinControllerFace_UltraDense}: a pathing run over a
     * grid
     * containing an ultra dense controller gives the controller faces {@code Integer.MAX_VALUE} channels.</li>
     * </ul>
     * AE2 is a hard dependency of this mod, but the target-mod condition keeps the mixins off if a pack ever drops it.
     */
    ULTRA_AE2(new MixinClass("MixinGridNode_ChannelCapacity").setClass("MixinGridNode_ChannelCapacity")
        .setPhase(Phase.LATE)
        .addTargetMod(TargetMod.APPLIED_ENERGISTICS2),
        new MixinClass("MixinPathingCalculation_UltraDense").setClass("MixinPathingCalculation_UltraDense")
            .setPhase(Phase.LATE)
            .addTargetMod(TargetMod.APPLIED_ENERGISTICS2),
        new MixinClass("MixinControllerFace_UltraDense").setClass("MixinControllerFace_UltraDense")
            .setPhase(Phase.LATE)
            .addTargetMod(TargetMod.APPLIED_ENERGISTICS2));

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
