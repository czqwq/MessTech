package com.MessTech.common.recipe;

import static com.MessTech.common.util.Utils.setStackSize;
import static gregtech.api.enums.TierEU.RECIPE_HV;
import static gregtech.api.enums.TierEU.RECIPE_IV;
import static gregtech.api.enums.TierEU.RECIPE_MV;
import static gregtech.api.enums.TierEU.RECIPE_UEV;
import static gregtech.api.enums.TierEU.RECIPE_UHV;
import static gregtech.api.enums.TierEU.RECIPE_UIV;
import static gregtech.api.enums.TierEU.RECIPE_UV;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static net.minecraftforge.fluids.FluidRegistry.getFluidStack;

import net.minecraftforge.fluids.FluidRegistry;

import bartworks.system.material.WerkstoffLoader;
import goodgenerator.items.GGMaterial;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialMisc;

public class MTChemicalTwisterRecipes {

    public static void addChemicalTwisterRecipes() {
        RecipeMap<RecipeMapBackend> MT = MTRecipeMaps.MTChemicalTwisterRecipes;
        /**
         * The one-step platinum-group-metal recipe of the Chemical Twister: the whole GT5U platinum line (bartworks
         * {@code PlatinumSludgeRecipes}) collapsed into a single reaction.
         * <p>
         * Derivation (`tmp/platinum/derive_platinum_recipe.py`, cross-checked in
         * `tmp/platinum/verify_platinum_recipe.py`):
         * every recipe of the line is written as a stoichiometric vector, all cyclic intermediates (Aqua Regia,
         * ammonium
         * chloride, formic acid, sodium sulfate/sodium, the refined-salt and leach-residue loops, ...) are forced to
         * balance exactly, and everything the line produces but never eats again (NO2, steam, salt, potassium,
         * ethylene,
         * calcium chloride, zinc sulfate, both PGS residues, surplus chlorine/water/ammonia, ...) is simply dropped.
         * What
         * is left is the net consumption per 6.8226 Platinum Metallic Powder: 4.8733 Pt, 3.1328 Pd, 1.5152 Ir, 3.6364
         * Ru,
         * 1 Rh and 0.1515 Os, scaled here by 33/5 so that osmium, iridium, ruthenium and the feed are integers.
         * <p>
         * The only metal feed is Platinum Metallic Powder: the line's own "palladium enriched ammonia" byproduct
         * carries
         * the palladium, so no Palladium Metallic Powder (i.e. no palladium ore) is needed. Ammonia is not inflated
         * beyond
         * what closes that palladium loop, which is why it stays an input instead of turning the loop into a palladium
         * printer.
         * <p>
         * 2701 K is the Kanthal coil, the heat the recipe asks for (IV energy adds 300 K on top, the same formula as
         * the
         * EBF); the machine refuses the recipe with {@code insufficientHeat} below that.
         */
        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                WerkstoffLoader.PTMetallicPowder.get(OrePrefixes.dust, 45),
                GTUtility.copyAmountUnsafe(124, Materials.SodiumHydroxide.getDust(1)),
                Materials.Saltpeter.getDust(33),
                Materials.Zinc.getDust(12),
                Materials.Calcium.getDust(26),
                GTUtility.copyAmountUnsafe(81, WerkstoffLoader.PotassiumDisulfate.get(OrePrefixes.dust, 1)))
            .fluidInputs(
                Materials.Ammonia.getGas(138_000),
                Materials.HydrochloricAcid.getFluid(259_500),
                Materials.NitricAcid.getFluid(186_000),
                Materials.CarbonMonoxide.getGas(41_400),
                Materials.SaltWater.getFluid(3_333))
            .itemOutputs(
                Materials.Platinum.getDust(32),
                Materials.Palladium.getDust(21),
                Materials.Osmium.getDust(1),
                Materials.Iridium.getDust(10),
                WerkstoffLoader.Rhodium.get(OrePrefixes.dust, 7),
                WerkstoffLoader.Ruthenium.get(OrePrefixes.dust, 24),
                // The net byproducts of the line: nothing in the chain consumes them again, so the one-step recipe
                // hands them out instead of dropping them. 172 and 69 exceed a stack, hence copyAmountUnsafe.
                GTUtility.copyAmountUnsafe(172, WerkstoffLoader.SodiumNitrate.get(OrePrefixes.dust, 1)),
                GTUtility.copyAmountUnsafe(69, WerkstoffLoader.ZincSulfate.get(OrePrefixes.dust, 1)),
                WerkstoffLoader.CalciumChloride.get(OrePrefixes.dust, 48),
                Materials.Salt.getDust(22),
                WerkstoffLoader.PGSDResidue.get(OrePrefixes.dust, 10),
                WerkstoffLoader.PGSDResidue2.get(OrePrefixes.dust, 10))
            .fluidOutputs(
                Materials.Chlorine.getGas(162_288),
                Materials.NitrogenDioxide.getGas(144_737),
                Materials.Water.getFluid(51_265),
                WerkstoffLoader.CalciumChloride.getFluidOrGas(30_000),
                Materials.Ethylene.getGas(10_338),
                Materials.Steam.getGas(3_333),
                Materials.Potassium.getMolten(2_105))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(2701)
            .eut(RECIPE_IV)
            .duration(256 * SECONDS)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(Materials.Carbon.getDust(20))
            .circuit(1)
            .fluidInputs(Materials.Nitrogen.getGas(4000), Materials.Hydrogen.getGas(12000))
            .fluidOutputs(Materials.Polybenzimidazole.getMolten(1000))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(6301)
            .eut(RECIPE_IV)
            .duration(60 * SECONDS)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(GTUtility.copyAmountUnsafe(20 * 10000, Materials.Carbon.getDust(1)))
            .circuit(4)
            .fluidInputs(Materials.Nitrogen.getGas(4000 * 10000), Materials.Hydrogen.getGas(12000 * 10000))
            .fluidOutputs(Materials.Polybenzimidazole.getMolten(1000 * 10000))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(6301)
            .eut(RECIPE_IV)
            .duration(60 * 2500 * SECONDS)
            .addTo(MT);

        // Next part is copy from Twist Space Technology

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(23),
                ItemList.Spinneret.get(0),
                GTUtility.copyAmountUnsafe(64 * 4 + 6, Materials.Carbon.getDust(64)),
                Materials.Calcium.getDust(2))
            .fluidInputs(
                Materials.Chlorine.getGas(1000 * 34),
                Materials.Hydrogen.getGas(1000 * 230),
                Materials.Oxygen.getGas(1000 * 36),
                Materials.Nitrogen.getGas(1000 * 36))
            .itemOutputs(GTUtility.copyAmountUnsafe(64 + 61, ItemList.WovenKevlar.get(64)))
            .specialValue(11700)
            .eut(RECIPE_UIV)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .duration(20 * 64)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(22),
                Materials.Tin.getDust(45),
                GTUtility.copyAmountUnsafe(64 * 71 + 11, Materials.Carbon.getDust(1)),
                Materials.Nickel.getDust(5),
                Materials.Palladium.getDust(10),
                Materials.Iron.getDust(5),
                Materials.Silicon.getDust(36))
            .fluidInputs(
                Materials.Oxygen.getGas(1000 * 1964),
                Materials.Hydrogen.getGas(1000 * 5292),
                Materials.Chlorine.getGas(1000 * 87),
                Materials.Nitrogen.getGas(1000 * 450))
            .fluidOutputs(Materials.PolyurethaneResin.getFluid(1000 * 45))
            .specialValue(11700)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_UIV)
            .duration(20 * 64)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(6),
                GTUtility.copyAmountUnsafe(0, Materials.Potassiumdichromate.getDust(1)),
                Materials.Carbon.getDust(8))
            .fluidInputs(Materials.Hydrogen.getGas(1000 * 6), Materials.Oxygen.getGas(1000 * 4))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .fluidOutputs(Materials.PhthalicAcid.getFluid(1000))
            .specialValue(9900)
            .eut(RECIPE_UV)
            .duration(5)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(7),
                GTUtility.copyAmountUnsafe(0, Materials.Potassiumdichromate.getDust(1)),
                GTUtility.copyAmountUnsafe(64 * 8, Materials.Carbon.getDust(1)))
            .fluidInputs(Materials.Hydrogen.getGas(1000 * 6 * 64), Materials.Oxygen.getGas(1000 * 4 * 64))
            .fluidOutputs(Materials.PhthalicAcid.getFluid(1000 * 64))
            .specialValue(9900)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_UHV)
            .duration(20)
            .addTo(MT);

        // endregion

        // region Phosphoric Acid
        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(11), Materials.Apatite.getDust(9))
            .fluidInputs(Materials.Water.getFluid(1000 * 5))
            .itemOutputs(Materials.Calcium.getDust(5))
            .fluidOutputs(Materials.PhosphoricAcid.getFluid(1000 * 3), Materials.HydrochloricAcid.getFluid(1000))
            .specialValue(3600)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_MV)
            .duration(20 * 8)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(19),
                GTUtility.copyAmountUnsafe(64 * 9, Materials.Apatite.getDust(1)))
            .fluidInputs(Materials.Water.getFluid(1000 * 5 * 64))
            .itemOutputs(GTUtility.copyAmountUnsafe(64 * 5, Materials.Calcium.getDust(1)))
            .fluidOutputs(
                Materials.PhosphoricAcid.getFluid(1000 * 3 * 64),
                Materials.HydrochloricAcid.getFluid(1000 * 1 * 64))
            .specialValue(4500)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_HV)
            .duration(20 * 8 * 16)
            .addTo(MT);
        // endregion

        // region Silicone
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(11),
                Materials.Sulfur.getDust(1),
                Materials.Silicon.getDust(3),
                Materials.Carbon.getDust(6))
            .fluidInputs(Materials.Hydrogen.getGas(12000), Materials.Water.getFluid(3000))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .fluidOutputs(Materials.RubberSilicone.getMolten(1296))
            .specialValue(400)
            .eut(96)
            .duration(128)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(19),
                Materials.Sulfur.getDust(64),
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Silicon.getDust(1)),
                GTUtility.copyAmountUnsafe(64 * 6, Materials.Carbon.getDust(1)))
            .fluidInputs(Materials.Hydrogen.getGas(12000 * 64), Materials.Water.getFluid(3000 * 64))
            .fluidOutputs(Materials.RubberSilicone.getMolten(1296 * 64))
            .specialValue(800)
            .eut(96)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .duration(128 * 64)
            .addTo(MT);

        // endregion

        // region Polyphenylene sulfide
        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(11), Materials.Sulfur.getDust(1))
            .fluidInputs(Materials.Benzene.getFluid(1000))
            .fluidOutputs(Materials.PolyphenyleneSulfide.getMolten(1500), Materials.Hydrogen.getGas(2000))
            .specialValue(400)
            .eut(RECIPE_HV)
            .duration(128)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(19), Materials.Sulfur.getDust(64))
            .fluidInputs(Materials.Benzene.getFluid(64000))
            .fluidOutputs(Materials.PolyphenyleneSulfide.getMolten(96000), Materials.Hydrogen.getGas(128000))
            .specialValue(800)
            .eut(RECIPE_HV)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .duration(128 * 64)
            .addTo(MT);

        // endregion

        // region Agar
        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(11), Materials.MeatRaw.getDust(8))
            .fluidInputs(
                Materials.SulfuricAcid.getFluid(4000),
                Materials.PhosphoricAcid.getFluid(1000),
                Materials.Water.getFluid(8000))
            .itemOutputs(GTModHandler.getModItem("dreamcraft", "GTNHBioItems", 8, 2))
            .specialValue(9900)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_IV)
            .duration(256)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(19),
                GTUtility.copyAmountUnsafe(64 * 6, Materials.MeatRaw.getDust(64)))
            .fluidInputs(Materials.SulfuricAcid.getFluid(4000 * 64), Materials.PhosphoricAcid.getFluid(64000))
            .itemOutputs(
                GTUtility.copyAmountUnsafe(64 * 8, GTModHandler.getModItem("dreamcraft", "GTNHBioItems", 64, 2)))
            .specialValue(10800)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_IV)
            .duration(256 * 36)
            .addTo(MT);
        // endregion
        // region HSbF6
        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(10), Materials.Antimony.getDust(12))
            .fluidInputs(Materials.Fluorine.getGas(1000 * 5 * 12), Materials.HydrofluoricAcid.getFluid(1000 * 12))
            .fluidOutputs(GGMaterial.fluoroantimonicAcid.getFluidOrGas(1000 * 12))
            .specialValue(9900)
            .eut(RECIPE_UHV)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .duration(16)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(11), Materials.Carbon.getDust(2 * 9))
            .fluidInputs(Materials.Fluorine.getGas(4000 * 9), Materials.Oxygen.getGas(125000))
            .fluidOutputs(Materials.Polytetrafluoroethylene.getMolten(36000))
            .specialValue(1800)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_IV)
            .duration(20 * 12)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(19),
                GTUtility.copyAmountUnsafe(64 * 9, Materials.Carbon.getDust(64)))
            .fluidInputs(Materials.Fluorine.getGas(4000 * 9 * 32), Materials.Oxygen.getGas(125000 * 32))
            .fluidOutputs(Materials.Polytetrafluoroethylene.getMolten(36000 * 32))
            .specialValue(1800)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_IV)
            .duration(20 * 12 * 32)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(5),
                GTUtility.copyAmountUnsafe(64 * 13 + 49, Materials.Carbon.getDust(64)))
            .fluidInputs(Materials.Hydrogen.getGas(1000 * 24 * 36), Materials.Oxygen.getGas(1000 * 4 * 36))
            .itemOutputs(GTUtility.copyAmountUnsafe(64 * 3 + 58, Materials.EpoxidFiberReinforced.getPlates(64)))
            .specialValue(5400)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .eut(RECIPE_HV)
            .duration(128 * 36)
            .addTo(MT);

        // region Bastnasite
        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(3),
                GTOreDictUnificator.get(OrePrefixes.crushed, Materials.Bastnasite, 64), // Bastnasite
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Carbon.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Saltpeter, 59),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Copper, 8),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sodium, 16),
                GTUtility.copyAmountUnsafe(64 * 2, Materials.Sugar.getDust(1)))
            .fluidInputs(
                Materials.Hydrogen.getGas(1000 * 768),
                Materials.Nitrogen.getGas(1000 * 210),
                Materials.Chlorine.getGas(1000 * 260))
            .itemOutputs(
                GTUtility.copyAmountUnsafe(64 + 15, Materials.Cerium.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Neodymium, 42),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Lanthanum, 26),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Holmium, 17),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Samarium, 11),
                WerkstoffLoader.Zirconium.get(OrePrefixes.dust, 11),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Gadolinium, 6),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Terbium, 3),
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Silicon.getDust(1)),
                GTUtility.copyAmountUnsafe(64 * 2 + 35, Materials.Titanium.getDust(1)))
            .fluidOutputs(Materials.Fluorine.getGas(1000 * 12), Materials.Oxygen.getGas(1000 * 150))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UHV)
            .duration(20 * 20)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(3),
                GTOreDictUnificator.get(OrePrefixes.crushed, Materials.Bastnasite, 64), // Bastnasite
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Carbon.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Saltpeter, 59),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Copper, 8),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.SodiumHydroxide, 48),
                GTUtility.copyAmountUnsafe(64 * 2, Materials.Sugar.getDust(1)))
            .fluidInputs(
                Materials.Hydrogen.getGas(1000 * 752),
                Materials.Nitrogen.getGas(1000 * 210),
                Materials.Chlorine.getGas(1000 * 260))
            .itemOutputs(
                GTUtility.copyAmountUnsafe(64 + 15, Materials.Cerium.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Neodymium, 42),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Lanthanum, 26),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Holmium, 17),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Samarium, 11),
                WerkstoffLoader.Zirconium.get(OrePrefixes.dust, 11),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Gadolinium, 6),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Terbium, 3),
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Silicon.getDust(1)),
                GTUtility.copyAmountUnsafe(64 * 2 + 35, Materials.Titanium.getDust(1)))
            .fluidOutputs(Materials.Fluorine.getGas(1000 * 12), Materials.Oxygen.getGas(1000 * 166))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UHV)
            .duration(20 * 20)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(3),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Bastnasite, 64), // Bastnasite
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Carbon.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Saltpeter, 59),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Copper, 8),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sodium, 16),
                GTUtility.copyAmountUnsafe(64 * 2, Materials.Sugar.getDust(1)))
            .fluidInputs(
                Materials.Hydrogen.getGas(1000 * 768),
                Materials.Nitrogen.getGas(1000 * 210),
                Materials.Chlorine.getGas(1000 * 260))
            .itemOutputs(
                GTUtility.copyAmountUnsafe(64 + 15, Materials.Cerium.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Neodymium, 42),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Lanthanum, 26),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Holmium, 17),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Samarium, 11),
                WerkstoffLoader.Zirconium.get(OrePrefixes.dust, 11),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Gadolinium, 6),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Terbium, 3),
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Silicon.getDust(1)),
                GTUtility.copyAmountUnsafe(64 * 2 + 35, Materials.Titanium.getDust(1)))
            .fluidOutputs(Materials.Fluorine.getGas(1000 * 12), Materials.Oxygen.getGas(1000 * 150))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UHV)
            .duration(20 * 20)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(3),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Bastnasite, 64), // Bastnasite
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Carbon.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Saltpeter, 59),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Copper, 8),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.SodiumHydroxide, 48),
                GTUtility.copyAmountUnsafe(64 * 2, Materials.Sugar.getDust(1))

            )
            .fluidInputs(
                Materials.Hydrogen.getGas(1000 * 752),
                Materials.Nitrogen.getGas(1000 * 210),
                Materials.Chlorine.getGas(1000 * 260))
            .itemOutputs(
                GTUtility.copyAmountUnsafe(64 + 15, Materials.Cerium.getDust(1)),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Neodymium, 42),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Lanthanum, 26),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Holmium, 17),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Samarium, 11),
                WerkstoffLoader.Zirconium.get(OrePrefixes.dust, 11),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Gadolinium, 6),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Terbium, 3),
                GTUtility.copyAmountUnsafe(64 * 3, Materials.Silicon.getDust(1)),
                GTUtility.copyAmountUnsafe(64 * 2 + 35, Materials.Titanium.getDust(1)))
            .fluidOutputs(Materials.Fluorine.getGas(1000 * 12), Materials.Oxygen.getGas(1000 * 166))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UHV)
            .duration(20 * 20)
            .addTo(MT);

        // endregion

        // region Living Solder
        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(1),
                ItemList.Circuit_Chip_Biocell.get(64),
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.NetherStar, 8),
                Materials.InfinityCatalyst.getDust(2))
            .fluidInputs(
                Materials.Tin.getPlasma(1000 * 18),
                Materials.Bismuth.getPlasma(1000 * 18),
                FluidRegistry.getFluidStack("cryotheum", 1000 * 4),
                Materials.Neutronium.getMolten(144 * 16))
            .fluidOutputs(MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(144 * 280 * 2))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UEV)
            .duration(20 * 400)
            .addTo(MT);

        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                GTUtility.getIntegratedCircuit(9),
                GTUtility.copyAmountUnsafe(64 * 12, ItemList.Circuit_Chip_Biocell.get(64)),
                GTUtility.copyAmountUnsafe(8 * 12, GTOreDictUnificator.get(OrePrefixes.gem, Materials.NetherStar, 8)),
                Materials.InfinityCatalyst.getDust(2 * 12))
            .fluidInputs(
                Materials.Tin.getPlasma(1000 * 18 * 12),
                Materials.Bismuth.getPlasma(1000 * 18 * 12),
                FluidRegistry.getFluidStack("cryotheum", 1000 * 4 * 12),
                Materials.Neutronium.getMolten(144 * 16 * 12))
            .fluidOutputs(MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(144 * 280 * 2 * 16))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(12600)
            .eut(RECIPE_UEV)
            .duration(20 * 1600)
            .addTo(MT);
        // endregion

        // region Ethyl Cyanoacrylate Super Glue
        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(GTUtility.getIntegratedCircuit(15), Materials.Carbon.getDust(41))
            .fluidInputs(Materials.Hydrogen.getGas(1000 * 142), Materials.Oxygen.getGas(1000 * 41))
            .fluidOutputs(MaterialMisc.ETHYL_CYANOACRYLATE.getFluidStack(1000 * 10))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UEV)
            .duration(20 * 8)
            .addTo(MT);
        // endregion
        GTValues.RA.stdBuilder()
            .itemInputsUnsafe(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Strontium, 1), 0),
                GTOreDictUnificator.get(OrePrefixes.shard, Materials.Prismarine, 8),
                Materials.Carbon.getDust(24),
                Materials.CrystallineAlloy.getDust(4))
            .fluidInputs(
                Materials.Hydrogen.getGas(36000),
                Materials.Oxygen.getGas(24000),
                Materials.Boron.getPlasma(800),
                Materials.Nitrogen.getGas(28000))
            .fluidOutputs(Materials.PrismaticAcid.getFluid(32000))
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .specialValue(11700)
            .eut(RECIPE_UHV)
            .duration(20 * 120)
            .addTo(MT);

    }

    public static void loadRecipePostInit() {
        // region H2O2
        RecipeMap<RecipeMapBackend> MT = MTRecipeMaps.MTChemicalTwisterRecipes;
        GTValues.RA.stdBuilder()
            .itemInputs(GTUtility.getIntegratedCircuit(16))
            .fluidInputs(Materials.Hydrogen.getGas(1000 * 128), Materials.Oxygen.getGas(1000 * 128))
            .fluidOutputs(getFluidStack("fluid.hydrogenperoxide", 1000 * 64))
            .specialValue(10800)
            .eut(RECIPE_UHV)
            .metadata(MTRecipeMaps.CHEMICAL_TWISTER_STRUCTURE_LEVEL, 1)
            .duration(32)
            .addTo(MT);
    }
}
