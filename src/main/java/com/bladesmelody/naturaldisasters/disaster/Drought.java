package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.sky.SkyZones;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Drought: weeks without rain squeezed into minutes. The sky stays clear,
 * ponds and shallow lakes shrink from their shores, fields dry out and crops
 * die, grass browns to dirt, trees drop leaves, snow and ice vanish and
 * players get hungry fast in the heat. Strong droughts start grass fires.
 */
public class Drought extends Disaster {
    /** Everything that sets a drought's size; saved with it. potency = 1–10. */
    public record Params(int radius, int durationTicks, int potency, double density) {
        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            // Owner: "Longer" — lasts 1 (M1) to 5 (M10) Minecraft days and dries the land slowly:
            // about twice the total effect the short version had, spread over the days.
            int duration = (int) Math.round((1 + 4 * (m - 1) / 9.0) * 24000);
            double density = 2 * 0.0006 * (120 + 40 * m) * 20 / duration;
            return new Params(96 + 32 * m, duration, m, density);
        }

        /** Water at most this deep (counting down from the surface) evaporates. */
        int maxDepth() {
            return 1 + potency / 3;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("duration", durationTicks);
            t.putInt("potency", potency);
            t.putDouble("density", density);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("radius", d.radius), t.getIntOr("duration", d.durationTicks),
                    t.getIntOr("potency", d.potency), t.getDoubleOr("density", d.density));
        }
    }

    private Params params;

    public Drought(DisasterType<Drought> type) {
        super(type);
    }

    /** Builds a drought with explicit parameters (game tests, tuning). */
    public static Drought create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Drought d = DisasterTypes.DROUGHT.create(center, magnitude, seed, warningTicks);
        d.params = params;
        return d;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        params = Params.forMagnitude(magnitude);
    }

    public Params params() {
        return params;
    }

    @Override
    public int radius() {
        return params.radius;
    }

    @Override
    public boolean isDone() {
        return !inWarning() && age - warningTicks >= params.durationTicks;
    }

    /** No rain for the whole drought (weather is server-wide). */
    private void clearSkies(ServerLevel level, int ticks) {
        if (level.dimensionType().hasCeiling()) return;
        if (level.isRaining() || age % 200 == 0) level.getServer().setWeatherParameters(Math.max(ticks, 200), 0, false, false);
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        clearSkies(level, ticksLeft + params.durationTicks);
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        SkyZones.put(id, new SkyZones.Zone(SkyZones.Kind.DROUGHT, center.getX() + 0.5, center.getZ() + 0.5, params.radius, magnitude));
        clearSkies(level, params.durationTicks - activeTicks);
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        double expected = Math.PI * params.radius * params.radius * params.density * (0.5 + params.potency * 0.1);
        int samples = (int) Math.min(1500, expected) + (random.nextDouble() < expected - (int) expected ? 1 : 0);
        for (int i = 0; i < samples && w.hasBudget(); i++) {
            BlockPos c = Terrain.randomColumn(random, center.getX() + 0.5, center.getZ() + 0.5, params.radius);
            if (Terrain.loaded(level, c.getX(), c.getZ())) parch(w, random, c.getX(), c.getZ());
        }
        if (activeTicks % 20 == 0) heat(level);
    }

    /** The drought works on the top of a column. */
    private void parch(BlockWriter w, RandomSource random, int x, int z) {
        ServerLevel level = w.level();
        BlockPos top = new BlockPos(x, Terrain.surface(level, x, z), z);
        BlockState s = level.getBlockState(top);
        float p = params.potency;
        if (s.getFluidState().is(Fluids.WATER)) {
            evaporate(w, top);
        } else if (s.is(Blocks.SNOW) || s.is(BlockTags.ICE) && !s.is(Blocks.BLUE_ICE) && !s.is(Blocks.PACKED_ICE)) {
            w.set(top, Blocks.AIR.defaultBlockState());
        } else if (s.is(BlockTags.CROPS) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
                || s.is(Blocks.FERN) || s.is(BlockTags.SAPLINGS)) {
            if (random.nextFloat() < 0.15F + 0.03F * p) {
                BlockState dead = Blocks.DEAD_BUSH.defaultBlockState();
                w.set(top, random.nextFloat() < 0.3F && dead.canSurvive(level, top) ? dead : Blocks.AIR.defaultBlockState());
            }
        } else if (s.is(Blocks.FARMLAND)) {
            int moisture = s.getValue(FarmlandBlock.MOISTURE);
            w.set(top, moisture > 0 ? s.setValue(FarmlandBlock.MOISTURE, 0) : Blocks.DIRT.defaultBlockState());
        } else if (s.is(BlockTags.LEAVES)) {
            if (random.nextFloat() < 0.02F * p) w.set(top, Blocks.AIR.defaultBlockState());
        } else if (s.is(Blocks.GRASS_BLOCK)) {
            if (random.nextFloat() < 0.15F) w.set(top, Blocks.DIRT.defaultBlockState());
            else if (p >= 6 && random.nextFloat() < 0.002F * p) startFire(w, top.above());
        } else if (s.is(Blocks.DIRT) && p >= 5 && random.nextFloat() < 0.05F) {
            w.set(top, Blocks.COARSE_DIRT.defaultBlockState());
        } else if (s.is(Blocks.MUD) && random.nextFloat() < 0.2F) {
            w.set(top, Blocks.PACKED_MUD.defaultBlockState());
        }
    }

    /**
     * Ponds dry up: a small body of water (up to 80 × potency blocks across its
     * surface) loses its whole top layer at once, so the pond sinks step by
     * step; doing it cell by cell doesn't work, because once anything wakes a
     * pond the infinite-water rule refills each dried spot. Bigger lakes only
     * lose shallow shore water, and the sea is never touched.
     */
    private void evaporate(BlockWriter w, BlockPos top) {
        ServerLevel level = w.level();
        // Plain water only (not waterlogged blocks), never the sea.
        if (!level.getBlockState(top).is(Blocks.WATER) || level.getBiome(top).is(BiomeTags.IS_OCEAN)) return;
        int limit = 80 * params.potency;
        LongOpenHashSet surface = new LongOpenHashSet();
        LongArrayList frontier = new LongArrayList();
        surface.add(top.asLong());
        frontier.add(top.asLong());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (!frontier.isEmpty() && surface.size() <= limit) {
            BlockPos at = BlockPos.of(frontier.removeLong(frontier.size() - 1));
            for (Direction d : Direction.Plane.HORIZONTAL) {
                p.setWithOffset(at, d);
                if (surface.contains(p.asLong()) || !Terrain.loaded(level, p.getX(), p.getZ())) continue;
                if (!level.getBlockState(p).is(Blocks.WATER) || !level.getBlockState(p.above()).getFluidState().isEmpty()) continue;
                surface.add(p.asLong());
                frontier.add(p.asLong());
            }
        }
        if (surface.size() <= limit) {
            // Quiet: waking what's left would let it flow back up.
            for (long packed : surface) w.setQuiet(BlockPos.of(packed), Blocks.AIR.defaultBlockState());
            return;
        }
        shoreEvaporate(w, top);
    }

    /** A big lake: only shallow water at its shore dries. */
    private void shoreEvaporate(BlockWriter w, BlockPos top) {
        ServerLevel level = w.level();
        int depth = 0;
        BlockPos.MutableBlockPos p = top.mutable();
        while (level.getFluidState(p).is(Fluids.WATER) && depth <= params.maxDepth()) {
            depth++;
            p.move(Direction.DOWN);
        }
        if (depth > params.maxDepth()) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(top.relative(d)).is(Fluids.WATER)) {
                w.setQuiet(top, Blocks.AIR.defaultBlockState());
                return;
            }
        }
    }

    private void startFire(BlockWriter w, BlockPos pos) {
        if (!Terrain.empty(w.level().getBlockState(pos))) return;
        BlockState fire = BaseFireBlock.getState(w.level(), pos);
        if (fire.canSurvive(w.level(), pos)) w.set(pos, fire);
    }

    /** Players out in the sun get hungry fast; heat shimmer around them. */
    private void heat(ServerLevel level) {
        for (ServerPlayer p : playersNear(level, params.radius)) {
            if (!level.canSeeSky(p.blockPosition().above()) || !level.isBrightOutside()) continue;
            // Mild per second, since it lasts for days (M5: about one hunger point per 20 s in the sun).
            p.causeFoodExhaustion(0.05F + 0.03F * params.potency);
            level.sendParticles(p, ParticleTypes.WHITE_ASH, true, true, p.getX(), p.getY() + 1, p.getZ(), 10, 6, 1, 6, 0.0);
        }
    }

    @Override
    protected void onEnd(ServerLevel level) {
        SkyZones.remove(id);
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
    }
}
