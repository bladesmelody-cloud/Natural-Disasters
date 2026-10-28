package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.disaster.Builds;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Starts disasters on their own: on average every {@code intervalDays} in-game
 * days (counting only while players are online), near a random player, with a
 * warning first.
 */
public final class RandomDisasters {
    private RandomDisasters() {}

    static void tick(MinecraftServer server) {
        if (!Config.RANDOM_ENABLED.getAsBoolean()) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        DisasterData data = DisasterData.get(server.overworld());
        if (data.ticksUntilRandom < 0) data.ticksUntilRandom = rollInterval(server.overworld().getRandom());
        if (--data.ticksUntilRandom > 0) {
            if (data.ticksUntilRandom % 200 == 0) data.setDirty();
            return;
        }
        data.ticksUntilRandom = rollInterval(server.overworld().getRandom());
        data.setDirty();
        trigger(server, Config.WARNING_SECONDS.getAsInt() * 20);
    }

    private static long rollInterval(RandomSource random) {
        double mean = Config.RANDOM_INTERVAL_DAYS.getAsDouble() * 24000;
        return Math.max(1, (long) (mean * (0.5 + random.nextDouble())));
    }

    /** Starts a random disaster near a random online player now. */
    public static Optional<Disaster> trigger(MinecraftServer server, int warningTicks) {
        RandomSource random = server.overworld().getRandom();
        // Overworld only for now (owner's choice); commands still work in any dimension.
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
                .filter(p -> !p.isSpectator() && p.level().dimension() == Level.OVERWORLD).toList();
        List<DisasterType<?>> types = DisasterTypes.all().stream().filter(DisasterType::randomEnabled).toList();
        if (players.isEmpty() || types.isEmpty()) return Optional.empty();

        ServerPlayer player = players.get(random.nextInt(players.size()));
        ServerLevel level = player.level();
        BlockPos center = pickCenter(level, player.blockPosition(), random);
        List<DisasterType<?>> fitting = types.stream().filter(t -> t.canHappenAt(level, center)).toList();
        if (fitting.isEmpty()) return Optional.empty();
        DisasterType<?> type = pickWeighted(fitting, random);
        int magnitude = DisasterType.rollMagnitude(random);
        Disaster d = DisasterManager.start(level, type, center, magnitude, warningTicks);
        NaturalDisasters.LOGGER.info("Random {} M{} at {} near {}", type.name(), magnitude, center.toShortString(), player.getName().getString());
        return Optional.of(d);
    }

    private static DisasterType<?> pickWeighted(List<DisasterType<?>> types, RandomSource random) {
        double total = types.stream().mapToDouble(DisasterType::weight).sum();
        double r = random.nextDouble() * total;
        for (DisasterType<?> t : types) {
            r -= t.weight();
            if (r <= 0) return t;
        }
        return types.getLast();
    }

    /** How far around a candidate spot to look for buildings. */
    private static final int BUILD_CHECK_RADIUS = 48;

    /**
     * Picks where to strike near a player: several candidate spots (some a bit
     * farther out), preferring the one with the fewest signs of buildings, so
     * bases and villages are rarely hit. With a small chance (config) the
     * first spot is used regardless.
     */
    private static BlockPos pickCenter(ServerLevel level, BlockPos near, RandomSource random) {
        int min = Config.STRIKE_MIN_DISTANCE.getAsInt();
        int max = Math.max(min, Config.STRIKE_MAX_DISTANCE.getAsInt());
        boolean ignoreBuilds = random.nextDouble() < Config.IGNORE_BUILDS_CHANCE.getAsDouble();
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 16; attempt++) {
            // First half within the normal range, second half up to twice as far.
            int far = attempt < 8 ? max : max * 2;
            int close = attempt < 8 ? min : max;
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Mth.lerp(random.nextDouble(), close, far);
            BlockPos p = near.offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
            if (!BlockWriter.isLoaded(level, p.atY(level.getMinY()))) continue;
            p = p.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()));
            if (ignoreBuilds) return p;
            int score = Builds.score(level, p, BUILD_CHECK_RADIUS);
            if (score < bestScore) {
                best = p;
                bestScore = score;
                if (score == 0) break;
            }
        }
        return best != null ? best : near;
    }
}
