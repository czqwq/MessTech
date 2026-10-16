package com.MessTech.common.gameconsole;

import com.MessTech.doom.DoomFatalError;
import com.MessTech.doom.doom.DoomMain;
import com.MessTech.doom.doom.ticcmd_t;
import com.MessTech.doom.i.IDoomSystem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The engine's I_Quit / I_Error / I_GetHeapSize implementation for the embedded console.
 * <p>
 * Mocha Doom's own {@code i.DoomSystem} answers all of these with {@code System.exit}, and its
 * {@code GenerateAlert} opens an AWT message box. Neither is acceptable inside Minecraft, so the
 * quit path only asks the host to stop and every error becomes a {@link DoomFatalError}, which the
 * host catches and shows in the console's window instead of taking the game down.
 */
@SideOnly(Side.CLIENT)
final class MTGameConsoleSystem implements IDoomSystem {

    /** Mocha Doom's own zone size, in bytes (upstream's "mb_used" of 6 MB, given some headroom). */
    private static final int ZONE_BYTES = 8 * 1024 * 1024;

    private final DoomMain<?, ?> doom;
    private final MTGameConsoleHost host;
    private final ticcmd_t emptyCommand = new ticcmd_t();

    MTGameConsoleSystem(final DoomMain<?, ?> doom, final MTGameConsoleHost host) {
        this.doom = doom;
        this.host = host;
    }

    @Override
    public void Tactile(final int on, final int off, final int total) {
        // Unused upstream too: no rumble hardware to talk to.
    }

    @Override
    public ticcmd_t BaseTiccmd() {
        return emptyCommand;
    }

    @Override
    public int GetHeapSize() {
        return ZONE_BYTES;
    }

    @Override
    public byte[] ZoneBase(final int size) {
        return new byte[ZONE_BYTES];
    }

    /**
     * I_Quit - reached from Doom's own QUIT GAME entry. In the console that means "close the
     * window", not "end the process": the host stops the loop and the GUI tears itself down.
     */
    @Override
    public void Quit() {
        doom.commit();
        host.requestStop();
    }

    @Override
    public void Init() {
        // I_InitGraphics / I_InitSound are the host's business, not the engine's.
    }

    @Override
    public void WaitVBL(final int count) {
        try {
            Thread.sleep(Math.max(0, count) * 1000L / 70L);
        } catch (final InterruptedException e) {
            // The host is stopping the engine.
            Thread.currentThread()
                .interrupt();
        }
    }

    @Override
    public void BeginRead() {
        // The disk-reading icon the original engine drew in a corner of the screen.
    }

    @Override
    public void EndRead() {
        // See BeginRead.
    }

    @Override
    public void AllocLow(final int length) {
        // DOS memory model leftover.
    }

    @Override
    public void Error(final String error, final Object... args) {
        throw new DoomFatalError(String.format(error, args));
    }

    @Override
    public void Error(final String error) {
        throw new DoomFatalError(error);
    }

    /**
     * Reached when a PWAD was supplied and the game wants to warn about it. The console never adds
     * one, so this is only here so that the "declined" branch of the engine - which ends in
     * {@code System.exit(-2)} - can never be taken. Saying yes is the honest answer.
     */
    @Override
    public boolean GenerateAlert(final String title, final String cause) {
        return true;
    }
}
