package com.MessTech.common.machine.Base;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.MessTech.common.machine.hatch.MTModuleHatchBase;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.IGTHatchAdder;

/**
 * Structure element for the module hatches of a {@link MTModuleMultiMachineBase}.
 * <p>
 * A machine opts into modules with one line in its structure definition:
 *
 * <pre>
 * buildHatchAdder(MyMachine.class).casingIndex(casing)
 *     .hint(1)
 *     .atLeast(MTModuleHatchElement.Module)
 *     .build()
 * </pre>
 *
 * The adder is {@link MTModuleMultiMachineBase#addModuleHatchToMachineList}, so the scan links the hatch and refuses
 * it when the machine does not take that module type (or already has a parallel module).
 */
public enum MTModuleHatchElement implements IHatchElement<MTModuleMultiMachineBase<?>> {

    Module(MTModuleMultiMachineBase::addModuleHatchToMachineList, MTModuleHatchBase.class);

    private final List<Class<? extends IMetaTileEntity>> mteClasses;
    private final IGTHatchAdder<MTModuleMultiMachineBase<?>> adder;

    @SafeVarargs
    MTModuleHatchElement(IGTHatchAdder<MTModuleMultiMachineBase<?>> adder,
        Class<? extends IMetaTileEntity>... mteClasses) {
        this.adder = adder;
        this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
    }

    @Override
    public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
        return mteClasses;
    }

    @Override
    public IGTHatchAdder<? super MTModuleMultiMachineBase<?>> adder() {
        return adder;
    }

    @Override
    public long count(MTModuleMultiMachineBase<?> machine) {
        return machine.getModules()
            .size();
    }
}
