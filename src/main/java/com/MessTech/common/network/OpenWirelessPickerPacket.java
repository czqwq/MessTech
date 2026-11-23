package com.MessTech.common.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.MessTech.common.network.base.ServerboundPacket;
import com.MessTech.common.wireless.gui.MTWirelessNetworkPickerGui;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

/**
 * Client to server: open the wireless ME network picker for the binding tool the player carries.
 * <p>
 * The picker is normally opened by right-clicking the air with the tool, but a tool worn in its Baubles accessory slot
 * never sees an item right click, so the picker key bind ({@code MTWirelessKeyBindings}) sends this packet instead.
 * GTLCore's {@code OpenBindingPickerPacket} is the same packet for its Curios slot.
 * <p>
 * The picker is built by MUI2 on the server (and mirrored on the client), so it has to be opened on the server thread,
 * not on Netty's.
 */
public class OpenWirelessPickerPacket extends ServerboundPacket {

    @Override
    public void handleServer(EntityPlayerMP player) {
        ServerThreadUtil.addScheduledTask(() -> {
            try {
                MTWirelessNetworkPickerGui.open(player);
            } catch (IllegalStateException e) {
                // The server is shutting down, so there is no state left to open a GUI for.
            }
        });
    }
}
