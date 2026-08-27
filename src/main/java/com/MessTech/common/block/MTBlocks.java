package com.MessTech.common.block;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registry of the standalone structure blocks of MessTech.
 * <p>
 * The two Assembly Matrix blocks act as the tiered 'I' structure element of
 * {@code com.MessTech.common.machine.MTAssFactory}: {@link #assMatrixBlock} is Tier 1,
 * {@link #advAssMatrixBlock} is Tier 2.
 */
public final class MTBlocks {

    /** Assembly Matrix Block, Tier 1. */
    public static final AssMatrixBlock assMatrixBlock = new AssMatrixBlock();
    /** Advanced Assembly Matrix Block, Tier 2. */
    public static final AdvAssMatrixBlock advAssMatrixBlock = new AdvAssMatrixBlock();

    private MTBlocks() {}

    /** Registers the blocks into the {@link GameRegistry}. Called before machine registration. */
    public static void registerBlocks() {
        GameRegistry.registerBlock(assMatrixBlock, "AssMatrixBlock");
        GameRegistry.registerBlock(advAssMatrixBlock, "AdvAssMatrixBlock");
    }
}
