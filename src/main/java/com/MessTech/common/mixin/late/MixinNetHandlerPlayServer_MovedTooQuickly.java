package com.MessTech.common.mixin.late;

import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.MessTech.init.Config;

/**
 * Raises the ceiling of vanilla's "moved too quickly!" server-side speed check. The check is in
 * {@code NetHandlerPlayServer#processPlayer} and is a Forge source patch, not a mod: Forge replaced vanilla's
 * {@code Math.min} with {@code Math.max} for the three axes
 * ({@code forgepatches.zip -> net/minecraft/network/NetHandlerPlayServer.java.patch}, "BUGFIX: min -> max, grabs the
 * highest
 * distance"), so the squared distance compared against {@code 100.0} is the largest of the reported delta and the
 * entity's own
 * motion - a big TNT pile therefore throws a player past the vanilla 10 blocks/tick on every tick, each trip
 * teleporting them
 * back to {@code lastPos}.
 * <p>
 * This only ever <em>raises</em> the constant, like Hodgepodge's
 * {@code MixinNetHandlerPlayServer_DisableMovedTooQuickly}:
 * values at or below 100.0 are handed straight back, so a config of 100.0 switches it off. A {@code @ModifyConstant}
 * rather
 * than a {@code @Redirect}/{@code @Overwrite} because modify injectors on the same constant <em>chain</em> instead of
 * conflicting - the other two would stop the game starting next to Hodgepodge. Priority 1500 runs after Hodgepodge's
 * 1000, so
 * the final ceiling is the largest of vanilla's 100.0, Hodgepodge's {@code fixes.movedTooQuicklyThreshold} and this
 * mod's
 * {@code MOVED_TOO_QUICKLY_THRESHOLD}.
 * <p>
 * The value is read when the method runs rather than gating the mixin, so it does not matter when GTNHMixins collects
 * the late
 * mixin list relative to this mod reading its config in {@code preInit}.
 */
@Mixin(value = NetHandlerPlayServer.class, priority = 1500)
public class MixinNetHandlerPlayServer_MovedTooQuickly {

    @ModifyConstant(method = "processPlayer", constant = @Constant(doubleValue = 100.0D, ordinal = 0))
    private double messTech$movedTooQuicklyThreshold(double original) {
        final double configured = Config.MOVED_TOO_QUICKLY_THRESHOLD;
        return configured > original ? configured : original;
    }
}
