package com.MessTech.common.mixin.late;

import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.MessTech.init.Config;

/**
 * Raises the ceiling of vanilla's "moved too quickly!" server-side speed check.
 * <p>
 * The check itself lives in {@code NetHandlerPlayServer#processPlayer} and is a Forge source patch, not a mod: Forge
 * replaced vanilla's {@code Math.min} with {@code Math.max} for the three axes
 * ({@code forgepatches.zip -> net/minecraft/network/NetHandlerPlayServer.java.patch}, "BUGFIX: min -> max, grabs the
 * highest distance"), so the squared distance it compares against {@code 100.0} is the largest of the reported delta
 * and the entity's own motion. That makes a legitimate fast launch - a big TNT pile throws a player well past the
 * vanilla 10 blocks/tick - trip the check on every tick, and each trip teleports the player back to {@code lastPos}.
 * <p>
 * This injector only ever <em>raises</em> the constant, exactly like Hodgepodge's
 * {@code MixinNetHandlerPlayServer_DisableMovedTooQuickly} does: values at or below the vanilla 100.0 are handed
 * straight back, so setting the config to 100.0 (or anything lower) switches the whole thing off again. It is
 * deliberately a {@code @ModifyConstant} and not a {@code @Redirect} or {@code @Overwrite}: modify injectors on the
 * same constant <em>chain</em> instead of conflicting, where the other two would make the game fail to start next to
 * Hodgepodge. The priority puts this one after Hodgepodge's (1000 by default), so it sees whatever Hodgepodge left
 * behind and can raise that further - the ceiling the server ends up with is the largest of vanilla's 100.0,
 * Hodgepodge's {@code fixes.movedTooQuicklyThreshold} and this mod's {@code MOVED_TOO_QUICKLY_THRESHOLD}.
 * <p>
 * The value is read when the method runs rather than gating the mixin itself, so it does not matter when GTNHMixins
 * collects the late mixin list relative to this mod reading its config in {@code preInit}.
 */
@Mixin(value = NetHandlerPlayServer.class, priority = 1500)
public class MixinNetHandlerPlayServer_MovedTooQuickly {

    @ModifyConstant(method = "processPlayer", constant = @Constant(doubleValue = 100.0D, ordinal = 0))
    private double messTech$movedTooQuicklyThreshold(double original) {
        final double configured = Config.MOVED_TOO_QUICKLY_THRESHOLD;
        return configured > original ? configured : original;
    }
}
