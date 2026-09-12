package com.MessTech.common.machine.hatch;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * Tiered reactor heat control hatch. A reactor needs exactly one of these.
 * <p>
 * The tier sets the heat ceiling of the reactor: every page of every {@link MTReactorAccessHatch} uses this value as
 * its {@code maxHeat}, so the player picks how much heat the reactor can take before it explodes. The base texture is
 * the multiblock casing; the heat control decal from
 * {@code assets/messtech/textures/blocks/hatch/Hatch_Reactor_Temp_Control.png} is layered on the front face.
 */
public class MTReactorHeatHatch extends MTEHatch {

    /** Lowest tier supported by this hatch (EV). */
    public static final int MIN_TIER = 4;
    /** Highest tier supported by this hatch (UIV). */
    public static final int MAX_TIER = 11;
    /** Heat ceiling used while no heat hatch is installed (identical to the EV hatch). */
    public static final int DEFAULT_HEAT_CAPACITY = 10_000;

    private static final String OVERLAY_DOMAIN = "messtech";
    /** Icon path relative to {@code textures/blocks}, matching {@code Textures.BlockIcons.custom}. */
    private static final String OVERLAY_PATH = "hatch/Hatch_Reactor_Temp_Control";

    /** Heat ceiling per tier, indexed by {@code tier - MIN_TIER} (EV .. UIV). */
    private static final int[] HEAT_CAPACITY = { 10_000, 20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000,
        Integer.MAX_VALUE };

    private static IIconContainer sHeatControlOverlay;

    public MTReactorHeatHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { StatCollector.translateToLocal("machine.mtreactor.heathatch.desc.0") });
    }

    public MTReactorHeatHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    /** Heat ceiling of a tier, clamped into the supported EV..UIV range. */
    public static int getHeatCapacityForTier(int aTier) {
        int index = aTier - MIN_TIER;
        if (index < 0) return HEAT_CAPACITY[0];
        if (index >= HEAT_CAPACITY.length) return HEAT_CAPACITY[HEAT_CAPACITY.length - 1];
        return HEAT_CAPACITY[index];
    }

    /** Heat ceiling this hatch grants to the reactor. */
    public int getHeatCapacity() {
        return getHeatCapacityForTier(mTier);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTReactorHeatHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocalFormatted("machine.mtreactor.heathatch.desc.0", getHeatCapacity()),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("machine.mtreactor.heathatch.desc.1"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("machine.mtreactor.heathatch.desc.2") };
    }

    /**
     * Only horizontal facings are allowed so the front decal can never end up on the top or bottom face. The base
     * tile entity defaults to {@link ForgeDirection#DOWN}, and {@code setFrontFacing} refuses to change an invalid
     * facing, so the access hatch uses the same restriction and first-tick fallback.
     */
    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing.offsetY == 0;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()
            && aBaseMetaTileEntity.getFrontFacing().offsetY != 0) {
            aBaseMetaTileEntity.setFrontFacing(ForgeDirection.NORTH);
        }
    }

    /** A heat hatch has no inventory, so every slot access must be refused. */
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
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        sHeatControlOverlay = Textures.BlockIcons.custom(OVERLAY_DOMAIN, OVERLAY_PATH);
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

    private ITexture[] withOverlay(ITexture aBaseTexture) {
        if (sHeatControlOverlay == null) {
            return new ITexture[] { aBaseTexture };
        }
        return new ITexture[] { aBaseTexture, TextureFactory.of(sHeatControlOverlay) };
    }
}
