package com.MessTech.common.ae2;

/**
 * Marker for the ultra dense ME controller blocks.
 * <p>
 * AE2's channel assignment limits every controller face to {@code CONTROLLER_FACE_CHANNELS = 32} in
 * {@code appeng.me.pathfinding.PathingCalculation}. The MessTech mixins
 * {@code MixinPathingCalculation_UltraDense} / {@code MixinControllerFace_UltraDense} detect that a grid contains a
 * controller whose block implements this interface and raise that grid's controller face capacity to
 * {@link Integer#MAX_VALUE} (the requested 2.1G channels per face).
 */
public interface IMTUltraDenseControllerBlock {
}
