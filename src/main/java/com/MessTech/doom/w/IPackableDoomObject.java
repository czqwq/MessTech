package com.MessTech.doom.w;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface IPackableDoomObject {

    public void pack(ByteBuffer buf) throws IOException;
}
