package com.MessTech.common.util;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;

import org.jetbrains.annotations.Nullable;

import com.MessTech.init.MessTech;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/**
 * Structure element helper for blocks that another mod's config may leave unregistered.
 * <p>
 * GTNH 2.9.0 ships an Et Futurum config that turns the 1.8 trapdoors and the granite/diorite/andesite stones off
 * ({@code config/etfuturum/blocksitems.cfg}: {@code enableIronTrapdoor}, {@code enableVanillaTrapdoors} and
 * {@code enableStones}). The block objects of those entries still exist in Et Futurum's {@code ModBlocks} enum, but
 * they are never registered, so {@link Item#getItemFromBlock(Block)} returns null for them and StructureLib's
 * autoplace throws an {@link IllegalArgumentException} from {@code StructureUtility.survivalPlaceBlock} - which
 * crashed the NEI multiblock preview of the Protocol Core tower (and would crash a survival build in game).
 * <p>
 * {@link #ofBlockOrAir(BlockCandidate...)} keeps the dump's preferred block while it is registered with an
 * {@link ItemBlock}; otherwise it uses the first alternative that is (the GTNH available equivalent of the same
 * decoration), and finally degrades the position to air so the structure definition never hands StructureLib an
 * item-less stack.
 */
public final class MTStructureBlocks {

    private MTStructureBlocks() {}

    /**
     * One block+meta candidate for {@link #ofBlockOrAir}: either a direct block reference, or a registry name for a
     * mod that is not a compile dependency (looked up when the candidate is resolved, null when the mod is absent).
     */
    public record BlockCandidate(@Nullable Block block, @Nullable String registryName, int meta) {

        /** Candidate from a block reference, e.g. {@code ModBlocks.IRON_TRAPDOOR.get()}. */
        public static BlockCandidate of(@Nullable Block block, int meta) {
            return new BlockCandidate(block, null, meta);
        }

        /** Candidate from a registry name, e.g. {@code "malisisdoors:iron_trapdoor"}. */
        public static BlockCandidate named(@Nullable String registryName, int meta) {
            return new BlockCandidate(null, registryName, meta);
        }

        @Nullable
        private Block resolve() {
            if (block != null) return block;
            return registryName == null ? null : Block.getBlockFromName(registryName);
        }

        private String describe() {
            if (block != null) return block.getUnlocalizedName() + ":" + meta;
            return registryName + ":" + meta;
        }
    }

    /**
     * Structure element for a decoration block whose preferred block may be disabled by another mod's config: the
     * first candidate that is registered with an {@link ItemBlock} wins, and when none of them can be placed the
     * position becomes air (accepted the same way as StructureLib's own {@code -} marker / {@link
     * StructureUtility#isAir()}).
     */
    public static <T> IStructureElement<T> ofBlockOrAir(BlockCandidate... candidates) {
        for (BlockCandidate candidate : candidates) {
            Block block = candidate.resolve();
            if (block == null || !(Item.getItemFromBlock(block) instanceof ItemBlock)) continue;
            return StructureUtility.ofBlock(block, candidate.meta());
        }
        MessTech.MT_LOG.warn(
            "No placeable block for a structure element (candidates: {}); using air so the structure can still form",
            describeAll(candidates));
        return StructureUtility.isAir();
    }

    private static String describeAll(BlockCandidate[] candidates) {
        StringBuilder builder = new StringBuilder();
        for (BlockCandidate candidate : candidates) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(candidate.describe());
        }
        return builder.toString();
    }
}
