package com.MessTech.common.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * The Voidstar Piercer's branch of the {@link MTKillBase} ladder: everything alive the arrow hits and everything
 * alive its tunnel is blown through dies through this one branch, so the two kill paths cannot diverge -
 * {@code MTEntityVoidstarArrow#onEntityHit} for a direct hit, {@code MTExplosionTunnel#annihilateLiving} for the
 * sweep along the tunnel.
 * <p>
 * It refuses one target, which is the point of having a branch for this weapon: <b>the shooter is never killed by
 * their own arrow</b>. The tunnel starts inside the shooter's own body (the bow's muzzle is at their eyes), so
 * without this rule a shot would kill the archer before it left the bow. The callers also skip the shooter, but a
 * rule the weapon depends on belongs where the kill happens, not in every caller.
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
     * A player is never the target of their own shot; everything else is accepted, the thrower included: another
     * player standing in the tunnel is a target like any other.
     */
    @Override
    protected boolean accepts(Entity target, EntityLivingBase thrower) {
        return thrower == null || target != thrower;
    }
}
