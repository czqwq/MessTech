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
 * Per-page heat state of {@link MTReactor}: heat, meltdown countdown, passive cooling, fluid heat exchange.
 * <p>
 * Pages are keyed by hatch coordinates ({@link #key}), so adding, removing or moving a hatch cannot renumber other
 * pages or migrate stored heat - a global page order did exactly that.
 * <p>
 * {@link #onCapacityChanged(int)} rescales stored heat when the ceiling changes (otherwise 900,000 HU under a UHV hatch
 * detonated once an EV hatch replaced it: the check is a plain {@code heat / maxHeat} ratio). {@link #tick()} runs the
 * meltdown countdown - heat at or above the ceiling arms a {@value #MELTDOWN_COUNTDOWN_TICKS} tick timer that
 * {@link #resetCountdown()} cancels once heat is back under. Its deadline is an absolute game tick, not a per-page
 * counter (a page is visited once per cycle: per-visit counting stretched the window to 30 s x page count), and keeps
 * running while the machine is off.
 */
public final class MTReactorHeatSystem {

    /** Ticks between two IC2 reactor cycles. */
    public static final int CYCLE_TICKS = 20;
    /** Meltdown countdown: 30 s of grace before a page at or over its ceiling detonates. */
    public static final int MELTDOWN_COUNTDOWN_TICKS = 600;
    /** Pages the heat store can hold: 25 access-hatch positions x 8 pages each. */
    public static final int MAX_PAGES = 256;
    /** Upper bound of the heat bonus one reactor plating may contribute. */
    private static final int MAX_HEAT_BONUS_PER_ITEM = 100_000;
    /**
     * Coolant (mB) per unit of buffered component heat: 1 mB per 1 HU here (IC2 converts through
     * {@code huOutputModifier}), so the feed rate reads as "as much coolant per second as the core makes heat per
     * second". <b>Keep this at 1, or adjust the caller:</b> {@code MTReactor#releaseCoolantHeat()} passes the mB it
     * exchanged into {@link #coolByCoolant(long)}, whose parameter is buffered-heat units, so IC2's x2 would need that
     * call site to divide by it.
     */
    public static final int COOLANT_PER_BUFFER_UNIT = 1;

    /**
     * Hard cap of the heat store. The UIV heat control hatch asks for {@code Integer.MAX_VALUE}, unusable as a
     * ceiling: heat effects compare {@code heat / maxHeat}, and at 2.1e9 the 40% / 70% / 85% / 100% thresholds are
     * unreachable, so a UIV hatch silently switched the risk model off. This cap is still far above what one 9x6 page
     * can produce (IC2's largest heat storage item is under 100,000).
     */
    public static final int MAX_HEAT_CAPACITY = 50_000_000;

    /** Lowest supported heat control hatch tier ({@link MTReactorHeatHatch#MIN_TIER}). */
    private static final int MIN_TIER = MTReactorHeatHatch.MIN_TIER;

    /**
     * Passive cooling in HU/s per heat control hatch tier, indexed by {@code tier - MIN_TIER} (EV..UIV): a real tier's
     * rate is its heat ceiling per 2400 ticks ({@code ceiling / 120} HU/s, a full ceiling in two minutes), e.g.
     * {@code 10,000 / 120 = 83.3} for EV, rounded to whole HU/s. UIV is decoupled on purpose: at its 50,000,000 ceiling
     * {@code ceiling / 120} would be 416,667 HU/s, faster than the reactor can produce heat; it uses 200,000 HU/s
     * instead (the whole 50,000,000 in 250 s).
     */
    private static final long[] PASSIVE_COOLING_PER_SECOND = { 83, 167, 417, 833, 1_667, 4_167, 8_333, 200_000 };

    /** Only pages with at least this fraction of the hottest page's heat are bled passively. */
    private static final float PASSIVE_COOLING_SHARE_THRESHOLD = 0.05F;

    private final MTReactor reactor;
    private final Map<ChunkCoordinates, int[]> pageHeat = new HashMap<>();
    /** Meltdown countdown per page, keyed by hatch coordinates plus page index. */
    private final Map<String, ArmedPage> armedPages = new HashMap<>();

    /** Ceiling in effect, from {@link MTReactor#getReactorHeatCapacity()}. */
    private int capacity = MTReactorHeatHatch.DEFAULT_HEAT_CAPACITY;
    /** Largest per-page value the components ask for through {@link #setMaxHeat}. */
    private int componentBonus;
    /** Per-page plating bonus, mirroring {@link #pageHeat}. */
    private final Map<ChunkCoordinates, int[]> pageBonus = new HashMap<>();

    /** Heat the components emitted this cycle via {@link #addEmitHeat(int)}. */
    private int emittedHeat;
    /** How much of {@link #emittedHeat} the coolant already carried away. */
    private int coolantExchangedHeat;
    private boolean fluidMode;
    private double coolingProgress;
    /** Last tick seen by {@link #refreshCountdownClock(long)}; detects a restarted counter. */
    private long lastNow = -1L;
    /** Tick counter of the last {@link #loadNBTData}, which reloaded countdowns re-anchor onto. */
    private long loadNow = -1L;

    public MTReactorHeatSystem(MTReactor reactor) {
        this.reactor = reactor;
    }

    static ChunkCoordinates key(PageOwner owner) {
        return owner == null ? null : owner.mtReactorKey();
    }

    public int getCapacity() {
        return capacity;
    }

    /**
     * Heat ceiling including the plating contribution, clamped: what components see as {@code getMaxHeat()}, hence the
     * explosion threshold.
     */
    public int getEffectiveMaxHeat() {
        long sum = (long) capacity + componentBonus;
        if (sum < 1) return 1;
        return (int) Math.min(sum, MAX_HEAT_CAPACITY);
    }

    /**
     * Reactor plating's {@code setMaxHeat(getMaxHeat() + maxHeatAdd)}, resolved as an absolute target: the largest
     * value becomes the page's ceiling ({@code capacity + largest plating bonus}), tracked per page
     * ({@link #setPageMaxHeat}) because a 9x6 page is an independent reactor grid.
     */
    public void setMaxHeat(PageOwner owner, int page, int newMaxHeat) {
        setPageMaxHeat(owner, page, newMaxHeat - capacity);
    }

    /** Records a plating's heat bonus for one page, clamped to the item bound. */
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
     * Clears one page's plating bonuses before its heat pass re-collects them; per page, not per cycle, so a plating
     * removed from page 3 stops contributing there while page 4 keeps its own.
     */
    public void resetPageBonus(PageOwner owner, int page) {
        int[] bonuses = pageBonus.get(key(owner));
        if (bonuses == null || page < 0 || page >= bonuses.length) return;
        bonuses[page] = 0;
    }

    public int getPageBonus(PageOwner owner, int page) {
        int[] bonuses = pageBonus.get(key(owner));
        if (bonuses == null || page < 0 || page >= bonuses.length) return 0;
        return bonuses[page];
    }

    public int getPageMaxHeat(PageOwner owner, int page) {
        return getMaxHeatWithBonus(getPageBonus(owner, page));
    }

    /**
     * Called whenever the structure's heat control hatch may have changed: rescales every page's heat by the ratio of
     * new to old ceiling and clears running countdowns. Rescale rather than clamp: 90% of a 1,000,000 ceiling stays 90%
     * of the new one, while clamping leaves the heat over the ceiling (the check reads {@code power >= 1.0}).
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
        // Changing the ceiling is a player action: give the meltdown its full grace period again.
        armedPages.clear();
        if (reactor != null) reactor.markDirty();
    }

    /** Heat of one page; 0 if the hatch was never written. */
    public int getPageHeat(PageOwner owner, int page) {
        int[] heat = pageHeat.get(key(owner));
        if (heat == null || page < 0 || page >= heat.length) return 0;
        return heat[page];
    }

    /** Writes one page's heat, creating the per-hatch array on first use. */
    public void setPageHeat(PageOwner owner, int page, int value) {
        int[] heat = lookupOrCreate(owner);
        if (heat == null || page < 0 || page >= heat.length) return;
        heat[page] = value;
    }

    /** Adds to one page's heat and returns the result, like IC2's {@code IReactor#addHeat}. */
    public int addPageHeat(PageOwner owner, int page, int amount) {
        int[] heat = lookupOrCreate(owner);
        if (heat == null || page < 0 || page >= heat.length) return 0;
        heat[page] += amount;
        return heat[page];
    }

    public int getHottestHeat() {
        int hottest = 0;
        for (int[] heat : pageHeat.values()) {
            for (int value : heat) {
                if (value > hottest) hottest = value;
            }
        }
        return Math.max(hottest, 0);
    }

    /** Ceiling shown next to the hottest heat, never below 1 (the GUI divides by it). */
    public int getHottestMaxHeat() {
        return Math.max(1, getEffectiveMaxHeat());
    }

    /** Heat percentage of the hottest page, computed in {@code double} to avoid overflow. */
    public int getHottestHeatPercent() {
        return (int) Math.round(getHottestHeat() * 100.0D / getHottestMaxHeat());
    }

    /**
     * Drops the heat of hatches no longer in the structure, so a removed hatch cannot hand its heat to a later hatch
     * at the same coordinates with a different page layout.
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

    /** Forgets the countdowns of one block position (hatch changed or removed). */
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

    /** Ticks left until the first armed page detonates; 0 when nothing is armed. */
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

    /** 0.0 (armed) .. 1.0 (detonating) countdown progress, for the GUI/Waila alarm. */
    public float getCountdownProgress() {
        int ticks = getCountdownTicks();
        if (ticks <= 0) return 0.0F;
        return 1.0F - Math.min(1.0F, (float) ticks / MELTDOWN_COUNTDOWN_TICKS);
    }

    /** Cancels every running countdown: a player action, or all heat came back down. */
    public void resetCountdown() {
        if (!armedPages.isEmpty()) {
            armedPages.clear();
            if (reactor != null) reactor.markDirty();
        }
    }

    /**
     * (Re-)arms one page's countdown, i.e. the page is at or over its ceiling; the deadline is an absolute game tick
     * (see the class comment).
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

    /** Drops one page's countdown once it is back under its ceiling. */
    public void clearCountdown(PageOwner owner, int page) {
        if (armedPages.remove(armedKey(owner, page)) != null && reactor != null) reactor.markDirty();
    }

    /**
     * Keeps the countdowns' "now" up to date; call once per game tick. {@link #saveNBTData} stores remaining ticks,
     * not a deadline, and the counter can reset (a save load, or a tile entity re-initialisation not preceded by
     * {@code readFromNBT}): a restored deadline is re-anchored on the first tick after {@link #loadNBTData}
     * ({@code loadNow}, once), and a backwards jump in the otherwise monotonic counter shifts every deadline by the
     * same amount.
     */
    public void refreshCountdownClock(long now) {
        if (loadNow >= 0) {
            // First tick after a data load: the countdowns came back as pending amounts (see loadNBTData), so anchor
            // them onto the counter the machine is really running on.
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

    /** Lists the pages whose grace window ran out and forgets them; the caller detonates these. */
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

    public long getPassiveCoolingPerSecond() {
        int tier = reactor == null ? MIN_TIER : reactor.getHeatControlTier();
        int index = tier - MIN_TIER;
        if (index < 0) index = 0;
        if (index >= PASSIVE_COOLING_PER_SECOND.length) index = PASSIVE_COOLING_PER_SECOND.length - 1;
        return PASSIVE_COOLING_PER_SECOND[index];
    }

    /**
     * Bleeds heat off every page carrying a meaningful fraction of the hottest page's heat, so the loop is a no-op for
     * a large structure's empty pages; it runs whether or not the reactor produces energy, since it is the only way an
     * already hot reactor comes back down. Once per game tick it applies {@code getPassiveCoolingPerSecond() / 20},
     * fractional for every tier (EV: 83/20 = 4.15 HU), so the fraction is accumulated rather than truncated
     * (truncating made EV..ZPM all cool at 4 HU/tick).
     *
     * @return true when any page's heat changed
     */
    public boolean tick() {
        return applyPassiveCooling();
    }

    /** Accumulates this tick's cooling fraction, applying whole HU when available. */
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
     * Removes {@code amount} HU in total from the hottest pages, never dropping a page below zero. As with the meltdown
     * countdown, a page only counts as "hot" when its share of the hottest page exceeds
     * {@link #PASSIVE_COOLING_SHARE_THRESHOLD}, so a large structure's empty pages do not absorb the budget.
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

    /**
     * Largest plating bonus in a page snapshot; the prediction needs the real ceiling <b>before</b> a page runs, so
     * platings are not missing from it.
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

    public int getMaxHeatWithBonus(int bonus) {
        long sum = (long) capacity + Math.max(0, Math.min(bonus, MAX_HEAT_BONUS_PER_ITEM));
        return (int) Math.min(Math.max(1L, sum), MAX_HEAT_CAPACITY);
    }

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
     * IC2's fluid exchange for one page tick: the coolant takes as much buffered heat as it can carry, the leftover
     * goes back onto the core as {@code overflow = leftover / 2} (IC2's {@code addHeat(huOutput / 2)}). Both numbers
     * are computed here, before anything is cleared, because clearing the counter first made the exchange absorb
     * nothing and discard the heat the coolant could not take.
     *
     * @param coolantAcceptedUnits buffered-heat units the coolant taken this tick can absorb
     */
    public CoolantExchange coolByCoolant(long coolantAcceptedUnits) {
        int pending = getPendingEmitHeat();
        int absorbed = (int) Math.max(0L, Math.min(coolantAcceptedUnits, pending));
        return new CoolantExchange(absorbed, (pending - absorbed) / 2);
    }

    /** Clears the per-cycle fluid accounting; call once per page tick, after the exchange. */
    public void resetCycleEmitHeat() {
        emittedHeat = 0;
        coolantExchangedHeat = 0;
    }

    public static long coolantForHeat(long heat) {
        return Math.max(0L, heat) * COOLANT_PER_BUFFER_UNIT;
    }

    /** One fluid exchange: what the coolant took and what goes back onto the core. */
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

        // A running meltdown is persisted as its *remaining* ticks, never an absolute deadline: the tick counter
        // restarts, so an absolute value would be meaningless after a reload, and an unloaded reactor resumes exactly
        // where it stopped instead of serving its grace window unseen.
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
     * Restores the heat store. Countdowns come back as <b>pending</b> amounts, not deadlines, because their counter
     * does not survive a load; {@link #refreshCountdownClock(long)} anchors them (see {@link #loadNow}).
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
     * Tells the heat store the machine's tick value while its data loads, so restored countdowns can be anchored next
     * tick; {@code MTReactor} calls it from {@code loadNBTData}, which knows the value.
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

    /** One page's running meltdown countdown. */
    public static final class ArmedPage {

        public final ChunkCoordinates owner;
        public final int page;
        /**
         * Absolute tick of the machine's counter at which the page detonates; not final, because the counter restarts
         * on chunk load and {@link #refreshCountdownClock(long)} shifts the deadline by the same amount.
         */
        private long deadline;
        private long now;

        ArmedPage(ChunkCoordinates owner, int page, long deadline, long now) {
            this.owner = owner;
            this.page = page;
            this.deadline = deadline;
            this.now = now;
        }
    }

    /**
     * The view of an access hatch the heat store needs, implemented by {@code MTReactorAccessHatch} so the heat system
     * does not depend on the hatch class.
     */
    public interface PageOwner {

        /** Stable identity of the hatch, i.e. its block coordinates. */
        ChunkCoordinates mtReactorKey();

        /** Number of 9x6 pages this hatch exposes. */
        int mtReactorPageCount();
    }
}
