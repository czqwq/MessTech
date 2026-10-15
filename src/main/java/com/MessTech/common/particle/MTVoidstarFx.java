package com.MessTech.common.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.MessTech.common.entity.MTEntityVoidstarArrow;
import com.MessTech.common.items.MTItems;
import com.MessTech.init.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The particle effects of the Voidstar Piercer's arrow, all of them spawned on the client that owns the view:
 * <ul>
 * <li>{@link #spawnTrail} - the trail: a few sparks along the step the arrow just flew, so a three block per tick
 * arrow leaves a continuous line of light instead of a dotted one.</li>
 * <li>{@link #spawnPierceBurst} - the small burst of one block the arrow chewed through while drilling (see
 * {@code MTEntityVoidstarArrow#onBlockContact}). It is deliberately cheap: a shot into a mountain triggers it every
 * few ticks for as long as the arrow digs.</li>
 * <li>{@link #spawnImpact} - the big burst at the end of the flight: a flash at the wall, sparks all along the
 * tunnel the arrow carved (using the muzzle position the arrow carries in its data watcher) and the vanilla
 * explosion particles, so the moment the shot goes off reads before the block updates of the tunnel arrive.</li>
 * </ul>
 * The magic circle the bow draws while it is being charged is <b>not</b> here: it is a textured quad rendered in
 * world space by {@code MTMagicCircleRenderer}. What is left in this class are the sprites that really are
 * particles; the spark sprite lives in the item atlas and is registered by the bow's own {@code registerIcons} (see
 * {@link MTEntityFXVoidstar} for why the item atlas is the one to use). Both ends of this class are client only.
 */
@SideOnly(Side.CLIENT)
public final class MTVoidstarFx {

    /** The trail: particles per tick, their life, how far they scatter and how big they are. */
    private static final int TRAIL_PARTICLES_PER_TICK = 3;
    private static final int TRAIL_PARTICLE_LIFE = 6;
    private static final double TRAIL_SPREAD = 0.06D;

    /** The burst of one drilled block: sparks thrown out of the face, and how far they spread. */
    private static final int PIERCE_SPARKS = 7;
    private static final double PIERCE_SPREAD = 0.45D;

    /** The impact: sparks flying out of the wall, sparks along the tunnel, and its own budget of them. */
    private static final int IMPACT_SPARKS = 26;
    private static final int IMPACT_TUNNEL_SPARKS = 60;
    private static final double IMPACT_TUNNEL_SPARKS_PER_BLOCK = 1.5D;

    /** The one sprite of this class, registered by the bow's {@code registerIcons}; {@code null} until then. */
    private static IIcon sparkIcon;

    private MTVoidstarFx() {}

    /**
     * Takes the spark sprite the item atlas is stitched with. Called from the bow's {@code Item#registerIcons}: the
     * register handed to an item is the item atlas' one, which is the atlas layer 2 particles are drawn from.
     */
    public static void registerIcons(IIconRegister register) {
        sparkIcon = register.registerIcon(MTItems.TEXTURE_DOMAIN + ":particle/voidstarSpark");
    }

    /**
     * The trail of one tick of the arrow's flight: sparks spread along the step it just took (the arrow moves up to
     * three blocks per tick, so one particle per position would leave a dotted line).
     */
    public static void spawnTrail(MTEntityVoidstarArrow arrow) {
        if (sparkIcon == null || arrow == null) return;

        World world = arrow.worldObj;
        if (world == null) return;

        double x = arrow.posX, y = arrow.posY, z = arrow.posZ;
        double previousX = arrow.prevPosX, previousY = arrow.prevPosY, previousZ = arrow.prevPosZ;

        for (int i = 0; i < TRAIL_PARTICLES_PER_TICK; i++) {
            double along = (i + 1.0D) / (TRAIL_PARTICLES_PER_TICK + 1.0D);
            double sparkX = previousX + (x - previousX) * along + (world.rand.nextDouble() - 0.5D) * TRAIL_SPREAD;
            double sparkY = previousY + (y - previousY) * along + (world.rand.nextDouble() - 0.5D) * TRAIL_SPREAD;
            double sparkZ = previousZ + (z - previousZ) * along + (world.rand.nextDouble() - 0.5D) * TRAIL_SPREAD;

            spawnSpark(
                world,
                sparkX,
                sparkY,
                sparkZ,
                0.0D,
                0.0D,
                0.0D,
                0.55F + world.rand.nextFloat() * 0.35F,
                TRAIL_PARTICLE_LIFE + world.rand.nextInt(4),
                0.62F,
                0.95F,
                1.0F,
                0.9F);
        }
    }

    /**
     * The burst of one block the arrow drilled through: a handful of sparks thrown back out of the face it is
     * pressed against. Cheap on purpose - the arrow triggers it once every few ticks while it digs.
     *
     * @param arrow the arrow that is stuck against the block
     * @param x     the face it is pressed against
     * @param y     see {@code x}
     * @param z     see {@code x}
     */
    public static void spawnPierceBurst(MTEntityVoidstarArrow arrow, double x, double y, double z) {
        if (sparkIcon == null || arrow == null) return;

        World world = arrow.worldObj;
        if (world == null) return;

        for (int i = 0; i < PIERCE_SPARKS; i++) {
            spawnSpark(
                world,
                x + (world.rand.nextDouble() - 0.5D) * PIERCE_SPREAD,
                y + (world.rand.nextDouble() - 0.5D) * PIERCE_SPREAD,
                z + (world.rand.nextDouble() - 0.5D) * PIERCE_SPREAD,
                (world.rand.nextDouble() - 0.5D) * 0.12D,
                world.rand.nextDouble() * 0.10D,
                (world.rand.nextDouble() - 0.5D) * 0.12D,
                0.6F + world.rand.nextFloat() * 0.5F,
                6 + world.rand.nextInt(5),
                0.78F,
                0.62F,
                1.0F,
                0.95F);
        }
    }

    /**
     * The big burst at the end of the flight: a flash at the point the arrow stopped at, sparks through the tunnel
     * it carved and the vanilla explosion particles on top.
     *
     * @param arrow the arrow whose flight ended; its data watcher carries the muzzle the tunnel starts at
     * @param hitX  the point the flight ended at
     * @param hitY  see {@code hitX}
     * @param hitZ  see {@code hitX}
     */
    public static void spawnImpact(MTEntityVoidstarArrow arrow, double hitX, double hitY, double hitZ) {
        if (arrow == null) return;

        World world = arrow.worldObj;
        if (world == null) return;

        // The flash: sparks thrown out of the wall in every direction.
        for (int i = 0; i < IMPACT_SPARKS; i++) {
            double speed = 0.15D + world.rand.nextDouble() * 0.55D;
            double pitch = Math.toRadians(world.rand.nextDouble() * 180.0D - 90.0D);
            double yaw = world.rand.nextDouble() * Math.PI * 2.0D;
            spawnSpark(
                world,
                hitX,
                hitY,
                hitZ,
                Math.cos(yaw) * Math.cos(pitch) * speed,
                Math.sin(pitch) * speed,
                Math.sin(yaw) * Math.cos(pitch) * speed,
                0.9F + world.rand.nextFloat() * 0.8F,
                10 + world.rand.nextInt(8),
                1.0F,
                0.88F,
                1.0F,
                1.0F);
        }

        spawnTunnelSparks(world, arrow, hitX, hitY, hitZ);

        // The vanilla particles of an explosion, so the bang looks like one before the tunnel's block updates
        // arrive at the client.
        for (int i = 0; i < 10; i++) {
            world.spawnParticle(
                "largeexplode",
                hitX + (world.rand.nextDouble() - 0.5D) * 2.0D,
                hitY + (world.rand.nextDouble() - 0.5D) * 2.0D,
                hitZ + (world.rand.nextDouble() - 0.5D) * 2.0D,
                0.0D,
                0.0D,
                0.0D);
        }
        for (int i = 0; i < 20; i++) {
            world.spawnParticle(
                "smoke",
                hitX + (world.rand.nextDouble() - 0.5D) * 3.0D,
                hitY + (world.rand.nextDouble() - 0.5D) * 3.0D,
                hitZ + (world.rand.nextDouble() - 0.5D) * 3.0D,
                (world.rand.nextDouble() - 0.5D) * 0.1D,
                world.rand.nextDouble() * 0.1D,
                (world.rand.nextDouble() - 0.5D) * 0.1D);
        }
    }

    /**
     * Sparks spread through the tunnel the shot carved: along the line from the muzzle to the wall, each one placed
     * at a random point of the tunnel's cross section, so the whole corridor lights up at once.
     */
    private static void spawnTunnelSparks(World world, MTEntityVoidstarArrow arrow, double hitX, double hitY,
        double hitZ) {
        double deltaX = hitX - arrow.getStartX();
        double deltaY = hitY - arrow.getStartY();
        double deltaZ = hitZ - arrow.getStartZ();
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (length < 0.5D) return;

        double directionX = deltaX / length, directionY = deltaY / length, directionZ = deltaZ / length;

        // The same right/up basis the magic circle uses, built from the flight direction.
        double rightX = -directionZ, rightZ = directionX;
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLength < 1.0E-3D) {
            rightX = 1.0D;
            rightZ = 0.0D;
            rightLength = 1.0D;
        }
        rightX /= rightLength;
        rightZ /= rightLength;
        double upX = -rightZ * directionY;
        double upY = rightZ * directionX - rightX * directionZ;
        double upZ = rightX * directionY;

        int sparks = (int) Math.min(IMPACT_TUNNEL_SPARKS, Math.max(6.0D, length * IMPACT_TUNNEL_SPARKS_PER_BLOCK));
        double radius = Config.VOIDSTAR_BOW_TUNNEL_RADIUS;

        for (int i = 0; i < sparks; i++) {
            double along = world.rand.nextDouble();
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            double offset = Math.sqrt(world.rand.nextDouble()) * radius;
            double cos = Math.cos(angle), sin = Math.sin(angle);

            double x = arrow.getStartX() + deltaX * along + (rightX * cos + upX * sin) * offset;
            double y = arrow.getStartY() + deltaY * along + (upY * sin) * offset;
            double z = arrow.getStartZ() + deltaZ * along + (rightZ * cos + upZ * sin) * offset;

            spawnSpark(
                world,
                x,
                y,
                z,
                (world.rand.nextDouble() - 0.5D) * 0.08D,
                (world.rand.nextDouble() - 0.5D) * 0.08D,
                (world.rand.nextDouble() - 0.5D) * 0.08D,
                0.7F + world.rand.nextFloat() * 0.7F,
                8 + world.rand.nextInt(8),
                0.78F,
                0.62F,
                1.0F,
                1.0F);
        }
    }

    /** One spark of the trail, of a drilled block and of the final impact. */
    private static void spawnSpark(World world, double x, double y, double z, double motionX, double motionY,
        double motionZ, float scale, int life, float red, float green, float blue, float alpha) {
        Minecraft.getMinecraft().effectRenderer.addEffect(
            new MTEntityFXVoidstar(
                world,
                x,
                y,
                z,
                motionX,
                motionY,
                motionZ,
                sparkIcon,
                scale,
                life,
                red,
                green,
                blue,
                alpha));
    }
}
