package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
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
    /** Every column and height range water was ever placed in (until forgotten): what {@link #settle} sweeps. */
    private final Long2LongOpenHashMap placed = new Long2LongOpenHashMap();
    /** Sweep state: when it started (game time, -1 = not yet), which pass, and how far through it. */
    private long settleStart = -1;
    private int settlePass;
    private it.unimi.dsi.fastutil.longs.LongArrayList settleQueue;

    /**
     * Trackers with water placed, and the box around it. Within it no new
     * water sources can form while the disaster lasts: woken water then can't
     * make anything permanent, it just flows and recedes.
     */
    private static final java.util.Set<WaterTracker> ACTIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /** How far around placed water sources can't form (water flows 7 blocks; falls further). */
    private static final int NO_SOURCE_MARGIN = 16;
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE,
            minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

    private void bind(BlockWriter w) {
        if (dimension == null) dimension = w.level().dimension();
        if (!placed.isEmpty()) ACTIVE.add(this);
    }

    private void grow(int x, int z, int from, int to) {
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minZ = Math.min(minZ, z);
        maxZ = Math.max(maxZ, z);
        minY = Math.min(minY, from);
        maxY = Math.max(maxY, to);
    }

    private boolean nearPlaced(net.minecraft.world.level.Level level, BlockPos pos) {
        return !placed.isEmpty() && level.dimension() == dimension
                && pos.getX() >= minX - NO_SOURCE_MARGIN && pos.getX() <= maxX + NO_SOURCE_MARGIN
                && pos.getZ() >= minZ - NO_SOURCE_MARGIN && pos.getZ() <= maxZ + NO_SOURCE_MARGIN
                && pos.getY() >= minY - NO_SOURCE_MARGIN && pos.getY() <= maxY + 1;
    }

    /** Water may not turn into a new source near a disaster's own water. */
    public static void onCreateFluidSource(net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent event) {
        if (ACTIVE.isEmpty() || !event.getFluidState().is(FluidTags.WATER)) return;
        for (WaterTracker t : ACTIVE) {
            if (t.nearPlaced(event.getLevel(), event.getPos())) {
                event.setCanConvert(false);
                return;
            }
        }
    }

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    /** The disaster is over (or was stopped): its area is no longer kept free of new sources. */
    public void release() {
        ACTIVE.remove(this);
    }

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
            bind(w);
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
        widen(ranges, x, z, from, to);
        widen(placed, x, z, from, to);
        grow(x, z, from, to);
    }

    private static void widen(Long2LongOpenHashMap map, int x, int z, int from, int to) {
        long k = key(x, z);
        if (map.containsKey(k)) {
            long old = map.get(k);
            from = Math.min(from, (int) (old >> 32));
            to = Math.max(to, (int) old);
        }
        map.put(k, range(from, to));
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
        bind(w);
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
            if (w.level().getFluidState(pos).is(FluidTags.WATER)) w.setQuiet(pos, Blocks.AIR.defaultBlockState());
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
            if (ranges.containsKey(key(nx, nz)) || placed.containsKey(key(nx, nz)) || !Terrain.loaded(w.level(), nx, nz)) continue;
            for (int y = to; y >= from; y--) {
                pos.set(nx, y, nz);
                if (w.level().getFluidState(pos).is(FluidTags.WATER) && w.level().getBlockState(pos).is(Blocks.WATER)) {
                    w.setQuiet(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** Drains placed water above {@code level} in one column (for a falling flood). */
    public void drainAbove(BlockWriter w, int x, int z, int level) {
        bind(w);
        long k = key(x, z);
        if (!ranges.containsKey(k)) return;
        long r = ranges.get(k);
        int from = (int) (r >> 32), to = (int) r;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, to, z);
        int y = to;
        for (; y > level && y >= from && w.hasBudget(); y--) {
            pos.setY(y);
            if (w.level().getFluidState(pos).is(FluidTags.WATER)) w.setQuiet(pos, Blocks.AIR.defaultBlockState());
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
        placed.remove(key(x, z));
    }

    /** Seconds between the two sweeps, so water that was set flowing has time to spread before the last one. */
    private static final int SETTLE_TICKS = 100;

    /**
     * Call each tick once everything is drained. If anything woke the quiet
     * water (and under heavy load something occasionally does), it flowed into
     * drained cells and may have made new sources there, which would stay for
     * good. Two sweeps, five seconds apart, clear any water left in the cells
     * this tracker ever filled and in the cells next to them within the same
     * height (only this disaster's water can be there; real lakes lie below).
     */
    public void settle(BlockWriter w) {
        bind(w);
        if (placed.isEmpty()) ACTIVE.remove(this);
        if (placed.isEmpty() || !ranges.isEmpty()) return;
        long now = w.level().getGameTime();
        if (settlePass >= 2) {
            // Swept twice: give the flowing water that was nudged time to recede, then done.
            if (now >= settleStart + SETTLE_TICKS + RECEDE_TICKS) placed.clear();
            return;
        }
        if (settleStart < 0) settleStart = now;
        if (settleQueue == null) {
            if (settlePass == 1 && now < settleStart + SETTLE_TICKS) return;
            settleQueue = new it.unimi.dsi.fastutil.longs.LongArrayList(placed.keySet());
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (!settleQueue.isEmpty() && w.hasBudget()) {
            long k = settleQueue.removeLong(settleQueue.size() - 1);
            BlockPos c = BlockPos.of(k);
            long r = placed.get(k);
            int from = (int) (r >> 32), to = (int) r;
            // The column itself, and stray sources up to two blocks out at the same height.
            for (int dx = -SETTLE_REACH; dx <= SETTLE_REACH; dx++) {
                for (int dz = -SETTLE_REACH; dz <= SETTLE_REACH; dz++) {
                    int x = c.getX() + dx, z = c.getZ() + dz;
                    // Filled neighbours are checked too: at the flood's sloping edge their own range is lower.
                    boolean own = dx == 0 && dz == 0 || placed.containsKey(key(x, z));
                    if (!Terrain.loaded(w.level(), x, z)) continue;
                    for (int y = to; y >= from; y--) {
                        pos.set(x, y, z);
                        var fluid = w.level().getFluidState(pos);
                        if (!fluid.is(FluidTags.WATER) || !(own || fluid.isSource() || Math.abs(dx) + Math.abs(dz) == 1)) continue;
                        if (w.setQuiet(pos, Blocks.AIR.defaultBlockState())) wakeFlowing(w, pos);
                    }
                }
            }
        }
        if (!settleQueue.isEmpty()) return;
        settleQueue = null;
        if (++settlePass == 1) settleStart = now;
    }

    /** After the last sweep: time for flowing water (8 blocks at 5 ticks each) to recede. */
    private static final int RECEDE_TICKS = 50;

    /** Cells around a filled column the sweep looks at for stray sources. */
    private static final int SETTLE_REACH = 2;

    /**
     * Flowing water next to a removed cell gets a tick, so water left without
     * its sources recedes on its own. Sources are never woken: they would
     * pour into the drained cells.
     */
    private static void wakeFlowing(BlockWriter w, BlockPos pos) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!w.level().isLoaded(n)) continue;
            var fluid = w.level().getFluidState(n);
            if (!fluid.isEmpty() && !fluid.isSource()) w.level().scheduleTick(n, fluid.getType(), fluid.getType().getTickDelay(w.level()));
        }
    }

    /** True once everything is drained and swept (or nothing was ever placed). */
    public boolean settled() {
        return ranges.isEmpty() && placed.isEmpty();
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
        long[] pk = placed.keySet().toLongArray();
        java.util.Arrays.sort(pk);
        long[] pv = new long[pk.length];
        for (int i = 0; i < pk.length; i++) pv[i] = placed.get(pk[i]);
        t.putLongArray("placedKeys", pk);
        t.putLongArray("placed", pv);
        t.putLong("settleStart", settleStart);
        t.putInt("settlePass", settlePass);
        return t;
    }

    public void load(CompoundTag t) {
        ranges.clear();
        long[] keys = t.getLongArray("keys").orElse(new long[0]);
        long[] values = t.getLongArray("ranges").orElse(new long[0]);
        for (int i = 0; i < Math.min(keys.length, values.length); i++) ranges.put(keys[i], values[i]);
        placed.clear();
        long[] pk = t.getLongArray("placedKeys").orElse(new long[0]);
        long[] pv = t.getLongArray("placed").orElse(new long[0]);
        for (int i = 0; i < Math.min(pk.length, pv.length); i++) {
            placed.put(pk[i], pv[i]);
            BlockPos c = BlockPos.of(pk[i]);
            grow(c.getX(), c.getZ(), (int) (pv[i] >> 32), (int) pv[i]);
        }
        settleStart = t.getLongOr("settleStart", -1L);
        settlePass = t.getIntOr("settlePass", 0);
        settleQueue = null; // a pass in progress starts over after a restart
    }
}
