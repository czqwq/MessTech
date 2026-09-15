package com.MessTech.common.machine.hatch;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

/**
 * Client -&gt; server actions for the reactor access hatch slot memory system.
 * <p>
 * Using one shared handler (instead of one per slot) keeps the MUI2 sync map small even for the 8-page UIV hatch.
 */
public class MTReactorSlotActionSyncHandler extends SyncHandler<MTReactorSlotActionSyncHandler> {

    /** Shift + right click on an unlocked slot: lock all slots of the same item type. */
    public static final int ACTION_LOCK_GROUP = 1;
    /** Shift + right click on a locked slot: unlock only this slot. */
    public static final int ACTION_UNLOCK_SLOT = 2;
    /** Alt + Shift + right click on a locked slot: unlock all slots of the same item type. */
    public static final int ACTION_UNLOCK_GROUP = 3;

    private final MTReactorAccessHatch hatch;

    public MTReactorSlotActionSyncHandler(MTReactorAccessHatch hatch) {
        this.hatch = hatch;
        allowC2S();
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        // Client -&gt; server only.
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        int slot = buf.readInt();
        switch (id) {
            case ACTION_LOCK_GROUP -> hatch.lockSlotGroup(slot);
            case ACTION_UNLOCK_SLOT -> hatch.unlockSlot(slot);
            case ACTION_UNLOCK_GROUP -> hatch.unlockSlotGroup(slot);
            default -> {}
        }
    }
}
