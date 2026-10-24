package com.MessTech.common.process;

/**
 * 1:1 port of Draconic Evolution's {@code com.brandon3055.draconicevolution.common.utils.handlers.IProcess} (see
 * {@code tmp/Draconic-Evolution-master}): a task ticked once per server tick that removes itself by returning true from
 * {@link #isDead()}.
 */
public interface IMTProcess {

    void updateProcess();

    boolean isDead();
}
