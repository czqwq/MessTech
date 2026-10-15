package com.MessTech.common.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * The Voidstar Piercer's branch of the {@link MTKillBase} ladder: everything alive that the arrow hits, and
 * everything alive that its tunnel is blown through, dies through this one branch, so the two kill paths of the
 * weapon cannot behave differently. Both use it - {@code MTEntityVoidstarArrow#onEntityHit} for a direct hit and
 * {@code MTExplosionTunnel#annihilateLiving} for the sweep along the tunnel.
 * <p>
 * It refuses one target, and that is the point of having a branch for this weapon: <b>the shooter is never killed by
 * their own arrow</b>. The tunnel starts inside the shooter's own body (the muzzle of the bow is at their eyes), so
 * without this rule a shot would kill the archer before it left the bow - which the callers also guard against, by
 * skipping the shooter when they look for targets, but a rule that the weapon depends on belongs where the kill
 * happens rather than in every caller.
 */
public final class MTBowKill extends MTKillBase {

    /** The one branch instance; the ladder holds no state per kill, so one is enough. */
    public static final MTBowKill INSTANCE = new MTBowKill();

    private MTBowKill() {
        super("Bow", "death.attack.messtech.voidstar", "messtech.voidstarBow.kill.failed");
    }

    /**
     * {@inheritDoc}
     * <p>
     * A player is never the target of their own shot. Everything else the ladder is asked for is accepted, the
     * thrower included: another player standing in the tunnel is a target like any other.
     */
    @Override
    protected boolean accepts(Entity target, EntityLivingBase thrower) {
        return thrower == null || target != thrower;
    }
}
