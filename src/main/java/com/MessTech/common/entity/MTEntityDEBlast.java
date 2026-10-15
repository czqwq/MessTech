package com.MessTech.common.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.MessTech.common.render.MTDEBlastRenderer;
import com.MessTech.init.MessTech;

import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * The visible half of a {@link com.MessTech.common.explosion.MTExplosionDE}, i.e. the fireball of Draconic
 * Evolution's reactor explosion.
 * <p>
 * <b>Why an entity at all.</b> Our port of DE's explosion is a server side process - {@code MTExplosionDE} and its
 * traces - and DE itself has no visual in that process either. In DE the visual is a separate, client side thing:
 * {@code TileReactorCore#goBoom} sends {@code (int) (power * 10F)} to every player within 512 blocks and the client
 * answers it with {@code new Particles.ReactorExplosionParticle(world, x, y, z, size)}
 * ({@code TileReactorCore.receiveObjectFromServer}, case 100), i.e. an effect that grows one block per tick for
 * {@code size * 1.2} ticks. MessTech has no packet channel of its own, and an entity is exactly a synced thing the
 * client learns about for free: spawning this entity is the port's replacement for that packet, and
 * {@link MTDEBlastRenderer} is the replacement for the particle.
 * <p>
 * Like DE's effect it draws nothing itself, keeps no collision ({@code noClip}, zero size), is never saved and is
 * always in range to be rendered ({@link #isInRangeToRenderDist}): a nuke has to be visible from far away.
 * <p>
 * Server side the entity only ages and dies; the client additionally plays the boom, which is what DE's particle
 * does at its third tick ({@code DraconicEvolution:fusionExplosion} there; this mod has no such sound, so the
 * loudest two vanilla explosion layers stand in for it - see {@link #BOOM_VOLUME}).
 */
public class MTEntityDEBlast extends Entity {

    /** Entity id inside MessTech, used by {@link #register()}; the arrow took id 1. */
    private static final int ENTITY_ID = 2;

    /** Data watcher id of the fireball's maximum size in blocks, i.e. {@code power * 10} on the server. */
    private static final int DW_MAX_SIZE = 20;

    /**
     * How long the fireball grows, as a multiple of its maximum size: DE's particle lives until
     * {@code size > maxSize * 1.2}, so a power 1 explosion of this mod burns for 12 ticks.
     */
    private static final double LIFETIME_FACTOR = 1.2D;

    /**
     * Tick at which the boom is played. DE plays its sound from the particle's own {@code onUpdate} at age 3, i.e.
     * on every client that can see the effect and not from the server, which is what keeps a nuke from being silent
     * for players who were not near the blast when it went off.
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
     * Registers the entity with FML; called by the common proxy during preInit.
     * <p>
     * The tracking range is 512 blocks and the update frequency 20 ticks: the entity has no motion at all, so a
     * position update every second is more than enough, and what has to reach the clients is the explosion itself -
     * which is the spawn packet nobody is allowed to miss.
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
            // Two layers of the vanilla explosion sound instead of DE's own fusion explosion: a deep one for the
            // pressure wave and a higher one for the crack, both far louder than a TNT blast.
            this.worldObj.playSound(this.posX, this.posY, this.posZ, "random.explode", BOOM_VOLUME, 0.45F, false);
            this.worldObj.playSound(this.posX, this.posY, this.posZ, "random.explode", BOOM_VOLUME * 0.5F, 1.1F, false);
        }

        if ((double) this.ticksExisted > getMaxSize() * LIFETIME_FACTOR) {
            setDead();
        }
    }

    /**
     * A nuke is drawn from as far away as the client can see it, which is what DE does for the same effect
     * ({@code EntityChaosVortex#isInRangeToRenderDist} returns {@code true} as well).
     */
    @Override
    public boolean isInRangeToRenderDist(double distance) {
        return true;
    }

    /** Nothing to save: the fireball only ever exists between the blast and a second later. */
    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}
}
