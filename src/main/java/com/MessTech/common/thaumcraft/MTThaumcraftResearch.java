package com.MessTech.common.thaumcraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.MessTech.common.items.MTItemList;
import com.MessTech.init.MessTech;

import bartworks.common.loaders.ItemRegistry;
import bartworks.system.material.WerkstoffLoader;
import goodgenerator.util.ItemRefer;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TCAspects;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import ic2.core.Ic2Items;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.config.ConfigItems;

/**
 * The "混乱科技 / MessTech" Thaumonomicon tab: the ported LEG research tree (the generator, the diffusion cells and the
 * nine upgrade categories) with the original infusion / arcane recipes, registered through the Thaumcraft API directly
 * so the tab and its texts are ours.
 *
 * <p>
 * The recipes are deliberately duplicated from GT5U's Large Essentia Smeltery research (which still owns the cells and
 * the magic casing there): a player who only knows this tab has to be able to craft the LEG structure parts without
 * researching the smeltery first.
 */
public final class MTThaumcraftResearch {

    public static final String CATEGORY = "messtech";
    /** Icon of the category tab: the dynamically rendered Chaos Matter gear (see the client tick handler). */
    public static final ResourceLocation TAB_ICON = new ResourceLocation("messtech", "textures/misc/research_tab.png");
    public static final ResourceLocation BACKGROUND = new ResourceLocation(
        "thaumcraft",
        "textures/gui/gui_researchback.png");

    private static final String LEG = "messtech.leg";
    private static final String CELLS = "messtech.cells";
    private static final String UPGRADE_BLANK = "messtech.upgrade.blank";
    private static final String UPGRADE_AIR = "messtech.upgrade.air";
    private static final String UPGRADE_THERMAL = "messtech.upgrade.thermal";
    private static final String UPGRADE_UNSTABLE = "messtech.upgrade.unstable";
    private static final String UPGRADE_VICTUS = "messtech.upgrade.victus";
    private static final String UPGRADE_TAINTED = "messtech.upgrade.tainted";
    private static final String UPGRADE_MECHANICS = "messtech.upgrade.mechanics";
    private static final String UPGRADE_SPRITE = "messtech.upgrade.sprite";
    private static final String UPGRADE_RADIATION = "messtech.upgrade.radiation";
    private static final String UPGRADE_ELECTRIC = "messtech.upgrade.electric";

    private MTThaumcraftResearch() {}

    /** Creates the tab; parents from other tabs (INFUSION) only resolve later, which is fine. */
    public static void registerCategory() {
        ResearchCategories.registerCategory(CATEGORY, TAB_ICON, BACKGROUND);
    }

    /** Adds every research of the tab and its recipes. Called once, after the machines exist. */
    public static void registerResearches() {
        registerLeg();
        registerCells();
        registerUpgrades();
    }

    private static Aspect a(TCAspects aspect) {
        return aspect.getAspect();
    }

    /**
     * Infusion recipes crash on a null pedestal item ({@code InfusionRecipe#matches} reads the component's damage), and
     * some optional components may not exist in a pack; drop those and keep the recipe usable.
     */
    private static ItemStack[] ingredients(ItemStack... items) {
        List<ItemStack> sanitized = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            if (item == null) {
                MessTech.MT_LOG.warn("Missing component in a MessTech Thaumcraft infusion recipe, skipping it");
            } else {
                sanitized.add(item);
            }
        }
        return sanitized.toArray(new ItemStack[0]);
    }

    /** The infused materials have no screws in GT; fall back to the Thaumium screw so the arcane recipe stays valid. */
    private static ItemStack screw(Materials material) {
        ItemStack stack = GTOreDictUnificator.get(OrePrefixes.screw, material, 1);
        if (stack != null) return stack;
        MessTech.MT_LOG.warn("Material {} has no screw, using Thaumium", material);
        ItemStack fallback = GTOreDictUnificator.get(OrePrefixes.screw, Materials.Thaumium, 1);
        return fallback == null ? new ItemStack(Items.iron_ingot) : fallback;
    }

    // region Research entries
    private static void registerLeg() {
        new ResearchItem(
            LEG,
            CATEGORY,
            new AspectList().add(a(TCAspects.MACHINA), 10)
                .add(a(TCAspects.PRAECANTATIO), 10)
                .add(a(TCAspects.POTENTIA), 10)
                .add(a(TCAspects.ELECTRUM), 10)
                .add(a(TCAspects.PERMUTATIO), 10),
            0,
            0,
            3,
            MTItemList.MTLargeEssentiaGenerator.get(1))
                .setPages(
                    new ResearchPage("tc.research_text." + LEG + ".0"),
                    new ResearchPage(
                        ThaumcraftApi.addInfusionCraftingRecipe(
                            LEG,
                            MTItemList.MTEssentiaHatch.get(1),
                            6,
                            new AspectList().add(Aspect.WATER, 128)
                                .add(Aspect.VOID, 64)
                                .add(Aspect.EXCHANGE, 32)
                                .add(Aspect.MIND, 32),
                            ItemList.Hatch_Input_HV.get(1),
                            ingredients(
                                new ItemStack(ConfigBlocks.blockJar, 1),
                                ItemRefer.Magic_Casing.get(1),
                                new ItemStack(ConfigBlocks.blockTube, 1),
                                ItemList.Electric_Pump_MV.get(1L)))),
                    new ResearchPage(
                        ThaumcraftApi.addInfusionCraftingRecipe(
                            LEG,
                            ItemRefer.Essentia_Cell_T1.get(1),
                            4,
                            new AspectList().add(Aspect.WATER, 32)
                                .add(Aspect.VOID, 32)
                                .add(Aspect.EXCHANGE, 16),
                            GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Thaumium, 1),
                            ingredients(
                                GTOreDictUnificator.get(OrePrefixes.pipeTiny, Materials.Titanium, 1),
                                GTOreDictUnificator.get(OrePrefixes.plate, Materials.ElectricalSteel, 1),
                                GTOreDictUnificator.get(OrePrefixes.plate, Materials.ElectricalSteel, 1),
                                ItemList.Electric_Pump_EV.get(1L),
                                new ItemStack(ConfigBlocks.blockTube, 1, 4),
                                new ItemStack(ConfigBlocks.blockCosmeticOpaque, 1, 2)))),
                    new ResearchPage(
                        ThaumcraftApi.addArcaneCraftingRecipe(
                            LEG,
                            ItemRefer.Magic_Casing.get(1),
                            new AspectList().add(Aspect.AIR, 50)
                                .add(Aspect.FIRE, 50)
                                .add(Aspect.ORDER, 50),
                            "SCS",
                            "GAG",
                            "SCS",
                            'S',
                            new ItemStack(ConfigItems.itemResource, 1, 14),
                            'C',
                            GTOreDictUnificator.get(OrePrefixes.itemCasing, Materials.Thaumium, 1),
                            'G',
                            Ic2Items.reinforcedGlass,
                            'A',
                            Ic2Items.advancedMachine)),
                    new ResearchPage("tc.research_text." + LEG + ".1"),
                    new ResearchPage("tc.research_text." + LEG + ".2"),
                    new ResearchPage("tc.research_text." + LEG + ".3"))
                .setParents("INFUSION")
                .setConcealed()
                .registerResearchItem();
    }

    private static void registerCells() {
        new ResearchItem(
            CELLS,
            CATEGORY,
            new AspectList().add(Aspect.WATER, 10)
                .add(Aspect.VOID, 10)
                .add(Aspect.ORDER, 10)
                .add(a(TCAspects.PRAECANTATIO), 10)
                .add(Aspect.EXCHANGE, 10),
            4,
            0,
            2,
            ItemRefer.Essentia_Cell_T4.get(1))
                .setPages(
                    new ResearchPage("tc.research_text." + CELLS + ".0"),
                    new ResearchPage(
                        ThaumcraftApi.addInfusionCraftingRecipe(
                            CELLS,
                            ItemRefer.Essentia_Cell_T2.get(1),
                            6,
                            new AspectList().add(Aspect.WATER, 64)
                                .add(Aspect.VOID, 64)
                                .add(Aspect.EXCHANGE, 32),
                            ItemRefer.Essentia_Cell_T1.get(1),
                            ingredients(
                                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Iridium, 1),
                                ItemList.Electric_Pump_IV.get(1L),
                                ItemList.QuantumStar.get(1L),
                                new ItemStack(ConfigBlocks.blockMetalDevice, 1, 3),
                                ItemList.Reactor_Coolant_Sp_1.get(1L),
                                GTOreDictUnificator.get(OrePrefixes.bolt, Materials.Plutonium241, 1)))),
                    new ResearchPage(
                        ThaumcraftApi.addInfusionCraftingRecipe(
                            CELLS,
                            ItemRefer.Essentia_Cell_T3.get(1),
                            8,
                            new AspectList().add(Aspect.WATER, 128)
                                .add(Aspect.VOID, 128)
                                .add(Aspect.EXCHANGE, 64),
                            ItemRefer.Essentia_Cell_T2.get(1),
                            ingredients(
                                ItemList.Field_Generator_IV.get(1L),
                                ItemList.Electric_Pump_LuV.get(1L),
                                new ItemStack(ConfigItems.itemResource, 1, 14),
                                Ic2Items.fluidregulator,
                                new ItemStack(ConfigBlocks.blockJar, 1, 0),
                                GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.Ichorium, 1)))),
                    new ResearchPage(
                        ThaumcraftApi.addInfusionCraftingRecipe(
                            CELLS,
                            ItemRefer.Essentia_Cell_T4.get(1),
                            10,
                            new AspectList().add(Aspect.WATER, 256)
                                .add(Aspect.VOID, 256)
                                .add(Aspect.EXCHANGE, 128),
                            ItemRefer.Essentia_Cell_T3.get(1),
                            ingredients(
                                GTOreDictUnificator.get(OrePrefixes.ring, Materials.Europium, 1),
                                ItemList.Emitter_LuV.get(1L),
                                new ItemStack(ItemRegistry.bw_realglas, 1, 4),
                                GTOreDictUnificator.get(OrePrefixes.plate, Materials.StellarAlloy, 1),
                                GTOreDictUnificator.get(OrePrefixes.cellPlasma, Materials.Helium, 1),
                                new ItemStack(ConfigItems.itemShard, 1, 6)))))
                .setParents(LEG)
                .setConcealed()
                .registerResearchItem();
    }

    private static void registerUpgrades() {
        // The empty upgrade: arcane crafting, the ingredient of all nine category upgrades.
        ItemStack broad = new ItemStack(ConfigBlocks.blockCosmeticSolid, 1, 6);
        if (Mods.NewHorizonsCoreMod.isModLoaded()) {
            ItemStack slate = GTModHandler.getModItem(Mods.NewHorizonsCoreMod.ID, "item.ArcaneSlate", 1);
            if (slate != null) broad = slate;
        }
        new ResearchItem(
            UPGRADE_BLANK,
            CATEGORY,
            new AspectList().add(Aspect.AURA, 10)
                .add(Aspect.EXCHANGE, 10)
                .add(Aspect.TOOL, 10)
                .add(a(TCAspects.ELECTRUM), 10),
            0,
            3,
            2,
            MTEssentiaUpgradeItem.getStack(0))
                .setPages(
                    new ResearchPage("tc.research_text." + UPGRADE_BLANK + ".0"),
                    new ResearchPage(
                        ThaumcraftApi.addArcaneCraftingRecipe(
                            UPGRADE_BLANK,
                            MTEssentiaUpgradeItem.getStack(0),
                            new AspectList().add(Aspect.AIR, 80)
                                .add(Aspect.ENTROPY, 50)
                                .add(Aspect.ORDER, 50)
                                .add(Aspect.WATER, 80),
                            "AMB",
                            "CZD",
                            "EIF",
                            'A',
                            screw(Materials.InfusedAir),
                            'B',
                            screw(Materials.InfusedEarth),
                            'C',
                            screw(Materials.InfusedFire),
                            'D',
                            screw(Materials.InfusedWater),
                            'E',
                            screw(Materials.InfusedOrder),
                            'F',
                            screw(Materials.InfusedEntropy),
                            'M',
                            new ItemStack(ConfigItems.itemResource, 1, 10),
                            'Z',
                            broad,
                            'I',
                            GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.PulsatingIron, 1))))
                .setParents(LEG)
                .setConcealed()
                .registerResearchItem();

        registerUpgrade(
            UPGRADE_AIR,
            1,
            1,
            4,
            a(TCAspects.AER),
            new AspectList().add(Aspect.AIR, 128)
                .add(Aspect.FLIGHT, 128)
                .add(Aspect.MOTION, 128)
                .add(Aspect.AURA, 128),
            5,
            new ItemStack[] { GTOreDictUnificator.get(OrePrefixes.cell, Materials.LiquidAir, 1),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Hydrogen, 1),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Helium, 1),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Argon, 1),
                WerkstoffLoader.Neon.get(OrePrefixes.cell, 1), WerkstoffLoader.Krypton.get(OrePrefixes.cell, 1) },
            2);
        registerUpgrade(
            UPGRADE_THERMAL,
            2,
            2,
            4,
            a(TCAspects.IGNIS),
            new AspectList().add(Aspect.FIRE, 1024),
            5,
            new ItemStack[] { GTOreDictUnificator.get(OrePrefixes.lens, Materials.Firestone, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Netherrack, 1), Ic2Items.reactorPlatingHeat,
                ItemList.Casing_Coil_Nichrome.get(1), new ItemStack(ConfigItems.itemResource, 1, 1),
                new ItemStack(ConfigItems.itemResource, 1, 0) },
            3);
        registerUpgrade(
            UPGRADE_UNSTABLE,
            3,
            3,
            4,
            a(TCAspects.PERDITIO),
            new AspectList().add(Aspect.ENTROPY, 128)
                .add(Aspect.VOID, 128)
                .add(Aspect.POISON, 128)
                .add(Aspect.WEAPON, 128),
            6,
            new ItemStack[] { GTOreDictUnificator.get(OrePrefixes.cell, Materials.GasolinePremium, 1),
                GTOreDictUnificator.get(OrePrefixes.dust, Materials.Unstable, 1),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.Void, 1),
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.InfusedEntropy, 1), Ic2Items.industrialTnt,
                extraUtilitiesTrashcan() },
            3);
        registerUpgrade(
            UPGRADE_VICTUS,
            4,
            4,
            4,
            a(TCAspects.VICTUS),
            new AspectList().add(Aspect.LIFE, 128)
                .add(Aspect.SOUL, 128)
                .add(Aspect.HEAL, 128)
                .add(Aspect.FLESH, 128)
                .add(Aspect.MAN, 128),
            5,
            new ItemStack[] { meatDust(), ItemList.Food_Dough_Sugar.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Calcium, 1), new ItemStack(Items.rotten_flesh),
                new ItemStack(ConfigItems.itemResource, 1, 4), new ItemStack(ConfigBlocks.blockMetalDevice, 1, 8) },
            3);
        registerUpgrade(
            UPGRADE_TAINTED,
            5,
            5,
            4,
            a(TCAspects.MORTUUS),
            new AspectList().add(Aspect.DEATH, 128)
                .add(Aspect.ELDRITCH, 128)
                .add(Aspect.UNDEAD, 128)
                .add(Aspect.TAINT, 128),
            7,
            new ItemStack[] { new ItemStack(ConfigBlocks.blockTaintFibres, 1, 0),
                new ItemStack(ConfigBlocks.blockTaintFibres, 1, 2), new ItemStack(ConfigItems.itemResource, 1, 11),
                GTOreDictUnificator.get(OrePrefixes.spring, Materials.NaquadahEnriched, 1),
                GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.EndSteel, 1), new ItemStack(Blocks.beacon) },
            4);
        registerUpgrade(
            UPGRADE_MECHANICS,
            6,
            6,
            4,
            a(TCAspects.MACHINA),
            new AspectList().add(Aspect.TRAVEL, 128)
                .add(Aspect.GREED, 128)
                .add(Aspect.MECHANISM, 128),
            5,
            new ItemStack[] { new ItemStack(ConfigBlocks.blockTube, 1, 4), new ItemStack(ConfigBlocks.blockTube, 1, 2),
                GTOreDictUnificator.get(OrePrefixes.rotor, Materials.VividAlloy, 1),
                GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Polybenzimidazole, 1),
                ItemList.Electric_Motor_IV.get(1), ItemList.Electric_Pump_IV.get(1) },
            3);
        registerUpgrade(
            UPGRADE_SPRITE,
            7,
            7,
            4,
            a(TCAspects.COGNITIO),
            new AspectList().add(Aspect.MIND, 128)
                .add(Aspect.SENSES, 128)
                .add(Aspect.GREED, 128)
                .add(a(TCAspects.STRONTIO), 128)
                .add(a(TCAspects.NEBRISUM), 128),
            5,
            new ItemStack[] { new ItemStack(ConfigBlocks.blockJar, 1, 1),
                GTOreDictUnificator.get(OrePrefixes.food, Materials.Cheese, 1),
                GTOreDictUnificator.get(OrePrefixes.rotor, Materials.Shadow, 1),
                GTOreDictUnificator.get(OrePrefixes.spring, Materials.FierySteel, 1),
                GTOreDictUnificator.get(OrePrefixes.cell, Materials.Milk, 1), ItemList.Machine_EV_Scanner.get(1) },
            3);
        registerUpgrade(
            UPGRADE_RADIATION,
            8,
            8,
            4,
            a(TCAspects.RADIO),
            new AspectList().add(a(TCAspects.RADIO), 1024),
            8,
            new ItemStack[] { ItemRefer.High_Density_Plutonium.get(1), ItemRefer.High_Density_Uranium.get(1),
                ItemRefer.High_Density_Thorium.get(1), Ic2Items.UranFuel, Ic2Items.MOXFuel,
                WerkstoffLoader.Tiberium.get(OrePrefixes.gem, 1) },
            3);
        registerUpgrade(
            UPGRADE_ELECTRIC,
            9,
            9,
            6,
            a(TCAspects.ELECTRUM),
            new AspectList().add(Aspect.ENERGY, 32768),
            10,
            new ItemStack[] { GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorMV, 1),
                GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorHV, 1),
                GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorEV, 1),
                GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorIV, 1),
                GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorLuV, 1),
                GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorZPM, 1) },
            2,
            UPGRADE_AIR,
            UPGRADE_THERMAL,
            UPGRADE_UNSTABLE,
            UPGRADE_VICTUS,
            UPGRADE_TAINTED,
            UPGRADE_MECHANICS,
            UPGRADE_SPRITE,
            UPGRADE_RADIATION);
    }

    private static void registerUpgrade(String key, int upgrade, int column, int row, Aspect tag,
        AspectList infusionAspects, int instability, ItemStack[] sides, int pages, String... parents) {
        ResearchItem research = new ResearchItem(
            key,
            CATEGORY,
            new AspectList().add(tag, 10)
                .add(Aspect.EXCHANGE, 10)
                .add(Aspect.TOOL, 10)
                .add(a(TCAspects.ELECTRUM), 10),
            column,
            row,
            1,
            MTEssentiaUpgradeItem.getStack(upgrade));
        ResearchPage[] researchPages = new ResearchPage[1 + pages];
        researchPages[0] = new ResearchPage("tc.research_text." + key + ".0");
        researchPages[1] = new ResearchPage(
            ThaumcraftApi.addInfusionCraftingRecipe(
                key,
                MTEssentiaUpgradeItem.getStack(upgrade),
                instability,
                infusionAspects,
                MTEssentiaUpgradeItem.getStack(0),
                ingredients(sides)));
        for (int i = 0; i < pages - 1; i++) {
            researchPages[i + 2] = new ResearchPage("tc.research_text." + key + "." + (i + 1));
        }
        research.setPages(researchPages);
        research.setParents(parents.length == 0 ? new String[] { UPGRADE_BLANK } : parents);
        research.setConcealed();
        research.registerResearchItem();
    }

    /** Extra Utilities is optional; fall back to a GT field generator so the recipe stays craftable. */
    private static ItemStack extraUtilitiesTrashcan() {
        if (Mods.ExtraUtilities.isModLoaded()) {
            ItemStack stack = GTModHandler.getModItem(Mods.ExtraUtilities.ID, "trashcan", 1);
            if (stack != null) return stack;
        }
        return ItemList.Field_Generator_MV.get(1);
    }

    private static ItemStack meatDust() {
        ItemStack dust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.MeatRaw, 1);
        if (Mods.NewHorizonsCoreMod.isModLoaded()) {
            ItemStack bio = GTModHandler.getModItem(Mods.NewHorizonsCoreMod.ID, "GTNHBioItems", 1, 2);
            if (bio != null) return bio;
        }
        return dust;
    }
    // endregion
}
