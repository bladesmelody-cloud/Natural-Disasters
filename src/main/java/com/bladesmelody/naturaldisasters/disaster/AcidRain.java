package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.protect.Protection;
import com.bladesmelody.naturaldisasters.registry.ModSounds;
import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.sky.SkyZones;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Acid rain: a sickly green downpour that eats at everything left out in it.
 * Leaves wither, grass and plants die back to dirt and dead bushes, exposed
 * copper turns green within minutes and, in strong storms, even stone pits
 * and crumbles. Creatures in the open are burnt unless they wear a helmet,
 * which corrodes instead.
 */
public class AcidRain extends Disaster {
    public static final ResourceKey<DamageType> ACID = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "acid_rain"));
    private static final DustParticleOptions ACID_DROP = new DustParticleOptions(0x9CFF3A, 0.8F);

    /** Everything that sets an acid storm's size; saved with it. potency = 1–10. */
    public record Params(int radius, int durationTicks, int potency, double density) {
        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            return new Params(64 + 24 * m, (60 + 20 * m) * 20, m, 0.0012);
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

    public AcidRain(DisasterType<AcidRain> type) {
        super(type);
    }

    /** Builds an acid storm with explicit parameters (game tests, tuning). */
    public static AcidRain create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        AcidRain a = DisasterTypes.ACID_RAIN.create(center, magnitude, seed, warningTicks);
        a.params = params;
        return a;
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
        keepWeather(level, ticksLeft + params.durationTicks + 200, false);
        if (ticksLeft % 10 == 0) drizzle(level, 6);
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        SkyZones.put(id, new SkyZones.Zone(SkyZones.Kind.ACID_RAIN, center.getX() + 0.5, center.getZ() + 0.5, params.radius, magnitude));
        keepWeather(level, params.durationTicks - activeTicks + 200, false);
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        double expected = Math.PI * params.radius * params.radius * params.density * (0.5 + params.potency * 0.15);
        int samples = (int) Math.min(1500, expected) + (random.nextDouble() < expected - (int) expected ? 1 : 0);
        for (int i = 0; i < samples && w.hasBudget(); i++) {
            BlockPos c = Terrain.randomColumn(random, center.getX() + 0.5, center.getZ() + 0.5, params.radius);
            if (Terrain.loaded(level, c.getX(), c.getZ())) corrode(w, random, c.getX(), c.getZ());
        }
        if (activeTicks % 20 == 0) burnEntities(level);
        if (activeTicks % 2 == 0) drizzle(level, 12 + 2 * params.potency);
    }

    /** The acid works on the top block of a column. */
    private void corrode(BlockWriter w, RandomSource random, int x, int z) {
        ServerLevel level = w.level();
        BlockPos top = new BlockPos(x, Terrain.surface(level, x, z), z);
        BlockState s = level.getBlockState(top);
        float p = params.potency;
        if (s.is(BlockTags.LEAVES)) {
            if (random.nextFloat() < 0.1F + 0.04F * p) w.set(top, Blocks.AIR.defaultBlockState());
        } else if (s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SHORT_GRASS)
                || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.BUSH)) {
            BlockState dead = Blocks.DEAD_BUSH.defaultBlockState();
            w.set(top, random.nextBoolean() && dead.canSurvive(level, top) ? dead : Blocks.AIR.defaultBlockState());
        } else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.MOSS_BLOCK)) {
            if (random.nextFloat() < 0.3F) w.set(top, (random.nextFloat() < 0.3F ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState());
        } else if (s.is(Blocks.FARMLAND) || s.is(Blocks.PODZOL)) {
            if (random.nextFloat() < 0.2F) w.set(top, Blocks.COARSE_DIRT.defaultBlockState());
        } else if (s.getBlock() instanceof WeatheringCopper) {
            WeatheringCopper.getNext(s.getBlock()).ifPresent(next -> w.set(top, next.withPropertiesOf(s)));
        } else if (s.is(Blocks.MOSSY_COBBLESTONE)) {
            w.set(top, Blocks.COBBLESTONE.defaultBlockState());
        } else if (p >= 6 && s.is(Blocks.STONE) && random.nextFloat() < 0.02F * p) {
            w.set(top, Blocks.COBBLESTONE.defaultBlockState());
        } else if (p >= 6 && s.is(Blocks.COBBLESTONE) && random.nextFloat() < 0.01F * p) {
            w.set(top, Blocks.GRAVEL.defaultBlockState());
        } else if (s.is(Blocks.SMOOTH_STONE) || s.is(Blocks.STONE_BRICKS)) {
            if (random.nextFloat() < 0.02F * p) w.set(top, (s.is(Blocks.STONE_BRICKS) ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE).defaultBlockState());
        }
    }

    /** Burns creatures in the open; a helmet takes the damage instead (and wears out). */
    private void burnEntities(ServerLevel level) {
        double r = params.radius;
        AABB box = new AABB(center.getX() - r, level.getMinY(), center.getZ() - r, center.getX() + r, level.getMaxY(), center.getZ() + r);
        float damage = 0.5F + 0.25F * params.potency;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (Protection.spares(level, e)) continue;
            if (e.isSpectator() || !level.canSeeSky(e.blockPosition().above())) continue;
            if (Terrain.dist(e.getX(), e.getZ(), center.getX(), center.getZ()) > r) continue;
            ItemStack helmet = e.getItemBySlot(EquipmentSlot.HEAD);
            if (!helmet.isEmpty() && helmet.isDamageableItem()) {
                helmet.hurtAndBreak(1 + params.potency / 3, e, EquipmentSlot.HEAD);
                continue;
            }
            if (hurt(level, e, level.damageSources().source(ACID), damage)) {
                level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.ACID_SIZZLE.get(), SoundSource.WEATHER, 0.4F, 1.0F);
            }
        }
    }

    /** Green droplets around players so the rain looks wrong. */
    private void drizzle(ServerLevel level, int count) {
        for (ServerPlayer p : playersNear(level, params.radius)) {
            level.sendParticles(p, ACID_DROP, true, true, p.getX(), p.getY() + 3, p.getZ(), count, 8, 3, 8, 0.0);
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
