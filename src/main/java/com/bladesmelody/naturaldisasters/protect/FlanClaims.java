package com.bladesmelody.naturaldisasters.protect;

import io.github.flemmli97.flan.claim.ClaimStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Flan (only loaded when that mod is installed). Flan claims are block areas, not whole chunks. */
final class FlanClaims {
    private FlanClaims() {}

    static boolean anyIn(ServerLevel level, int chunkX, int chunkZ) {
        return !ClaimStorage.get(level).getClaimsAt(chunkX, chunkZ).isEmpty();
    }

    static boolean claimed(ServerLevel level, BlockPos pos) {
        return ClaimStorage.get(level).getClaimAt(pos) != null;
    }
}
