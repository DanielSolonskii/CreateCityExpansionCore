package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalRegion;

interface MineGeologyAccess {

    boolean storageAvailable();

    GeologicalRegion getRegion();
}
