package com.MessTech.common.explosion;

import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.MessTech.common.entity.MTEntityDEBlast;
import com.MessTech.common.process.IMTProcess;
import com.MessTech.common.process.MTProcessHandler;
import com.MessTech.common.util.Utils;

/**
 * 1:1 port of Draconic Evolution's
 * {@code com.brandon3055.draconicevolution.common.tileentities.multiblocktiles.reactor.ReactorExplosion} (see
 * {@code tmp/Draconic-Evolution-master}), used as the MTReactor meltdown and the Voidstar Piercer's DE mode.
 * <p>
 * One block ring per tick up to {@code power * 10} blocks of radius, one {@link MTExplosionDETrace} per ring block,
 * each blasting that block's whole vertical column - instead of IC2's {@code ExplosionIC2}, which never scales (ray
 * count {@code 2 * ceil(pi / atan(0.4 / power))^2}: a 450 power explosion fires ~25 million rays, each stepping
 * hundreds of blocks, and freezes the server for minutes before the first block goes).
 * <p>
 * <b>The fireball is ported too.</b> DE's process has no visual either; the player sees a client side effect that
 * {@code TileReactorCore#goBoom} triggers over the network, the {@code Particles.ReactorExplosionParticle} growing one
 * block per tick up to {@code power * 10}. This mod has no packet channel, so it is a synced entity instead
 * ({@link MTEntityDEBlast}, drawn by {@code MTDEBlastRenderer}); DE's parameter {@code (int) (power * 10F)} is the
 * entity's maximum size. Every explosion of this class gets it.
 */
public class MTExplosionDE implements IMTProcess {

    /**
     * DE's own reactor explodes with 2..20 power, one power unit being roughly ten blocks of radius. This port keeps
     * twice the original value, so the biggest possible MTReactor meltdown is 40 (400 blocks).
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

        if (!world.isRemote) {
            // The fireball, i.e. what DE sends to its clients as `(int) (power * 10F)` with the explosion.
            MTEntityDEBlast blast = new MTEntityDEBlast(world);
            blast.setPosition(x + 0.5D, y + 0.5D, z + 0.5D);
            blast.setMaxSize(this.power * 10.0D);
            world.spawnEntityInWorld(blast);
        }
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
