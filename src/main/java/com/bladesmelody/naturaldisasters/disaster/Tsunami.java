package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.registry.ModSounds;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tsunami: a wall of water rolls in from the sea and runs inland, smashing
 * what it is strong enough to break (the {@link Wind} table, with water force
 * instead of wind), sweeping creatures along, then draining back and leaving
 * sand and puddles behind. It loses height as it climbs over land.
 *
 * <p>The wave is processed as rows across its front, one row per step, so it
 * advances only as fast as the block budget allows and resumes after restarts.
 */
public class Tsunami extends Disaster {
    /** Rows of water behind the front before it drains again. */
    private static final int WAVE_THICKNESS = 10;

    /** Everything that sets a tsunami's size; saved with it. */
    public record Params(int halfWidth, int height, int inland, double speed) {
        private static final int[] HEIGHT = {3, 4, 5, 6, 8, 10, 12, 14, 17, 22};

        public static Params forMagnitude(int magnitude) {
            int i = Math.clamp(magnitude, 1, 10) - 1;
            int h = HEIGHT[i];
            return new Params(24 + 10 * i, h, 24 + h * 8, 0.5 + 0.05 * i);
        }

        /** Force in mph-equivalent against the {@link Wind} table. */
        double force() {
            return 70 + 8 * height;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("halfWidth", halfWidth);
            t.putInt("height", height);
            t.putInt("inland", inland);
            t.putDouble("speed", speed);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("halfWidth", d.halfWidth), t.getIntOr("height", d.height),
                    t.getIntOr("inland", d.inland), t.getDoubleOr("speed", d.speed));
        }
    }

    private Params params;
    /** Direction the wave travels (unit vector), from the sea toward land. */
    private double dirX = 1, dirZ = 0;
    /** Where row 0 starts (out at sea) and how many rows the wave travels. */
    private double originX, originZ;
    private int rows;
    private int seaLevel;
    private boolean aimed;
    private int nextRow;
    private int nextDrain;
    private final WaterTracker water = new WaterTracker();

    public Tsunami(DisasterType<Tsunami> type) {
        super(type);
    }

    /** Builds a tsunami with explicit parameters and direction (game tests, tuning). */
    public static Tsunami create(BlockPos center, int magnitude, Params params, long seed, double angle, int seaLevel) {
        Tsunami t = DisasterTypes.TSUNAMI.create(center, magnitude, seed, 0);
        t.params = params;
        t.aim(angle, seaLevel);
        return t;
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
        return params.inland + params.halfWidth;
    }

    @Override
    public boolean isDone() {
        if (inWarning() || !aimed) return false;
        if (age - warningTicks > (rows / params.speed + 400) * 3) return true;
        return nextRow >= rows && nextDrain >= rows && water.size() == 0;
    }

    /** Sets the travel direction; the wave starts a little out to sea and runs past the centre. */
    private void aim(double angle, int sea) {
        dirX = Math.cos(angle);
        dirZ = Math.sin(angle);
        seaLevel = sea;
        int start = 24;
        originX = center.getX() + 0.5 - dirX * start;
        originZ = center.getZ() + 0.5 - dirZ * start;
        rows = start + params.inland;
        aimed = true;
    }

    /** Points the wave from the sea: the direction around the centre with the most open water. */
    private void aimFromSea(ServerLevel level) {
        double best = RandomSource.create(seed).nextDouble() * Math.PI * 2;
        int bestWater = 0;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int wet = 0;
            for (int d = 16; d <= 128; d += 16) {
                int x = (int) Math.floor(center.getX() + Math.cos(a) * d), z = (int) Math.floor(center.getZ() + Math.sin(a) * d);
                if (!Terrain.loaded(level, x, z)) continue;
                BlockPos top = new BlockPos(x, Terrain.top(level, x, z), z);
                if (level.getFluidState(top).is(Fluids.WATER)) wet++;
            }
            if (wet > bestWater) {
                bestWater = wet;
                best = a;
            }
        }
        // The wave comes FROM the wet side, so it travels the opposite way.
        aim(best + Math.PI, level.getSeaLevel() - 1);
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        if (!aimed) aimFromSea(level);
        if (ticksLeft % 40 != 0) return;
        for (ServerPlayer p : playersNear(level, radius() * 1.5)) {
            level.playSound(null, p.getX() - dirX * 40, p.getY(), p.getZ() - dirZ * 40, ModSounds.TSUNAMI_ROAR.get(),
                    SoundSource.AMBIENT, 0.8F, 0.8F);
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        if (!aimed) aimFromSea(level);
        int target = Math.min(rows, (int) (activeTicks * params.speed) + 1);
        while (nextRow < target && w.hasBudget()) {
            surge(w, nextRow);
            nextRow++;
        }
        while (nextDrain < nextRow - WAVE_THICKNESS && w.hasBudget()) {
            if (!drainRow(w, nextDrain)) break;
            nextDrain++;
        }
        if (nextRow >= rows) {
            while (nextDrain < rows && w.hasBudget()) {
                if (!drainRow(w, nextDrain)) break;
                nextDrain++;
            }
        }
        if (activeTicks % 5 == 0) sweep(level);
        if (activeTicks % 60 == 0) roar(level);
    }

    /** Water surface height of the wave at a row: full out at sea, shrinking over the distance inland. */
    int surfaceAt(int row) {
        double inlandFraction = Math.max(0, row - 24) / (double) params.inland;
        return seaLevel + (int) Math.round(params.height * (1 - inlandFraction));
    }

    private int columnX(int row, int lateral) {
        return (int) Math.floor(originX + dirX * row - dirZ * lateral);
    }

    private int columnZ(int row, int lateral) {
        return (int) Math.floor(originZ + dirZ * row + dirX * lateral);
    }

    /** The wave front reaches a row: smash weak blocks under the wave and fill it with water. */
    private void surge(BlockWriter w, int row) {
        ServerLevel level = w.level();
        for (int lat = -params.halfWidth; lat <= params.halfWidth; lat++) {
            int edge = params.halfWidth - Math.abs(lat);
            int surface = surfaceAt(row) - (edge < 6 ? (6 - edge) * params.height / 6 : 0);
            int x = columnX(row, lat), z = columnZ(row, lat);
            if (!Terrain.loaded(level, x, z)) continue;
            int top = Terrain.surface(level, x, z);
            if (top >= surface) continue;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
            // Smash from the top down while the water is strong enough.
            for (int y = top; y > top - 4; y--) {
                pos.setY(y);
                BlockState s = level.getBlockState(pos);
                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                if (Wind.resistance(level, pos, s) > params.force()) break;
                // Quiet: waking fluids here would set the wave's own water flowing (and spreading for good).
                w.setQuiet(pos, Blocks.AIR.defaultBlockState());
            }
            water.fill(w, x, z, Terrain.top(level, x, z) + 1, surface);
        }
    }

    /** After the wave passes a row: drain it, leaving sand on some of the land and puddles in hollows. */
    private boolean drainRow(BlockWriter w, int row) {
        ServerLevel level = w.level();
        for (int lat = -params.halfWidth; lat <= params.halfWidth; lat++) {
            int x = columnX(row, lat), z = columnZ(row, lat);
            if (!water.has(x, z)) continue;
            int bottom = water.bottom(x, z);
            RandomSource r = RandomSource.create(seed ^ BlockPos.asLong(x, 0, z));
            boolean puddle = bottom > seaLevel && r.nextFloat() < 0.08F;
            if (puddle) {
                water.drainAbove(w, x, z, bottom);
                if (!w.hasBudget()) return false;
                water.forget(x, z);
                continue;
            } else if (!water.drain(w, x, z)) {
                return false;
            }
            if (bottom > seaLevel && !puddle && r.nextFloat() < 0.3F) {
                BlockPos ground = new BlockPos(x, bottom - 1, z);
                BlockState g = level.getBlockState(ground);
                if (g.is(Blocks.GRASS_BLOCK) || g.is(BlockTags.DIRT)) w.set(ground, Blocks.SAND.defaultBlockState());
            }
        }
        return true;
    }

    /** Creatures in the wave get carried along and up with it. */
    private void sweep(ServerLevel level) {
        if (nextRow <= 0 || nextRow > rows) return;
        int front = nextRow - 1;
        double fx = originX + dirX * front, fz = originZ + dirZ * front;
        double reach = params.halfWidth + 4;
        AABB box = new AABB(fx - reach, seaLevel - 8, fz - reach, fx + reach, seaLevel + params.height + 8, fz + reach);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e.isSpectator() || e.noPhysics) continue;
            double along = (e.getX() - originX) * dirX + (e.getZ() - originZ) * dirZ;
            if (along < front - WAVE_THICKNESS || along > front + 2 || !e.isInWater()) continue;
            double push = 0.25 + 0.03 * params.height;
            e.setDeltaMovement(e.getDeltaMovement().add(new Vec3(dirX * push, 0.05, dirZ * push)));
            e.hurtMarked = true;
            if (e instanceof LivingEntity living && along > front - 2 && level.getRandom().nextInt(4) == 0) {
                living.hurtServer(level, level.damageSources().drown(), 1.0F + params.height / 6.0F);
            }
        }
        RandomSource r = level.getRandom();
        for (int i = 0; i < 4; i++) {
            int lat = r.nextInt(params.halfWidth * 2 + 1) - params.halfWidth;
            level.sendParticles(ParticleTypes.SPLASH, false, true, columnX(front, lat) + 0.5, surfaceAt(front) + 1,
                    columnZ(front, lat) + 0.5, 20, 1.5, 0.5, 1.5, 0.3);
        }
    }

    private void roar(ServerLevel level) {
        if (nextRow >= rows) return;
        double fx = originX + dirX * nextRow, fz = originZ + dirZ * nextRow;
        level.playSound(null, fx, seaLevel + 2, fz, ModSounds.TSUNAMI_ROAR.get(), SoundSource.AMBIENT, 4.0F, 1.0F);
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putBoolean("aimed", aimed);
        tag.putDouble("dirX", dirX);
        tag.putDouble("dirZ", dirZ);
        tag.putDouble("originX", originX);
        tag.putDouble("originZ", originZ);
        tag.putInt("rows", rows);
        tag.putInt("sea", seaLevel);
        tag.putInt("nextRow", nextRow);
        tag.putInt("nextDrain", nextDrain);
        tag.put("water", water.save());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        aimed = tag.getBooleanOr("aimed", false);
        dirX = tag.getDoubleOr("dirX", 1);
        dirZ = tag.getDoubleOr("dirZ", 0);
        originX = tag.getDoubleOr("originX", center.getX());
        originZ = tag.getDoubleOr("originZ", center.getZ());
        rows = tag.getIntOr("rows", 0);
        seaLevel = tag.getIntOr("sea", 62);
        nextRow = tag.getIntOr("nextRow", 0);
        nextDrain = tag.getIntOr("nextDrain", 0);
        water.load(tag.getCompoundOrEmpty("water"));
    }
}
