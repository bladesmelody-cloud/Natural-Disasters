package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.protect.Protection;
import com.bladesmelody.naturaldisasters.registry.ModSounds;
import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.shake.Shake;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/**
 * Earthquake: the ground shakes for a while, fissures tear open, blocks crack
 * and collapse, patches of ground heave up or sink, and creatures get thrown
 * around. Bigger magnitudes do all of it over a larger area.
 */
public class Earthquake extends Disaster {
    /** Blocks below the surface that shaking damage reaches. */
    private static final int SHAKE_DEPTH = 12;
    /** Extra shaking from M5 up, aimed at the top blocks where buildings are. */
    private static final int BUILDING_DEPTH = 8;
    /** How far down collapse looks for unsupported blocks. */
    private static final int COLLAPSE_DEPTH = 24;
    /** Depth of the ground slab a heave moves. */
    private static final int HEAVE_DEPTH = 12;
    private static final int MAX_SAMPLES_PER_TICK = 1500;
    private static final int NONE = Integer.MIN_VALUE;

    private static final Map<Block, Block> CRACKS = Map.of(
            Blocks.STONE, Blocks.COBBLESTONE,
            Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
            Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
            Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS,
            Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);

    /**
     * Everything that sets an earthquake's size. Saved with the quake, so a
     * running quake keeps its shape even if the table changes.
     */
    public record Params(int radius, int durationTicks, int fissures, int fissureLength, int fissureHalfWidth,
                         int fissureDepth, double shakeDensity, int heaves, int heaveRadius, int heaveHeight,
                         double knockback) {
        private static final int[] RADIUS = {16, 24, 32, 48, 64, 80, 112, 136, 160, 256};
        private static final int[] SECONDS = {10, 15, 20, 28, 34, 40, 60, 70, 80, 120};
        /** Deep enough to reach the bottom of the world; carving stops just above it. */
        private static final int TO_BEDROCK = 4096;

        public static Params forMagnitude(int magnitude) {
            int m = Math.clamp(magnitude, 1, 10);
            int radius = RADIUS[m - 1];
            int duration = SECONDS[m - 1] * 20;
            int fissures, length, halfWidth, depth;
            if (m <= 3) {
                fissures = m;
                length = 6 + 3 * m;
                halfWidth = 0;
                depth = 3 + m;
            } else if (m <= 6) {
                fissures = m - 2;
                length = (int) (radius * 0.8);
                halfWidth = 1;
                depth = 30;
            } else if (m <= 9) {
                fissures = m - 3;
                length = (int) (radius * 0.9);
                halfWidth = 2;
                depth = TO_BEDROCK;
            } else {
                fissures = 8;
                length = radius;
                halfWidth = 3;
                depth = TO_BEDROCK;
            }
            int heaves = m >= 7 ? m - 5 : 0;
            int heaveHeight = switch (m) {
                case 7 -> 2;
                case 8, 9 -> 3;
                case 10 -> 4;
                default -> 0;
            };
            return new Params(radius, duration, fissures, length, halfWidth, depth,
                    0.0005 * m, heaves, 8 + m, heaveHeight, 0.02 * m);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("duration", durationTicks);
            t.putInt("fissures", fissures);
            t.putInt("fissureLength", fissureLength);
            t.putInt("fissureHalfWidth", fissureHalfWidth);
            t.putInt("fissureDepth", fissureDepth);
            t.putDouble("shakeDensity", shakeDensity);
            t.putInt("heaves", heaves);
            t.putInt("heaveRadius", heaveRadius);
            t.putInt("heaveHeight", heaveHeight);
            t.putDouble("knockback", knockback);
            return t;
        }

        static Params load(CompoundTag t, int magnitude) {
            Params d = forMagnitude(magnitude);
            return new Params(t.getIntOr("radius", d.radius), t.getIntOr("duration", d.durationTicks),
                    t.getIntOr("fissures", d.fissures), t.getIntOr("fissureLength", d.fissureLength),
                    t.getIntOr("fissureHalfWidth", d.fissureHalfWidth), t.getIntOr("fissureDepth", d.fissureDepth),
                    t.getDoubleOr("shakeDensity", d.shakeDensity), t.getIntOr("heaves", d.heaves),
                    t.getIntOr("heaveRadius", d.heaveRadius), t.getIntOr("heaveHeight", d.heaveHeight),
                    t.getDoubleOr("knockback", d.knockback));
        }
    }

    /** One ground column a fissure cuts, {@code depth} blocks deep from the surface. */
    private record Column(int x, int z, int depth) {}

    /** One heaving patch: columns move {@code shift} blocks (up if positive). */
    private record HeaveColumn(int x, int z, int shift) {}

    private Params params;

    // Progress (saved).
    private int[] fissureCursor = new int[0];
    private int[] columnTop = new int[0];
    private int[] columnY = new int[0];
    private int heavePatch;
    private int heaveColumn;

    // Rebuilt from the seed.
    private List<List<Column>> fissureColumns;
    private List<List<HeaveColumn>> heavePatches;
    private int fallingThisSecond;

    public Earthquake(DisasterType<Earthquake> type) {
        super(type);
    }

    /** Builds a quake with explicit parameters (game tests, tuning). */
    public static Earthquake create(BlockPos center, int magnitude, Params params, long seed, int warningTicks) {
        Earthquake q = DisasterTypes.EARTHQUAKE.create(center, magnitude, seed, warningTicks);
        q.setParams(params);
        return q;
    }

    @Override
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        super.init(center, magnitude, seed, warningTicks);
        setParams(Params.forMagnitude(magnitude));
    }

    private void setParams(Params params) {
        this.params = params;
        fissureCursor = new int[params.fissures];
        columnTop = new int[params.fissures];
        columnY = new int[params.fissures];
        Arrays.fill(columnTop, NONE);
        heavePatch = 0;
        heaveColumn = 0;
        fissureColumns = null;
        heavePatches = null;
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
        if (inWarning()) return false;
        int active = age - warningTicks;
        if (active >= params.durationTicks * 3) return true;
        if (active < params.durationTicks) return false;
        build();
        for (int f = 0; f < params.fissures; f++) {
            if (fissureCursor[f] < fissureColumns.get(f).size()) return false;
        }
        return heavePatch >= heavePatches.size();
    }

    @Override
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 40 != 0) return;
        for (ServerPlayer p : playersNear(level, params.radius * 1.5)) {
            if (ticksLeft % 120 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EARTHQUAKE_RUMBLE.get(), SoundSource.AMBIENT, 0.3F, 0.8F);
            }
            BlockState ground = level.getBlockState(p.blockPosition().below());
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                        p.getX(), p.getY() + 0.1, p.getZ(), 6, 1.5, 0.05, 1.5, 0.0);
            }
        }
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        build();
        RandomSource random = RandomSource.create(seed ^ (activeTicks * 0x9E3779B97F4A7C15L));
        if (activeTicks % 20 == 0) fallingThisSecond = 0;

        boolean shaking = activeTicks < params.durationTicks;
        if (shaking) {
            if (activeTicks % 5 == 0) shakeEntities(level, random);
            if (activeTicks % 30 == 0) rumble(level, random);
            if (activeTicks % 10 == 0) Shake.near(level, center.getX() + 0.5, center.getZ() + 0.5, params.radius * 1.3,
                    0.4F + 0.15F * Math.clamp(magnitude, 1, 10), 15);
        }

        openFissures(w, activeTicks);
        heave(w, activeTicks);

        if (shaking) {
            double expected = params.shakeDensity * Math.PI * params.radius * params.radius;
            int samples = (int) expected + (random.nextDouble() < expected - (int) expected ? 1 : 0);
            samples = Math.min(samples, MAX_SAMPLES_PER_TICK);
            for (int i = 0; i < samples && w.hasBudget(); i++) shakeDamage(w, random, SHAKE_DEPTH);
            if (magnitude >= 5) {
                for (int i = 0; i < samples && w.hasBudget(); i++) shakeDamage(w, random, BUILDING_DEPTH);
            }
            for (int i = 0; i < (samples + 1) / 2 && w.hasBudget(); i++) collapse(w, random);
        }
    }

    // ---- Fissures ----

    private void openFissures(BlockWriter w, int activeTicks) {
        double progress = Math.min(1.0, (activeTicks + 1) / (params.durationTicks * 0.6));
        for (int k = 0; k < params.fissures && w.hasBudget(); k++) {
            int f = (k + activeTicks) % params.fissures;
            List<Column> columns = fissureColumns.get(f);
            int allowed = (int) Math.ceil(columns.size() * progress);
            while (fissureCursor[f] < allowed && w.hasBudget()) carve(w, f, columns.get(fissureCursor[f]));
        }
    }

    /** Carves (part of) one fissure column, top-down, resuming where it left off. */
    private void carve(BlockWriter w, int f, Column c) {
        ServerLevel level = w.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(c.x, level.getMinY(), c.z);
        if (columnTop[f] == NONE) {
            if (!BlockWriter.isLoaded(level, pos)) {
                fissureCursor[f]++;
                return;
            }
            columnTop[f] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.x, c.z) - 1;
            columnY[f] = columnTop[f];
        }
        int bottom = Math.max(level.getMinY() + 5, columnTop[f] - c.depth + 1);
        while (columnY[f] >= bottom && w.hasBudget()) {
            pos.setY(columnY[f]--);
            w.set(pos, Blocks.AIR.defaultBlockState());
        }
        if (columnY[f] < bottom) {
            fissureCursor[f]++;
            columnTop[f] = NONE;
        }
    }

    // ---- Shaking damage ----

    /** Samples a block within {@code depth} of the surface and shakes it. */
    private void shakeDamage(BlockWriter w, RandomSource random, int depth) {
        ServerLevel level = w.level();
        BlockPos column = randomColumn(random);
        if (!BlockWriter.isLoaded(level, column)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) - 1;
        BlockPos pos = column.atY(top - random.nextInt(depth));
        BlockState state = level.getBlockState(pos);
        float knockOut = buildingDamage(state);
        if (knockOut > 0 && random.nextFloat() < knockOut && movable(level, pos, state)) {
            drop(w, pos, state, pos);
            return;
        }
        BlockState result = shaken(state, random);
        if (result != null) w.set(pos, result);
    }

    /**
     * Chance that shaking knocks a building block loose (it then falls as
     * rubble). Nothing below M5; 30 % at M6, 60 % at M8, 90 % at M10. Logs
     * count half, so trees mostly survive.
     */
    float buildingDamage(BlockState state) {
        float chance = Math.clamp((magnitude - 4) * 0.15F, 0F, 0.9F);
        if (chance == 0) return 0;
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.WOOL) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.BRICKS)
                || state.is(Blocks.MUD_BRICKS) || state.is(Blocks.HAY_BLOCK)) {
            return chance;
        }
        return state.is(BlockTags.LOGS) ? chance / 2 : 0;
    }

    /** What shaking turns a block into, or null to leave it. */
    static BlockState shaken(BlockState state, RandomSource random) {
        if (state.isAir()) return null;
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) return Blocks.AIR.defaultBlockState();
        if (state.is(BlockTags.LEAVES)) return random.nextBoolean() ? Blocks.AIR.defaultBlockState() : null;
        if (state.is(Blocks.COBBLESTONE)) return random.nextFloat() < 0.3F ? Blocks.GRAVEL.defaultBlockState() : null;
        Block cracked = CRACKS.get(state.getBlock());
        return cracked == null ? null : cracked.defaultBlockState();
    }

    // ---- Collapse ----

    /** Finds one unsupported block in a random column (or a crumbling edge) and drops it. */
    private void collapse(BlockWriter w, RandomSource random) {
        ServerLevel level = w.level();
        BlockPos column = randomColumn(random);
        if (!BlockWriter.isLoaded(level, column)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(), top, column.getZ());

        // Edges over a drop (fissure walls, cliffs) crumble into it.
        BlockState topState = level.getBlockState(pos);
        if (movable(level, pos, topState) && random.nextFloat() < 0.3F) {
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos side = pos.relative(d);
            if (BlockWriter.isLoaded(level, side) && empty(level.getBlockState(side)) && empty(level.getBlockState(side.below()))) {
                drop(w, pos.immutable(), topState, side);
                return;
            }
        }

        for (int y = top; y > top - COLLAPSE_DEPTH && y > level.getMinY(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (!movable(level, pos, state) || !empty(level.getBlockState(pos.below()))) continue;
            if (supports(level, pos) < 2 || (random.nextFloat() < spanCollapse() && groundedNeighbors(level, pos) == 0)) {
                drop(w, pos.immutable(), state, pos.immutable());
            }
            return;
        }
    }

    private static boolean movable(ServerLevel level, BlockPos pos, BlockState state) {
        return !empty(state) && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0;
    }

    private static boolean empty(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /**
     * Chance a block over air falls when none of its side neighbours rests on
     * anything: roofs cave in from the middle, cave ceilings drop. Rises from
     * 15 % at M4 to 100 % at M10.
     */
    private float spanCollapse() {
        return Math.clamp((magnitude - 3) * 0.15F, 0F, 1F);
    }

    /** Side neighbours that are solid and have something solid under them. */
    private static int groundedNeighbors(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(d);
            if (BlockWriter.isLoaded(level, side) && !empty(level.getBlockState(side)) && !empty(level.getBlockState(side.below()))) n++;
        }
        return n;
    }

    private static int supports(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(d);
            if (BlockWriter.isLoaded(level, side) && !empty(level.getBlockState(side))) n++;
        }
        return n;
    }

    /** Drops a block from {@code from}, falling straight down from {@code start}. */
    private void drop(BlockWriter w, BlockPos from, BlockState state, BlockPos start) {
        ServerLevel level = w.level();
        if (!w.canEdit(from) || !w.canEdit(start)) return;
        if (fallingThisSecond < Config.MAX_FALLING_BLOCKS_PER_SECOND.getAsInt()) {
            fallingThisSecond++;
            if (!from.equals(start)) w.set(from, Blocks.AIR.defaultBlockState());
            FallingBlockEntity.fall(level, from.equals(start) ? from : start, state).addTag(Protection.DEBRIS_TAG);
            w.spend(1);
            return;
        }
        BlockPos.MutableBlockPos land = start.mutable();
        int limit = 64;
        while (limit-- > 0 && land.getY() > level.getMinY() && empty(level.getBlockState(land.below()))) land.move(Direction.DOWN);
        if (w.set(from, Blocks.AIR.defaultBlockState())) w.set(land, state);
    }

    // ---- Ground heave ----

    private void heave(BlockWriter w, int activeTicks) {
        while (heavePatch < heavePatches.size() && w.hasBudget()) {
            int startTick = (int) (params.durationTicks * (0.15 + 0.6 * heavePatch / heavePatches.size()));
            if (activeTicks < startTick) return;
            List<HeaveColumn> patch = heavePatches.get(heavePatch);
            while (heaveColumn < patch.size() && w.hasBudget()) shiftColumn(w, patch.get(heaveColumn++));
            if (heaveColumn >= patch.size()) {
                heavePatch++;
                heaveColumn = 0;
            }
        }
    }

    /** Moves the top {@value #HEAVE_DEPTH} blocks of a column up or down as one piece. */
    private void shiftColumn(BlockWriter w, HeaveColumn c) {
        ServerLevel level = w.level();
        BlockPos base = new BlockPos(c.x, level.getMinY(), c.z);
        if (!BlockWriter.isLoaded(level, base)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.x, c.z) - 1;
        int bottom = Math.max(level.getMinY() + 1, top - HEAVE_DEPTH + 1);
        BlockState[] states = new BlockState[top - bottom + 1];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(c.x, 0, c.z);
        for (int y = bottom; y <= top; y++) {
            pos.setY(y);
            BlockState s = level.getBlockState(pos);
            if (s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0) return;
            states[y - bottom] = s;
        }
        int shift = c.shift;
        if (shift > 0) {
            for (int y = top; y >= bottom; y--) w.set(pos.setY(y + shift), states[y - bottom]);
        } else {
            int down = -shift;
            BlockState above = level.getBlockState(pos.setY(top + 1));
            if (!above.isAir() && empty(above)) w.set(pos, Blocks.AIR.defaultBlockState());
            for (int y = bottom; y <= top; y++) w.set(pos.setY(y - down), states[y - bottom]);
            for (int y = top - down + 1; y <= top; y++) w.set(pos.setY(y), Blocks.AIR.defaultBlockState());
        }
    }

    // ---- Creatures ----

    private void shakeEntities(ServerLevel level, RandomSource random) {
        double r = params.radius;
        AABB box = new AABB(center).inflate(r, 48, r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (Protection.spares(level, e)) continue;
            if (!e.onGround() || e.isSpectator()) continue;
            double dx = e.getX() - center.getX() - 0.5;
            double dz = e.getZ() - center.getZ() - 0.5;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > r) continue;
            double f = params.knockback * (1 - 0.5 * dist / r);
            e.push((random.nextDouble() * 2 - 1) * f, random.nextDouble() * f * 0.5, (random.nextDouble() * 2 - 1) * f);
            carried(e);
        }
    }

    private void rumble(ServerLevel level, RandomSource random) {
        float volume = 0.3F + 0.05F * Math.clamp(magnitude, 1, 10);
        // The rumble layers run 5–27 s: a new one every 6 s keeps it going without piling up.
        boolean rumble = level.getGameTime() / 30 % 4 == 0;
        for (ServerPlayer p : playersNear(level, params.radius * 1.2)) {
            if (rumble) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.EARTHQUAKE_RUMBLE.get(), SoundSource.AMBIENT,
                        volume, 0.9F + random.nextFloat() * 0.2F);
            }
            if (random.nextInt(3) == 0) {
                level.playSound(null, p.getX(), p.getY() - 2, p.getZ(), ModSounds.EARTHQUAKE_CRACK.get(), SoundSource.AMBIENT,
                        volume, 0.9F + random.nextFloat() * 0.2F);
            }
        }
    }

    // ---- Shapes from the seed ----

    private BlockPos randomColumn(RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2;
        double d = params.radius * Math.sqrt(random.nextDouble());
        return new BlockPos(center.getX() + (int) Math.floor(Math.cos(a) * d), 0, center.getZ() + (int) Math.floor(Math.sin(a) * d));
    }

    private boolean inRadius(int x, int z) {
        long dx = x - center.getX(), dz = z - center.getZ();
        return dx * dx + dz * dz <= (long) params.radius * params.radius;
    }

    private void build() {
        if (fissureColumns != null) return;
        fissureColumns = new ArrayList<>();
        for (int f = 0; f < params.fissures; f++) fissureColumns.add(buildFissure(f));
        heavePatches = new ArrayList<>();
        for (int h = 0; h < params.heaves; h++) heavePatches.add(buildHeave(h));
    }

    /** A wandering crack: widest and deepest in the middle, tapering to the ends. */
    private List<Column> buildFissure(int index) {
        RandomSource r = RandomSource.create(seed * 31 + index);
        double startAngle = r.nextDouble() * Math.PI * 2;
        double startDist = r.nextDouble() * params.radius * 0.3;
        double x = center.getX() + 0.5 + Math.cos(startAngle) * startDist;
        double z = center.getZ() + 0.5 + Math.sin(startAngle) * startDist;
        double heading = r.nextDouble() * Math.PI * 2;
        int length = Math.max(2, (int) (params.fissureLength * (0.6 + 0.4 * r.nextDouble())));

        List<Column> columns = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (int s = 0; s < length; s++) {
            double taper = Math.sin(Math.PI * (s + 0.5) / length);
            int hw = (int) Math.round(params.fissureHalfWidth * Math.sqrt(taper));
            int depth = Math.max(1, (int) Math.round(params.fissureDepth * (0.3 + 0.7 * taper)));
            int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -hw; dz <= hw; dz++) {
                    if (dx * dx + dz * dz > hw * hw + 0.5) continue;
                    int px = cx + dx, pz = cz + dz;
                    if (!inRadius(px, pz) || !seen.add(BlockPos.asLong(px, 0, pz))) continue;
                    columns.add(new Column(px, pz, depth));
                }
            }
            heading += (r.nextDouble() - 0.5) * 0.35;
            x += Math.cos(heading);
            z += Math.sin(heading);
        }
        return columns;
    }

    /** A round patch rising or sinking, most in the middle; centre columns move first. */
    private List<HeaveColumn> buildHeave(int index) {
        RandomSource r = RandomSource.create(seed * 17 + 999 + index);
        double a = r.nextDouble() * Math.PI * 2;
        double d = params.radius * 0.7 * Math.sqrt(r.nextDouble());
        int hx = center.getX() + (int) (Math.cos(a) * d);
        int hz = center.getZ() + (int) (Math.sin(a) * d);
        int height = (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(Math.max(1, params.heaveHeight)));
        int rad = params.heaveRadius;
        List<HeaveColumn> columns = new ArrayList<>();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > rad || !inRadius(hx + dx, hz + dz)) continue;
                int shift = (int) Math.round(height * (1 - dist / (rad + 1)));
                if (shift != 0) columns.add(new HeaveColumn(hx + dx, hz + dz, shift));
            }
        }
        columns.sort(Comparator.comparingInt(c -> (c.x - hx) * (c.x - hx) + (c.z - hz) * (c.z - hz)));
        return columns;
    }

    // ---- Saving ----

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        tag.putIntArray("fissureCursor", fissureCursor);
        tag.putIntArray("columnTop", columnTop);
        tag.putIntArray("columnY", columnY);
        tag.putInt("heavePatch", heavePatch);
        tag.putInt("heaveColumn", heaveColumn);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        setParams(Params.load(tag.getCompoundOrEmpty("params"), magnitude));
        copyInto(tag.getIntArray("fissureCursor").orElse(new int[0]), fissureCursor, 0);
        copyInto(tag.getIntArray("columnTop").orElse(new int[0]), columnTop, NONE);
        copyInto(tag.getIntArray("columnY").orElse(new int[0]), columnY, 0);
        heavePatch = tag.getIntOr("heavePatch", 0);
        heaveColumn = tag.getIntOr("heaveColumn", 0);
    }

    private static void copyInto(int[] from, int[] to, int fill) {
        Arrays.fill(to, fill);
        System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
    }
}
