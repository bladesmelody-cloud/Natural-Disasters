package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small world helpers shared by disasters. All of them refuse unloaded chunks. */
public final class Terrain {
    private Terrain() {}

    /** A random column (y = 0) within {@code radius} of {@code (cx, cz)}, uniform over the disc. */
    public static BlockPos randomColumn(RandomSource random, double cx, double cz, double radius) {
        double a = random.nextDouble() * Math.PI * 2;
        double d = radius * Math.sqrt(random.nextDouble());
        return new BlockPos((int) Math.floor(cx + Math.cos(a) * d), 0, (int) Math.floor(cz + Math.sin(a) * d));
    }

    public static boolean loaded(ServerLevel level, int x, int z) {
        return BlockWriter.isLoaded(level, new BlockPos(x, level.getMinY(), z));
    }

    /** Y of the highest motion-blocking block (or fluid) in a loaded column. */
    public static int top(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }

    /** Y of the highest solid block, ignoring leaves (ground under trees). */
    public static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Y of the highest non-air block of any kind (plants and snow layers included). */
    public static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    /** Air or something that can be replaced and isn't a fluid (grass, flowers, snow layers). */
    public static boolean empty(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /** Horizontal distance between two points. */
    public static double dist(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
