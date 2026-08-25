package com.MessTech.common.machine.hatch;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.recipe.QuantumComputerRecipeData;
import tectech.TecTech;
import tectech.thing.metaTileEntity.hatch.MTEHatchRack;
import tectech.util.TTUtility;

/**
 * MessTech computer rack hatch, reusing GT5U's {@link MTEHatchRack} behaviour.
 * <p>
 * Differences from the base GT5U rack:
 * <ul>
 * <li>No "TecTech: Elemental Matter" line in the tooltip.</li>
 * <li>Slightly softer overclock penalty: the {@code (overclock - overvoltage)^2} term in
 * the computation formula is scaled down by {@link #PENALTY_FACTOR}, so overclocking
 * gives a bit more computation than the stock rack.</li>
 * </ul>
 */
public class MTHatchRack extends MTEHatchRack {

    /** Scales the (overclock - overvoltage)^2 penalty down; 0.8f = 20% softer penalty. */
    private static final float PENALTY_FACTOR = 0.8f;
    private static final Map<String, QuantumComputerRecipeData> COMPONENT_DATA_CACHE = new HashMap<>();

    public MTHatchRack(int aID, String aName, String aNameRegional, int aTier) {
        // The base ID constructor injects "TecTech: Elemental Matter"; we keep it internally only
        // and hide it by overriding getDescription() below so the item tooltip stays clean.
        super(aID, aName, aNameRegional, aTier);
    }

    @Override
    public String[] getDescription() {
        // "TecTech: Elemental Matter" removed on purpose.
        return new String[] {
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gt.blockmachines.hatch.rack.desc.0"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gt.blockmachines.hatch.rack.desc.1") };
    }

    public MTHatchRack(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTHatchRack(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public int tickComponents(float oc, float ov) {
        float computation = 0, heat = 0;
        for (int i = 0; i < mInventory.length; i++) {
            if (mInventory[i] == null || mInventory[i].stackSize != 1) {
                continue;
            }
            QuantumComputerRecipeData comp = getComponentData(mInventory[i]);
            if (comp == null) {
                continue;
            }
            if (getHeat() > comp.maxHeat) {
                mInventory[i] = null;
            } else if (comp.subZero || getHeat() >= 0) {
                heat += (1f + comp.coolConstant * getHeat() / 100000f)
                    * (comp.heatConstant > 0 ? comp.heatConstant * oc * ov * ov : -10f);

                if (ov > TecTech.RANDOM.nextFloat()) {
                    // Stock GT5U formula uses (oc - ov)^2 as the penalty; we scale it down a bit.
                    computation += comp.computation * (1 + oc * oc) / (1 + PENALTY_FACTOR * (oc - ov) * (oc - ov));
                }
            }
        }
        setHeat(getHeat() + (int) Math.ceil(heat));
        return (int) Math.floor(computation);
    }

    private static QuantumComputerRecipeData getComponentData(ItemStack stack) {
        String key = TTUtility.getUniqueIdentifier(stack);
        QuantumComputerRecipeData cached = COMPONENT_DATA_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        for (GTRecipe recipe : RecipeMaps.quantumComputerFakeRecipes.getAllRecipes()) {
            QuantumComputerRecipeData data = recipe.getMetadata(GTRecipeConstants.QUANTUM_COMPUTER_DATA);
            if (data == null) {
                continue;
            }
            for (ItemStack input : recipe.mInputs) {
                if (input != null && input.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(input, stack)) {
                    COMPONENT_DATA_CACHE.put(key, data);
                    return data;
                }
            }
        }
        return null;
    }
}
