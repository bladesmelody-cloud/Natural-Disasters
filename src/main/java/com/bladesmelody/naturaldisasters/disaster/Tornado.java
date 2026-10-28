package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import com.bladesmelody.naturaldisasters.entity.TornadoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tornado: a funnel that wanders across the area for a while, tearing loose
 * every block the wind is strong enough for (see {@link Wind}) and sucking
 * creatures and items up into a spin before flinging them out.
 *
 * <p>Strength follows the EF scale: magnitude 1–2 is EF0, up to EF5 at M10.
 * Torn blocks mostly vanish; some become spinning falling-block debris
 * (capped, like other mods' debris entities) and some are thrown and land as
 * rubble further along.
 */
public class Tornado extends Disaster {
    /** Everything that sets a tornado's size; saved with it. */
    public record Params(int ef, int coreRadius, double maxWind, int durationTicks, double speed, int bite,
                         int height, int wander, int throwDistance, int debrisPerSecond) {
        /** Debris rate taken from the server config. */
        public static final int CONFIG_DEBRIS = -1;

        public Params(int ef, int coreRadius, double maxWind, int durationTicks, double speed, int bite, int height, int wander) {
            this(ef, coreRadius, maxWind, durationTicks, speed, bite, height, wander, 30, CONFIG_DEBRIS);
        }

        private static final int[] EF = {0, 0, 1, 1, 2, 2, 3, 4, 4, 5};
        private static final int[] CORE = {5, 9, 12, 18, 25, 35};
        private static final double[] WIND = {80, 110, 135, 165, 200, 250};
        private static final int[] SECONDS = {60, 90, 120, 160, 200, 240};
        private static final double[] SPEED = {0.25, 0.25, 0.22, 0.2, 0.18, 0.15};
        private static final int[] BITE = {2, 3, 4, 5, 6, 8};

        public static Params forMagnitude(int magnitude) {
            int ef = EF[Math.clamp(magnitude, 1, 10) - 1];
            return new Params(ef, CORE[ef], WIND[ef], SECONDS[ef] * 20, SPEED[ef], BITE[ef], 40 + 12 * ef, 48 + 24 * ef);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("ef", ef);
            t.putInt("core", coreRadius);
            t.putDouble("wind", maxWind);
            t.putInt("duration", durationTicks);
            t.putDouble("speed", speed);
            t.putInt("bite", bite);
            t.putInt("height", height);
            t.putInt("wander", wander);
            t.putInt("throw", throwDistance);
            t.putInt("debris", debrisPerSecond);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("ef", d.ef), t.getIntOr("core", d.coreRadius), t.getDoubleOr("wind", d.maxWind),
                    t.getIntOr("duration", d.durationTicks), t.getDoubleOr("speed", d.speed), t.getIntOr("bite", d.bite),
                    t.getIntOr("height", d.height), t.getIntOr("wander", d.wander), t.getIntOr("throw", d.throwDistance),
                    t.getIntOr("debris", d.debrisPerSecond));
        }
    }

    /** Wind is felt (blocks and creatures) out to this many core radii. */
    private static final double REACH = 1.8;

    private Params params;
    private double x, z, heading;
    private int fallingThisSecond;
    private TornadoEntity funnel;

    public Tornado(DisasterType<Tornado> type) {
        super(type);
    }

    /** Builds a tornado with explicit parameters (game tests, tuning). */
    public static Tornado create(BlockPos center, int magnitude, Params params, long seed, int warningTicks, double heading) {
        Tornado t = DisasterTypes.TORNADO.create(center, magnitude, seed, warningTicks);
        t.params = params;
        t.heading = heading;
        return t;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        params = Params.forMagnitude(magnitude);
        x = center.getX() + 0.5;
        z = center.getZ() + 0.5;
        heading = RandomSource.create(seed).nextDouble() * Math.PI * 2;
    }

    public Params params() {
        return params;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    @Override
    public int radius() {
        return params.wander;
    }

    @Override
    public String describe() {
        return "tornado EF" + params.ef + " (M" + magnitude + ")";
    }

    @Override
    public boolean isDone() {
        return !inWarning() && age - warningTicks >= params.durationTicks;
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        keepWeather(level, ticksLeft + params.durationTicks + 600, params.ef >= 1);
        if (ticksLeft % 40 != 0) return;
        for (ServerPlayer p : playersNear(level, params.wander * 1.5)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.4F, 0.5F);
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        keepWeather(level, params.durationTicks - activeTicks + 600, params.ef >= 1);
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        if (activeTicks % 20 == 0) fallingThisSecond = 0;
        move(random);

        if (!Terrain.loaded(level, (int) Math.floor(x), (int) Math.floor(z))) return;
        int groundY = Terrain.ground(level, (int) Math.floor(x), (int) Math.floor(z));
        updateFunnel(level, groundY);
        swirlEntities(level, groundY);
        if (activeTicks % 20 == 0) roar(level);

        double reach = params.coreRadius * REACH;
        int samples = (int) Math.ceil(Math.PI * reach * reach * 0.12);
        for (int i = 0; i < samples && w.hasBudget(); i++) tear(w, random, Terrain.randomColumn(random, x, z, reach));
    }

    /** Drifts along its heading, turning back toward the centre when it strays too far. */
    private void move(RandomSource random) {
        heading += (random.nextDouble() - 0.5) * 0.08;
        double fromCenter = Terrain.dist(x, z, center.getX() + 0.5, center.getZ() + 0.5);
        if (fromCenter > params.wander) {
            double home = Math.atan2(center.getZ() + 0.5 - z, center.getX() + 0.5 - x);
            double diff = Math.atan2(Math.sin(home - heading), Math.cos(home - heading));
            heading += diff * 0.05;
        }
        x += Math.cos(heading) * params.speed;
        z += Math.sin(heading) * params.speed;
    }

    /** Wind speed at a horizontal distance from the funnel's middle. */
    double windAt(double dist) {
        double rc = params.coreRadius;
        if (dist <= rc) return params.maxWind * (0.75 + 0.25 * dist / rc);
        return params.maxWind * Math.pow(rc / dist, 1.5);
    }

    /** Strips one column from the top down while the wind beats each block's resistance. */
    private void tear(BlockWriter w, RandomSource random, BlockPos column) {
        ServerLevel level = w.level();
        if (!Terrain.loaded(level, column.getX(), column.getZ())) return;
        double wind = windAt(Terrain.dist(column.getX() + 0.5, column.getZ() + 0.5, x, z));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), Terrain.surface(level, column.getX(), column.getZ()), column.getZ());
        for (int i = 0; i < params.bite && w.hasBudget(); i++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                pos.move(0, -1, 0);
                continue;
            }
            if (Wind.resistance(level, pos, state) > wind) return;
            fling(w, random, pos.immutable(), state, wind);
            pos.move(0, -1, 0);
        }
    }

    /** What happens to a torn-off block: spinning debris, thrown rubble, or gone. */
    private void fling(BlockWriter w, RandomSource random, BlockPos pos, BlockState state, double wind) {
        ServerLevel level = w.level();
        boolean solid = !state.getCollisionShape(level, pos).isEmpty();
        int debrisCap = params.debrisPerSecond == Params.CONFIG_DEBRIS ? Config.MAX_FALLING_BLOCKS_PER_SECOND.getAsInt() : params.debrisPerSecond;
        if (solid && fallingThisSecond < debrisCap) {
            fallingThisSecond++;
            FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, state);
            Vec3 spin = tangent(pos.getX() + 0.5, pos.getZ() + 0.5).scale(0.6);
            debris.setDeltaMovement(spin.x, 0.6 + random.nextDouble() * 0.6, spin.z);
            debris.setHurtsEntities(1.0F, 10);
            debris.dropItem = false;
            w.spend(1);
            return;
        }
        if (!w.set(pos, Blocks.AIR.defaultBlockState())) return;
        if (solid && random.nextFloat() < 0.25F) {
            double a = heading + (random.nextDouble() - 0.5) * 1.5;
            double d = params.throwDistance * (0.25 + random.nextDouble() * 0.75) * wind / 100;
            int lx = (int) Math.floor(pos.getX() + Math.cos(a) * d), lz = (int) Math.floor(pos.getZ() + Math.sin(a) * d);
            if (Terrain.loaded(level, lx, lz)) {
                BlockPos land = new BlockPos(lx, Terrain.ground(level, lx, lz) + 1, lz);
                // Rubble landing in water sinks out of sight rather than floating on top.
                if (Terrain.empty(level.getBlockState(land)) && level.getFluidState(land.below()).isEmpty()) w.set(land, state);
            }
        }
    }

    /** Unit vector of the counter-clockwise spin at a point. */
    private Vec3 tangent(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
        return new Vec3(-dz / len, 0, dx / len);
    }

    /** Pulls creatures, items and debris in, spins them and lifts them inside the core. */
    private void swirlEntities(ServerLevel level, int groundY) {
        double reach = params.coreRadius * REACH + 4;
        double top = groundY + params.height;
        AABB box = new AABB(x - reach, groundY - 4, z - reach, x + reach, top, z + reach);
        double pullScale = 0.04 + 0.02 * params.ef;
        for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof TornadoEntity || e.isSpectator() || e.noPhysics) continue;
            if (e instanceof Player p && (p.isCreative() && p.getAbilities().flying)) continue;
            double dx = x - e.getX(), dz = z - e.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > reach) continue;
            double strength = 1 - dist / reach;
            Vec3 inward = dist < 0.01 ? Vec3.ZERO : new Vec3(dx / dist, 0, dz / dist);
            Vec3 spin = tangent(e.getX(), e.getZ());
            double lift = 0;
            double height = e.getY() - groundY;
            // Inside the core the updraft beats gravity (0.08/tick); near the top things get flung outward.
            if (dist <= params.coreRadius * 1.2 && height < params.height * 0.6) lift = 0.12 + 0.03 * params.ef;
            else if (height > params.height * 0.6) inward = inward.scale(-1);
            Vec3 add = inward.scale(pullScale * strength).add(spin.scale((0.08 + 0.03 * params.ef) * strength)).add(0, lift, 0);
            Vec3 v = e.getDeltaMovement().add(add);
            double maxSpeed = 0.8 + 0.25 * params.ef;
            if (v.length() > maxSpeed) v = v.normalize().scale(maxSpeed);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            if (lift > 0) e.resetFallDistance();
        }
    }

    private void updateFunnel(ServerLevel level, int groundY) {
        if (funnel == null || funnel.isRemoved()) {
            funnel = TornadoEntity.spawn(level, x, groundY + 1, z, params.coreRadius, params.height);
        }
        funnel.follow(x, groundY + 1, z);
    }

    private void roar(ServerLevel level) {
        for (ServerPlayer p : level.getPlayers(p -> Terrain.dist(p.getX(), p.getZ(), x, z) < params.coreRadius * 8)) {
            double d = Terrain.dist(p.getX(), p.getZ(), x, z);
            float volume = (float) Math.max(0.2, 1.5 - d / (params.coreRadius * 6));
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, volume, 0.4F);
        }
    }

    // ---- Saving ----

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putDouble("x", x);
        tag.putDouble("z", z);
        tag.putDouble("heading", heading);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        x = tag.getDoubleOr("x", center.getX() + 0.5);
        z = tag.getDoubleOr("z", center.getZ() + 0.5);
        heading = tag.getDoubleOr("heading", 0);
    }
}
