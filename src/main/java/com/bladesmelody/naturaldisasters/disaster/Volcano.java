package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.shake.Shake;
import com.bladesmelody.naturaldisasters.engine.DisasterManager;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import com.bladesmelody.naturaldisasters.entity.MeteorEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Volcano: the ground rumbles and a cone of rock pushes up out of it with a
 * lava-filled crater on top. Then it erupts: lava rivers run down the slopes
 * (and on downhill past the foot of the mountain), glowing bombs rain down
 * and blast small craters, and a column of ash rises. When it's over the
 * lava rivers cool into basalt and the mountain stays.
 */
public class Volcano extends Disaster {
    public static final ResourceKey<DamageType> PYROCLASTIC = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "pyroclastic"));

    /** Everything that sets a volcano's size; saved with it. */
    /** VEI (0–8) for each magnitude 1–10. */
    private static final int[] VEI = {0, 0, 1, 2, 3, 4, 5, 6, 7, 8};
    /** Magnitude for each VEI 0–8. */
    private static final int[] MAGNITUDE = {1, 3, 4, 5, 6, 7, 8, 9, 10};
    // By VEI 0–8 (owner's table; VEI 0–2 have no heat wave or ash cloud).
    private static final int[] HEAT_REACH = {0, 0, 0, 64, 200, 640, 2000, 6400, 100_000};
    private static final int[] ASH_RADIUS = {0, 0, 0, 1000, 3200, 0, 0, 0, 0}; // 0 = whole world
    private static final int[] ASH_LAYERS = {0, 0, 0, 1, 3, 6, 8, 16, 24};
    private static final int[] DARK_DAYS = {0, 0, 0, 2, 20, 28, 42, 56, 84};

    public static int vei(int magnitude) {
        return VEI[Math.clamp(magnitude, 1, 10) - 1];
    }

    public static int magnitudeForVei(int vei) {
        return MAGNITUDE[Math.clamp(vei, 0, 8)];
    }

    /** Ordinary random volcanoes: VEI 0–4, VEI 4 ten times rarer than the rest. VEI 5+ only come from the great-eruption timer. */
    public static int rollRandomMagnitude(RandomSource random) {
        double[] weights = {1, 1, 1, 1, 0.1};
        return magnitudeForVei(pick(weights, random));
    }

    /** A great eruption: VEI 5–8, each step ten times rarer. */
    public static int rollGreatMagnitude(RandomSource random) {
        double[] weights = {1, 0.1, 0.01, 0.001};
        return magnitudeForVei(5 + pick(weights, random));
    }

    private static int pick(double[] weights, RandomSource random) {
        double total = 0;
        for (double w : weights) total += w;
        double r = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0) return i;
        }
        return weights.length - 1;
    }

    /**
     * Everything that sets a volcano's size; saved with it. From VEI 3 an
     * eruption also sends a heat wave out to {@code heatReach}, leaves an ash
     * cloud (radius {@code ashRadius}, 0 = whole world; ash {@code ashLayers}
     * deep near the volcano; dark for {@code darkTicks}) and is preceded by an
     * earthquake of magnitude {@code quake} (0 = none).
     */
    public record Params(int height, int baseRadius, int craterRadius, int buildTicks, int eruptTicks, int flows,
                         int flowLength, int bombInterval, int vei, int heatReach, int ashRadius, int ashLayers,
                         int darkTicks, int quake) {
        private static final int[] HEIGHT = {8, 12, 16, 20, 26, 32, 40, 48, 56, 72};

        /** A small effusive volcano (VEI 0: no heat wave, ash cloud or quake). */
        public Params(int height, int baseRadius, int craterRadius, int buildTicks, int eruptTicks, int flows,
                      int flowLength, int bombInterval) {
            this(height, baseRadius, craterRadius, buildTicks, eruptTicks, flows, flowLength, bombInterval, 0, 0, 0, 0, 0, 0);
        }

        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            int h = HEIGHT[m - 1];
            int base = (int) (h * 2.2);
            int v = Volcano.vei(m);
            return new Params(h, base, Math.max(2, h / 5), (20 + h / 2) * 20, (30 + 10 * m) * 20, 2 + m / 2,
                    (int) (base * 1.3), Math.max(4, 22 - 2 * m), v, HEAT_REACH[v], ASH_RADIUS[v], ASH_LAYERS[v],
                    DARK_DAYS[v] * 24000, v >= 3 ? Math.min(5, v) : 0);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("height", height);
            t.putInt("base", baseRadius);
            t.putInt("crater", craterRadius);
            t.putInt("build", buildTicks);
            t.putInt("erupt", eruptTicks);
            t.putInt("flows", flows);
            t.putInt("flowLength", flowLength);
            t.putInt("bombs", bombInterval);
            t.putInt("vei", vei);
            t.putInt("heatReach", heatReach);
            t.putInt("ashRadius", ashRadius);
            t.putInt("ashLayers", ashLayers);
            t.putInt("darkTicks", darkTicks);
            t.putInt("quake", quake);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("height", d.height), t.getIntOr("base", d.baseRadius), t.getIntOr("crater", d.craterRadius),
                    t.getIntOr("build", d.buildTicks), t.getIntOr("erupt", d.eruptTicks), t.getIntOr("flows", d.flows),
                    t.getIntOr("flowLength", d.flowLength), t.getIntOr("bombs", d.bombInterval), t.getIntOr("vei", d.vei),
                    t.getIntOr("heatReach", d.heatReach), t.getIntOr("ashRadius", d.ashRadius), t.getIntOr("ashLayers", d.ashLayers),
                    t.getIntOr("darkTicks", d.darkTicks), t.getIntOr("quake", d.quake));
        }
    }

    /** A lava river creeping downhill one block at a time. */
    private static final class Flow {
        int x, z, dx, dz, length;
        boolean stopped;

        Flow(int x, int z, int dx, int dz) {
            this.x = x;
            this.z = z;
            this.dx = dx;
            this.dz = dz;
        }
    }

    /** A volcanic bomb in the air. */
    private record Bomb(int impactTick, int x, int z) {}

    private static final int BOMB_FLIGHT = 40;
    private static final int NONE = Integer.MIN_VALUE;

    private Params params;
    private int baseY = NONE;
    private int buildCursor;
    private boolean lakeFilled;
    private int eruptStart = NONE;
    private final List<Flow> flows = new ArrayList<>();
    private final LongArrayList lava = new LongArrayList();
    private final List<Bomb> bombs = new ArrayList<>();
    private int coolCursor;
    private boolean quakeStarted;
    /** Heat wave: current radius, or NONE before it starts. */
    private double heat = NONE;
    private boolean heatDone;
    private final LongArrayList heatQueue = new LongArrayList();
    private final LongOpenHashSet heatSeen = new LongOpenHashSet();
    private int heatColumn;
    private static final long NO_CHUNK = Long.MIN_VALUE;
    private long heatChunk = NO_CHUNK;
    private int heatLimit = 256;

    public Volcano(DisasterType<Volcano> type) {
        super(type);
    }

    /** Builds a volcano with explicit parameters (game tests, tuning). */
    public static Volcano create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Volcano v = DisasterTypes.VOLCANO.create(center, magnitude, seed, warningTicks);
        v.params = params;
        return v;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        params = Params.forMagnitude(magnitude);
    }

    public Params params() {
        return params;
    }

    public int baseY() {
        return baseY;
    }

    @Override
    public int radius() {
        return params.baseRadius;
    }

    @Override
    public String describe() {
        return "volcano VEI " + params.vei + " (M" + magnitude + ", radius " + params.baseRadius + ")";
    }

    @Override
    public boolean isDone() {
        if (inWarning()) return false;
        int active = age - warningTicks;
        if (active > (params.buildTicks + params.eruptTicks) * 4 + 2000) return true;
        boolean heatOver = params.vei < 3 || heatDone && heatQueue.isEmpty() && heatChunk == NO_CHUNK;
        return eruptStart != NONE && active >= eruptStart + params.eruptTicks && coolCursor >= lava.size() && bombs.isEmpty() && heatOver;
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 20 != 0) return;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, false, true, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                6, 1.5, 0.2, 1.5, 0.02);
        if (ticksLeft % 40 == 0) {
            for (ServerPlayer p : playersNear(level, params.baseRadius * 3)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.3F, 0.2F);
            }
        }
    }

    /** Height of the finished cone above the base at a distance from the vent. */
    int coneTop(double dist) {
        if (dist <= params.craterRadius) return baseY + params.height - Math.max(1, params.height / 4);
        double f = Math.min(1, dist / params.baseRadius);
        return baseY + (int) Math.round(params.height * Math.pow(1 - f, 1.2));
    }

    /** Set each tick so helpers that only get a random source can still reach the level. */
    private ServerLevel lastLevel;

    private AshCloud.Params ashCloudParams() {
        int radius = params.ashRadius;
        // Without world-wide darkness (config) the big ones darken a very large area instead.
        if (radius <= 0 && !Config.WORLD_DARKNESS.getAsBoolean()) radius = 10_000;
        return new AshCloud.Params(radius, Math.min(params.heatReach, 2000), params.ashLayers, 1, params.darkTicks);
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        lastLevel = level;
        if (baseY == NONE) baseY = Terrain.ground(level, center.getX(), center.getZ());
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        int[][] disc = Terrain.disc(params.baseRadius);
        if (params.quake > 0 && !quakeStarted) {
            // The ground shakes as the magma rises.
            DisasterManager.start(level, DisasterTypes.EARTHQUAKE, center, params.quake, 0);
            quakeStarted = true;
        }

        if (eruptStart == NONE) {
            int allowed = (int) Math.min(disc.length, (long) disc.length * (activeTicks + 1) / params.buildTicks);
            while (buildCursor < allowed && w.hasBudget()) raise(w, disc[buildCursor++]);
            liftEntities(level);
            if (activeTicks % 10 == 0) shake(level, random);
            if (buildCursor >= disc.length && w.hasBudget()) {
                fillLake(w);
                if (lakeFilled) startEruption(activeTicks, random);
            }
            return;
        }

        int erupting = activeTicks - eruptStart;
        if (erupting < params.eruptTicks) {
            if (erupting % 4 == 0) for (Flow f : flows) advance(w, f);
            if (erupting % params.bombInterval == 0) launchBomb(level, random, activeTicks);
            plume(level, random);
            if (erupting % 30 == 0) boom(level);
        }
        landBombs(w, activeTicks);
        if (heat != NONE) heatWave(level, w, activeTicks);
        if (erupting >= params.eruptTicks) {
            while (coolCursor < lava.size() && w.hasBudget()) cool(w, lava.getLong(coolCursor++));
        }
    }

    // ---- Building the cone ----

    private void raise(BlockWriter w, int[] off) {
        ServerLevel level = w.level();
        int x = center.getX() + off[0], z = center.getZ() + off[1];
        if (!Terrain.loaded(level, x, z)) return;
        double dist = Math.sqrt(off[0] * off[0] + off[1] * off[1]);
        int target = coneTop(dist);
        int ground = Terrain.ground(level, x, z);
        if (ground >= target) return;
        RandomSource r = RandomSource.create(seed ^ BlockPos.asLong(x, 0, z));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = ground + 1; y <= target; y++) {
            pos.setY(y);
            w.set(pos, y == target ? surfaceRock(r) : (r.nextInt(5) == 0 ? Blocks.BLACKSTONE : Blocks.STONE).defaultBlockState());
        }
    }

    private static BlockState surfaceRock(RandomSource r) {
        int roll = r.nextInt(10);
        if (roll < 5) return Blocks.BASALT.defaultBlockState();
        if (roll < 8) return Blocks.BLACKSTONE.defaultBlockState();
        return Blocks.TUFF.defaultBlockState();
    }

    /** Creatures caught as the rock rises are pushed up on top rather than buried. */
    private void liftEntities(ServerLevel level) {
        double r = params.baseRadius;
        AABB box = new AABB(center).inflate(r, params.height + 8, r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            BlockPos feet = e.blockPosition();
            if (Terrain.empty(level.getBlockState(feet))) continue;
            e.teleportTo(e.getX(), Terrain.ground(level, feet.getX(), feet.getZ()) + 1, e.getZ());
        }
    }

    private void shake(ServerLevel level, RandomSource random) {
        double r = params.baseRadius * 1.5;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(r, 32, r))) {
            if (e.onGround()) e.push((random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2);
        }
    }

    /** Fills the crater with a lava lake (kept quiet so it stays in the crater). */
    private void fillLake(BlockWriter w) {
        if (lakeFilled) return;
        ServerLevel level = w.level();
        int floor = coneTop(0);
        for (int[] off : Terrain.disc(params.craterRadius)) {
            int x = center.getX() + off[0], z = center.getZ() + off[1];
            BlockPos pos = new BlockPos(x, floor, z);
            if (!w.hasBudget()) return;
            if (Terrain.loaded(level, x, z)) w.setQuiet(pos, Blocks.LAVA.defaultBlockState());
        }
        lakeFilled = true;
    }

    private void startEruption(int activeTicks, RandomSource random) {
        eruptStart = activeTicks;
        if (params.vei >= 3) {
            heat = 0;
            DisasterManager.add(lastLevel, AshCloud.create(center, params.vei, ashCloudParams(), seed + 1));
        }
        int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        int first = random.nextInt(8);
        for (int i = 0; i < params.flows; i++) {
            int[] d = dirs[(first + i * 8 / Math.max(1, params.flows) + random.nextInt(2)) % 8];
            int start = params.craterRadius + 1;
            flows.add(new Flow(center.getX() + d[0] * start, center.getZ() + d[1] * start, d[0], d[1]));
        }
    }

    // ---- Lava rivers ----

    /** Moves a lava river one block downhill (or straight on over flat ground). */
    private void advance(BlockWriter w, Flow f) {
        if (f.stopped || !w.hasBudget()) return;
        ServerLevel level = w.level();
        if (f.length >= params.flowLength || !Terrain.loaded(level, f.x, f.z)) {
            f.stopped = true;
            return;
        }
        int here = Terrain.ground(level, f.x, f.z);
        int bestX = f.x + f.dx, bestZ = f.z + f.dz;
        int best = Terrain.loaded(level, bestX, bestZ) ? Terrain.ground(level, bestX, bestZ) : Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                // Only roughly forward: lava doesn't turn back uphill toward the vent.
                if (dx * f.dx + dz * f.dz < 0) continue;
                int nx = f.x + dx, nz = f.z + dz;
                if (!Terrain.loaded(level, nx, nz)) continue;
                int g = Terrain.ground(level, nx, nz);
                if (g < best) {
                    best = g;
                    bestX = nx;
                    bestZ = nz;
                }
            }
        }
        if (best > here + 1) {
            f.stopped = true;
            return;
        }
        BlockPos pos = new BlockPos(f.x, here + 1, f.z);
        if (Terrain.empty(level.getBlockState(pos)) && w.setQuiet(pos, Blocks.LAVA.defaultBlockState())) lava.add(pos.asLong());
        igniteAround(w, pos);
        f.dx = Integer.signum(bestX - f.x);
        f.dz = Integer.signum(bestZ - f.z);
        f.x = bestX;
        f.z = bestZ;
        f.length++;
    }

    /** Lava sets plants and wood beside it alight. */
    private static void igniteAround(BlockWriter w, BlockPos pos) {
        ServerLevel level = w.level();
        for (BlockPos p : new BlockPos[] {pos.north(), pos.south(), pos.east(), pos.west(), pos.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) {
                BlockState fire = BaseFireBlock.getState(level, p);
                if (fire.canSurvive(level, p) && level.getRandom().nextInt(3) == 0) w.set(p, fire);
            }
        }
    }

    /** A cooled river block: basalt with the odd magma block. */
    private void cool(BlockWriter w, long packed) {
        BlockPos pos = BlockPos.of(packed);
        if (!w.level().getFluidState(pos).isEmpty()) {
            RandomSource r = RandomSource.create(seed ^ packed);
            w.setQuiet(pos, (r.nextInt(5) == 0 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).defaultBlockState());
        }
    }

    // ---- Bombs and ash ----

    private void launchBomb(ServerLevel level, RandomSource random, int activeTicks) {
        BlockPos target = Terrain.randomColumn(random, center.getX() + 0.5, center.getZ() + 0.5, params.baseRadius * 1.5);
        if (!Terrain.loaded(level, target.getX(), target.getZ())) return;
        Vec3 vent = new Vec3(center.getX() + 0.5, coneTop(0) + 2, center.getZ() + 0.5);
        Vec3 land = new Vec3(target.getX() + 0.5, Terrain.ground(level, target.getX(), target.getZ()) + 1, target.getZ() + 0.5);
        // Visual: up out of the vent and down onto the target, as two straight legs.
        Vec3 peak = vent.add(land).scale(0.5).add(0, params.height + 10, 0);
        MeteorEntity.launch(level, peak, land, BOMB_FLIGHT, 1.0F);
        level.sendParticles(ParticleTypes.LAVA, false, true, vent.x, vent.y, vent.z, 12, 1, 0.5, 1, 0.3);
        bombs.add(new Bomb(activeTicks + BOMB_FLIGHT, target.getX(), target.getZ()));
    }

    private void landBombs(BlockWriter w, int activeTicks) {
        ServerLevel level = w.level();
        while (!bombs.isEmpty() && bombs.getFirst().impactTick <= activeTicks) {
            Bomb b = bombs.removeFirst();
            if (!Terrain.loaded(level, b.x, b.z)) continue;
            int y = Terrain.ground(level, b.x, b.z);
            BlockPos hit = new BlockPos(b.x, y, b.z);
            for (int[] off : Terrain.disc(2)) {
                BlockPos p = hit.offset(off[0], 0, off[1]);
                w.set(p, (off[0] == 0 && off[1] == 0 ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState());
                BlockPos above = p.above();
                if (Terrain.empty(level.getBlockState(above)) && level.getRandom().nextInt(3) == 0) w.set(above, BaseFireBlock.getState(level, above));
            }
            Vec3 at = Vec3.atCenterOf(hit.above());
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4))) {
                e.hurtServer(level, level.damageSources().explosion(null, null), 6.0F);
                e.igniteForTicks(80);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, false, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 2.0F, 0.9F);
        }
    }

    /** The ash column over the vent and ash drifting down around it. */
    private void plume(ServerLevel level, RandomSource random) {
        double vx = center.getX() + 0.5, vy = coneTop(0) + 1, vz = center.getZ() + 0.5;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, false, true, vx, vy, vz, 4 + params.craterRadius,
                params.craterRadius * 0.4, 0.5, params.craterRadius * 0.4, 0.06);
        if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.LAVA, false, true, vx, vy, vz, 4, 1, 0.2, 1, 0.2);
        for (ServerPlayer p : playersNear(level, params.baseRadius * 3)) {
            level.sendParticles(p, ParticleTypes.ASH, true, true, p.getX(), p.getY() + 6, p.getZ(), 30, 12, 4, 12, 0.01);
        }
    }

    private void boom(ServerLevel level) {
        Shake.near(level, center.getX() + 0.5, center.getZ() + 0.5, params.baseRadius * 4 + 32, 0.6F + 0.25F * params.vei, 20);
        for (ServerPlayer p : playersNear(level, params.baseRadius * 4)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.8F, 0.3F);
        }
    }

    // ---- Heat wave (VEI 3+) ----

    /** The glowing cloud of ash and hot gas rolling out from the volcano. */
    private void heatWave(ServerLevel level, BlockWriter w, int activeTicks) {
        if (!heatDone) {
            double from = heat;
            heat = Math.min(params.heatReach, heat + 1.5 + 0.5 * (params.vei - 3));
            if (activeTicks % 100 == 0) heatLimit = farthestReach(level);
            enqueueRing(level, heat);
            scorchCreatures(level, from - 2, heat + 1);
            if (activeTicks % 3 == 0) ringCloud(level);
            if (heat >= params.heatReach || heat > heatLimit) heatDone = true;
        }
        // Finish the chunk in progress first: heatColumn belongs to it, not to whatever was queued last.
        while ((heatChunk != NO_CHUNK || !heatQueue.isEmpty()) && w.hasBudget()) {
            if (heatChunk == NO_CHUNK) {
                heatChunk = heatQueue.removeLong(heatQueue.size() - 1);
                heatColumn = 0;
            }
            if (burnChunk(w, heatChunk)) heatChunk = NO_CHUNK;
        }
    }

    /** No point rolling on past every player's loaded area. */
    private int farthestReach(ServerLevel level) {
        int view = level.getServer().getPlayerList().getViewDistance() * 16 + 32;
        double far = 256;
        for (ServerPlayer p : level.players()) far = Math.max(far, Terrain.dist(p.getX(), p.getZ(), center.getX(), center.getZ()) + view);
        return (int) far;
    }

    private void enqueueRing(ServerLevel level, double r) {
        // Points 4 blocks apart, so no chunk corner the wave crosses is skipped.
        int steps = Math.max(16, (int) (2 * Math.PI * r / 4));
        for (int i = 0; i < steps; i++) {
            double a = i * 2 * Math.PI / steps;
            int cx = (int) Math.floor(center.getX() + Math.cos(a) * r) >> 4, cz = (int) Math.floor(center.getZ() + Math.sin(a) * r) >> 4;
            long key = ChunkPos.pack(cx, cz);
            if (level.getChunkSource().getChunkNow(cx, cz) != null && heatSeen.add(key)) heatQueue.add(key);
        }
    }

    /** Burns the vegetation of one chunk inside the heat wave's reach; true when done. */
    private boolean burnChunk(BlockWriter w, long key) {
        ServerLevel level = w.level();
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        if (level.getChunkSource().getChunkNow(cx, cz) == null) return true;
        while (heatColumn < 256 && w.hasBudget()) {
            int x = (cx << 4) + (heatColumn & 15), z = (cz << 4) + (heatColumn >> 4);
            heatColumn++;
            if (Terrain.dist(x, z, center.getX(), center.getZ()) <= params.heatReach) scorchColumn(w, x, z);
        }
        return heatColumn >= 256;
    }

    /** Leaves, plants and crops gone, trunks charred, grass scorched; buildings are left alone. */
    private static void scorchColumn(BlockWriter w, int x, int z) {
        ServerLevel level = w.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Terrain.surface(level, x, z), z);
        // A column is always finished once started (at most 24 blocks): stopping half-way would
        // leave it counted as done with unburnt trunks under the burnt-off leaves.
        for (int i = 0; i < 24; i++, pos.move(0, -1, 0)) {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) continue;
            if (s.is(BlockTags.LOGS)) {
                if (!s.is(Blocks.STRIPPED_DARK_OAK_LOG)) w.set(pos, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
            } else if (s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS)
                    || s.is(Blocks.VINE) || (s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(Blocks.SNOW))) {
                w.set(pos, Blocks.AIR.defaultBlockState());
            } else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.MOSS_BLOCK)) {
                w.set(pos, Blocks.COARSE_DIRT.defaultBlockState());
                return;
            } else {
                return;
            }
        }
    }

    /** Creatures the wave passes over: dead in the open, badly burnt under cover. */
    private void scorchCreatures(ServerLevel level, double from, double to) {
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof LivingEntity living) || living.isSpectator() || !living.isAlive()) continue;
            double d = Terrain.dist(e.getX(), e.getZ(), center.getX() + 0.5, center.getZ() + 0.5);
            if (d < from || d > to) continue;
            if (level.canSeeSky(BlockPos.containing(e.getX(), e.getEyeY(), e.getZ()))) {
                living.hurtServer(level, level.damageSources().source(PYROCLASTIC), 1000.0F);
            } else {
                living.hurtServer(level, level.damageSources().source(PYROCLASTIC), 8.0F);
                living.igniteForTicks(200);
            }
        }
    }

    /** A rolling grey-black cloud along the wave front, near each player. */
    private void ringCloud(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            double d = Terrain.dist(p.getX(), p.getZ(), center.getX() + 0.5, center.getZ() + 0.5);
            if (Math.abs(d - heat) > 160 || d < 0.1) continue;
            double ux = (p.getX() - center.getX() - 0.5) / d, uz = (p.getZ() - center.getZ() - 0.5) / d;
            for (int side = -2; side <= 2; side++) {
                double px = center.getX() + 0.5 + ux * heat - uz * side * 12, pz = center.getZ() + 0.5 + uz * heat + ux * side * 12;
                int y = Terrain.loaded(level, (int) px, (int) pz) ? Terrain.surface(level, (int) px, (int) pz) : (int) p.getY();
                level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, px, y + 3, pz, 6, 5, 4, 5, 0.02);
                level.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, true, px, y + 2, pz, 10, 6, 3, 6, 0.05);
            }
        }
    }

    // ---- Saving ----

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putInt("baseY", baseY);
        tag.putInt("build", buildCursor);
        tag.putBoolean("lake", lakeFilled);
        tag.putInt("eruptStart", eruptStart);
        ListTag fl = new ListTag();
        for (Flow f : flows) {
            CompoundTag t = new CompoundTag();
            t.putIntArray("v", new int[] {f.x, f.z, f.dx, f.dz, f.length, f.stopped ? 1 : 0});
            fl.add(t);
        }
        tag.put("flows", fl);
        tag.putLongArray("lava", lava.toLongArray());
        ListTag bl = new ListTag();
        for (Bomb b : bombs) {
            CompoundTag t = new CompoundTag();
            t.putIntArray("v", new int[] {b.impactTick, b.x, b.z});
            bl.add(t);
        }
        tag.put("bombs", bl);
        tag.putInt("cool", coolCursor);
        tag.putBoolean("quake", quakeStarted);
        tag.putDouble("heat", heat);
        tag.putBoolean("heatDone", heatDone);
        tag.putLongArray("heatQueue", heatQueue.toLongArray());
        tag.putInt("heatColumn", heatColumn);
        tag.putLong("heatChunk", heatChunk);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        baseY = tag.getIntOr("baseY", NONE);
        buildCursor = tag.getIntOr("build", 0);
        lakeFilled = tag.getBooleanOr("lake", false);
        eruptStart = tag.getIntOr("eruptStart", NONE);
        flows.clear();
        ListTag fl = tag.getListOrEmpty("flows");
        for (int i = 0; i < fl.size(); i++) {
            int[] v = fl.getCompoundOrEmpty(i).getIntArray("v").orElse(new int[6]);
            if (v.length < 6) continue;
            Flow f = new Flow(v[0], v[1], v[2], v[3]);
            f.length = v[4];
            f.stopped = v[5] != 0;
            flows.add(f);
        }
        lava.clear();
        lava.addElements(0, tag.getLongArray("lava").orElse(new long[0]));
        bombs.clear();
        ListTag bl = tag.getListOrEmpty("bombs");
        for (int i = 0; i < bl.size(); i++) {
            int[] v = bl.getCompoundOrEmpty(i).getIntArray("v").orElse(new int[3]);
            if (v.length >= 3) bombs.add(new Bomb(v[0], v[1], v[2]));
        }
        coolCursor = tag.getIntOr("cool", 0);
        quakeStarted = tag.getBooleanOr("quake", false);
        heat = tag.getDoubleOr("heat", NONE);
        heatDone = tag.getBooleanOr("heatDone", false);
        heatQueue.clear();
        heatQueue.addElements(0, tag.getLongArray("heatQueue").orElse(new long[0]));
        heatSeen.clear();
        heatSeen.addAll(heatQueue);
        heatColumn = tag.getIntOr("heatColumn", 0);
        heatChunk = tag.getLongOr("heatChunk", NO_CHUNK);
        if (heatChunk != NO_CHUNK) heatSeen.add(heatChunk);
    }
}
