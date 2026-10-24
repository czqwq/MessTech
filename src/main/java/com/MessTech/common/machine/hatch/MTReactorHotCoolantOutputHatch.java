package com.MessTech.common.machine.hatch;

import java.util.Arrays;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.render.TextureFactory;

/**
 * Hot-coolant port of {@code MTReactor}'s fluid ("wet") mode: takes back exactly the hot coolant
 * {@link MTReactorCoolantInputHatch} fed in cold - 1 mB cold in / 1 mB hot out per 1 HU of buffered component heat
 * (MessTech's own amounts, not IC2's x2).
 * <p>
 * Nothing else may enter: {@link #isFluidInputAllowed} and {@link #canStoreFluid} reject every other fluid, so neither
 * a
 * pipe nor GT's output routing can park something unrelated in the loop.
 * <p>
 * <b>Tier:</b> one EV variant on purpose (plumbing, not a tier gate); inherited {@code MTEHatchOutput#getCapacity()} of
 * {@code 8000 * 2^4 = 128,000 mB} holds twice the 64,000 mB one cycle asks for, and no structure check compares this
 * tier,
 * so it fits a UIV controller. Inherited {@code fill(FluidStack, boolean)} takes at most
 * {@code getCapacity() - getFluidAmount()} and returns the mB really taken, letting the reactor abort a cycle instead
 * of
 * voiding coolant.
 * <p>
 * <b>Texture:</b> multiblock casing + GT's fluid output pipe overlay; the hot coolant decal is pending, so
 * {@code hatch/Hatch_Reactor_Temp_Control} stands in (replace {@link #OVERLAY_PATH} later).
 */
public class MTReactorHotCoolantOutputHatch extends MTEHatchOutput {

    /** Fixed tier (EV); the class comment says why it is not a tier family. */
    public static final int TIER = 4;

    /** Registry name of IC2's hot coolant ({@code ic2.core.init.BlocksItems#registerIC2fluid}). */
    public static final String HOT_COOLANT_FLUID_NAME = "ic2hotcoolant";

    private static final String OVERLAY_DOMAIN = "messtech";
    /** Decal path relative to {@code textures/blocks}, matching {@code Textures.BlockIcons.custom}. */
    private static final String OVERLAY_PATH = "hatch/Hatch_Reactor_Temp_Control";

    private static IIconContainer sHotCoolantOverlay;

    public MTReactorHotCoolantOutputHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, (String[]) null, 4);
    }

    public MTReactorHotCoolantOutputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 4, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTReactorHotCoolantOutputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    /**
     * The IC2 hot coolant, or {@code null} while nothing is registered under that name (a pack without IC2 fluids).
     * Every
     * look-up goes through the registry and is null checked, so such a pack gets a hatch that accepts nothing instead
     * of
     * crashing at class load.
     */
    public static Fluid getHotCoolantFluid() {
        return FluidRegistry.getFluid(HOT_COOLANT_FLUID_NAME);
    }

    public static boolean isHotCoolant(Fluid fluid) {
        Fluid hotCoolant = getHotCoolantFluid();
        return hotCoolant != null && fluid == hotCoolant;
    }

    /** The tank only ever holds hot coolant, so anything else is refused on the pipe/container path. */
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return aFluid != null && isHotCoolant(aFluid.getFluid());
    }

    /** Same rule for GT's fluid output routing, which goes through {@code IFluidStore} instead of {@code fill}. */
    @Override
    public boolean canStoreFluid(FluidStack fluidStack) {
        return fluidStack != null && isHotCoolant(fluidStack.getFluid());
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocalFormatted("machine.mtreactor.hotcoolanthatch.desc.0", getCapacity()),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.hotcoolanthatch.desc.1"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("machine.mtreactor.hotcoolanthatch.desc.2") };
    }

    // region Textures

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        sHotCoolantOverlay = Textures.BlockIcons.custom(OVERLAY_DOMAIN, OVERLAY_PATH);
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return withDecal(super.getTexturesActive(aBaseTexture));
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return withDecal(super.getTexturesInactive(aBaseTexture));
    }

    /**
     * Keeps GT's fluid pipe overlay (the base array) and layers the reactor decal on top. {@code isFacingValid} is not
     * restricted (unlike the access hatch): a coolant port sits on any casing face incl. top and bottom, fluids leave
     * from
     * every side and only the decal's direction would suffer.
     */
    private ITexture[] withDecal(ITexture[] aBaseTextures) {
        if (sHotCoolantOverlay == null) return aBaseTextures;
        ITexture[] result = Arrays.copyOf(aBaseTextures, aBaseTextures.length + 1);
        result[aBaseTextures.length] = TextureFactory.of(sHotCoolantOverlay);
        return result;
    }

    // endregion
}
