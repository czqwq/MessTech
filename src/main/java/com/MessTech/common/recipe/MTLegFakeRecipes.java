package com.MessTech.common.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.thaumcraft.MTEssentiaEnergyData;
import com.MessTech.common.thaumcraft.MTEssentiaUpgradeItem;
import com.MessTech.init.MessTech;
import com.gtnewhorizons.aspectrecipeindex.ModItems;
import com.gtnewhorizons.aspectrecipeindex.common.items.ItemAspect;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.Loader;
import goodgenerator.items.GGMaterial;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTRecipeBuilder;
import thaumcraft.api.aspects.Aspect;

/**
 * Fills the Large Essentia Generator's fake NEI pool ({@link MTRecipeMaps#legFakeRecipes}) with two page kinds:
 *
 * <ul>
 * <li>one page per upgrade item and auxiliary fluid of its aspect category - the upgrade item is installed by
 * right-clicking the controller and is not consumed, and the fluids are the ones {@code MTLargeEssentiaGenerator}
 * tries in order (see {@code airEssentia()} and the other per-category methods);</li>
 * <li>one page per burnable aspect with its base EU value from {@code MTEssentiaEnergyData} (only the base value: the
 * actual output also depends on the auxiliary fluid and the diffusion cell stable points).</li>
 * </ul>
 *
 * Fake recipes only: they are NEI display pages and can never be found by a machine.
 */
public final class MTLegFakeRecipes {

    /**
     * Display amount of the auxiliary fluids. There is no single real amount per page: the generator consumes
     * {@code (int) consumeCeo * categoryFactor} per essentia unit and every aspect has its own {@code consumeCeo}, so 1
     * L
     * only says "this fluid is one of the options".
     */
    private static final int FLUID_DISPLAY_AMOUNT = 1;

    private MTLegFakeRecipes() {}

    public static void populate() {
        addUpgradePages();
        addAspectPages();
    }

    private static void addUpgradePages() {
        // The order is the order the generator tries the fluids in: the first one is the best boost.
        addUpgrade(
            MTEssentiaEnergyData.TYPE_AIR,
            Materials.LiquidAir.getFluid(FLUID_DISPLAY_AMOUNT),
            Materials.Air.getGas(FLUID_DISPLAY_AMOUNT));
        addUpgrade(
            MTEssentiaEnergyData.TYPE_THERMAL,
            Materials.SuperCoolant.getFluid(FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("cryotheum", FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("ic2coolant", FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("ice", FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("ic2distilledwater", FLUID_DISPLAY_AMOUNT),
            Materials.Water.getFluid(FLUID_DISPLAY_AMOUNT));
        addUpgrade(
            MTEssentiaEnergyData.TYPE_UNSTABLE,
            WerkstoffLoader.Xenon.getFluidOrGas(FLUID_DISPLAY_AMOUNT),
            WerkstoffLoader.Krypton.getFluidOrGas(FLUID_DISPLAY_AMOUNT),
            Materials.Argon.getFluid(FLUID_DISPLAY_AMOUNT),
            WerkstoffLoader.Neon.getFluidOrGas(FLUID_DISPLAY_AMOUNT),
            Materials.Helium.getFluid(FLUID_DISPLAY_AMOUNT),
            Materials.Nitrogen.getFluid(FLUID_DISPLAY_AMOUNT));
        addUpgrade(
            MTEssentiaEnergyData.TYPE_VICTUS,
            FluidRegistry.getFluidStack("xpjuice", FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("lifeessence", FLUID_DISPLAY_AMOUNT));
        addUpgrade(
            MTEssentiaEnergyData.TYPE_TAINTED,
            FluidRegistry.getFluidStack("fluidpure", FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("fluiddeath", FLUID_DISPLAY_AMOUNT));
        addUpgrade(MTEssentiaEnergyData.TYPE_MECHANIC, Materials.Lubricant.getFluid(FLUID_DISPLAY_AMOUNT));
        addUpgrade(
            MTEssentiaEnergyData.TYPE_SPRITE,
            FluidRegistry.getFluidStack("witchery:fluidspirit", FLUID_DISPLAY_AMOUNT),
            FluidRegistry.getFluidStack("witchery:hollowtears", FLUID_DISPLAY_AMOUNT));
        addUpgrade(
            MTEssentiaEnergyData.TYPE_RADIATION,
            Materials.Caesium.getMolten(FLUID_DISPLAY_AMOUNT),
            Materials.Uranium235.getMolten(FLUID_DISPLAY_AMOUNT),
            Materials.Naquadah.getMolten(FLUID_DISPLAY_AMOUNT),
            GGMaterial.atomicSeparationCatalyst.getMolten(FLUID_DISPLAY_AMOUNT));
        // ELECTRIC needs no fluid.
        addUpgrade(MTEssentiaEnergyData.TYPE_ELECTRIC);
    }

    private static void addUpgrade(int upgradeType, FluidStack... fluids) {
        ItemStack upgrade = MTEssentiaUpgradeItem.getStack(upgradeType);
        if (fluids.length == 0) {
            addFakePage(upgrade, null, null);
            return;
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null) continue;
            addFakePage(upgrade, fluid, null);
        }
    }

    private static void addAspectPages() {
        if (!Loader.isModLoaded("aspectrecipeindex")) {
            MessTech.MT_LOG.info("AspectRecipeIndex is missing, skipping the LEG aspect NEI pages");
            return;
        }
        List<Aspect> aspects = new ArrayList<>(Aspect.aspects.values());
        aspects
            .removeIf(aspect -> MTEssentiaEnergyData.getAspectTypeIndex(aspect) == MTEssentiaEnergyData.TYPE_INVALID);
        aspects.sort(
            Comparator.comparingInt(MTEssentiaEnergyData::getAspectTypeIndex)
                .thenComparing(Aspect::getTag));
        for (Aspect aspect : aspects) {
            long basePower = MTEssentiaEnergyData.getAspectFuelValue(aspect);
            addFakePage(
                aspectItem(aspect),
                null,
                StatCollector.translateToLocalFormatted("mt.recipe.leg.aspect_power", basePower));
        }
    }

    /** @return The Aspect Recipe Index item showing {@code aspect} as its icon, used as the NEI page input. */
    private static ItemStack aspectItem(Aspect aspect) {
        ItemStack stack = new ItemStack(ModItems.itemAspect);
        ItemAspect.setAspect(stack, aspect);
        return stack;
    }

    private static void addFakePage(ItemStack item, FluidStack fluid, String neiDescription) {
        GTRecipeBuilder builder = GTValues.RA.stdBuilder()
            .itemInputs(item)
            // Display-only pages, like GT's own fuel pages (FuelBackend): no duration / EU/t line in NEI.
            .duration(0)
            .eut(0)
            // The pages share their input item (same upgrade item, aspect item with a different NBT) on purpose.
            .ignoreCollision()
            .fake();
        if (fluid != null) builder.fluidInputs(fluid);
        if (neiDescription != null) builder.setNEIDesc(neiDescription);
        builder.addTo(MTRecipeMaps.legFakeRecipes);
    }
}
