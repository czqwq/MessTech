package com.MessTech.common.network.base;

import net.minecraft.entity.player.EntityPlayerMP;

import com.MessTech.init.MessTech;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * Base of every packet MessTech sends from a client to the server.
 * <p>
 * Ported unchanged from GT-Not-Leisure's {@code com.science.gtnl.common.packet.base.ServerboundPacket}: a malformed
 * payload is marked invalid instead of throwing out of Netty's decode loop, and the concrete packet only has to
 * implement {@link #read(ByteBuf)}, {@link #write(ByteBuf)} and {@link #handleServer(EntityPlayerMP)}.
 */
public abstract class ServerboundPacket implements IMessage {

    private boolean invalid;

    @Override
    public final void fromBytes(ByteBuf buf) {
        try {
            this.read(buf);
        } catch (RuntimeException e) {
            invalidateMalformed(buf, e);
        }
    }

    @Override
    public final void toBytes(ByteBuf buf) {
        this.write(buf);
    }

    protected void read(ByteBuf buf) {}

    protected void write(ByteBuf buf) {}

    protected final void invalidateMalformed(ByteBuf buf, RuntimeException exception) {
        this.invalid = true;
        MessTech.LOG.warn(
            "Discarding malformed serverbound packet {} with {} readable bytes",
            getClass().getName(),
            buf.readableBytes(),
            exception);
    }

    public final boolean isInvalid() {
        return this.invalid;
    }

    public abstract void handleServer(EntityPlayerMP player);
}
