package com.MessTech.common.nei;

import java.util.LinkedHashSet;
import java.util.Set;

import com.MessTech.common.gui.GuiUltimatePatternTerminal;
import com.MessTech.common.items.MTItemList;
import com.github.vfyjxf.nee.nei.NEEPatternTerminalHandler;
import com.github.vfyjxf.nee.processor.IRecipeProcessor;
import com.github.vfyjxf.nee.processor.RecipeProcessor;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.api.IOverlayHandler;
import cpw.mods.fml.common.Loader;
import gregtech.api.recipe.RecipeCategory;

/**
 * MessTech NEI configuration.
 * <p>
 * Registers the infinite Space Pump as a catalyst for the original GTNH-Intergalactic Space Pump recipe handler, so
 * its custom {@code SpacePumpingRecipes} show up in NEI, and lets NEI transfer recipe pages into the Ultimate Pattern
 * Terminal's grid. The terminal is deliberately <em>not</em> a recipe catalyst: it is a pattern terminal, not a machine
 * that runs those recipes, so it has no business being listed as one on the page.
 * <p>
 * Who does the transfer depends on the pack: with NotEnoughEnergistics - the GTNH transfer for AE's pattern terminals -
 * loaded, its {@link NEEPatternTerminalHandler} takes the terminal's pages, so that one mod owns the behaviour and its
 * options apply here too; without it, this mod's own {@link PatternImportOverlayHandler} takes every GT recipe page.
 */
public class NEI_MessTechConfig implements IConfigureNEI {

    /** Mod id of NotEnoughEnergistics. */
    private static final String NOT_ENOUGH_ENERGISTICS = "neenergistics";

    public static boolean isAdded = true;

    @Override
    public void loadConfig() {
        API.addRecipeCatalyst(
            MTItemList.SpaceModulePumpInfinity.get(1),
            "gtnhintergalactic.nei.SpacePumpModuleRecipeHandler");

        // NotEnoughEnergistics registers itself for AE's own terminals only, and NEI looks a transfer up by the GUI's
        // exact class, so this terminal has to ask for the very idents NEU serves if it wants NEU to do its transfer.
        // Every NEU reference sits in its own method, so a pack that ships without NEU never has to link its classes.
        final boolean useNEU = Loader.isModLoaded(NOT_ENOUGH_ENERGISTICS);
        final IOverlayHandler handler = useNEU ? neuTransferHandler() : new PatternImportOverlayHandler();
        for (String ident : useNEU ? neuTransferIdents() : gtTransferIdents()) {
            // `registerGuiOverlay` is what lets NEI draw such a page over this GUI at all, `registerGuiOverlayHandler`
            // is the transfer button next to it.
            API.registerGuiOverlay(GuiUltimatePatternTerminal.class, ident);
            API.registerGuiOverlayHandler(GuiUltimatePatternTerminal.class, handler, ident);
        }

        isAdded = true;
    }

    /** NotEnoughEnergistics' own transfer for AE's pattern terminals. */
    private static IOverlayHandler neuTransferHandler() {
        return NEEPatternTerminalHandler.instance;
    }

    /**
     * Everything NotEnoughEnergistics itself serves this kind of terminal: the GT pages below plus every page of the
     * mods its processor list knows (the vanilla processing pages, IC2, Forestry, Thaumcraft, ...), which this mod's
     * own handler cannot pack - it reads GT's {@code FixedPositionedStack} flags.
     */
    private static Set<String> neuTransferIdents() {
        final Set<String> idents = gtTransferIdents();
        // NEI may load this config before NEU's own one, and the processor list is a plain static fill with no side
        // effects beyond its log lines, so it is built on demand here. NEU's own later call appends the same
        // processors again, which its lookup tolerates: it breaks at the first processor that claims the page.
        if (RecipeProcessor.recipeProcessors.isEmpty()) {
            RecipeProcessor.init();
        }
        for (IRecipeProcessor processor : RecipeProcessor.recipeProcessors) {
            idents.addAll(processor.getAllOverlayIdentifier());
        }
        // Pages whose handler defines no overlay id at all (NEU registers those too, under a null ident).
        idents.add(null);
        // AE's extended pattern terminal cannot craft, and this terminal is built on it: a crafting page has no
        // processing pattern to become. NEU skips exactly these two for that same terminal.
        idents.remove("crafting");
        idents.remove("crafting2x2");
        return idents;
    }

    /**
     * Every GT machine recipe page, read from GT's own categories - the same source NotEnoughEnergistics builds its
     * idents from - so a newly registered GT recipe map (the Nano-Scale Foundry "24" pool is one) becomes importable
     * without touching this class, and a map that keeps itself out of NEI ({@code registerNEI} false) is skipped. The
     * overlay id of such a page is the category's unlocalized name.
     */
    private static Set<String> gtTransferIdents() {
        final Set<String> idents = new LinkedHashSet<>();
        for (RecipeCategory category : RecipeCategory.ALL_RECIPE_CATEGORIES.values()) {
            if (!category.recipeMap.getFrontend()
                .getNEIProperties().registerNEI) {
                continue;
            }
            idents.add(category.unlocalizedName);
        }
        return idents;
    }

    @Override
    public String getName() {
        return "MessTech NEI Plugin";
    }

    @Override
    public String getVersion() {
        return com.MessTech.init.Tags.VERSION;
    }
}
