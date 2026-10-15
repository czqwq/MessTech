package com.MessTech.common.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.MessTech.common.explosion.MTExplosionDE;
import com.MessTech.common.explosion.MTExplosionTunnel;
import com.MessTech.common.items.MTItemVoidstarBow;
import com.MessTech.common.particle.MTVoidstarFx;
import com.MessTech.common.process.MTProcessHandler;
import com.MessTech.common.util.MTBowKill;
import com.MessTech.common.util.MTKillBase;
import com.MessTech.init.Config;
import com.MessTech.init.MessTech;

import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.IThrowableEntity;

/**
 * The arrow of the Voidstar Piercer (混沌终焉·虚无贯星弓).
 * <p>
 * Four things separate it from a vanilla arrow, all of them in {@link #onUpdate}:
 * <ul>
 * <li><b>It flies dead straight.</b> The launch direction and the launch speed are kept, and the motion is written
 * back from them at the start and at the end of every tick. Vanilla's arrow loses 1% of its speed and 0.05 blocks
 * per tick of altitude at the end of each tick ({@code motion *= 0.99; motionY -= 0.05}), which is what makes it
 * drop; overwriting the motion every tick means neither the drag nor the gravity is ever carried into the next
 * tick's move, so the arrow travels exactly one {@code aim * speed} step per tick from the bow to whatever it
 * reaches. The vertical part of that step is the launch pitch, so a shot at a mob 50 blocks away lands where the
 * crosshair points, not 12 blocks below it.
 * <p>
 * The <b>server</b> owns that aim. A client is never told it - the fields below are not synced - so the client copy
 * takes the aim from the velocity the server sends instead ({@link #adoptSyncedAim}), which is the only reason this
 * class implements {@link IThrowableEntity}: FML only writes the velocity of a throwable into the spawn packet, so
 * without the interface a client copy starts with a motion of zero and flies the arrow along this class's default
 * aim, i.e. due south and perfectly level, until the server's position updates drag it onto the real line. That is
 * what a "curving arrow" (a model that starts out sideways and then swings onto the aim) looks like.</li>
 * <li><b>It drills on through what it hits.</b> A block in the way does not end the flight: the arrow is stopped by
 * it for a tick or two - vanilla catches it with its own ray trace - and {@link #onBlockContact} then blasts the
 * piece of tunnel between the last blast and that block, after which the arrow digs on by itself, because the block
 * it was stuck against is gone and vanilla's own in-ground branch releases the arrow again. Every contact therefore
 * extends the same tunnel, and one shot fired into a mountain carves a corridor all the way through it until the
 * arrow runs out of flight ticks ({@link #MAX_FLIGHT_TICKS}, 600 blocks at a full draw). The pieces are handed out
 * back to back ({@link #blastX} ...), so no block is ever scanned twice.</li>
 * <li><b>Anything it hits dies for good.</b> A hit on an entity is vanilla's (damage, knockback, the sound) and then
 * {@link MTBowKill}, the bow's branch of the {@link MTKillBase} ladder, finishes it - through armour, shields, entity
 * invulnerability and "second life" items. The target is found again with the same intercept test vanilla used
 * ({@link #findHitEntity}). The same happens when an entity refuses the hit and vanilla bounces the arrow off it:
 * the ladder does not care about the refusal.</li>
 * <li><b>It trails.</b> While it is in the air the client spawns chaos sparks along the step it just took
 * ({@link MTVoidstarFx}), a smaller burst at every block it chews through, and a large one when the flight ends.</li>
 * </ul>
 * The arrow cannot be picked up ({@code canBePickedUp = 0}), and one that hits nothing at all is removed after
 * {@link #MAX_FLIGHT_TICKS} ticks.
 */
public class MTEntityVoidstarArrow extends EntityArrow implements IThrowableEntity {

    /** Entity id inside MessTech, used by {@link #register()}. */
    private static final int ENTITY_ID = 1;

    /**
     * Data watcher ids of the muzzle position, i.e. where the tunnel of a shot starts. Every client simulates the
     * flight itself (as it does for vanilla arrows), so the spark bursts are spawned locally - only the destruction
     * of the blocks is the server's - and the muzzle position is what the client cannot work out on its own.
     * {@code EntityArrow} uses id 16 for its own flags, everything above that is free.
     */
    private static final int DW_START_X = 20, DW_START_Y = 21, DW_START_Z = 22;

    /** Data watcher id of the sub-mode of the shot; see {@link #getMode()}. */
    private static final int DW_MODE = 23;

    /** Speed of the arrow in blocks per tick at the weakest draw that fires at all, and at a full draw. */
    public static final double MIN_SPEED = 1.2D;
    public static final double MAX_SPEED = 3.0D;

    /**
     * Ticks the arrow may fly before it gives up and vanishes (no impact, no tunnel). At
     * {@link #MAX_SPEED} this is 600 blocks, i.e. further than a chunk is tracked, so it only ever catches an arrow
     * fired into the open sky.
     */
    private static final int MAX_FLIGHT_TICKS = 200;

    /**
     * Fraction of its own speed the arrow has to have advanced by for a tick to count as an open one. Vanilla backs
     * the arrow off by 0.05 blocks along the step when it stops it against a block, so a tick that ended on a wall
     * always comes out shorter than the step - by 4.2% of the speed even at the weakest draw that fires
     * ({@code 0.05 / MIN_SPEED}) and by 1.7% at a full one - while an open tick advances by exactly the speed. The
     * threshold sits between the two.
     */
    private static final double STOPPED_SPEED_FRACTION = 0.995D;

    /**
     * How many ticks of being held up by blocks end the flight. Two of them always belong to a contact that went
     * through (the tick vanilla stopped the arrow, and the tick it releases itself again once the block is gone), so
     * anything above that is a wall the tunnel cannot open - bedrock, a protection plugin, an explosion resistant
     * block - and the arrow gives up instead of sitting there for the rest of its life.
     */
    private static final int MAX_STUCK_TICKS = 6;

    /**
     * Below this speed the client has not been told how the arrow flies yet (see {@link #adoptSyncedAim}). A tenth of
     * {@link #MIN_SPEED} is far below anything a shot can have and far above the rounding of the packet, which sends
     * the velocity in 1/8000 blocks per tick.
     */
    private static final double MIN_AIM_LENGTH = 0.02D;

    /** The launch direction (a unit vector) and speed the arrow holds for its whole flight. */
    private double aimX = 0.0D, aimY = 0.0D, aimZ = 1.0D;
    private double speed = MAX_SPEED;

    /** Where the arrow left the bow; the tunnel is blasted from here onwards. */
    private double startX, startY, startZ;

    /** The far end of the tunnel blasted so far: the next piece starts here, so nothing is scanned twice. */
    private double blastX, blastY, blastZ;

    /** How many ticks the arrow has been held up by blocks; see {@link #MAX_STUCK_TICKS}. */
    private int stuckTicks;

    /** How many ticks the arrow has been flying. */
    private int flightTicks;

    /** The sub-mode of the bow that shot this arrow; {@link MTItemVoidstarBow#MODE_DE} explodes on the first block. */
    private int mode = MTItemVoidstarBow.MODE_PIERCE;

    public MTEntityVoidstarArrow(World world) {
        super(world);
        setSize(0.5F, 0.5F);
    }

    /**
     * The arrow of one shot: launched from the shooter's eyes along exactly the way they are looking.
     * <p>
     * Vanilla's own spread ({@code setThrowableHeading} with a deviation of 1.0 adds up to 0.0075 per axis of
     * random direction) is deliberately dropped here: the item's promise is the straight line, and re-adding an
     * aim error on top of the enchantment would be the one thing the player cannot compensate for.
     *
     * @param world   the world to spawn the arrow in
     * @param shooter the player who shot it
     * @param draw    the draw of the bow, 0..1, i.e. vanilla's {@code f} of {@code ItemBow#onPlayerStoppedUsing};
     *                it scales the speed between {@link #MIN_SPEED} and {@link #MAX_SPEED}
     * @param mode    the sub-mode of the bow that shot it; only {@link MTItemVoidstarBow#MODE_DE} changes what the
     *                arrow does when it hits a block, see {@link #onBlockContact}
     */
    public MTEntityVoidstarArrow(World world, EntityPlayer shooter, float draw, int mode) {
        super(world, shooter, draw);
        setSize(0.5F, 0.5F);

        // The arrow is magic, not ammunition: it never lies around to be picked up again.
        this.canBePickedUp = 0;

        Vec3 look = shooter.getLook(1.0F);
        double length = Math.sqrt(look.xCoord * look.xCoord + look.yCoord * look.yCoord + look.zCoord * look.zCoord);
        if (length < 1.0E-4D) {
            // A zero length look vector cannot happen for a player, but a division by it would poison the flight.
            look = Vec3.createVectorHelper(0.0D, 0.0D, 1.0D);
            length = 1.0D;
        }

        this.aimX = look.xCoord / length;
        this.aimY = look.yCoord / length;
        this.aimZ = look.zCoord / length;
        this.speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * MathHelper.clamp_float(draw, 0.0F, 1.0F);
        this.publishMode(mode);

        this.startX = this.posX;
        this.startY = this.posY;
        this.startZ = this.posZ;
        this.blastX = this.startX;
        this.blastY = this.startY;
        this.blastZ = this.startZ;
        publishStart();

        applyAim();
        applyAimToRotation();
    }

    /** Registers the entity with FML; called by the common proxy during preInit. */
    public static void register() {
        EntityRegistry.registerModEntity(
            MTEntityVoidstarArrow.class,
            "MTEntityVoidstarArrow",
            ENTITY_ID,
            MessTech.instance,
            64,
            10,
            true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        getDataWatcher().addObject(DW_START_X, Float.valueOf(0.0F));
        getDataWatcher().addObject(DW_START_Y, Float.valueOf(0.0F));
        getDataWatcher().addObject(DW_START_Z, Float.valueOf(0.0F));
        getDataWatcher().addObject(DW_MODE, Byte.valueOf((byte) MTItemVoidstarBow.MODE_PIERCE));
    }

    /** Writes the muzzle position into the data watcher, which is how a client learns it. */
    private void publishStart() {
        getDataWatcher().updateObject(DW_START_X, Float.valueOf((float) this.startX));
        getDataWatcher().updateObject(DW_START_Y, Float.valueOf((float) this.startY));
        getDataWatcher().updateObject(DW_START_Z, Float.valueOf((float) this.startZ));
    }

    /** @return the x the arrow left the bow at, read off the data watcher on the client. */
    public double getStartX() {
        return this.worldObj.isRemote ? getDataWatcher().getWatchableObjectFloat(DW_START_X) : this.startX;
    }

    /** @return the y the arrow left the bow at, read off the data watcher on the client. */
    public double getStartY() {
        return this.worldObj.isRemote ? getDataWatcher().getWatchableObjectFloat(DW_START_Y) : this.startY;
    }

    /** @return the z the arrow left the bow at, read off the data watcher on the client. */
    public double getStartZ() {
        return this.worldObj.isRemote ? getDataWatcher().getWatchableObjectFloat(DW_START_Z) : this.startZ;
    }

    /** Writes the sub-mode of the shot into the data watcher, so the client can draw it the way the server flies it. */
    private void publishMode(int mode) {
        this.mode = mode < 0 || mode >= MTItemVoidstarBow.MODE_COUNT ? MTItemVoidstarBow.MODE_PIERCE : mode;
        getDataWatcher().updateObject(DW_MODE, Byte.valueOf((byte) this.mode));
    }

    /** @return the sub-mode of the bow that shot this arrow, read off the data watcher on the client. */
    public int getMode() {
        return this.worldObj.isRemote ? getDataWatcher().getWatchableObjectByte(DW_MODE) : this.mode;
    }

    /** @return whether this arrow explodes once on the first block it touches instead of drilling through it. */
    public boolean isDeMode() {
        return getMode() == MTItemVoidstarBow.MODE_DE;
    }

    @Override
    public void onUpdate() {
        boolean hasAim = true;

        if (this.worldObj.isRemote) {
            MTVoidstarFx.spawnTrail(this);
            hasAim = adoptSyncedAim();
        }

        if (this.flightTicks++ > MAX_FLIGHT_TICKS) {
            finish(this.posX, this.posY, this.posZ);
            return;
        }

        if (!hasAim) {
            // The server has not told this client how the arrow flies yet (see adoptSyncedAim). Vanilla recomputes
            // the rotation from the motion, and a motion of zero is yaw 0 and pitch 0 - due south and level - so the
            // tick is spent holding the orientation the spawn packet brought instead of swinging away and back.
            float yaw = this.rotationYaw, pitch = this.rotationPitch;
            super.onUpdate();
            this.rotationYaw = this.prevRotationYaw = yaw;
            this.rotationPitch = this.prevRotationPitch = pitch;
            return;
        }

        // The step of this tick. It is written again at the end of the tick, so the vanilla drag and gravity never
        // reach the next move - see the class comment.
        applyAim();

        double previousX = this.posX, previousY = this.posY, previousZ = this.posZ;
        super.onUpdate();

        // The one thing vanilla kills an arrow with down here is a hit on an entity: it damages the target and
        // removes the arrow. That hit - and a hit an entity refused, which leaves the arrow bounced instead of dead -
        // is what the bow's true kill takes over.
        boolean bounced = this.motionX * this.aimX + this.motionY * this.aimY + this.motionZ * this.aimZ < 0.0D;
        if (this.isDead || bounced) {
            onEntityHit();
            return;
        }

        applyAim();

        double advance = distance(previousX, previousY, previousZ, this.posX, this.posY, this.posZ);
        if (advance >= this.speed * STOPPED_SPEED_FRACTION) {
            this.stuckTicks = 0;
            return;
        }

        onBlockContact();
    }

    /**
     * The client side half of the flight: the aim of a shot is <b>not</b> part of the spawn packet, so a client has
     * to take it from the velocity the server sends - which is exactly what {@link IThrowableEntity} is implemented
     * for. Vanilla's own {@code EntityArrow} is not an {@code IThrowableEntity}, and FML only writes the velocity of
     * one into the spawn packet ({@code FMLMessage.EntitySpawnMessage#toBytes}, the {@code instanceof
     * IThrowableEntity} branch; the client reads it back in {@code EntitySpawnHandler#spawnEntity}). Without the
     * interface a freshly created client copy of the arrow has a motion of zero and, once this class wrote its
     * default aim of due south and perfectly level into the motion, the copy would fly off to the side until the
     * position updates of the server dragged it onto the real line - the "curving arrow" this fixes.
     *
     * @return whether an aim could be taken; {@code false} while the first velocity of the server is still on its
     *         way, which is at most the first tick of the flight
     */
    private boolean adoptSyncedAim() {
        double length = Math
            .sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ);
        if (length < MIN_AIM_LENGTH) return false;

        this.aimX = this.motionX / length;
        this.aimY = this.motionY / length;
        this.aimZ = this.motionZ / length;
        this.speed = length;
        return true;
    }

    /**
     * {@link IThrowableEntity}: the shooter of this arrow is what FML sends along with the spawn packet, and what
     * makes it put the velocity in that packet at all (see {@link #adoptSyncedAim}).
     */
    @Override
    public Entity getThrower() {
        return this.shootingEntity;
    }

    @Override
    public void setThrower(Entity entity) {
        this.shootingEntity = entity;
    }

    /**
     * The end of a hit on an entity: the target of the shot is killed by the bow's branch of the true kill ladder
     * ({@link MTBowKill}), which is the same death the tunnel hands out. The target has to be found again because
     * vanilla does not tell us what it hit: the intercept test of {@link #findHitEntity} is the one vanilla itself
     * ran a moment ago, on the same step, with the same 0.3 block expansion, so it finds the same entity.
     */
    private void onEntityHit() {
        if (this.worldObj.isRemote) {
            MTVoidstarFx.spawnImpact(this, this.posX, this.posY, this.posZ);
        } else {
            EntityLivingBase victim = findHitEntity();
            if (victim != null) {
                // The branch refuses the shooter on its own, so a shot can never kill the player that fired it.
                MTBowKill.INSTANCE.kill(victim, asThrower(this.shootingEntity), this);
            }
        }

        setDead();
    }

    /** The shooter as a living entity, or {@code null} when there is none (an arrow that a command spawned). */
    private static EntityLivingBase asThrower(Entity shooter) {
        return shooter instanceof EntityLivingBase ? (EntityLivingBase) shooter : null;
    }

    /**
     * @return the living entity this tick's step ran into, or {@code null} when there is none (an arrow that was
     *         removed for another reason, or an entity that moved away between the two scans).
     */
    private EntityLivingBase findHitEntity() {
        Vec3 from = Vec3.createVectorHelper(this.prevPosX, this.prevPosY, this.prevPosZ);
        Vec3 to = Vec3.createVectorHelper(
            from.xCoord + this.aimX * this.speed,
            from.yCoord + this.aimY * this.speed,
            from.zCoord + this.aimZ * this.speed);

        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            Math.min(from.xCoord, to.xCoord) - 1.0D,
            Math.min(from.yCoord, to.yCoord) - 1.0D,
            Math.min(from.zCoord, to.zCoord) - 1.0D,
            Math.max(from.xCoord, to.xCoord) + 1.0D,
            Math.max(from.yCoord, to.yCoord) + 1.0D,
            Math.max(from.zCoord, to.zCoord) + 1.0D);

        List<EntityLivingBase> candidates = this.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);
        EntityLivingBase best = null;
        double bestDistance = 0.0D;

        for (EntityLivingBase candidate : candidates) {
            if (candidate == this.shootingEntity) continue;
            if (!candidate.canBeCollidedWith()) continue;

            // The same test vanilla's own scan does: vanilla expands every candidate's box by 0.3 blocks and takes
            // the intercept closest to the arrow.
            MovingObjectPosition intercept = candidate.boundingBox.expand(0.3D, 0.3D, 0.3D)
                .calculateIntercept(from, to);
            if (intercept == null) continue;

            double distance = from.distanceTo(intercept.hitVec);
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best;
    }

    /**
     * A block stopped the arrow. What happens here is the whole difference between the bow's two sub-modes:
     * <ul>
     * <li><b>Pierce mode</b> (the default): the piece of tunnel between the last blast and the face in front of the
     * arrow is blasted, and the arrow digs on. The digging works on its own, because the block vanilla stuck the
     * arrow into is the one that was just destroyed: on the next tick vanilla's in-ground branch finds a different
     * block there, clears its own in-ground flag and the arrow flies again - with the aim written back by
     * {@link #applyAim}. An arrow that stays stuck (the wall does not give way, see {@link #MAX_STUCK_TICKS}) ends
     * its flight here.</li>
     * <li><b>DE mode</b>: one explosion, at this contact, and the arrow is gone. It is a
     * {@link MTExplosionDE} - the port of Draconic Evolution's reactor explosion, fireball included - of
     * {@link Config#VOIDSTAR_BOW_DE_POWER} power, centred on the block the arrow touched, and nothing of the tunnel
     * machinery runs at all: no piece is blasted, no block is drilled, and the arrow does not fly on.</li>
     * </ul>
     */
    private void onBlockContact() {
        // The face the arrow is pressed against: the ray from where it sits, along its own aim.
        MovingObjectPosition hit = this.worldObj.func_147447_a(
            Vec3.createVectorHelper(this.posX, this.posY, this.posZ),
            Vec3.createVectorHelper(
                this.posX + this.aimX * this.speed,
                this.posY + this.aimY * this.speed,
                this.posZ + this.aimZ * this.speed),
            false,
            true,
            false);

        double faceX = hit != null ? hit.hitVec.xCoord : this.posX;
        double faceY = hit != null ? hit.hitVec.yCoord : this.posY;
        double faceZ = hit != null ? hit.hitVec.zCoord : this.posZ;

        if (this.worldObj.isRemote) {
            MTVoidstarFx.spawnPierceBurst(this, faceX, faceY, faceZ);
            return;
        }

        if (isDeMode()) {
            detonate(faceX, faceY, faceZ);
            return;
        }

        MTExplosionTunnel.blast(
            this.worldObj,
            this.shootingEntity,
            this,
            this.blastX,
            this.blastY,
            this.blastZ,
            faceX,
            faceY,
            faceZ);
        this.blastX = faceX;
        this.blastY = faceY;
        this.blastZ = faceZ;

        if (++this.stuckTicks > MAX_STUCK_TICKS) {
            finish(faceX, faceY, faceZ);
        }
    }

    /**
     * The one explosion of DE mode: a {@link MTExplosionDE} of {@link Config#VOIDSTAR_BOW_DE_POWER} power, centred on
     * the block the given point is in, and the arrow is removed.
     * <p>
     * The explosion carries its own fireball ({@link MTEntityDEBlast} is spawned by {@code MTExplosionDE} itself), so
     * no burst of this mod's own particles is spawned on top of it - unlike pierce mode, where the arrow's own
     * sparks are the only visual a drilled block gets. Server side only, like every other block change of the arrow.
     */
    private void detonate(double hitX, double hitY, double hitZ) {
        if (!this.worldObj.isRemote) {
            MTProcessHandler.addProcess(
                new MTExplosionDE(
                    this.worldObj,
                    MathHelper.floor_double(hitX),
                    MathHelper.floor_double(hitY),
                    MathHelper.floor_double(hitZ),
                    Config.VOIDSTAR_BOW_DE_POWER));
        }

        setDead();
    }

    /**
     * The end of the flight - the arrow ran out of ticks, or was held up by a wall it cannot open. Whatever is still
     * between the last blast and this point is blasted, the bang is played and the arrow is removed.
     * <p>
     * In DE mode this is where a shot that hit nothing at all ends: the arrow never stops on a block there (it
     * explodes on the first one), so the only way to get here is the flight running out of range - and a bomb arrow
     * that simply vanished after 600 blocks would be a bug, so it detonates instead.
     *
     * @param hitX the point the flight ended at
     * @param hitY see {@code hitX}
     * @param hitZ see {@code hitX}
     */
    private void finish(double hitX, double hitY, double hitZ) {
        if (this.worldObj.isRemote) {
            MTVoidstarFx.spawnImpact(this, hitX, hitY, hitZ);
            return;
        }

        if (isDeMode()) {
            detonate(hitX, hitY, hitZ);
            return;
        }

        MTExplosionTunnel
            .blast(this.worldObj, this.shootingEntity, this, this.blastX, this.blastY, this.blastZ, hitX, hitY, hitZ);
        this.worldObj.playSoundEffect(hitX, hitY, hitZ, "random.explode", 2.0F, 0.8F);

        setDead();
    }

    /**
     * Writes the launch direction and speed back into the motion, undoing everything vanilla did to it. Called
     * before the position advance, where it is what makes the flight straight, and again after it, where it keeps
     * the motion the server reports to its clients constant (a motion that changed every tick would otherwise be
     * re-sent as a velocity update every tick).
     */
    private void applyAim() {
        this.motionX = this.aimX * this.speed;
        this.motionY = this.aimY * this.speed;
        this.motionZ = this.aimZ * this.speed;
    }

    /** Points the rendered arrow along its aim, so it is drawn nose first at every speed. */
    private void applyAimToRotation() {
        float horizontal = MathHelper.sqrt_double(this.aimX * this.aimX + this.aimZ * this.aimZ);
        this.rotationYaw = this.prevRotationYaw = (float) (Math.atan2(this.aimX, this.aimZ) * 180.0D / Math.PI);
        this.rotationPitch = this.prevRotationPitch = (float) (Math.atan2(this.aimY, (double) horizontal) * 180.0D
            / Math.PI);
    }

    /** Distance between two points, so the shortcut above does not have to build a {@link Vec3} for it. */
    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double x = x1 - x2, y = y1 - y2, z = z1 - z2;
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Saves the flight, so an arrow that was saved to disk mid-air (a chunk unload) resumes on the same line instead
     * of falling out of the sky with a default aim, and so the tunnel it already opened is not blasted again.
     */
    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setDouble("MTStartX", this.startX);
        tag.setDouble("MTStartY", this.startY);
        tag.setDouble("MTStartZ", this.startZ);
        tag.setDouble("MTBlastX", this.blastX);
        tag.setDouble("MTBlastY", this.blastY);
        tag.setDouble("MTBlastZ", this.blastZ);
        tag.setDouble("MTAimX", this.aimX);
        tag.setDouble("MTAimY", this.aimY);
        tag.setDouble("MTAimZ", this.aimZ);
        tag.setDouble("MTSpeed", this.speed);
        tag.setInteger("MTStuckTicks", this.stuckTicks);
        tag.setInteger("MTFlightTicks", this.flightTicks);
        tag.setInteger("MTMode", this.mode);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        this.startX = tag.getDouble("MTStartX");
        this.startY = tag.getDouble("MTStartY");
        this.startZ = tag.getDouble("MTStartZ");
        this.blastX = tag.getDouble("MTBlastX");
        this.blastY = tag.getDouble("MTBlastY");
        this.blastZ = tag.getDouble("MTBlastZ");
        this.aimX = tag.getDouble("MTAimX");
        this.aimY = tag.getDouble("MTAimY");
        this.aimZ = tag.getDouble("MTAimZ");
        this.speed = tag.getDouble("MTSpeed");
        this.stuckTicks = tag.getInteger("MTStuckTicks");
        this.flightTicks = tag.getInteger("MTFlightTicks");
        publishMode(tag.getInteger("MTMode"));
        publishStart();
    }
}
