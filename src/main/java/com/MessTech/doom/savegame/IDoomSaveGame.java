package com.MessTech.doom.savegame;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import com.MessTech.doom.p.ThinkerList;

public interface IDoomSaveGame {

    void setThinkerList(ThinkerList li);

    boolean doLoad(DataInputStream f);

    IDoomSaveGameHeader getHeader();

    void setHeader(IDoomSaveGameHeader header);

    boolean doSave(DataOutputStream f);
}
