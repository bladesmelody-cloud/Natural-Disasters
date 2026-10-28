package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wildfire: fires break out in a few places and a burning front races through
 * everything that burns, fastest downwind, leaving charred trunks, scorched
 * ground and ash. It runs its own spread (so it works with fire tick off and
 * stays inside the area) on top of whatever vanilla fire does.
 */
public class Wildfire extends Disaster {
    /** Everything that sets a wildfire's size; saved with it. */
    public record Params(int radius, int durationTicks, int ignitions, double spread, int perTick, int maxFront) {
        private static final int[] RADIUS = {32, 40, 48, 64, 80, 96, 128, 160, 192, 256};

        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            return new Params(RADIUS[m - 1], (60 + 20 * m) * 20, 1 + m / 2, 0.35 + 0.03 * m, 40 + 20 * m, 1500 + 500 * m);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("duration", durationTicks);
            t.putInt("ignitions", ignitions);
            t.putDouble("spread", spread);
            t.putInt("perTick", perTick);
            t.putInt("maxFront", maxFront);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("radius", d.radius), t.getIntOr("duration", d.durationTicks), t.getIntOr("ignitions", d.ignitions),
                    t.getDoubleOr("spread", d.spread), t.getIntOr("perTick", d.perTick), t.getIntOr("maxFront", d.maxFront));
        }
    }

    /** Ticks a spot burns before it spreads and burns out. */
    private static final int BURN_TICKS = 20;

    private Params params;
    private double windX, windZ;
    private boolean ignited;
    /** Burning spots, oldest first, with the tick each caught fire. */
    private final LongArrayList front = new LongArrayList();
    private final IntArrayList frontTick = new IntArrayList();
    private int head;

    public Wildfire(DisasterType<Wildfire> type) {
        super(type);
    }

    /** Builds a wildfire with explicit parameters (game tests, tuning). */
    public static Wildfire create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Wildfire f = DisasterTypes.WILDFIRE.create(center, magnitude, seed, warningTicks);
        f.params = params;
        return f;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        params = Params.forMagnitude(magnitude);
        double a = RandomSource.create(seed).nextDouble() * Math.PI * 2;
        windX = Math.cos(a);
        windZ = Math.sin(a);
    }

    public Params params() {
        return params;
    }

    public int burning() {
        return front.size() - head;
    }

    @Override
    public int radius() {
        return params.radius;
    }

    @Override
    public boolean isDone() {
        if (inWarning()) return false;
        int active = age - warningTicks;
        return active >= params.durationTicks && burning() == 0 || active >= params.durationTicks * 2;
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 20 != 0) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer p : playersNear(level, params.radius * 1.5)) {
            level.sendParticles(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true,
                    p.getX() + windX * -30 + (r.nextDouble() - 0.5) * 20, p.getY() + 10, p.getZ() + windZ * -30, 3, 4, 2, 4, 0.01);
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        if (!ignited) ignite(w, random, activeTicks);
        boolean spreading = activeTicks < params.durationTicks;
        int done = 0;
        while (head < front.size() && done < params.perTick && w.hasBudget()) {
            if (spreading && activeTicks - frontTick.getInt(head) < BURN_TICKS) break;
            BlockPos pos = BlockPos.of(front.getLong(head));
            head++;
            done++;
            if (spreading) spreadFrom(w, random, pos, activeTicks);
            burnOut(w, random, pos);
        }
        if (head > 4096 && head > front.size() / 2) compact();
        if (activeTicks % 10 == 0) smoke(level, random);
    }

    private void compact() {
        front.removeElements(0, head);
        frontTick.removeElements(0, head);
        head = 0;
    }

    private void ignite(BlockWriter w, RandomSource random, int activeTicks) {
        ServerLevel level = w.level();
        for (int i = 0; i < params.ignitions; i++) {
            BlockPos c = i == 0 ? center : Terrain.randomColumn(random, center.getX() + 0.5, center.getZ() + 0.5, params.radius / 3.0);
            if (!Terrain.loaded(level, c.getX(), c.getZ())) continue;
            BlockPos at = new BlockPos(c.getX(), Terrain.surface(level, c.getX(), c.getZ()) + 1, c.getZ());
            light(w, at, activeTicks);
        }
        ignited = true;
    }

    /** Puts fire at a spot (if fire can sit there) and adds it to the front. */
    private boolean light(BlockWriter w, BlockPos pos, int tick) {
        ServerLevel level = w.level();
        if (burning() >= params.maxFront) return false;
        if (Terrain.dist(pos.getX(), pos.getZ(), center.getX(), center.getZ()) > params.radius) return false;
        BlockState here = level.getBlockState(pos);
        if (!(here.isAir() || (here.canBeReplaced() && here.getFluidState().isEmpty()))) return false;
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (!fire.canSurvive(level, pos) || !w.set(pos, fire)) return false;
        front.add(pos.asLong());
        frontTick.add(tick);
        return true;
    }

    /** From a burning spot, fire jumps to fuel nearby, much more readily downwind. */
    private void spreadFrom(BlockWriter w, RandomSource random, BlockPos pos, int tick) {
        ServerLevel level = w.level();
        boolean raining = level.isRainingAt(pos.above());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    double len = Math.sqrt(dx * dx + dz * dz);
                    double windward = len == 0 ? 0 : (dx * windX + dz * windZ) / len;
                    double reach = Math.max(Math.abs(dx), Math.abs(dz));
                    // Two blocks away only downwind (embers carried by the wind).
                    if (reach == 2 && windward < 0.5) continue;
                    double chance = params.spread * (1 + 0.7 * windward) / (reach * (1 + Math.abs(dy) * 0.3));
                    if (raining) chance *= 0.5;
                    if (random.nextDouble() >= chance) continue;
                    BlockPos fuel = pos.offset(dx, dy, dz);
                    if (!Terrain.loaded(level, fuel.getX(), fuel.getZ())) continue;
                    BlockState s = level.getBlockState(fuel);
                    if (!burns(level, fuel, s)) continue;
                    if (s.canBeReplaced() && s.getFluidState().isEmpty()) {
                        light(w, fuel, tick);
                    } else {
                        // Any open face of the fuel: fire climbs trunks and into canopies.
                        Direction face = Direction.getRandom(random);
                        if (!light(w, fuel.relative(face), tick)) light(w, fuel.above(), tick);
                    }
                }
            }
        }
    }

    private static boolean burns(ServerLevel level, BlockPos pos, BlockState s) {
        return !s.isAir() && (s.isFlammable(level, pos, Direction.UP) || s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.CROPS));
    }

    /** The spot burns out: its fire goes and the fuel around it is charred or gone. */
    private void burnOut(BlockWriter w, RandomSource random, BlockPos pos) {
        ServerLevel level = w.level();
        if (level.getBlockState(pos).is(BlockTags.FIRE)) w.set(pos, Blocks.AIR.defaultBlockState());
        for (Direction d : Direction.values()) {
            BlockPos p = pos.relative(d);
            BlockState s = level.getBlockState(p);
            BlockState burnt = charred(s, random);
            if (burnt != null) w.set(p, burnt);
        }
        if (random.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** What fire leaves of a block, or null to leave it. */
    static BlockState charred(BlockState s, RandomSource random) {
        if (s.is(BlockTags.LOGS)) {
            return random.nextFloat() < 0.5F ? Blocks.AIR.defaultBlockState() : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        }
        if (s.is(Blocks.STRIPPED_DARK_OAK_LOG)) return null;
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL)) return Blocks.COARSE_DIRT.defaultBlockState();
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
                || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOOL) || s.is(Blocks.HAY_BLOCK) || s.is(BlockTags.CROPS)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
                || s.is(Blocks.FERN) || s.is(Blocks.BUSH) || s.is(BlockTags.WOOL_CARPETS)) {
            return Blocks.AIR.defaultBlockState();
        }
        return null;
    }

    private void smoke(ServerLevel level, RandomSource random) {
        if (burning() == 0) return;
        for (int i = 0; i < 6; i++) {
            BlockPos p = BlockPos.of(front.getLong(head + random.nextInt(burning())));
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, false, true, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5,
                    1, 0.3, 0.5, 0.3, 0.02);
        }
        if (random.nextInt(4) == 0) {
            BlockPos p = BlockPos.of(front.getLong(head + random.nextInt(burning())));
            level.playSound(null, p, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.8F);
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putDouble("windX", windX);
        tag.putDouble("windZ", windZ);
        tag.putBoolean("ignited", ignited);
        tag.putLongArray("front", front.subList(head, front.size()).toLongArray());
        tag.putIntArray("frontTick", frontTick.subList(head, frontTick.size()).toIntArray());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        windX = tag.getDoubleOr("windX", 1);
        windZ = tag.getDoubleOr("windZ", 0);
        ignited = tag.getBooleanOr("ignited", false);
        front.clear();
        frontTick.clear();
        head = 0;
        long[] f = tag.getLongArray("front").orElse(new long[0]);
        int[] t = tag.getIntArray("frontTick").orElse(new int[0]);
        for (int i = 0; i < Math.min(f.length, t.length); i++) {
            front.add(f[i]);
            frontTick.add(t[i]);
        }
    }
}
