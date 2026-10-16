package com.MessTech.doom.savegame;

import com.MessTech.doom.p.ThinkerList;

public interface ILoadSaveGame {

    void setThinkerList(ThinkerList li);

    void doSave(ThinkerList li);

    void doLoad(ThinkerList li);
}
