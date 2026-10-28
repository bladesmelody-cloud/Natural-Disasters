package com.bladesmelody.naturaldisasters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (per world, in serverconfig/naturaldisasters-server.toml). */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue TARGET_TICK_MS;
    public static final ModConfigSpec.IntValue MAX_WORK_MS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_FALLING_BLOCKS_PER_SECOND;

    public static final ModConfigSpec.BooleanValue RANDOM_ENABLED;
    public static final ModConfigSpec.DoubleValue RANDOM_INTERVAL_DAYS;
    public static final ModConfigSpec.IntValue WARNING_SECONDS;
    public static final ModConfigSpec.IntValue STRIKE_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue STRIKE_MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue IGNORE_BUILDS_CHANCE;
    public static final ModConfigSpec.BooleanValue PROTECT_CLAIMS;
    public static final ModConfigSpec.DoubleValue GREAT_ERUPTION_DAYS;
    public static final ModConfigSpec.BooleanValue WORLD_DARKNESS;

    /** Disaster names, in config order. */
    public static final List<String> DISASTERS = List.of("earthquake", "meteor", "tornado", "hurricane", "acid_rain",
            "drought", "flood", "tsunami", "volcano", "wildfire", "blizzard", "hailstorm");
    private static final Map<String, ModConfigSpec.BooleanValue> RANDOM_BY_TYPE = new HashMap<>();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Lag control: every disaster's block changes go through one shared budget.").push("performance");
        MAX_BLOCKS_PER_TICK = b.comment("Most block changes applied per server tick, across all disasters.")
                .defineInRange("maxBlocksPerTick", 2000, 1, 100_000);
        TARGET_TICK_MS = b.comment("When the server's average tick time goes over this, the budget shrinks automatically.")
                .defineInRange("targetTickMs", 40, 5, 1000);
        MAX_WORK_MS_PER_TICK = b.comment("Hard cap on milliseconds disasters may spend changing blocks in one tick.")
                .defineInRange("maxWorkMsPerTick", 15, 1, 1000);
        MAX_FALLING_BLOCKS_PER_SECOND = b.comment("Collapsing blocks shown as falling-block entities, per disaster per second; the rest move instantly.")
                .defineInRange("maxFallingBlocksPerSecond", 8, 0, 1000);
        b.pop();

        b.push("random");
        RANDOM_ENABLED = b.comment("Disasters happen on their own (also toggled by /disaster random on|off).")
                .define("enabled", true);
        RANDOM_INTERVAL_DAYS = b.comment("Average in-game days of server uptime between random disasters (if nobody is online when it's due, it waits for a player).")
                .defineInRange("intervalDays", 7.0, 0.01, 1000.0);
        WARNING_SECONDS = b.comment("Seconds of rumbling and hints before a random disaster hits.")
                .defineInRange("warningSeconds", 20, 0, 600);
        STRIKE_MIN_DISTANCE = b.comment("Random disasters strike at least this many blocks from the chosen player.")
                .defineInRange("strikeMinDistance", 16, 0, 512);
        STRIKE_MAX_DISTANCE = b.comment("...and at most this many.")
                .defineInRange("strikeMaxDistance", 64, 0, 512);
        IGNORE_BUILDS_CHANCE = b.comment("Random disasters prefer spots away from buildings; this is the chance they ignore that and strike anyway.")
                .defineInRange("ignoreBuildsChance", 0.05, 0.0, 1.0);
        GREAT_ERUPTION_DAYS = b.comment("Average Minecraft days of server uptime between great eruptions (VEI 5+, world-wide darkness). 0 = never.")
                .defineInRange("greatEruptionDays", 800.0, 0.0, 100_000.0);
        WORLD_DARKNESS = b.comment("VEI 5+ eruptions darken the whole Overworld (clock stopped at midnight). If false they darken a 10,000-block radius instead.")
                .define("worldDarkness", true);
        b.pop();

        b.push("protection");
        PROTECT_CLAIMS = b.comment("Disasters never change blocks in land claimed with Open Parties and Claims or Flan (creatures there are still hurt).",
                        "Safe zones made with /disaster safezone are always protected.")
                .define("protectClaims", true);
        b.pop();

        for (String name : DISASTERS) {
            b.push(name);
            RANDOM_BY_TYPE.put(name, b.comment("This disaster can happen randomly.").define("random", true));
            b.pop();
        }

        SPEC = b.build();
    }

    /** Whether the named disaster may be picked for random disasters. */
    public static boolean randomEnabled(String name) {
        ModConfigSpec.BooleanValue v = RANDOM_BY_TYPE.get(name);
        return v == null || v.getAsBoolean();
    }

    private Config() {}
}
