package com.MessTech.common.explosion;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import com.MessTech.common.util.MTBowKill;
import com.MessTech.common.util.MTKillBase;
import com.MessTech.init.Config;

/**
 * The tunnel of the Voidstar Piercer's arrow: everything within {@link Config#VOIDSTAR_BOW_TUNNEL_RADIUS} of the
 * line the arrow flew is taken out of the world.
 * <ul>
 * <li><b>Blocks</b> are destroyed - "the wall has a three by three hole through it, and so does everything behind
 * it".</li>
 * <li><b>Living things</b> in it are killed for good by {@link MTBowKill}, the bow's branch of the true kill ladder of
 * {@link MTKillBase} - the same death a direct hit deals. The shooter is exempt (the muzzle is inside their own body;
 * see {@link #annihilateLiving}).</li>
 * </ul>
 * The line runs from the muzzle to where the arrow stopped, so the tunnel starts in the air the player shot through
 * and ends inside the block. Above {@link Config#VOIDSTAR_BOW_TUNNEL_LENGTH} the far end is kept and the near end
 * dropped: the wall the arrow hit is what the player is looking at, and one arrow must not walk the whole render
 * distance.
 * <p>
 * What survives: air, fluids (vanilla explosions do not destroy them either - see blast resistance), blocks with no
 * hardness at all (bedrock, the portal, GT's own unbreakable blocks - {@code getBlockHardness < 0}) and anything with
 * an explosion resistance above {@link #MAX_RESISTANCE} (obsidian and up, at 600). Nothing is dropped - the tunnel
 * annihilates, as the item's tooltip says. Server side only: the client learns about it from the block change packets
 * and from the deaths.
 */
public final class MTExplosionTunnel {

    /**
     * The highest explosion resistance the tunnel still tears open. Stone is 30, an iron block 10, GT's machine
     * casings 10 to 20 and obsidian 600; 100 therefore means "everything a player can mine, nothing that is meant to
     * be blast proof".
     */
    public static final float MAX_RESISTANCE = 100.0F;

    /**
     * How close to the impact a destroyed block must be for its break particles to be sent to the clients
     * ({@code World#playAuxSFX} id 2001, the effect vanilla explosions send per block). The far end of a long tunnel is
     * destroyed without them - one packet per block over hundreds of blocks is what makes big explosions lag, and the
     * arrow's client side burst covers the whole tunnel anyway.
     */
    private static final double EFFECT_RADIUS = 12.0D;

    private MTExplosionTunnel() {}

    /**
     * Blasts the tunnel of one shot: the living things inside it are killed, the blocks inside it destroyed. A pass is
     * skipped when its config switch is off.
     *
     * @param source     the entity the tunnel belongs to, i.e. the shooter (see the class comment for the exemption);
     *                   passed to the blocks' own resistance check and used as the thrower of the kills
     * @param projectile the arrow that cut the tunnel; it is the projectile of the kill the ladder builds, so the death
     *                   reads as the bow's and not as a bare player hit
     * @param fromX      the muzzle, i.e. where the arrow left the bow; {@code fromY}/{@code fromZ} likewise
     * @param toX        the point the arrow stopped at, i.e. the far end of the tunnel; {@code toY}/{@code toZ}
     *                   likewise
     */
    public static void blast(World world, Entity source, Entity projectile, double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ) {
        if (world == null || world.isRemote) return;
        if (!Config.voidstarbow.VOIDSTAR_BOW_BREAKS_BLOCKS && !Config.voidstarbow.VOIDSTAR_BOW_ANNIHILATES_LIVING)
            return;

        double deltaX = toX - fromX, deltaY = toY - fromY, deltaZ = toZ - fromZ;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (length < 1.0E-4D) return;

        double maxLength = Config.voidstarbow.VOIDSTAR_BOW_TUNNEL_LENGTH;
        if (length > maxLength) {
            // Keep the far end: the wall the arrow hit stays the centre of the effect.
            double keep = maxLength / length;
            fromX = toX - deltaX * keep;
            fromY = toY - deltaY * keep;
            fromZ = toZ - deltaZ * keep;
            deltaX = toX - fromX;
            deltaY = toY - fromY;
            deltaZ = toZ - fromZ;
            length = maxLength;
        }

        double lengthSquared = length * length;
        double radius = Config.voidstarbow.VOIDSTAR_BOW_TUNNEL_RADIUS;

        if (Config.voidstarbow.VOIDSTAR_BOW_ANNIHILATES_LIVING) {
            annihilateLiving(
                world,
                source,
                projectile,
                fromX,
                fromY,
                fromZ,
                deltaX,
                deltaY,
                deltaZ,
                lengthSquared,
                radius);
        }

        if (Config.voidstarbow.VOIDSTAR_BOW_BREAKS_BLOCKS) {
            destroyBlocks(world, source, fromX, fromY, fromZ, deltaX, deltaY, deltaZ, length, lengthSquared, radius);
        }
    }

    /**
     * Kills every living thing inside the tunnel with the bow's branch of the true kill ladder ({@link MTBowKill}).
     * The entity's size counts: a horse, a boss or a player is a big target whose centre can sit outside the radius
     * while half of it is inside, so the radius compared against grows by half the entity's largest side.
     * <p>
     * The shooter is skipped - the muzzle is inside their own body, so without it every shot would kill the player who
     * fired it - and so is anything already dead or dying (a corpse at zero health has no death left to run).
     * {@link MTBowKill} refuses the shooter a second time: that rule belongs to the weapon, not to every caller.
     */
    private static void annihilateLiving(World world, Entity source, Entity projectile, double fromX, double fromY,
        double fromZ, double deltaX, double deltaY, double deltaZ, double lengthSquared, double radius) {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            Math.min(fromX, fromX + deltaX) - radius,
            Math.min(fromY, fromY + deltaY) - radius,
            Math.min(fromZ, fromZ + deltaZ) - radius,
            Math.max(fromX, fromX + deltaX) + radius,
            Math.max(fromY, fromY + deltaY) + radius,
            Math.max(fromZ, fromZ + deltaZ) + radius);

        List<EntityLivingBase> found = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        for (EntityLivingBase victim : found) {
            if (victim == source) continue;
            if (victim.isDead || victim.getHealth() <= 0.0F) continue;

            double reach = radius + Math.max(victim.width, victim.height) * 0.5D;
            double centreY = victim.posY + victim.height * 0.5D;
            if (distanceToSegment(
                victim.posX,
                centreY,
                victim.posZ,
                fromX,
                fromY,
                fromZ,
                deltaX,
                deltaY,
                deltaZ,
                lengthSquared) > reach) {
                continue;
            }

            MTBowKill.INSTANCE
                .kill(victim, source instanceof EntityLivingBase ? (EntityLivingBase) source : null, projectile);
        }
    }

    /**
     * Destroys the blocks of the tunnel: every block whose centre is within {@code radius} of the line, minus what
     * survives (see the class comment).
     * <p>
     * The line is walked in one block steps, each opening a cube around the sample, not by iterating the segment's
     * bounding box: a diagonal shot would otherwise scan a box as big as its own extent in all three axes (a 128 block
     * flight at 45 degrees is a 90 x 90 x 90 box, 729,000 blocks, to destroy a few hundred). One block steps cover the
     * line within half a block, so a cube of the ceiling of the radius around each sample covers every block whose
     * centre can be within the radius; blocks an earlier step destroyed read as air and are skipped.
     */
    private static void destroyBlocks(World world, Entity source, double fromX, double fromY, double fromZ,
        double deltaX, double deltaY, double deltaZ, double length, double lengthSquared, double radius) {
        EntityPlayer player = source instanceof EntityPlayer ? (EntityPlayer) source : null;

        int steps = Math.max(1, (int) Math.ceil(length));
        int reach = Math.max(0, (int) Math.ceil(radius));
        double stepX = deltaX / steps, stepY = deltaY / steps, stepZ = deltaZ / steps;
        double toX = fromX + deltaX, toY = fromY + deltaY, toZ = fromZ + deltaZ;

        for (int step = 0; step <= steps; step++) {
            double pointX = fromX + stepX * step;
            double pointY = fromY + stepY * step;
            double pointZ = fromZ + stepZ * step;

            int minX = MathHelper.floor_double(pointX) - reach;
            int maxX = MathHelper.floor_double(pointX) + reach;
            int minY = Math.max(0, MathHelper.floor_double(pointY) - reach);
            int maxY = Math.min(255, MathHelper.floor_double(pointY) + reach);
            int minZ = MathHelper.floor_double(pointZ) - reach;
            int maxZ = MathHelper.floor_double(pointZ) + reach;

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (distanceToSegment(
                            x + 0.5D,
                            y + 0.5D,
                            z + 0.5D,
                            fromX,
                            fromY,
                            fromZ,
                            deltaX,
                            deltaY,
                            deltaZ,
                            lengthSquared) > radius) {
                            continue;
                        }

                        Block block = world.getBlock(x, y, z);
                        if (block.isAir(world, x, y, z)) continue;
                        if (block.getMaterial()
                            .isLiquid()) continue;
                        if (block.getBlockHardness(world, x, y, z) < 0.0F) continue;
                        if (block.getExplosionResistance(source) > MAX_RESISTANCE) continue;

                        int meta = world.getBlockMetadata(x, y, z);
                        if (!mayBreak(world, player, x, y, z, block, meta)) continue;

                        world.setBlockToAir(x, y, z);

                        double distanceToImpact = distance(x + 0.5D, y + 0.5D, z + 0.5D, toX, toY, toZ);
                        if (distanceToImpact <= EFFECT_RADIUS) {
                            // Id 2001 is vanilla's "block was destroyed" effect: every client spawns the block's own
                            // break particles for it, which is exactly what an explosion looks like.
                            world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
                        }
                    }
                }
            }
        }
    }

    /**
     * Asks the world's protection mods whether the block may go. The tunnel is not a pickaxe, but it is caused by one,
     * so the standard {@link BlockEvent.BreakEvent} is posted for a shot fired by a player and a cancelled event leaves
     * the block standing. Blocks destroyed by anything else (a dispenser, a mob) are not posted, exactly like the event
     * only ever covers players.
     */
    private static boolean mayBreak(World world, EntityPlayer player, int x, int y, int z, Block block, int meta) {
        if (player == null) return true;

        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(x, y, z, world, block, meta, player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    /**
     * Distance from a point to the segment {@code from .. from + delta}, the standard clamped projection.
     * {@code lengthSquared} is handed in because the caller already has it; a zero length segment cannot happen (the
     * caller returns early for one).
     */
    private static double distanceToSegment(double pointX, double pointY, double pointZ, double fromX, double fromY,
        double fromZ, double deltaX, double deltaY, double deltaZ, double lengthSquared) {
        double offsetX = pointX - fromX, offsetY = pointY - fromY, offsetZ = pointZ - fromZ;
        double t = (offsetX * deltaX + offsetY * deltaY + offsetZ * deltaZ) / lengthSquared;
        if (t < 0.0D) t = 0.0D;
        else if (t > 1.0D) t = 1.0D;

        return distance(pointX, pointY, pointZ, fromX + deltaX * t, fromY + deltaY * t, fromZ + deltaZ * t);
    }

    /** Distance between two points, the same maths {@code Vec3#distanceTo} does without allocating one. */
    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double x = x1 - x2, y = y1 - y2, z = z1 - z2;
        return Math.sqrt(x * x + y * y + z * z);
    }
}
