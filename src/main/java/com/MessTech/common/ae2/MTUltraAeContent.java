package com.MessTech.common.ae2;

/**
 * Wiring for the ultra dense AE2 content (controllers and cables), the 1.7.10/GTNH counterpart of the requested
 * "超致密" additions. See {@code docs/ultra-ae2.md} for the full design.
 * <p>
 * The content is AE2 based (not GregTech), so it lives outside the {@code MTMachineLoader}/GT machine registry and is
 * wired from the same places the wireless ME network content uses: {@code CommonProxy#preInit} for the items and
 * {@code MTBlocks#registerBlocks} for the blocks.
 */
public final class MTUltraAeContent {

    private MTUltraAeContent() {}

    /** Items and other preInit-time setup; called from {@code CommonProxy#preInit}. */
    public static void preInit() {
        MTUltraDenseCables.preInit();
    }

    /** Blocks; called from {@code MTBlocks#registerBlocks} (postInit, after preInit). */
    public static void registerBlocks() {
        MTUltraDenseControllers.registerBlocks();
    }
}
