package com.MessTech.common.mixin.late;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.MessTech.common.machine.module.ISpaceElevatorModule;
import com.MessTech.common.machine.module.SpaceModuleMinerInfinity;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gtnhintergalactic.tile.multi.elevator.ElevatorUtil;
import gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;

/**
 * Late mixin that lets the vanilla GTNH Space Elevator mount MessTech's wireless infinity modules.
 * <p>
 * MessTech modules cannot extend {@link TileEntityModuleBase} because they already extend the
 * wireless multi-machine base. Instead, they implement {@link ISpaceElevatorModule}; this mixin keeps
 * them in a separate list on the elevator and mirrors the original module lifecycle (connect,
 * disconnect, module count, computation distribution).
 */
@Mixin(value = TileEntitySpaceElevator.class, remap = false)
public abstract class MixinTileEntitySpaceElevator {

    @Shadow
    public ArrayList<TileEntityModuleBase> mProjectModuleHatches;

    @Unique
    private ArrayList<ISpaceElevatorModule> mMessTechModuleHatches;

    @Unique
    private ArrayList<ISpaceElevatorModule> getMessTechModuleHatches() {
        if (mMessTechModuleHatches == null) {
            mMessTechModuleHatches = new ArrayList<>();
        }
        return mMessTechModuleHatches;
    }

    @Inject(method = "addProjectModuleToMachineList", at = @At("HEAD"), cancellable = true)
    private void MessTech$addProjectModuleToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null) {
            return;
        }
        IMetaTileEntity metaTileEntity = aTileEntity.getMetaTileEntity();
        if (metaTileEntity == null) {
            return;
        }
        if (metaTileEntity instanceof ISpaceElevatorModule && !(metaTileEntity instanceof TileEntityModuleBase)) {
            cir.setReturnValue(getMessTechModuleHatches().add((ISpaceElevatorModule) metaTileEntity));
        }
    }

    @Inject(method = "checkMachine", at = @At("HEAD"))
    private void MessTech$clearModuleList(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        java.util.List<StructureError> errors, CallbackInfo ci) {
        if (mMessTechModuleHatches != null) {
            mMessTechModuleHatches.clear();
        }
    }

    @Inject(method = "checkMachine", at = @At("RETURN"))
    private void MessTech$checkModuleCount(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
        java.util.List<StructureError> errors, CallbackInfo ci) {
        TileEntitySpaceElevator elevator = (TileEntitySpaceElevator) (Object) this;
        int originalCount = mProjectModuleHatches == null ? 0 : mProjectModuleHatches.size();
        int totalCount = originalCount + getMessTechModuleHatches().size();
        int slots = ElevatorUtil.getModuleSlotsUnlocked(elevator.getMotorTier());
        if (originalCount <= slots && totalCount > slots) {
            errors.add(StructureErrors.of("GT5U.gui.text.structure_error.spelv_module_exceed"));
        }
    }

    @Inject(method = "getNumberOfModules", at = @At("RETURN"), cancellable = true)
    private void MessTech$getNumberOfModules(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValueI() + getMessTechModuleHatches().size());
    }

    @Inject(method = "onRemoval", at = @At("HEAD"))
    private void MessTech$onRemoval(CallbackInfo ci) {
        if (mMessTechModuleHatches != null) {
            for (ISpaceElevatorModule module : mMessTechModuleHatches) {
                module.disconnect();
            }
        }
    }

    @Inject(method = "onPostTick", at = @At("RETURN"))
    private void MessTech$onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if (!aBaseMetaTileEntity.isServerSide()) {
            return;
        }
        TileEntitySpaceElevator elevator = (TileEntitySpaceElevator) (Object) this;
        if (aBaseMetaTileEntity.isAllowedToWork()) {
            for (ISpaceElevatorModule module : getMessTechModuleHatches()) {
                if (module.getNeededMotorTier() <= elevator.getMotorTier()) {
                    module.connect(elevator);
                } else {
                    module.disconnect();
                }
            }
        } else {
            for (ISpaceElevatorModule module : getMessTechModuleHatches()) {
                module.disconnect();
            }
        }
    }

    @Inject(method = "getAvailableDataForModules", at = @At("RETURN"), cancellable = true)
    private void MessTech$getAvailableDataForModules(CallbackInfoReturnable<Long> cir) {
        long originalMiners = 0;
        if (mProjectModuleHatches != null) {
            originalMiners = mProjectModuleHatches.stream()
                .filter(
                    module -> module instanceof TileEntityModuleMiner && module.getBaseMetaTileEntity() != null
                        && module.getBaseMetaTileEntity()
                            .isActive()
                        && module.isDataInputListEmpty())
                .count();
        }

        long messTechMiners = 0;
        for (ISpaceElevatorModule module : getMessTechModuleHatches()) {
            if (module instanceof SpaceModuleMinerInfinity && module instanceof IMetaTileEntity metaTileEntity
                && metaTileEntity.getBaseMetaTileEntity() != null
                && metaTileEntity.getBaseMetaTileEntity()
                    .isActive()
                && module.isDataInputListEmpty()) {
                messTechMiners++;
            }
        }

        long originalReturn = cir.getReturnValue();
        long fullData = originalMiners == 0 ? originalReturn : originalReturn * originalMiners;
        long totalMiners = originalMiners + messTechMiners;
        if (totalMiners == 0) {
            cir.setReturnValue(fullData);
        } else {
            cir.setReturnValue(fullData / totalMiners);
        }
    }
}
