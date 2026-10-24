package com.MessTech.common.machine.Base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import com.MessTech.common.gui.base.MTModuleMultiMachineBaseGui;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * Base class for multiblocks that take {@link IMTModule modules}: module values fold into the machine's EU modifier,
 * speed bonus and parallel. Subclasses supply their own values in {@link #getBaseEuModifier()},
 * {@link #getBaseSpeedBonus()} and {@link #getBaseMaxParallelRecipes()}; the aggregating methods are final. EU and
 * speed modules multiply the machine's value, a parallel control module replaces it
 * ({@link #getMaxParallelRecipes()}).
 * <p>
 * The list is rebuilt on every structure check ({@link #checkMachine} then {@link #checkMachineStructure}, usually via
 * {@link #addModuleHatchToMachineList}), so losing a hatch loses the bonus. {@link MTModuleType#CROSS_RECIPE_PARALLEL}
 * and {@link MTModuleType#WIRELESS} are not default (a machine opts in); this class only reports them.
 */
public abstract class MTModuleMultiMachineBase<T extends MTModuleMultiMachineBase<T>> extends MTMultiMachineBase<T> {

    public MTModuleMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTModuleMultiMachineBase(String aName) {
        super(aName);
    }

    /** Module-aware GUI: {@link MTModuleMultiMachineBaseGui} adds the parallel field of the parallel control module. */
    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new MTModuleMultiMachineBaseGui(this);
    }

    // region Module registry

    protected final List<IMTModule> modules = new ArrayList<>();

    /** @return Module types this machine takes; default {@link MTModuleType#defaultSupported()}, override to opt in. */
    public Set<MTModuleType> getSupportedModuleTypes() {
        return MTModuleType.defaultSupported();
    }

    public boolean supportsModuleType(MTModuleType type) {
        return getSupportedModuleTypes().contains(type);
    }

    /** @return If true the machine takes that module: it supports every provided type and the tier is IV .. MAX. */
    public boolean acceptsModule(IMTModule module) {
        if (module == null || !IMTModule.isValidTier(module.getModuleTier())) return false;
        Set<MTModuleType> provided = module.getModuleTypes();
        if (provided == null || provided.isEmpty()) return false;
        for (MTModuleType type : provided) {
            if (!supportsModuleType(type)) return false;
        }
        return true;
    }

    /**
     * Link a module, normally while the structure is checked. Only one {@link MTModuleType#PARALLEL_CONTROL} module is
     * taken (it supplies the parallel instead of adding to it), like TST's {@code checkSingleModularHatch}.
     *
     * @return If true the module is linked now, false when it is not taken or already linked.
     */
    public boolean addModule(IMTModule module) {
        if (!acceptsModule(module) || modules.contains(module)) return false;
        if (module.provides(MTModuleType.PARALLEL_CONTROL) && hasModule(MTModuleType.PARALLEL_CONTROL)) return false;
        return modules.add(module);
    }

    /**
     * @return Hatch adder for {@link MTModuleHatchElement}: links a module hatch found by the structure scan, if the
     *         machine takes it.
     */
    public boolean addModuleHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof IMTModule module) || !addModule(module)) return false;
        module.onLinkedToMachine(aBaseCasingIndex);
        return true;
    }

    public List<IMTModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<IMTModule> getModules(MTModuleType type) {
        List<IMTModule> found = new ArrayList<>();
        for (IMTModule module : modules) {
            if (module.provides(type)) found.add(module);
        }
        return found;
    }

    public boolean hasModule(MTModuleType type) {
        for (IMTModule module : modules) {
            if (module.provides(type)) return true;
        }
        return false;
    }

    /** Drop every linked module. Called at the start of every structure check. */
    public void clearModules() {
        modules.clear();
    }

    // endregion

    // region Module contributions

    /** @return The EU modifier of the linked modules, multiplied. */
    protected float getModuleEuModifier() {
        float modifier = 1.0F;
        for (IMTModule module : modules) modifier *= module.getEuModifier();
        return modifier;
    }

    /** @return The speed bonus of the linked modules, multiplied. */
    protected float getModuleSpeedBonus() {
        float bonus = 1.0F;
        for (IMTModule module : modules) bonus *= module.getSpeedBonus();
        return bonus;
    }

    /**
     * @return The parallel of the linked parallel control module (the highest one), 0 when there is none; such a module
     *         supplies the parallel rather than adding to it.
     */
    protected int getModuleParallel() {
        int parallel = 0;
        for (IMTModule module : modules) {
            if (!module.provides(MTModuleType.PARALLEL_CONTROL)) continue;
            if (module.getParallel() > parallel) parallel = module.getParallel();
        }
        return parallel;
    }

    protected int getModuleCycleNum() {
        int cycles = 1;
        for (IMTModule module : modules) {
            if (module.getCycleNum() > cycles) cycles = module.getCycleNum();
        }
        return cycles;
    }

    protected boolean hasWirelessModule() {
        for (IMTModule module : modules) {
            if (module.providesWireless()) return true;
        }
        return false;
    }

    // endregion

    // region MTMultiMachineBase hooks

    @Override
    protected final float getEuModifier() {
        return getBaseEuModifier() * getModuleEuModifier();
    }

    @Override
    protected final float getSpeedBonus() {
        return getBaseSpeedBonus() * getModuleSpeedBonus();
    }

    /**
     * A parallel control module supplies the parallel ({@link IMTModule#getParallel()}), else
     * {@link #getBaseMaxParallelRecipes()}; with {@link #isModuleParallelAdditive()} it is added on top instead.
     */
    @Override
    public final int getMaxParallelRecipes() {
        int supplied = getModuleParallel();
        if (supplied <= 0) return getBaseMaxParallelRecipes();
        if (!isModuleParallelAdditive()) return supplied;
        return (int) Math.min(Integer.MAX_VALUE, (long) getBaseMaxParallelRecipes() + supplied);
    }

    /** @return If true a parallel control module adds to the base parallel instead of replacing it. */
    protected boolean isModuleParallelAdditive() {
        return false;
    }

    protected float getBaseEuModifier() {
        return 1.0F;
    }

    protected float getBaseSpeedBonus() {
        return 1.0F;
    }

    protected int getBaseMaxParallelRecipes() {
        return 1;
    }

    // endregion

    // region Module GUI

    /** @return The linked parallel control module, {@code null} when none ({@link #addModule} takes only one). */
    private IMTModule parallelModule() {
        for (IMTModule module : modules) {
            if (module.provides(MTModuleType.PARALLEL_CONTROL)) return module;
        }
        return null;
    }

    /**
     * @return The GUI's parallel, i.e. that of the parallel control module (= {@link #getMaxParallelRecipes()}), 1
     *         without one. Client safe, so 1 until the client received the module values.
     */
    public int getParallelForGui() {
        IMTModule module = parallelModule();
        return module == null ? 1 : Math.max(1, module.getParallel());
    }

    public int getParallelCeilingForGui() {
        IMTModule module = parallelModule();
        return module == null ? 1 : Math.max(1, module.getMaxParallel());
    }

    /** Sets the GUI parallel of the linked parallel control module (clamped by it); no-op without one. */
    public void setParallelForGui(int value) {
        IMTModule module = parallelModule();
        if (module != null) module.setParallelFromGui(value);
    }

    // endregion

    // region Waila

    /** Writes the module values into the Waila tag: the structure scan that links modules only runs on the server. */
    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setBoolean("hasSpeedModule", hasModule(MTModuleType.SPEED_BONUS));
        tag.setBoolean("hasEuModule", hasModule(MTModuleType.EU_DISCOUNT));
        tag.setBoolean("hasParallelModule", hasModule(MTModuleType.PARALLEL_CONTROL));
        tag.setFloat("moduleSpeedBonus", getModuleSpeedBonus());
        tag.setFloat("moduleEuModifier", getModuleEuModifier());
        tag.setInteger("moduleParallel", getMaxParallelRecipes());
    }

    /**
     * The module lines of the Waila body, in module tooltip order (duration reduction, EU discount, parallel), each
     * only while such a module is linked. Numbers use the {@code MTModuleValues} tooltip format, colours live in the
     * lang files.
     */
    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();

        if (tag.getBoolean("hasSpeedModule")) {
            currentTip.add(
                StatCollector.translateToLocalFormatted(
                    "machine.module.waila.speed",
                    MTModuleValues.speedBonusText(tag.getFloat("moduleSpeedBonus"))));
        }
        if (tag.getBoolean("hasEuModule")) {
            currentTip.add(
                StatCollector.translateToLocalFormatted(
                    "machine.module.waila.eu",
                    MTModuleValues.euModifierText(tag.getFloat("moduleEuModifier"))));
        }
        if (tag.getBoolean("hasParallelModule")) {
            currentTip.add(
                StatCollector.translateToLocalFormatted(
                    "machine.module.waila.parallel",
                    NumberFormatUtil.formatNumber(tag.getInteger("moduleParallel"))));
        }
    }

    // endregion

    // region Structure

    /** Structure check entry: drops the previous modules, then runs {@link #checkMachineStructure}. */
    @Override
    public final void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        List<StructureError> errors) {
        clearModules();
        checkMachineStructure(aBaseMetaTileEntity, aStack, errors);
    }

    /**
     * Check the structure and register the modules, see {@link #checkMachine}.
     *
     * @param errors Collected structure errors.
     */
    protected abstract void checkMachineStructure(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        List<StructureError> errors);

    // endregion
}
