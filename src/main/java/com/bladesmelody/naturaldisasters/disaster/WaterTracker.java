package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Remembers which water a disaster placed (one vertical range per column), so
 * it can drain exactly that water later and never real lakes or the sea.
 * Water is placed and drained quietly (it never starts flowing), so a flood
 * keeps the exact shape it was given; waking it while draining would let the
 * rest pour into drained cells and form new sources that never go away.
 */
public final class WaterTracker {
    private final Long2LongOpenHashMap ranges = new Long2LongOpenHashMap();

    private static long key(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    private static long range(int from, int to) {
        return ((long) from << 32) | (to & 0xFFFFFFFFL);
    }

    /** Fills empty blocks from {@code fromY} up to {@code toY} with water; stops at the first solid block. */
    public void fill(BlockWriter w, int x, int z, int fromY, int toY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        int placedFrom = Integer.MAX_VALUE, placedTo = Integer.MIN_VALUE;
        for (int y = fromY; y <= toY && w.hasBudget(); y++) {
            pos.setY(y);
            BlockState state = w.level().getBlockState(pos);
            if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource()) continue;
            if (!Terrain.empty(state)) break;
            if (w.setQuiet(pos, Blocks.WATER.defaultBlockState())) {
                placedFrom = Math.min(placedFrom, y);
                placedTo = Math.max(placedTo, y);
            }
        }
        if (placedFrom <= placedTo) {
            add(x, z, placedFrom, placedTo);
            drown(w, new BlockPos(x, placedFrom - 1, z));
        }
    }

    /**
     * Grass-like ground under water turns to dirt straight away. Left alone it
     * would do that on a random tick with a full block update, which wakes the
     * quiet water above and sets it flowing.
     */
    private static void drown(BlockWriter w, BlockPos ground) {
        BlockState g = w.level().getBlockState(ground);
        if (g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.PODZOL) || g.is(Blocks.MYCELIUM)) {
            w.setQuiet(ground, Blocks.DIRT.defaultBlockState());
        }
    }

    private void add(int x, int z, int from, int to) {
        long k = key(x, z);
        if (ranges.containsKey(k)) {
            long old = ranges.get(k);
            from = Math.min(from, (int) (old >> 32));
            to = Math.max(to, (int) old);
        }
        ranges.put(k, range(from, to));
    }

    public boolean has(int x, int z) {
        return ranges.containsKey(key(x, z));
    }

    /** Lowest y this tracker put water at in a column. */
    public int bottom(int x, int z) {
        return (int) (ranges.get(key(x, z)) >> 32);
    }

    /**
     * Drains the water placed in one column (top down) if it is still water.
     * Returns false if it ran out of budget part way; call again to finish.
     */
    public boolean drain(BlockWriter w, int x, int z) {
        long k = key(x, z);
        if (!ranges.containsKey(k)) return true;
        long r = ranges.get(k);
        int from = (int) (r >> 32), to = (int) r;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, to, z);
        for (int y = to; y >= from; y--) {
            if (!w.hasBudget()) {
                ranges.put(k, range(from, y));
                return false;
            }
            pos.setY(y);
            if (w.level().getFluidState(pos).is(Fluids.WATER)) w.setQuiet(pos, Blocks.AIR.defaultBlockState());
        }
        ranges.remove(k);
        cleanLeaks(w, x, z, from, to);
        return true;
    }

    /**
     * Placed water that got woken anyway (lightning fire, a player's block next
     * to it) flows into neighbouring columns and can form new sources there.
     * Water in an untracked neighbour within the drained column's height range
     * can only have come from us — real lakes and the sea sit below it — so it
     * goes too.
     */
    private void cleanLeaks(BlockWriter w, int x, int z, int from, int to) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : sides) {
            int nx = x + d[0], nz = z + d[1];
            if (ranges.containsKey(key(nx, nz)) || !Terrain.loaded(w.level(), nx, nz)) continue;
            for (int y = to; y >= from; y--) {
                pos.set(nx, y, nz);
                if (w.level().getFluidState(pos).is(Fluids.WATER) && w.level().getBlockState(pos).is(Blocks.WATER)) {
                    w.setQuiet(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** Drains placed water above {@code level} in one column (for a falling flood). */
    public void drainAbove(BlockWriter w, int x, int z, int level) {
        long k = key(x, z);
        if (!ranges.containsKey(k)) return;
        long r = ranges.get(k);
        int from = (int) (r >> 32), to = (int) r;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, to, z);
        int y = to;
        for (; y > level && y >= from && w.hasBudget(); y--) {
            pos.setY(y);
            if (w.level().getFluidState(pos).is(Fluids.WATER)) w.setQuiet(pos, Blocks.AIR.defaultBlockState());
        }
        if (y < from) {
            ranges.remove(k);
            cleanLeaks(w, x, z, from, to);
        } else {
            ranges.put(k, range(from, y));
        }
    }

    /** Stops tracking a column; whatever water is left there stays for good (puddles). */
    public void forget(int x, int z) {
        ranges.remove(key(x, z));
    }

    public int size() {
        return ranges.size();
    }

    /** Any one tracked column (for draining everything), or null if empty. */
    public long[] anyColumn() {
        for (Long2LongMap.Entry e : ranges.long2LongEntrySet()) {
            BlockPos p = BlockPos.of(e.getLongKey());
            return new long[] {p.getX(), p.getZ()};
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        long[] keys = ranges.keySet().toLongArray();
        java.util.Arrays.sort(keys);
        long[] values = new long[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = ranges.get(keys[i]);
        t.putLongArray("keys", keys);
        t.putLongArray("ranges", values);
        return t;
    }

    public void load(CompoundTag t) {
        ranges.clear();
        long[] keys = t.getLongArray("keys").orElse(new long[0]);
        long[] values = t.getLongArray("ranges").orElse(new long[0]);
        for (int i = 0; i < Math.min(keys.length, values.length); i++) ranges.put(keys[i], values[i]);
    }
}
