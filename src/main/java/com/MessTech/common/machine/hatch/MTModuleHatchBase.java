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
 * Common base of the module hatches of {@link com.MessTech.common.machine.Base.MTModuleMultiMachineBase}: a plain
 * {@link MTEHatch} with no inventory, carrying {@link IMTModule} values read by the machine during its structure check.
 * The
 * hatch tier is the module tier, IV .. MAX.
 * <p>
 * Each module wears its own decal, the controller artwork under {@code assets/messtech/textures/blocks/ModuleHatch}:
 * {@link #getOverlayPath()} names the icon, resolved once per class in {@link #registerIcons(IIconRegister)} like
 * {@code MTReactorAccessHatch}'s decal, so a module is told apart by face, name and tooltip.
 */
public abstract class MTModuleHatchBase extends MTEHatch implements IMTModule {

    private static final String OVERLAY_DOMAIN = "messtech";

    /**
     * Module decals keyed by {@link #getOverlayPath()}. GT resolves a custom container in the block icon load phase,
     * which
     * {@code BlockMachines} runs after every meta tile entity's {@code registerIcons}.
     */
    private static final Map<String, IIconContainer> MODULE_OVERLAYS = new HashMap<>();

    protected MTModuleHatchBase(int aID, String aName, String aNameRegional, int aTier) {
        // Cast picks the String[] overload: the concrete modules build their tooltip in getDescription().
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
     * @return this module's decal: an icon path relative to {@code textures/blocks}, e.g.
     *         {@code ModuleHatch/OVERLAY_SpeedController}
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
