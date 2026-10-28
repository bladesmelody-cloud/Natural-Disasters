package com.bladesmelody.naturaldisasters.gametest;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.protect.Protection;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import io.github.flemmli97.flan.player.ClaimMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

/**
 * Tests against the real claim mods; registered only when that mod is
 * loaded (run with {@code -PwithClaims}). Kept apart so nothing touches the
 * mods' classes when they're absent.
 */
final class ClaimTests {
    private ClaimTests() {}

    static void opacClaimIsProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(20, 3, 20));
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(level.getServer()).getServerClaimsManager();
        claims.claim(level.dimension().identifier(), UUID.nameUUIDFromBytes("naturaldisasters-test".getBytes()), 0, cx, cz, false);
        try {
            helper.assertTrue(claims.get(level.dimension().identifier(), cx, cz) != null, "OPAC didn't claim the chunk");
            helper.assertTrue(Protection.isProtected(level, pos), "OPAC claim not protected");
            BlockWriter w = new BlockWriter(level, 10, Long.MAX_VALUE);
            helper.assertTrue(!w.set(pos, Blocks.GLASS.defaultBlockState()), "set a block in an OPAC claim");
            helper.assertTrue(!Protection.isProtected(level, pos.offset(48, 0, 48)), "unclaimed chunk protected");
        } finally {
            claims.unclaim(level.dimension().identifier(), cx, cz);
        }
        helper.succeed();
    }

    static void flanClaimIsProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(16, 1, 16)), b = helper.absolutePos(new BlockPos(24, 1, 24));
        ClaimStorage storage = ClaimStorage.get(level);
        Claim claim = storage.createAdminClaim(a, b, level, false);
        try {
            helper.assertTrue(claim != null, "Flan didn't create the claim");
            BlockPos inside = helper.absolutePos(new BlockPos(20, 3, 20));
            helper.assertTrue(Protection.isProtected(level, inside), "Flan claim not protected");
            helper.assertTrue(!Protection.isProtected(level, helper.absolutePos(new BlockPos(30, 3, 30))), "outside the Flan claim protected");
            BlockWriter w = new BlockWriter(level, 10, Long.MAX_VALUE);
            helper.assertTrue(!w.set(inside, Blocks.GLASS.defaultBlockState()), "set a block in a Flan claim");
        } finally {
            if (claim != null) storage.deleteClaim(claim, true, ClaimMode.DEFAULT, level);
        }
        helper.succeed();
    }
}
