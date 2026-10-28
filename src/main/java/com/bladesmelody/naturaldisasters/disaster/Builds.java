package com.bladesmelody.naturaldisasters.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.Tags;

/**
 * Rough "how built-up is this spot" score, used so random disasters prefer
 * to strike away from bases and villages. Only looks at loaded chunks.
 */
public final class Builds {
    private Builds() {}

    /** 0 = nothing built nearby; higher = more signs of buildings. */
    public static int score(ServerLevel level, BlockPos center, int radius) {
        int score = 0;
        // Beds, workstations, bells, nether portals... (villages and player bases).
        score += (int) Math.min(50, level.getPoiManager().getCountInRange(t -> true, center, radius, PoiManager.Occupancy.ANY));
        score += blockEntities(level, center, radius);
        score += sampledBlocks(level, center, radius);
        return score;
    }

    /** Chests, furnaces, signs etc. near the surface (not deep cave loot). */
    private static int blockEntities(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        long r2 = (long) radius * radius;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos p : chunk.getBlockEntities().keySet()) {
                    long dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
                    if (dx * dx + dz * dz > r2) continue;
                    if (p.getY() < Terrain.ground(level, p.getX(), p.getZ()) - 8) continue;
                    if (++n >= 50) return n;
                }
            }
        }
        return n;
    }

    /** Samples the top blocks of random columns for building materials. */
    private static int sampledBlocks(ServerLevel level, BlockPos center, int radius) {
        RandomSource r = RandomSource.create(center.asLong());
        int n = 0;
        for (int i = 0; i < 96; i++) {
            BlockPos c = Terrain.randomColumn(r, center.getX() + 0.5, center.getZ() + 0.5, radius);
            if (!Terrain.loaded(level, c.getX(), c.getZ())) continue;
            int top = Terrain.surface(level, c.getX(), c.getZ());
            for (int y = top; y > top - 6; y--) {
                if (isBuildBlock(level.getBlockState(c.atY(y)))) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    static boolean isBuildBlock(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)
                || s.is(BlockTags.DOORS) || s.is(BlockTags.BEDS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
                || s.is(BlockTags.WOODEN_FENCES) || s.is(Blocks.BRICKS) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.TORCH)
                || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.FARMLAND)
                || s.is(Blocks.DIRT_PATH);
    }
}
