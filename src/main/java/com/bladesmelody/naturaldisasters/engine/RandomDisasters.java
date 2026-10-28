package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.disaster.Builds;
import com.bladesmelody.naturaldisasters.disaster.Volcano;
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
 * days of server uptime, near a random player, with a warning first.
 */
public final class RandomDisasters {
    private RandomDisasters() {}

    static void tick(MinecraftServer server) {
        if (!Config.RANDOM_ENABLED.getAsBoolean()) return;
        tickGreatEruption(server);
        // Counts server uptime, players online or not (owner); when it runs out with
        // nobody around it waits for a player, since disasters strike near one.
        DisasterData data = DisasterData.get(server.overworld());
        if (data.ticksUntilRandom < 0) data.ticksUntilRandom = rollInterval(server.overworld().getRandom());
        if (data.ticksUntilRandom > 0) {
            data.ticksUntilRandom--;
            if (data.ticksUntilRandom % 200 == 0) data.setDirty();
            return;
        }
        if (trigger(server, Config.WARNING_SECONDS.getAsInt() * 20).isPresent()) {
            data.ticksUntilRandom = rollInterval(server.overworld().getRandom());
            data.setDirty();
        }
    }

    /**
     * The great-eruption timer counts server uptime (owner: "should be server
     * up time, not only player online"). When it runs out it waits for a
     * player in the Overworld, since the eruption strikes near one.
     */
    private static void tickGreatEruption(MinecraftServer server) {
        double days = Config.GREAT_ERUPTION_DAYS.getAsDouble();
        if (days <= 0) return;
        DisasterData data = DisasterData.get(server.overworld());
        if (data.ticksUntilGreat < 0) {
            data.ticksUntilGreat = Math.max(1, (long) (days * 24000 * (0.5 + server.overworld().getRandom().nextDouble())));
            data.setDirty();
        }
        if (data.ticksUntilGreat > 0) {
            data.ticksUntilGreat--;
            if (data.ticksUntilGreat % 1200 == 0) data.setDirty();
            return;
        }
        if (triggerGreatEruption(server, Config.WARNING_SECONDS.getAsInt() * 20 * 3).isPresent()) {
            data.ticksUntilGreat = -1;
            data.setDirty();
        }
    }

    /** Starts a VEI 5+ volcano near a random Overworld player now. */
    public static Optional<Disaster> triggerGreatEruption(MinecraftServer server, int warningTicks) {
        RandomSource random = server.overworld().getRandom();
        List<ServerPlayer> players = server.overworld().players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) return Optional.empty();
        ServerPlayer player = players.get(random.nextInt(players.size()));
        BlockPos center = pickCenter(server.overworld(), player.blockPosition(), random);
        int magnitude = Volcano.rollGreatMagnitude(random);
        Disaster d = DisasterManager.start(server.overworld(), DisasterTypes.VOLCANO, center, magnitude, warningTicks);
        NaturalDisasters.LOGGER.info("Great eruption: VEI {} at {} near {}", Volcano.vei(magnitude), center.toShortString(), player.getName().getString());
        return Optional.of(d);
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
        int magnitude = type.randomMagnitude(random);
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
