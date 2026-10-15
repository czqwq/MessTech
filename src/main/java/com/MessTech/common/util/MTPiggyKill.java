package com.MessTech.common.util;

/**
 * The piggy's branch of the {@link MTKillBase} ladder: whatever the thrown piggy lands on dies for good, the player
 * that threw it into the sky included.
 * <p>
 * Nothing is added to the ladder here and nothing is refused - {@link #accepts} is not overridden on purpose. The
 * piggy is thrown *at* a target, so the thrower is a target like any other: the piggy that a player throws straight
 * up has to be able to kill them on the way down, and a branch that exempted its own thrower would make that
 * impossible.
 * <p>
 * The messages are its own, which is the whole reason the ladder has branches: a death by piggy is announced as a
 * knock flying, and a target that survives everything tells the thrower that even the piggy cannot chew through it.
 */
public final class MTPiggyKill extends MTKillBase {

    /** The one branch instance; the ladder holds no state per kill, so one is enough. */
    public static final MTPiggyKill INSTANCE = new MTPiggyKill();

    private MTPiggyKill() {
        super("Piggy", "death.attack.messtech.piggy", "messtech.piggy.kill.failed");
    }
}
