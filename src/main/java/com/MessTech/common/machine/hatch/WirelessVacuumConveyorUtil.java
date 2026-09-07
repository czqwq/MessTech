package com.MessTech.common.machine.hatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryElement;
import gregtech.common.tileentities.machines.multi.nanochip.factory.VacuumFactoryGrid;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyor;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyorInput;
import gregtech.common.tileentities.machines.multi.nanochip.hatches.MTEHatchVacuumConveyorOutput;

/**
 * Helpers shared by the wireless NAC vacuum conveyor input/output hatches.
 * <p>
 * The grid's own {@code vertices} set only contains elements that already have an edge, so isolated
 * wireless hatches are tracked in a dedicated registry here. Without this, two newly placed wireless
 * hatches could never discover each other.
 */
public final class WirelessVacuumConveyorUtil {

    private static final Set<IWirelessVacuumConveyor> REGISTRY = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private WirelessVacuumConveyorUtil() {}

    public static void register(IWirelessVacuumConveyor hatch) {
        REGISTRY.add(hatch);
    }

    public static void unregister(IWirelessVacuumConveyor hatch) {
        REGISTRY.remove(hatch);
    }

    public static String getKey(IWirelessVacuumConveyor hatch) {
        String scope = hatch.getOwnerUuid() == null ? "public"
            : hatch.getOwnerUuid()
                .toString();
        byte color = hatch instanceof MTEHatchVacuumConveyor h ? h.getColorization() : -1;
        return scope + ":" + color + ":" + (hatch.getFrequency() == null ? "" : hatch.getFrequency());
    }

    private static boolean isValid(IWirelessVacuumConveyor hatch) {
        if (!(hatch instanceof MTEHatchVacuumConveyor conveyor)) return false;
        IGregTechTileEntity base = conveyor.getBaseMetaTileEntity();
        return base != null && !base.isDead() && base.getMetaTileEntity() == conveyor;
    }

    /**
     * Adds every registered wireless vacuum conveyor element that uses the same colour, frequency and
     * privacy scope. Colour is required because the NAC module routing still identifies item flows by
     * hatch colour (input-hatch colour decides the module output colour).
     */
    public static void collectWirelessNeighbours(VacuumFactoryElement self, Collection<VacuumFactoryElement> out) {
        if (!(self instanceof IWirelessVacuumConveyor wirelessSelf)) return;

        MTEHatchVacuumConveyor selfHatch = (MTEHatchVacuumConveyor) self;
        if (selfHatch.getColorization() == -1) return;

        String key = getKey(wirelessSelf);
        for (IWirelessVacuumConveyor element : REGISTRY) {
            if (element == self) continue;
            if (!isValid(element)) continue;
            if (element instanceof VacuumFactoryElement factoryElement && getKey(element).equals(key)) {
                out.add(factoryElement);
            }
        }
    }

    /**
     * Directly finds all matching wireless output hatches for a wireless input. This is used by the
     * input's movement tick as a more reliable path than depending on factory-grid edge formation.
     */
    public static List<MTEHatchVacuumConveyorOutput> findMatchingOutputs(IWirelessVacuumConveyor self) {
        List<MTEHatchVacuumConveyorOutput> result = new ArrayList<>();
        if (!(self instanceof MTEHatchVacuumConveyor selfHatch) || selfHatch.getColorization() == -1) return result;

        String key = getKey(self);
        for (IWirelessVacuumConveyor element : REGISTRY) {
            if (element == self) continue;
            if (!isValid(element)) continue;
            if (!(element instanceof MTEHatchVacuumConveyorOutput output)) continue;
            if (output.getColorization() == -1) continue;
            if (getKey(element).equals(key)) {
                result.add(output);
            }
        }
        return result;
    }

    /**
     * Directly finds all matching wireless input hatches for a wireless output. Used as an extra
     * output-push fallback so transfer does not depend solely on the input-side movement tick.
     */
    public static List<MTEHatchVacuumConveyorInput> findMatchingInputs(IWirelessVacuumConveyor self) {
        List<MTEHatchVacuumConveyorInput> result = new ArrayList<>();
        if (!(self instanceof MTEHatchVacuumConveyor selfHatch) || selfHatch.getColorization() == -1) return result;

        String key = getKey(self);
        for (IWirelessVacuumConveyor element : REGISTRY) {
            if (element == self) continue;
            if (!isValid(element)) continue;
            if (!(element instanceof MTEHatchVacuumConveyorInput input)) continue;
            if (input.getColorization() == -1) continue;
            if (getKey(element).equals(key)) {
                result.add(input);
            }
        }
        return result;
    }

    /** Rebuilds the vacuum factory grid after frequency/private settings change. */
    public static void onWirelessSettingsChanged(MTEHatchVacuumConveyor hatch) {
        VacuumFactoryGrid.INSTANCE.updateElement(hatch);
    }
}
