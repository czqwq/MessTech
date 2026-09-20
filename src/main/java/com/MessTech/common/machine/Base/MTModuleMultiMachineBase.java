package com.MessTech.common.machine.Base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;

/**
 * Base class for multiblocks that take {@link IMTModule modules}.
 * <p>
 * The machine owns the module list and folds the module values into the three knobs it already has: EU modifier,
 * speed bonus and parallel. Subclasses put their own values in {@link #getBaseEuModifier()},
 * {@link #getBaseSpeedBonus()} and {@link #getBaseMaxParallelRecipes()}; the aggregating methods themselves are
 * final, so a module can never be skipped by accident. The EU and speed modules multiply the value of the machine, a
 * parallel control module replaces it (see {@link #getMaxParallelRecipes()}).
 * <p>
 * The module list is rebuilt on every structure check: {@link #checkMachine} drops the old modules first and
 * subclasses register the current ones in {@link #checkMachineStructure}, usually from
 * {@link #addModuleHatchToMachineList}. A machine that lost a module hatch therefore loses its bonus at the next
 * check.
 * <p>
 * {@link MTModuleType#CROSS_RECIPE_PARALLEL} and {@link MTModuleType#WIRELESS} are not part of the default set, a
 * machine has to opt in; this class only reports what the modules provide ({@link #getModuleCycleNum()},
 * {@link #hasWirelessModule()}), the processing loop that uses it stays in the machine.
 */
public abstract class MTModuleMultiMachineBase<T extends MTModuleMultiMachineBase<T>> extends MTMultiMachineBase<T> {

    public MTModuleMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTModuleMultiMachineBase(String aName) {
        super(aName);
    }

    // region Module registry

    protected final List<IMTModule> modules = new ArrayList<>();

    /**
     * The module types this machine takes. The default is the standard set
     * ({@link MTModuleType#defaultSupported()}); a machine that can also use the cross recipe parallel or the
     * wireless module opts in by overriding this.
     *
     * @return The module types this machine takes.
     */
    public Set<MTModuleType> getSupportedModuleTypes() {
        return MTModuleType.defaultSupported();
    }

    /**
     * @param type The module type to test.
     * @return If true this machine takes that module type.
     */
    public boolean supportsModuleType(MTModuleType type) {
        return getSupportedModuleTypes().contains(type);
    }

    /**
     * A module is only taken when the machine supports every type it provides and its tier is IV .. MAX.
     *
     * @param module The module to test.
     * @return If true this machine takes that module.
     */
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
     * Link a module to this machine, normally called while the structure is checked.
     * <p>
     * Only one {@link MTModuleType#PARALLEL_CONTROL} module is taken, because such a module supplies the parallel of
     * the machine instead of adding to it: a second parallel controller is a structure error, the way TST's
     * {@code checkSingleModularHatch} rejects a second modular hatch.
     *
     * @param module The module to link.
     * @return If true the module is linked now, false when it is not taken or was already linked.
     */
    public boolean addModule(IMTModule module) {
        if (!acceptsModule(module) || modules.contains(module)) return false;
        if (module.provides(MTModuleType.PARALLEL_CONTROL) && hasModule(MTModuleType.PARALLEL_CONTROL)) return false;
        return modules.add(module);
    }

    /**
     * Hatch adder for {@link MTModuleHatchElement}, i.e. the structure scan entry point that links a module hatch to
     * this machine.
     *
     * @param aTileEntity      The hatch that was found.
     * @param aBaseCasingIndex The casing texture index of the structure.
     * @return If true the hatch is a module this machine takes and the structure stays valid.
     */
    public boolean addModuleHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof IMTModule module) || !addModule(module)) return false;
        module.onLinkedToMachine(aBaseCasingIndex);
        return true;
    }

    /**
     * @return Every linked module, in the order they were added.
     */
    public List<IMTModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    /**
     * @param type The module type to look for.
     * @return Every linked module that provides that type.
     */
    public List<IMTModule> getModules(MTModuleType type) {
        List<IMTModule> found = new ArrayList<>();
        for (IMTModule module : modules) {
            if (module.provides(type)) found.add(module);
        }
        return found;
    }

    /**
     * @param type The module type to look for.
     * @return If true at least one linked module provides that type.
     */
    public boolean hasModule(MTModuleType type) {
        for (IMTModule module : modules) {
            if (module.provides(type)) return true;
        }
        return false;
    }

    /**
     * Drop every linked module. Called at the start of every structure check.
     */
    public void clearModules() {
        modules.clear();
    }

    // endregion

    // region Module contributions

    /**
     * @return The EU modifier of every linked module, multiplied.
     */
    protected float getModuleEuModifier() {
        float modifier = 1.0F;
        for (IMTModule module : modules) modifier *= module.getEuModifier();
        return modifier;
    }

    /**
     * @return The speed bonus of every linked module, multiplied.
     */
    protected float getModuleSpeedBonus() {
        float bonus = 1.0F;
        for (IMTModule module : modules) bonus *= module.getSpeedBonus();
        return bonus;
    }

    /**
     * The parallel of every linked parallel control module, taking the highest one.
     * <p>
     * Such a module supplies the parallel of the machine rather than adding to it, so several of them would be
     * ambiguous; {@link #addModule} only accepts the first one.
     *
     * @return The parallel the linked modules supply, 0 when there is none.
     */
    protected int getModuleParallel() {
        int parallel = 0;
        for (IMTModule module : modules) {
            if (!module.provides(MTModuleType.PARALLEL_CONTROL)) continue;
            if (module.getParallel() > parallel) parallel = module.getParallel();
        }
        return parallel;
    }

    /**
     * @return The highest cycle count of the linked modules, 1 when there is none.
     */
    protected int getModuleCycleNum() {
        int cycles = 1;
        for (IMTModule module : modules) {
            if (module.getCycleNum() > cycles) cycles = module.getCycleNum();
        }
        return cycles;
    }

    /**
     * @return If true at least one linked module provides wireless operation.
     */
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
     * A parallel control module replaces the parallel of the machine: the machine runs on
     * {@link IMTModule#getParallel()} of that module and falls back to {@link #getBaseMaxParallelRecipes()} only when
     * it has none.
     */
    @Override
    public final int getMaxParallelRecipes() {
        int supplied = getModuleParallel();
        return supplied > 0 ? supplied : getBaseMaxParallelRecipes();
    }

    /**
     * @return The EU modifier of the machine itself, without its modules. Defaults to 1.
     */
    protected float getBaseEuModifier() {
        return 1.0F;
    }

    /**
     * @return The speed bonus of the machine itself, without its modules. Defaults to 1.
     */
    protected float getBaseSpeedBonus() {
        return 1.0F;
    }

    /**
     * @return The parallel of the machine itself, without its modules. Defaults to 1.
     */
    protected int getBaseMaxParallelRecipes() {
        return 1;
    }

    // endregion

    // region Structure

    /**
     * Structure check entry. The modules of the previous check are dropped here and the structure scan of the
     * subclass registers the current ones, so subclasses implement {@link #checkMachineStructure} instead of this
     * method.
     */
    @Override
    public final void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        List<StructureError> errors) {
        clearModules();
        checkMachineStructure(aBaseMetaTileEntity, aStack, errors);
    }

    /**
     * Check the structure and register the modules, see {@link #checkMachine}.
     *
     * @param aBaseMetaTileEntity The base tile entity of this machine.
     * @param aStack              The stack that triggered the check.
     * @param errors              Collected structure errors.
     */
    protected abstract void checkMachineStructure(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        List<StructureError> errors);

    // endregion
}
