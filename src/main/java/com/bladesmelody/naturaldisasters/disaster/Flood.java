package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.registry.ModSounds;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flood: under heavy rain the water rises over the lowest ground in the area,
 * drowning fields and the lower floors of buildings, holds for a while, then
 * drains away again and leaves mud behind.
 *
 * <p>The flood level starts at the lowest ground found in the area and rises
 * by {@code rise} blocks, sloping back down to nothing near the edge so the
 * flood has no walls. Only water the flood placed is drained ({@link WaterTracker}).
 */
public class Flood extends Disaster {
    /** Columns checked per tick while filling or draining. */
    private static final int COLUMNS_PER_TICK = 3000;
    private static final int NONE = Integer.MIN_VALUE;

    /** Everything that sets a flood's size; saved with it. */
    public record Params(int radius, int rise, int riseTicks, int holdTicks, int recedeTicks) {
        private static final int[] RADIUS = {24, 32, 40, 56, 72, 96, 120, 144, 176, 224};
        private static final int[] RISE = {2, 3, 4, 5, 6, 8, 10, 12, 14, 18};

        public static Params forMagnitude(int magnitude) {
            int i = Math.clamp(magnitude, 1, 10) - 1;
            int rise = RISE[i];
            return new Params(RADIUS[i], rise, (20 + rise * 8) * 20, (30 + rise * 2) * 20, (30 + rise * 5) * 20);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("rise", rise);
            t.putInt("riseTicks", riseTicks);
            t.putInt("holdTicks", holdTicks);
            t.putInt("recedeTicks", recedeTicks);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("radius", d.radius), t.getIntOr("rise", d.rise), t.getIntOr("riseTicks", d.riseTicks),
                    t.getIntOr("holdTicks", d.holdTicks), t.getIntOr("recedeTicks", d.recedeTicks));
        }

        int total() {
            return riseTicks + holdTicks + recedeTicks;
        }
    }

    private Params params;
    private int base = NONE;
    private int cursor;
    private final WaterTracker water = new WaterTracker();

    public Flood(DisasterType<Flood> type) {
        super(type);
    }

    /** Builds a flood with explicit parameters (game tests, tuning). */
    public static Flood create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Flood f = DisasterTypes.FLOOD.create(center, magnitude, seed, warningTicks);
        f.params = params;
        return f;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        params = Params.forMagnitude(magnitude);
    }

    public Params params() {
        return params;
    }

    public int base() {
        return base;
    }

    @Override
    public int radius() {
        return params.radius;
    }

    @Override
    public boolean isDone() {
        if (inWarning()) return false;
        int active = age - warningTicks;
        if (active >= params.total() * 3) return true;
        return active >= params.total() && water.size() == 0;
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        keepWeather(level, ticksLeft + params.total(), false);
        if (ticksLeft % 60 != 0) return;
        for (ServerPlayer p : playersNear(level, params.radius * 1.5)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FLOOD_RUSH.get(), SoundSource.WEATHER, 1.0F, 1.0F);
        }
    }

    /** Flood level (absolute y) at this point of the flood, before the edge slope. */
    int levelAt(int activeTicks) {
        double f;
        if (activeTicks < params.riseTicks) f = (activeTicks + 1) / (double) params.riseTicks;
        else if (activeTicks < params.riseTicks + params.holdTicks) f = 1;
        else f = 1 - (activeTicks - params.riseTicks - params.holdTicks + 1) / (double) params.recedeTicks;
        return base + (int) Math.round(params.rise * Math.clamp(f, 0, 1));
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        if (activeTicks < params.riseTicks + params.holdTicks) keepWeather(level, params.total() - activeTicks, false);
        if (base == NONE) base = findBase(level);
        int floodLevel = levelAt(activeTicks);
        boolean receding = activeTicks >= params.riseTicks + params.holdTicks;
        int[][] disc = Terrain.disc(params.radius);
        for (int n = 0; n < Math.min(COLUMNS_PER_TICK, disc.length) && w.hasBudget(); n++) {
            int[] off = disc[cursor];
            cursor = (cursor + 1) % disc.length;
            int x = center.getX() + off[0], z = center.getZ() + off[1];
            if (!Terrain.loaded(level, x, z)) continue;
            int local = floodLevel - edgeDrop(Math.sqrt(off[0] * off[0] + off[1] * off[1]));
            if (receding || activeTicks >= params.total()) {
                if (!water.has(x, z)) continue;
                int bottom = water.bottom(x, z);
                water.drainAbove(w, x, z, activeTicks >= params.total() ? Integer.MIN_VALUE : local);
                if (!water.has(x, z)) leaveMud(w, x, bottom - 1, z);
            } else {
                int top = Terrain.top(level, x, z);
                if (top < local) water.fill(w, x, z, top + 1, local);
            }
        }
    }

    /** The flood slopes down over the outer quarter so it ends without a wall. */
    private int edgeDrop(double dist) {
        double start = params.radius * 0.75;
        if (dist <= start) return 0;
        return (int) Math.ceil(params.rise * (dist - start) / (params.radius - start));
    }

    /** Lowest ground among sampled columns near the middle: floods start where water collects. */
    private int findBase(ServerLevel level) {
        RandomSource r = RandomSource.create(seed);
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            BlockPos c = Terrain.randomColumn(r, center.getX() + 0.5, center.getZ() + 0.5, params.radius * 0.5);
            if (!Terrain.loaded(level, c.getX(), c.getZ())) continue;
            lowest = Math.min(lowest, Terrain.top(level, c.getX(), c.getZ()));
        }
        return lowest == Integer.MAX_VALUE ? center.getY() : lowest;
    }

    /** Some soaked ground turns to mud once the water is gone. */
    private void leaveMud(BlockWriter w, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState ground = w.level().getBlockState(pos);
        if ((ground.is(Blocks.GRASS_BLOCK) || ground.is(BlockTags.DIRT) && !ground.is(Blocks.MUD))
                && RandomSource.create(seed ^ pos.asLong()).nextFloat() < 0.25F) {
            w.set(pos, Blocks.MUD.defaultBlockState());
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putInt("base", base);
        tag.putInt("cursor", cursor);
        tag.put("water", water.save());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        base = tag.getIntOr("base", NONE);
        cursor = tag.getIntOr("cursor", 0);
        water.load(tag.getCompoundOrEmpty("water"));
    }
}
