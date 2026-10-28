package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.Config;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runs every active disaster each server tick, sharing one adaptive block
 * budget between them.
 *
 * <p>The budget starts at {@code maxBlocksPerTick}; it shrinks when the
 * server's average tick time goes over {@code targetTickMs} and grows back
 * when there is headroom. On top of that, block work stops for the tick once
 * {@code maxWorkMsPerTick} has been spent.
 */
public final class DisasterManager {
    private static double budget = -1;

    private DisasterManager() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        RandomDisasters.tick(server);

        List<Running> running = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Disaster d : DisasterData.get(level).active) running.add(new Running(level, d));
        }
        if (running.isEmpty()) return;

        int total = adaptBudget(server);
        int share = Math.max(16, total / running.size());
        long deadline = System.nanoTime() + Config.MAX_WORK_MS_PER_TICK.getAsInt() * 1_000_000L;

        for (Running r : running) {
            r.disaster.tick(r.level, new BlockWriter(r.level, share, deadline));
        }
        for (ServerLevel level : server.getAllLevels()) {
            DisasterData data = DisasterData.get(level);
            if (data.active.isEmpty()) continue;
            data.active.removeIf(Disaster::isFinished);
            data.setDirty();
        }
    }

    private static int adaptBudget(MinecraftServer server) {
        int max = Config.MAX_BLOCKS_PER_TICK.getAsInt();
        if (budget < 0 || budget > max) budget = max;
        double avgMs = server.getAverageTickTimeNanos() / 1_000_000.0;
        if (avgMs > Config.TARGET_TICK_MS.getAsInt()) {
            budget = Math.max(32, budget * 0.8);
        } else {
            budget = Math.min(max, budget * 1.05 + 8);
        }
        return (int) budget;
    }

    /** Current block budget per tick (for /disaster list). */
    public static int currentBudget() {
        return budget < 0 ? Config.MAX_BLOCKS_PER_TICK.getAsInt() : (int) budget;
    }

    public static <D extends Disaster> D start(ServerLevel level, DisasterType<D> type, BlockPos center, int magnitude, int warningTicks) {
        D d = type.create(center, magnitude, level.getRandom().nextLong(), warningTicks);
        add(level, d);
        return d;
    }

    /** Adds an already-built disaster (gives it an id). */
    public static void add(ServerLevel level, Disaster d) {
        DisasterData global = DisasterData.get(level.getServer().overworld());
        d.id = global.nextId++;
        global.setDirty();
        DisasterData data = DisasterData.get(level);
        data.active.add(d);
        data.setDirty();
    }

    public record Running(ServerLevel level, Disaster disaster) {}

    public static List<Running> all(MinecraftServer server) {
        List<Running> list = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Disaster d : DisasterData.get(level).active) list.add(new Running(level, d));
        }
        return list;
    }

    /** Stops disasters with the given id (or all if id &lt; 0). Returns how many were stopped. */
    public static int stop(MinecraftServer server, int id) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            DisasterData data = DisasterData.get(level);
            for (Iterator<Disaster> it = data.active.iterator(); it.hasNext(); ) {
                Disaster d = it.next();
                if (id < 0 || d.id == id) {
                    d.stop();
                    it.remove();
                    count++;
                }
            }
            data.setDirty();
        }
        return count;
    }
}
