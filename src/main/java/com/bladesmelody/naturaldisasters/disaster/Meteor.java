package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import com.bladesmelody.naturaldisasters.entity.MeteorEntity;
import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor shower: meteors streak down over an area for a while and each one
 * blasts a scorched crater with meteorite ore at its centre. From M8 one
 * giant meteor comes in near the end and leaves a huge crater.
 *
 * <p>The meteors in the sky are {@link MeteorEntity} visuals; the impact time
 * and place come from this disaster's seeded schedule, so a shower resumes
 * correctly after a restart.
 */
public class Meteor extends Disaster {
    private static final int FLIGHT_TICKS = 60;
    private static final int GIANT_FLIGHT_TICKS = 100;

    /** Everything that sets a shower's size; saved with it. */
    public record Params(int areaRadius, int durationTicks, int count, int craterMin, int craterMax, int giantRadius) {
        private static final int[] AREA = {48, 64, 80, 96, 128, 128, 128, 160, 160, 192};
        private static final int[] COUNT = {10, 16, 24, 32, 30, 40, 50, 40, 50, 40};
        private static final int[] CRATER_MIN = {4, 5, 6, 7, 8, 10, 12, 8, 8, 10};
        private static final int[] CRATER_MAX = {4, 5, 6, 8, 10, 12, 14, 12, 12, 10};
        private static final int[] GIANT = {0, 0, 0, 0, 0, 0, 0, 28, 36, 64};
        private static final int[] SECONDS = {30, 30, 30, 30, 40, 40, 40, 50, 50, 60};

        public static Params forMagnitude(int magnitude) {
            int i = Math.clamp(magnitude, 1, 10) - 1;
            return new Params(AREA[i], SECONDS[i] * 20, COUNT[i], CRATER_MIN[i], CRATER_MAX[i], GIANT[i]);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("area", areaRadius);
            t.putInt("duration", durationTicks);
            t.putInt("count", count);
            t.putInt("craterMin", craterMin);
            t.putInt("craterMax", craterMax);
            t.putInt("giant", giantRadius);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("area", d.areaRadius), t.getIntOr("duration", d.durationTicks),
                    t.getIntOr("count", d.count), t.getIntOr("craterMin", d.craterMin),
                    t.getIntOr("craterMax", d.craterMax), t.getIntOr("giant", d.giantRadius));
        }
    }

    /** One meteor of the schedule: launched at {@code launchTick}, lands {@code flight} ticks later. */
    private record Strike(int launchTick, int flight, int x, int z, int radius, double angle) {
        int impactTick() {
            return launchTick + flight;
        }
    }

    /** A crater being dug, column by column from the middle out. */
    private static final class Crater {
        final int x, y, z, radius;
        int cursor;
        int columnTop = Integer.MIN_VALUE;
        int columnY;

        Crater(int x, int y, int z, int radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("x", x);
            t.putInt("y", y);
            t.putInt("z", z);
            t.putInt("r", radius);
            t.putInt("cursor", cursor);
            t.putInt("top", columnTop);
            t.putInt("cy", columnY);
            return t;
        }

        static Crater load(CompoundTag t) {
            Crater c = new Crater(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0), t.getIntOr("r", 1));
            c.cursor = t.getIntOr("cursor", 0);
            c.columnTop = t.getIntOr("top", Integer.MIN_VALUE);
            c.columnY = t.getIntOr("cy", 0);
            return c;
        }
    }

    /** Column offsets within a radius, nearest first; shared between craters of the same size. */
    private static final Map<Integer, int[][]> DISCS = new HashMap<>();

    private Params params;
    private int impacted;
    private final List<Crater> craters = new ArrayList<>();
    private List<Strike> schedule;

    public Meteor(DisasterType<Meteor> type) {
        super(type);
    }

    /** Builds a shower with explicit parameters (game tests, tuning). */
    public static Meteor create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Meteor m = DisasterTypes.METEOR.create(center, magnitude, seed, warningTicks);
        m.params = params;
        m.schedule = null;
        return m;
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
        return params.areaRadius;
    }

    @Override
    public boolean isDone() {
        if (inWarning()) return false;
        int active = age - warningTicks;
        if (active >= Math.max(params.durationTicks, 200) * 4) return true;
        return impacted >= schedule().size() && craters.isEmpty();
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 40 != 0) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer p : playersNear(level, params.areaRadius * 1.5)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 0.3F, 0.5F);
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ParticleTypes.FIREWORK, false, true,
                        p.getX() + (r.nextDouble() - 0.5) * 60, p.getY() + 50 + r.nextDouble() * 20, p.getZ() + (r.nextDouble() - 0.5) * 60,
                        0, (r.nextDouble() - 0.5), -0.6, (r.nextDouble() - 0.5), 0.8);
            }
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        List<Strike> strikes = schedule();
        for (Strike s : strikes) {
            if (s.launchTick == activeTicks) launch(level, s);
        }
        while (impacted < strikes.size() && strikes.get(impacted).impactTick() <= activeTicks) {
            impact(level, strikes.get(impacted++));
        }
        while (!craters.isEmpty() && w.hasBudget()) {
            Crater c = craters.getFirst();
            dig(w, c);
            if (c.cursor >= disc(c.radius).length) craters.removeFirst();
        }
    }

    // ---- Schedule ----

    private List<Strike> schedule() {
        if (schedule != null) return schedule;
        RandomSource r = RandomSource.create(seed * 13 + 7);
        List<Strike> list = new ArrayList<>();
        int window = Math.max(1, (int) (params.durationTicks * 0.9));
        for (int i = 0; i < params.count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = params.areaRadius * Math.sqrt(r.nextDouble());
            int radius = params.craterMin + r.nextInt(params.craterMax - params.craterMin + 1);
            list.add(new Strike(r.nextInt(window), FLIGHT_TICKS,
                    center.getX() + (int) (Math.cos(a) * d), center.getZ() + (int) (Math.sin(a) * d), radius, r.nextDouble() * Math.PI * 2));
        }
        if (params.giantRadius > 0) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = params.areaRadius * 0.25 * r.nextDouble();
            int launch = Math.max(0, (int) (params.durationTicks * 0.8) - GIANT_FLIGHT_TICKS);
            list.add(new Strike(launch, GIANT_FLIGHT_TICKS, center.getX() + (int) (Math.cos(a) * d),
                    center.getZ() + (int) (Math.sin(a) * d), params.giantRadius, r.nextDouble() * Math.PI * 2));
        }
        list.sort(Comparator.comparingInt(Strike::impactTick));
        schedule = list;
        return list;
    }

    // ---- Flight and impact ----

    private void launch(ServerLevel level, Strike s) {
        BlockPos column = new BlockPos(s.x, level.getMinY(), s.z);
        if (!BlockWriter.isLoaded(level, column)) return;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
        Vec3 to = new Vec3(s.x + 0.5, ground, s.z + 0.5);
        double height = 120 + s.radius * 2;
        Vec3 from = to.add(Math.cos(s.angle) * height * 0.7, height, Math.sin(s.angle) * height * 0.7);
        if (!level.isLoaded(BlockPos.containing(from))) from = to.add(0, height, 0);
        MeteorEntity.launch(level, from, to, s.flight, Math.max(1.0F, s.radius / 3.0F));
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 6.0F, 0.5F);
    }

    private void impact(ServerLevel level, Strike s) {
        BlockPos column = new BlockPos(s.x, level.getMinY(), s.z);
        if (!BlockWriter.isLoaded(level, column)) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z) - 1;
        blast(level, new Vec3(s.x + 0.5, y + 1, s.z + 0.5), s.radius);
        craters.add(new Crater(s.x, y, s.z, s.radius));
    }

    /** Explosion-like damage and knockback, weaker with distance. */
    private void blast(ServerLevel level, Vec3 at, int radius) {
        double reach = radius * 1.5 + 2;
        float maxDamage = 2 + radius * 1.5F;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach))) {
            if (e.isSpectator()) continue;
            Vec3 away = e.position().subtract(at);
            double dist = away.length();
            if (dist > reach) continue;
            double falloff = 1 - dist / reach;
            e.hurtServer(level, level.damageSources().explosion(null, null), (float) (maxDamage * falloff));
            Vec3 push = (dist < 0.01 ? new Vec3(0, 1, 0) : away.normalize()).scale(1.5 * falloff);
            e.push(push.x, push.y * 0.5 + 0.4 * falloff, push.z);
        }
        level.sendParticles(radius >= 16 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, false, true,
                at.x, at.y, at.z, Math.max(1, radius / 4), radius * 0.3, radius * 0.2, radius * 0.3, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT,
                Math.max(4.0F, radius / 2.0F), 0.6F + level.getRandom().nextFloat() * 0.2F);
    }

    // ---- Craters ----

    static int[][] disc(int radius) {
        return DISCS.computeIfAbsent(radius, r -> {
            List<int[]> list = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) list.add(new int[] {dx, dz});
                }
            }
            list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
            return list.toArray(new int[0][]);
        });
    }

    /** How deep the bowl is at a distance from the centre: half the radius in the middle, 0 at the rim. */
    static int depthAt(int radius, double dist) {
        double f = dist / radius;
        return (int) Math.round(Math.max(2, radius / 2.0) * (1 - f * f));
    }

    private void dig(BlockWriter w, Crater c) {
        ServerLevel level = w.level();
        int[] off = disc(c.radius)[c.cursor];
        int x = c.x + off[0], z = c.z + off[1];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getMinY(), z);
        double dist = Math.sqrt(off[0] * off[0] + off[1] * off[1]);
        int floor = c.y - depthAt(c.radius, dist) + 1;
        if (c.columnTop == Integer.MIN_VALUE) {
            if (!BlockWriter.isLoaded(level, pos)) {
                c.cursor++;
                return;
            }
            c.columnTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            c.columnY = c.columnTop;
        }
        floor = Math.max(level.getMinY() + 1, floor);
        while (c.columnY >= floor && w.hasBudget()) {
            pos.setY(c.columnY--);
            w.set(pos, Blocks.AIR.defaultBlockState());
        }
        if (c.columnY >= floor) return;
        finishColumn(w, c, x, z, floor, dist);
        c.cursor++;
        c.columnTop = Integer.MIN_VALUE;
    }

    /** Scorches the new crater floor, plants meteorite ore in the middle, lava in big ones and fire on the rim. */
    private void finishColumn(BlockWriter w, Crater c, int x, int z, int floor, double dist) {
        if (c.columnTop < floor - 1) return;
        RandomSource r = RandomSource.create(seed ^ BlockPos.asLong(x, c.y, z));
        double f = dist / c.radius;
        BlockPos lining = new BlockPos(x, floor - 1, z);
        int oreRadius = c.radius / 10;
        if (dist <= oreRadius) {
            w.set(lining, ModRegistry.METEORITE_ORE.get().defaultBlockState());
            if (dist <= oreRadius / 2.0) w.set(lining.below(), ModRegistry.METEORITE_ORE.get().defaultBlockState());
            return;
        }
        if (c.radius >= 24 && f < 0.12) {
            w.set(lining, Blocks.OBSIDIAN.defaultBlockState());
            w.set(lining.above(), Blocks.LAVA.defaultBlockState());
            return;
        }
        BlockState scorched = scorched(f, r);
        if (scorched != null) w.set(lining, scorched);
        if (f >= 0.7 && r.nextFloat() < 0.3F && !w.level().getBlockState(lining).isAir()) {
            w.set(lining.above(), Blocks.FIRE.defaultBlockState());
        }
    }

    private static BlockState scorched(double f, RandomSource r) {
        float roll = r.nextFloat();
        if (f < 0.5) {
            if (roll < 0.45F) return Blocks.MAGMA_BLOCK.defaultBlockState();
            if (roll < 0.8F) return Blocks.BLACKSTONE.defaultBlockState();
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        if (roll < 0.3F) return Blocks.BLACKSTONE.defaultBlockState();
        if (roll < 0.45F) return Blocks.MAGMA_BLOCK.defaultBlockState();
        return null;
    }

    // ---- Saving ----

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putInt("impacted", impacted);
        ListTag list = new ListTag();
        for (Crater c : craters) list.add(c.save());
        tag.put("craters", list);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"), magnitude);
        schedule = null;
        impacted = tag.getIntOr("impacted", 0);
        craters.clear();
        ListTag list = tag.getListOrEmpty("craters");
        for (int i = 0; i < list.size(); i++) craters.add(Crater.load(list.getCompoundOrEmpty(i)));
    }
}
