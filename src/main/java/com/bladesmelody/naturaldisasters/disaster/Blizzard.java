package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.sky.SkyZones;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * Blizzard: a whiteout that buries the area under snow (up to three blocks
 * deep at M10), freezes open water and puts out fires, while anything caught
 * outside slowly freezes like in powder snow (leather armour still helps).
 */
public class Blizzard extends Disaster {
    /** Everything that sets a blizzard's size; saved with it. */
    public record Params(int radius, int durationTicks, int maxLayers, double freezeRate) {
        private static final int[] LAYERS = {2, 4, 6, 8, 10, 12, 14, 16, 20, 24};

        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            // Owner: "Longer storms" (2x the first proposal); snow builds up to the same depth, just slower.
            return new Params(48 + 24 * m, (60 + 20 * m) * 40, LAYERS[m - 1], 2 + 0.3 * m);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("duration", durationTicks);
            t.putInt("layers", maxLayers);
            t.putDouble("freeze", freezeRate);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("radius", d.radius), t.getIntOr("duration", d.durationTicks),
                    t.getIntOr("layers", d.maxLayers), t.getDoubleOr("freeze", d.freezeRate));
        }
    }

    private static final int MAX_SAMPLES = 1500;

    private Params params;

    public Blizzard(DisasterType<Blizzard> type) {
        super(type);
    }

    /** Builds a blizzard with explicit parameters (game tests, tuning). */
    public static Blizzard create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Blizzard b = DisasterTypes.BLIZZARD.create(center, magnitude, seed, warningTicks);
        b.params = params;
        return b;
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
        if (ticksLeft % 10 != 0) return;
        for (ServerPlayer p : playersNear(level, params.radius * 1.3)) {
            level.sendParticles(p, ParticleTypes.SNOWFLAKE, true, true, p.getX(), p.getY() + 3, p.getZ(), 15, 8, 3, 8, 0.02);
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        SkyZones.put(id, new SkyZones.Zone(SkyZones.Kind.BLIZZARD, center.getX() + 0.5, center.getZ() + 0.5, params.radius, magnitude));
        keepWeather(level, params.durationTicks - activeTicks + 200, false);
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        double area = Math.PI * params.radius * params.radius;
        double expected = area * params.maxLayers / (params.durationTicks * 0.7);
        int samples = (int) Math.min(MAX_SAMPLES, expected) + (random.nextDouble() < expected - (int) expected ? 1 : 0);
        for (int i = 0; i < samples && w.hasBudget(); i++) {
            BlockPos c = Terrain.randomColumn(random, center.getX() + 0.5, center.getZ() + 0.5, params.radius);
            if (Terrain.loaded(level, c.getX(), c.getZ())) snowOn(w, c.getX(), c.getZ());
        }
        if (activeTicks % 5 == 0) freezeEntities(level);
        if (activeTicks % 2 == 0) whiteout(level);
        if (activeTicks % 40 == 0) {
            for (ServerPlayer p : playersNear(level, params.radius)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.5F, 1.2F);
            }
        }
    }

    /** Adds one layer of snow to a column (or freezes water / puts out fire there). */
    private void snowOn(BlockWriter w, int x, int z) {
        ServerLevel level = w.level();
        BlockPos top = new BlockPos(x, Terrain.surface(level, x, z), z);
        BlockState s = level.getBlockState(top);
        if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource() && s.is(Blocks.WATER)) {
            w.set(top, Blocks.ICE.defaultBlockState());
            return;
        }
        if (s.is(BlockTags.FIRE)) {
            w.set(top, Blocks.AIR.defaultBlockState());
            return;
        }
        if (!s.getFluidState().isEmpty()) return;
        if (snowDepth(level, top) >= params.maxLayers) return;
        if (s.is(Blocks.SNOW)) {
            int layers = s.getValue(SnowLayerBlock.LAYERS);
            w.set(top, layers >= 7 ? Blocks.SNOW_BLOCK.defaultBlockState() : s.setValue(SnowLayerBlock.LAYERS, layers + 1));
            return;
        }
        BlockState layer = Blocks.SNOW.defaultBlockState();
        if (s.canBeReplaced() && layer.canSurvive(level, top)) {
            w.set(top, layer);
            return;
        }
        BlockPos above = top.above();
        if (Terrain.empty(level.getBlockState(above)) && layer.canSurvive(level, above)) w.set(above, layer);
    }

    /** Snow already piled at the top of a column, in layers (a snow block counts 8). */
    static int snowDepth(ServerLevel level, BlockPos top) {
        int layers = 0;
        BlockPos.MutableBlockPos p = top.mutable();
        for (int i = 0; i < 8; i++) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.SNOW)) layers += s.getValue(SnowLayerBlock.LAYERS);
            else if (s.is(Blocks.SNOW_BLOCK)) layers += 8;
            else break;
            p.move(0, -1, 0);
        }
        return layers;
    }

    /** Creatures under the open sky freeze up, as if standing in powder snow. */
    private void freezeEntities(ServerLevel level) {
        double r = params.radius;
        AABB box = new AABB(center.getX() - r, level.getMinY(), center.getZ() - r, center.getX() + r, level.getMaxY(), center.getZ() + r);
        int add = (int) Math.round(5 * params.freezeRate);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.canFreeze() || e.isSpectator() || !level.canSeeSky(e.blockPosition().above())) continue;
            if (Terrain.dist(e.getX(), e.getZ(), center.getX(), center.getZ()) > r) continue;
            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + add));
        }
    }

    private void whiteout(ServerLevel level) {
        int count = 20 + 6 * magnitude;
        for (ServerPlayer p : playersNear(level, params.radius)) {
            level.sendParticles(p, ParticleTypes.SNOWFLAKE, true, true, p.getX(), p.getY() + 2, p.getZ(), count, 7, 3, 7, 0.08);
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
