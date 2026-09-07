package com.MessTech.common.machine.Base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.VoidProtectionHelper;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for tick-driven, multi-threaded parallel machines.
 * <p>
 * The scheduler is driven from {@code checkProcessing()}, normally in a one-second GT multiblock
 * cycle, so the standard {@code startRecipeProcessing()} / {@code endRecipeProcessing()} wrapper is
 * active while inputs are read and consumed. Each {@link WorkThread} belongs to one recipe pool and
 * can run multiple independent {@link RecipeTask}s simultaneously. A task is one started recipe with
 * its own progress, EU/t, parallel count and output buffers.
 * <p>
 * Subclasses should set {@code mMaxProgresstime} to a short cycle (e.g. 20 ticks) and call
 * {@link #tickThreadScheduler(IGregTechTileEntity, long)} from their {@code checkProcessing()}.
 * The normal GT base then drains {@code lEUt} every running tick and invokes the scheduler once per
 * cycle.
 */
public abstract class TickableParallelismAcrossMultiMachineBase<T extends TickableParallelismAcrossMultiMachineBase<T>>
    extends MTMultiMachineBase<T> {

    /** Internal work-thread registry, ordered by creation time. */
    protected final Map<String, WorkThread> threads = new LinkedHashMap<>();

    public TickableParallelismAcrossMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public TickableParallelismAcrossMultiMachineBase(String aName) {
        super(aName);
    }

    // region Thread management API

    /** Maximum number of work threads this machine allows. */
    public abstract int getMaxThreadCount();

    /** Main scheduler interval in ticks. Defaults to 20 ticks = 1 second. */
    protected int getMainThreadInterval() {
        return 20;
    }

    /** How many new recipe tasks one thread may try to start per scheduler tick. */
    protected int getMaxNewTasksPerThread(WorkThread thread) {
        return 16;
    }

    /** Maximum parallel recipe copies per single task. */
    protected int getMaxParallelForTask(RecipeTask task) {
        return 1;
    }

    /** Maximum parallel recipe copies a thread may request before a task exists. */
    protected int getMaxParallelForThread(WorkThread thread) {
        return 1;
    }

    /** Default thread name generator, used by subclasses that pre-create threads. */
    protected String getDefaultThreadName(int index) {
        return "Thread_" + index;
    }

    public Collection<WorkThread> getThreads() {
        return threads.values();
    }

    public List<String> getThreadNames() {
        return new ArrayList<>(threads.keySet());
    }

    public int getThreadCount() {
        return threads.size();
    }

    @Nullable
    public WorkThread getThread(String name) {
        return name == null ? null : threads.get(name);
    }

    @Nullable
    public WorkThread addThread(String name) {
        if (name == null || name.isEmpty()) return null;
        if (threads.containsKey(name)) return null;
        if (threads.size() >= getMaxThreadCount()) return null;

        WorkThread thread = new WorkThread(name);
        threads.put(name, thread);
        onThreadAdded(thread);
        return thread;
    }

    public boolean removeThread(String name) {
        WorkThread removed = threads.remove(name);
        if (removed != null) {
            onThreadRemoved(removed);
            return true;
        }
        return false;
    }

    public void clearThreads() {
        for (WorkThread thread : new ArrayList<>(threads.values())) {
            removeThread(thread.getName());
        }
    }

    protected void onThreadAdded(WorkThread thread) {}

    protected void onThreadRemoved(WorkThread thread) {}

    /**
     * Scheduling priority. Higher priority threads get first chance to consume power/inputs.
     */
    protected int getThreadPriority(WorkThread thread) {
        return 0;
    }

    // endregion

    // region NBT persistence

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);

        NBTTagList taskList = new NBTTagList();
        for (WorkThread thread : threads.values()) {
            for (RecipeTask task : thread.getTasks()) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("thread", thread.getName());
                entry.setInteger("progress", task.progressTime);
                entry.setInteger("max", task.maxProgressTime);
                entry.setLong("eut", task.eut);
                entry.setInteger("parallel", task.parallel);
                entry.setBoolean("awaitingOutput", task.awaitingOutput);

                if (task.outputItems != null) {
                    entry.setInteger("itemOutCount", task.outputItems.length);
                    for (int i = 0; i < task.outputItems.length; i++) {
                        if (task.outputItems[i] != null) {
                            GTUtility.saveItem(entry, "itemOut" + i, task.outputItems[i]);
                        }
                    }
                }
                if (task.outputFluids != null) {
                    entry.setInteger("fluidOutCount", task.outputFluids.length);
                    for (int i = 0; i < task.outputFluids.length; i++) {
                        if (task.outputFluids[i] != null) {
                            entry.setTag("fluidOut" + i, task.outputFluids[i].writeToNBT(new NBTTagCompound()));
                        }
                    }
                }

                taskList.appendTag(entry);
            }
        }
        aNBT.setTag("nsfTasks", taskList);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        clearAllTasks();

        if (!aNBT.hasKey("nsfTasks")) return;
        NBTTagList taskList = aNBT.getTagList("nsfTasks", 10);
        for (int i = 0; i < taskList.tagCount(); i++) {
            NBTTagCompound entry = taskList.getCompoundTagAt(i);
            WorkThread thread = getThread(entry.getString("thread"));
            if (thread == null || thread.getRecipeMap() == null) continue;

            ItemStack[] itemOutputs = null;
            if (entry.hasKey("itemOutCount")) {
                int count = entry.getInteger("itemOutCount");
                itemOutputs = new ItemStack[count];
                for (int j = 0; j < count; j++) {
                    itemOutputs[j] = GTUtility.loadItem(entry, "itemOut" + j);
                }
            }

            FluidStack[] fluidOutputs = null;
            if (entry.hasKey("fluidOutCount")) {
                int count = entry.getInteger("fluidOutCount");
                fluidOutputs = new FluidStack[count];
                for (int j = 0; j < count; j++) {
                    if (entry.hasKey("fluidOut" + j)) {
                        fluidOutputs[j] = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag("fluidOut" + j));
                    }
                }
            }

            RecipeTask task = thread.start(entry.getInteger("max"), thread.getRecipeMap(), itemOutputs, fluidOutputs);
            task.progressTime = entry.getInteger("progress");
            task.eut = entry.getLong("eut");
            task.parallel = Math.max(1, entry.getInteger("parallel"));
            task.awaitingOutput = entry.getBoolean("awaitingOutput");
        }
    }

    // endregion

    // region GUI status text

    public WorkThread getThreadByIndex(int index) {
        if (index < 0) return null;
        int current = 0;
        for (WorkThread thread : threads.values()) {
            if (current == index) return thread;
            current++;
        }
        return null;
    }

    public String getThreadName(int index) {
        WorkThread thread = getThreadByIndex(index);
        return thread == null ? "" : thread.getName();
    }

    public boolean isThreadActive(int index) {
        WorkThread thread = getThreadByIndex(index);
        return thread != null && thread.isActive();
    }

    public double getThreadProgress(int index) {
        WorkThread thread = getThreadByIndex(index);
        if (thread == null || !thread.isActive()) return 0;
        int max = thread.getMaxProgressTime();
        if (max <= 0) return 0;
        return Math.min(1.0, (double) thread.getProgressTime() / max);
    }

    /** Builds a simple text representation of all threads, used by the base multi-thread GUI. */
    public String getThreadStatusText() {
        StringBuilder sb = new StringBuilder();
        for (WorkThread thread : threads.values()) {
            sb.append(thread.getName())
                .append(": ");
            if (!thread.isActive()) {
                sb.append("idle\n");
                continue;
            }
            for (RecipeTask task : thread.getTasks()) {
                sb.append(task.getProgressTime())
                    .append('/')
                    .append(task.getMaxProgressTime())
                    .append("t ")
                    .append(task.getParallel())
                    .append("x ")
                    .append(task.getEUt())
                    .append("EU/t  ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // endregion

    // region Scheduler

    /**
     * Advances and starts work-thread tasks. This should be called from {@code checkProcessing()}
     * while the standard multiblock {@code checkRecipe()} wrapper has {@code startRecipeProcessing()} /
     * {@code endRecipeProcessing()} active, so ME hatches and other recipe-processing-aware hatches
     * work correctly.
     */
    protected void tickThreadScheduler(IGregTechTileEntity base, long aTick) {
        List<WorkThread> orderedThreads = new ArrayList<>(threads.values());
        orderedThreads.sort(
            Comparator.comparingInt(this::getThreadPriority)
                .reversed());
        for (WorkThread thread : orderedThreads) {
            tickThread(base, thread, aTick);
        }
    }

    protected void tickThread(IGregTechTileEntity base, WorkThread thread, long aTick) {
        // Advance and finish tasks.
        Iterator<RecipeTask> iterator = thread.getTasks()
            .iterator();
        while (iterator.hasNext()) {
            RecipeTask task = iterator.next();
            if (!task.isAwaitingOutput()) {
                if (task.progressTime < task.maxProgressTime) {
                    task.progressTime += getMainThreadInterval();
                    if (task.progressTime >= task.maxProgressTime) {
                        task.progressTime = task.maxProgressTime;
                    }
                    onTaskTick(thread, task);
                }
            }

            if (task.progressTime >= task.maxProgressTime) {
                if (canOutputTask(task)) {
                    finishTask(thread, task);
                    iterator.remove();
                } else {
                    task.setAwaitingOutput(true);
                }
            }
        }

        // Try to start additional independent tasks from the same thread/pool.
        int attempts = 0;
        while (attempts < getMaxNewTasksPerThread(thread)) {
            CheckRecipeResult result = checkAndStartThread(base, thread);
            if (!result.wasSuccessful()) break;
            attempts++;
        }
    }

    /**
     * Checks whether every item/fluid output of the task can currently fit into output buses/hatches.
     * Used by the output retry mechanism to avoid voiding outputs.
     */
    protected boolean canOutputTask(RecipeTask task) {
        if (task.outputItems == null && task.outputFluids == null) return true;
        if ((task.outputItems == null || task.outputItems.length == 0)
            && (task.outputFluids == null || task.outputFluids.length == 0)) {
            return true;
        }

        VoidProtectionHelper helper = new VoidProtectionHelper().setMachine(this, true, true)
            .setItemOutputs(task.outputItems)
            .setFluidOutputs(task.outputFluids)
            .build();
        return helper.getMaxParallel() > 0;
    }

    /**
     * Try to find and start one recipe task on the given work thread.
     *
     * @return successful result if a task was started.
     */
    @NotNull
    protected CheckRecipeResult checkAndStartThread(IGregTechTileEntity base, WorkThread thread) {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    protected void finishTask(WorkThread thread, RecipeTask task) {
        if (task.outputItems != null && task.outputItems.length > 0) {
            addItemOutputs(task.outputItems);
        }
        if (task.outputFluids != null && task.outputFluids.length > 0) {
            addFluidOutputs(task.outputFluids);
        }
        onTaskFinished(thread, task);
    }

    protected void onTaskStarted(WorkThread thread, RecipeTask task) {}

    protected void onTaskFinished(WorkThread thread, RecipeTask task) {}

    /** Called each time a running task advances by one scheduler interval. */
    protected void onTaskTick(WorkThread thread, RecipeTask task) {}

    protected long getTotalThreadEUt() {
        long total = 0;
        for (WorkThread thread : threads.values()) {
            total += thread.getEUt();
        }
        return total;
    }

    protected void clearAllTasks() {
        for (WorkThread thread : threads.values()) {
            thread.clearTasks();
        }
    }

    // endregion

    /**
     * A thread is a named worker bound to one recipe pool. It can hold multiple {@link RecipeTask}s.
     */
    @Getter
    public class WorkThread {

        private final String name;
        private RecipeMap<?> recipeMap;
        private int circuitNumber = 0;
        private final List<RecipeTask> tasks = new ArrayList<>();

        public WorkThread(String name) {
            this.name = name;
        }

        public WorkThread setRecipeMap(RecipeMap<?> recipeMap) {
            this.recipeMap = recipeMap;
            return this;
        }

        public WorkThread setCircuitNumber(int circuitNumber) {
            this.circuitNumber = circuitNumber;
            return this;
        }

        public boolean isActive() {
            return !tasks.isEmpty();
        }

        /** Aggregate EU/t of all running (not output-blocked) tasks in this thread. */
        public long getEUt() {
            long total = 0;
            for (RecipeTask task : tasks) {
                if (!task.isAwaitingOutput()) {
                    total += task.eut;
                }
            }
            return total;
        }

        /** Aggregate parallel count of all tasks in this thread. */
        public int getParallel() {
            int total = 0;
            for (RecipeTask task : tasks) {
                total += task.parallel;
            }
            return total;
        }

        // Backwards-compatible single-progress view used by Waila: first task, or 0 when idle.
        public int getProgressTime() {
            return tasks.isEmpty() ? 0 : tasks.get(0).progressTime;
        }

        public int getMaxProgressTime() {
            return tasks.isEmpty() ? 0 : tasks.get(0).maxProgressTime;
        }

        public ItemStack[] getOutputItems() {
            return tasks.isEmpty() ? null : tasks.get(0).outputItems;
        }

        public FluidStack[] getOutputFluids() {
            return tasks.isEmpty() ? null : tasks.get(0).outputFluids;
        }

        public void clearTasks() {
            tasks.clear();
        }

        /**
         * Legacy helper: add a single task. Kept for existing subclasses that used one task per thread.
         */
        public RecipeTask start(int duration, RecipeMap<?> map, ItemStack[] outputs, FluidStack[] fluidOutputs) {
            RecipeTask task = new RecipeTask(duration, map, outputs, fluidOutputs);
            tasks.add(task);
            onTaskStarted(this, task);
            return task;
        }
    }

    /**
     * A single independent started recipe inside a thread.
     */
    public class RecipeTask {

        @Getter
        private final RecipeMap<?> recipeMap;
        @Getter
        private int progressTime = 0;
        @Getter
        private final int maxProgressTime;
        private long eut = 0;
        @Getter
        private int parallel = 1;
        @Setter
        @Getter
        private boolean awaitingOutput = false;
        @Setter
        @Getter
        private Object moduleData = null;
        @Getter
        private ItemStack[] outputItems;
        @Getter
        private FluidStack[] outputFluids;

        public RecipeTask(int duration, RecipeMap<?> recipeMap, ItemStack[] outputItems, FluidStack[] outputFluids) {
            this.maxProgressTime = Math.max(1, duration);
            this.recipeMap = recipeMap;
            this.outputItems = outputItems;
            this.outputFluids = outputFluids;
        }

        public long getEUt() {
            return eut;
        }

        public void setEUt(long eut) {
            this.eut = eut;
        }

        public void setParallel(int parallel) {
            this.parallel = Math.max(1, parallel);
        }

    }
}
