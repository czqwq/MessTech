package com.MessTech.common.explosion;

import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.MessTech.common.process.IMTProcess;
import com.MessTech.common.process.MTProcessHandler;
import com.MessTech.common.util.Utils;

/**
 * 1:1 port of Draconic Evolution's
 * {@code com.brandon3055.draconicevolution.common.tileentities.multiblocktiles.reactor.ReactorExplosion} (see
 * {@code tmp/Draconic-Evolution-master}), used as the MTReactor meltdown.
 * <p>
 * The explosion expands one block ring per tick up to {@code power * 10} blocks of radius and drops one
 * {@link MTExplosionDETrace} per ring block, which then blasts its whole vertical column, instead of IC2's
 * {@code ExplosionIC2}. IC2 never scales: its ray count is {@code 2 * ceil(pi / atan(0.4 / power))^2}, so a
 * 450 power explosion fires ~25 million rays (each one stepping hundreds of blocks) which freezes the server for
 * minutes before the very first block is removed.
 */
public class MTExplosionDE implements IMTProcess {

    /**
     * Draconic Evolution's own reactor explodes with 2..20 power, one power unit being roughly ten blocks of radius.
     * This port keeps twice the original value, so the biggest possible MTReactor meltdown is 40 (400 blocks).
     */
    public static final float MAX_POWER = 40.0F;

    public static final DamageSource REACTOR_EXPLOSION = new DamageSource("damage.messtech.reactorExplode")
        .setExplosion()
        .setDamageBypassesArmor()
        .setDamageIsAbsolute()
        .setDamageAllowedInCreativeMode();

    private final World world;
    private final int xCoord;
    private final int yCoord;
    private final int zCoord;
    private final float power;
    private boolean isDead;
    private double expansion = 0;

    public MTExplosionDE(World world, int x, int y, int z, float power) {
        this.world = world;
        this.xCoord = x;
        this.yCoord = y;
        this.zCoord = z;
        this.power = Math.min(power, MAX_POWER);
        isDead = world.isRemote;
    }

    @Override
    public void updateProcess() {

        int size = (int) expansion;
        for (int x = xCoord - size; x < xCoord + size; x++) {
            for (int z = zCoord - size; z < zCoord + size; z++) {
                double distance = Utils.getDistanceAtoB(x, z, xCoord, zCoord);
                if (distance < expansion && distance >= size - 1) {
                    float tracePower = power - (float) (expansion / 10D);
                    tracePower *= 1F + (world.rand.nextFloat() - 0.5F) * 0.2;
                    MTProcessHandler.addProcess(new MTExplosionDETrace(world, x, yCoord, z, tracePower));
                }
            }
        }

        isDead = expansion >= power * 10;
        expansion += 1;
    }

    @Override
    public boolean isDead() {
        return isDead;
    }
}
