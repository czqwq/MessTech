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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.MessTech.init.MessTech;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.util.GTUtility;

/**
 * The "true kill" ladder for targets that would normally survive armour, energy shields, entity level
 * invulnerability or a "second life" item. Branch-less: each killer owns a branch saying only how its kill reads and
 * whom it refuses - {@link MTPiggyKill} (thrown piggy), {@link MTBowKill} (Voidstar Piercer).
 * <p>
 * Polite to direct, each protection bypassed where it gives up:
 * <ol>
 * <li>{@code attackEntityFrom} with {@link #OVERKILL}, type "infinity". Set-immunity mods cancel hurt/attack/death
 * events for every other type; heavy armour shield code escalates on exactly {@code Float.MAX_VALUE} to its own kill
 * source (how those mods keep {@code /kill} and their weapons working).</li>
 * <li>Same, armour piercing, allowed in creative mode.</li>
 * <li>Same, fire: some death gates open for one hit type only (the Witchery vampire dies to fire, sunlight and the
 * void, refuses every sword); Witchery rolls for the flame protected vampire.</li>
 * <li>Damage pipeline skipped: health zeroed, death path by hand - entity level invulnerability, damage caps, custom
 * {@code attackEntityFrom} overrides cannot stop it.</li>
 * <li>Players: everything carried (armour, inventory, optional bauble inventory) dropped, then hit again - set
 * immunity incomplete, "second life" item gone. Dropped, not destroyed.</li>
 * <li>Players: more death events, to count down single use protections.</li>
 * <li>Everything else: removed from the world.</li>
 * </ol>
 * Two rules about the state an attempt leaves behind:
 * <ul>
 * <li>A death counts only when the death path ran (see {@link #onLivingDeath}). Zero health is <em>not</em> a death:
 * a gate can leave a walking entity at zero health, and Witchery's vampire puts its health back to one and returns.
 * Health alone stops on step one, leaving an unkillable target.</li>
 * <li>NaN is repaired after every attempt: the overkill hit overflows to infinity in the armour calculation, the
 * absorption bookkeeping computes {@code infinity - infinity} = NaN, and later hits are NaN too -
 * {@code Math.max(NaN, 0)} is NaN, {@code MathHelper.clamp_float} passes NaN, {@code health <= 0} is false. See
 * {@link #repair}.</li>
 * </ul>
 * A player kill is announced by the branch: the type stays {@value #BYPASS_DAMAGE_TYPE} (protection mods), but the
 * message belongs to their infinity weapons, so it is replaced - see {@link TrueKillDamageSource}.
 * <p>
 * Steps are logged at debug, so a test can tell which one killed.
 */
public abstract class MTKillBase {

    /** Every hit's damage type; set-immunity mods cancel events for every other type. */
    public static final String BYPASS_DAMAGE_TYPE = "infinity";

    /** Amount that makes shielded armour sets escalate to their own kill source. */
    private static final float OVERKILL = Float.MAX_VALUE;

    /**
     * Fire step hits: a flame protected Witchery vampire passes one roll in four, so one hit leaves three of four
     * standing and a lootless corpse.
     */
    private static final int FIRE_ATTEMPTS = 6;

    /** Death events counting down single use protections of a surviving player. */
    private static final int EXTRA_DEATH_ATTEMPTS = 3;

    /** Ticks dropped items stay unlootable, so the target cannot grab them back. */
    private static final int DROP_PICKUP_DELAY = 40;

    /**
     * Deaths whose death path completed, filled by {@link #onLivingDeath}. Weak set, so gone entries do not pile up;
     * {@link #isDead} trusts one only while the health agrees.
     */
    private static final Set<Entity> DEATHS = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** Branch name in the debug log and messages, e.g. {@code "Piggy"}. */
    private final String killName;

    /**
     * Translation key of the death message of a player this branch kills. Sent as a translation component, so each
     * client reads it in its own language; the text is in the two language files. See
     * {@link TrueKillDamageSource}.
     */
    private final String deathMessageKey;

    /** Translation key of the thrower message when a target survives. */
    private final String failureMessageKey;

    protected MTKillBase(String killName, String deathMessageKey, String failureMessageKey) {
        this.killName = killName;
        this.deathMessageKey = deathMessageKey;
        this.failureMessageKey = failureMessageKey;
    }

    /** Registers the shared death tracker from the common proxy preInit, once for all branches. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new DeathTracker());
    }

    /**
     * Whether this branch will kill this target at all. A refused target is otherwise untouched, so a branch can add
     * its own rule - see {@link MTBowKill}.
     */
    protected boolean accepts(Entity target, EntityLivingBase thrower) {
        return true;
    }

    /**
     * Kills {@code rawTarget}. Server side only.
     *
     * @param rawTarget  hit entity, may be a multi part boss body part
     * @param thrower    credited with the kill, may be {@code null}
     * @param projectile carried the kill, used for the damage source and failure message
     * @return whether the target is dead (or gone) afterwards
     */
    public final boolean kill(Entity rawTarget, EntityLivingBase thrower, Entity projectile) {
        if (rawTarget == null || rawTarget.worldObj == null || rawTarget.worldObj.isRemote) return false;
        if (!accepts(rawTarget, thrower)) return false;

        Entity target = resolveMultiPart(rawTarget);
        repair(target);
        if (isDead(target)) return true;

        DamageSource piercing = indirect(thrower, projectile).setDamageIsAbsolute();

        // 1) Plain hit.
        if (hit(target, piercing)) return killLog(target, 1);

        // 2) Strong hit.
        DamageSource unresistable = indirect(thrower, projectile).setDamageIsAbsolute()
            .setDamageBypassesArmor()
            .setDamageAllowedInCreativeMode();
        if (hit(target, unresistable)) return killLog(target, 2);

        // 3) Fire hit. See FIRE_ATTEMPTS.
        DamageSource fire = indirect(thrower, projectile).setDamageIsAbsolute()
            .setDamageBypassesArmor()
            .setDamageAllowedInCreativeMode()
            .setFireDamage();

        for (int attempt = 0; attempt < FIRE_ATTEMPTS; attempt++) {
            // hit() handles hurt animation and knockback; forceDeath() reaches a zero-health target, where
            // attackEntityFrom() returns immediately.
            if (hit(target, fire) || forceDeath(target, fire)) return killLog(target, 3);
        }

        // 4) By hand.
        if (forceDeath(target, piercing)) return killLog(target, 4);

        if (target instanceof EntityPlayer player) {
            // 5) Drop everything carried, hit again.
            dropEverything(player);
            if (hit(target, piercing) || forceDeath(target, piercing)) return killLog(target, 5);

            // 6) Count down the rest.
            for (int attempt = 0; attempt < EXTRA_DEATH_ATTEMPTS && !forceDeath(target, piercing); attempt++) {
                // One forceDeath() = one death event, what single use protection counts down.
            }
            if (isDead(target)) return killLog(target, 6);
        } else {
            // 7) Nothing left to cancel the death.
            repair(target);
            target.setDead();
            target.worldObj.removeEntity(target);
            if (isDead(target)) return killLog(target, 7);
        }

        // Nothing worked: the target must stay workable.
        restore(target);

        if (thrower instanceof EntityPlayer player) {
            GTUtility.sendChatTrans(player, this.failureMessageKey, target.getCommandSenderName());
        }
        return false;
    }

    /**
     * @return whether the target really died, i.e. its death path completed (or it is gone).
     */
    private static boolean isDead(Entity target) {
        if (target.isDead) return true;

        synchronized (DEATHS) {
            if (!DEATHS.contains(target)) return false;
        }

        // A memory, not a state: a respawned player (same entity) or revived mob is alive again, so an entry counts
        // only while the health agrees; also stops a second death path.
        return !(target instanceof EntityLivingBase living) || living.getHealth() <= 0.0F;
    }

    /** One hit of the ladder. */
    private static boolean hit(Entity target, DamageSource source) {
        // Drop the vanilla immunity window, or a recently hit target ignores hits.
        target.hurtResistantTime = 0;
        target.attackEntityFrom(source, OVERKILL);
        repair(target);
        return isDead(target);
    }

    /**
     * Runs the vanilla death path without {@code attackEntityFrom}: the call GT5U uses to kill for good, which
     * bypasses an entity level invulnerability flag.
     */
    private static boolean forceDeath(Entity target, DamageSource source) {
        if (!(target instanceof EntityLivingBase living)) return false;

        // setHealth() clamps, but a NaN survives it, so setting the value also repairs.
        float healthBeforeHit = living.getHealth();
        living.setHealth(0.0F);

        // The death message comes from the last hit the combat tracker remembers (filled in damageEntity()); this path
        // skips the pipeline, so the branch is written in by hand, else the kill shows its last hit or none.
        CombatTracker tracker = living.func_110142_aN();
        tracker.func_94547_a(source, healthBeforeHit, 0.0F);

        living.onDeath(source);
        repair(target);
        return isDead(living);
    }

    /**
     * Repairs the two values the game can turn into NaN, which makes an entity unkillable for good:
     * {@code Float.MAX_VALUE} damage overflows to infinity in {@code applyArmorCalculations} and the absorption
     * bookkeeping computes {@code absorption - (infinity - infinity)} = NaN (absorption first, the health next hit).
     * See the class javadoc.
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
     * Hands a survivor back workable: a refusing step leaves a walking corpse at zero health, and the ladder must
     * kill, not break.
     */
    private static void restore(Entity target) {
        if (!(target instanceof EntityLivingBase living) || isDead(target)) return;

        if (living.getHealth() <= 0.0F) {
            living.setHealth(1.0F);
        }
    }

    /** Dragon style bosses are hit on a body part; the ladder wants the boss. */
    private static Entity resolveMultiPart(Entity hit) {
        if (hit instanceof EntityDragonPart part && part.entityDragonObj instanceof Entity boss) return boss;
        return hit;
    }

    /**
     * Drops everything a player carries. Slots are cleared directly, not via the vanilla "toss one item" helper:
     * some protections return to their owner when tossed.
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
     * Clears the optional bauble inventory, when the bauble API is installed. Going through it rather than the array
     * behind it makes the removed item run its "on unequipped" hook, switching entity level invulnerability off.
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
            // The bauble API is optional; missing is not an error.
        }
    }

    /** Drops one stack at the player, with a short pickup delay. */
    private static void drop(EntityPlayer player, ItemStack stack) {
        EntityItem item = new EntityItem(player.worldObj, player.posX, player.posY + 0.5D, player.posZ, stack);
        item.delayBeforeCanPickup = DROP_PICKUP_DELAY;
        player.worldObj.spawnEntityInWorld(item);
    }

    /**
     * @return the damage source of this branch. The projectile is the <em>indirect</em> entity on purpose:
     *         {@code getEntity()} is then the projectile, not a player, which keeps the amount exactly
     *         {@code Float.MAX_VALUE} and the type exactly {@value #BYPASS_DAMAGE_TYPE} (damage mods key off a player
     *         attacker). A mob killed this way is not "hit by a player" - the infinity weapons' trade off.
     */
    private DamageSource indirect(EntityLivingBase thrower, Entity projectile) {
        Entity owner = thrower != null ? thrower : projectile;
        return new TrueKillDamageSource(this.deathMessageKey, owner, projectile != null ? projectile : owner);
    }

    private boolean killLog(Entity target, int step) {
        MessTech.MT_LOG.debug("[{}] true kill of {} needed step {}", this.killName, target, step);
        return true;
    }

    /**
     * The damage source of a branch, its own only for the death message.
     * <p>
     * The <em>type</em> must stay {@link MTKillBase#BYPASS_DAMAGE_TYPE}: the pack's armour sets let only that type
     * through, so our own type would also lose the kill (see the class javadoc). The <em>message</em> derives from
     * that type, {@code death.attack.infinity} being theirs, so it is replaced on the source as Avaritia does
     * ({@code DamageSourceInfinitySword}).
     */
    private static final class TrueKillDamageSource extends EntityDamageSourceIndirect {

        private final String deathMessageKey;

        private TrueKillDamageSource(String deathMessageKey, Entity direct, Entity projectile) {
            super(BYPASS_DAMAGE_TYPE, direct, projectile);
            this.deathMessageKey = deathMessageKey;
        }

        /**
         * {@code EntityDamageSourceIndirect.func_151519_b}: the indirect-hit message, with the branch's translation
         * key instead of the damage type's. Arguments as vanilla, so a language file can name the victim
         * ({@code %1$s}) and, when a pack names the entity, the projectile ({@code %2$s}); this mod uses the first.
         */
        @Override
        public IChatComponent func_151519_b(EntityLivingBase victim) {
            Entity killer = getEntity() != null ? getEntity() : getSourceOfDamage();
            return new ChatComponentTranslation(this.deathMessageKey, victim.func_145748_c_(), killer.func_145748_c_());
        }
    }

    /**
     * Records deaths whose death path completed. {@link EventPriority#LOWEST} + {@code receiveCanceled} = the event's
     * final state: a refusing mod (Witchery's vampire players) runs before this and leaves no entry.
     * <p>
     * A player death posts the event more than once ({@code EntityPlayerMP.onDeath} -> {@code EntityPlayer.onDeath} ->
     * {@code EntityLivingBase.onDeath}; the innermost decides if the death path runs), so a canceled post
     * <em>removes</em> the entry - else a per-post rolling gate (vampire clothes' flame protection) leaves a "half
     * dead" record and the ladder stops on a zero-health player.
     */
    public static final class DeathTracker {

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
    }
}
