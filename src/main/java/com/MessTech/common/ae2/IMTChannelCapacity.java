package com.MessTech.common.ae2;

/**
 * Implemented by the AE2 grid nodes whose channel capacity differs from AE2's native 8/32.
 * <p>
 * AE2 computes a node's capacity in {@code appeng.me.GridNode#getMaxChannels()} from
 * {@code CHANNEL_COUNT[compressedData & 0x3]} ({@code {0, 8, 32, Integer.MAX_VALUE}}), where the third value is only
 * used when the channel feature is disabled. The MessTech mixin
 * {@code MixinGridNode_ChannelCapacity} makes that method consult this interface through the node's machine instead, so
 * a cable part can carry 1024 or {@link Integer#MAX_VALUE} channels.
 */
public interface IMTChannelCapacity {

    /** @return the number of channels this node can carry. */
    int mtChannelCapacity();
}
