package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hurricane: a huge, slow storm whose calm eye drifts across the area. Wind
 * (strongest in the eyewall) tears weak blocks off everything it reaches,
 * pushes creatures that are out in the open around the storm, lightning
 * strikes all over, and near the sea a storm surge floods the coast.
 *
 * <p>Strength follows the Saffir-Simpson scale: M1–2 tropical storm, up to
 * category 5 at M9–10.
 */
public class Hurricane extends Disaster {
    /** Everything that sets a hurricane's size; saved with it. */
    public record Params(int category, int radius, int eyeRadius, double maxWind, int durationTicks, double speed,
                         int surge, double density) {
        private static final int[] CATEGORY = {0, 0, 1, 1, 2, 3, 4, 4, 5, 5};
        private static final double[] WIND = {60, 85, 100, 120, 145, 170};
        private static final int[] SURGE = {1, 2, 2, 3, 4, 5};

        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            int cat = CATEGORY[m - 1];
            return new Params(cat, 128 + 32 * m, 12 + 2 * m, WIND[cat], (90 + 25 * m) * 20, 0.06 + 0.004 * m,
                    SURGE[cat], 0.004);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("category", category);
            t.putInt("radius", radius);
            t.putInt("eye", eyeRadius);
            t.putDouble("wind", maxWind);
            t.putInt("duration", durationTicks);
            t.putDouble("speed", speed);
            t.putInt("surge", surge);
            t.putDouble("density", density);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("category", d.category), t.getIntOr("radius", d.radius), t.getIntOr("eye", d.eyeRadius),
                    t.getDoubleOr("wind", d.maxWind), t.getIntOr("duration", d.durationTicks), t.getDoubleOr("speed", d.speed),
                    t.getIntOr("surge", d.surge), t.getDoubleOr("density", d.density));
        }
    }

    private static final int MAX_SAMPLES = 1200;

    private Params params;
    private double eyeX, eyeZ, heading;
    private int seaLevel = Integer.MIN_VALUE;
    private final WaterTracker surge = new WaterTracker();

    public Hurricane(DisasterType<Hurricane> type) {
        super(type);
    }

    /** Builds a hurricane with explicit parameters (game tests, tuning). */
    public static Hurricane create(BlockPos center, int magnitude, Params params, long seed, double heading, int seaLevel) {
        Hurricane h = DisasterTypes.HURRICANE.create(center, magnitude, seed, 0);
        h.params = params;
        h.heading = heading;
        h.seaLevel = seaLevel;
        h.placeEye();
        return h;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        params = Params.forMagnitude(magnitude);
        heading = RandomSource.create(seed).nextDouble() * Math.PI * 2;
        placeEye();
    }

    /** The eye starts upwind so it passes over the centre halfway through. */
    private void placeEye() {
        double half = params.speed * params.durationTicks / 2;
        eyeX = center.getX() + 0.5 - Math.cos(heading) * half;
        eyeZ = center.getZ() + 0.5 - Math.sin(heading) * half;
    }

    public Params params() {
        return params;
    }

    @Override
    public int radius() {
        return params.radius;
    }

    @Override
    public String describe() {
        return (params.category == 0 ? "tropical storm" : "hurricane cat " + params.category) + " (M" + magnitude + ", radius " + params.radius + ")";
    }

    @Override
    public boolean isDone() {
        if (inWarning()) return false;
        int active = age - warningTicks;
        if (active >= params.durationTicks * 3) return true;
        return active >= params.durationTicks && surge.size() == 0;
    }

    /** Wind speed at a distance from the eye: calm inside, peak in the eyewall, easing off outward. */
    double windAt(double dist) {
        double eye = params.eyeRadius;
        if (dist < eye * 0.8) return params.maxWind * 0.15;
        if (dist < eye * 2.5) return params.maxWind;
        return params.maxWind * Math.pow(eye * 2.5 / dist, 0.6);
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        keepWeather(level, ticksLeft + params.durationTicks + 600, false);
        if (ticksLeft % 40 != 0) return;
        for (ServerPlayer p : playersNear(level, params.radius * 1.3)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.3F, 0.7F);
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        if (seaLevel == Integer.MIN_VALUE) seaLevel = level.getSeaLevel() - 1;
        boolean blowing = activeTicks < params.durationTicks;
        if (!blowing) {
            drainSurge(w);
            return;
        }
        keepWeather(level, params.durationTicks - activeTicks + 600, params.category >= 1);
        eyeX += Math.cos(heading) * params.speed;
        eyeZ += Math.sin(heading) * params.speed;
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));

        int samples = (int) Math.min(MAX_SAMPLES, Math.PI * params.radius * params.radius * params.density);
        for (int i = 0; i < samples && w.hasBudget(); i++) {
            BlockPos column = Terrain.randomColumn(random, eyeX, eyeZ, params.radius);
            if (!Terrain.loaded(level, column.getX(), column.getZ())) continue;
            double wind = windAt(Terrain.dist(column.getX() + 0.5, column.getZ() + 0.5, eyeX, eyeZ));
            strip(w, random, column, wind);
            if (params.surge > 0 && i % 4 == 0) surgeAt(w, column);
        }
        if (activeTicks % 2 == 0) blowEntities(level);
        if (random.nextInt(Math.max(4, 40 - 3 * magnitude)) == 0) lightning(level, random);
        if (activeTicks % 30 == 0) howl(level);
    }

    /** Tears up to two weak blocks off the top of a column. */
    private void strip(BlockWriter w, RandomSource random, BlockPos column, double wind) {
        ServerLevel level = w.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), Terrain.surface(level, column.getX(), column.getZ()), column.getZ());
        for (int i = 0; i < 2 && w.hasBudget(); i++) {
            BlockState state = level.getBlockState(pos);
            if (Wind.resistance(level, pos, state) > wind * (0.7 + random.nextDouble() * 0.3)) return;
            // Quiet, so the storm surge's own water next door doesn't start flowing.
            w.setQuiet(pos, Blocks.AIR.defaultBlockState());
            pos.move(0, -1, 0);
        }
    }

    /** Low land right next to the sea goes under the storm surge. */
    private void surgeAt(BlockWriter w, BlockPos column) {
        ServerLevel level = w.level();
        int x = column.getX(), z = column.getZ();
        int top = Terrain.top(level, x, z);
        int surface = seaLevel + params.surge;
        if (top >= surface || top < seaLevel - 1 || surge.has(x, z)) return;
        // Open water itself isn't raised: column-by-column it would look like a bumpy, frozen sea.
        if (level.getFluidState(new BlockPos(x, top, z)).is(Fluids.WATER)) return;
        // Land: only if open water is close by.
        for (int d = 1; d <= 6; d++) {
            for (int[] dir : new int[][] {{d, 0}, {-d, 0}, {0, d}, {0, -d}}) {
                int nx = x + dir[0], nz = z + dir[1];
                if (!Terrain.loaded(level, nx, nz)) continue;
                int nt = Terrain.top(level, nx, nz);
                if (nt <= seaLevel && level.getFluidState(new BlockPos(nx, nt, nz)).is(Fluids.WATER)) {
                    surge.fill(w, x, z, top + 1, surface);
                    return;
                }
            }
        }
    }

    private void drainSurge(BlockWriter w) {
        while (surge.size() > 0 && w.hasBudget()) {
            long[] c = surge.anyColumn();
            if (!surge.drain(w, (int) c[0], (int) c[1])) return;
        }
    }

    /** Creatures out in the open get pushed around the storm (counter-clockwise, a little inward). */
    private void blowEntities(ServerLevel level) {
        double r = params.radius;
        AABB box = new AABB(eyeX - r, level.getMinY(), eyeZ - r, eyeX + r, level.getMaxY(), eyeZ + r);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e.isSpectator() || e.noPhysics) continue;
            if (e instanceof Player p && p.getAbilities().flying) continue;
            if (!level.canSeeSky(e.blockPosition().above())) continue;
            double dx = e.getX() - eyeX, dz = e.getZ() - eyeZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > r || dist < 0.5) continue;
            double wind = windAt(dist);
            double force = wind / 170.0 * 0.12;
            Vec3 spin = new Vec3(-dz / dist, 0, dx / dist).scale(force).add(new Vec3(-dx / dist, 0, -dz / dist).scale(force * 0.3));
            // Strong gusts knock creatures off their feet now and then.
            double lift = wind > 100 && e.onGround() && level.getRandom().nextInt(10) == 0 ? 0.25 + wind / 170.0 * 0.2 : 0;
            e.setDeltaMovement(e.getDeltaMovement().add(spin.x, lift, spin.z));
            e.hurtMarked = true;
        }
    }

    private void lightning(ServerLevel level, RandomSource random) {
        BlockPos column = Terrain.randomColumn(random, eyeX, eyeZ, params.radius);
        if (!Terrain.loaded(level, column.getX(), column.getZ())) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt == null) return;
        bolt.snapTo(Vec3.atBottomCenterOf(column.atY(Terrain.top(level, column.getX(), column.getZ()) + 1)));
        level.addFreshEntity(bolt);
    }

    private void howl(ServerLevel level) {
        for (ServerPlayer p : level.getPlayers(p -> Terrain.dist(p.getX(), p.getZ(), eyeX, eyeZ) < params.radius)) {
            double wind = windAt(Terrain.dist(p.getX(), p.getZ(), eyeX, eyeZ));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER,
                    (float) Math.min(1.5, wind / 120), 0.6F);
        }
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putDouble("eyeX", eyeX);
        tag.putDouble("eyeZ", eyeZ);
        tag.putDouble("heading", heading);
        tag.putInt("sea", seaLevel);
        tag.put("surge", surge.save());
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        eyeX = tag.getDoubleOr("eyeX", center.getX());
        eyeZ = tag.getDoubleOr("eyeZ", center.getZ());
        heading = tag.getDoubleOr("heading", 0);
        seaLevel = tag.getIntOr("sea", Integer.MIN_VALUE);
        surge.load(tag.getCompoundOrEmpty("surge"));
    }
}
