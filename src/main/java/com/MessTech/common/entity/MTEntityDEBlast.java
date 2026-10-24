package com.MessTech.common.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.MessTech.common.render.MTDEBlastRenderer;
import com.MessTech.init.MessTech;

import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * The visible half of a {@link com.MessTech.common.explosion.MTExplosionDE}: the fireball of Draconic Evolution's
 * reactor explosion.
 * <p>
 * <b>Why an entity.</b> The port ({@code MTExplosionDE} and its traces) is a server side process with no visual, like
 * DE's own; DE's visual is client side - {@code TileReactorCore#goBoom} sends {@code (int) (power * 10F)} to every
 * player within 512 blocks, and the client answers with {@code Particles.ReactorExplosionParticle}
 * ({@code TileReactorCore.receiveObjectFromServer}, case 100). MessTech has no packet channel, but an entity is synced
 * for free: spawning it replaces that message, {@link MTDEBlastRenderer} the particle.
 * <p>
 * It draws nothing, has no collision ({@code noClip}, zero size), is never saved and is always rendered
 * ({@link #isInRangeToRenderDist}): a nuke has to be visible from far away. Server side it only ages and dies; the
 * client plays the boom at tick 3 ({@code DraconicEvolution:fusionExplosion} in DE; this mod uses the loudest two
 * vanilla explosion layers - see {@link #BOOM_VOLUME}).
 */
public class MTEntityDEBlast extends Entity {

    /** Entity id inside MessTech, used by {@link #register()}; the arrow took id 1. */
    private static final int ENTITY_ID = 2;

    /** Data watcher id of the fireball's maximum size in blocks, i.e. {@code power * 10} on the server. */
    private static final int DW_MAX_SIZE = 20;

    /**
     * Grow time as a multiple of the maximum size: DE's particle lives until {@code size > maxSize * 1.2}, so power
     * 1 burns for 12 ticks.
     */
    private static final double LIFETIME_FACTOR = 1.2D;

    /**
     * Tick at which the boom is played. DE plays it from the particle's {@code onUpdate} at age 3 - on every client
     * that sees the effect, not from the server, so players away from the blast still hear it.
     */
    private static final int BOOM_TICK = 3;

    /** Volume of the boom. DE uses 100 for its own sound; two vanilla layers are used here instead. */
    private static final float BOOM_VOLUME = 24.0F;

    public MTEntityDEBlast(World world) {
        super(world);
        this.noClip = true;
        this.setSize(0.0F, 0.0F);
        this.preventEntitySpawning = false;
    }

    /**
     * Registers the entity with FML; called by the common proxy during preInit. Tracking range 512, update frequency
     * 20: no motion, so one position update per second is enough - but the spawn packet has to reach every client.
     */
    public static void register() {
        EntityRegistry
            .registerModEntity(MTEntityDEBlast.class, "MTEntityDEBlast", ENTITY_ID, MessTech.instance, 512, 20, false);
    }

    @Override
    protected void entityInit() {
        this.dataWatcher.addObject(DW_MAX_SIZE, 10);
    }

    /** Turns this spawn into a fireball of {@code size} blocks, i.e. what DE's packet carries. */
    public void setMaxSize(double size) {
        this.dataWatcher.updateObject(DW_MAX_SIZE, (int) size);
    }

    /** The maximum size of the fireball in blocks, i.e. {@code power * 10} of the explosion that spawned it. */
    public int getMaxSize() {
        return this.dataWatcher.getWatchableObjectInt(DW_MAX_SIZE);
    }

    /** How far the fireball has grown, in blocks; one block per tick, exactly like DE's particle. */
    public double getSize(float partialTicks) {
        return Math.min((double) this.ticksExisted + partialTicks, getMaxSize() * LIFETIME_FACTOR);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.worldObj.isRemote && this.ticksExisted == BOOM_TICK) {
            // Two vanilla explosion layers instead of DE's fusion explosion: a deep one for the pressure wave, a
            // higher one for the crack, both far louder than a TNT blast.
            this.worldObj.playSound(this.posX, this.posY, this.posZ, "random.explode", BOOM_VOLUME, 0.45F, false);
            this.worldObj.playSound(this.posX, this.posY, this.posZ, "random.explode", BOOM_VOLUME * 0.5F, 1.1F, false);
        }

        if ((double) this.ticksExisted > getMaxSize() * LIFETIME_FACTOR) {
            setDead();
        }
    }

    /** Drawn from as far as the client can see, like DE's {@code EntityChaosVortex#isInRangeToRenderDist}. */
    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true;
    }

    /** Nothing to save: the fireball is gone a second after the blast. */
    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}
}
