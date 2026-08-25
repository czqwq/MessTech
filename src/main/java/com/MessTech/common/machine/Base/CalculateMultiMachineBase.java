package com.MessTech.common.machine.Base;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.MessTech.common.machine.hatch.MTHatchRack;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.IGTHatchAdder;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataInput;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataOutput;
import tectech.thing.metaTileEntity.hatch.MTEHatchObjectHolder;
import tectech.thing.metaTileEntity.hatch.MTEHatchUncertainty;
import tectech.thing.metaTileEntity.hatch.MTEHatchWirelessComputationOutput;

/**
 * Base class for MessTech computation multiblocks.
 * <p>
 * Adds the TecTech-style computation hatch lists and structure elements
 * (Uncertainty, InputData, OutputData, Rack) on top of {@link MTMultiMachineBase}.
 */
public abstract class CalculateMultiMachineBase<T extends CalculateMultiMachineBase<T>> extends MTMultiMachineBase<T> {

    public CalculateMultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public CalculateMultiMachineBase(String aName) {
        super(aName);
    }

    // region Computation hatches
    protected ArrayList<MTEHatchUncertainty> eUncertainHatches = new ArrayList<>();
    protected ArrayList<MTEHatchDataInput> eInputData = new ArrayList<>();
    protected ArrayList<MTEHatchDataOutput> eOutputData = new ArrayList<>();
    protected ArrayList<MTHatchRack> eRacks = new ArrayList<>();
    protected ArrayList<MTEHatchObjectHolder> eHolders = new ArrayList<>();
    protected ArrayList<MTEHatchWirelessComputationOutput> eWirelessComputationOutputs = new ArrayList<>();
    // endregion

    // region Computation state (mirrors TTMultiblockBase)
    protected long eRequiredData = 0;
    protected long eAvailableData = 0;
    protected int eComputationTimeout = 100;
    protected byte eCertainMode = 0;
    protected byte eCertainStatus = 0;
    public boolean ePowerPass = false;
    public boolean eSafeVoid = false;
    // endregion

    // region Hatch adders
    public boolean addUncertainToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHatchUncertainty hatch) {
            addIfSmartInput(hatch);
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(this.getMachineCraftingIcon());
            return eUncertainHatches.add(hatch);
        }
        return false;
    }

    public boolean addDataInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHatchDataInput hatch) {
            addIfSmartInput(hatch);
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(this.getMachineCraftingIcon());
            return eInputData.add(hatch);
        }
        return false;
    }

    public boolean addDataOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHatchDataOutput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(this.getMachineCraftingIcon());
            return eOutputData.add(hatch);
        }
        return false;
    }

    public boolean addRackToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTHatchRack rack) {
            rack.updateTexture(aBaseCasingIndex);
            rack.updateCraftingIcon(this.getMachineCraftingIcon());
            return eRacks.add(rack);
        }
        return false;
    }

    public boolean addHolderToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHatchObjectHolder holder) {
            addIfSmartInput(holder);
            holder.updateTexture(aBaseCasingIndex);
            holder.updateCraftingIcon(this.getMachineCraftingIcon());
            return eHolders.add(holder);
        }
        return false;
    }

    public boolean addWirelessComputationOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHatchWirelessComputationOutput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(this.getMachineCraftingIcon());
            return eWirelessComputationOutputs.add(hatch) && eOutputData.add(hatch);
        }
        return false;
    }
    // endregion

    // region Structure checks
    protected final void checkOneUncertaintyHatch(List<StructureError> errors) {
        if (eUncertainHatches.isEmpty()) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_uncertainty_hatch"));
        }
    }

    protected final void checkHasDataInput(List<StructureError> errors) {
        if (eInputData.isEmpty()) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_data_input_hatch"));
        }
    }

    protected final void checkHasDataOutput(List<StructureError> errors) {
        if (eOutputData.isEmpty()) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_data_output_hatch"));
        }
    }

    protected final void checkHasRack(List<StructureError> errors) {
        if (eRacks.isEmpty()) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_rack"));
        }
    }

    protected final void checkHasHolder(List<StructureError> errors) {
        if (eHolders.isEmpty()) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.missing_holder"));
        }
    }
    // endregion

    // region Hatch elements for structure definitions
    public enum HatchElement implements IHatchElement<CalculateMultiMachineBase<?>> {

        InputBus(translateToLocal("GT5U.MBTT.InputBus"), (t, te, i) -> t.addInputBusToMachineList(te, i),
            MTEHatchInputBus.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.mInputBusses.size();
            }
        },
        InputHatch(translateToLocal("GT5U.MBTT.InputHatch"), (t, te, i) -> t.addInputHatchToMachineList(te, i),
            MTEHatchInput.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.mInputHatches.size();
            }
        },
        OutputBus(translateToLocal("GT5U.MBTT.OutputBus"), (t, te, i) -> t.addOutputBusToMachineList(te, i),
            MTEHatchOutputBus.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.mOutputBusses.size();
            }
        },
        OutputHatch(translateToLocal("GT5U.MBTT.OutputHatch"), (t, te, i) -> t.addOutputHatchToMachineList(te, i),
            MTEHatchOutput.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.mOutputHatches.size();
            }
        },
        Energy(translateToLocal("GT5U.MBTT.EnergyHatch"), (t, te, i) -> t.addEnergyInputToMachineList(te, i),
            MTEHatchEnergy.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.mEnergyHatches.size();
            }
        },
        Dynamo(translateToLocal("GT5U.MBTT.DynamoHatch"), (t, te, i) -> t.addDynamoToMachineList(te, i),
            MTEHatchDynamo.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.mDynamoHatches.size();
            }
        },
        ExoticEnergy(translateToLocal("GT5U.MBTT.ExoticEnergyHatch"),
            (t, te, i) -> t.addExoticEnergyInputToMachineList(te, i), MTEHatch.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.getExoticEnergyHatches()
                    .size();
            }
        },
        ExoticDynamo(translateToLocal("GT5U.MBTT.ExoticEnergyDynamo"),
            (t, te, i) -> t.addExoticDynamoToMachineList(te, i), MTEHatch.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.getExoticDynamoHatches()
                    .size();
            }
        },
        Uncertainty(translateToLocal("GT5U.MBTT.Uncertainty"), (t, te, i) -> t.addUncertainToMachineList(te, i),
            MTEHatchUncertainty.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.eUncertainHatches.size();
            }
        },
        InputData(translateToLocal("GT5U.MBTT.InputData"), (t, te, i) -> t.addDataInputToMachineList(te, i),
            MTEHatchDataInput.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.eInputData.size();
            }
        },
        OutputData(translateToLocal("GT5U.MBTT.OutputData"), (t, te, i) -> t.addDataOutputToMachineList(te, i),
            MTEHatchDataOutput.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.eOutputData.size();
            }
        },
        Rack(translateToLocal("GT5U.MBTT.Rack"), (t, te, i) -> t.addRackToMachineList(te, i), MTHatchRack.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.eRacks.size();
            }
        },
        Holder(translateToLocal("GT5U.MBTT.Holder"), (t, te, i) -> t.addHolderToMachineList(te, i),
            MTEHatchObjectHolder.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.eHolders.size();
            }
        },
        WirelessComputationOutput(translateToLocal("GT5U.MBTT.WirelessComputationOutput"),
            (t, te, i) -> t.addWirelessComputationOutputToMachineList(te, i), MTEHatchWirelessComputationOutput.class) {

            @Override
            public long count(CalculateMultiMachineBase<?> t) {
                return t.eWirelessComputationOutputs.size();
            }
        };

        private final String langKey;
        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<CalculateMultiMachineBase<?>> adder;

        @SafeVarargs
        HatchElement(String langKey, IGTHatchAdder<CalculateMultiMachineBase<?>> adder,
            Class<? extends IMetaTileEntity>... mteClasses) {
            this.langKey = langKey;
            this.adder = adder;
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super CalculateMultiMachineBase<?>> adder() {
            return adder;
        }

        @Override
        public String getDisplayName() {
            return langKey;
        }

        @Override
        public String getDescriptionLangKey() {
            return langKey;
        }

        @Override
        public long count(CalculateMultiMachineBase<?> t) {
            return 0;
        }
    }
    // endregion
}
