package com.bladesmelody.naturaldisasters.protect;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.meteorite.WardData;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Land disasters must leave alone: claims from Open Parties and Claims or
 * Flan (when installed and {@code protectClaims} is on) and our own safe
 * zones. Blocks there are never changed; creatures there are still hurt.
 * Answers are cached per chunk for the current tick.
 */
public final class Protection {
    /** Tag on falling blocks a disaster throws: they crumble instead of landing on protected land. */
    public static final String DEBRIS_TAG = "naturaldisasters.debris";

    /** Chunk states: none of it protected, all of it, or some (check each block). */
    static final byte FREE = 0, ALL = 1, PARTLY = 2;
    private static final boolean OPAC = ModList.get().isLoaded("openpartiesandclaims");
    private static final boolean FLAN = ModList.get().isLoaded("flan");

    private record Cache(long tick, Long2ByteOpenHashMap chunks) {}
    private static final Map<ServerLevel, Cache> CACHE = new WeakHashMap<>();

    private Protection() {}

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        byte state = chunkState(level, pos.getX() >> 4, pos.getZ() >> 4);
        if (state == FREE) return false;
        if (state == ALL) return true;
        return SafeZones.get(level.getServer()).contains(level, pos.getX(), pos.getZ())
                || (wards() && inWard(level, pos.getX(), pos.getZ()))
                || (FLAN && claims() && FlanClaims.claimed(level, pos));
    }

    /** True if any of the chunk may be protected (cheap; for spreading the search for a spot). */
    public static boolean touchesChunk(ServerLevel level, int chunkX, int chunkZ) {
        return chunkState(level, chunkX, chunkZ) != FREE;
    }

    /**
     * Disasters spare things in protected land that aren't creatures: item
     * frames, armor stands, dropped items, minecarts. Creatures and players
     * there are still pushed and hurt (owner: "still dangerous").
     */
    public static boolean spares(ServerLevel level, Entity e) {
        return !(e instanceof Mob || e instanceof Player) && isProtected(level, e.blockPosition());
    }

    /** Forget cached answers (a safe zone or ward was added, changed or removed). */
    public static void invalidate() {
        CACHE.clear();
    }

    private static boolean wards() {
        return Config.WARDS.getAsBoolean();
    }

    private static boolean inWard(ServerLevel level, int x, int z) {
        for (var w : WardData.get(level).entries()) {
            BlockPos p = BlockPos.of(w.getLongKey());
            long dx = x - p.getX(), dz = z - p.getZ(), r = w.getIntValue();
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    /** Whether a chunk lies wholly, partly or not at all in a circle (bedrock to sky). */
    static byte circle(int cx, int cz, int x, int z, int radius) {
        double r2 = (double) radius * radius;
        double nx = Math.clamp(x, cx * 16, cx * 16 + 15) - x, nz = Math.clamp(z, cz * 16, cz * 16 + 15) - z;
        if (nx * nx + nz * nz > r2) return FREE;
        double fx = Math.max(Math.abs(cx * 16 - x), Math.abs(cx * 16 + 15 - x));
        double fz = Math.max(Math.abs(cz * 16 - z), Math.abs(cz * 16 + 15 - z));
        return fx * fx + fz * fz <= r2 ? ALL : PARTLY;
    }

    private static boolean claims() {
        return Config.PROTECT_CLAIMS.getAsBoolean();
    }

    private static byte chunkState(ServerLevel level, int cx, int cz) {
        long tick = level.getGameTime();
        Cache cache = CACHE.get(level);
        if (cache == null || cache.tick != tick) {
            cache = new Cache(tick, new Long2ByteOpenHashMap());
            CACHE.put(level, cache);
        }
        long key = ChunkPos.pack(cx, cz);
        if (cache.chunks.containsKey(key)) return cache.chunks.get(key);
        byte state = compute(level, cx, cz);
        cache.chunks.put(key, state);
        return state;
    }

    private static byte compute(ServerLevel level, int cx, int cz) {
        byte state = SafeZones.get(level.getServer()).chunkState(level, cx, cz);
        if (state == ALL) return state;
        if (wards()) {
            for (var w : WardData.get(level).entries()) {
                BlockPos p = BlockPos.of(w.getLongKey());
                byte s = circle(cx, cz, p.getX(), p.getZ(), w.getIntValue());
                if (s == ALL) return ALL;
                if (s == PARTLY) state = PARTLY;
            }
        }
        if (!claims()) return state;
        if (OPAC && OpacClaims.claimed(level, cx, cz)) return ALL;
        if (FLAN && FlanClaims.anyIn(level, cx, cz)) return PARTLY;
        return state;
    }

    /** Thrown debris crumbles at the edge of protected land instead of landing on it. */
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof FallingBlockEntity block) || !(block.level() instanceof ServerLevel level)) return;
        if (!block.entityTags().contains(DEBRIS_TAG)) return;
        BlockPos next = BlockPos.containing(block.position().add(block.getDeltaMovement()));
        if (isProtected(level, next) || isProtected(level, block.blockPosition())) block.discard();
    }
}
