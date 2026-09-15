package com.MessTech.common.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.MessTech.init.MessTech;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.util.GTUtility;

/**
 * The "true kill" of the thrown piggy: kills a target that would normally survive, i.e. through armour, energy
 * shields, entity level invulnerability or a "second life" item. The piggy kills whatever it lands on, the player
 * that threw it into the sky included.
 * <p>
 * It is a ladder of attempts, from the most polite one to the most direct one, because every kind of protection has
 * to be bypassed at the point where it actually gives up:
 * <ol>
 * <li>{@code attackEntityFrom} with {@link #OVERKILL} damage and the damage type of an "infinity" weapon. Mods that
 * cancel the hurt/attack/death events while a complete armour set is worn skip sources of exactly that damage type,
 * and the shield code of the heavy armour sets looks for exactly {@code Float.MAX_VALUE} to hand the hit over to its
 * own internal kill source - which is how those mods keep {@code /kill} and their own weapons working.</li>
 * <li>The same, but armour piercing and allowed in creative mode.</li>
 * <li>The same, but marked as fire damage. Some death gates only open for one specific kind of hit - the Witchery
 * vampire dies to fire, sunlight and the void, and refuses every sword - so the type of the hit is part of the
 * protection that has to be respected. The hit is repeated, because Witchery rolls for the flame protected vampire
 * instead of deciding it.</li>
 * <li>The damage pipeline is skipped completely: the health is set to zero and the death path is run by hand. An
 * entity level invulnerability flag, damage caps and custom {@code attackEntityFrom} overrides cannot stop this,
 * because none of them are involved in the call.</li>
 * <li>For players: everything the target carries is taken away (armour, inventory and, when present, the optional
 * bauble inventory) and the player is hit again. That removes item based protection: a set based immunity is no
 * longer complete and a "second life" item is no longer in the inventory. The items are dropped, not destroyed.</li>
 * <li>For players: a few more death events, to count down one shot protections that are consumed on use.</li>
 * <li>For everything else: the entity is removed from the world.</li>
 * </ol>
 * Two rules keep that ladder honest, and both are about the state a kill attempt leaves behind:
 * <ul>
 * <li>A death only counts when the death path of the target actually ran (see {@link #onLivingDeath}). Zero health
 * is <em>not</em> a death: a death gate can leave a walking entity at zero health while the death path never ran, and
 * Witchery's vampire even puts its health back to one and returns. Checking the health instead of the death is how
 * the ladder used to stop on the first step and leave an unkillable target behind.</li>
 * <li>NaN is repaired after every attempt. The overkill hit overflows to infinity in the armour calculation of the
 * game, and the absorption bookkeeping then computes {@code infinity - infinity}, which is NaN. From there every
 * later hit is NaN as well - {@code Math.max(NaN, 0)} is NaN, {@code MathHelper.clamp_float} passes NaN through and
 * even {@code health <= 0} is false for NaN - so the target never dies again. See {@link #repair}.</li>
 * </ul>
 * A player the piggy kills is announced by the piggy: the damage type has to stay {@value #BYPASS_DAMAGE_TYPE} for the
 * protection mods, and the death message of that type belongs to their infinity weapons, so the message is replaced
 * instead of translated - see {@link PiggyDamageSource}.
 * <p>
 * Every step that is needed is logged with debug level, so a test can tell which one did the kill.
 */
public final class MTTrueKill {

    /**
     * Damage type of every hit. See the class javadoc: the set based immunity mods cancel their events for every
     * source <em>except</em> this one, so this is the type that walks past them.
     */
    public static final String BYPASS_DAMAGE_TYPE = "infinity";

    /**
     * Translation key of the death message of a player the piggy kills. The game ships the death message to the
     * clients as a translation component, so every client reads it in its own language, and the text of this key lives
     * in the two language files of the mod. See {@link PiggyDamageSource}.
     */
    public static final String DEATH_MESSAGE_KEY = "death.attack.messtech.piggy";

    /** Amount that makes the shielded armour sets escalate to their own internal kill source. */
    private static final float OVERKILL = Float.MAX_VALUE;

    /**
     * Hits used by the fire step. Witchery lets a flame protected vampire through on one roll in four, so a single
     * hit would leave three of them standing and a removed - i.e. lootless - corpse behind.
     */
    private static final int FIRE_ATTEMPTS = 6;

    /** Death events used to count down single use protections of a player that survived everything else. */
    private static final int EXTRA_DEATH_ATTEMPTS = 3;

    /** Ticks the dropped protection items stay unlootable, so the target cannot grab them back mid kill. */
    private static final int DROP_PICKUP_DELAY = 40;

    /**
     * Every entity whose death path completed, filled by {@link #onLivingDeath}. The health of an entity is whatever
     * its own death gate decided it to be, so a completed death is the only state that tells a finished death from a
     * refused one. A weak set, so the entries of entities that are long gone do not pile up; {@link #isDead} only
     * trusts an entry while the health agrees with it.
     */
    private static final Set<Entity> DEATHS = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    private MTTrueKill() {}

    /**
     * Registers the death tracker. Called by the common proxy during preInit.
     */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new MTTrueKill());
    }

    /**
     * Records every death whose death path completed. {@link EventPriority#LOWEST} and {@code receiveCanceled} are
     * what make this the final state of the event: a mod that refuses the death of a protected entity, like Witchery
     * does for its vampire players, runs before this and leaves no entry behind.
     * <p>
     * A death posts the event more than once for a player (the chain is {@code EntityPlayerMP.onDeath} ->
     * {@code EntityPlayer.onDeath} -> {@code EntityLivingBase.onDeath}, and only the innermost one decides whether
     * the death path runs), so a canceled post <em>removes</em> the entry again. Without that, a death gate that rolls
     * per post - the flame protection of the vampire clothes does - could leave a "half dead" record behind and the
     * ladder would stop on a player that is still standing at zero health.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingDeath(LivingDeathEvent event) {
        synchronized (DEATHS) {
            if (event.isCanceled()) {
                DEATHS.remove(event.entityLiving);
            } else {
                DEATHS.add(event.entityLiving);
            }
        }
    }

    /**
     * Kills {@code rawTarget}. Has to be called on the server side.
     *
     * @param rawTarget  the entity that was hit, may be a body part of a multi part boss
     * @param thrower    the player that threw the piggy, may be {@code null}. It is not protected: the piggy has to
     *                   be able to kill the player that threw it straight up
     * @param projectile the piggy itself, used to build the damage source and for the failure message
     * @return {@code true} when the target is dead (or gone) afterwards
     */
    public static boolean kill(Entity rawTarget, EntityLivingBase thrower, Entity projectile) {
        if (rawTarget == null || rawTarget.worldObj == null || rawTarget.worldObj.isRemote) return false;

        Entity target = resolveMultiPart(rawTarget);
        repair(target);
        if (isDead(target)) return true;

        DamageSource piercing = indirect(thrower, projectile).setDamageIsAbsolute();

        // 1) The plain hit: set based immunity ignores this damage type and the shield code escalates on this amount.
        if (hit(target, piercing)) return killLog(target, 1);

        // 2) The strong hit: armour piercing and allowed in creative mode.
        DamageSource unresistable = indirect(thrower, projectile).setDamageIsAbsolute()
            .setDamageBypassesArmor()
            .setDamageAllowedInCreativeMode();
        if (hit(target, unresistable)) return killLog(target, 2);

        // 3) The fire hit: the death gate of the target may only open for fire damage. See FIRE_ATTEMPTS.
        DamageSource fire = indirect(thrower, projectile).setDamageIsAbsolute()
            .setDamageBypassesArmor()
            .setDamageAllowedInCreativeMode()
            .setFireDamage();

        for (int attempt = 0; attempt < FIRE_ATTEMPTS; attempt++) {
            // The hit keeps the vanilla path complete (hurt animation, knockback, the bookkeeping of the game), the
            // forced death behind it is what reaches a target whose health a refused death already left at zero -
            // attackEntityFrom() returns immediately on such a target.
            if (hit(target, fire) || forceDeath(target, fire)) return killLog(target, 3);
        }

        // 4) Zero the health and run the death path by hand, without the damage pipeline.
        if (forceDeath(target, piercing)) return killLog(target, 4);

        if (target instanceof EntityPlayer player) {
            // 5) Take everything the target carries away and try again.
            dropEverything(player);
            if (hit(target, piercing) || forceDeath(target, piercing)) return killLog(target, 5);

            // 6) Count down whatever is left.
            for (int attempt = 0; attempt < EXTRA_DEATH_ATTEMPTS && !forceDeath(target, piercing); attempt++) {
                // A call to forceDeath() is one death event, which is what single use protection counts down.
            }
            if (isDead(target)) return killLog(target, 6);
        } else {
            // 7) Nothing left that could cancel the death: remove the entity from the world outright.
            repair(target);
            target.setDead();
            target.worldObj.removeEntity(target);
            if (isDead(target)) return killLog(target, 7);
        }

        // Nothing on the ladder worked: the target is alive, and it has to stay a target that works.
        restore(target);

        if (thrower instanceof EntityPlayer player) {
            GTUtility.sendChatToPlayer(
                player,
                StatCollector.translateToLocalFormatted("messtech.piggy.kill.failed", target.getCommandSenderName()));
        }
        return false;
    }

    /**
     * @return whether the target really died, i.e. whether its death path completed (or it is gone from the world).
     *         The health alone is deliberately not used: a death gate can refuse the death and leave the health at
     *         zero, or even at NaN, while the entity keeps walking around.
     */
    private static boolean isDead(Entity target) {
        if (target.isDead) return true;

        synchronized (DEATHS) {
            if (!DEATHS.contains(target)) return false;
        }

        // The entry is a memory, not a state: a player that respawned - the same entity, health back up - or a mob
        // that a mod revived is alive again, so the memory only counts while the health agrees with it. This is also
        // what keeps a second throw from running a second death path on an entity that is already dying.
        return !(target instanceof EntityLivingBase living) || living.getHealth() <= 0.0F;
    }

    /** One hit of the piggy. */
    private static boolean hit(Entity target, DamageSource source) {
        // Dropping the vanilla immunity window first: a target that was hit a moment ago ignores hits otherwise.
        target.hurtResistantTime = 0;
        target.attackEntityFrom(source, OVERKILL);
        repair(target);
        return isDead(target);
    }

    /**
     * Runs the vanilla death path of an entity without going through {@code attackEntityFrom}. This is the same call
     * GT5U uses when it has to kill something for good, and it is what bypasses an entity level invulnerability flag.
     */
    private static boolean forceDeath(Entity target, DamageSource source) {
        if (!(target instanceof EntityLivingBase living)) return false;

        // setHealth() clamps, but a NaN would survive that clamp, so setting the value also repairs it.
        float healthBeforeHit = living.getHealth();
        living.setHealth(0.0F);

        // The death message is built from the last hit the combat tracker of the target remembers, and a hit fills that
        // tracker on its way through damageEntity(). This path does not go through the damage pipeline at all, so the
        // piggy is written into the tracker by hand - otherwise a target that refused every hit and died here would be
        // announced with whatever hit it took last, or with no attacker at all. See PiggyDamageSource.
        CombatTracker tracker = living.func_110142_aN();
        tracker.func_94547_a(source, healthBeforeHit, 0.0F);

        living.onDeath(source);
        repair(target);
        return isDead(living);
    }

    /**
     * Repairs the two values that the game itself can turn into NaN, which makes an entity unkillable for good:
     * {@code MathHelper.clamp_float} passes NaN through, every comparison with NaN is false (so {@code health <= 0}
     * never fires again) and {@code Math.max(NaN, 0)} is NaN, which poisons the next hit as well.
     * <p>
     * The source is the damage pipeline of the game itself: {@code Float.MAX_VALUE} damage overflows to infinity in
     * {@code applyArmorCalculations}, and the absorption bookkeeping computes {@code absorption - (infinity -
     * infinity)}, i.e. NaN. The absorption is the value that is poisoned first, the health follows on the next hit.
     */
    private static void repair(Entity target) {
        if (!(target instanceof EntityLivingBase living)) return;

        float absorption = living.getAbsorptionAmount();
        if (Float.isNaN(absorption) || Float.isInfinite(absorption)) {
            living.setAbsorptionAmount(0.0F);
        }

        if (Float.isNaN(living.getHealth())) {
            living.setHealth(0.0F);
        }
    }

    /**
     * Hands a target that survived the whole ladder back in a state the game can work with. Every step that refuses
     * the death leaves the health at zero, which would be a walking corpse - and the piggy is thrown at a target, not
     * at the world, so the target has to keep working when the piggy fails.
     */
    private static void restore(Entity target) {
        if (!(target instanceof EntityLivingBase living) || isDead(target)) return;

        if (living.getHealth() <= 0.0F) {
            living.setHealth(1.0F);
        }
    }

    /** Dragon style bosses are hit on a body part, but the piggy wants the boss itself. */
    private static Entity resolveMultiPart(Entity hit) {
        if (hit instanceof EntityDragonPart part && part.entityDragonObj instanceof Entity boss) return boss;
        return hit;
    }

    /**
     * Takes everything a player carries and drops it on the ground. The slots are cleared directly instead of using
     * the vanilla "toss one item" helper, because some protections are handed back to their owner when they are
     * tossed, which would defeat the whole point of this step.
     */
    private static void dropEverything(EntityPlayer player) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null) continue;

            player.inventory.setInventorySlotContents(slot, null);
            drop(player, stack);
        }
        player.inventory.markDirty();

        dropBaubles(player);
    }

    /**
     * Clears the optional bauble inventory, when the bauble API is installed. Going through that inventory (instead
     * of clearing the array behind it) is what makes the removed item run its own "on unequipped" hook, which is how
     * an entity level invulnerability switches itself back off.
     */
    private static void dropBaubles(EntityPlayer player) {
        try {
            Method getBaubles = Class.forName("baubles.api.BaublesApi")
                .getMethod("getBaubles", EntityPlayer.class);
            Object inventory = getBaubles.invoke(null, player);
            if (!(inventory instanceof IInventory baubles)) return;

            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                ItemStack stack = baubles.getStackInSlot(slot);
                if (stack == null) continue;

                baubles.setInventorySlotContents(slot, null);
                drop(player, stack);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The bauble API is optional, and a missing API is not an error.
        }
    }

    /** Drops one stack at the player, with a short pickup delay. */
    private static void drop(EntityPlayer player, ItemStack stack) {
        EntityItem item = new EntityItem(player.worldObj, player.posX, player.posY + 0.5D, player.posZ, stack);
        item.delayBeforeCanPickup = DROP_PICKUP_DELAY;
        player.worldObj.spawnEntityInWorld(item);
    }

    /**
     * @return the damage source of the piggy. The piggy is the <em>indirect</em> entity on purpose:
     *         {@code getEntity()} - the entity the game credits a kill to - is then a piggy and not a player. That is
     *         what keeps the amount exactly {@code Float.MAX_VALUE} and the damage type exactly
     *         {@value #BYPASS_DAMAGE_TYPE}: a mod that adjusts incoming damage while the attacker holds an infinity
     *         weapon keys off the attacker being a player, and the shield sets escalate their own kill source on
     *         exactly this amount. It also means a mob killed by the piggy does not count as "hit by a player", which
     *         is the same trade off the infinity weapons make.
     */
    private static DamageSource indirect(EntityLivingBase thrower, Entity projectile) {
        Entity owner = thrower != null ? thrower : projectile;
        return new PiggyDamageSource(owner, projectile != null ? projectile : owner);
    }

    /**
     * The damage source of the piggy. It is a damage source of its own for one reason only: the death message.
     * <p>
     * The damage <em>type</em> has to stay {@link MTTrueKill#BYPASS_DAMAGE_TYPE}. The armour sets of the pack hand
     * every other type to their own immunity and let exactly this one through, so a type of our own would not only
     * change the message but also take the kill away (see the class javadoc). The death <em>message</em> is derived
     * from that same type by the game, and {@code death.attack.infinity} is the message of the infinity weapons of
     * those mods: translating it here would relabel their kills as piggy hits. The message is therefore replaced on
     * the source, the way Avaritia does it for its own infinity sword ({@code DamageSourceInfinitySword}).
     */
    private static final class PiggyDamageSource extends EntityDamageSourceIndirect {

        private PiggyDamageSource(Entity direct, Entity piggy) {
            super(BYPASS_DAMAGE_TYPE, direct, piggy);
        }

        /**
         * {@code EntityDamageSourceIndirect.func_151519_b}, i.e. the message of an indirect hit, with the translation
         * key of the piggy instead of the one of the damage type. Both arguments are handed over the way the vanilla
         * method does it, so a language file can name the victim ({@code %1$s}) and, when a pack gives the entity a
         * name, the piggy itself ({@code %2$s}); the mod only uses the first one.
         */
        @Override
        public IChatComponent func_151519_b(EntityLivingBase victim) {
            Entity killer = getEntity() != null ? getEntity() : getSourceOfDamage();
            return new ChatComponentTranslation(DEATH_MESSAGE_KEY, victim.func_145748_c_(), killer.func_145748_c_());
        }
    }

    private static boolean killLog(Entity target, int step) {
        MessTech.MT_LOG.debug("[Piggy] true kill of {} needed step {}", target, step);
        return true;
    }
}
