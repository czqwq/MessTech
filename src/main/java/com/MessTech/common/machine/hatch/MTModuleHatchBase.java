package com.MessTech.common.machine.hatch;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.machine.Base.IMTModule;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Common base of the module hatches of {@link com.MessTech.common.machine.Base.MTModuleMultiMachineBase}.
 * <p>
 * A module hatch is a plain {@link MTEHatch} with no inventory: it is a carrier for the values of
 * {@link IMTModule}, and the machine it is built into reads those values while it checks its structure. The tier of
 * the hatch is the module tier, IV .. MAX.
 * <p>
 * The decal is GT's data access overlay, the same one TST's {@code ModularHatchBase} puts on every modular hatch; a
 * module is told apart by its name and its tooltip, not by its face.
 */
public abstract class MTModuleHatchBase extends MTEHatch implements IMTModule {

    protected MTModuleHatchBase(int aID, String aName, String aNameRegional, int aTier) {
        // The cast picks the String[] overload: the concrete module hatches build their tooltip in getDescription().
        super(aID, aName, aNameRegional, aTier, 0, (String[]) null);
    }

    protected MTModuleHatchBase(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public int getModuleTier() {
        return mTier;
    }

    /** Take the casing texture of the structure this hatch was built into. */
    @Override
    public void onLinkedToMachine(int aBaseCasingIndex) {
        updateTexture(aBaseCasingIndex);
    }

    // region General

    @Override
    public boolean willExplodeInRain() {
        return false;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    /** A module hatch has no inventory, so every slot access must be refused. */
    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean isLiquidInput(ForgeDirection side) {
        return false;
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return false;
    }

    // endregion

    // region Texture

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_DATA_ACCESS) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_DATA_ACCESS) };
    }

    // endregion
}
