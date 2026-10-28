package com.bladesmelody.naturaldisasters.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/** Where random disasters fit, by climate and terrain. */
public final class Places {
    private Places() {}

    /** Any dimension with a sky (not the Nether). */
    public static boolean openSky(ServerLevel level, BlockPos pos) {
        return !level.dimensionType().hasCeiling() && level.dimensionType().hasSkyLight();
    }

    private static Biome biome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).value();
    }

    /** Snowy or cold places. */
    public static boolean cold(ServerLevel level, BlockPos pos) {
        return openSky(level, pos) && biome(level, pos).getBaseTemperature() < 0.3F;
    }

    /** Places where it rains (not deserts or snow). */
    public static boolean wet(ServerLevel level, BlockPos pos) {
        Biome b = biome(level, pos);
        return openSky(level, pos) && b.hasPrecipitation() && b.getBaseTemperature() >= 0.3F;
    }

    /** Hot or dry places: deserts, savannas, badlands. */
    public static boolean hot(ServerLevel level, BlockPos pos) {
        Biome b = biome(level, pos);
        return openSky(level, pos) && (b.getBaseTemperature() >= 1.0F || !b.hasPrecipitation() && b.getBaseTemperature() > 0.5F);
    }

    /** Warm places (plains and hotter), where a drought makes sense. */
    public static boolean warm(ServerLevel level, BlockPos pos) {
        return openSky(level, pos) && biome(level, pos).getBaseTemperature() >= 0.5F;
    }

    /** Within reach of an ocean: samples biomes in a ring around the spot. */
    public static boolean nearOcean(ServerLevel level, BlockPos pos) {
        if (!openSky(level, pos)) return false;
        for (int r = 32; r <= 160; r += 64) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                BlockPos p = pos.offset((int) (Math.cos(a) * r), 0, (int) (Math.sin(a) * r));
                if (level.isLoaded(p) && level.getBiome(p).is(BiomeTags.IS_OCEAN)) return true;
            }
        }
        return false;
    }

    /** Wooded places that can burn. */
    public static boolean forest(ServerLevel level, BlockPos pos) {
        return openSky(level, pos) && (level.getBiome(pos).is(BiomeTags.IS_FOREST) || level.getBiome(pos).is(BiomeTags.IS_TAIGA)
                || level.getBiome(pos).is(BiomeTags.IS_SAVANNA) || level.getBiome(pos).is(BiomeTags.IS_JUNGLE));
    }
}
