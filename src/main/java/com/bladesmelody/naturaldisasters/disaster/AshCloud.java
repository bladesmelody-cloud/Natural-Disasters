package com.bladesmelody.naturaldisasters.disaster;

import com.bladesmelody.naturaldisasters.ash.AshZones;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The ash cloud a big eruption leaves behind: for days to weeks the area (or
 * the whole Overworld from VEI 5) lies under a black sky that counts as
 * night ({@link AshZones}), and ash settles on every chunk in it — the ones
 * loaded now and any loaded later while the cloud lasts. World-wide clouds
 * stop the clock at midnight; beds can't skip it.
 *
 * <p>Started by {@link Volcano}; can also be started by command.
 */
public class AshCloud extends Disaster {
    /**
     * radius &lt;= 0 means the whole world. Ash is {@code nearLayers} deep out
     * to {@code nearRadius}, thinning to {@code farLayers} at {@code radius}
     * (or at 10 × nearRadius for world-wide clouds, and stays that thin beyond).
     */
    public record Params(int radius, int nearRadius, int nearLayers, int farLayers, int durationTicks) {
        public boolean worldWide() {
            return radius <= 0;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("radius", radius);
            t.putInt("nearRadius", nearRadius);
            t.putInt("near", nearLayers);
            t.putInt("far", farLayers);
            t.putInt("duration", durationTicks);
            return t;
        }

        static Params load(CompoundTag t) {
            return new Params(t.getIntOr("radius", 1000), t.getIntOr("nearRadius", 64), t.getIntOr("near", 1),
                    t.getIntOr("far", 1), t.getIntOr("duration", 48000));
        }
    }

    private Params params = new Params(1000, 64, 1, 1, 48000);
    private final LongOpenHashSet ashed = new LongOpenHashSet();
    private final LongArrayList queue = new LongArrayList();
    private int column;
    private static final long NO_CHUNK = Long.MIN_VALUE;
    private long current = NO_CHUNK;
    private boolean clockStopped;

    public AshCloud(DisasterType<AshCloud> type) {
        super(type);
    }

    public static AshCloud create(BlockPos center, int vei, Params params, long seed) {
        AshCloud c = DisasterTypes.ASH_CLOUD.create(center, Math.clamp(vei, 1, 10), seed, 0);
        c.params = params;
        return c;
    }

    public Params params() {
        return params;
    }

    @Override
    public int radius() {
        return params.worldWide() ? 30_000_000 : params.radius;
    }

    @Override
    public String describe() {
        return "ash cloud (" + (params.worldWide() ? "whole world" : "radius " + params.radius) + ", "
                + String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, (params.durationTicks - (age - warningTicks)) / 24000.0))
                + " days left)";
    }

    @Override
    public boolean isDone() {
        return !inWarning() && age - warningTicks >= params.durationTicks;
    }

    @Override
    protected void tickActive(ServerLevel level, BlockWriter w, int activeTicks) {
        AshZones.put(id, new AshZones.Zone(center.getX() + 0.5, center.getZ() + 0.5, params.worldWide() ? 0 : params.radius, magnitude));
        if (params.worldWide() && !clockStopped) stopClock(level);
        if (activeTicks == 0) enqueueAround(level, center.getX() >> 4, center.getZ() >> 4, 16);
        if (activeTicks % 100 == 0) {
            int view = level.getServer().getPlayerList().getViewDistance();
            for (ServerPlayer p : level.players()) enqueueAround(level, p.chunkPosition().x(), p.chunkPosition().z(), view);
        }
        // Finish the chunk in progress first: `column` belongs to it, not to whatever was queued last.
        while ((current != NO_CHUNK || !queue.isEmpty()) && w.hasBudget()) {
            if (current == NO_CHUNK) {
                current = queue.removeLong(queue.size() - 1);
                column = 0;
            }
            if (ashChunk(w, current)) current = NO_CHUNK;
        }
        if (activeTicks % 4 == 0) fallingAsh(level);
    }

    /** A chunk just loaded: queue it for ash if the cloud covers it. */
    public void chunkLoaded(LevelChunk chunk) {
        if (inWarning()) return;
        long key = chunk.getPos().pack();
        if (!ashed.contains(key) && covers(chunk.getPos())) {
            ashed.add(key);
            queue.add(key);
        }
    }

    private void enqueueAround(ServerLevel level, int cx, int cz, int range) {
        for (int x = cx - range; x <= cx + range; x++) {
            for (int z = cz - range; z <= cz + range; z++) {
                long key = ChunkPos.pack(x, z);
                if (ashed.contains(key) || level.getChunkSource().getChunkNow(x, z) == null || !covers(new ChunkPos(x, z))) continue;
                ashed.add(key);
                queue.add(key);
            }
        }
    }

    private boolean covers(ChunkPos chunk) {
        if (params.worldWide()) return true;
        double dx = chunk.getMiddleBlockX() - center.getX(), dz = chunk.getMiddleBlockZ() - center.getZ();
        return dx * dx + dz * dz <= (double) (params.radius + 8) * (params.radius + 8);
    }

    /** Ash depth (in layers, 8 per block) at a distance from the volcano. */
    int layersAt(double dist) {
        if (dist <= params.nearRadius) return params.nearLayers;
        double edge = params.worldWide() ? params.nearRadius * 10.0 : params.radius;
        if (dist >= edge) return params.farLayers;
        double f = (dist - params.nearRadius) / Math.max(1, edge - params.nearRadius);
        return (int) Math.round(params.nearLayers + (params.farLayers - params.nearLayers) * f);
    }

    /** Covers one chunk's columns in ash; returns true when the chunk is done. */
    private boolean ashChunk(BlockWriter w, long key) {
        ServerLevel level = w.level();
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        if (level.getChunkSource().getChunkNow(cx, cz) == null) return true;
        while (column < 256 && w.hasBudget()) {
            int x = (cx << 4) + (column & 15), z = (cz << 4) + (column >> 4);
            column++;
            if (!params.worldWide() && Terrain.dist(x, z, center.getX(), center.getZ()) > params.radius) continue;
            ashColumn(w, x, z, layersAt(Terrain.dist(x, z, center.getX(), center.getZ())));
        }
        return column >= 256;
    }

    private void ashColumn(BlockWriter w, int x, int z, int layers) {
        ServerLevel level = w.level();
        BlockPos top = new BlockPos(x, Terrain.surface(level, x, z), z);
        BlockState s = level.getBlockState(top);
        if (!s.getFluidState().isEmpty()) return; // ash sinks in water and lava
        BlockPos pos;
        if (s.is(Blocks.SNOW) || s.is(ModRegistry.VOLCANIC_ASH.get()) || (s.canBeReplaced() && !s.isAir())) pos = top;
        else pos = top.above();
        BlockState layer = ModRegistry.VOLCANIC_ASH.get().defaultBlockState();
        if (!layer.canSurvive(level, pos)) return;
        while (layers >= 8) {
            w.set(pos, ModRegistry.VOLCANIC_ASH_BLOCK.get().defaultBlockState());
            pos = pos.above();
            layers -= 8;
        }
        if (layers > 0) w.set(pos, layer.setValue(SnowLayerBlock.LAYERS, layers));
    }

    private void fallingAsh(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (!AshZones.isDark(level, p.blockPosition())) continue;
            level.sendParticles(p, ParticleTypes.ASH, true, true, p.getX(), p.getY() + 4, p.getZ(), 40, 12, 4, 12, 0.0);
            level.sendParticles(p, ParticleTypes.WHITE_ASH, true, true, p.getX(), p.getY() + 4, p.getZ(), 10, 12, 4, 12, 0.0);
        }
    }

    // ---- World-wide night ----

    private static Optional<Holder<WorldClock>> clock(ServerLevel level) {
        return level.getServer().overworld().dimensionType().defaultClock();
    }

    private void stopClock(ServerLevel level) {
        clock(level).ifPresent(c -> {
            level.getServer().clockManager().moveToTimeMarker(c, ClockTimeMarkers.MIDNIGHT);
            level.getServer().clockManager().setPaused(c, true);
        });
        clockStopped = true;
    }

    @Override
    protected void onEnd(ServerLevel level) {
        AshZones.remove(id);
        if (clockStopped) {
            clock(level).ifPresent(c -> {
                level.getServer().clockManager().setPaused(c, false);
                level.getServer().clockManager().moveToTimeMarker(c, ClockTimeMarkers.DAY);
            });
            clockStopped = false;
        }
    }

    // ---- Saving ----

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.put("params", params.save());
        long[] done = ashed.toLongArray();
        Arrays.sort(done);
        tag.putLongArray("ashed", done);
        tag.putLongArray("queue", queue.toLongArray());
        tag.putInt("column", column);
        tag.putLong("current", current);
        tag.putBoolean("clockStopped", clockStopped);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        params = Params.load(tag.getCompoundOrEmpty("params"));
        ashed.clear();
        for (long k : tag.getLongArray("ashed").orElse(new long[0])) ashed.add(k);
        queue.clear();
        queue.addElements(0, tag.getLongArray("queue").orElse(new long[0]));
        column = tag.getIntOr("column", 0);
        current = tag.getLongOr("current", NO_CHUNK);
        clockStopped = tag.getBooleanOr("clockStopped", false);
    }
}
