package com.MessTech.common.machine.hatch;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.MessTech.common.machine.Base.IMTModule;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
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
 * Every module wears its own decal, the controller artwork under
 * {@code assets/messtech/textures/blocks/ModuleHatch}: {@link #getOverlayPath()} names the icon and the base resolves
 * it once per class in {@link #registerIcons(IIconRegister)}, the way {@code MTReactorAccessHatch} takes its own
 * hatch decal. A module can therefore be told apart by its face as well as by its name and tooltip.
 */
public abstract class MTModuleHatchBase extends MTEHatch implements IMTModule {

    private static final String OVERLAY_DOMAIN = "messtech";

    /**
     * The decals of the module hatches, keyed by {@link #getOverlayPath()}. GT resolves a custom container in the
     * block icon load phase, which {@code BlockMachines} runs after every meta tile entity has had its
     * {@code registerIcons} called, so an entry exists for every module before anything is drawn.
     */
    private static final Map<String, IIconContainer> MODULE_OVERLAYS = new HashMap<>();

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

    /**
     * @return The decal of this module: an icon path relative to {@code textures/blocks}, e.g.
     *         {@code ModuleHatch/OVERLAY_SpeedController}.
     */
    protected abstract String getOverlayPath();

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
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        String path = getOverlayPath();
        MODULE_OVERLAYS.put(path, Textures.BlockIcons.custom(OVERLAY_DOMAIN, path));
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return withOverlay(aBaseTexture);
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return withOverlay(aBaseTexture);
    }

    /** The casing of the structure, plus the decal of this module while it is loaded. */
    private ITexture[] withOverlay(ITexture aBaseTexture) {
        IIconContainer overlay = MODULE_OVERLAYS.get(getOverlayPath());
        if (overlay == null) {
            return new ITexture[] { aBaseTexture };
        }
        return new ITexture[] { aBaseTexture, TextureFactory.of(overlay) };
    }

    // endregion
}
