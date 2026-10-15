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
 * Cold-coolant port of {@code MTReactor}'s fluid ("wet") mode. IC2's coolant goes in here, the same amount of hot
 * coolant leaves through {@link MTReactorHotCoolantOutputHatch}, and the pair is what lets a page be cooled without a
 * single coolant cell.
 * <p>
 * <b>It is a through-put, not a heat sink.</b> The conversion is MessTech's own 1 mB of coolant per 1 HU of buffered
 * component heat ({@code MTReactorHeatSystem#coolantForHeat}) - <em>not</em> IC2's x2 factor - and the hot coolant that
 * leaves is the same amount. A page of quad rods emits roughly 24,000 HU/s, so a wet reactor only stays cool while
 * coolant keeps arriving and someone keeps taking the hot coolant away; with the feed cut the loop simply cools
 * nothing and the core keeps the heat.
 * <p>
 * <b>Tier.</b> Deliberately a single variant at EV: it is plumbing, not a tier gate, and EV's
 * {@code 8000 * 2^4 = 128,000 mB} tank already holds twice the 64,000 mB one full cycle can ask for. Nothing in the
 * reactor structure compares this hatch's tier (the heat control hatch is the tier gate), so the same hatch fits a
 * UIV controller.
 * <p>
 * <b>Texture.</b> Base texture = multiblock casing, with GT's own fluid input pipe overlay kept so the port is
 * recognisable as a fluid input from the outside. The dedicated MessTech coolant decal is still pending, so the
 * existing {@code hatch/Hatch_Reactor_Temp_Control} panel stands in for it - replace {@link #OVERLAY_PATH} once the
 * coolant art exists.
 */
public class MTReactorCoolantInputHatch extends MTEHatchInput {

    /** Fixed tier of this hatch (EV). See the class comment for why it is not a tier family. */
    public static final int TIER = 4;

    /** Registry name of IC2's cold coolant, see {@code ic2.core.init.BlocksItems#registerIC2fluid}. */
    public static final String COOLANT_FLUID_NAME = "ic2coolant";

    private static final String OVERLAY_DOMAIN = "messtech";
    /** Decal path relative to {@code textures/blocks}, matching {@code Textures.BlockIcons.custom}. */
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
     * The IC2 coolant, or {@code null} while no fluid is registered under that name (a pack without IC2 fluids). Every
     * look-up goes through the registry and is null checked, so such a pack gets a hatch that accepts nothing instead
     * of a crash while the class loads.
     */
    public static Fluid getCoolantFluid() {
        return FluidRegistry.getFluid(COOLANT_FLUID_NAME);
    }

    /** True when the given fluid is IC2's cold coolant. */
    public static boolean isCoolant(Fluid fluid) {
        Fluid coolant = getCoolantFluid();
        return coolant != null && fluid == coolant;
    }

    /**
     * The coolant filter is fixed: this hatch is the reactor's coolant port and anything else in the tank would just be
     * in the way. It cannot be switched off (see {@link #onScrewdriverRightClick}).
     */
    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return aFluid != null && isCoolant(aFluid.getFluid());
    }

    /**
     * The GT input hatch's screwdriver switch would toggle {@code disableFilter} and announce that the filter is off
     * while {@link #isFluidInputAllowed} keeps rejecting every other fluid - a lie. The hatch therefore answers with
     * the real state instead of toggling anything.
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        GTUtility.sendChatTrans(aPlayer, "machine.mtreactor.coolanthatch.filter_fixed");
    }

    // region Coolant API used by MTReactor's fluid cycle

    /**
     * Cold coolant held by this hatch.
     *
     * @param coolant the fluid to report on; any other fluid - and an empty tank - reports 0.
     */
    public int getCoolantAmount(Fluid coolant) {
        if (mFluid == null || coolant == null) return 0;
        return mFluid.getFluid() == coolant ? mFluid.amount : 0;
    }

    /**
     * Drains at most {@code amount} mB of cold coolant out of the tank.
     *
     * @return the mB actually drained, never more than {@code amount} and never more than the tank held - the caller
     *         can rely on it the same way it relies on {@code fill}'s return value.
     */
    public int drainCoolant(Fluid coolant, int amount) {
        if (amount <= 0) return 0;
        int available = getCoolantAmount(coolant);
        if (available <= 0) return 0;
        FluidStack drained = drain(Math.min(amount, available), true);
        return drained == null ? 0 : drained.amount;
    }

    /**
     * Puts cold coolant back into the tank, for a cycle that drained coolant and could not deliver the hot coolant.
     *
     * @return the mB actually taken back, capped by the free tank space (the tank is 128,000 mB at EV)
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
     * Keeps GT's fluid pipe overlay (the base array) and layers the reactor decal on top of it. Unlike the access
     * hatch this hatch does not restrict {@code isFacingValid}: a coolant port legitimately sits on any casing face
     * including the top and bottom, fluids go in from every side anyway, and only the decal's direction would suffer.
     */
    private ITexture[] withDecal(ITexture[] aBaseTextures) {
        if (sCoolantOverlay == null) return aBaseTextures;
        ITexture[] result = Arrays.copyOf(aBaseTextures, aBaseTextures.length + 1);
        result[aBaseTextures.length] = TextureFactory.of(sCoolantOverlay);
        return result;
    }

    // endregion
}
