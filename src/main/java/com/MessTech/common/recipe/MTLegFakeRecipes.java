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
 * Fills the Large Essentia Generator's fake NEI pool ({@link MTRecipeMaps#legFakeRecipes}) with one page per burnable
 * aspect and auxiliary fluid option. A page shows, in this order:
 *
 * <ul>
 * <li>the aspect icon (an Aspect Recipe Index item carrying that aspect);</li>
 * <li>the upgrade item its category needs - or nothing for the NORMAL aspects that burn without an upgrade;</li>
 * <li>one auxiliary fluid of that category, exactly the list {@code MTLargeEssentiaGenerator} tries in order (see
 * {@code airEssentia()} and the other per-category methods) - categories without auxiliary fluids show none;</li>
 * <li>three NEI text lines: the aspect's base EU value, the auxiliary fluid's multiplier (a fixed value, or the
 * formula the generator uses when it depends on other values) and the EU-per-essentia formula. No pre-computed
 * result: the actual output also depends on the player's diffusion cell stable points.</li>
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
            addAspectPages(aspect);
        }
    }

    private static void addAspectPages(Aspect aspect) {
        int type = MTEssentiaEnergyData.getAspectTypeIndex(aspect);
        long basePower = MTEssentiaEnergyData.getAspectFuelValue(aspect);

        // NORMAL aspects burn without an upgrade item; every other category has exactly one upgrade item.
        ItemStack[] items = type == MTEssentiaEnergyData.TYPE_NORMAL ? new ItemStack[] { aspectItem(aspect) }
            : new ItemStack[] { aspectItem(aspect), MTEssentiaUpgradeItem.getStack(type) };

        String baseLine = StatCollector.translateToLocalFormatted("mt.recipe.leg.aspect_power", basePower);
        String formulaLine = StatCollector.translateToLocal("mt.recipe.leg.formula");
        for (FluidOption option : optionsForType(type, basePower)) {
            if (option.fluid != null && option.fluid.getFluid() == null) continue;
            String multiplierLine = StatCollector
                .translateToLocalFormatted("mt.recipe.leg.multiplier", option.multiplier);
            addFakePage(items, option.fluid, new String[] { baseLine, multiplierLine, formulaLine });
        }
    }

    /**
     * The burn options of one aspect category: the auxiliary fluids in the order the generator tries them (the first
     * one is the best boost) plus, for the categories where the fluid is optional, the no-fluid fallback. The lists
     * mirror the {@code deplete(...)} calls and multiplier constants of {@code MTLargeEssentiaGenerator}'s
     * per-category methods; categories that cannot burn without a fluid have no fallback entry.
     */
    private static List<FluidOption> optionsForType(int type, long basePower) {
        return switch (type) {
            case MTEssentiaEnergyData.TYPE_AIR -> List.of(
                new FluidOption(Materials.LiquidAir.getFluid(FLUID_DISPLAY_AMOUNT), "×1.5"),
                new FluidOption(Materials.Air.getGas(FLUID_DISPLAY_AMOUNT), "×1"));
            case MTEssentiaEnergyData.TYPE_THERMAL -> List.of(
                new FluidOption(Materials.SuperCoolant.getFluid(FLUID_DISPLAY_AMOUNT), "×9"),
                new FluidOption(FluidRegistry.getFluidStack("cryotheum", FLUID_DISPLAY_AMOUNT), "×5"),
                new FluidOption(FluidRegistry.getFluidStack("ic2coolant", FLUID_DISPLAY_AMOUNT), "×1.5"),
                new FluidOption(FluidRegistry.getFluidStack("ice", FLUID_DISPLAY_AMOUNT), "×1.2"),
                new FluidOption(FluidRegistry.getFluidStack("ic2distilledwater", FLUID_DISPLAY_AMOUNT), "×1"),
                new FluidOption(Materials.Water.getFluid(FLUID_DISPLAY_AMOUNT), "×0.5"));
            case MTEssentiaEnergyData.TYPE_UNSTABLE -> List.of(
                new FluidOption(WerkstoffLoader.Xenon.getFluidOrGas(FLUID_DISPLAY_AMOUNT), "×4"),
                new FluidOption(WerkstoffLoader.Krypton.getFluidOrGas(FLUID_DISPLAY_AMOUNT), "×3"),
                new FluidOption(Materials.Argon.getFluid(FLUID_DISPLAY_AMOUNT), "×2.5"),
                new FluidOption(WerkstoffLoader.Neon.getFluidOrGas(FLUID_DISPLAY_AMOUNT), "×2.2"),
                new FluidOption(Materials.Helium.getFluid(FLUID_DISPLAY_AMOUNT), "×2"),
                new FluidOption(Materials.Nitrogen.getFluid(FLUID_DISPLAY_AMOUNT), "×1"));
            case MTEssentiaEnergyData.TYPE_VICTUS -> List.of(
                new FluidOption(FluidRegistry.getFluidStack("xpjuice", FLUID_DISPLAY_AMOUNT), "×2"),
                new FluidOption(FluidRegistry.getFluidStack("lifeessence", FLUID_DISPLAY_AMOUNT), "×6"),
                new FluidOption(null, "×1"));
            case MTEssentiaEnergyData.TYPE_TAINTED -> List.of(
                new FluidOption(FluidRegistry.getFluidStack("fluidpure", FLUID_DISPLAY_AMOUNT), "×60"),
                new FluidOption(
                    FluidRegistry.getFluidStack("fluiddeath", FLUID_DISPLAY_AMOUNT),
                    StatCollector.translateToLocalFormatted("mt.recipe.leg.multiplier.tainted_death", basePower)),
                new FluidOption(null, "×1"));
            case MTEssentiaEnergyData.TYPE_MECHANIC -> List
                .of(new FluidOption(Materials.Lubricant.getFluid(FLUID_DISPLAY_AMOUNT), "×1"));
            case MTEssentiaEnergyData.TYPE_SPRITE -> List.of(
                new FluidOption(
                    FluidRegistry.getFluidStack("witchery:fluidspirit", FLUID_DISPLAY_AMOUNT),
                    StatCollector.translateToLocal("mt.recipe.leg.multiplier.spirit_spirit")),
                new FluidOption(
                    FluidRegistry.getFluidStack("witchery:hollowtears", FLUID_DISPLAY_AMOUNT),
                    StatCollector.translateToLocal("mt.recipe.leg.multiplier.spirit_tears")),
                new FluidOption(null, "×1"));
            case MTEssentiaEnergyData.TYPE_RADIATION -> List.of(
                new FluidOption(Materials.Caesium.getMolten(FLUID_DISPLAY_AMOUNT), "×2"),
                new FluidOption(Materials.Uranium235.getMolten(FLUID_DISPLAY_AMOUNT), "×3"),
                new FluidOption(Materials.Naquadah.getMolten(FLUID_DISPLAY_AMOUNT), "×4"),
                new FluidOption(GGMaterial.atomicSeparationCatalyst.getMolten(FLUID_DISPLAY_AMOUNT), "×16"),
                new FluidOption(null, "×1"));
            case MTEssentiaEnergyData.TYPE_ELECTRIC -> List
                .of(new FluidOption(null, StatCollector.translateToLocal("mt.recipe.leg.multiplier.electric")));
            // NORMAL burns without any fluid at all.
            default -> List.of(new FluidOption(null, "×1"));
        };
    }

    /** @return The Aspect Recipe Index item showing {@code aspect} as its icon, used as the NEI page input. */
    private static ItemStack aspectItem(Aspect aspect) {
        ItemStack stack = new ItemStack(ModItems.itemAspect);
        ItemAspect.setAspect(stack, aspect);
        return stack;
    }

    private static void addFakePage(ItemStack[] items, FluidStack fluid, String[] neiDescription) {
        GTRecipeBuilder builder = GTValues.RA.stdBuilder()
            .itemInputs(items)
            // Display-only pages, like GT's own fuel pages (FuelBackend): no duration / EU/t line in NEI.
            .duration(0)
            .eut(0)
            // The pages share their input items (same upgrade item, aspect item with a different NBT) on purpose.
            .ignoreCollision()
            .fake();
        if (fluid != null) builder.fluidInputs(fluid);
        builder.setNEIDesc(neiDescription);
        builder.addTo(MTRecipeMaps.legFakeRecipes);
    }

    /** One burn option of a category: an auxiliary fluid (or none) and how the generator's multiplier is written. */
    private static final class FluidOption {

        private final FluidStack fluid;
        private final String multiplier;

        private FluidOption(FluidStack fluid, String multiplier) {
            this.fluid = fluid;
            this.multiplier = multiplier;
        }
    }
}
