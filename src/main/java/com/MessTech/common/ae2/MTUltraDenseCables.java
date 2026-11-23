package com.MessTech.common.ae2;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.util.AEColor;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Registration entry point of the two ultra dense cables (超致密压缩ME线缆 / 压缩ME玻璃线缆) and the item stack helpers
 * the GT compressor recipes are built from. See {@code docs/ultra-ae2.md} (§0, §1.5, §2 T-B).
 * <p>
 * Both items carry all 17 {@link AEColor#VALUES} variants as item damage ({@link AEColor#ordinal()}) and are AE2
 * {@link appeng.api.parts.IPartItem}s whose part reads the colour back from that damage value. The parts are built on
 * top of AE2's own dense / glass cable parts, so connections, rendering, channel display and the cable bus NBT
 * round-trip all stay AE2's; only the channel capacity and the reported item stack are ours.
 */
public final class MTUltraDenseCables {

    /** 超致密压缩ME线缆 item; its 17 damage values are {@link AEColor#ordinal()}. */
    private static MTUltraDenseCableItem ultraDenseCable;
    /** 压缩ME玻璃线缆 item; its 17 damage values are {@link AEColor#ordinal()}. */
    private static MTCompressedGlassCableItem compressedGlassCable;

    private MTUltraDenseCables() {}

    /**
     * Creates and registers both items. Called from {@code MTUltraAeContent#preInit}, i.e.
     * {@code CommonProxy#preInit}, after AE2's own preInit (which creates the creative tab the items use).
     */
    public static void preInit() {
        ultraDenseCable = new MTUltraDenseCableItem();
        GameRegistry.registerItem(ultraDenseCable, "mtUltraDenseCable");

        compressedGlassCable = new MTCompressedGlassCableItem();
        GameRegistry.registerItem(compressedGlassCable, "mtCompressedGlassCable");
    }

    /**
     * @param color one of {@link AEColor#VALUES}
     * @return one 超致密压缩ME线缆 of that colour
     */
    public static ItemStack ultraDenseStack(AEColor color) {
        return new ItemStack(ultraDenseCable, 1, color.ordinal());
    }

    /**
     * @param color one of {@link AEColor#VALUES}
     * @return one 压缩ME玻璃线缆 of that colour
     */
    public static ItemStack compressedGlassStack(AEColor color) {
        return new ItemStack(compressedGlassCable, 1, color.ordinal());
    }

    /**
     * @return AE2's own dense cable stack of that colour, the compressor recipe input
     */
    public static ItemStack ae2DenseCable(AEColor color, int amount) {
        return AEApi.instance()
            .definitions()
            .parts()
            .cableDense()
            .stack(color, amount);
    }

    /**
     * @return AE2's own glass cable stack of that colour, the compressor recipe input
     */
    public static ItemStack ae2GlassCable(AEColor color, int amount) {
        return AEApi.instance()
            .definitions()
            .parts()
            .cableGlass()
            .stack(color, amount);
    }

    /** @return all 17 AE2 colours in {@link AEColor#VALUES} order (White..Black, then Transparent). */
    public static List<AEColor> colors() {
        return Arrays.asList(AEColor.VALUES);
    }

    /**
     * Colour stored in an item damage value, also used when the part rebuilds its item stack. Out-of-range damage
     * (e.g. an externally edited NBT tag) falls back to {@link AEColor#Transparent} instead of throwing.
     */
    static AEColor colorFromDamage(int damage) {
        final AEColor[] values = AEColor.VALUES;
        return damage >= 0 && damage < values.length ? values[damage] : AEColor.Transparent;
    }
}
