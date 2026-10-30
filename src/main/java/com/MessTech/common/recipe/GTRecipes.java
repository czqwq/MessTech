package com.MessTech.common.recipe;

import static com.MessTech.common.recipe.MTChemicalTwisterRecipes.addChemicalTwisterRecipes;
import static com.MessTech.common.recipe.MTRecipeMaps.Steel_brick_Recipes;
import static goodgenerator.loader.Loaders.compactFusionCoil;
import static gregtech.api.casing.Casings.NanochipFirewallProjectionCasing;
import static gregtech.api.casing.Casings.NanochipMeshInterfaceCasing;
import static gregtech.api.enums.TierEU.RECIPE_HV;
import static gregtech.api.enums.TierEU.RECIPE_IV;
import static gregtech.api.enums.TierEU.RECIPE_LV;
import static gregtech.api.enums.TierEU.RECIPE_MAX;
import static gregtech.api.enums.TierEU.RECIPE_MV;
import static gregtech.api.enums.TierEU.RECIPE_UEV;
import static gregtech.api.enums.TierEU.RECIPE_UHV;
import static gregtech.api.enums.TierEU.RECIPE_UIV;
import static gregtech.api.enums.TierEU.RECIPE_UMV;
import static gregtech.api.enums.TierEU.RECIPE_UV;
import static gregtech.api.enums.TierEU.RECIPE_ZPM;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.cannerRecipes;
import static gregtech.api.recipe.RecipeMaps.centrifugeRecipes;
import static gregtech.api.recipe.RecipeMaps.compressorRecipes;
import static gregtech.api.recipe.RecipeMaps.electrolyzerRecipes;
import static gregtech.api.util.GTRecipeBuilder.HOURS;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeBuilder.STACKS;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.NANITE_TIERS;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;
import static gtPlusPlus.core.material.nuclear.MaterialsFluorides.FLUORITE;
import static tectech.thing.CustomItemList.DATApipe;
import static tectech.thing.CustomItemList.Machine_Multi_BECGenerator;
import static tectech.thing.CustomItemList.Machine_Multi_Computer;
import static tectech.thing.CustomItemList.Machine_Multi_DataBank;
import static tectech.thing.CustomItemList.UncertaintyX_Hatch;
import static tectech.thing.CustomItemList.rack_Hatch;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.items.MTItemList;
import com.MessTech.common.items.MTItems;
import com.MessTech.common.items.MTNACComponentItems;
import com.MessTech.common.machine.Base.IMTModule;
import com.MessTech.common.machine.hatch.MTReactorAccessHatch;
import com.MessTech.common.machine.hatch.MTReactorCoolantInputHatch;
import com.MessTech.common.thaumcraft.MTEssentiaHatchMETileEntity;
import com.MessTech.init.MessTech;

import appeng.api.AEApi;
import bartworks.common.loaders.ItemRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import goodgenerator.items.GGMaterial;
import goodgenerator.loader.Loaders;
import goodgenerator.util.ItemRefer;
import gregtech.api.casing.Casings;
import gregtech.api.enums.CondensateType;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.NaniteTier;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.recipe.Scanning;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;
import gtPlusPlus.core.material.MaterialMisc;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;
import gtPlusPlus.xmod.thermalfoundation.fluid.TFFluids;
import gtnhintergalactic.recipe.IGRecipeMaps;
import gtnhlanth.common.register.LanthItemList;
import ic2.core.Ic2Items;
import tectech.recipe.TTRecipeAdder;
import tectech.recipe.TecTechRecipeMaps;
import tectech.thing.CustomItemList;
import thaumcraft.common.config.ConfigBlocks;
import thaumicenergistics.api.ThEApi;

public class GTRecipes {

    public static void loadRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(FLUORITE.getDust(16))
            .itemOutputs(Materials.Calcium.getDust(3), Materials.Iron.getDust(2), Materials.Coal.getDust(1))
            .fluidOutputs(Materials.Fluorine.getGas(16000))
            .duration(16 * SECONDS)
            .eut(RECIPE_HV)
            .addTo(electrolyzerRecipes);
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.SpaceElevatorController.get(2),
                ItemList.SpaceElevatorCable.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UV, 16))
            .itemOutputs(MTItemList.MTSpaceElevator.get(1))
            .circuit(23)
            .duration(64 * MINUTES)
            .eut(RECIPE_UV)
            .addTo(assemblerRecipes);
        GameRegistry.addShapedRecipe(
            MTItemList.GameConsole.get(1),
            "IGI",
            "APA",
            "IYI",
            'A',
            Blocks.wooden_button,
            'G',
            Blocks.glass_pane,
            'P',
            ItemList.Cover_Screen.get(1),
            'Y',
            Items.redstone,
            'I',
            GTOreDictUnificator.get(OrePrefixes.plate, Materials.Iron, 1));
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
                NanochipMeshInterfaceCasing.toStack(8),
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
                NanochipFirewallProjectionCasing.toStack(1),
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
                Casings.AssemblerMachineCasing.toStack(64),
                Casings.AssemblyLineCasing.toStack(64),
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
            new Object[] { ItemList.LargeNaquadahReactor.get(64), Casings.NaquadahReactorCasing.toStack(64),
                ItemList.Electric_Piston_UMV.get(32), ItemList.Electric_Pump_UMV.get(64),
                ItemList.Field_Generator_UMV.get(16), ItemList.Sensor_UMV.get(8), ItemList.Emitter_UMV.get(64),
                new Object[] { OrePrefixes.circuit.get(Materials.MAX), 16 },
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

        // Wireless particle beam hatches: the same wireless kit as the vacuum conveyor hatches above, on the matching
        // wired beam hatch. Input takes the receiver half, output the transmitter half.
        GTValues.RA.stdBuilder()
            .itemInputs(
                LanthItemList.LUV_BEAMLINE_INPUT_HATCH,
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 8, 47),
                ItemList.Sensor_ZPM.get(4),
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 4 },
                ItemList.Cover_AdvancedRedstoneReceiver.get(1))
            .fluidInputs(MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(9216))
            .itemOutputs(MTItemList.MTWirelessBeamlineInput.get(1))
            .eut(RECIPE_ZPM)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                LanthItemList.LUV_BEAMLINE_OUTPUT_HATCH,
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 8, 47),
                ItemList.Emitter_ZPM.get(4),
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 4 },
                ItemList.Cover_AdvancedRedstoneTransmitter.get(1))
            .fluidInputs(Materials.Lubricant.getFluid(9216))
            .itemOutputs(MTItemList.MTWirelessBeamlineOutput.get(1))
            .eut(RECIPE_ZPM)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.AdvancedBeamlineOutputHatch.get(1),
                GTModHandler.getModItem(Mods.AppliedEnergistics2.getID(), "item.ItemMultiMaterial", 8, 47),
                ItemList.Emitter_UV.get(4),
                new Object[] { OrePrefixes.circuit.get(Materials.UHV), 4 },
                ItemList.Cover_AdvancedRedstoneTransmitter.get(1))
            .fluidInputs(Materials.Lubricant.getFluid(9216))
            .itemOutputs(MTItemList.MTWirelessBeamlineAdvancedOutput.get(1))
            .eut(RECIPE_ZPM)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        TTRecipeAdder.addResearchableAssemblylineRecipe(
            ItemList.Machine_Multi_NanochipAssemblyComplex.get(1),
            16_777_216 * 2,
            16384,
            (int) TierEU.RECIPE_UIV,
            1,
            new Object[] { ItemList.NanoChipModule_AssemblyMatrix.get(1), ItemList.NanoChipModule_PartProcessor.get(1),
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
            .itemOutputs(Casings.BronzePlatedBricks.toStack(1))
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

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.copyAmountUnsafe(10000, GTOreDictUnificator.get(OrePrefixes.nanite, Materials.Carbon, 1)),
                ItemList.MegaChemicalReactor.get(8),
                ItemList.Robot_Arm_UV.get(64),
                ItemList.Field_Generator_UV.get(16),
                new ItemStack(compactFusionCoil, 16, 3),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UHV, 32))
            .fluidInputs(Materials.Kevlar.getMolten(1024 * 144))
            .circuit(24)
            .itemOutputs(MTItemList.MTChemicalTwister.get(1))
            .eut(RECIPE_UHV)
            .duration(MINUTES * 16)
            .addTo(assemblerRecipes);

        // The Huge Chemical Reactor is built from the reactor it runs: a stack of the Large Chemical Reactor
        // multiblocks, the IV reactor to copy the pool of, and the PTFE pipes and cupronickel coils of its 5x5x5 shell
        // (the coil band takes any tier, this is the level 1 pictured).
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Machine_Multi_LargeChemicalReactor.get(64),
                ItemList.Machine_IV_ChemicalReactor.get(4),
                ItemList.Casing_Pipe_Polytetrafluoroethylene.get(4),
                ItemList.Casing_Coil_Cupronickel.get(8),
                new Object[] { OrePrefixes.circuit.get(Materials.IV), 16 })
            .fluidInputs(Materials.Polytetrafluoroethylene.getMolten(144 * 128))
            .circuit(17)
            .itemOutputs(MTItemList.MTHugeChemicalReactor.get(1))
            .eut(RECIPE_IV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        addBec(
            MTItemList.BosesCraftingArray.get(1),
            new ItemStack[] { CustomItemList.Machine_Multi_BECAssembler.get(8),
                CustomItemList.Machine_Multi_BECIONode.get(1), CustomItemList.Machine_Multi_BECStorage.get(1),
                Machine_Multi_BECGenerator.get(1), ItemList.Robot_Arm_UMV.get(32), ItemList.Sensor_UMV.get(16),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UXV, 16),
                Casings.CoherencePreservingPlasmaConduit.toStack(16), Casings.CondensateTransformativeCoil.toStack(16),
                Casings.PeaceEnforcementCasing.toStack(16), Casings.ElectromagneticallyIsolatedCasing.toStack(16),
                CustomItemList.Pipe_BEC.get(64), GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.SpaceTime, 8),
                GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.SpaceTime, 16) },
            nanites(1, 1, 4, 5, 1, 4, 1, 1, 4, 5, 1, 4, 4, 4),
            new FluidStack[] { CondensateType.Infinity.getEntangled(1_080_000),
                CondensateType.ChromaticGlass.getEntangled(2_001_600),
                CondensateType.Neutronium.getEntangled(3_024_000), CondensateType.Bedrockium.getEntangled(4_032_000) },
            10 * MINUTES,
            TierEU.RECIPE_UIV);

        addReactorRecipes();
        addChemicalTwisterRecipes();
        addModuleRecipes();
        addSpaceApiaryRecipes();
        addUltimatePatternTerminalRecipe();
        addLegacyMachineRecipes();
        addComprehensiveMiningPlantRecipe();
        addSmallEssentiaSmelteryRecipes();
        MTLegFakeRecipes.populate();
        // The Chemical Twister's mode 2 pool is converted from GT5U's large chemical reactor comb recipes, which GT and
        // NHCoreMod have registered by now (postInit ran before this FMLLoadComplete pass, and the mod depends on GT).
        MTCombProcessingRecipes.populateCombProcessingRecipes();
    }

    /**
     * The two ported GT++/GoodGenerator machines: neither had a crafting recipe in GT5U (the Large Essentia Generator
     * only
     * had its research), so both get an assembler recipe here. The LPF is the nine-in-one EV machine, so its recipe is
     * one
     * hull plus the three parts per machine block it replaces; the LEG is the HV magic generator of its research page.
     */
    private static void addLegacyMachineRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_EV.get(1),
                Casings.MultiUseCasing.toStack(6),
                ItemList.Electric_Motor_EV.get(3),
                ItemList.Electric_Piston_EV.get(3),
                ItemList.Conveyor_Module_EV.get(3),
                new Object[] { OrePrefixes.circuit.get(Materials.EV), 4 })
            .fluidInputs(Materials.Polytetrafluoroethylene.getMolten(INGOTS * 16))
            .circuit(9)
            .itemOutputs(MTItemList.MTLargeProcessingFactory.get(1))
            .eut(TierEU.RECIPE_EV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_HV.get(1),
                ItemList.Casing_Firebox_TungstenSteel.get(4),
                ItemList.Electric_Pump_HV.get(2),
                ItemList.Field_Generator_HV.get(1),
                new ItemStack(thaumcraft.common.config.ConfigBlocks.blockJar, 4),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Thaumium, 4),
                new Object[] { OrePrefixes.circuit.get(Materials.HV), 4 })
            .fluidInputs(Materials.Thaumium.getMolten(INGOTS * 16))
            .itemOutputs(MTItemList.MTLargeEssentiaGenerator.get(1))
            .eut(RECIPE_HV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        // The ME essentia input hatch is the normal hatch plus an AE network interface (only exists with ThE).
        if (MTItemList.MTEssentiaHatchME.hasBeenSet()) {
            var ae = AEApi.instance()
                .definitions();
            GTValues.RA.stdBuilder()
                .itemInputs(
                    MTItemList.MTEssentiaHatch.get(1),
                    ae.materials()
                        .certusQuartzCrystal()
                        .maybeStack(4)
                        .orNull(),
                    ae.materials()
                        .logicProcessor()
                        .maybeStack(1)
                        .orNull(),
                    ae.parts()
                        .importBus()
                        .maybeStack(1)
                        .orNull(),
                    ItemList.Emitter_HV.get(2),
                    GTOreDictUnificator.get(OrePrefixes.plate, Materials.Thaumium, 4))
                .fluidInputs(Materials.Thaumium.getMolten(INGOTS * 4))
                .itemOutputs(MTItemList.MTEssentiaHatchME.get(1))
                .eut(RECIPE_HV)
                .duration(MINUTES)
                .addTo(assemblerRecipes);
        }

        // The int-capacity ME hatch: the same hatch upgraded with KekzTech ichor jars, ThE storage buses and UV
        // circuits.
        // Capacity and per-tick limit ride in the item's NBT, so the upgrade survives breaking and replacing the block.
        if (MTItemList.MTEssentiaHatchME.hasBeenSet() && kekztech.common.Blocks.jarIchor != null) {
            ThEApi thEApi = ThEApi.instance();
            if (thEApi != null) {
                ItemStack upgraded = MTItemList.MTEssentiaHatchME.get(1);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger(MTEssentiaHatchMETileEntity.NBT_CAPACITY, Integer.MAX_VALUE);
                tag.setInteger(MTEssentiaHatchMETileEntity.NBT_MAX_INPUT, Integer.MAX_VALUE);
                upgraded.setTagCompound(tag);
                GTValues.RA.stdBuilder()
                    .itemInputs(
                        MTItemList.MTEssentiaHatchME.get(1),
                        new ItemStack(kekztech.common.Blocks.jarIchor, 4, 0),
                        thEApi.parts().Essentia_StorageBus.getStacks(8),
                        new Object[] { OrePrefixes.circuit.get(Materials.UV), 64 })
                    .itemOutputs(upgraded)
                    .eut(TierEU.RECIPE_UV)
                    .duration(MINUTES)
                    .addTo(assemblerRecipes);
            }
        }
    }

    /**
     * The Comprehensive Mining Plant, built from the structure it is made of plus the two GT machines whose behaviour
     * it combines: the tower's casing, frames, metal blocks, advanced casing and High Power Casing, a ring of eight IV
     * fluid drilling rigs and one LuV Void Miner I. The Void Miner I is read from BartWorks' own registry list
     * ({@code ItemRegistry.voidminer[0]}); the source mod instead scanned {@code GregTechAPI.METATILEENTITIES} for the
     * {@code MTEVoidMiners$VMLUV} class name, which the registry makes unnecessary.
     * <p>
     * The counts follow the structure: 8 A (Steel Gearbox Casing), 4 B (Steel Frame), 19 C (Tritanium Frame), 7 D (HSLA
     * Steel Block), 4 E (Steel Block), 8 G (Rebolted Naquadah Casing). F (High Power Casing, 19 positions) is taken as
     * 16 because the hatches use part of it.
     */
    private static void addComprehensiveMiningPlantRecipe() {
        // BartWorks fills its lists in GT's preInit, long before this mod's recipe load, but a missing entry must not
        // turn into a recipe with a null input.
        final ItemStack voidMiner = ItemRegistry.voidminer[0];
        if (voidMiner == null) {
            MessTech.MT_LOG.warn("Comprehensive Mining Plant recipe not registered: the Void Miner I item is missing");
            return;
        }

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_LuV.get(1),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Steel, 64),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Tritanium, 32),
                GTOreDictUnificator.get(OrePrefixes.block, Materials.Steel, 4),
                casingStack(Casings.ReboltedNaquadahCasing, 8),
                casingStack(Casings.HighPowerCasing, 16),
                ItemList.OilDrill4.get(8),
                voidMiner,
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UV, 16))
            .fluidInputs(Materials.Polytetrafluoroethylene.getMolten(INGOTS * 16))
            .circuit(6)
            .itemOutputs(MTItemList.MTComprehensiveMiningPlant.get(1))
            .eut(TierEU.RECIPE_ZPM)
            .duration(SECONDS * 325)
            .addTo(assemblerRecipes);
    }

    /**
     * {@code ICasing#toStack(int)} ignores its amount argument in GT 5.09.54.205 (it always returns a stack of one), so
     * recipes that need more than one casing have to build the stack by hand.
     */
    private static ItemStack casingStack(Casings casing, int amount) {
        return new ItemStack(casing.getBlock(), amount, casing.getBlockMeta());
    }

    /**
     * The Small Essentia Smeltery and its ME essentia output hatch. The smeltery is a compact counterpart of the Large
     * Essentia Smeltery: an IV hull around the same magic parts, so it is an IV assembler recipe. The ME output hatch
     * is GoodGenerator's normal output hatch plus a ThE 16384k essentia storage component, as requested: one output
     * hatch, circuit 1, IV assembler, 100 seconds.
     */
    private static void addSmallEssentiaSmelteryRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_IV.get(1),
                new ItemStack(Loaders.magicCasing, 8, 0),
                new ItemStack(ConfigBlocks.blockMetalDevice, 8, 9),
                ItemRefer.Essentia_Cell_T2.get(2),
                new Object[] { OrePrefixes.circuit.get(Materials.IV), 4 })
            .fluidInputs(Materials.Thaumium.getMolten(INGOTS * 16))
            .itemOutputs(MTItemList.MTSmallEssentiaSmeltery.get(1))
            .eut(RECIPE_IV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        if (MTItemList.MTEssentiaOutputHatchME.hasBeenSet()) {
            ThEApi thEApi = ThEApi.instance();
            if (thEApi != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(
                        ItemRefer.Essentia_Output_Hatch.get(1),
                        thEApi.items().EssentiaStorageComponent_16384k.getStacks(1))
                    .circuit(1)
                    .itemOutputs(MTItemList.MTEssentiaOutputHatchME.get(1))
                    .eut(RECIPE_IV)
                    .duration(SECONDS * 100)
                    .addTo(assemblerRecipes);
            }
        }
    }

    /**
     * The Ultimate Pattern Terminal (终极样板编码终端) is AE's extended pattern terminal on a part item, and its recipe
     * says exactly that: a titanium frame carrying that part, two Engineering Processors, a Pattern Capacity Card
     * (样板容量卡) and a Calculation Processor.
     */
    private static void addUltimatePatternTerminalRecipe() {
        var ae = AEApi.instance()
            .definitions();

        GTModHandler.addCraftingRecipe(
            MTItemList.UltimatePatternTerminal.get(1),
            new Object[] { "ABA", "CDC", "AEA", 'A', GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 1),
                'B', ae.parts()
                    .patternTerminalEx()
                    .maybeStack(1)
                    .orNull(),
                'C', ae.materials()
                    .engProcessor()
                    .maybeStack(1)
                    .orNull(),
                'D', ae.materials()
                    .calcProcessor()
                    .maybeStack(1)
                    .orNull(),
                'E', ae.materials()
                    .cardPatternCapacity()
                    .maybeStack(1)
                    .orNull() });
    }

    /**
     * The 30 module hatch recipes: the speed, EU discount and parallel control modules of {@code MTModuleValues}, one
     * assembler recipe per family per tier IV..MAX.
     * <p>
     * TST's controller recipes ({@code ModularHatchesRecipes}) and GTNL's parallel controller hatch are the shape:
     * anchored on the tier's hull, a flat handful of that tier's components, circuits and plates, plus the tier's own
     * material as plates and as melt - the counts stay flat on purpose, material and voltage carry the price of a tier.
     * The families differ only in components, TST's own split: speed = field generators, motors and pistons; parallel =
     * a
     * field generator, robot arms and conveyors; EU = field generators and emitters. The circuit numbers are this mod's
     * own family markers (the idea of the {@code 4} GTNL puts on its parallel controller hatch) and tell them apart in
     * NEI at a glance.
     */
    private static void addModuleRecipes() {
        final int HULLS = 4;
        final int COUNT = 16;
        final int SPEED_CIRCUIT = 4;
        final int PARALLEL_CIRCUIT = 5;
        final int EU_CIRCUIT = 6;

        // One entry per module tier IV..MAX, aligned with MTItemList.SPEED_MODULES and friends.
        ItemList[] fieldGenerators = { ItemList.Field_Generator_IV, ItemList.Field_Generator_LuV,
            ItemList.Field_Generator_ZPM, ItemList.Field_Generator_UV, ItemList.Field_Generator_UHV,
            ItemList.Field_Generator_UEV, ItemList.Field_Generator_UIV, ItemList.Field_Generator_UMV,
            ItemList.Field_Generator_UXV, ItemList.Field_Generator_MAX };
        ItemList[] motors = { ItemList.Electric_Motor_IV, ItemList.Electric_Motor_LuV, ItemList.Electric_Motor_ZPM,
            ItemList.Electric_Motor_UV, ItemList.Electric_Motor_UHV, ItemList.Electric_Motor_UEV,
            ItemList.Electric_Motor_UIV, ItemList.Electric_Motor_UMV, ItemList.Electric_Motor_UXV,
            ItemList.Electric_Motor_MAX };
        ItemList[] pistons = { ItemList.Electric_Piston_IV, ItemList.Electric_Piston_LuV, ItemList.Electric_Piston_ZPM,
            ItemList.Electric_Piston_UV, ItemList.Electric_Piston_UHV, ItemList.Electric_Piston_UEV,
            ItemList.Electric_Piston_UIV, ItemList.Electric_Piston_UMV, ItemList.Electric_Piston_UXV,
            ItemList.Electric_Piston_MAX };
        ItemList[] robotArms = { ItemList.Robot_Arm_IV, ItemList.Robot_Arm_LuV, ItemList.Robot_Arm_ZPM,
            ItemList.Robot_Arm_UV, ItemList.Robot_Arm_UHV, ItemList.Robot_Arm_UEV, ItemList.Robot_Arm_UIV,
            ItemList.Robot_Arm_UMV, ItemList.Robot_Arm_UXV, ItemList.Robot_Arm_MAX };
        ItemList[] conveyors = { ItemList.Conveyor_Module_IV, ItemList.Conveyor_Module_LuV,
            ItemList.Conveyor_Module_ZPM, ItemList.Conveyor_Module_UV, ItemList.Conveyor_Module_UHV,
            ItemList.Conveyor_Module_UEV, ItemList.Conveyor_Module_UIV, ItemList.Conveyor_Module_UMV,
            ItemList.Conveyor_Module_UXV, ItemList.Conveyor_Module_MAX };
        ItemList[] emitters = { ItemList.Emitter_IV, ItemList.Emitter_LuV, ItemList.Emitter_ZPM, ItemList.Emitter_UV,
            ItemList.Emitter_UHV, ItemList.Emitter_UEV, ItemList.Emitter_UIV, ItemList.Emitter_UMV,
            ItemList.Emitter_UXV, ItemList.Emitter_MAX };

        for (int i = 0; i < MTItemList.SPEED_MODULES.length; i++) {
            int tier = IMTModule.MIN_TIER + i;
            Materials material = MODULE_MATERIALS[i];

            GTValues.RA.stdBuilder()
                .itemInputs(
                    TIER_HULLS[tier].get(HULLS),
                    fieldGenerators[i].get(COUNT),
                    fieldGenerators[i].get(COUNT),
                    fieldGenerators[i].get(COUNT),
                    motors[i].get(COUNT),
                    pistons[i].get(COUNT),
                    new Object[] { OrePrefixes.circuit.get(TIER_CIRCUIT_MATERIALS[tier]), COUNT },
                    GTOreDictUnificator.get(OrePrefixes.plate, material, COUNT))
                .fluidInputs(material.getMolten(144 * COUNT))
                .circuit(SPEED_CIRCUIT)
                .itemOutputs(MTItemList.SPEED_MODULES[i].get(1))
                .eut(TIER_RECIPE_EU[tier])
                .duration(MINUTES * (i + 1))
                .addTo(assemblerRecipes);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    TIER_HULLS[tier].get(HULLS),
                    fieldGenerators[i].get(COUNT),
                    robotArms[i].get(COUNT),
                    conveyors[i].get(COUNT),
                    new Object[] { OrePrefixes.circuit.get(TIER_CIRCUIT_MATERIALS[tier]), COUNT },
                    GTOreDictUnificator.get(OrePrefixes.plate, material, COUNT))
                .fluidInputs(material.getMolten(144 * COUNT))
                .circuit(PARALLEL_CIRCUIT)
                .itemOutputs(MTItemList.PARALLEL_MODULES[i].get(1))
                .eut(TIER_RECIPE_EU[tier])
                .duration(MINUTES * (i + 1))
                .addTo(assemblerRecipes);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    TIER_HULLS[tier].get(HULLS),
                    fieldGenerators[i].get(COUNT),
                    fieldGenerators[i].get(COUNT),
                    emitters[i].get(COUNT),
                    new Object[] { OrePrefixes.circuit.get(TIER_CIRCUIT_MATERIALS[tier]), COUNT },
                    GTOreDictUnificator.get(OrePrefixes.plate, material, COUNT))
                .fluidInputs(material.getMolten(144 * COUNT))
                .circuit(EU_CIRCUIT)
                .itemOutputs(MTItemList.EU_MODULES[i].get(1))
                .eut(TIER_RECIPE_EU[tier])
                .duration(MINUTES * (i + 1))
                .addTo(assemblerRecipes);
        }
    }

    /**
     * The four space apiary modules, one Space Assembler recipe each - the shape TST gives its {@code TST_SpaceApiary}
     * in {@code GTCMMachineRecipes}. Every tier spends 4 stacks of 64 Industrial Apiaries and 4 stacks of 64 fully
     * upgraded acceleration upgrades (the apiary it replaces four hundred times over), 16 of that tier's machine parts
     * and 64 circuits, and pays in a solder fluid and honey; TST's parts tier and doubling volumes as well.
     * <p>
     * The pages share {@code IGRecipeMaps.spaceAssemblerRecipes} with TST's, so the inputs must stay distinguishable:
     * MK-III and MK-IV differ in circuit (ours UXV/MAX, TST's UMV/UXV), and MK-I and MK-II pay in Mutated Living Solder
     * where TST's T1/T2 pay UU-Matter - with identical inputs a Space Assembler matched both recipes and took whichever
     * it looked at first, leaving one module unobtainable. Replacing a *required* fluid is what makes them mutually
     * exclusive; an added item or fluid leaves TST's recipe matching anyway. 16 ingots doubling per tier follows the
     * pool's own module ladder (Space Elevator Pump Module MK-I 9 ingots, MK-II 32, MK-III 1 stack,
     * MachineRecipes:279/297/332), and GT++'s solder chemistry puts out 4 stacks + 24 ingots per batch
     * (RecipeLoaderGenericChem:163), so one module costs a fraction of a batch.
     * <p>
     * {@code specialValue} is inert here: the assembler's tier gate reads the {@code MODULE_TIER} metadata
     * ({@code TileEntityModuleAssembler#createProcessingLogic}), which neither mod sets, so every recipe counts as
     * module tier 1.
     */
    private static void addSpaceApiaryRecipes() {
        ItemStack[][] parts = {
            { ItemList.Field_Generator_UHV.get(16), ItemList.Conveyor_Module_UHV.get(16),
                ItemList.Robot_Arm_UHV.get(16), ItemList.Electric_Pump_UHV.get(16) },
            { ItemList.Field_Generator_UEV.get(16), ItemList.Conveyor_Module_UEV.get(16),
                ItemList.Robot_Arm_UEV.get(16), ItemList.Electric_Pump_UEV.get(16) },
            { ItemList.Field_Generator_UIV.get(16), ItemList.Conveyor_Module_UIV.get(16),
                ItemList.Robot_Arm_UIV.get(16), ItemList.Electric_Pump_UIV.get(16) },
            { ItemList.Field_Generator_UMV.get(16), ItemList.Conveyor_Module_UMV.get(16),
                ItemList.Robot_Arm_UMV.get(16), ItemList.Electric_Pump_UMV.get(16) } };
        Materials[] circuits = { Materials.UEV, Materials.UIV, Materials.UXV, Materials.MAX };
        long[] voltages = { RECIPE_UHV, RECIPE_UEV, RECIPE_UIV, RECIPE_UMV };
        ItemStack[] outputs = { MTItemList.SpaceModuleApiaryMK1.get(1), MTItemList.SpaceModuleApiaryMK2.get(1),
            MTItemList.SpaceModuleApiaryMK3.get(1), MTItemList.SpaceModuleApiaryMK4.get(1) };

        for (int i = 0; i < outputs.length; i++) {
            long scale = 1L << i;
            FluidStack firstFluid = i < 2
                ? MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack((int) (16 * INGOTS * scale))
                : Materials.UUMatter.getFluid(1000L * 128 * scale);
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Machine_IndustrialApiary.get(64),
                    ItemList.Machine_IndustrialApiary.get(64),
                    ItemList.Machine_IndustrialApiary.get(64),
                    ItemList.Machine_IndustrialApiary.get(64),
                    ItemList.IndustrialApiary_Upgrade_Acceleration_8_Upgraded.get(64),
                    ItemList.IndustrialApiary_Upgrade_Acceleration_8_Upgraded.get(64),
                    ItemList.IndustrialApiary_Upgrade_Acceleration_8_Upgraded.get(64),
                    ItemList.IndustrialApiary_Upgrade_Acceleration_8_Upgraded.get(64),
                    parts[i][0],
                    parts[i][1],
                    parts[i][2],
                    parts[i][3],
                    new Object[] { OrePrefixes.circuit.get(circuits[i]), 64 })
                .fluidInputs(firstFluid, Materials.Honey.getFluid(1000L * 256 * scale))
                .itemOutputs(outputs[i])
                .specialValue(1)
                .eut(voltages[i])
                .duration(20 * 300 * scale)
                .addTo(IGRecipeMaps.spaceAssemblerRecipes);
        }
    }

    /**
     * Nuclear reactor parts: access hatches, heat control hatches, the coolant hatch pair of the fluid (wet) mode, the
     * controller and the Transcendent Metal fuel rods. The hatches are one assembler recipe per tier (EV..UIV; the
     * coolant pair EV only, see the hatch classes); the single rod is canned from The Core and Avaritia Star Fuel, the
     * dual/quad rods are UIV assembler recipes (GT's own {@code FissionFuelLoader} shape).
     */
    private static void addReactorRecipes() {
        for (int i = 0; i < MTItemList.REACTOR_ACCESS_HATCHES.length; i++) {
            int tier = MTReactorAccessHatch.MIN_TIER + i;
            long eut = TIER_RECIPE_EU[tier];

            // Access hatch: tier casing + lever + 4 tier circuits.
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.MACHINE_CASINGS[tier].get(1),
                    new ItemStack(Blocks.lever, 1),
                    new Object[] { OrePrefixes.circuit.get(TIER_CIRCUIT_MATERIALS[tier]), 4 })
                .circuit(1)
                .itemOutputs(MTItemList.REACTOR_ACCESS_HATCHES[i].get(1))
                .eut(eut)
                .duration(MINUTES * 2)
                .addTo(assemblerRecipes);

            // Heat control hatch: tier casing + 4 solid steel casings + 4 tier circuits.
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.MACHINE_CASINGS[tier].get(1),
                    ItemList.Casing_SolidSteel.get(4),
                    new Object[] { OrePrefixes.circuit.get(TIER_CIRCUIT_MATERIALS[tier]), 4 })
                .circuit(2)
                .itemOutputs(MTItemList.REACTOR_HEAT_HATCHES[i].get(1))
                .eut(eut)
                .duration(MINUTES * 2)
                .addTo(assemblerRecipes);
        }

        // Reactor coolant pair of the fluid ("wet") mode: tier casing + 2 solid steel casings + 2 tier circuits,
        // deliberately cheaper than the heat control hatch - plumbing, not a tier gate, and the coolant they consume is
        // what matters. Cold coolant in and hot coolant out cost the same, so the loop is never gated behind the
        // dearer.
        int coolantTier = MTReactorCoolantInputHatch.TIER;
        for (MTItemList coolantHatch : MTItemList.REACTOR_FLUID_HATCHES) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.MACHINE_CASINGS[coolantTier].get(1),
                    ItemList.Casing_SolidSteel.get(2),
                    new Object[] { OrePrefixes.circuit.get(TIER_CIRCUIT_MATERIALS[coolantTier]), 2 })
                .circuit(3)
                .itemOutputs(coolantHatch.get(1))
                .eut(TIER_RECIPE_EU[coolantTier])
                .duration(MINUTES * 2)
                .addTo(assemblerRecipes);
        }

        // Reactor controller: 16 solid steel casings, 16 IC2 nuclear reactors, 64 IC2 reactor chambers, molten lead.
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Casing_SolidSteel.get(16),
                GTUtility.copyAmount(16, Ic2Items.nuclearReactor),
                GTUtility.copyAmount(64, Ic2Items.reactorChamber))
            .circuit(1)
            .fluidInputs(Materials.Lead.getMolten(144 * 256))
            .itemOutputs(MTItemList.MTReactor.get(1))
            .eut(TierEU.RECIPE_EV)
            .duration(MINUTES * 2)
            .addTo(assemblerRecipes);

        // Single rod: The Core + Avaritia Star Fuel ("Resource" meta 8) in the canning machine.
        if (Mods.Avaritia.isModLoaded()) {
            GTValues.RA.stdBuilder()
                .itemInputs(ItemList.RodNaquadah32.get(1), GTModHandler.getModItem(Mods.Avaritia.ID, "Resource", 1, 8))
                .fluidInputs(Materials.TranscendentMetal.getMolten(INGOTS * 4))
                .itemOutputs(new ItemStack(MTItems.rodTranscendentMetal, 1))
                .eut(RECIPE_UEV)
                .duration(32 * SECONDS)
                .addTo(cannerRecipes);
        }

        // Dual rod: 2 single rods + 4 transcendent sticks.
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(MTItems.rodTranscendentMetal, 2),
                GTOreDictUnificator.get(OrePrefixes.stick, Materials.TranscendentMetal, 4))
            .circuit(2)
            .itemOutputs(new ItemStack(MTItems.rodTranscendentMetal2, 1))
            .eut(RECIPE_UIV)
            .duration(100 * SECONDS)
            .addTo(assemblerRecipes);

        // Quad rod (main recipe): 4 single rods + 6 long transcendent sticks.
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(MTItems.rodTranscendentMetal, 4),
                GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.TranscendentMetal, 6))
            .circuit(4)
            .itemOutputs(new ItemStack(MTItems.rodTranscendentMetal4, 1))
            .eut(RECIPE_UIV)
            .duration(100 * SECONDS)
            .addTo(assemblerRecipes);

        // Quad rod (alternative recipe): 2 dual rods + 2 long transcendent sticks.
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(MTItems.rodTranscendentMetal2, 2),
                GTOreDictUnificator.get(OrePrefixes.stickLong, Materials.TranscendentMetal, 2))
            .itemOutputs(new ItemStack(MTItems.rodTranscendentMetal4, 1))
            .eut(RECIPE_UIV)
            .duration(50 * SECONDS)
            .addTo(assemblerRecipes);

        Casings containmentFieldCasing = Casings.ContainmentFieldMachineCasing;
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(15),
                GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Steel, 1),
                ItemList.Field_Generator_UV.get(4),
                new Object[] { OrePrefixes.circuit.get(Materials.ZPM), 8 },
                GTOreDictUnificator.get(OrePrefixes.cableGt16, Materials.Naquadah, 4),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Steel, 8))
            .fluidInputs(Materials.NaquadahAlloy.getMolten(144 * 4 * 64))
            .itemOutputs(containmentFieldCasing.toStack(8))
            .eut(RECIPE_UV)
            .duration(20 * 60)
            .addTo(assemblerRecipes);

        // Depleted rods: centrifuge recycling, scaled with the rod size (single 1x / dual 2x / quad 4x).
        addDepletedRodRecycling(new ItemStack(MTItems.rodTranscendentMetalDepleted, 1), 1, 50 * SECONDS);
        addDepletedRodRecycling(new ItemStack(MTItems.rodTranscendentMetalDepleted2, 1), 2, 100 * SECONDS);
        addDepletedRodRecycling(new ItemStack(MTItems.rodTranscendentMetalDepleted4, 1), 4, 200 * SECONDS);
    }

    /**
     * Centrifuge recycling for one depleted Transcendent Metal rod, following GT's depleted naquadah rod recycling
     * (same output chances): the fuel is burned off, so the tungstensteel frame, the naquadah family remains and part
     * of
     * the rod shell come back as Transcendent Metal dust.
     *
     * @param rod        the depleted rod (stack size is ignored, the recipe always consumes one)
     * @param multiplier 1 for a single, 2 for a dual and 4 for a quad rod
     * @param duration   recipe duration in ticks
     */
    private static void addDepletedRodRecycling(ItemStack rod, int multiplier, int duration) {
        GTValues.RA.stdBuilder()
            .itemInputs(rod)
            .itemOutputs(
                Materials.TranscendentMetal.getDust(4 * multiplier),
                Materials.Naquadah.getDust(8 * multiplier),
                Materials.Naquadah.getDust(8 * multiplier),
                Materials.Naquadria.getDustSmall(4 * multiplier),
                Materials.NaquadahEnriched.getDustTiny(8 * multiplier),
                Materials.TungstenSteel.getDust(16 * multiplier),
                Materials.Platinum.getDust(2 * multiplier))
            .outputChances(100_00, 100_00, 50_00, 50_00, 25_00, 100_00, 100_00)
            .duration(duration)
            .eut(RECIPE_UIV)
            .addTo(centrifugeRecipes);
    }

    // BEC RECIPE REQUIRE THIS BUILDER
    private static void addBec(ItemStack output, ItemStack[] inputs, NaniteTier[] nanites, FluidStack[] condensates,
        int duration, long eut) {
        GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .fluidInputs(condensates)
            .itemOutputs(output)
            .metadata(NANITE_TIERS, nanites)
            .duration(duration)
            .eut(eut)
            .addTo(TecTechRecipeMaps.condensateAssemblingRecipes);
    }

    private static NaniteTier[] nanites(int... tiers) {
        NaniteTier[] result = new NaniteTier[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            result[i] = TIER_TO_NANITE[tiers[i] - 1];
        }
        return result;
    }

    private static final NaniteTier[] TIER_TO_NANITE = { NaniteTier.Carbon, NaniteTier.Silver, NaniteTier.Gold,
        NaniteTier.Transcendent, NaniteTier.SixPhasedCopper, NaniteTier.WhiteDwarf, NaniteTier.BlackDwarf,
        NaniteTier.Universium, NaniteTier.Eternity, NaniteTier.MagMatter };

    /** Recipe EU/t of every voltage tier (GT's {@code TierEU.RECIPE_*} convention), indexed by tier. */
    private static final long[] TIER_RECIPE_EU = { TierEU.RECIPE_ULV, TierEU.RECIPE_LV, RECIPE_MV, RECIPE_HV,
        TierEU.RECIPE_EV, TierEU.RECIPE_IV, TierEU.RECIPE_LuV, TierEU.RECIPE_ZPM, TierEU.RECIPE_UV, TierEU.RECIPE_UHV,
        TierEU.RECIPE_UEV, TierEU.RECIPE_UIV, TierEU.RECIPE_UMV, TierEU.RECIPE_UXV, TierEU.RECIPE_MAX };

    /** Circuit material of every voltage tier, the same mapping GT uses for its machine recipes. */
    private static final Materials[] TIER_CIRCUIT_MATERIALS = { Materials.ULV, Materials.LV, Materials.MV, Materials.HV,
        Materials.EV, Materials.IV, Materials.LuV, Materials.ZPM, Materials.UV, Materials.UHV, Materials.UEV,
        Materials.UIV, Materials.UMV, Materials.UXV, Materials.MAX };

    /**
     * Hull of every voltage tier, indexed by GT tier. Two names break the pattern, GT's own: {@code Hull_MAX} is the
     * UHV hull (MAX was the top tier when it was added, UHV came with the rename) and the MAX tier uses
     * {@code Hull_MAXV}. TST reads it the same way: {@code Hull_ZPM, Hull_UV, Hull_MAX, Hull_UEV, ...} for ZPM..MAX.
     */
    private static final ItemList[] TIER_HULLS = { ItemList.Hull_ULV, ItemList.Hull_LV, ItemList.Hull_MV,
        ItemList.Hull_HV, ItemList.Hull_EV, ItemList.Hull_IV, ItemList.Hull_LuV, ItemList.Hull_ZPM, ItemList.Hull_UV,
        ItemList.Hull_MAX, ItemList.Hull_UEV, ItemList.Hull_UIV, ItemList.Hull_UMV, ItemList.Hull_UXV,
        ItemList.Hull_MAXV };

    /**
     * The material a module is paid in, one entry per module tier IV..MAX (aligned with
     * {@code MTItemList.SPEED_MODULES} and the other two families): the first four are GT5U's own muffler hatch
     * materials ({@code AssemblerRecipes}), above that TST's controller chain, see its {@code ModularHatchesRecipes}.
     */
    private static final Materials[] MODULE_MATERIALS = { Materials.TungstenSteel, Materials.Enderium,
        Materials.NaquadahAlloy, Materials.Neutronium, Materials.CosmicNeutronium, Materials.Infinity,
        Materials.TranscendentMetal, Materials.SpaceTime, Materials.MHDCSM, Materials.MagMatter };

}
