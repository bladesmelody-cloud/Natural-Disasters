package com.bladesmelody.naturaldisasters.protect;

import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.server.api.OpenPACServerAPI;

/** Open Parties and Claims (only loaded when that mod is installed). */
final class OpacClaims {
    private OpacClaims() {}

    static boolean claimed(ServerLevel level, int chunkX, int chunkZ) {
        return OpenPACServerAPI.get(level.getServer()).getServerClaimsManager()
                .get(level.dimension().identifier(), chunkX, chunkZ) != null;
    }
}
