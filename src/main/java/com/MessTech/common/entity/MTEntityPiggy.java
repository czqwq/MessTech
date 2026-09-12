package com.MessTech.common.entity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.MessTech.common.util.MTDynamicItemHelper.Effect;
import com.MessTech.common.util.MTTrueKill;
import com.MessTech.init.MessTech;

import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * The thrown "A Piggy".
 * <p>
 * A plain {@link EntityThrowable} with one job: whatever it hits is killed for good (see {@link MTTrueKill}). The
 * effect (the damage value of the thrown stack) travels in a data watcher entry, so the client draws the same icon
 * the player was holding.
 */
public class MTEntityPiggy extends EntityThrowable {

    /**
     * Data watcher id of the effect ordinal. {@code EntityThrowable} itself does not use any id, so everything above
     * the two ids of {@code Entity} is free.
     */
    private static final int DW_EFFECT = 20;

    /** Entity id inside MessTech, used by {@link #register()}. */
    private static final int ENTITY_ID = 0;

    /** NBT key of the effect, so a piggy in the air survives a chunk save with its look. */
    private static final String NBT_EFFECT = "Effect";

    public MTEntityPiggy(World world) {
        super(world);
        setSize(0.25F, 0.25F);
    }

    public MTEntityPiggy(World world, EntityLivingBase thrower, int effect) {
        super(world, thrower);
        setSize(0.25F, 0.25F);
        setEffect(effect);
    }

    /** Registers the entity with FML; called by the common proxy during preInit. */
    public static void register() {
        EntityRegistry
            .registerModEntity(MTEntityPiggy.class, "MTEntityPiggy", ENTITY_ID, MessTech.instance, 64, 10, true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        getDataWatcher().addObject(DW_EFFECT, 0);
    }

    /** @return the {@link Effect} ordinal this piggy was thrown with. */
    public int getEffect() {
        return getDataWatcher().getWatchableObjectInt(DW_EFFECT);
    }

    /** Stores the effect. Out of range values fall back to the plain look. */
    public void setEffect(int effect) {
        getDataWatcher().updateObject(
            DW_EFFECT,
            Effect.byIndex(effect)
                .ordinal());
    }

    @Override
    protected void onImpact(MovingObjectPosition hit) {
        if (this.worldObj.isRemote) return;

        if (hit.entityHit != null) {
            MTTrueKill.kill(hit.entityHit, getThrower(), this);
        }

        // Vanilla style: the server only flags the pop, every client spawns the particles itself.
        this.worldObj.setEntityState(this, (byte) 3);
        setDead();
    }

    @Override
    public void handleHealthUpdate(byte id) {
        if (id != 3) {
            super.handleHealthUpdate(id);
            return;
        }

        for (int i = 0; i < 8; i++) {
            this.worldObj.spawnParticle(
                "heart",
                this.posX,
                this.posY,
                this.posZ,
                this.rand.nextGaussian() * 0.2D,
                this.rand.nextDouble() * 0.2D,
                this.rand.nextGaussian() * 0.2D);
        }
        playSound("mob.pig.say", 1.0F, 1.4F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger(NBT_EFFECT, getEffect());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        setEffect(tag.getInteger(NBT_EFFECT));
    }
}
