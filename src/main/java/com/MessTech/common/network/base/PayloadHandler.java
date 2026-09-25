package com.MessTech.common.network.base;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.Nullable;

import com.MessTech.init.MessTech;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * The FML side of the packet framework, ported unchanged from GT-Not-Leisure's
 * {@code com.science.gtnl.common.packet.base.PayloadHandler} (client-bound half dropped, MessTech only talks
 * upwards).
 */
public final class PayloadHandler {

    private PayloadHandler() {}

    public static final class Server<T extends ServerboundPacket> implements IMessageHandler<T, IMessage> {

        @Override
        public @Nullable IMessage onMessage(T message, MessageContext ctx) {
            if (message.isInvalid()) {
                return null;
            }
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            try {
                message.handleServer(player);
            } catch (RuntimeException exception) {
                MessTech.LOG.error(
                    "Unhandled serverbound packet {}",
                    message.getClass()
                        .getName(),
                    exception);
                throw exception;
            }
            return null;
        }
    }
}
