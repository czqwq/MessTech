package com.MessTech.common.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;

import com.MessTech.common.machine.hatch.MTReactorHeatHatch;

import ic2.core.item.reactor.ItemReactorPlating;

/**
 * Per-page heat state of {@link MTReactor}: the heat value itself, the meltdown countdown, and the passive cooling and
 * fluid heat exchange that the reactor cycle drives.
 * <p>
 * <b>Why this is not a plain array any more.</b> The heat used to live in an {@code int[]} indexed by the global page
 * order over all access hatches, where that order came from sorting the hatches by their block coordinates. Adding,
 * removing or moving a single access hatch therefore renumbered every following page, so stored heat silently migrated
 * from one hatch's page to another hatch's page. Pages are now anchored on the coordinates of the access hatch that
 * owns them ({@link #key}), which makes the mapping independent of how many hatches exist or where they are.
 * <p>
 * Two safety nets live here as well:
 * <ul>
 * <li>{@link #onCapacityChanged(int)} rescales the stored heat whenever the ceiling changes (a heat control hatch was
 * added, removed, or swapped for another tier). Without it a reactor carrying 900,000 HU under a UHV hatch detonated on
 * the first cycle after that hatch was replaced by an EV one, because the heat effect check is a plain
 * {@code heat / maxHeat} ratio.</li>
 * <li>{@link #tick()} runs the meltdown countdown: heat at or above the ceiling no longer detonates immediately, it
 * arms a {@value #MELTDOWN_COUNTDOWN_TICKS} tick timer that {@link #resetCountdown()} cancels when the heat is brought
 * back under the ceiling. The countdown is a wall-clock deadline (an absolute game tick, not a per-page counter),
 * because a page is only visited once per cycle and counting "per visit" stretched the grace window to 30 s x page
 * count. It therefore keeps running while the machine is switched off - that is what makes "get the heat back under the
 * ceiling within 30 seconds" a real instruction instead of a race the player always loses.</li>
 * </ul>
 */
public final class MTReactorHeatSystem {

    /** Ticks between two IC2 reactor cycles. */
    public static final int CYCLE_TICKS = 20;
    /** Meltdown countdown: 30 seconds of grace before a page that is at or over its ceiling detonates. */
    public static final int MELTDOWN_COUNTDOWN_TICKS = 600;
    /** Pages the heat store can hold. The structure offers at most 25 access-hatch positions with 8 pages each. */
    public static final int MAX_PAGES = 256;
    /** Upper bound of the heat bonus one reactor plating may contribute. */
    private static final int MAX_HEAT_BONUS_PER_ITEM = 100_000;
    /**
     * Coolant (mB) needed to carry away one unit of buffered component heat. MessTech's own ratio: IC2 converts through
     * {@code huOutputModifier}, this port runs the loop straight at 1 mB of coolant per 1 HU, which makes the required
     * feed rate directly readable as "as much coolant per second as the core makes heat per second".
     * <p>
     * <b>Keep this at 1, or adjust the caller.</b> {@code MTReactor#releaseCoolantHeat()} passes the mB it actually
     * exchanged straight into {@link #coolByCoolant(long)}, whose parameter is buffered-heat units. The two only match
     * while this constant is 1; moving it to IC2's own x2 would need that call site to divide by it.
     */
    public static final int COOLANT_PER_BUFFER_UNIT = 1;

    /**
     * Hard cap of the heat store. The UIV heat control hatch asks for {@code Integer.MAX_VALUE}, which is not usable as
     * a real ceiling: every heat effect compares {@code heat / maxHeat}, and with a 2.1e9 ceiling no reachable amount
     * of
     * heat drives that ratio anywhere near the 40% / 70% / 85% / 100% thresholds, so a UIV hatch silently switched the
     * whole risk model off. This cap stays far above anything one 9x6 page's components can produce (IC2's largest heat
     * storage item is well under 100,000) while keeping every threshold reachable.
     */
    public static final int MAX_HEAT_CAPACITY = 50_000_000;

    /** Numeric value of the lowest supported heat control hatch tier ({@link MTReactorHeatHatch#MIN_TIER}). */
    private static final int MIN_TIER = MTReactorHeatHatch.MIN_TIER;

    /**
     * Passive cooling in HU per second per heat control hatch tier, indexed by {@code tier - MIN_TIER} (EV..UIV).
     * <p>
     * The rate of every real tier is exactly that tier's own heat ceiling per 2400 ticks, i.e. {@code ceiling / 120}
     * HU/s, so a full ceiling of heat bleeds off in two minutes: {@code 10,000 / 120 = 83.3} for EV, and so on. Two
     * minutes is slow enough that the hatch cannot out-cool a running reactor, and fast enough that shutting the
     * machine down is a real way to save a reactor that is on its way to a meltdown. The values below are rounded to
     * whole HU/s ({@code round(ceiling / 120)}), which for the seven real tiers reproduces that rule to within one
     * HU/s.
     * <p>
     * The UIV entry is deliberately decoupled from the rule: that hatch's ceiling is 50,000,000, so {@code ceiling /
     * 120} would be 416,667 HU/s, and following the rule literally would make the top hatch cool faster than the
     * reactor can possibly produce heat. 200,000 HU/s instead drains the whole 50,000,000 in 250 s, which is the
     * reasonable-time answer for a hatch whose practical ceiling nobody reaches.
     */
    private static final long[] PASSIVE_COOLING_PER_SECOND = { 83, 167, 417, 833, 1_667, 4_167, 8_333, 200_000 };

    /** Only pages carrying at least this fraction of the hottest page's heat take part in the passive bleed-off. */
    private static final float PASSIVE_COOLING_SHARE_THRESHOLD = 0.05F;

    private final MTReactor reactor;
    /** Heat per page, keyed by the owning access hatch. */
    private final Map<ChunkCoordinates, int[]> pageHeat = new HashMap<>();
    /** Meltdown countdown per page, keyed by hatch coordinates plus page index. */
    private final Map<String, ArmedPage> armedPages = new HashMap<>();

    /** The ceiling currently in effect, as reported by {@link MTReactor#getReactorHeatCapacity()}. */
    private int capacity = MTReactorHeatHatch.DEFAULT_HEAT_CAPACITY;
    /** What the reactor components ask for through {@link #setMaxHeat}: the largest per-page value seen. */
    private int componentBonus;
    /** Per-page plating bonus, mirroring {@link #pageHeat}: a page is an independent reactor grid. */
    private final Map<ChunkCoordinates, int[]> pageBonus = new HashMap<>();

    /** Heat the components emitted during the current cycle through {@link #addEmitHeat(int)}. */
    private int emittedHeat;
    /** How much of {@link #emittedHeat} the fluid loop already carried away this cycle. */
    private int coolantExchangedHeat;
    /** True once the controller switched the reactor into fluid (wet) mode. */
    private boolean fluidMode;
    /** Fractional HU of passive cooling accumulated across ticks; see {@link #applyPassiveCooling()}. */
    private double coolingProgress;
    /** Last tick value seen by {@link #refreshCountdownClock(long)}, used to detect a restarted tick counter. */
    private long lastNow = -1L;
    /**
     * Tick counter of the last {@link #loadNBTData}, used to re-anchor reloaded countdowns onto the running counter.
     */
    private long loadNow = -1L;

    public MTReactorHeatSystem(MTReactor reactor) {
        this.reactor = reactor;
    }

    static ChunkCoordinates key(PageOwner owner) {
        return owner == null ? null : owner.mtReactorKey();
    }

    // region Capacity

    /** The ceiling currently in effect. */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Heat ceiling including the plating contribution, clamped to the supported range. This is the value components see
     * as {@code getMaxHeat()}, and therefore the value the explosion threshold uses.
     */
    public int getEffectiveMaxHeat() {
        long sum = (long) capacity + componentBonus;
        if (sum < 1) return 1;
        return (int) Math.min(sum, MAX_HEAT_CAPACITY);
    }

    /**
     * Reactor plating's {@code setMaxHeat(getMaxHeat() + maxHeatAdd)} call, resolved as an absolute target.
     * <p>
     * Each plating of a page passes its own {@code getMaxHeat() + maxHeatAdd}, so the largest value in the page is the
     * ceiling the page ends up with: {@code capacity + largest plating bonus}. Taking the maximum instead of applying
     * the calls in order keeps the result independent of the component layout.
     * <p>
     * The bonus is tracked per page ({@link #setPageMaxHeat}) because a 9x6 page is an independent reactor grid: a page
     * with four platings must not raise the ceiling of a page without any.
     */
    public void setMaxHeat(PageOwner owner, int page, int newMaxHeat) {
        setPageMaxHeat(owner, page, newMaxHeat - capacity);
    }

    /** Records a plating's heat bonus for one page, ignoring anything outside the item bound. */
    public void setPageMaxHeat(PageOwner owner, int page, int bonus) {
        if (owner == null || bonus <= 0) return;
        if (bonus > MAX_HEAT_BONUS_PER_ITEM) bonus = MAX_HEAT_BONUS_PER_ITEM;
        ChunkCoordinates key = key(owner);
        if (key == null) return;
        int pages = Math.max(1, Math.min(owner.mtReactorPageCount(), MAX_PAGES));
        if (page < 0 || page >= pages) return;
        int[] bonuses = pageBonus.get(key);
        if (bonuses == null) {
            bonuses = new int[pages];
            pageBonus.put(key, bonuses);
        }
        if (bonuses[page] >= bonus) return;
        bonuses[page] = bonus;
        if (bonus > componentBonus) {
            componentBonus = bonus;
            if (reactor != null) reactor.markDirty();
        }
    }

    /**
     * Clears the plating bonuses of one page before its heat pass re-collects them. Called per page and not per cycle,
     * so a plating removed from page 3 stops contributing to page 3 while page 4 keeps its own.
     */
    public void resetPageBonus(PageOwner owner, int page) {
        int[] bonuses = pageBonus.get(key(owner));
        if (bonuses == null || page < 0 || page >= bonuses.length) return;
        bonuses[page] = 0;
    }

    /** Plating bonus of one page. */
    public int getPageBonus(PageOwner owner, int page) {
        int[] bonuses = pageBonus.get(key(owner));
        if (bonuses == null || page < 0 || page >= bonuses.length) return 0;
        return bonuses[page];
    }

    /** Ceiling of one page, i.e. the hatch ceiling plus that page's own plating bonus. */
    public int getPageMaxHeat(PageOwner owner, int page) {
        return getMaxHeatWithBonus(getPageBonus(owner, page));
    }

    /**
     * Called whenever the heat control hatch of the structure may have changed. Rescales every page's heat by the ratio
     * of the new to the old ceiling and clears any running meltdown countdown.
     * <p>
     * Rescaling rather than clamping is what makes installing or removing the hatch a proportional operation: a reactor
     * at 90% of a 1,000,000 ceiling ends up at 90% of the new ceiling instead of either detonating (clamping leaves it
     * over the new ceiling, which the heat effect check reads as {@code power >= 1.0}) or losing its heat entirely.
     */
    public void onCapacityChanged(int newCapacity) {
        int clamped = Math.max(1, Math.min(newCapacity, MAX_HEAT_CAPACITY));
        if (clamped == capacity) return;
        int oldCapacity = capacity;
        capacity = clamped;
        for (int[] heat : pageHeat.values()) {
            for (int i = 0; i < heat.length; i++) {
                int heatBefore = heat[i];
                if (heatBefore <= 0) continue;
                heat[i] = (int) Math.min(clamped, heatBefore * (long) clamped / oldCapacity);
            }
        }
        // Changing the ceiling is a player action; the meltdown has to be given its full grace period again.
        armedPages.clear();
        if (reactor != null) reactor.markDirty();
    }

    // endregion

    // region Page heat access

    /** Heat of one page. Returns 0 for a hatch that was never written. */
    public int getPageHeat(PageOwner owner, int page) {
        int[] heat = pageHeat.get(key(owner));
        if (heat == null || page < 0 || page >= heat.length) return 0;
        return heat[page];
    }

    /** Writes the heat of one page, creating the per-hatch array on first use. */
    public void setPageHeat(PageOwner owner, int page, int value) {
        int[] heat = lookupOrCreate(owner);
        if (heat == null || page < 0 || page >= heat.length) return;
        heat[page] = value;
    }

    /** Adds to the heat of one page and returns the resulting value, like IC2's {@code IReactor#addHeat}. */
    public int addPageHeat(PageOwner owner, int page, int amount) {
        int[] heat = lookupOrCreate(owner);
        if (heat == null || page < 0 || page >= heat.length) return 0;
        heat[page] += amount;
        return heat[page];
    }

    /** Heat of the hottest page. */
    public int getHottestHeat() {
        int hottest = 0;
        for (int[] heat : pageHeat.values()) {
            for (int value : heat) {
                if (value > hottest) hottest = value;
            }
        }
        return Math.max(hottest, 0);
    }

    /** Ceiling shown next to the hottest heat value, never below 1 so the GUI cannot divide by zero. */
    public int getHottestMaxHeat() {
        return Math.max(1, getEffectiveMaxHeat());
    }

    /** Heat percentage of the hottest page. Computed in {@code double} so a large ceiling cannot overflow. */
    public int getHottestHeatPercent() {
        return (int) Math.round(getHottestHeat() * 100.0D / getHottestMaxHeat());
    }

    /**
     * Drops the heat of hatches that are no longer part of the structure, so a removed hatch cannot hand its heat to a
     * hatch that later occupies the same coordinates with a different page layout.
     */
    public void retainOnly(Iterable<? extends PageOwner> alive) {
        Map<ChunkCoordinates, PageOwner> current = new HashMap<>();
        for (PageOwner owner : alive) {
            ChunkCoordinates key = key(owner);
            if (key != null) current.put(key, owner);
        }
        boolean changed = false;
        for (Iterator<Map.Entry<ChunkCoordinates, int[]>> it = pageHeat.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<ChunkCoordinates, int[]> entry = it.next();
            PageOwner owner = current.get(entry.getKey());
            if (owner == null) {
                it.remove();
                pageBonus.remove(entry.getKey());
                dropArmedPagesOf(entry.getKey());
                changed = true;
                continue;
            }
            int pages = Math.max(1, Math.min(owner.mtReactorPageCount(), MAX_PAGES));
            if (entry.getValue().length != pages) {
                // Same block position with another hatch tier: keep the pages that still exist.
                entry.setValue(Arrays.copyOf(entry.getValue(), pages));
                pageBonus.remove(entry.getKey());
                dropArmedPagesOf(entry.getKey());
                changed = true;
            }
        }
        if (changed && reactor != null) reactor.markDirty();
    }

    /** Forgets the countdowns that belong to one block position, e.g. after its hatch changed or was removed. */
    private void dropArmedPagesOf(ChunkCoordinates key) {
        String prefix = key.posX + "," + key.posY + "," + key.posZ + "#";
        armedPages.keySet()
            .removeIf(k -> k != null && k.startsWith(prefix));
    }

    private int[] lookupOrCreate(PageOwner owner) {
        ChunkCoordinates key = key(owner);
        if (key == null) return null;
        int pages = Math.max(1, Math.min(owner.mtReactorPageCount(), MAX_PAGES));
        int[] heat = pageHeat.get(key);
        if (heat == null) {
            heat = new int[pages];
            pageHeat.put(key, heat);
        }
        return heat;
    }

    // endregion

    // region Meltdown countdown

    /** Ticks left until the first armed page detonates, or 0 while nothing is counting down. */
    public int getCountdownTicks() {
        int left = 0;
        for (ArmedPage armed : armedPages.values()) {
            int remaining = (int) Math.max(0L, armed.deadline - armed.now);
            if (left == 0 || remaining < left) left = remaining;
        }
        return left;
    }

    /** Ticks left for one page's countdown, for per-page display. */
    public int getCountdownTicks(PageOwner owner, int page) {
        ArmedPage armed = armedPages.get(armedKey(owner, page));
        if (armed == null) return 0;
        return (int) Math.max(0L, armed.deadline - armed.now);
    }

    public boolean isCountdownActive() {
        return !armedPages.isEmpty();
    }

    /** 0.0 (armed) .. 1.0 (detonating) progress of the countdown, for the GUI/Waila alarm. */
    public float getCountdownProgress() {
        int ticks = getCountdownTicks();
        if (ticks <= 0) return 0.0F;
        return 1.0F - Math.min(1.0F, (float) ticks / MELTDOWN_COUNTDOWN_TICKS);
    }

    /** Cancels every running countdown: the player did something, or all heat came back down. */
    public void resetCountdown() {
        if (!armedPages.isEmpty()) {
            armedPages.clear();
            if (reactor != null) reactor.markDirty();
        }
    }

    /**
     * (Re-)arms the countdown of one page, i.e. the page is at or over its ceiling right now. The deadline is an
     * absolute game tick instead of a per-page counter, because a page is only visited once per full cycle: counting
     * "20 ticks per visit" made the grace window stretch to 30 s x page count, so a large reactor effectively got no
     * grace at all.
     *
     * @param now the current game tick
     */
    public void armCountdown(PageOwner owner, int page, long now) {
        ChunkCoordinates key = key(owner);
        if (key == null) return;
        String armedKey = armedKey(owner, page);
        if (armedPages.containsKey(armedKey)) return;
        armedPages.put(armedKey, new ArmedPage(key, page, now + MELTDOWN_COUNTDOWN_TICKS, now));
        if (reactor != null) reactor.markDirty();
    }

    /** Drops the countdown of one page once it is back under its ceiling. */
    public void clearCountdown(PageOwner owner, int page) {
        if (armedPages.remove(armedKey(owner, page)) != null && reactor != null) reactor.markDirty();
    }

    /**
     * Keeps the countdowns' idea of "now" up to date; call once per game tick. Two things have to be handled:
     * <p>
     * <b>A restored save.</b> {@link #saveNBTData} stores the remaining ticks, not an absolute deadline, because the
     * machine's tick counter is not persisted. The first call after {@link #loadNBTData} therefore re-anchors every
     * restored deadline onto the counter the machine is really running on; {@code loadNow} makes that happen exactly
     * once. Without it, a countdown saved with 300 ticks left would be measured against a counter value that no longer
     * means anything.
     * <p>
     * <b>A restarted counter.</b> Even outside a data load the counter can be reset (a tile entity re-initialisation
     * that is not preceded by {@code readFromNBT}), and a deadline that outlived that reset would count from the old
     * counter and turn a 30 second window into a much longer one. The counter is monotonic in every other case, so a
     * backwards jump is treated as a reset and every deadline is shifted by the same amount, preserving the window
     * each page had left.
     */
    public void refreshCountdownClock(long now) {
        if (loadNow >= 0) {
            // First tick after a data load: the countdowns came back as pending amounts (see loadNBTData), so anchor
            // them onto the counter the machine is really running on instead of onto the value they were saved with.
            for (ArmedPage armed : armedPages.values()) {
                armed.deadline = now + Math.max(1L, armed.deadline - loadNow);
                armed.now = now;
            }
            loadNow = -1L;
            lastNow = now;
            return;
        }
        long resetBy = lastNow >= 0 && now < lastNow ? lastNow - now : 0L;
        lastNow = now;
        for (ArmedPage armed : armedPages.values()) {
            armed.deadline -= resetBy;
            armed.now = now;
        }
    }

    /**
     * Lists the pages whose grace window has run out, and forgets them - the caller is expected to detonate exactly
     * these pages and nothing else.
     */
    public List<ArmedPage> takeExpiredCountdowns(long now) {
        if (armedPages.isEmpty()) return Collections.emptyList();
        List<ArmedPage> expired = null;
        for (Iterator<ArmedPage> it = armedPages.values()
            .iterator(); it.hasNext();) {
            ArmedPage armed = it.next();
            armed.now = now;
            if (armed.deadline <= now) {
                if (expired == null) expired = new ArrayList<>(1);
                expired.add(armed);
                it.remove();
            }
        }
        return expired == null ? Collections.emptyList() : expired;
    }

    private static String armedKey(PageOwner owner, int page) {
        ChunkCoordinates key = key(owner);
        return key == null ? null : (key.posX + "," + key.posY + "," + key.posZ + "#" + page);
    }

    // endregion

    // region Passive cooling

    /** Passive cooling per second granted by the installed heat control hatch tier. */
    public long getPassiveCoolingPerSecond() {
        int tier = reactor == null ? MIN_TIER : reactor.getHeatControlTier();
        int index = tier - MIN_TIER;
        if (index < 0) index = 0;
        if (index >= PASSIVE_COOLING_PER_SECOND.length) index = PASSIVE_COOLING_PER_SECOND.length - 1;
        return PASSIVE_COOLING_PER_SECOND[index];
    }

    /**
     * Bleeds heat off every page that carries a meaningful fraction of the hottest page's heat, so the loop stays a
     * no-op for the empty pages of a large structure. This runs whether or not the reactor produces energy: it is the
     * only way an already hot reactor can be brought back down.
     * <p>
     * Called once per game tick. The per-tick amount is {@code getPassiveCoolingPerSecond() / 20}, which is fractional
     * for every tier (EV: 83/20 = 4.15 HU), so the fraction is accumulated instead of truncated. Truncating it was a
     * real bug: {@code floor(83/20)} is 4 HU/tick, i.e. 80 HU/s, and every tier from EV to ZPM would have cooled at
     * an identical 4 HU/tick because the floor swallowed the difference between them.
     *
     * @return true when any page's heat changed
     */
    public boolean tick() {
        return applyPassiveCooling();
    }

    /** Accumulates this tick's fraction of the cooling rate and applies whole HU once a full unit is available. */
    private boolean applyPassiveCooling() {
        double perSecond = getPassiveCoolingPerSecond();
        if (perSecond <= 0 || pageHeat.isEmpty()) return false;
        double factor = isCountdownActive() ? 0.5D : 1.0D;
        coolingProgress += perSecond / CYCLE_TICKS * factor;
        if (coolingProgress < 1.0D) return false;
        int toRemove = (int) coolingProgress;
        coolingProgress -= toRemove;
        return removeHeat(toRemove);
    }

    /**
     * Removes {@code amount} HU in total from the hottest pages of the structure, never dropping a page below zero.
     * Page selection follows the same rule as the meltdown countdown: it only regards a page as "hot" when its share
     * of the hottest page exceeds {@link #PASSIVE_COOLING_SHARE_THRESHOLD}, so the empty pages of a large structure do
     * not silently absorb the whole cooling budget.
     */
    private boolean removeHeat(int amount) {
        if (pageHeat.isEmpty()) return false;
        int hottest = getHottestHeat();
        if (hottest <= 0) {
            coolingProgress = 0.0D;
            return false;
        }
        int threshold = Math.max(1, (int) (hottest * PASSIVE_COOLING_SHARE_THRESHOLD));
        boolean changed = false;
        for (int[] heat : pageHeat.values()) {
            for (int i = 0; i < heat.length && amount > 0; i++) {
                if (heat[i] < threshold) continue;
                int removed = Math.min(amount, heat[i]);
                heat[i] -= removed;
                amount -= removed;
                changed = true;
            }
        }
        return changed;
    }

    // endregion

    /**
     * Largest plating bonus found in a page snapshot. The prediction needs the page's real ceiling <b>before</b> it
     * runs, because a page carrying platings must not be predicted to melt down against the bare hatch ceiling.
     */
    public int collectPlatingBonus(ItemStack[] pageItems) {
        if (pageItems == null) return 0;
        int best = 0;
        for (ItemStack stack : pageItems) {
            if (!(stack != null && stack.getItem() instanceof ItemReactorPlating plating)) continue;
            if (plating.maxHeatAdd > best) best = plating.maxHeatAdd;
        }
        return best;
    }

    /** Ceiling of a page for which the plating bonus is already known. */
    public int getMaxHeatWithBonus(int bonus) {
        long sum = (long) capacity + Math.max(0, Math.min(bonus, MAX_HEAT_BONUS_PER_ITEM));
        return (int) Math.min(Math.max(1L, sum), MAX_HEAT_CAPACITY);
    }

    // region Fluid (wet) mode
    /** True when the controller's fluid reactor mode is enabled. */
    public boolean isFluidMode() {
        return fluidMode;
    }

    public void setFluidMode(boolean value) {
        if (fluidMode == value) return;
        fluidMode = value;
        if (reactor != null) reactor.markDirty();
    }

    /** IC2's {@code IReactor#addEmitHeat}: components hand heat to the fluid loop instead of to the page. */
    public void addEmitHeat(int heat) {
        if (heat > 0) emittedHeat += heat;
    }

    /** Buffered heat still waiting for coolant, shown in the GUI. */
    public int getPendingEmitHeat() {
        return Math.max(0, emittedHeat - coolantExchangedHeat);
    }

    /**
     * IC2's fluid exchange for one page tick: the coolant takes as much buffered heat as it can carry, and whatever is
     * left over goes back onto the core as {@code overflow = leftover / 2} (IC2's {@code addHeat(huOutput / 2)}).
     * <p>
     * The result is returned as a pair on purpose. The earlier signature returned only the overflow while reading the
     * buffered amount from the internal counter - which the caller had to clear to keep one page's buffer from leaking
     * into the next page's exchange. That order made the exchange always absorb nothing and silently discard the heat
     * the coolant could not take. Both numbers are computed here, before anything is cleared.
     *
     * @param coolantAcceptedUnits buffered-heat units the coolant taken this tick can absorb
     */
    public CoolantExchange coolByCoolant(long coolantAcceptedUnits) {
        int pending = getPendingEmitHeat();
        int absorbed = (int) Math.max(0L, Math.min(coolantAcceptedUnits, pending));
        return new CoolantExchange(absorbed, (pending - absorbed) / 2);
    }

    /** Clears the per-cycle fluid accounting; call once per page tick after the exchange. */
    public void resetCycleEmitHeat() {
        emittedHeat = 0;
        coolantExchangedHeat = 0;
    }

    /** Coolant (mB) needed to carry away one unit of buffered component heat. */
    public static long coolantForHeat(long heat) {
        return Math.max(0L, heat) * COOLANT_PER_BUFFER_UNIT;
    }

    /** Outcome of one fluid exchange: how much the coolant took, and what has to go back onto the core. */
    public static final class CoolantExchange {

        /** Buffered component heat the coolant absorbed this tick. */
        public final int absorbed;
        /** Remaining buffered heat that goes back onto the core, already halved. */
        public final int overflow;

        CoolantExchange(int absorbed, int overflow) {
            this.absorbed = absorbed;
            this.overflow = overflow;
        }
    }

    // endregion

    // region Persistence

    public void saveNBTData(NBTTagCompound aNBT) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<ChunkCoordinates, int[]> entry : pageHeat.entrySet()) {
            ChunkCoordinates key = entry.getKey();
            NBTTagCompound pageTag = new NBTTagCompound();
            pageTag.setInteger("x", key.posX);
            pageTag.setInteger("y", key.posY);
            pageTag.setInteger("z", key.posZ);
            pageTag.setIntArray("heat", entry.getValue());
            list.appendTag(pageTag);
        }
        aNBT.setTag("mtReactorPageHeat2", list);
        aNBT.setInteger("mtReactorComponentBonus", componentBonus);
        aNBT.setBoolean("mtReactorFluidMode", fluidMode);

        // A running meltdown is persisted as its *remaining* ticks, never as an absolute deadline: the machine's tick
        // counter restarts, so an absolute value would be meaningless after a reload. Saving the remainder also means
        // an unloaded reactor does not quietly serve its grace window while nobody is looking - it resumes exactly
        // where it stopped.
        NBTTagList armedList = new NBTTagList();
        for (ArmedPage armed : armedPages.values()) {
            NBTTagCompound armedTag = new NBTTagCompound();
            armedTag.setInteger("x", armed.owner.posX);
            armedTag.setInteger("y", armed.owner.posY);
            armedTag.setInteger("z", armed.owner.posZ);
            armedTag.setInteger("page", armed.page);
            long remaining = armed.deadline - armed.now;
            if (remaining <= 0) continue;
            armedTag.setInteger("ticks", (int) Math.min(remaining, MELTDOWN_COUNTDOWN_TICKS));
            armedList.appendTag(armedTag);
        }
        aNBT.setTag("mtReactorArmedCountdowns", armedList);
    }

    /**
     * Restores the heat store.
     * <p>
     * The countdowns come back as <b>pending</b>, not as deadlines: the tick counter they were measured against does
     * not survive a load, so {@link #refreshCountdownClock(long)} anchors them onto the counter the machine actually
     * starts with (see {@link #loadNow}).
     */
    public void loadNBTData(NBTTagCompound aNBT) {
        pageHeat.clear();
        pageBonus.clear();
        armedPages.clear();
        emittedHeat = 0;
        coolantExchangedHeat = 0;
        componentBonus = Math.max(0, aNBT.getInteger("mtReactorComponentBonus"));
        fluidMode = aNBT.getBoolean("mtReactorFluidMode");

        NBTTagList list = aNBT.getTagList("mtReactorPageHeat2", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound pageTag = list.getCompoundTagAt(i);
            ChunkCoordinates key = new ChunkCoordinates(
                pageTag.getInteger("x"),
                pageTag.getInteger("y"),
                pageTag.getInteger("z"));
            int[] heat = pageTag.getIntArray("heat");
            if (heat == null || heat.length == 0) continue;
            if (heat.length > MAX_PAGES) heat = Arrays.copyOf(heat, MAX_PAGES);
            pageHeat.put(key, heat);
        }

        NBTTagList armedList = aNBT.getTagList("mtReactorArmedCountdowns", 10);
        for (int i = 0; i < armedList.tagCount(); i++) {
            NBTTagCompound armedTag = armedList.getCompoundTagAt(i);
            int ticks = Math.min(Math.max(1, armedTag.getInteger("ticks")), MELTDOWN_COUNTDOWN_TICKS);
            ChunkCoordinates owner = new ChunkCoordinates(
                armedTag.getInteger("x"),
                armedTag.getInteger("y"),
                armedTag.getInteger("z"));
            int page = Math.max(0, armedTag.getInteger("page"));
            armedPages.put(
                owner.posX + "," + owner.posY + "," + owner.posZ + "#" + page,
                new ArmedPage(owner, page, loadNow + ticks, loadNow));
        }
    }

    /**
     * Tells the heat store which tick the machine's counter is at while its data is being loaded, so countdowns
     * restored from NBT can be anchored onto the running counter on the next tick. Called by {@code MTReactor} from its
     * own {@code loadNBTData}, which knows the tick value; the heat store cannot look it up itself.
     */
    public void setLoadClock(long now) {
        loadNow = now;
    }

    /** Drops all heat and countdown state; used when the reactor detonates. */
    public void clear() {
        pageHeat.clear();
        pageBonus.clear();
        armedPages.clear();
        componentBonus = 0;
        emittedHeat = 0;
        coolantExchangedHeat = 0;
    }

    // endregion

    /** One page's running meltdown countdown. */
    public static final class ArmedPage {

        /** Coordinates of the access hatch that owns the page. */
        public final ChunkCoordinates owner;
        public final int page;
        /**
         * Absolute tick of the machine's own counter at which the page detonates. Not final: the counter restarts when
         * the chunk is loaded, and {@link #refreshCountdownClock(long)} shifts the deadline by the same amount so the
         * remaining grace window survives the reload.
         */
        private long deadline;
        /** Current game tick, refreshed by {@link #refreshCountdownClock(long)}. */
        private long now;

        ArmedPage(ChunkCoordinates owner, int page, long deadline, long now) {
            this.owner = owner;
            this.page = page;
            this.deadline = deadline;
            this.now = now;
        }
    }

    /**
     * The view of an access hatch the heat store needs. Implemented by
     * {@code MTReactorAccessHatch}, so the heat system does not have to depend on the hatch class directly.
     */
    public interface PageOwner {

        /** Stable identity of the hatch, i.e. its block coordinates. */
        ChunkCoordinates mtReactorKey();

        /** Number of 9x6 pages this hatch exposes. */
        int mtReactorPageCount();
    }
}
