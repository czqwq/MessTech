package com.MessTech.doom.timing;

import static com.MessTech.doom.doom.SourceCode.I_IBM.*;

import com.MessTech.doom.doom.CVarManager;
import com.MessTech.doom.doom.CommandVariable;
import com.MessTech.doom.doom.SourceCode.I_IBM;

public interface ITicker {

    static ITicker createTicker(CVarManager CVM) {
        if (CVM.bool(CommandVariable.MILLIS)) {
            return new MilliTicker();
        } else if (CVM.bool(CommandVariable.FASTTIC) || CVM.bool(CommandVariable.FASTDEMO)) {
            return new DelegateTicker();
        } else {
            return new NanoTicker();
        }
    }

    @I_IBM.C(I_GetTime)
    public int GetTime();
}
