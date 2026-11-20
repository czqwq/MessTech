package com.MessTech.common.recipe;

import static com.MessTech.init.MessTech.MT_LOG;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraftforge.oredict.OreDictionary;

import com.MessTech.common.items.MTItemList;

import gregtech.api.objects.OreDictItemStack;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.nei.RecipeDisplayInfo;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.items.wands.ItemWandCasting;
import thaumcraft.common.lib.crafting.ArcaneSceptreRecipe;
import thaumcraft.common.lib.crafting.ArcaneWandRecipe;

/**
 * The independent recipe pool of the Small Molecular Arcane Assembler (小型分子奥术装配室). It converts every Thaumcraft
 * arcane workbench recipe (and every wand / sceptre that the arcane workbench can assemble from caps and rods) into a
 * regular GT recipe whose only extra data is the vis cost, kept in {@link #ARCANE_ASPECTS} so both the machine and the
 * NEI page can read it. No knowledge core is involved: the pool is built once from Thaumcraft's recipe registry and
 * every recipe is available as long as the machine owner knows the research that gates it.
 *
 * <p>
 * Recipes are paid for with EU instead of wand vis: the machine stores primal aspects and the pool only records the
 * recipe's cost, while {@link #EU_PER_ASPECT} is the conversion rate the machine uses to buy them. Ingredient cells are
 * converted one item per cell (Thaumcraft ignores the template stack size), ore dictionary cells become
 * {@link OreDictItemStack}, other alternative lists become item alternatives. Items that Thaumcraft does not consume in
 * the arcane workbench (the primordial pearl is the well known catalyst) are kept as zero-sized GT inputs, which GT
 * matches as "must be present, is never consumed".
 */
public final class MTArcaneAssemblyRecipes {

    private MTArcaneAssemblyRecipes() {}

    /** EU paid for one point (one vis) of any primal aspect when the machine refills its buffer. */
    public static final long EU_PER_ASPECT = 2048L;
    /** Thaumcraft stores wand vis in hundredths of a vis; the machine buffer uses the same unit. */
    public static final int CENTIVIS_PER_VIS = 100;
    /** Fixed crafting time of one arcane recipe before overclocking. */
    public static final int CRAFT_TICKS = 100;
    /** Base EU/t of one arcane recipe before overclocking. */
    public static final long CRAFT_EU_T = 2048L;

    /** Width in pixels of the NEI description column; aspect requirements wrap to it. */
    private static final int NEI_DESCRIPTION_WIDTH = 150;
    private static final int NEI_FONT_WIDTH = 6;
    /** Page height of the NEI page: the default 135 plus room for three aspect lines. */
    private static final int NEI_PAGE_HEIGHT = 166;

    /**
     * Vis cost of one recipe, in Thaumcraft's own unit (one point = one vis). The metadata key draws the requirement
     * line on the NEI page: "Aer: 8 Ignis: 8 ...", omitting aspects the recipe does not use.
     */
    public static final RecipeMetadataKey<AspectList> ARCANE_ASPECTS = new ArcaneAspectKey();

    /** Research keys the machine owner has to know before the recipe may run; absent for ungated recipes. */
    public static final RecipeMetadataKey<String[]> ARCANE_RESEARCH = SimpleRecipeMetadataKey
        .create(String[].class, "mt_arcane_assembly_research");

    /** The pool the Small Molecular Arcane Assembler is bound to. */
    public static final RecipeMap<RecipeMapBackend> arcaneAssemblyRecipes = RecipeMapBuilder
        .of("mt.recipe.arcaneassembler")
        .maxIO(9, 10, 0, 0)
        .minInputs(1, 0)
        .neiHandlerInfo(
            builder -> builder.setDisplayStack(MTItemList.MTSmallMolecularArcaneAssembler.get(1))
                .setHeight(NEI_PAGE_HEIGHT))
        .build();

    /** The six primal aspects, cached: {@code Aspect#getPrimalAspects()} allocates a new list on every call. */
    private static final List<Aspect> PRIMALS = Aspect.getPrimalAspects();

    /** Live OreDictionary lists, keyed by identity: Thaumcraft keeps the returned list object in the recipe. */
    private static final Map<List<?>, String> ORE_DICTIONARY_LISTS = new IdentityHashMap<>();

    /** Converted recipes in registration order; the pattern access hatch matches its AE patterns against these. */
    private static final List<ArcaneDefinition> DEFINITIONS = new ArrayList<>();

    /**
     * One converted recipe: the GT builder input spec, the item results, the aspect cost, the research keys and the
     * item slots the pattern matching needs. Built once when the pool is populated, immutable afterwards.
     */
    public static final class ArcaneDefinition {

        /** One recipe slot: the acceptable stacks (never empty for a valid recipe) and how many are consumed. */
        private static final class InputSlot {

            private final List<ItemStack> choices;
            /** Positive amount consumed; 0 is GT's "must be present, never consumed" convention. */
            private final int amount;

            private InputSlot(List<ItemStack> choices, int amount) {
                this.choices = choices;
                this.amount = amount;
            }
        }

        private final List<Object> inputs;
        private final List<InputSlot> slots;
        private final List<ItemStack> outputs;
        private final @Nullable AspectList aspects;
        private final List<String> research;

        private ArcaneDefinition(List<Object> inputs, List<InputSlot> slots, List<ItemStack> outputs,
            @Nullable AspectList aspects, List<String> research) {
            this.inputs = inputs;
            this.slots = slots;
            this.outputs = outputs;
            this.aspects = aspects;
            this.research = research;
        }

        /** @return the item results: the main result plus every container item the recipe returns. */
        public List<ItemStack> getOutputs() {
            return outputs;
        }

        /** @return the vis cost, or null for an aspect-free recipe. */
        public @Nullable AspectList getAspects() {
            return aspects;
        }

        /** @return the Thaumcraft research keys the machine owner has to know. */
        public List<String> getResearch() {
            return research;
        }
    }

    /**
     * Forces this class to initialise while the mod is loading (called by {@code MTMachineLoader.loadMachines()} in
     * postInit), not when the first recipe-map lookup runs: the NEI recipe category remembers the owning mod at
     * construction time, and the category has to belong to MessTech.
     */
    public static void init() {
        if (arcaneAssemblyRecipes == null) {
            throw new IllegalStateException("The arcane assembly recipe map is missing");
        }
    }

    /**
     * Fills {@link #arcaneAssemblyRecipes} from Thaumcraft's arcane recipes. Re-runnable: it does nothing once the pool
     * has entries. Must run after every mod registered its Thaumcraft recipes (GTRecipes runs at load complete).
     */
    public static void populateArcaneAssemblyRecipes() {
        if (!arcaneAssemblyRecipes.getAllRecipes()
            .isEmpty()) {
            return;
        }

        buildOreDictionaryIndex();
        final List<ArcaneDefinition> definitions = new ArrayList<>();
        int skipped = 0;
        Set<String> unsupported = new LinkedHashSet<>();
        for (Object recipe : (List<?>) ThaumcraftApi.getCraftingRecipes()) {
            if (recipe instanceof ShapedArcaneRecipe shaped) {
                if (addStaticRecipe(
                    definitions,
                    shaped.getInput(),
                    shaped.getRecipeOutput(),
                    shaped.getAspects(),
                    shaped.getResearch())) {
                    // registered below, once every definition was converted
                } else {
                    skipped++;
                }
            } else if (recipe instanceof ShapelessArcaneRecipe shapeless) {
                if (addStaticRecipe(
                    definitions,
                    shapeless.getInput()
                        .toArray(),
                    shapeless.getRecipeOutput(),
                    shapeless.getAspects(),
                    shapeless.getResearch())) {
                    // registered below, once every definition was converted
                } else {
                    skipped++;
                }
            } else if (recipe instanceof ArcaneWandRecipe) {
                addWandRecipes(definitions);
            } else if (recipe instanceof ArcaneSceptreRecipe) {
                addSceptreRecipes(definitions);
            } else if (recipe instanceof IArcaneRecipe unsupportedRecipe) {
                // The IArcaneRecipe interface exposes no ingredient list, so a conversion is impossible for a custom
                // implementation that is not one of the classes above (and whose aspects may be per-grid anyway).
                unsupported.add(
                    unsupportedRecipe.getClass()
                        .getName());
            }
        }

        if (!unsupported.isEmpty()) {
            MT_LOG.warn(
                "Small Molecular Arcane Assembler: skipped {} custom IArcaneRecipe implementation(s) without a "
                    + "convertible ingredient list: {}",
                unsupported.size(),
                unsupported);
        }

        DEFINITIONS.clear();
        for (ArcaneDefinition definition : definitions) {
            buildRecipe(definition);
            DEFINITIONS.add(definition);
        }

        MT_LOG.info(
            "Small Molecular Arcane Assembler: converted {} arcane recipes ({} skipped, {} total in the pool).",
            definitions.size(),
            skipped,
            arcaneAssemblyRecipes.getAllRecipes()
                .size());
    }

    // region conversion

    /**
     * Converts one shaped / shapeless arcane recipe, one item per grid cell, into an unregistered definition.
     *
     * @return whether the recipe could be converted.
     */
    private static boolean addStaticRecipe(List<ArcaneDefinition> definitions, Object[] cells, ItemStack output,
        AspectList aspectCost, String research) {
        if (cells == null || output == null || output.getItem() == null) return false;

        List<Object> inputs = new ArrayList<>(cells.length);
        List<ArcaneDefinition.InputSlot> slots = new ArrayList<>(cells.length);
        List<ItemStack> extraOutputs = new ArrayList<>(2);
        for (Object cell : cells) {
            if (cell == null) continue;
            if (cell instanceof ItemStack stack) {
                if (!addCellInput(inputs, slots, extraOutputs, stack)) return false;
            } else if (cell instanceof List<?>alternatives) {
                if (!addAlternativeInput(inputs, slots, alternatives)) return false;
            }
        }
        if (inputs.isEmpty()) return false;

        registerDefinition(definitions, inputs, slots, output, extraOutputs, aspectCost, research);
        return true;
    }

    /** Adds one ingredient cell. Catalysts become non-consuming inputs, container items become extra outputs. */
    private static boolean addCellInput(List<Object> inputs, List<ArcaneDefinition.InputSlot> slots,
        List<ItemStack> extraOutputs, ItemStack cell) {
        ItemStack stack = GTUtility.copyAmount(1, cell);
        if (stack == null || stack.getItem() == null) return false;

        if (isCatalyst(stack)) {
            inputs.add(GTUtility.copyAmount(0, stack));
            slots.add(new ArcaneDefinition.InputSlot(slotChoices(List.of(stack)), 0));
            return true;
        }

        // A container item that is the item itself is Thaumcraft's "reuse me" convention: never consumed.
        ItemStack container = stack.getItem()
            .getContainerItem(stack);
        if (container != null && container.getItem() != null) {
            if (container.getItem() == stack.getItem() && container.getItemDamage() == stack.getItemDamage()) {
                inputs.add(GTUtility.copyAmount(0, stack));
                slots.add(new ArcaneDefinition.InputSlot(slotChoices(List.of(stack)), 0));
                return true;
            }
            extraOutputs.add(GTUtility.copyAmount(1, container));
        }

        inputs.add(stack);
        slots.add(new ArcaneDefinition.InputSlot(slotChoices(List.of(stack)), 1));
        return true;
    }

    /** Adds one ore dictionary / alternative cell; an empty alternative list means the recipe cannot be matched. */
    private static boolean addAlternativeInput(List<Object> inputs, List<ArcaneDefinition.InputSlot> slots,
        List<?> alternatives) {
        List<ItemStack> stacks = new ArrayList<>(alternatives.size());
        for (Object candidate : alternatives) {
            if (candidate instanceof ItemStack stack && stack.getItem() != null) {
                stacks.add(GTUtility.copyAmount(1, stack));
            }
        }
        if (stacks.isEmpty()) return false;

        final String oreName = ORE_DICTIONARY_LISTS.get(alternatives);
        if (oreName != null) {
            inputs.add(new OreDictItemStack(oreName, 1));
            List<ItemStack> ores = new ArrayList<>();
            for (ItemStack ore : OreDictionary.getOres(oreName, false)) {
                if (ore != null && ore.getItem() != null) ores.add(GTUtility.copyAmount(1, ore));
            }
            slots.add(new ArcaneDefinition.InputSlot(slotChoices(ores.isEmpty() ? stacks : ores), 1));
        } else if (stacks.size() == 1) {
            inputs.add(stacks.get(0));
            slots.add(new ArcaneDefinition.InputSlot(slotChoices(stacks), 1));
        } else {
            inputs.add(stacks.toArray(new ItemStack[0]));
            slots.add(new ArcaneDefinition.InputSlot(slotChoices(stacks), 1));
        }
        return true;
    }

    /**
     * Builds the accepted stacks of one slot. Besides the recipe's own stack this also accepts its GT-unified form:
     * GT's
     * NEI page unifies the ingredient ({@code GTRecipeBuilder#itemInputs} runs
     * {@code GTOreDictUnificator.setStackArray}),
     * so a pattern encoded from that page can carry the unified item, not the item Thaumcraft registered.
     */
    private static List<ItemStack> slotChoices(List<ItemStack> stacks) {
        final List<ItemStack> choices = new ArrayList<>(stacks.size() + 2);
        for (ItemStack stack : stacks) {
            addChoice(choices, stack);
            if (stack != null) addChoice(choices, GTOreDictUnificator.get(stack));
        }
        return choices;
    }

    private static void addChoice(List<ItemStack> choices, ItemStack candidate) {
        if (candidate == null || candidate.getItem() == null) return;
        for (ItemStack choice : choices) {
            if (GTUtility.areStacksEqual(choice, candidate)) return;
        }
        choices.add(GTUtility.copyAmount(1, candidate));
    }

    /** Builds the definition (result plus extra container outputs, primal-only aspect cost, research keys). */
    private static void registerDefinition(List<ArcaneDefinition> definitions, List<Object> inputs,
        List<ArcaneDefinition.InputSlot> slots, ItemStack output, List<ItemStack> extraOutputs, AspectList aspectCost,
        String... researchKeys) {
        List<ItemStack> outputs = new ArrayList<>(1 + extraOutputs.size());
        outputs.add(GTUtility.copyAmount(output.stackSize, output));
        outputs.addAll(extraOutputs);

        AspectList primalCost = toPrimalCost(aspectCost);
        if (primalCost.size() == 0) primalCost = null;

        List<String> research = new ArrayList<>(researchKeys.length);
        for (String key : researchKeys) {
            if (key != null && !key.isEmpty()) research.add(key);
        }
        definitions.add(new ArcaneDefinition(inputs, slots, outputs, primalCost, research));
    }

    /** Builds and registers the GT recipe of one converted definition (the NEI page of the pool). */
    private static void buildRecipe(ArcaneDefinition definition) {
        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .duration(CRAFT_TICKS)
            .eut(CRAFT_EU_T)
            .itemOutputs(definition.outputs.toArray(new ItemStack[0]))
            .ignoreCollision();

        if (definition.inputs.stream()
            .allMatch(ItemStack.class::isInstance)) {
            builder.itemInputs(definition.inputs.toArray(new ItemStack[0]));
        } else {
            builder.itemInputs(definition.inputs.toArray());
        }

        if (definition.aspects != null && definition.aspects.size() > 0) {
            builder.metadata(ARCANE_ASPECTS, definition.aspects);
        }
        if (!definition.research.isEmpty()) {
            builder.metadata(ARCANE_RESEARCH, definition.research.toArray(new String[0]));
        }

        builder.addTo(arcaneAssemblyRecipes);
    }

    // region wand and sceptre recipes

    /** Adds {@code count} slots that all accept the same item (a recipe needs so many of it). */
    private static void addRepeatedSlot(List<Object> inputs, List<ArcaneDefinition.InputSlot> slots, ItemStack item,
        int count) {
        final List<ItemStack> choices = List.of(GTUtility.copyAmount(1, item));
        for (int index = 0; index < count; index++) {
            inputs.add(GTUtility.copyAmount(1, item));
            slots.add(new ArcaneDefinition.InputSlot(choices, 1));
        }
    }

    /** Enumerates the wand recipe ArcaneWandRecipe computes at runtime: two equal caps, one rod, six primal aspects. */
    private static void addWandRecipes(List<ArcaneDefinition> definitions) {
        for (WandCap cap : WandCap.caps.values()) {
            for (WandRod rod : WandRod.rods.values()) {
                // The arcane workbench refuses the wood rod + iron cap combination (the plain wooden wand).
                if ("wood".equals(rod.getTag()) && "iron".equals(cap.getTag())) continue;

                int cost = cap.getCraftCost() * rod.getCraftCost();
                ItemStack output = new ItemStack(ConfigItems.itemWandCasting, 1, cost);
                ItemWandCasting wand = (ItemWandCasting) output.getItem();
                wand.setCap(output, cap);
                wand.setRod(output, rod);

                List<Object> inputs = new ArrayList<>(3);
                List<ArcaneDefinition.InputSlot> slots = new ArrayList<>(3);
                addRepeatedSlot(inputs, slots, cap.getItem(), 2);
                addRepeatedSlot(inputs, slots, rod.getItem(), 1);

                registerDefinition(
                    definitions,
                    inputs,
                    slots,
                    output,
                    List.of(),
                    primalCost(cost),
                    cap.getResearch(),
                    rod.getResearch());
            }
        }
    }

    /** Enumerates the sceptre recipe ArcaneSceptreRecipe computes at runtime: three equal caps, a rod and a charm. */
    private static void addSceptreRecipes(List<ArcaneDefinition> definitions) {
        ItemStack charm = new ItemStack(ConfigItems.itemResource, 1, 15);
        for (WandCap cap : WandCap.caps.values()) {
            for (WandRod rod : WandRod.rods.values()) {
                int cost = (int) ((float) (cap.getCraftCost() * rod.getCraftCost()) * 1.5F);
                ItemStack output = new ItemStack(ConfigItems.itemWandCasting, 1, cost);
                ItemWandCasting wand = (ItemWandCasting) output.getItem();
                wand.setCap(output, cap);
                wand.setRod(output, rod);
                output.setTagInfo("sceptre", new NBTTagByte((byte) 1));

                List<Object> inputs = new ArrayList<>(5);
                List<ArcaneDefinition.InputSlot> slots = new ArrayList<>(5);
                addRepeatedSlot(inputs, slots, cap.getItem(), 3);
                addRepeatedSlot(inputs, slots, rod.getItem(), 1);
                addRepeatedSlot(inputs, slots, charm, 1);

                registerDefinition(
                    definitions,
                    inputs,
                    slots,
                    output,
                    List.of(),
                    primalCost(cost),
                    "SCEPTRE",
                    cap.getResearch(),
                    rod.getResearch());
            }
        }
    }

    // endregion

    /** @return The same amount of every primal aspect, as the arcane workbench charges for wand-like recipes. */
    private static AspectList primalCost(int amount) {
        AspectList aspects = new AspectList();
        for (Aspect aspect : PRIMALS) {
            aspects.add(aspect, amount);
        }
        return aspects;
    }

    /**
     * @return A copy of {@code aspects} with primals only. Arcane recipes cannot cost compound aspects (a wand cannot
     *         store them), so a compound entry would mean the recipe is not castable in Thaumcraft either; it is
     *         dropped with a log line instead of being turned into a cost the machine could never pay.
     */
    private static AspectList toPrimalCost(@Nullable AspectList aspects) {
        AspectList primals = new AspectList();
        if (aspects == null) return primals;

        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            int amount = aspects.getAmount(aspect);
            if (amount <= 0) continue;
            if (!aspect.isPrimal()) {
                MT_LOG.warn("Small Molecular Arcane Assembler: dropping compound aspect cost {} for a recipe.", aspect);
                continue;
            }
            primals.add(aspect, amount);
        }
        return primals;
    }

    /**
     * The primordial pearl is a catalyst in this pool: required for the recipe, never consumed. The Thaumic
     * Energistics Arcane Assembler this pool is converted from returns it after every craft as well (its
     * {@code ArcaneCraftingPattern#getAllOutputs} puts an {@code ItemEldritchObject} with damage 3 back into the
     * output), so a converted recipe must not eat a pearl per craft.
     */
    private static boolean isCatalyst(ItemStack stack) {
        return stack.getItem() == ConfigItems.itemEldritchObject && stack.getItemDamage() == 3;
    }

    /** Indexes every live OreDictionary list, so an ore dictionary cell can keep its ore name instead of items. */
    private static void buildOreDictionaryIndex() {
        ORE_DICTIONARY_LISTS.clear();
        for (String oreName : OreDictionary.getOreNames()) {
            if (oreName == null) continue;
            List<ItemStack> ores = OreDictionary.getOres(oreName, false);
            if (ores == null || ores.isEmpty()) continue;
            ORE_DICTIONARY_LISTS.putIfAbsent(ores, oreName);
        }
    }

    // endregion

    // region pattern matching

    /** @return the index of {@code definition} in registration order, or -1 when it is not part of the pool. */
    public static int indexOf(ArcaneDefinition definition) {
        return DEFINITIONS.indexOf(definition);
    }

    /** @return the definition at {@code index}, or null when the index is stale (different recipe set). */
    public static @Nullable ArcaneDefinition definitionAt(int index) {
        if (index < 0 || index >= DEFINITIONS.size()) return null;
        return DEFINITIONS.get(index);
    }

    /**
     * Finds the converted recipe an AE2 pattern describes: the pattern's input stacks must fill the recipe's slots
     * exactly (the recipe's alternative choices decide which concrete item is accepted) and every pattern output must
     * be
     * one of the recipe's item results. The recipe pool itself is untouched; this is the only crafting recognition
     * path,
     * used by the pattern access hatch.
     *
     * @return the recipe, or null when the pattern does not describe any converted arcane recipe.
     */
    public static @Nullable ArcaneDefinition findRecipeForPattern(List<ItemStack> patternInputs,
        List<ItemStack> patternOutputs) {
        if (patternInputs.isEmpty() || patternOutputs.isEmpty()) return null;

        for (ArcaneDefinition definition : DEFINITIONS) {
            if (!outputsContained(definition.outputs, patternOutputs)) continue;
            if (!assignSlots(definition, patternInputs, null)) continue;
            return definition;
        }
        return null;
    }

    /**
     * @return the provided stacks the recipe does not consume: its non-consumed inputs (catalysts). Uses the same
     *         slot assignment as {@link #findRecipeForPattern}, so an accepted pattern can never come back as "all
     *         inputs unconsumed".
     */
    public static List<ItemStack> unconsumedInputs(ArcaneDefinition recipe, List<ItemStack> provided) {
        final List<ItemStack> units = expandUnits(provided);
        final int[] assignment = new int[recipe.slots.size()];
        if (!assignSlots(recipe, provided, assignment)) return new ArrayList<>(provided);

        final List<ItemStack> leftovers = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < recipe.slots.size(); slotIndex++) {
            if (recipe.slots.get(slotIndex).amount > 0) continue;
            final ItemStack unit = units.get(assignment[slotIndex]);
            if (unit != null) leftovers.add(unit);
        }
        return mergeUnits(leftovers);
    }

    /** @return true when every pattern output is part of {@code recipeOutputs} (amounts included). */
    private static boolean outputsContained(List<ItemStack> recipeOutputs, List<ItemStack> patternOutputs) {
        final List<ItemStack> remaining = new ArrayList<>(recipeOutputs.size());
        for (ItemStack output : recipeOutputs) remaining.add(GTUtility.copy(output));

        for (ItemStack patternOutput : patternOutputs) {
            if (patternOutput == null || patternOutput.getItem() == null || patternOutput.stackSize <= 0) continue;
            boolean found = false;
            for (int index = 0; index < remaining.size(); index++) {
                final ItemStack candidate = remaining.get(index);
                if (candidate == null || !GTUtility.areStacksEqual(candidate, patternOutput)) continue;
                if (candidate.stackSize < patternOutput.stackSize) continue;
                final int left = candidate.stackSize - patternOutput.stackSize;
                if (left <= 0) {
                    remaining.remove(index);
                } else {
                    candidate.stackSize = left;
                }
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    /**
     * Backtracking assignment of the pattern's single-item units to the recipe's slots.
     *
     * @param assignment optional output: the unit index chosen for each slot (null when only the check is needed).
     * @return whether the provided stacks fill every recipe slot exactly.
     */
    private static boolean assignSlots(ArcaneDefinition definition, List<ItemStack> provided, int[] assignment) {
        final List<ItemStack> units = expandUnits(provided);
        int required = 0;
        for (ArcaneDefinition.InputSlot slot : definition.slots) required += Math.max(1, slot.amount);
        if (units.size() != required) return false;
        return assignSlots(definition.slots, 0, units, new boolean[units.size()], assignment);
    }

    private static boolean assignSlots(List<ArcaneDefinition.InputSlot> slots, int slotIndex, List<ItemStack> units,
        boolean[] used, int[] assignment) {
        if (slotIndex == slots.size()) return true;
        final ArcaneDefinition.InputSlot slot = slots.get(slotIndex);
        return assignSlotUnits(slots, slotIndex, Math.max(1, slot.amount), units, used, assignment);
    }

    private static boolean assignSlotUnits(List<ArcaneDefinition.InputSlot> slots, int slotIndex, int remaining,
        List<ItemStack> units, boolean[] used, int[] assignment) {
        if (remaining <= 0) return assignSlots(slots, slotIndex + 1, units, used, assignment);

        final ArcaneDefinition.InputSlot slot = slots.get(slotIndex);
        for (int index = 0; index < units.size(); index++) {
            if (used[index] || !slotAccepts(slot, units.get(index))) continue;
            used[index] = true;
            if (assignment != null && remaining == Math.max(1, slot.amount)) assignment[slotIndex] = index;
            if (assignSlotUnits(slots, slotIndex, remaining - 1, units, used, assignment)) return true;
            used[index] = false;
        }
        return false;
    }

    private static boolean slotAccepts(ArcaneDefinition.InputSlot slot, ItemStack stack) {
        for (ItemStack choice : slot.choices) {
            if (GTUtility.areStacksEqual(choice, stack)) return true;
        }
        return false;
    }

    /** Expands stacks into single-item units, so alternative slots can be matched item by item. */
    private static List<ItemStack> expandUnits(List<ItemStack> stacks) {
        final List<ItemStack> units = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) continue;
            final ItemStack unit = GTUtility.copyAmount(1, stack);
            for (int copy = 0; copy < stack.stackSize; copy++) units.add(unit);
        }
        return units;
    }

    /** Merges equal units back into counted stacks (what the machine returns to the AE network). */
    private static List<ItemStack> mergeUnits(List<ItemStack> units) {
        final List<ItemStack> working = new ArrayList<>(units);
        final List<ItemStack> merged = new ArrayList<>(working.size());
        while (!working.isEmpty()) {
            final ItemStack first = working.remove(0);
            int count = 1;
            for (int index = working.size() - 1; index >= 0; index--) {
                if (GTUtility.areStacksEqual(working.get(index), first)) {
                    working.remove(index);
                    count++;
                }
            }
            final ItemStack stack = GTUtility.copyAmount(count, first);
            if (stack != null) merged.add(stack);
        }
        return merged;
    }

    // endregion

    // region NEI

    /** Metadata key that writes the vis requirement as one or more wrapped lines under the NEI recipe. */
    private static final class ArcaneAspectKey extends RecipeMetadataKey<AspectList> {

        private ArcaneAspectKey() {
            super(AspectList.class, "mt_arcane_assembly_aspects");
        }

        @Override
        public void drawInfo(RecipeDisplayInfo recipeInfo, @Nullable Object value) {
            AspectList aspects = cast(value, null);
            if (aspects == null || aspects.size() == 0) return;

            for (String line : aspectRequirementLines(aspects)) {
                recipeInfo.drawText(line);
            }
        }
    }

    /**
     * @return The vis requirement of one recipe split into NEI-sized lines, e.g. {@code "Aer: 8 Ignis: 8"} with each
     *         aspect tinted by its own chat colour and every unused aspect omitted.
     */
    private static List<String> aspectRequirementLines(AspectList aspects) {
        List<String> lines = new ArrayList<>(3);
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;

        for (Aspect aspect : PRIMALS) {
            int amount = aspects.getAmount(aspect);
            if (amount <= 0) continue;

            String token = "§" + aspect.getChatcolor() + aspect.getName() + "§7: §b" + amount;
            int tokenWidth = visibleWidth(token);
            if (lineWidth > 0 && lineWidth + NEI_FONT_WIDTH + tokenWidth > NEI_DESCRIPTION_WIDTH) {
                lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }
            if (lineWidth > 0) {
                line.append(' ');
                lineWidth += NEI_FONT_WIDTH;
            }
            line.append(token);
            lineWidth += tokenWidth;
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** @return Width of {@code text} in pixels, ignoring {@code §} formatting pairs. */
    private static int visibleWidth(String text) {
        int chars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            chars++;
        }
        return chars * NEI_FONT_WIDTH;
    }

    // endregion
}
