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
 * Hot-coolant port of {@code MTReactor}'s fluid ("wet") mode: it takes back exactly the hot coolant that
 * {@link MTReactorCoolantInputHatch} fed in as cold coolant, so the loop is 1 mB cold coolant in and 1 mB hot coolant
 * out per 1 HU of buffered component heat. Both the amount and the 1:1 conversion are MessTech's own, not IC2's x2.
 * <p>
 * Nothing else may enter: {@link #isFluidInputAllowed} and {@link #canStoreFluid} both reject every fluid but IC2's hot
 * coolant, so neither a pipe nor GT's output routing can park an unrelated fluid in the loop.
 * <p>
 * <b>Tier.</b> Deliberately a single variant at EV: it is plumbing, not a tier gate, and the inherited
 * {@code MTEHatchOutput#getCapacity()} of {@code 8000 * 2^4 = 128,000 mB} already holds twice the 64,000 mB one full
 * cycle can ask for. Nothing in the reactor structure compares this hatch's tier, so the same hatch fits a UIV
 * controller.
 * <p>
 * {@code fill(FluidStack, boolean)} is inherited unchanged, and it reports honestly: it never accepts more than
 * {@code getCapacity() - getFluidAmount()} and returns the mB it really took, which is what lets the reactor abort a
 * cycle instead of voiding coolant.
 * <p>
 * <b>Texture.</b> Base texture = multiblock casing, with GT's own fluid output pipe overlay kept so the port is
 * recognisable as a fluid output from the outside. The dedicated MessTech hot coolant decal is still pending, so the
 * existing {@code hatch/Hatch_Reactor_Temp_Control} panel stands in for it - replace {@link #OVERLAY_PATH} once the
 * hot coolant art exists.
 */
public class MTReactorHotCoolantOutputHatch extends MTEHatchOutput {

    /** Fixed tier of this hatch (EV). See the class comment for why it is not a tier family. */
    public static final int TIER = 4;

    /** Registry name of IC2's hot coolant, see {@code ic2.core.init.BlocksItems#registerIC2fluid}. */
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
     * The IC2 hot coolant, or {@code null} while no fluid is registered under that name (a pack without IC2 fluids).
     * Every look-up goes through the registry and is null checked, so such a pack gets a hatch that accepts nothing
     * instead of a crash while the class loads.
     */
    public static Fluid getHotCoolantFluid() {
        return FluidRegistry.getFluid(HOT_COOLANT_FLUID_NAME);
    }

    /** True when the given fluid is IC2's hot coolant. */
    public static boolean isHotCoolant(Fluid fluid) {
        Fluid hotCoolant = getHotCoolantFluid();
        return hotCoolant != null && fluid == hotCoolant;
    }

    /** The tank of this port only ever holds hot coolant, so anything else is refused on the pipe/container path. */
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
     * Keeps GT's fluid pipe overlay (the base array) and layers the reactor decal on top of it. Unlike the access
     * hatch this hatch does not restrict {@code isFacingValid}: a coolant port legitimately sits on any casing face
     * including the top and bottom, fluids leave from every side anyway, and only the decal's direction would suffer.
     */
    private ITexture[] withDecal(ITexture[] aBaseTextures) {
        if (sHotCoolantOverlay == null) return aBaseTextures;
        ITexture[] result = Arrays.copyOf(aBaseTextures, aBaseTextures.length + 1);
        result[aBaseTextures.length] = TextureFactory.of(sHotCoolantOverlay);
        return result;
    }

    // endregion
}
