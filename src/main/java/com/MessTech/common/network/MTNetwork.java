package com.MessTech.common.network;

import com.MessTech.common.network.base.PayloadHandler;
import com.MessTech.common.network.base.ServerboundPacket;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * MessTech's one network channel, modelled on GT-Not-Leisure's {@code NetWorkHandler}.
 * <p>
 * Packets are numbered in registration order, so a new packet must be appended (never inserted) once a world has
 * been played with the previous build.
 */
public final class MTNetwork {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("MessTech");

    private static int nextPacketId;
    private static boolean initialized;

    private MTNetwork() {}

    public static synchronized void register() {
        if (initialized) return;
        initialized = true;
        nextPacketId = 0;
        registerServerbound(PatternImportHandler.class);
    }

    public static <T extends ServerboundPacket> void registerServerbound(Class<T> packet) {
        registerServerbound(PayloadHandler.Server.class, packet);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends ServerboundPacket> void registerServerbound(Class<? extends IMessageHandler> handler,
        Class<T> packet) {
        NETWORK.registerMessage((Class) handler, packet, nextPacketId++, Side.SERVER);
    }
}
