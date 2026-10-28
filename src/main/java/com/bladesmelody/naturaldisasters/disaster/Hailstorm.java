package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/**
 * Hailstorm: ice pelts down out of a thunderstorm. Anything under the open
 * sky gets hit (armour helps, a roof is better), glass cracks and shatters,
 * leaves are shredded, crops and flowers flattened, and the ground ends up
 * dusted white. Bigger magnitudes mean bigger stones.
 */
public class Hailstorm extends Disaster {
    public static final ResourceKey<DamageType> HAIL = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "hail"));

    /** Everything that sets a hailstorm's size; saved with it. size = hailstone size 1–5. */
    public record Params(int radius, int durationTicks, int size, double density) {
        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            return new Params(48 + 16 * m, (40 + 10 * m) * 20, (m + 1) / 2, 0.0015);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("duration", durationTicks);
            t.putInt("size", size);
            t.putDouble("density", density);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("radius", d.radius), t.getIntOr("duration", d.durationTicks),
                    t.getIntOr("size", d.size), t.getDoubleOr("density", d.density));
        }
    }

    private Params params;

    public Hailstorm(DisasterType<Hailstorm> type) {
        super(type);
    }

    /** Builds a hailstorm with explicit parameters (game tests, tuning). */
    public static Hailstorm create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Hailstorm h = DisasterTypes.HAILSTORM.create(center, magnitude, seed, warningTicks);
        h.params = params;
        return h;
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

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        keepWeather(level, ticksLeft + params.durationTicks + 200, true);
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        keepWeather(level, params.durationTicks - activeTicks + 200, true);
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        double expected = Math.PI * params.radius * params.radius * params.density * params.size;
        int samples = (int) Math.min(1500, expected) + (random.nextDouble() < expected - (int) expected ? 1 : 0);
        for (int i = 0; i < samples && w.hasBudget(); i++) {
            BlockPos c = Terrain.randomColumn(random, center.getX() + 0.5, center.getZ() + 0.5, params.radius);
            if (Terrain.loaded(level, c.getX(), c.getZ())) strike(w, random, c.getX(), c.getZ());
        }
        if (activeTicks % 10 == 0) pelt(level, random);
        if (activeTicks % 2 == 0) clatter(level, random);
    }

    /** One hailstone hitting the top of a column. */
    private void strike(BlockWriter w, RandomSource random, int x, int z) {
        ServerLevel level = w.level();
        BlockPos top = new BlockPos(x, Terrain.surface(level, x, z), z);
        BlockState s = level.getBlockState(top);
        float force = params.size;
        if (s.is(Tags.Blocks.GLASS_BLOCKS) || s.is(Tags.Blocks.GLASS_PANES)) {
            if (random.nextFloat() < 0.15F * force) {
                w.set(top, Blocks.AIR.defaultBlockState());
                level.playSound(null, top, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
        } else if (s.is(BlockTags.LEAVES)) {
            if (random.nextFloat() < 0.15F * force) w.set(top, Blocks.AIR.defaultBlockState());
        } else if (s.is(BlockTags.CROPS) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
                || s.is(BlockTags.SAPLINGS) || s.is(Blocks.FERN) || s.is(Blocks.TURTLE_EGG)) {
            if (random.nextFloat() < 0.25F * force) w.set(top, Blocks.AIR.defaultBlockState());
        } else if (s.isFaceSturdy(level, top, net.minecraft.core.Direction.UP) && s.getFluidState().isEmpty() && random.nextFloat() < 0.03F) {
            BlockPos above = top.above();
            if (level.getBlockState(above).isAir()) w.set(above, Blocks.SNOW.defaultBlockState());
        }
    }

    /** Creatures under the open sky take hits; armour (not bypassed) soaks some up. */
    private void pelt(ServerLevel level, RandomSource random) {
        double r = params.radius;
        AABB box = new AABB(center.getX() - r, level.getMinY(), center.getZ() - r, center.getX() + r, level.getMaxY(), center.getZ() + r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e.isSpectator() || !level.canSeeSky(e.blockPosition().above())) continue;
            if (Terrain.dist(e.getX(), e.getZ(), center.getX(), center.getZ()) > r) continue;
            if (random.nextFloat() < 0.25F + 0.1F * params.size) {
                e.hurtServer(level, level.damageSources().source(HAIL), params.size);
            }
        }
    }

    private void clatter(ServerLevel level, RandomSource random) {
        for (ServerPlayer p : playersNear(level, params.radius)) {
            level.sendParticles(p, ParticleTypes.ITEM_SNOWBALL, true, true, p.getX(), p.getY() + 6, p.getZ(),
                    10 + 5 * params.size, 8, 2, 8, 0.0);
            if (random.nextInt(3) == 0) {
                level.playSound(null, p.getX() + (random.nextDouble() - 0.5) * 8, p.getY(), p.getZ() + (random.nextDouble() - 0.5) * 8,
                        SoundEvents.STONE_HIT, SoundSource.WEATHER, 0.4F + 0.1F * params.size, 1.6F);
            }
        }
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
