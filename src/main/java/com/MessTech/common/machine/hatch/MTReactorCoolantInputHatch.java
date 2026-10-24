package com.MessTech.common.machine.hatch;

import java.util.Arrays;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
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
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/**
 * Cold-coolant port of {@code MTReactor}'s fluid ("wet") mode: IC2 coolant in, the same amount of hot coolant out
 * through
 * {@link MTReactorHotCoolantOutputHatch}.
 * <p>
 * <b>Through-put, not a heat sink:</b> 1 mB coolant per 1 HU of buffered component heat
 * ({@code MTReactorHeatSystem#coolantForHeat}), MessTech's own ratio, <em>not</em> IC2's x2. A page of quad rods emits
 * ~24,000 HU/s, so the loop stays cool only while coolant arrives and hot coolant leaves; without feed it cools nothing
 * and the core keeps the heat.
 * <p>
 * <b>Tier:</b> one EV variant on purpose (plumbing, not a tier gate); EV's {@code 8000 * 2^4 = 128,000 mB} tank holds
 * twice
 * the 64,000 mB one cycle asks for. No structure check compares this tier (the heat control hatch is the gate), so it
 * fits
 * a UIV controller.
 * <p>
 * <b>Texture:</b> multiblock casing + GT's fluid input pipe overlay. The coolant decal is pending, so
 * {@code hatch/Hatch_Reactor_Temp_Control} stands in - replace {@link #OVERLAY_PATH} once it exists.
 */
public class MTReactorCoolantInputHatch extends MTEHatchInput {

    /** Fixed tier (EV); the class comment says why it is not a tier family. */
    public static final int TIER = 4;

    /** Registry name of IC2's cold coolant ({@code ic2.core.init.BlocksItems#registerIC2fluid}). */
    public static final String COOLANT_FLUID_NAME = "ic2coolant";

    private static final String OVERLAY_DOMAIN = "messtech";
    /** Decal path relative to {@code textures/blocks} ({@code Textures.BlockIcons.custom}). */
    private static final String OVERLAY_PATH = "hatch/Hatch_Reactor_Temp_Control";

    private static IIconContainer sCoolantOverlay;

    public MTReactorCoolantInputHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTReactorCoolantInputHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, 4, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTReactorCoolantInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    /**
     * The IC2 coolant, or {@code null} while nothing is registered under that name (a pack without IC2 fluids). Every
     * look-up goes through the registry and is null checked, so such a pack gets a hatch that accepts nothing instead
     * of
     * crashing at class load.
     */
    public static Fluid getCoolantFluid() {
        return FluidRegistry.getFluid(COOLANT_FLUID_NAME);
    }

    public static boolean isCoolant(Fluid fluid) {
        Fluid coolant = getCoolantFluid();
        return coolant != null && fluid == coolant;
    }

    /**
     * The coolant filter is fixed: this is the reactor's coolant port and anything else would just be in the way. It
     * cannot be switched off (see {@link #onScrewdriverRightClick}).
     */
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return aFluid != null && isCoolant(aFluid.getFluid());
    }

    /**
     * GT's screwdriver switch would toggle {@code disableFilter} and announce the filter is off while
     * {@link #isFluidInputAllowed} keeps rejecting every other fluid - a lie. So report the real state, toggle nothing.
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        GTUtility.sendChatTrans(aPlayer, "machine.mtreactor.coolanthatch.filter_fixed");
    }

    // region Coolant API used by MTReactor's fluid cycle

    /** Cold coolant held by this hatch; any other fluid - and an empty tank - reports 0. */
    public int getCoolantAmount(Fluid coolant) {
        if (mFluid == null || coolant == null) return 0;
        return mFluid.getFluid() == coolant ? mFluid.amount : 0;
    }

    /**
     * Drains at most {@code amount} mB of cold coolant.
     *
     * @return mB actually drained, never more than {@code amount} or the tank held - callers rely on it like on
     *         {@code fill}'s return value.
     */
    public int drainCoolant(Fluid coolant, int amount) {
        if (amount <= 0) return 0;
        int available = getCoolantAmount(coolant);
        if (available <= 0) return 0;
        FluidStack drained = drain(Math.min(amount, available), true);
        return drained == null ? 0 : drained.amount;
    }

    /**
     * Puts coolant back for a cycle that drained it and could not deliver the hot coolant.
     *
     * @return mB actually taken back, capped by free tank space (128,000 mB at EV)
     */
    public int refundCoolant(Fluid coolant, int amount) {
        if (coolant == null || amount <= 0) return 0;
        return fill(new FluidStack(coolant, amount), true);
    }

    // endregion

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocalFormatted("machine.mtreactor.coolanthatch.desc.0", getCapacity()),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.coolanthatch.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.coolanthatch.desc.2"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("machine.mtreactor.coolanthatch.desc.3") };
    }

    // region Textures

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        sCoolantOverlay = Textures.BlockIcons.custom(OVERLAY_DOMAIN, OVERLAY_PATH);
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
     * restricted (unlike the access hatch): a coolant port sits on any casing face incl. top and bottom, fluids enter
     * from
     * every side and only the decal's direction would suffer.
     */
    private ITexture[] withDecal(ITexture[] aBaseTextures) {
        if (sCoolantOverlay == null) return aBaseTextures;
        ITexture[] result = Arrays.copyOf(aBaseTextures, aBaseTextures.length + 1);
        result[aBaseTextures.length] = TextureFactory.of(sCoolantOverlay);
        return result;
    }

    // endregion
}
