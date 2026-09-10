package com.MessTech.common.recipe;

import static com.MessTech.common.recipe.MTRecipeMaps.Steel_brick_Recipes;
import static goodgenerator.loader.Loaders.compactFusionCoil;
import static gregtech.api.casing.Casings.NanochipFirewallProjectionCasing;
import static gregtech.api.casing.Casings.NanochipMeshInterfaceCasing;
import static gregtech.api.enums.TierEU.RECIPE_IV;
import static gregtech.api.enums.TierEU.RECIPE_LV;
import static gregtech.api.enums.TierEU.RECIPE_MAX;
import static gregtech.api.enums.TierEU.RECIPE_UEV;
import static gregtech.api.enums.TierEU.RECIPE_UHV;
import static gregtech.api.enums.TierEU.RECIPE_UIV;
import static gregtech.api.enums.TierEU.RECIPE_UV;
import static gregtech.api.enums.TierEU.RECIPE_ZPM;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.compressorRecipes;
import static gregtech.api.util.GTRecipeBuilder.HOURS;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.STACKS;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;
import static tectech.thing.CustomItemList.DATApipe;
import static tectech.thing.CustomItemList.Machine_Multi_Computer;
import static tectech.thing.CustomItemList.Machine_Multi_DataBank;
import static tectech.thing.CustomItemList.UncertaintyX_Hatch;
import static tectech.thing.CustomItemList.rack_Hatch;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.item.MTNACComponentItems;
import com.MessTech.common.misc.MTItemList;

import goodgenerator.items.GGMaterial;
import goodgenerator.loader.Loaders;
import gregtech.api.casing.Casings;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;
import gtPlusPlus.core.material.MaterialMisc;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;
import gtPlusPlus.xmod.thermalfoundation.fluid.TFFluids;
import tectech.recipe.TTRecipeAdder;
import tectech.thing.CustomItemList;

public class GTRecipes {

    public static void loadRecipes() {
        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, GTOreDictUnificator.get(OrePrefixes.wireGt12, Materials.SuperconductorLuV, 1))
            .metadata(SCANNING, new Scanning(10 * MINUTES, RECIPE_ZPM))
            .itemInputs(
                new ItemStack(compactFusionCoil, 64, 1),
                new ItemStack(Loaders.LFC[0].getItem(), 64, 32019),
                GTOreDictUnificator.get(OrePrefixes.plateDense, GGMaterial.marCeM200.getGTMaterial(), 64),
                ItemList.Field_Generator_LuV.get(64),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.NaquadahAlloy, 64),
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 16 },
                GTOreDictUnificator.get(OrePrefixes.stickLong, GGMaterial.marCeM200.getGTMaterial(), 64),
                ItemList.Circuit_Wafer_UHPIC.get(64),
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.SuperconductorLuV, 64))
            .fluidInputs(
                (MaterialsAlloy.INDALLOY_140.getFluidStack(144 * 1024)),
                (Materials.VanadiumGallium.getMolten(9216)))
            .itemOutputs(MTItemList.MTDTPF.get(1))
            .eut(RECIPE_UV)
            .duration(600 * 20)
            .addTo(AssemblyLine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(NanochipMeshInterfaceCasing.getItem(), 8, 1),
                UncertaintyX_Hatch.get(64),
                Machine_Multi_Computer.get(8),
                Machine_Multi_DataBank.get(1),
                DATApipe.get(64),
                new Object[] { OrePrefixes.circuit.get(Materials.UIV), 4 },
                new Object[] { OrePrefixes.circuit.get(Materials.UEV), 64 })
            .fluidInputs(new FluidStack(TFFluids.fluidCryotheum, 1000000))
            .itemOutputs(MTItemList.MTComputingCenter.get(1))
            .eut(RECIPE_UEV)
            .duration(128 * 20)
            .requiresCleanRoom()
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                rack_Hatch.get(1),
                new ItemStack(NanochipFirewallProjectionCasing.getItem(), 1, 4),
                new Object[] { OrePrefixes.circuit.get(Materials.UEV), 2 })
            .itemOutputs(MTItemList.MTHatchRack.get(1))
            .eut(RECIPE_UHV)
            .duration(32 * 20)
            .requiresCleanRoom()
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, new ItemStack(Loaders.CompAssline.getItem(), 1, 32026))
            .metadata(SCANNING, new Scanning(6 * HOURS, RECIPE_UHV))
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.nanite, Materials.Neutronium, 8),
                // ItemList.Casing_Assembler.get(64),
                new ItemStack(Casings.AssemblerMachineCasing.getItem(), 64, 9),
                new ItemStack(Casings.AssemblyLineCasing.getItem(), 64, 5),
                ItemList.Robot_Arm_UHV.get(64),
                ItemList.Conveyor_Module_UHV.get(64),
                ItemList.Electric_Pump_UHV.get(64),
                ItemList.Sensor_UHV.get(64),
                ItemList.Emitter_UHV.get(64),
                ItemList.Field_Generator_UHV.get(4),
                GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Infinity, 8),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.Infinity, 8),
                GTOreDictUnificator.get(OrePrefixes.screw, Materials.Infinity, 8),
                new Object[] { OrePrefixes.circuit.get(Materials.UEV), 16 },
                new Object[] { OrePrefixes.circuit.get(Materials.UHV), 32 },
                new Object[] { OrePrefixes.circuit.get(Materials.UV), 64 },
                GregtechItemList.Laser_Lens_Special.get(1))
            .fluidInputs(
                MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(STACKS * INGOTS),
                MaterialsAlloy.INDALLOY_140.getFluidStack(144 * 1024),
                MaterialsAlloy.PIKYONIUM.getFluidStack(144 * 512))
            .itemOutputs(MTItemList.MTAssFactory.get(1))
            .eut(RECIPE_UHV)
            .duration(20 * 600)
            .addTo(AssemblyLine);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Casing_SolidSteel.get(1),
                ItemList.Electric_Motor_UHV.get(16),
                ItemList.Electric_Piston_UHV.get(8),
                ItemList.Conveyor_Module_UHV.get(8),
                ItemList.Robot_Arm_UHV.get(4),
                ItemList.Electric_Pump_UHV.get(4),
                ItemList.Field_Generator_UV.get(8),
                GTOreDictUnificator.get(OrePrefixes.gearGt, GGMaterial.marCeM200.getGTMaterial(), 64),
                new Object[] { OrePrefixes.circuit.get(Materials.UHV), 8 })
            .fluidInputs(MaterialsAlloy.INDALLOY_140.getFluidStack(STACKS))
            .itemOutputs(MTItemList.AssMatrixBlock.get(1))
            .eut(RECIPE_UHV)
            .duration(MINUTES * 4)
            .addTo(assemblerRecipes);

        TTRecipeAdder.addResearchableAssemblylineRecipe(
            MTItemList.AssMatrixBlock.get(1),
            16_777_216 * 2,
            16384,
            (int) TierEU.RECIPE_UMV,
            1,
            new Object[] { MTItemList.AssMatrixBlock.get(1), ItemList.Electric_Motor_UMV.get(4),
                ItemList.Electric_Piston_UMV.get(4), ItemList.Robot_Arm_UMV.get(4), ItemList.Electric_Pump_UMV.get(4),
                ItemList.Conveyor_Module_UMV.get(4), ItemList.Field_Generator_UMV.get(1),
                new Object[] { OrePrefixes.circuit.get(Materials.UMV), 4 },
                GTOreDictUnificator.get(OrePrefixes.spring, Materials.SpaceTime, 4),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.SpaceTime, 2),
                GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.SpaceTime, 1) },
            new FluidStack[] { MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(STACKS * INGOTS),
                MaterialsAlloy.INDALLOY_140.getFluidStack(144 * 1024),
                MaterialsAlloy.PIKYONIUM.getFluidStack(144 * 512) },
            MTItemList.AdvAssMatrixBlock.get(1),
            20 * 600,
            (int) TierEU.RECIPE_UMV);

        GTValues.RA.stdBuilder()
            .itemInputs(
                CustomItemList.eM_Containment.get(1),
                ItemList.Field_Generator_ZPM.get(2),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Naquadria, 6),
                GTOreDictUnificator.get(OrePrefixes.foil, Materials.Europium, 12),
                MaterialsAlloy.PIKYONIUM.getScrew(24),
                MaterialsAlloy.PIKYONIUM.getRing(24))
            .fluidInputs(Materials.NaquadahAlloy.getMolten(INGOTS * 64))
            .itemOutputs(CustomItemList.eM_Containment_Advanced.get(1))
            .eut(RECIPE_UV)
            .duration(MINUTES)
            .requiresCleanRoom()
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                CustomItemList.eM_Containment_Advanced.get(1),
                ItemList.Field_Generator_UV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Neutronium, 6),
                GTOreDictUnificator.get(OrePrefixes.foil, Materials.Americium, 12),
                GTOreDictUnificator.get(OrePrefixes.screw, Materials.Naquadria, 24),
                GTOreDictUnificator.get(OrePrefixes.ring, Materials.Naquadria, 24))
            .fluidInputs(Materials.Naquadria.getMolten(INGOTS * 64))
            .itemOutputs(CustomItemList.eM_Ultimate_Containment.get(1))
            .eut(RECIPE_UHV)
            .duration(MINUTES * 2)
            .requiresCleanRoom()
            .addTo(assemblerRecipes);

        TTRecipeAdder.addResearchableAssemblylineRecipe(
            ItemList.SpaceElevatorModuleMinerT3.get(1),
            16_777_216 * 2,
            16384,
            (int) RECIPE_MAX,
            1,
            new Object[] { ItemList.SpaceElevatorModuleMinerT3.get(64), ItemList.InfiniteFluidDrillingRig.get(64),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Universium, 16),
                ItemList.Electric_Pump_UXV.get(64), ItemList.Field_Generator_UXV.get(16),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.Universium, 16),
                GTOreDictUnificator.get(OrePrefixes.screw, Materials.Universium, 64),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Eternity, 64),
                new Object[] { OrePrefixes.circuit.get(Materials.MAX), 64 } },
            new FluidStack[] { MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(STACKS * INGOTS),
                Materials.Universium.getMolten(144 * 2048), Materials.Eternity.getMolten(144 * 4096) },
            MTItemList.SpaceModuleMinerInfinity.get(1),
            20 * 6000,
            (int) RECIPE_MAX);

        TTRecipeAdder.addResearchableAssemblylineRecipe(
            ItemList.SpaceElevatorModulePumpT3.get(1),
            16_777_216 * 2,
            16384,
            (int) RECIPE_MAX,
            1,
            new Object[] { ItemList.SpaceElevatorModulePumpT3.get(64), ItemList.Sensor_UXV.get(64),
                ItemList.Field_Generator_UXV.get(64), new Object[] { OrePrefixes.circuit.get(Materials.MAX), 16 },
                ItemList.Robot_Arm_UXV.get(64), ItemList.Robot_Arm_UXV.get(64),
                GTOreDictUnificator.get(OrePrefixes.round, Materials.Universium, 64),
                GTOreDictUnificator.get(OrePrefixes.wireGt04, Materials.SpaceTime, 32),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Universium, 16) },
            new FluidStack[] { MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(STACKS * INGOTS),
                Materials.Universium.getMolten(144 * 1024), Materials.Eternity.getMolten(144 * 2048),
                Materials.SpaceTime.getMolten(144 * 4096) },
            MTItemList.SpaceModulePumpInfinity.get(1),
            20 * 6000,
            (int) RECIPE_MAX);

        TTRecipeAdder.addResearchableAssemblylineRecipe(
            new ItemStack(Loaders.AMGenerator.getItem(), 1, 32028),
            16_777_216 * 2,
            16384,
            (int) TierEU.RECIPE_UXV,
            1,
            new Object[] { ItemList.LargeNaquadahReactor.get(64),
                new ItemStack(Casings.NaquadahReactorCasing.getItem(), 64, 15), ItemList.Electric_Piston_UMV.get(32),
                ItemList.Electric_Pump_UMV.get(64), ItemList.Field_Generator_UMV.get(16), ItemList.Sensor_UMV.get(8),
                ItemList.Emitter_UMV.get(64), new Object[] { OrePrefixes.circuit.get(Materials.MAX), 16 },
                MaterialsElements.STANDALONE.HYPOGEN.getGear(16), MaterialsElements.STANDALONE.HYPOGEN.getRing(64),
                MaterialsElements.STANDALONE.HYPOGEN.getScrew(64),
                GTOreDictUnificator.get(OrePrefixes.pipeLarge, Materials.SpaceTime, 8),
                GregtechItemList.Compressed_Fusion_Reactor.get(1), MaterialsElements.STANDALONE.HYPOGEN.getFineWire(64),
                MaterialsElements.STANDALONE.HYPOGEN.getFineWire(64),
                MaterialsElements.STANDALONE.HYPOGEN.getFineWire(64) },
            new FluidStack[] { GGMaterial.naquadahBasedFuelMkIV.getFluidOrGas(1000000),
                Materials.SpaceTime.getMolten(144 * 4096), Materials.DimensionallyShiftedSuperfluid.getFluid(4096000) },
            MTItemList.MTNQDAFReactor.get(1),
            20 * 600,
            (int) TierEU.RECIPE_UMV);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTModHandler.getModItem("gregtech", "gt.blockmachines", 1, 2712),
                ItemList.Electric_Pump_UHV.get(4),
                ItemList.Electric_Piston_UHV.get(4),
                ItemList.Emitter_UHV.get(4),
                ItemList.Sensor_UHV.get(4),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 4, 23),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 4, 67))
            .fluidInputs(MaterialsAlloy.INDALLOY_140.getFluidStack(9216))
            .itemOutputs(MTItemList.MTInventoryInputHatchME.get(1))
            .eut(RECIPE_UHV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTModHandler.getModItem("gregtech", "gt.blockmachines", 1, 2711),
                ItemList.Conveyor_Module_IV.get(4),
                ItemList.Robot_Arm_IV.get(4),
                ItemList.Emitter_IV.get(4),
                ItemList.Sensor_IV.get(4),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 4, 23),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 4, 56))
            .fluidInputs(Materials.Lubricant.getFluid(9216))
            .itemOutputs(MTItemList.MTInventoryInputBusME.get(1))
            .eut(RECIPE_IV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hatch_VacuumConveyor_Input.get(1),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 8, 47),
                ItemList.WormholeGenerator.get(1),
                ItemList.Sensor_UIV.get(4),
                new Object[] { OrePrefixes.circuit.get(Materials.UIV), 4 },
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.SuperconductorUIV, 64),
                ItemList.Cover_AdvancedRedstoneReceiver.get(1))
            .fluidInputs(MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(9216))
            .itemOutputs(MTItemList.MTWirelessVacuumConveyorInput.get(1))
            .eut(RECIPE_UIV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hatch_VacuumConveyor_Output.get(1),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 8, 47),
                ItemList.WormholeGenerator.get(1),
                ItemList.Emitter_UIV.get(4),
                new Object[] { OrePrefixes.circuit.get(Materials.UIV), 4 },
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.SuperconductorUIV, 64),
                ItemList.Cover_AdvancedRedstoneTransmitter.get(1))
            .fluidInputs(Materials.Lubricant.getFluid(9216))
            .itemOutputs(MTItemList.MTWirelessVacuumConveyorOutput.get(1))
            .eut(RECIPE_UIV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        TTRecipeAdder.addResearchableAssemblylineRecipe(
            ItemList.Machine_Multi_NanochipAssemblyComplex.get(1),
            16_777_216 * 2,
            16384,
            (int) TierEU.RECIPE_UIV,
            1,
            new Object[] { ItemList.NanoChipModule_AssemblyMatrix.get(1), ItemList.NanoChipModule_SMDProcessor.get(1),
                ItemList.NanoChipModule_BoardProcessor.get(1), ItemList.NanoChipModule_OpticalOrganizer.get(1),
                ItemList.NanoChipModule_EncasementWrapper.get(1), ItemList.NanoChipModule_BiologicalCoordinator.get(1),
                ItemList.NanoChipModule_EtchingArray.get(1), ItemList.NanoChipModule_CuttingChamber.get(1),
                ItemList.NanoChipModule_WireTracer.get(1), ItemList.NanoChipModule_SuperconductorSplitter.get(1),
                ItemList.NanoChipModule_Splitter.get(1), GregtechItemList.Compressed_Fusion_Reactor.get(1),
                GTOreDictUnificator.get(OrePrefixes.wireGt16, Materials.SuperconductorUIV, 64),
                MTNACComponentItems.getRealItemStack(CircuitComponent.PicoCircuit, 16) },
            new FluidStack[] { Materials.Lubricant.getFluid(1000000), MaterialsAlloy.INDALLOY_140.getFluidStack(921600),
                MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(STACKS * INGOTS) },
            MTItemList.MTNanoScaleFoundry.get(1),
            20 * 600,
            (int) TierEU.RECIPE_UIV);

        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(
                    CircuitComponent.PicoCircuit.realComponent.get()
                        .getItem(),
                    9))
            .itemOutputs(MTNACComponentItems.getRealItemStack(CircuitComponent.PicoCircuit, 1))
            .eut(RECIPE_UIV)
            .duration(MINUTES)
            .addTo(compressorRecipes);

        GTValues.RA.stdBuilder()
            .circuit(1)
            .itemInputs(new ItemStack(Items.iron_ingot, 1))
            .itemOutputs(Materials.Steel.getIngots(1))
            .eut(0)
            .duration(1)
            .addTo(Steel_brick_Recipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Bronze.getPlates(4), new ItemStack(Items.brick, 1))
            .itemOutputs(new ItemStack(Casings.BronzePlatedBricks.getItem(), 1, 10))
            .eut(0)
            .duration(1)
            .addTo(Steel_brick_Recipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Machine_Bricked_BlastFurnace.get(64),
                ItemList.Machine_Bricked_BlastFurnace.get(64),
                ItemList.Machine_Bricked_BlastFurnace.get(64),
                ItemList.Hull_Bronze_Bricks.get(64),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.Bronze, 64),
                GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.Bronze, 64),
                new ItemStack(Items.brick, 64))
            .circuit(1)
            .fluidInputs(Materials.Bronze.getMolten(144 * 512))
            .itemOutputs(MTItemList.MTDBBFurnace.get(1))
            .eut(RECIPE_LV)
            .duration(114514 * 20)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.SpaceElevatorModuleAssemblerT3.get(1),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.Eternity, 16),
                GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.Eternity, 8),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.MagMatter, 16),
                GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.MagMatter, 8),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Universium, 16),
                GTOreDictUnificator.get(OrePrefixes.screw, Materials.Universium, 64),
                ItemList.Robot_Arm_UXV.get(64),
                new Object[] { OrePrefixes.circuit.get(Materials.UXV), 16 })
            .fluidInputs(Materials.MHDCSM.getMolten(1145140))
            .itemOutputs(MTItemList.SpaceModuleAssemblerInfinity.get(1))
            .eut(RECIPE_UV)
            .duration(1919810)
            .addTo(assemblerRecipes);

    }
}
