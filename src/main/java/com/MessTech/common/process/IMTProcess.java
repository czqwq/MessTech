package com.MessTech.common.process;

/**
 * 1:1 port of Draconic Evolution's {@code com.brandon3055.draconicevolution.common.utils.handlers.IProcess}
 * (see {@code tmp/Draconic-Evolution-master}). A process is a task that is ticked once per server tick and removes
 * itself by returning true from {@link #isDead()}.
 */
public interface IMTProcess {

    void updateProcess();

    boolean isDead();
}
